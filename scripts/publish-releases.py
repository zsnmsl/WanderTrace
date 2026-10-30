"""Owner-operated, resumable publication of the two prepared releases.

--dry-run is offline and never writes Git objects, index, refs, or credentials.
Only a normal invocation commits, tags, pushes, and publishes GitHub releases.
"""
import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tarfile
import tempfile
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
REPO = "zsnmsl/WanderTrace"
ORIGIN = "https://github.com/" + REPO + ".git"
PLAN = ROOT / "scripts/release-plan.json"


class PublishError(RuntimeError):
    pass


def sha(data):
    return hashlib.sha256(data).hexdigest()


def canonical(data):
    return data.replace(b"\r\n", b"\n")


def find_git():
    executable = shutil.which("git")
    if not executable:
        executable = str(Path.home() / ".cache/codex-runtimes/codex-primary-runtime/dependencies/native/git/cmd/git.exe")
    if not Path(executable).is_file():
        raise PublishError("Install Git for Windows and make git available in PATH.")
    return executable


class Git:
    def __init__(self):
        self.executable = find_git()

    def call(self, *args, data=None, allow=()):
        result = subprocess.run([self.executable, *args], cwd=ROOT, input=data,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode and result.returncode not in allow:
            # Never include credential helper output or HTTP authorization data.
            raise PublishError("Git command failed: " + args[0] + ". " +
                               result.stderr.decode("utf-8", errors="replace").strip())
        return result.stdout

    def text(self, *args):
        return self.call(*args).decode("utf-8").strip()

    def tag(self, name):
        result = self.call("rev-parse", "--verify", "--quiet", "refs/tags/" + name + "^{commit}", allow=(1,))
        return result.decode().strip()


def source_path(name):
    parts = name.split("/")
    if any(p in {"build", "build-release", "build-stable", "jniLibs", ".gradle"} for p in parts):
        return False
    return name.startswith(("app/src/main/", "native/")) or name in {
        "app/build.gradle", "build.gradle", "settings.gradle", "gradle.properties",
        "gradle/wrapper/gradle-wrapper.properties", "scripts/build-native.ps1"}


def source_digest(files):
    digest = hashlib.sha256()
    for name in sorted(n for n in files if source_path(n)):
        digest.update(name.encode("utf-8") + b"\0" + canonical(files[name]) + b"\0")
    return digest.hexdigest()


def archive_tree(git, revision):
    with tarfile.open(fileobj=io.BytesIO(git.call("archive", "--format=tar", revision)), mode="r:") as archive:
        return {m.name: archive.extractfile(m).read() for m in archive.getmembers() if m.isfile()}


def working_files(git):
    raw = git.call("-c", "core.quotepath=false", "ls-files", "-z", "--cached", "--others", "--exclude-standard")
    names = set(x.decode("utf-8") for x in raw.split(b"\0") if x)
    blocked = re.compile(r"(^|/)(build[^/]*|\.gradle|\.tools|releases|jniLibs|\.cxx|__pycache__)/|"
                         r"\.(apk|aab|apks|aar|jar|so|a|o|obj|class|dex|idsig|log|jks|keystore|p12|pfx|pem|key|db|sqlite3?|zip|pyc)$|"
                         r"(^|/)(local\.properties|keystore\.properties|signing\.properties|\.env(?:\..*)?)$", re.I)
    files = {}
    for name in sorted(names):
        if name != "gradle/wrapper/gradle-wrapper.jar" and not name.endswith("/.env.example") and name != ".env.example" and blocked.search(name):
            raise PublishError("Do not commit generated files or credentials: " + name)
        path = ROOT / name
        if path.is_symlink():
            raise PublishError("Review symbolic links before release publication: " + name)
        if path.is_file():
            if path.stat().st_size > 95 * 1024 * 1024:
                raise PublishError("Source file is too large for this workflow: " + name)
            files[name] = path.read_bytes()
    return files


def snapshot_242(git, plan, files):
    snapshot = archive_tree(git, plan["base_commit"])
    for name in plan["snapshot_242_paths"]:
        if name not in files:
            raise PublishError("Missing 2.4.2 snapshot source: " + name)
        snapshot[name] = files[name]
    gradle = canonical(files["app/build.gradle"]).decode("utf-8")
    gradle, codes = re.subn(r"versionCode\s+13\b", "versionCode 12", gradle)
    gradle, names = re.subn(r'versionName\s+"2\.4\.3-pace"', 'versionName "2.4.2-stepfix"', gradle)
    if (codes, names) != (1, 1):
        raise PublishError("The prepared source tree must still be version 2.4.3 / code 13.")
    snapshot["app/build.gradle"] = gradle.encode("utf-8")
    insertions = {
        "README.md": ("## System integration and hot reload", "Step timing, bounded delivery queues, diagnostics, and compatibility limits are described in the [step simulation notes](docs/STEP_SIMULATION.md). Compatibility with 云运动 has not been verified on a device for this revision."),
        "README.zh-CN.md": ("## 系统实现与热重载", "步频计时、发送队列、诊断指标和兼容边界见[计步实现说明](docs/STEP_SIMULATION.md)（英文）。新版本尚未完成云运动的真机验证。"),
        "README.ja.md": ("## システム連携とホットリロード", "計時、送信キュー、診断情報、互換性の範囲は[歩数シミュレーションの説明](docs/STEP_SIMULATION.md)（英語）を参照してください。このリビジョンは云运动での実機検証を完了していません。")}
    for name, (heading, paragraph) in insertions.items():
        text = canonical(snapshot[name]).decode("utf-8")
        if text.count(heading + "\n") != 1:
            raise PublishError("Cannot reconstruct the original 2.4.2 README: " + name)
        snapshot[name] = text.replace(heading + "\n", heading + "\n\n" + paragraph + "\n", 1).encode("utf-8")
    return snapshot


def apk_manifest(path):
    import zipfile
    with zipfile.ZipFile(path) as archive:
        data = archive.read("AndroidManifest.xml")
    pool, attrs, offset = [], {}, 8
    while offset < len(data):
        kind, header, size = struct.unpack_from("<HHI", data, offset)
        if size < 8 or offset + size > len(data):
            raise PublishError("Invalid APK manifest: " + path.name)
        if kind == 1:
            count, _, flags, start, _ = struct.unpack_from("<IIIII", data, offset + 8)
            utf8 = bool(flags & 256)
            def length(p):
                if utf8:
                    value = data[p]; p += 1
                    if value & 128:
                        value = ((value & 127) << 8) | data[p]; p += 1
                else:
                    value = struct.unpack_from("<H", data, p)[0]; p += 2
                    if value & 32768:
                        value = ((value & 32767) << 16) | struct.unpack_from("<H", data, p)[0]; p += 2
                return value, p
            for index in range(count):
                p = offset + start + struct.unpack_from("<I", data, offset + header + index * 4)[0]
                n, p = length(p)
                if utf8:
                    n, p = length(p)
                pool.append(data[p:p + n * (1 if utf8 else 2)].decode("utf-8" if utf8 else "utf-16le"))
        elif kind == 258:
            ext = offset + 16
            _, name, astart, asize, acount = struct.unpack_from("<IIHHH", data, ext)
            if pool[name] in {"manifest", "application"}:
                for index in range(acount):
                    a = ext + astart + index * asize
                    _, name, raw, _, _, dtype, value = struct.unpack_from("<IIIHBBI", data, a)
                    attrs[pool[name]] = pool[raw] if raw != 0xffffffff else pool[value] if dtype == 3 else value
        offset += size
    return attrs


def check_assets(plan):
    import zipfile
    for item in plan["releases"]:
        apk = ROOT / item["apk"]
        if not apk.is_file() or sha(apk.read_bytes()) != item["sha256"]:
            raise PublishError("APK missing or checksum differs: " + str(apk))
        attrs = apk_manifest(apk)
        if (attrs.get("package"), attrs.get("versionCode"), attrs.get("versionName")) != ("com.wandertrace", item["version_code"], item["version_name"]):
            raise PublishError("APK metadata does not match " + item["tag"])
        if attrs.get("debuggable", 0) or attrs.get("testOnly", 0):
            raise PublishError("Refusing a debuggable or test-only APK.")
        with zipfile.ZipFile(apk) as archive:
            native = archive.read("lib/arm64-v8a/libwander_native.so")
            if sha(native) != plan["native_sha256"]:
                raise PublishError("Unexpected native library in " + apk.name)
            if item["version_code"] == 13 and not any(b"Lcom/wandertrace/Pace;" in archive.read(n) for n in archive.namelist() if n.endswith(".dex")):
                raise PublishError("Pace implementation missing from the 2.4.3 APK.")
        notes = ROOT / item["notes"]
        if not notes.is_file() or len(notes.read_text(encoding="utf-8").strip()) < 100:
            raise PublishError("Version-specific release notes are required.")


def commit(git, message):
    with tempfile.TemporaryDirectory(prefix="wander-release-message-") as temporary:
        path = Path(temporary) / "message.txt"
        path.write_text(message + "\n", encoding="utf-8")
        git.call("commit", "--file", str(path))
    return git.text("rev-parse", "HEAD")


def stage_snapshot(git, snapshot):
    base = archive_tree(git, "HEAD")
    for name, data in snapshot.items():
        if name not in base or canonical(base[name]) != canonical(data):
            blob = git.call("hash-object", "-w", "--stdin", data=canonical(data)).decode().strip()
            git.call("update-index", "--add", "--cacheinfo", "100644," + blob + "," + name)


def check_tag(git, item, revision):
    text = git.text("show", revision + ":app/build.gradle")
    if not re.search(r"versionCode\s+" + str(item["version_code"]) + r"\b", text) or '"' + item["version_name"] + '"' not in text:
        raise PublishError("Existing tag points to another app version: " + item["tag"])
    if source_digest(archive_tree(git, revision)) != item["source_digest"]:
        raise PublishError("Source under the tag does not match the prepared release: " + item["tag"])


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, request, file, code, message, headers, url):
        return None


class GitHub:
    def __init__(self, token):
        self.token = token
        self.opener = urllib.request.build_opener(NoRedirect)

    def call(self, method, path, body=None, asset=None, allow404=False):
        base = "https://uploads.github.com" if asset else "https://api.github.com"
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json",
                   "X-GitHub-Api-Version": "2026-03-10", "User-Agent": "WanderTrace-release-script"}
        data = None
        if asset:
            data = asset.read_bytes()
            headers["Content-Type"] = "application/vnd.android.package-archive" if asset.suffix == ".apk" else "text/plain; charset=utf-8"
        elif body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8"); headers["Content-Type"] = "application/json"
        request = urllib.request.Request(base + path, data=data, headers=headers, method=method)
        try:
            with self.opener.open(request, timeout=90) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if allow404 and error.code == 404:
                return None
            raise PublishError("GitHub returned HTTP " + str(error.code) + ". Check login, Contents write permission, and existing draft releases; rerun after resolving it.") from None
        except urllib.error.URLError:
            raise PublishError("GitHub request failed. No write is automatically retried; inspect the draft and rerun to resume.") from None


def token_for(git):
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    if token:
        return token
    result = subprocess.run([git.executable, "credential", "fill"], cwd=ROOT,
                            input=b"protocol=https\nhost=github.com\n\n", stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    credentials = dict(line.split("=", 1) for line in result.stdout.decode("utf-8").splitlines() if "=" in line)
    if result.returncode or not credentials.get("password"):
        raise PublishError("GitHub authentication unavailable. Sign in with Git Credential Manager, or set GH_TOKEN with Contents write permission. Never put a token in source files.")
    return credentials["password"]


def publish_release(api, item, revision):
    prefix = "/repos/" + REPO + "/releases"
    notes = (ROOT / item["notes"]).read_text(encoding="utf-8")
    # The list endpoint explicitly includes drafts for authenticated push users.
    release, page = None, 1
    while release is None:
        rows = api.call("GET", prefix + "?per_page=100&page=" + str(page))
        release = next((r for r in rows if r["tag_name"] == item["tag"]), None)
        if len(rows) < 100:
            break
        page += 1
    if release is None:
        release = api.call("POST", prefix, {"tag_name": item["tag"], "target_commitish": revision,
                                           "name": item["title"], "body": notes, "draft": True, "prerelease": False})
    if release.get("body", "").replace("\r\n", "\n").strip() != notes.strip() or release.get("prerelease"):
        raise PublishError("Existing release has different notes or release type; review it manually: " + item["tag"])
    apk = ROOT / item["apk"]
    sums = ROOT / "releases" / (item["tag"] + "-SHA256SUMS.txt")
    sums.write_text(item["sha256"] + "  " + apk.name + "\n", encoding="utf-8")
    for asset in (apk, sums):
        assets = api.call("GET", prefix + "/" + str(release["id"]) + "/assets?per_page=100")
        found = next((a for a in assets if a["name"] == asset.name), None)
        expected = "sha256:" + sha(asset.read_bytes())
        if found is None:
            if not release["draft"]:
                raise PublishError("Published release is incomplete; no assets will be overwritten: " + item["tag"])
            found = api.call("POST", prefix + "/" + str(release["id"]) + "/assets?name=" + urllib.parse.quote(asset.name), asset=asset)
        if found.get("state") != "uploaded" or found.get("digest") != expected or found.get("size") != asset.stat().st_size:
            raise PublishError("Remote asset checksum/state differs. Review the draft manually: " + asset.name)
    if release["draft"]:
        release = api.call("PATCH", prefix + "/" + str(release["id"]),
                           {"draft": False, "make_latest": "true" if item["latest"] else "false"})
    print("Published: " + release["html_url"])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="Offline preview; no Git writes, credentials or network access")
    args = parser.parse_args()
    plan = json.loads(PLAN.read_text(encoding="utf-8"))
    git = Git()
    if Path(git.text("rev-parse", "--show-toplevel")).resolve() != ROOT:
        raise PublishError("Unexpected Git repository root.")
    if git.text("branch", "--show-current") != "main":
        raise PublishError("Switch to main yourself before publishing.")
    origin = git.text("remote", "get-url", "origin")
    if origin not in {ORIGIN, "git@github.com:" + REPO + ".git"}:
        raise PublishError("Origin differs from the configured project; it will not be replaced.")
    if git.call("diff", "--cached", "--name-only"):
        raise PublishError("The index already has staged changes. Commit or unstage them yourself before this workflow.")
    files = working_files(git)
    check_assets(plan)
    if source_digest(files) != plan["releases"][1]["source_digest"]:
        raise PublishError("Source changed since the prepared 2.4.3 build. Rebuild and review the release plan; do not publish mismatched sources and APKs.")
    snapshot = snapshot_242(git, plan, files)
    if source_digest(snapshot) != plan["releases"][0]["source_digest"]:
        raise PublishError("The reconstructed 2.4.2 source does not match the prepared snapshot.")
    revisions = {item["tag"]: git.tag(item["tag"]) for item in plan["releases"]}
    for item in plan["releases"]:
        if revisions[item["tag"]]:
            check_tag(git, item, revisions[item["tag"]])
    head = git.text("rev-parse", "HEAD")
    if not revisions["v2.4.2"] and head != plan["base_commit"]:
        try:
            check_tag(git, plan["releases"][0], head)
        except PublishError:
            raise PublishError("Commit history changed before the 2.4.2 snapshot was created. Review the version split rather than rewriting history automatically.") from None
    if revisions["v2.4.3"] and git.call("--no-optional-locks", "status", "--porcelain"):
        raise PublishError("Existing release tags found with new source changes. Handle those changes separately before resuming publication.")
    if args.dry_run:
        for item in plan["releases"]:
            print(item["tag"] + ": code " + str(item["version_code"]) + ", APK SHA-256 " + item["sha256"])
            print("  Notes: " + item["notes"] + "; source fingerprint verified; existing tag: " + str(bool(revisions[item["tag"]])))
        print("Preview only: would commit versioned source snapshots, push main/tags, upload verified APK/checksum assets, then publish draft releases.")
        print("No commits, tags, index/config changes, credential access or network requests performed.")
        return

    # Authentication and remote checks happen before creating any source commits.
    api = GitHub(token_for(git))
    remote = api.call("GET", "/repos/" + REPO)
    if remote.get("full_name", "").lower() != REPO.lower() or not remote.get("permissions", {}).get("push", False):
        raise PublishError("The authenticated account must have push access to this repository.")
    git.call("fetch", "--no-tags", "origin", "main")
    git.call("merge-base", "--is-ancestor", "FETCH_HEAD", "HEAD")
    for item in plan["releases"]:
        remote_tag = git.call("ls-remote", "--tags", "origin", "refs/tags/" + item["tag"])
        if remote_tag:
            if not revisions[item["tag"]]:
                raise PublishError("Remote tag exists but is absent locally. Fetch and review it first: " + item["tag"])
            remote_object = remote_tag.decode().split()[0]
            if remote_object != git.text("rev-parse", "refs/tags/" + item["tag"]):
                raise PublishError("Local and remote tags differ; no tag will be overwritten.")
    for key in ("user.name", "user.email"):
        if not git.text("config", "--get", key):
            raise PublishError("Configure your Git author name and email before publishing.")
    for index, item in enumerate(plan["releases"]):
        if not revisions[item["tag"]]:
            current = git.text("rev-parse", "HEAD")
            matches = source_digest(archive_tree(git, current)) == item["source_digest"]
            if matches and (index == 0 or not git.call("--no-optional-locks", "status", "--porcelain")):
                revision = current  # Resume if commit succeeded but tag creation was interrupted.
            else:
                if index == 0:
                    stage_snapshot(git, snapshot)
                else:
                    git.call("add", "--all", "--", ".")
                    git.call("update-index", "--chmod=+x", "--", "gradlew")
                revision = commit(git, item["commit_message"])
            check_tag(git, item, revision)
            git.call("tag", "-a", item["tag"], revision, "--file", str(ROOT / item["notes"]))
            revisions[item["tag"]] = revision
    git.call("push", "--atomic", "origin", "HEAD:refs/heads/main", "refs/tags/v2.4.2", "refs/tags/v2.4.3")
    for item in plan["releases"]:
        publish_release(api, item, revisions[item["tag"]])


if __name__ == "__main__":
    try:
        main()
    except (PublishError, OSError, ValueError, KeyError, struct.error) as error:
        print("Stopped: " + str(error), file=sys.stderr)
        sys.exit(1)
