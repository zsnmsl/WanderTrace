package com.wandertrace.bridge;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Process-lifetime native owner, loaded in an isolated loader whose parent is the system loader.
 * No module-generation classes, callbacks or Java worker threads may be referenced here.
 */
public final class NativeBridge {
    public static final int ABI=1;
    private static boolean loaded;
    private static String fingerprint="", error="not initialized";
    private NativeBridge() {}
    public static synchronized String initialize(Context context,String path) {
        if (loaded) return "ready";
        try {
            File library=new File(path);
            fingerprint=sha256(path);
            System.load(library.getAbsolutePath());
            ArrayList<Integer> handles=new ArrayList<>(),types=new ArrayList<>();
            Method getHandle=Sensor.class.getDeclaredMethod("getHandle"); getHandle.setAccessible(true);
            SensorManager manager=(SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
            for (Sensor sensor:manager.getSensorList(Sensor.TYPE_ALL)) {
                if ((sensor.getType()==Sensor.TYPE_STEP_COUNTER || sensor.getType()==Sensor.TYPE_STEP_DETECTOR) && !sensor.isWakeUpSensor()) {
                    handles.add((Integer)getHandle.invoke(sensor)); types.add(sensor.getType());
                }
            }
            int[] h=new int[handles.size()],t=new int[handles.size()];
            for (int i=0;i<h.length;i++) { h[i]=handles.get(i); t[i]=types.get(i); }
            error=nativeInstall(h,t); loaded="ready".equals(error);
        } catch (Throwable failure) { error=failure.toString(); }
        return error;
    }
    public static synchronized String fingerprint() { return fingerprint; }
    public static int abi() { return ABI; }
    public static String tick(boolean active,double cadence,long session) {
        return loaded ? nativeTick(active,cadence,session) : error;
    }
    public static String sha256(String path) throws Exception {
        MessageDigest hash=MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in=Files.newInputStream(new File(path).toPath())) {
            byte[] bytes=new byte[32768];int count;
            while ((count=in.read(bytes))!=-1) hash.update(bytes,0,count);
        }
        StringBuilder result=new StringBuilder();
        for (byte b:hash.digest()) result.append(String.format(java.util.Locale.US,"%02x",b&255));
        return result.toString();
    }
    private static native String nativeInstall(int[] handles,int[] types);
    private static native String nativeTick(boolean enabled,double cadence,long session);
}
