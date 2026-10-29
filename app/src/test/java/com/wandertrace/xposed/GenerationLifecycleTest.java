package com.wandertrace.xposed;

import static org.junit.Assert.*;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class GenerationLifecycleTest {
    public static void target() {}
    private XposedModule framework(AtomicInteger registrations) {
        XposedModule entry=new XposedModule() {};
        XposedInterface api=(XposedInterface)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{XposedInterface.class},(p,m,args) -> {
            if (m.getName().equals("hook")) return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{XposedInterface.HookBuilder.class},(builder,method,values) -> {
                if (method.getName().equals("intercept")) {registrations.incrementAndGet();return null;}
                return builder;
            });
            if (m.getName().equals("getApiVersion")) return 102;
            if (m.getReturnType()==boolean.class) return false;
            if (m.getReturnType()==long.class) return 0L;
            if (m.getReturnType()==int.class) return 0;
            return null;
        });
        // Test harness plays the framework role; production modules never call attachFramework.
        entry.attachFramework(api,() -> {});return entry;
    }
    @Test public void replacingAHookConsumesOldHandleWithoutAddingADuplicate() throws Exception {
        AtomicInteger installs=new AtomicInteger(),replacements=new AtomicInteger();XposedModule api=framework(installs);
        SystemHooker next=new SystemHooker(api,ClassLoader.getSystemClassLoader());
        Method target=getClass().getMethod("target"),register=SystemHooker.class.getDeclaredMethod("register",Executable.class,Map.class,XposedInterface.Hooker.class);register.setAccessible(true);
        XposedInterface.HookHandle old=(XposedInterface.HookHandle)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{XposedInterface.HookHandle.class},(p,m,a) -> {
            if (m.getName().equals("replaceHook")) {replacements.incrementAndGet();return p;}
            return null;
        });
        Map<String,XposedInterface.HookHandle> previous=new HashMap<>();previous.put(target.toString(),old);
        register.invoke(next,target,previous,(XposedInterface.Hooker)chain -> chain.proceed());
        assertEquals(1,replacements.get());assertEquals(0,installs.get());assertTrue(previous.isEmpty());
        register.invoke(next,target,new HashMap<>(),(XposedInterface.Hooker)chain -> chain.proceed());assertEquals(1,installs.get());
    }
    @Test public void oldWorkerExitsAndStateDoesNotRetainItsHandlersOrEntry() throws Exception {
        XposedModule api=framework(new AtomicInteger());SystemHooker old=new SystemHooker(api,ClassLoader.getSystemClassLoader());
        Method start=SystemHooker.class.getDeclaredMethod("start",long.class);start.setAccessible(true);start.invoke(old,10000L);
        var field=SystemHooker.class.getDeclaredField("worker");field.setAccessible(true);Thread worker=(Thread)field.get(old);assertTrue(worker.isAlive());
        assertTrue(old.quiesce());Map<String,Object> state=old.saveState();assertFalse(worker.isAlive());
        start.invoke(old,10000L);assertNull("in-flight old startup callback cannot restart a retired generation",field.get(old));
        for (Object item:state.values()) {
            assertFalse(item instanceof Thread);assertFalse(item instanceof android.os.Handler);assertFalse(item instanceof SystemHooker);assertFalse(item instanceof XposedModule);
        }
        SystemHooker next=new SystemHooker(api,ClassLoader.getSystemClassLoader());next.restore(state);
        assertSame(state.get("managers"),next.saveState().get("managers"));
        start.invoke(next,10000L);Thread newWorker=(Thread)field.get(next);assertNotSame(worker,newWorker);assertTrue(newWorker.isAlive());assertTrue(next.quiesce());assertFalse(newWorker.isAlive());
        next.resumeAfterRefusedReload();Thread resumed=(Thread)field.get(next);assertNotNull(resumed);assertTrue(resumed.isAlive());assertTrue(next.quiesce());assertFalse(resumed.isAlive());
    }
}
