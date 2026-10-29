package com.wandertrace.xposed;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import com.wandertrace.Config;
import com.wandertrace.RootController;
import com.wandertrace.RouteEngine;
import com.wandertrace.SystemIdentity;
import com.wandertrace.BuildConfig;
import org.json.JSONObject;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/** One Java generation. Native callbacks live in a separate resident bridge. */
final class SystemHooker {
    private final XposedModule api;
    private final ClassLoader loader;
    private CopyOnWriteArrayList<Object> managers=new CopyOnWriteArrayList<>();
    private final NativeSensors sensors=new NativeSensors();
    private final Object tickLock=new Object();
    private Class<?> resultClass;
    private volatile boolean retiring;
    private volatile long workerToken;
    private HandlerThread worker;
    private Handler handler;
    private boolean reportHook,sensorAttempted;
    private boolean resumeWorker,deferHotStart;
    private long reports,previousReload,previousReport,previousStatus,reloadCount;
    private String lastError="",moduleApk;
    private Context context;
    private final String generationId=java.util.UUID.randomUUID().toString();
    private static final String BOOT_ID=SystemIdentity.read("/proc/sys/kernel/random/boot_id");
    private static final String PROCESS_START=SystemIdentity.processStartTicks(SystemIdentity.read("/proc/self/stat"));
    SystemHooker(XposedModule api,ClassLoader loader) {this.api=api;this.loader=loader;}
    void error(String message) {lastError=message;api.log(Log.WARN,"WanderTrace",message);}
    void install(String apk,List<XposedInterface.HookHandle> previous,boolean hot) {
        moduleApk=apk;
        if (new File(RootController.DIR,"disable").exists()) throw new IllegalStateException("recovery disable flag present");
        Map<String,XposedInterface.HookHandle> old=new HashMap<>();
        if (previous!=null) for (XposedInterface.HookHandle h:previous) old.put(h.getId(),h);
        try {
            SystemState.reload();resultClass=Class.forName("android.location.LocationResult",false,loader);
            Class<?> provider=Class.forName("com.android.server.location.provider.LocationProviderManager",false,loader);
            for (Constructor<?> c:provider.getDeclaredConstructors()) register(c,old,chain -> {Object r=chain.proceed();managers.addIfAbsent(chain.getThisObject());return r;});
            for (Method m:provider.getDeclaredMethods()) {
                if (m.getName().equals("onReportLocation") && m.getParameterTypes().length==1 && m.getParameterTypes()[0]==resultClass) {
                    register(m,old,chain -> {
                        if (!SystemState.config.enabled) return chain.proceed();
                        reports++;return chain.proceed(new Object[]{locationResult(location(providerName(chain.getThisObject())))});
                    });reportHook=true;
                } else if (m.getName().equals("getLastLocation") && Location.class.isAssignableFrom(m.getReturnType())) {
                    register(m,old,chain -> {Object real=chain.proceed();return SystemState.config.enabled ? location(providerName(chain.getThisObject())) : real;});
                }
            }
            if (!reportHook) throw new IllegalStateException("location report hook point missing");
            if (!hot) {
                Class<?> server=Class.forName("com.android.server.SystemServer",false,loader);
                for (Method m:server.getDeclaredMethods()) if (m.getName().equals("startOtherServices")) register(m,old,chain -> {Object r=chain.proceed();start(5000);return r;});
            }
            for (XposedInterface.HookHandle h:old.values()) h.unhook();
            if (hot) {reloadCount++;start(deferHotStart ? 5000 : 0);}
        } catch (Throwable failure) {error("系统 Hook 安装失败："+failure);throw new IllegalStateException("system hook installation failed",failure);}
    }
    private void register(Executable executable,Map<String,XposedInterface.HookHandle> old,XposedInterface.Hooker hooker) {
        executable.setAccessible(true);String id=executable.toString();XposedInterface.HookHandle prior=old.remove(id);
        if (prior!=null) prior.replaceHook(hooker);
        else api.hook(executable).setId(id).setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(hooker);
    }
    private String providerName(Object manager) {try {return (String)Reflect.field(manager,"mName");}catch (Throwable ignored) {return "gps";}}
    private Object locationResult(Location l) throws Throwable {return Reflect.call(resultClass,"wrap",(Object)new Location[]{l});}
    private Location location(String provider) {
        RouteEngine.Snap s=RouteEngine.compute(SystemState.config,System.currentTimeMillis());Location l=new Location(provider);
        l.setLatitude(s.lat);l.setLongitude(s.lon);l.setAltitude(s.alt);l.setAccuracy((float)s.accuracy);l.setSpeed((float)s.speedMps);l.setBearing((float)s.bearing);
        l.setTime(System.currentTimeMillis());l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        l.setVerticalAccuracyMeters((float)s.accuracy);l.setSpeedAccuracyMetersPerSecond(0.2f);l.setBearingAccuracyDegrees(1);
        Bundle extras=new Bundle();extras.putInt("satellites",12);l.setExtras(extras);return l;
    }
    private Object managerLock(Object manager) throws Exception {
        for (String name:new String[]{"mMultiplexerLock","mLock"}) try {Object lock=Reflect.field(manager,name);if (lock!=null) return lock;}catch (Exception ignored) {}
        throw new IllegalStateException("provider lock unavailable");
    }
    private Context systemContext() throws Throwable {
        if (context==null) {Class<?> at=Class.forName("android.app.ActivityThread");context=(Context)Reflect.call(Reflect.call(at,"currentActivityThread"),"getSystemContext");}return context;
    }
    boolean nativeCompatible() throws Throwable {return sensors.compatible(systemContext());}
    Map<String,Object> saveState() {
        Map<String,Object> state=new LinkedHashMap<>();state.put("loader",loader);state.put("managers",managers);state.put("nativeOwner",sensors.owner());
        state.put("reports",reports);state.put("reloadCount",reloadCount);state.put("started",resumeWorker);return state;
    }
    @SuppressWarnings("unchecked") void restore(Map<?,?> state) {
        if (state.get("managers") instanceof CopyOnWriteArrayList<?>) managers=(CopyOnWriteArrayList<Object>)state.get("managers");
        sensors.adopt(state.get("nativeOwner"));reports=state.get("reports") instanceof Long ? (Long)state.get("reports") : 0;
        reloadCount=state.get("reloadCount") instanceof Long ? (Long)state.get("reloadCount") : 0;
        deferHotStart=!Boolean.TRUE.equals(state.get("started"));
    }
    /** Native ownership remains resident; only this generation's worker is stopped. */
    boolean quiesce() throws InterruptedException {
        HandlerThread thread=worker;resumeWorker=thread!=null && thread.isAlive();
        if (!resumeWorker) {retiring=true;return true;}
        retiring=true;CountDownLatch idle=new CountDownLatch(1);handler.post(idle::countDown);
        if (!idle.await(2,TimeUnit.SECONDS)) {retiring=false;error("工作线程繁忙，保留原模块");return false;}
        thread.quitSafely();thread.join(1000);
        if (thread.isAlive()) {retiring=false;startReplacementWorker();error("工作线程未退出，拒绝热重载");return false;}
        handler.removeCallbacksAndMessages(null);handler=null;worker=null;context=null;return true;
    }
    void resumeAfterRefusedReload() {retiring=false;if (resumeWorker) start(0);}
    private synchronized void start(long delay) {if (!retiring && worker==null) startWorker(delay);}
    private synchronized void startReplacementWorker() {startWorker(0);}
    private void startWorker(long delay) {
        retiring=false;long token=++workerToken;worker=new HandlerThread("wander-system-v"+BuildConfig.VERSION_CODE);
        worker.setContextClassLoader(ClassLoader.getSystemClassLoader());worker.start();handler=new Handler(worker.getLooper());Handler own=handler;
        own.postDelayed(new Runnable() {
            @Override public void run() {
                if (retiring || token!=workerToken) return;
                synchronized (tickLock) {if (retiring || token!=workerToken) return;try {update();}catch (Throwable failure) {lastError=failure.toString();}}
                if (!retiring && token==workerToken) own.postDelayed(this,100);
            }
        },delay);
    }
    private void update() throws Throwable {
        long now=SystemClock.elapsedRealtime();if (now-previousReload>=250) {SystemState.reload();previousReload=now;}
        if (!sensorAttempted) {sensors.install(systemContext(),moduleApk);sensorAttempted=true;}
        Config c=SystemState.config;double cadence=c.cadenceMode==2 ? c.cadenceSpm : c.cadenceMode==1 ? Math.min(220,RouteEngine.compute(c,System.currentTimeMillis()).speedMps*80) : 0;
        String sensor=sensors.tick(c.enabled && c.cadenceMode!=0,cadence,c.startTime);
        if (c.enabled && now-previousReport>=Math.max(200,c.intervalMs)) {
            for (Object manager:managers) {String name=providerName(manager);if ("passive".equals(name)) continue;
                try {synchronized (managerLock(manager)) {Reflect.call(manager,"onReportLocation",locationResult(location(name)));}}catch (Throwable failure) {lastError=name+": "+failure;}}
            previousReport=now;
        }
        if (now-previousStatus>=1000) {writeStatus(now,c,sensor);previousStatus=now;}
    }
    private void writeStatus(long now,Config c,String sensor) throws Exception {
        JSONObject s=new JSONObject();s.put("pid",android.os.Process.myPid());s.put("system",true);s.put("bootId",BOOT_ID);s.put("processStartTicks",PROCESS_START);
        s.put("moduleVersionCode",BuildConfig.VERSION_CODE);s.put("frameworkApi",api.getApiVersion());s.put("frameworkName",api.getFrameworkName());
        s.put("configSchema",Config.SYSTEM_CONFIG_SCHEMA);
        s.put("hotReload",true);s.put("reloadCount",reloadCount);s.put("generationId",generationId);s.put("nativeBridgeAbi",NativeSensors.BRIDGE_ABI);
        s.put("locationHook",reportHook);s.put("providers",managers.size());s.put("reports",reports);s.put("enabled",c.enabled);s.put("sensorHook",sensors.loaded);
        s.put("sensor",sensor);s.put("error",lastError);s.put("elapsedMs",now);File dir=new File(RootController.DIR);
        if (dir.isDirectory()) {File tmp=new File(dir,"status.json.tmp"),target=new File(dir,"status.json");Files.write(tmp.toPath(),s.toString().getBytes(StandardCharsets.UTF_8));
            try {Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {Files.move(tmp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING);}}
    }
}
