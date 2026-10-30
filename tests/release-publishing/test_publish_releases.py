"""Offline release workflow tests. Never create commits, tags, or real API calls."""
import copy
import hashlib
import io
import runpy
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MODULE = runpy.run_path(str(ROOT / "scripts/publish-releases.py"))
ERROR = MODULE["PublishError"]


def manifest(code=13, name="2.4.3-pace", debuggable=False):
    strings = ["manifest", "application", "package", "versionCode", "versionName", "debuggable", "com.wandertrace", name]
    encoded, offsets = b"", []
    for value in strings:
        text = value.encode(); offsets.append(len(encoded))
        encoded += bytes([len(value), len(text)]) + text + b"\0"
    encoded += b"\0" * (-len(encoded) % 4)
    start = 28 + len(strings) * 4
    pool = struct.pack("<HHIIIIII", 1, 28, start + len(encoded), len(strings), 0, 256, start, 0)
    pool += struct.pack("<" + "I" * len(offsets), *offsets) + encoded
    def node(element, attrs):
        ext = struct.pack("<IIHHHHHH", 0xffffffff, element, 20, 20, len(attrs), 0, 0, 0)
        values = b"".join(struct.pack("<IIIHBBI", 0xffffffff, n, 0xffffffff, 8, 0, t, v) for n, t, v in attrs)
        return struct.pack("<HHIII", 258, 16, 16 + len(ext) + len(values), 1, 0xffffffff) + ext + values
    body = pool + node(0, [(2, 3, 6), (3, 16, code), (4, 3, 7)]) + node(1, [(5, 18, int(debuggable))])
    return struct.pack("<HHI", 3, 8, 8 + len(body)) + body


class FakeAPI:
    def __init__(self):
        self.release = None
        self.assets = []
        self.events = []
        self.fail_sums_once = False

    def call(self, method, path, body=None, asset=None, **kwargs):
        self.events.append((method, path, body, asset.name if asset else None))
        if method == "GET" and "/assets?" in path:
            return copy.deepcopy(self.assets)
        if method == "GET":
            return [copy.deepcopy(self.release)] if self.release else []
        if method == "POST" and asset:
            if self.fail_sums_once and asset.suffix == ".txt":
                self.fail_sums_once = False
                raise ERROR("Simulated upload interruption")
            result = {"name": asset.name, "size": asset.stat().st_size, "state": "uploaded",
                      "digest": "sha256:" + hashlib.sha256(asset.read_bytes()).hexdigest()}
            self.assets.append(result)
            return copy.deepcopy(result)
        if method == "POST":
            self.release = dict(body, id=123, html_url="https://github.com/zsnmsl/WanderTrace/releases/tag/v2.4.3")
            return copy.deepcopy(self.release)
        if method == "PATCH":
            if len(self.assets) != 2:
                raise AssertionError("Release published before both assets were uploaded")
            self.release.update(body)
            return copy.deepcopy(self.release)
        raise AssertionError("Unexpected API operation: " + method)


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="wander-release-unit-")
        self.root = Path(self.temporary.name)
        (self.root / "releases").mkdir()
        (self.root / "notes.md").write_text("Release notes / 更新说明\n" * 10, encoding="utf-8")
        self.apk = self.root / "releases/test.apk"
        self.apk.write_bytes(b"offline APK fixture")
        self.item = {"tag": "v2.4.3", "title": "Version 2.4.3", "notes": "notes.md", "apk": "releases/test.apk",
                     "sha256": MODULE["sha"](self.apk.read_bytes()), "latest": True}
        self.publish = MODULE["publish_release"]
        self.output_patch = patch("sys.stdout", io.StringIO())
        self.output_patch.start()
        self.root_patch = patch.dict(self.publish.__globals__, ROOT=self.root)
        self.root_patch.start()

    def tearDown(self):
        self.output_patch.stop()
        self.root_patch.stop()
        self.temporary.cleanup()

    def test_manifest_metadata_and_debuggable_flag(self):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", manifest(debuggable=True))
        attrs = MODULE["apk_manifest"](self.apk)
        self.assertEqual((attrs["package"], attrs["versionCode"], attrs["versionName"]), ("com.wandertrace", 13, "2.4.3-pace"))
        self.assertEqual(attrs["debuggable"], 1)

    def test_source_digest_is_newline_independent_and_ignores_notes(self):
        digest = MODULE["source_digest"]
        self.assertEqual(digest({"app/build.gradle": b"code\n"}), digest({"app/build.gradle": b"code\r\n", "docs/notes.md": b"different"}))
        self.assertNotEqual(digest({"app/build.gradle": b"code\n"}), digest({"app/build.gradle": b"other\n"}))

    def test_apk_hash_mismatch_stops_before_publication(self):
        plan = {"releases": [dict(self.item, sha256="0" * 64)]}
        with self.assertRaises(ERROR):
            MODULE["check_assets"](plan)

    def test_draft_is_published_only_after_both_verified_assets(self):
        api = FakeAPI(); self.publish(api, self.item, "commit")
        self.assertFalse(api.release["draft"])
        self.assertEqual(api.release["make_latest"], "true")
        self.assertEqual([e[0] for e in api.events if e[0] != "GET"], ["POST", "POST", "POST", "PATCH"])

    def test_older_release_does_not_become_latest(self):
        api = FakeAPI(); self.publish(api, dict(self.item, latest=False), "commit")
        self.assertEqual(api.release["make_latest"], "false")

    def test_second_run_does_not_duplicate_assets_or_publish_again(self):
        api = FakeAPI(); self.publish(api, self.item, "commit"); api.events.clear()
        self.publish(api, self.item, "commit")
        self.assertTrue(all(e[0] == "GET" for e in api.events))

    def test_upload_interruption_keeps_draft_and_resumes(self):
        api = FakeAPI(); api.fail_sums_once = True
        with self.assertRaises(ERROR):
            self.publish(api, self.item, "commit")
        self.assertTrue(api.release["draft"])
        self.publish(api, self.item, "commit")
        self.assertFalse(api.release["draft"])
        self.assertEqual(sum(e[3] == "test.apk" for e in api.events), 1)

    def test_conflicting_asset_is_not_deleted_or_replaced(self):
        api = FakeAPI(); self.publish(api, self.item, "commit")
        api.assets[0]["digest"] = "sha256:" + "0" * 64; api.events.clear()
        with self.assertRaises(ERROR):
            self.publish(api, self.item, "commit")
        self.assertTrue(all(e[0] == "GET" for e in api.events))

    def test_existing_notes_are_not_silently_overwritten(self):
        api = FakeAPI(); self.publish(api, self.item, "commit")
        api.release["body"] = "Maintainer edited this"; api.events.clear()
        with self.assertRaises(ERROR):
            self.publish(api, self.item, "commit")
        self.assertTrue(all(e[0] == "GET" for e in api.events))

    def test_242_snapshot_keeps_old_config_and_excludes_pace(self):
        base = {"app/build.gradle": b'versionCode 11\nversionName "2.4.1-mapfix"\n',
                "app/src/main/java/com/wandertrace/Config.java": b"random=0;"}
        for name, heading in [("README.md", "## System integration and hot reload"),
                              ("README.zh-CN.md", "## 系统实现与热重载"),
                              ("README.ja.md", "## システム連携とホットリロード")]:
            base[name] = (heading + "\n").encode()
        current = {"app/build.gradle": b'versionCode 13\nversionName "2.4.3-pace"\n',
                   "app/src/main/java/com/wandertrace/Config.java": b"random=10;",
                   "app/src/main/java/com/wandertrace/Pace.java": b"new pace UI",
                   "native/step_clock.h": b"corrected clock"}
        with patch.dict(MODULE["snapshot_242"].__globals__, archive_tree=lambda *args: dict(base)):
            result = MODULE["snapshot_242"](None, {"base_commit": "base", "snapshot_242_paths": ["native/step_clock.h"]}, current)
        self.assertIn(b"versionCode 12", result["app/build.gradle"])
        self.assertEqual(result["app/src/main/java/com/wandertrace/Config.java"], b"random=0;")
        self.assertNotIn("app/src/main/java/com/wandertrace/Pace.java", result)
        self.assertIn(b"versionCode 13", current["app/build.gradle"])


if __name__ == "__main__":
    unittest.main()
