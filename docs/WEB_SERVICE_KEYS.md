# 地图与单 Key 配置

首次向导和设置均只显示当前服务的一项 Web 服务 Key。不需要 Android SDK Key、证书 SHA-1 或第二把搜索 Key。

| 地图选项 | 唯一凭据 | 道路图 / 卫星图 | 地点搜索 |
| --- | --- | --- | --- |
| 高德地图 | 高德 Web 服务 Key | 高德公开瓦片，卫星叠加道路注记 | 高德 REST 地理编码 |
| 腾讯地图 | 腾讯 WebService Key | 腾讯公开瓦片，卫星叠加道路注记 | 腾讯地理编码 |
| 百度搜索 · 高德底图 | 百度服务端 AK | 高德公开瓦片 | 百度地理编码，返回 GCJ-02 |
| 天地图 | 天地图服务端 tk | 同一 tk 的 WMTS 底图和注记 | 同一 tk 的地理编码 |
| OpenStreetMap | 底图免 Key | OSM 道路图 | 可选一把高德 Web Key 启用搜索 |

不同厂商的 Key 不能互换；每次仅需配置当前服务。高德 Web Key 用于搜索，公开底图请求不携带该 Key，不初始化 Android SDK。在线服务需有对应地理编码权限与可用配额；本版不支持另需 sig / sn / SK 的请求签名配置。

地图内核为本地打包的 Leaflet 1.9.4，支持拖动与双指连续缩放。道路与卫星来源保持原署名。高德有效数据请求最多到 18 级，显示可放大至 20 级；更高倍数放大已有图像，不会增加源影像细节。公开瓦片接口可能调整，可用性独立于 Web 服务 Key。

相机和路线按 GCJ-02 保存。OSM / 天地图使用 WGS-84 基准，切换时转换地图坐标；高德 / 腾讯按 GCJ-02 绘制。地图状态与路线持久化通过应用本地配置完成，仓库不包含用户路线和 Key。

服务说明：[高德](https://lbs.amap.com/api/webservice/gettingstarted)、[腾讯](https://lbs.qq.com/service/webService/webServiceGuide/address/Gcoder)、[百度](https://lbsyun.baidu.com/docs/webapi?title=geocoding%2Fguide%2Fwebservice-geocoding-base)、[天地图](https://lbs.tianditu.gov.cn/server/geocoding.html)。地图源码复用和许可见 [第三方声明](../THIRD_PARTY.md)。
