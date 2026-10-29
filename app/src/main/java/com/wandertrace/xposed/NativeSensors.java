package com.wandertrace.xposed;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import com.wandertrace.Config;
import dalvik.system.DexClassLoader;
import java.io.File;
import java.lang.reflect.Method;

/** Reloadable facade; the native owner has a separate process-lifetime class loader. */
final class NativeSensors {
    static final int BRIDGE_ABI=1;
    private Class<?> owner;
    private Method tick;
    boolean loaded;
    String error="not initialized";
    void install(Context context,String apk) {
        try {
            ApplicationInfo app=context.getPackageManager().getApplicationInfo(Config.PKG,0);
            if (owner==null) {
                ClassLoader loader=new DexClassLoader(apk,null,app.nativeLibraryDir,ClassLoader.getSystemClassLoader());
                owner=Class.forName("com.wandertrace.bridge.NativeBridge",true,loader);
                if (owner.getClassLoader()==NativeSensors.class.getClassLoader()) throw new IllegalStateException("native owner must be isolated");
            }
            if ((Integer)owner.getMethod("abi").invoke(null)!=BRIDGE_ABI) throw new IllegalStateException("native bridge ABI changed");
            error=(String)owner.getMethod("initialize",Context.class,String.class).invoke(null,context,new File(app.nativeLibraryDir,"libwander_native.so").getAbsolutePath());
            loaded="ready".equals(error);tick=owner.getMethod("tick",boolean.class,double.class,long.class);
        } catch (Throwable failure) { loaded=false;error=failure.toString(); }
    }
    void adopt(Object saved) { if (saved instanceof Class<?>) owner=(Class<?>)saved; }
    Object owner() { return owner; }
    String tick(boolean enabled,double cadence,long session) throws Exception { return loaded ? (String)tick.invoke(null,enabled,cadence,session) : error; }
    boolean compatible(Context context) throws Exception {
        if (owner==null) return true;
        ApplicationInfo app=context.getPackageManager().getApplicationInfo(Config.PKG,android.content.pm.PackageManager.GET_META_DATA);
        if (app.metaData==null || app.metaData.getInt("wandertrace.nativeBridgeAbi",-1)!=BRIDGE_ABI) return false;
        String disk=(String)owner.getMethod("sha256",String.class).invoke(null,new File(app.nativeLibraryDir,"libwander_native.so").getAbsolutePath());
        return disk.equals(owner.getMethod("fingerprint").invoke(null));
    }
}
