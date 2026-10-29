package com.wandertrace.xposed;

import android.util.Log;
import java.util.Map;
import io.github.libxposed.api.XposedModule;

/** Single API 102 entry, scoped only to system_server. */
public final class XposedInit extends XposedModule {
    private SystemHooker hooks;
    @Override public void onSystemServerStarting(SystemServerStartingParam param) {
        hooks=new SystemHooker(this,param.getClassLoader());
        hooks.install(getModuleApplicationInfo().sourceDir,null,false);
    }
    @Override public boolean onHotReloading(HotReloadingParam param) {
        if (hooks==null) return false;
        boolean paused=false;
        try {
            if (!hooks.quiesce()) return false;
            paused=true;
            if (!hooks.nativeCompatible()) {
                hooks.resumeAfterRefusedReload();paused=false;
                hooks.error("原生桥文件或 ABI 已改变，拒绝进程内替换；原模块继续运行");return false;
            }
            Map<String,Object> state=hooks.saveState();
            param.setSavedInstanceState(state);
            return true;
        } catch (Throwable failure) {
            if (paused) hooks.resumeAfterRefusedReload();
            hooks.error("热重载准备失败："+failure);return false;
        }
    }
    @Override public void onHotReloaded(HotReloadedParam param) {
        if (!param.isSystemServer() || !(param.getSavedInstanceState() instanceof Map<?,?>)) throw new IllegalStateException("missing system reload state");
        Map<?,?> state=(Map<?,?>)param.getSavedInstanceState();
        if (!(state.get("loader") instanceof ClassLoader)) throw new IllegalStateException("system class loader missing");
        hooks=new SystemHooker(this,(ClassLoader)state.get("loader"));hooks.restore(state);
        hooks.install(getModuleApplicationInfo().sourceDir,param.getOldHookHandles(),true);
        log(Log.INFO,"WanderTrace","system module hot reloaded in PID "+android.os.Process.myPid());
    }
}
