# WanderTrace · 游迹

**English** · [简体中文](README.zh-CN.md) · [日本語](README.ja.md)

An Android module for system-wide location, route, and step simulation, powered by ROOT and LSPosed. Choose a position on the map, draw a route, and control movement speed and step cadence from one app.

| Item | Value |
| --- | --- |
| App name | 游迹 · WanderTrace |
| Package ID | `com.wandertrace` |
| Native architecture | `arm64-v8a` |
| Minimum installation version | Android 8.1 / API 27 |
| Framework API | libxposed API 102 |
| Project license | GPL-3.0-only |

## Features

- **Single-point simulation:** drag the map to position the center crosshair, then start at that location. No manual coordinate entry is needed.
- **Route simulation:** add multiple points using the center crosshair. Drafts persist locally; save, load, and delete multiple named routes.
- **Movement profiles:** walking, jogging, running, cycling, driving, and custom. Selecting a profile updates the suggested speed. The interface uses **m/s**.
- **Smooth speed variation:** choose a randomization range from **0–50%** of the base speed, with linear transitions between speed targets.
- **Step cadence:** disable step simulation, adapt cadence to speed, or set a custom cadence in steps/minute. Cadence changes gradually with a limited slope.
- **GPS jitter:** configure a jitter radius of **1–3 meters**.
- **Map controls:** road and satellite layers, place search, continuous pinch zoom, and center-based point selection. The Leaflet renderer is bundled locally.
- **Simple controls:** speed, cadence, travel profile, and looping are configured in the route start dialog. The start button becomes a red stop button while simulation is active.
- **Automatic checks:** ROOT authorization and LSPosed status are checked when the app opens or returns to the foreground.

## Requirements

- A rooted **arm64** Android device and ROOT permission granted to WanderTrace itself.
- LSPosed or a compatible framework providing **libxposed API 102**.
- The module enabled for the recommended **System Framework** scope, declared as `system`.
- Internet access for map tiles and online place search.

Android 8.1 is the APK's minimum installation version, not a guarantee that every ROM is compatible. The system location interfaces and native SensorService ABI must match the implementation. Ordinary apps do not need to be selected in the module scope; their Android location and physical activity permissions still apply.

## Setup and use

1. Build an APK using the instructions below and install it.
2. Grant **游迹 · WanderTrace** ROOT access in your ROOT manager.
3. Enable the module in LSPosed and select the recommended **System Framework** scope. The framework must load the module in the target process before simulation can work.
4. On first launch, choose a map provider and complete the map setup. For AMap, enter **one Web Service Key**.
5. Use the bottom tabs: **Single Point (单点模拟)**, **Route (路线模拟)**, and **Settings (设置)**.

For a single position, move the map until the crosshair is over the desired location, then tap **Start simulation**. For a route, tap **Add point** at each desired position; after at least two points, tap **Start simulation** and configure the movement parameters in the dialog. A tap on the map alone does not add a point. Saved routes are edited or loaded without automatically starting simulation.

Use **Settings** to switch map providers and layers, or adjust jitter and other location options. Tap the red **Stop simulation** button to stop output. A looping route jumps from the last point to the first; draw a closed route for a continuous loop.

An earlier build with a different package ID cannot be replaced by installing this package over it. Stop simulation and disable the old module first, then authorize and enable `com.wandertrace`. Avoid enabling both modules together.

## One-key map setup

The setup dialog and Settings show one credential field for the selected provider. **AMap requires only a Web Service Key, with no Android SDK Key or certificate SHA-1 configuration.**

| Provider | Credential | Map layers | Place search |
| --- | --- | --- | --- |
| AMap / Gaode | AMap Web Service Key | AMap public road/satellite tiles and labels | AMap geocoding |
| Tencent Maps | Tencent WebService Key | Tencent public road/satellite tiles and labels | Tencent geocoding |
| Baidu Search · AMap basemap | Baidu server-side AK | AMap road/satellite tiles | Baidu geocoding, returning GCJ-02 |
| Tianditu | Tianditu server-side `tk` | Road/satellite WMTS layers and labels using the same `tk` | Tianditu geocoding |
| OpenStreetMap | No key for the basemap | Road map | Optional AMap Web Service Key for search |

Keys from different providers are not interchangeable. Configure only the provider you use; saved keys are kept separately when switching providers. Online search requires the relevant geocoding permission and available quota. Requests requiring an additional `sig`, `sn`, or `SK` signing secret are not supported.

The AMap key is used for search; public basemap requests do not carry it or initialize the Android SDK. Public tile endpoints may change independently of key validity. AMap requests native tiles up to zoom 18 and displays up to zoom 20 by scaling those tiles; further magnification does not add image detail.

Routes are stored in GCJ-02. Coordinates are converted when drawing on WGS-84 basemaps such as OSM and Tianditu. Further details: [map configuration](docs/WEB_SERVICE_KEYS.md) (Chinese).

## System integration and hot reload

Location is handled at the provider distribution layer in `system_server`. Step simulation uses native system sensor event channels for non-wake-up **STEP_COUNTER / STEP_DETECTOR** sensors. The module does not use Android test location providers or sensor data injection mode, and it does not rewrite GNSS or sensor firmware. Vendor-specific health services, private sensors, and other ROM implementations require separate verification.

Changes to route and simulation parameters are read during operation. Updating Java module code in the same process additionally requires framework support and an already loaded, compatible API 102 entry point. The native bridge remains resident for the process lifetime; an incompatible bridge ABI or a changed native library hash causes hot reload to be refused. First installation, migration from a legacy entry point, package changes, and native library replacement are outside this hot reload guarantee.

The implementation does not fall back to killing `system_server`, Zygote, the system UI, or other apps. See the [hot reload design](docs/HOT_RELOAD.md) (Chinese) for lifecycle details.

## Building from source

### Toolchain

- JDK **17 or 21**.
- Android SDK Platform **37.0** and Build Tools **36.0.0**.
- Android NDK **r27d**, CMake **3.18+**, and Ninja. Make CMake and Ninja available in `PATH`.
- The included Gradle Wrapper **9.3.1** and Android Gradle Plugin **9.1.1**.

Configure your SDK path in a local, untracked `local.properties` file (`sdk.dir`) or through your Android SDK environment. Run the following commands from the repository root. **Build the native library first:** generated `.so` files are not committed, and the app build reports an error if the required library is missing.

### Windows / PowerShell

```powershell
# Replace the example path with your Android NDK installation directory.
./scripts/build-native.ps1 -NdkRoot "C:/Android/android-ndk-r27d"
./gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

The native script also accepts `ANDROID_NDK_HOME` or `ANDROID_NDK_ROOT` when `-NdkRoot` is omitted.

### Linux / macOS

Set `ANDROID_NDK_HOME` to your NDK installation, then run:

```sh
cmake -S native -B native/build-release -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-27 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build native/build-release --target wander_native
mkdir -p app/src/main/jniLibs/arm64-v8a
cp native/build-release/libwander_native.so app/src/main/jniLibs/arm64-v8a/
sh ./gradlew testDebugUnitTest assembleDebug assembleRelease
```

### Outputs and signing

| Build | Default output |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release, without signing configuration | `app/build/outputs/apk/release/app-release-unsigned.apk` |

For a signed Release, set all four environment variables before running `assembleRelease`:

- `WANDERTRACE_RELEASE_KEYSTORE`: path to your keystore.
- `WANDERTRACE_RELEASE_STORE_PASSWORD`: keystore password.
- `WANDERTRACE_RELEASE_KEY_ALIAS`: signing alias.
- `WANDERTRACE_RELEASE_KEY_PASSWORD`: private key password.

Inspect the generated package with Python 3, replacing the APK path if you configured signing:

```sh
python scripts/verify-release.py app/build/outputs/apk/release/app-release-unsigned.apk
```

This checks the module entry, system-only scope, native bridge, map assets, licenses, and exclusion of test/probe classes. It does not replace device testing. See [additional build notes](docs/BUILDING.md) (Chinese).

## Repository layout and development

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines. GitHub issue and pull request templates are provided in `.github/`.

```text
app/       Android application, module entry, map assets, and unit tests
native/    Native sensor implementation, checks, and pinned Dobby source
scripts/   Native build and release package inspection
tests/     Standalone Java checks
docs/      Additional implementation and build notes (Chinese)
gradle/    Gradle Wrapper
```

Unit tests use isolated data; they do not include captured device records and are not packaged in Release. Keep generated binaries, map keys, user routes, device logs, and signing material out of commits. Retain the Gradle Wrapper JAR and third-party license files.

`gradlew.bat clean` (Windows) or `sh ./gradlew clean` (Unix) removes Android build output. Native build directories and generated `app/src/main/jniLibs/` files need separate cleanup; generated paths are covered by `.gitignore`.

## Disabling and recovery

Stop simulation in the app, then disable the module in LSPosed when needed. To skip system hooks on the next module load, an authorized ADB ROOT session can create `/data/misc/wandertrace/disable`. This flag is checked at module load; creating it does not immediately remove already installed hooks. Remove the flag before loading the module again if you want to re-enable it. Do not remove the app while system simulation is still active.

## License and acknowledgements

Original project source is distributed under **GNU GPL version 3 only (GPL-3.0-only)**; see [LICENSE](LICENSE). Third-party components retain their own licenses: Leaflet (BSD-2-Clause), Leaflet.ChineseTmsProviders (MIT), and osmdroid-derived projection code, Dobby, and libxposed (Apache-2.0). Map data and online services remain subject to their providers' terms and attribution requirements.

See [third-party notices](THIRD_PARTY.md) (Chinese) and [native dependency details](native/THIRD_PARTY.md) (English). Full license texts are included with the relevant sources and application assets.
