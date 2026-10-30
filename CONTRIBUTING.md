# Contributing to WanderTrace

Start with the [English README](README.md), [简体中文](README.zh-CN.md), or [日本語](README.ja.md) for setup and build instructions.

## Scope and layout

- `app/src/main/`: Android app and LSPosed module implementation.
- `app/src/test/`: Android unit tests using Robolectric.
- `native/`: production native source and CMake configuration.
- `native/tests/`: native checks, built only when `WANDERTRACE_BUILD_CHECKS=ON`.
- `native/vendor/`: pinned upstream dependencies; retain licenses and avoid unrelated edits.
- `tests/`: standalone Java checks, including their isolated test stubs.
- `scripts/`: build and package verification tools.
- `docs/`: implementation notes. Keep the three root READMEs consistent when behavior changes.

## Changes and validation

Keep a pull request focused on one problem. Explain the resulting behavior, how you checked it, and any device or ROM limitations. For code changes, run the relevant checks from the README. Run `scripts/verify-release.py` against the generated APK when changing packaging or module metadata.

Framework and native changes need special care: test in the intended process and distinguish local checks from device validation. Never claim that all ROMs work based on one device. Changes to the resident native bridge must account for its ABI and hot reload compatibility.

Use the bug report template for failures and include Android version, ROM, CPU ABI, framework/API version, and reproduction steps. Remove API keys, coordinates, routes, device identifiers, and account details from any logs before sharing them.

## One-click publishing (repository owner)

On Windows, double-click `commit.bat` from the project root after editing your code. It shows the changed files and asks for a commit message, then stages non-ignored changes, commits them locally, and pushes the current branch to `https://github.com/zsnmsl/WanderTrace.git`. It does not upload the ignored local `releases/` directory. GitHub authentication uses Git's normal credential flow; no token belongs in the script.

For a read-only preview, or a custom commit message, use PowerShell:

```powershell
./scripts/publish-github.ps1 -DryRun
./scripts/publish-github.ps1 -Message "Describe your change"
./scripts/publish-github.ps1 -CommitOnly -Message "Keep this change local"
```

`-CommitOnly` never contacts GitHub or modifies remote configuration. If the working tree is clean, the default mode can push existing local commits without creating an empty commit. A blank message cancels a new commit before staging. For Chinese daily-use instructions, see [Git workflow](docs/GIT_WORKFLOW.zh-CN.md).

Missing Git author details are requested interactively and stored only in this repository. Optional `-UserName`, `-Email`, `-GitPath`, and `-Branch` parameters are available. By default, an existing repository keeps its current branch; a new repository uses `main`. The script refuses a different origin, an unexpected explicit branch, prohibited tracked files, and remote history that cannot be fast-forwarded. It never force-pushes or resets your work; resolve history differences manually before retrying.

## Repository hygiene

For the prepared 2.4.2 and 2.4.3 source commits, tags, and binary releases, use `release.bat` or `scripts/publish-releases.ps1`. Run with `-DryRun` for an offline preview. This dedicated workflow keeps historical source versions separate and publishes verified APK/checksum assets through GitHub Releases; see the [release workflow](docs/RELEASING.zh-CN.md) (Chinese) and [versioned notes](docs/release-notes/v2.4.3.md).

Do not commit build directories, generated native libraries, APKs, local SDK paths, user data, or signing credentials. Local installable releases may be kept in the ignored `releases/` directory; publish binaries separately through GitHub Releases if desired.

Keep the Gradle Wrapper JAR tracked: it is the build launcher, not a project build output. Follow `.editorconfig` and `.gitattributes`. Preserve third-party notices and the project's GPL-3.0-only license.
