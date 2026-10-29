# 入口类与 Hook 相关类不能被裁剪/混淆
-keep class com.wandertrace.xposed.** { *; }
-keep class com.wandertrace.bridge.NativeBridge { *; }
-keep class com.wandertrace.Config { *; }
-keep class com.wandertrace.RouteEngine { *; }
-keep class com.wandertrace.CoordTransform { *; }
