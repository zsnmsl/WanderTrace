# 游迹 · WanderTrace

[English](README.md) · **简体中文** · [日本語](README.ja.md)

基于 ROOT 与 LSPosed 的 Android 系统全局定位、路线和计步模拟模块。在地图上选择位置、绘制路线，并在同一应用内设置移动速度和步频。

| 项目 | 内容 |
| --- | --- |
| 应用名称 | 游迹 · WanderTrace |
| 应用包名 | `com.wandertrace` |
| 原生架构 | `arm64-v8a` |
| 最低安装版本 | Android 8.1 / API 27 |
| 框架接口 | libxposed API 102 |
| 项目许可 | GPL-3.0-only |

## 功能

- **单点模拟**：拖动地图，将中心标记对准目标后开始，无需手动填写经纬度。
- **路线模拟**：按地图中心添加多个节点，自动保存草稿，并支持保存、载入和删除多条命名路线。
- **出行预设**：步行、慢跑、跑步、自行车、驾车和自定义；选择后带入建议速度，仍以 **m/s** 输入，并实时显示**分/公里**配速。
- **平滑速度随机**：设置基准速度 **0–50%** 的随机范围，按本次运行生成多时间尺度的线性变化。新配置默认 10%，已保存的 0% 仍为匀速；公里配速随曲线变化，不再复用固定的 640 秒表。
- **步频模拟**：可关闭、按速度自适应或自定义步/分钟，步频变化具有斜率限制。
- **GPS 抖动**：在 **1–3 米**半径内，结合连续慢漂移和较小的短期变化；实现及验证边界见[运动模型说明](docs/MOTION_MODEL.md)（英文）。
- **地图操作**：道路图与卫星图、地点搜索、双指连续缩放和中心选点，Leaflet 渲染内核本地打包。
- **统一启停**：路线开始对话框集中设置出行方式、速度、步频和循环；运行后，原开始按钮变为红色停止按钮。
- **自动检查**：每次打开或返回前台时检查本应用 ROOT 授权和 LSPosed 状态。

## 运行条件

- 已获得 ROOT 的 **arm64** Android 设备，并在 ROOT 管理器中授权游迹本身。
- 提供 **libxposed API 102** 的 LSPosed 或兼容框架。
- 启用模块，并选择推荐的**系统框架**作用域，对应声明为 `system`。
- 地图图块和在线地点搜索需要网络。

Android 8.1 仅表示 APK 的最低安装版本，不代表全部 ROM 均兼容。系统定位接口和原生 SensorService ABI 仍需与实现匹配。普通应用无需加入模块作用域，但其定位和身体活动等 Android 权限仍然有效。

## 安装与使用

1. 按下文步骤构建 APK 并安装。
2. 在 ROOT 管理器中允许**游迹 · WanderTrace**获取 ROOT。
3. 在 LSPosed 启用模块，并选择推荐的**系统框架**作用域。框架需要先在目标进程加载模块，模拟才能生效。
4. 首次启动选择地图服务并完成配置；高德仅需填写**一把 Web 服务 Key**。
5. 使用底部的**单点模拟 / 路线模拟 / 设置**三个标签页。

单点模式下，拖动地图使中心标记对准目标，点击**开始模拟**。路线模式下，在每个目标位置点击**添加选点**；至少添加两个点后点击**开始模拟**，在弹出的对话框中设置移动参数。直接轻触地图不会添加节点，编辑或载入已保存的路线也不会自动开始模拟。

在**设置**中切换地图服务、图层，调整抖动等定位选项。点击红色**停止模拟**按钮停止输出。循环路线到终点后会跳回起点，需要连续循环时应绘制闭合路线。

如果之前安装的版本使用其他包名，新包无法直接覆盖它。请先停止旧应用的模拟并停用旧模块，再为 `com.wandertrace` 授权和启用，避免同时启用两份模块。

## 单 Key 地图配置

首次向导和设置均只显示当前服务的一项凭据。**高德只需 Web 服务 Key，无需 Android SDK Key 或证书 SHA-1 配置。**

| 服务 | 唯一凭据 | 地图图层 | 地点搜索 |
| --- | --- | --- | --- |
| 高德地图 | 高德 Web 服务 Key | 高德公开道路 / 卫星瓦片及注记 | 高德地理编码 |
| 腾讯地图 | 腾讯 WebService Key | 腾讯公开道路 / 卫星瓦片及注记 | 腾讯地理编码 |
| 百度搜索 · 高德底图 | 百度服务端 AK | 高德道路 / 卫星瓦片 | 百度地理编码，返回 GCJ-02 |
| 天地图 | 天地图服务端 `tk` | 同一 `tk` 的道路 / 卫星 WMTS 图层及注记 | 天地图地理编码 |
| OpenStreetMap | 底图免 Key | 道路图 | 可选一把高德 Web 服务 Key 启用搜索 |

不同厂商的 Key 不能互换，只需配置正在使用的服务；切换服务时各自保存凭据。在线搜索需有对应的地理编码权限和可用配额，不支持额外要求 `sig`、`sn` 或 `SK` 签名密钥的请求配置。

高德 Key 用于搜索，公开底图请求不携带该 Key，也不初始化 Android SDK。公开瓦片接口可能独立于 Key 有效性发生变化。高德数据请求最多到 18 级，视图可放大到 20 级；高倍缩放使用已有图像，不会增加源影像细节。

路线按 GCJ-02 保存，在 OSM、天地图等 WGS-84 底图上绘制时转换坐标。更多说明见[地图配置文档](docs/WEB_SERVICE_KEYS.md)。

## 系统实现与热重载

步频计时、发送队列、诊断指标和兼容边界见[计步实现说明](docs/STEP_SIMULATION.md)（英文）。新版本尚未完成云运动的真机验证。

定位在 `system_server` 的提供者分发层处理，步频通过原生系统传感器事件通道处理，针对非唤醒的标准 **STEP_COUNTER / STEP_DETECTOR**。模块不使用 Android 测试定位提供者或传感器数据注入模式，也不改写 GNSS 芯片或传感器固件。厂商私有健康服务、私有传感器和其他 ROM 实现需要单独验证。

路线和模拟参数在运行时读取。要在同一进程更新 Java 模块代码，还需要框架支持，且进程内已加载兼容的 API 102 入口。原生桥在进程生命周期内驻留，桥 ABI 不兼容或原生库哈希改变时会拒绝热重载。首次安装、legacy 入口迁移、更换包名和原生库替换不属于这项热重载保证。

实现不会通过终止 `system_server`、Zygote、系统界面或其他应用来回退。生命周期细节见[热重载设计](docs/HOT_RELOAD.md)。

## 从源码构建

### 工具链

- JDK **17 或 21**。
- Android SDK Platform **37.0**、Build Tools **36.0.0**。
- Android NDK **r27d**、CMake **3.18+** 和 Ninja，CMake 与 Ninja 需在 `PATH` 中。
- 仓库自带的 Gradle Wrapper **9.3.1** 和 Android Gradle Plugin **9.1.1**。

在本机、不提交的 `local.properties` 中设置 `sdk.dir`，或配置 Android SDK 环境变量。以下命令均在仓库根目录执行。**必须先构建原生库**：仓库不提交生成的 `.so`，缺少所需库时应用构建会明确报错。

### Windows / PowerShell

```powershell
# 将示例路径替换为本机 Android NDK 安装目录。
./scripts/build-native.ps1 -NdkRoot "C:/Android/android-ndk-r27d"
./gradlew.bat testDebugUnitTest assembleDebug assembleRelease
```

不传 `-NdkRoot` 时，原生脚本也可读取 `ANDROID_NDK_HOME` 或 `ANDROID_NDK_ROOT`。

### Linux / macOS

先将 `ANDROID_NDK_HOME` 设置为 NDK 安装目录，然后执行：

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

### 输出与签名

| 构建类型 | 默认输出 |
| --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| 未配置签名的 Release | `app/build/outputs/apk/release/app-release-unsigned.apk` |

需要签名的 Release 时，在运行 `assembleRelease` 前设置以下四个环境变量：

- `WANDERTRACE_RELEASE_KEYSTORE`：证书文件路径。
- `WANDERTRACE_RELEASE_STORE_PASSWORD`：证书密码。
- `WANDERTRACE_RELEASE_KEY_ALIAS`：签名别名。
- `WANDERTRACE_RELEASE_KEY_PASSWORD`：私钥密码。

使用 Python 3 检查生成的安装包；已配置签名时替换为实际 APK 路径：

```sh
python scripts/verify-release.py app/build/outputs/apk/release/app-release-unsigned.apk
```

该脚本检查模块入口、仅系统作用域、原生桥、地图资源、许可证及测试 / 探针类未进入安装包等内容，不能代替真机测试。更多说明见[构建文档](docs/BUILDING.md)。

## 目录结构与开发

贡献流程见 [CONTRIBUTING.md](CONTRIBUTING.md)（英文），`.github/` 提供 Issue 和 Pull Request 模板。

```text
app/       Android 应用、模块入口、地图资源和单元测试
native/    原生传感器实现、检查代码及固定版本 Dobby 源码
scripts/   原生构建与 Release 安装包检查
tests/     独立 Java 检查代码
docs/      补充实现与构建说明（中文）
gradle/    Gradle Wrapper
```

单元测试使用隔离数据，不携带设备采集记录，也不会打入 Release。提交时请排除生成的二进制、地图 Key、用户路线、设备日志和签名材料，并保留 Gradle Wrapper JAR 与第三方许可证。

Windows 使用 `gradlew.bat clean`、Unix 使用 `sh ./gradlew clean` 清理 Android 构建输出。原生构建目录与生成的 `app/src/main/jniLibs/` 需另行清理，相关生成路径已纳入 `.gitignore`。

## 停用与恢复

先在应用内停止模拟，再按需在 LSPosed 中停用模块。已授权的 ADB ROOT 会话可创建 `/data/misc/wandertrace/disable`，使模块在下次加载时跳过系统 Hook。该标记在加载时检查，创建它不会立即移除已安装的 Hook；恢复启用前需移除标记并重新加载模块。不要在系统模拟仍运行时直接移除应用。

## 许可与致谢

项目自有源代码按 **GNU GPL 第 3 版（仅此版本，GPL-3.0-only）** 发布，见 [LICENSE](LICENSE)。第三方组件保留各自许可：Leaflet 为 BSD-2-Clause，Leaflet.ChineseTmsProviders 为 MIT，osmdroid 派生的投影代码、Dobby 和 libxposed 为 Apache-2.0。地图数据和在线服务仍受各提供商的条款及署名要求约束。

完整复用说明见[第三方声明](THIRD_PARTY.md)及[原生依赖文档](native/THIRD_PARTY.md)（英文）；相应源码和应用资源中保留完整许可文本。
