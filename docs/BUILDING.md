# 构建游迹

## 工具

- JDK 17 或 21。
- Android SDK Platform 37.0，Build Tools 36.0.0。
- Android NDK r27d、CMake 3.18 及以上、Ninja；CMake 和 Ninja 需在 PATH 中。
- 项目使用 Gradle Wrapper 9.3.1、Android Gradle Plugin 9.1.1，依赖从 Google Maven / Maven Central 获取。

在本机创建不提交的 `local.properties`，设置 `sdk.dir`，也可配置 Android SDK 环境变量。不要提交本机目录、地图 Key 或发布证书。

## Windows / PowerShell

```powershell
# 路径替换为本机 NDK 安装目录；也可设置 ANDROID_NDK_HOME。
./scripts/build-native.ps1 -NdkRoot <NDK 安装目录>
./gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

原生脚本将 `native/wander_native.cpp` 与仓库内固定版本的 Dobby 源码编译为 `app/src/main/jniLibs/arm64-v8a/libwander_native.so`。原生构建中间文件位于 `native/build-release/`。这两个生成目录均已忽略，构建前缺少原生库会明确报错，不会静默生成缺少计步组件的安装包。

Linux / macOS 可用同样的 CMake 选项构建，再将生成的原生库复制到上述 jniLibs 目录：

```sh
cmake -S native -B native/build-release -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-27 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build native/build-release --target wander_native
mkdir -p app/src/main/jniLibs/arm64-v8a
cp native/build-release/libwander_native.so app/src/main/jniLibs/arm64-v8a/
./gradlew testDebugUnitTest assembleDebug assembleRelease
```

## 安装包与签名

Debug 输出为 `app/build/outputs/apk/debug/app-debug.apk`。默认 Release 输出为 `app/build/outputs/apk/release/app-release-unsigned.apk`。

发布签名通过以下环境变量传入，不在源码中填写密码：

| 变量 | 内容 |
| --- | --- |
| `WANDERTRACE_RELEASE_KEYSTORE` | 本机证书文件路径 |
| `WANDERTRACE_RELEASE_STORE_PASSWORD` | 证书密码 |
| `WANDERTRACE_RELEASE_KEY_ALIAS` | 签名别名 |
| `WANDERTRACE_RELEASE_KEY_PASSWORD` | 私钥密码 |

设置四项变量后运行 `assembleRelease`。包名固定为 `com.wandertrace`，显示名为「游迹 · WanderTrace」。生成后可以检查打包内容：

```sh
python scripts/verify-release.py app/build/outputs/apk/release/app-release-unsigned.apk
```

若已配置签名，请将命令中的文件名换为实际签名 APK。

## 测试与清理

`app/src/test` 和 `tests/` 中保留测试源码；它们不进入 Release APK。测试图像在运行时生成，不携带设备截图、定位记录、账号或真实服务凭据。原生检查源码统一位于 `native/tests/`，其中 `device_abi_check.cpp` 只应在独立进程中运行。CMake 默认仅构建生产库，设置 `-DWANDERTRACE_BUILD_CHECKS=ON` 可启用检查目标；PowerShell 构建脚本会启用并编译这些目标。

运行 `./gradlew.bat clean`（Unix 为 `./gradlew clean`）清理 Android 构建输出；完整恢复源码状态时，还需删除 `.gradle/`、`native/build*/` 和 `app/src/main/jniLibs/` 等本机生成目录。Gradle Wrapper 的 jar 是构建启动文件，应保留在仓库中。
