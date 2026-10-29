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

## Repository hygiene

Do not commit build directories, generated native libraries, APKs, local SDK paths, user data, or signing credentials. Local installable releases may be kept in the ignored `releases/` directory; publish binaries separately through GitHub Releases if desired.

Keep the Gradle Wrapper JAR tracked: it is the build launcher, not a project build output. Follow `.editorconfig` and `.gitattributes`. Preserve third-party notices and the project's GPL-3.0-only license.
