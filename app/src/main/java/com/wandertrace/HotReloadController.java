package com.wandertrace;

import android.os.Bundle;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.HotReloadResult;

/** Official framework IPC only. Never kills system_server, zygote or any application. */
public final class HotReloadController {
    private static final AtomicBoolean registered=new AtomicBoolean();
    private static volatile XposedService service;
    private static final Object connection=new Object();
    private HotReloadController() {}
    public static void initialize() {
        if (registered.compareAndSet(false,true)) XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService current) {synchronized(connection) {service=current;connection.notifyAll();}}
            @Override public void onServiceDied(XposedService current) {if (service==current) service=null;}
        });
    }
    public record Result(boolean accepted,String message) {}
    public static Result request() {
        try {
            XposedService current;
            synchronized(connection) {
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while (service==null) {
                    long remaining=deadline-System.nanoTime();
                    if (remaining<=0) break;
                    TimeUnit.NANOSECONDS.timedWait(connection,remaining);
                }
                current=service;
            }
            if (current==null) return new Result(false,"后台热重载未完成：框架服务未连接。已验证的兼容后端仍可使用。");
            if (current.getApiVersion()<102) return new Result(false,"当前框架未提供 API 102 热重载接口。");
            HookedTarget target=null;
            for (HookedTarget candidate:current.getRunningTargets()) {
                if (candidate.getUid()==1000 && ("system_server".equals(candidate.getProcessName()) || "system".equals(candidate.getProcessName()))) {
                    if (target!=null) return new Result(false,"框架返回多个系统目标，无法安全确定热重载对象。");
                    target=candidate;
                }
            }
            if (target==null) return new Result(false,"后台热重载未完成：框架没有返回本模块的系统进程目标，继续使用已验证的兼容后端。");
            if (target.getState()==HookedTarget.State.RELOADING) return new Result(false,"框架已在热重载，请稍后重新检测。");
            CountDownLatch complete=new CountDownLatch(1);AtomicReference<HotReloadResult> result=new AtomicReference<>();
            Bundle extras=new Bundle();extras.putInt("requestedVersion",BuildConfig.VERSION_CODE);
            current.hotReloadModule(target,extras,(which,outcome) -> {result.set(outcome);complete.countDown();});
            if (!complete.await(20,TimeUnit.SECONDS)) return new Result(false,"框架尚未返回热重载结果，状态待确认；没有重启任何进程。");
            HotReloadResult outcome=result.get();
            return switch (outcome.status()) {
                case SUCCEEDED -> new Result(true,"框架已完成替换，正在核对同一进程中的新版心跳。");
                case UNSUPPORTED -> new Result(false,"框架或当前目标不支持进程内热重载，保留现有进程。");
                case IN_PROGRESS -> new Result(false,"框架正在处理另一项热重载，请稍后重新检测。");
                case PROCESS_DIED -> new Result(false,"目标进程已发生退出，不能判定为无中断热重载成功。");
                case FAILED -> new Result(false,outcome.message()==null ? "旧模块拒绝热重载：可能是原生桥改变或线程未能退出，未执行进程重启。" : "框架报告热重载失败："+outcome.message());
            };
        } catch (InterruptedException interrupted) {Thread.currentThread().interrupt();return new Result(false,"热重载等待已取消，状态待确认。");}
        catch (Exception failure) {return new Result(false,"热重载服务不可用："+failure.getClass().getSimpleName());}
    }
    static boolean confirmed(EnvironmentStatus before,EnvironmentStatus after) {
        return before!=null && after!=null && before.hotReloadCapable && before.systemPid>0 && before.systemPid==after.systemPid
                && before.processIdentity.equals(after.processIdentity) && !before.processIdentity.isEmpty()
                && !before.generationId.isEmpty() && !after.generationId.isEmpty() && !before.generationId.equals(after.generationId)
                && after.moduleLive && after.currentModule && after.hotReloadCapable;
    }
}
