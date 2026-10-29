package com.wandertrace;

import static org.junit.Assert.*;
import android.content.pm.PackageManager;
import android.os.Looper;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class EnvironmentActivityTest {
    public static class CheckingActivity extends MainActivity {
        final AtomicInteger calls=new AtomicInteger();
        @Override protected EnvironmentStatus inspectEnvironment() {
            calls.incrementAndGet(); return EnvironmentStatus.rootFailure(EnvironmentStatus.Root.UNAVAILABLE);
        }
    }
    public static class CompatibleActivity extends CheckingActivity {
        final AtomicInteger reloads=new AtomicInteger();
        @Override protected EnvironmentStatus inspectEnvironment() {
            calls.incrementAndGet();
            String before="{\"system\":true,\"pid\":3311,\"elapsedMs\":98000}";
            String after="{\"system\":true,\"pid\":3311,\"elapsedMs\":99000,\"locationHook\":true,\"sensorHook\":true,\"providers\":4}";
            return EnvironmentStatus.parse(0,"WT_UID=0\nWT_SYSTEM_PID=3311\nWT_STATUS_OWNER=1000\n"
                    +"WT_PREVIOUS_STATUS_BEGIN\n"+before+"\nWT_PREVIOUS_STATUS_END\nWT_STATUS_BEGIN\n"+after+"\nWT_STATUS_END\n",100000,BuildConfig.VERSION_CODE);
        }
        @Override protected HotReloadController.Result requestHotReload() {
            reloads.incrementAndGet();return new HotReloadController.Result(false,"测试框架不支持热重载");
        }
    }
    @Before public void resetPrefs() {
        RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0).edit().clear()
                .putBoolean("map_setup_complete",true).putInt("map_setup_schema",MapSetup.SCHEMA).putString("map_provider","osm").commit();
    }
    private void awaitCheck(CheckingActivity activity,int expected) throws Exception {
        for (int i=0;i<100;i++) {
            org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
            if (activity.calls.get() >= expected && !((TextView)activity.findViewById(R.id.tvEnvironment)).getText().toString().startsWith("正在检查")) return;
            Thread.sleep(10);
        }
        fail("environment check did not finish");
    }
    @Test public void checksEachForegroundEntryWithoutManualEnvironmentSectionOrApplyingSimulation() throws Exception {
        var controller=Robolectric.buildActivity(CheckingActivity.class).setup(); CheckingActivity activity=controller.get();
        awaitCheck(activity,1); assertEquals(1,activity.calls.get());
        assertTrue(((TextView)activity.findViewById(R.id.tvEnvironment)).getText().toString().contains("未获得 ROOT"));
        controller.pause().stop().start().resume().visible(); awaitCheck(activity,2); assertEquals(2,activity.calls.get());
        activity.findViewById(R.id.tvEnvironment).performClick();
        assertEquals(android.view.View.GONE,activity.findViewById(R.id.pageSettings).getVisibility());
        org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().dismiss();
        assertEquals(0,activity.getResources().getIdentifier("btnRoot","id",activity.getPackageName()));
        assertNull(activity.getSharedPreferences(com.wandertrace.Config.PREFS,0).getString("config_json",null));
        assertFalse(com.wandertrace.Config.load(activity).enabled);
        controller.pause().stop().destroy();
    }
    @Test public void modernModuleMetadataRecommendsOnlySystemFramework() throws Exception {
        var app=RuntimeEnvironment.getApplication();
        var info=app.getPackageManager().getApplicationInfo(app.getPackageName(),PackageManager.GET_META_DATA);
        assertFalse(info.metaData.containsKey("xposedmodule"));
        try(var scope=MainActivity.class.getClassLoader().getResourceAsStream("META-INF/xposed/scope.list")) {
            assertNotNull(scope);assertEquals("system",new String(scope.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).trim());
        }
    }
    @Test public void compatibleBackendStaysUsableAndAutomaticallyRequestsReloadEachOpening() throws Exception {
        var controller=Robolectric.buildActivity(CompatibleActivity.class).setup(); var activity=controller.get();
        awaitCheck(activity,2); assertEquals(1,activity.reloads.get());
        assertTrue(((TextView)activity.findViewById(R.id.tvEnvironment)).getText().toString().contains("兼容后端已就绪"));
        assertTrue(activity.findViewById(R.id.btnStart).isEnabled());
        controller.pause().stop().start().resume().visible(); awaitCheck(activity,4); assertEquals(2,activity.reloads.get());
        assertNull(activity.getSharedPreferences(com.wandertrace.Config.PREFS,0).getString("config_json",null));
        assertFalse(com.wandertrace.Config.load(activity).enabled);
        controller.pause().stop().destroy();
    }
}
