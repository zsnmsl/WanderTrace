package com.wandertrace;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Spinner;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MapFlowTest {
    public static class ReadyActivity extends MainActivity {
        final AtomicInteger checks=new AtomicInteger(),writes=new AtomicInteger();
        volatile com.wandertrace.Config applied;
        @Override protected EnvironmentStatus inspectEnvironment() {
            checks.incrementAndGet();
            String status="{\"system\":true,\"pid\":3311,\"bootId\":\"boot\",\"elapsedMs\":99000,\"moduleVersionCode\":"+BuildConfig.VERSION_CODE+",\"locationHook\":true,\"sensorHook\":true,\"providers\":4}";
            return EnvironmentStatus.parse(0,"WT_UID=0\nWT_SYSTEM_PID=3311\nWT_BOOT_ID=boot\nWT_STATUS_BEGIN\n"+status+"\nWT_STATUS_END\n",100000,BuildConfig.VERSION_CODE);
        }
        @Override protected void applySimulation(com.wandertrace.Config config,boolean stopping) {
            applied=com.wandertrace.Config.fromJson(config.toJson());writes.incrementAndGet();
        }
    }
    @Before public void reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0).edit().clear()
                .putBoolean("map_setup_complete",true).putInt("map_setup_schema",MapSetup.SCHEMA).putString("map_provider","osm").commit();
    }
    private static void idle() {org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();}
    private static void await(ReadyActivity activity,int writes) throws Exception {
        for(int i=0;i<200;i++) {
            idle();
            if(activity.checks.get()>0 && activity.writes.get()>=writes && activity.findViewById(R.id.btnStart).isEnabled()
                    && !((android.widget.TextView)activity.findViewById(R.id.tvEnvironment)).getText().toString().startsWith("正在检查")) return;
            Thread.sleep(5);
        }
        fail("asynchronous UI operation did not finish");
    }
    private static void add(ReadyActivity activity,double lat,double lon) {
        ((RouteMapView)activity.findViewById(R.id.routeMap)).centerOn(lat,lon);activity.findViewById(R.id.btnAddPoint).performClick();
    }
    @Test public void singleUsesMapCenterAndSameButtonStopsWithRedBackground() throws Exception {
        var c=Robolectric.buildActivity(ReadyActivity.class).setup();var a=c.get();await(a,0);
        RouteMapView map=a.findViewById(R.id.routeMap);map.centerOn(31.23,121.47);double[] center=map.centerGcj();
        assertEquals(View.GONE,a.findViewById(R.id.btnAddPoint).getVisibility());
        Button action=a.findViewById(R.id.btnStart);action.performClick();await(a,1);
        assertEquals(center[0],a.applied.lat,1e-9);assertEquals(center[1],a.applied.lon,1e-9);
        assertEquals(0,a.applied.cadenceMode);assertTrue(a.applied.enabled);
        assertEquals("停止模拟",action.getText().toString());
        assertEquals(0xFFC44040,((GradientDrawable)action.getBackground()).getColor().getDefaultColor());
        assertEquals(2,((android.view.ViewGroup)a.findViewById(R.id.editorActions)).getChildCount());
        assertEquals(0,a.getResources().getIdentifier("btnStop","id",a.getPackageName()));
        action.performClick();await(a,2);assertFalse(a.applied.enabled);assertEquals("开始模拟",action.getText().toString());
        c.pause().stop().destroy();
    }
    @Test public void routeDialogOwnsAllMotionFieldsAndConvertsMpsOnlyOnce() throws Exception {
        var c=Robolectric.buildActivity(ReadyActivity.class).setup();var a=c.get();await(a,0);
        a.findViewById(R.id.rbRoute).performClick();add(a,39.91,116.40);add(a,39.92,116.41);
        assertNull(a.findViewById(R.id.etSpeed));assertNull(a.findViewById(R.id.rgCadence));
        a.findViewById(R.id.btnStart).performClick();idle();AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.isShowing());
        ((EditText)d.findViewById(R.id.etSpeed)).setText("3.5");
        ((EditText)d.findViewById(R.id.etSpeedRandom)).setText("35");
        ((RadioGroup)d.findViewById(R.id.rgCadence)).check(R.id.rbCadenceCustom);
        ((EditText)d.findViewById(R.id.etCadence)).setText("175");
        d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();await(a,1);
        assertFalse(d.isShowing());assertEquals(12.6,a.applied.speedKmh,1e-9);
        assertEquals(35,a.applied.speedRandomPercent,0);assertEquals(2,a.applied.cadenceMode);assertEquals(175,a.applied.cadenceSpm,0);
        assertEquals("停止模拟",((Button)a.findViewById(R.id.btnStart)).getText().toString());
        a.findViewById(R.id.btnStart).performClick();await(a,2);
        a.findViewById(R.id.btnStart).performClick();idle();d=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("3.50",((EditText)d.findViewById(R.id.etSpeed)).getText().toString());
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();c.pause().stop().destroy();
    }
    @Test public void profileSelectionChangesSpeedAndInvalidOrCancelledDialogCannotApply() throws Exception {
        var c=Robolectric.buildActivity(ReadyActivity.class).setup();var a=c.get();await(a,0);
        a.findViewById(R.id.rbRoute).performClick();add(a,39.91,116.4);add(a,39.92,116.41);
        a.findViewById(R.id.btnStart).performClick();idle();AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
        Spinner profiles=d.findViewById(R.id.spProfile);
        for(int i=0;i<5;i++) {
            profiles.setSelection(i);idle();
            assertEquals(TravelProfiles.SPEEDS[i],Double.parseDouble(((EditText)d.findViewById(R.id.etSpeed)).getText().toString()),1e-9);
            assertEquals(i<3 ? R.id.rbCadenceAuto : R.id.rbCadenceOff,((RadioGroup)d.findViewById(R.id.rgCadence)).getCheckedRadioButtonId());
        }
        ((EditText)d.findViewById(R.id.etSpeedRandom)).setText("51");d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue(d.isShowing());assertEquals(0,a.writes.get());
        d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();idle();assertFalse(com.wandertrace.Config.load(a).enabled);
        a.findViewById(R.id.btnStart).performClick();idle();d=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("0",((EditText)d.findViewById(R.id.etSpeedRandom)).getText().toString());
        d.dismiss();c.pause().stop().destroy();
    }
    @Test public void everyAddedNodeIsImmediatelyPersistedAndCameraSurvivesRecreation() throws Exception {
        var c=Robolectric.buildActivity(ReadyActivity.class).setup();var a=c.get();await(a,0);
        a.findViewById(R.id.rbRoute).performClick();
        for(int i=0;i<25;i++) add(a,39.91+i*.001,116.40+i*.001);
        var prefs=a.getSharedPreferences(com.wandertrace.Config.PREFS,0);
        assertEquals(25,com.wandertrace.Config.fromJson(prefs.getString("editor_draft",null)).points.length);
        double[] center=((RouteMapView)a.findViewById(R.id.routeMap)).centerGcj();
        c.pause().stop().destroy();
        var next=Robolectric.buildActivity(ReadyActivity.class).setup();var b=next.get();await(b,0);
        double[] restored=((RouteMapView)b.findViewById(R.id.routeMap)).centerGcj();
        assertEquals(center[0],restored[0],1e-5);assertEquals(center[1],restored[1],1e-5);
        assertFalse(com.wandertrace.Config.load(b).enabled);assertEquals(25,com.wandertrace.Config.fromJson(prefs.getString("editor_draft",null)).points.length);
        next.pause().stop().destroy();
    }
}
