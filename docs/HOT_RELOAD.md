# 进程内热重载

模块通过 libxposed API / Service 102 请求已加载 Java 模块的进程内更新，不使用终止 system_server、Zygote、系统界面或其他应用的回退方式。

## 前提

- 框架提供 API 102，且支持当前系统进程的重载请求。
- 进程内已经加载支持 `onHotReloading` 的兼容入口。首次加载、legacy 入口迁移和更换应用包名不属于该保证。
- 安装包中的原生桥 ABI 与原生库 SHA-256 保持兼容。原生库属于进程生命周期内的固定组件，不在线替换。

位置、路线、速度、抖动与步频参数由工作线程实时读取，修改这些参数不需要代码重载。

## 入口与接管

现代入口为 `com.wandertrace.xposed.XposedInit`，声明于 `app/src/main/resources/META-INF/xposed/java_init.list`。推荐作用域只有 `system`，模块属性设为 API 102 并启用自动热重载。

旧代退出前停止并等待自己的工作线程，核对原生桥兼容性，保存提供者列表、独立原生桥所有者与统计状态。退出或兼容检查失败时拒绝重载并恢复旧代。新代接管状态，Hook 使用 Hook ID 原位替换，避免重复注册。

`com.wandertrace.bridge.NativeBridge` 在独立类加载器中驻留，只引用系统类型，不持有可重载引擎的工作线程与回调。改变其协议或原生库时需重新评估桥 ABI 和加载条件，不能按普通 Java 更新处理。

首次安装或更换包名需重新在 ROOT 管理器授权、启用新模块并选择系统框架。不要同时启用旧包与新包；更换应用身份不等同覆盖安装热重载。

官方接口：[模块生命周期](https://github.com/libxposed/api/blob/master/api/src/main/java/io/github/libxposed/api/XposedModuleInterface.java)、[Hook 接口](https://github.com/libxposed/api/blob/master/api/src/main/java/io/github/libxposed/api/XposedInterface.java)、[框架服务](https://github.com/libxposed/service/blob/master/service/src/main/java/io/github/libxposed/service/XposedService.java)。API 仅用于编译与测试，Service 及其 IPC 接口随应用提供；Apache-2.0 许可保留在应用 assets 中。
