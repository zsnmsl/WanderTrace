package com.wandertrace.xposed;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

/** Framework reflection only; no legacy Xposed helpers in an API 102 generation. */
final class Reflect {
    static Object field(Object target,String name) throws Exception {
        for (Class<?> c=target.getClass();c!=null;c=c.getSuperclass()) try {
            Field f=c.getDeclaredField(name); f.setAccessible(true); return f.get(target);
        } catch (NoSuchFieldException ignored) {}
        throw new NoSuchFieldException(name);
    }
    static Object call(Object target,String name,Object... args) throws Throwable {
        Class<?> type=target instanceof Class<?> ? (Class<?>)target : target.getClass();
        for (Class<?> c=type;c!=null;c=c.getSuperclass()) for (Method m:c.getDeclaredMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length!=args.length) continue;
            Class<?>[] parameters=m.getParameterTypes(); boolean matches=true;
            for (int i=0;i<args.length;i++) if (args[i]!=null && !parameters[i].isInstance(args[i])) matches=false;
            if (!matches) continue;
            m.setAccessible(true);
            try { return m.invoke(target instanceof Class<?> ? null : target,args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }
        throw new NoSuchMethodException(type.getName()+"."+name);
    }
}
