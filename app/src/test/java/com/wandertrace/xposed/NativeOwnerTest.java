package com.wandertrace.xposed;

import static org.junit.Assert.*;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class NativeOwnerTest {
    private void nativeDirectory(Context app,String folder) throws Exception {
        var info=app.getPackageManager().getPackageInfo(app.getPackageName(),android.content.pm.PackageManager.GET_META_DATA);
        info.applicationInfo.nativeLibraryDir=folder;
        org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowPackageManager>extract(app.getPackageManager()).installPackage(info);
    }
    public static final class Owner {
        static String hash;
        static long counter;
        public static int abi() {return 1;}
        public static String initialize(Context c,String file) throws Exception {
            if (hash==null) hash=com.wandertrace.bridge.NativeBridge.sha256(file);
            return "ready";
        }
        public static String fingerprint() {return hash;}
        public static String sha256(String file) throws Exception {return com.wandertrace.bridge.NativeBridge.sha256(file);}
        public static String tick(boolean active,double cadence,long session) {if (active) counter++;return String.valueOf(counter);}
    }
    @Test public void newFacadeReusesResidentOwnerAndCounter() throws Exception {
        var app=RuntimeEnvironment.getApplication();var info=app.getPackageManager().getApplicationInfo(app.getPackageName(),0);
        var folder=Files.createTempDirectory("wander-bridge");Files.write(folder.resolve("libwander_native.so"),new byte[]{1,2,3});nativeDirectory(app,folder.toString());Owner.hash=null;Owner.counter=100;
        NativeSensors old=new NativeSensors();old.adopt(Owner.class);old.install(app,"unused");assertTrue(old.error,old.loaded);assertEquals("101",old.tick(true,120,1));
        NativeSensors next=new NativeSensors();next.adopt(old.owner());next.install(app,"unused");assertTrue(next.error,next.loaded);assertSame(old.owner(),next.owner());assertEquals("102",next.tick(true,120,1));
    }
    @Test public void changedNativeBinaryIsRejectedBeforeJavaGenerationRetires() throws Exception {
        var app=RuntimeEnvironment.getApplication();var info=app.getPackageManager().getApplicationInfo(app.getPackageName(),0);
        var folder=Files.createTempDirectory("wander-native-compat");var library=folder.resolve("libwander_native.so");Files.write(library,new byte[]{1,2,3});nativeDirectory(app,folder.toString());Owner.hash=null;
        NativeSensors sensors=new NativeSensors();sensors.adopt(Owner.class);sensors.install(app,"unused");assertTrue(sensors.error,sensors.loaded);assertTrue(sensors.compatible(app));
        Files.write(library,new byte[]{4,5,6});assertFalse(sensors.compatible(app));assertTrue("resident owner is still loaded",sensors.loaded);
    }
}
