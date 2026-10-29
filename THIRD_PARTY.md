# 第三方组件与复用记录

## 地图投影代码：osmdroid

- 上游项目：<https://github.com/osmdroid/osmdroid>
- 固定提交：`b491ee4b70dc8bf1e51493fca2c5acc8d02d491a`
- 原文件：`osmdroid-android/src/main/java/org/osmdroid/util/TileSystemWebMercator.java`
- 作者声明：Fabrice Fontaine。
- 许可证：Apache License 2.0，全文随 APK 放在 `assets/licenses/osmdroid-Apache-2.0.txt`。
- 本项目复用范围：`MapProjection.worldY` 和 `latitude` 的 Web Mercator 正反投影公式。
- 修改：改为像素单位，增加纬度钳制；焦点保持和双指手势累计逻辑由本项目实现。

检索时也查看了 FakeTraveler、Android-Mock-Location 和高德地图手势示例。未复制这些项目的定位服务、官方模拟定位或 Hook 代码。

## 地图渲染：Leaflet

- 上游：<https://github.com/Leaflet/Leaflet>，固定版本 1.9.4。
- 作者：Volodymyr Agafonkin（2010–2023）、CloudMade（2010–2011）。
- 许可证：BSD-2-Clause，完整文本在 `app/src/main/assets/licenses/Leaflet-BSD-2-Clause.txt`。
- 官方发行的 `leaflet.js`、`leaflet.css` 保存在 `app/src/main/assets/map/`，本地加载。地图图层、相机桥接、路线显示和状态处理由本项目的 `map.js` 与 Java 适配器实现。

当前项目不包含高德 Android SDK 二进制，也不需要 Android SDK Key，配置见 [单 Web Key 说明](docs/WEB_SERVICE_KEYS.md)。

## 瓦片地址模板：Leaflet.ChineseTmsProviders

- 上游：<https://github.com/htoooth/Leaflet.ChineseTmsProviders>
- 固定提交：`bafcb9a4c76ca702e02c81ddd25e986ab667ec88`，文件 `src/leaflet.ChineseTmsProviders.js`。
- 作者与许可：Tao Huang，2014，MIT。完整许可随 APK 保存于 `assets/licenses/Leaflet.ChineseTmsProviders-MIT.txt`。
- 复用高德道路 / 卫星 / 注记和腾讯瓦片 URL 模板，改为 Java HTTPS 实现；腾讯翻转 Y 和卫星分组目录在本项目计算，普通道路样式改为 1，卫星注记样式为 2。
- 没有复制或使用上游附带的演示 Key，也没有复制其百度投影代码。百度选项仅调用百度 Web 地理编码，底图明确使用高德瓦片。
- MIT 许可覆盖代码，不转授地图数据与在线服务的许可。保留相应地图来源署名；公开瓦片不等同厂商官方 Web 服务接口的稳定性承诺。

## 地图数据与在线服务

- OpenStreetMap 数据归贡献者所有，版权说明：<https://www.openstreetmap.org/copyright>；公共瓦片使用政策：<https://operations.osmfoundation.org/policies/tiles/>。
- 天地图地图服务使用用户提供的 `tk`，叠加矢量底图与地名注记，保留底图署名；服务与开发许可：<https://lbs.tianditu.gov.cn/>。
- 高德、腾讯、百度的 Web 搜索分别使用用户自己的 Web 服务 Key / AK，每次仅配置当前服务的一把；天地图同一 tk 用于瓦片和搜索。道路 / 影像图层通过相应来源的公开服务获取，保留署名。

## 原生 Hook 与测试

- Dobby 的固定版本、源码与 Apache-2.0 许可见 [native/THIRD_PARTY.md](native/THIRD_PARTY.md)。
- libxposed API 102 仅作为编译 / 测试期接口，运行时由框架提供；libxposed Service 102 与 IPC 接口随应用提供。它们遵循 Apache-2.0，官方来源与哈希见 [热重载说明](docs/HOT_RELOAD.md)，许可文本保留在 APK 的 `assets/licenses/libxposed-*-Apache-2.0.txt`。
- JUnit 与 Robolectric 仅用于本地单元测试，不进入 Release APK。
