package com.wandertrace;

import static org.junit.Assert.*;
import android.app.Activity;
import android.app.AlertDialog;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.CheckBox;
import android.widget.EditText;
import android.view.View;
import android.os.Looper;
import org.junit.Before;
import org.robolectric.RuntimeEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.annotation.Config;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class MapInteractionTest {
    @Before public void clearPreferences() {
        RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS, 0).edit().clear().commit();
    }
    private static AlertDialog latestDialog() {
        org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
        return ShadowAlertDialog.getLatestAlertDialog();
    }
    private static void chooseProvider(AlertDialog dialog, int index) {
        ((Spinner)dialog.findViewById(R.id.setupProvider)).setSelection(index);
        org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
    }
    private static void completeOsmSetup() {
        AlertDialog dialog=latestDialog(); assertNotNull(dialog); assertTrue(dialog.isShowing());
        chooseProvider(dialog,2);
        ((CheckBox)dialog.findViewById(R.id.setupConsent)).setChecked(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
    }
    private static MotionEvent event(long down, long time, int action, float[] xs, float[] ys) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[xs.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[xs.length];
        for (int i=0;i<xs.length;i++) {
            props[i]=new MotionEvent.PointerProperties(); props[i].id=i; props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords(); coords[i].x=xs[i]; coords[i].y=ys[i]; coords[i].pressure=1; coords[i].size=1;
        }
        return MotionEvent.obtain(down,time,action,xs.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    }
    private static void send(RouteMapView view,long down,long t,int action,float[] xs,float[] ys) {
        MotionEvent e=event(down,t,action,xs,ys); view.dispatchTouchEvent(e); e.recycle();
    }
    private static void tap(RouteMapView view,long t,float x,float y) {
        send(view,t,t,MotionEvent.ACTION_DOWN,new float[]{x},new float[]{y});
        send(view,t,t+40,MotionEvent.ACTION_UP,new float[]{x},new float[]{y});
    }

    @Test public void pinchAndTapNeverAddPointsWithoutExplicitButton() {
        Activity activity=Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout parent=new FrameLayout(activity); RouteMapView view=new RouteMapView(activity,null);
        parent.addView(view,new FrameLayout.LayoutParams(900,700)); parent.layout(0,0,900,700); view.layout(0,0,900,700);
        AtomicInteger changes=new AtomicInteger(); view.setRouteMode(true); view.setOnPointsChanged(p -> changes.incrementAndGet());
        long t=1000;
        send(view,t,t,MotionEvent.ACTION_DOWN,new float[]{300},new float[]{350});
        send(view,t,t+20,MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{300,500},new float[]{350,350});
        send(view,t,t+40,MotionEvent.ACTION_MOVE,new float[]{250,550},new float[]{350,350});
        send(view,t,t+60,MotionEvent.ACTION_MOVE,new float[]{180,700},new float[]{350,350});
        send(view,t,t+80,MotionEvent.ACTION_MOVE,new float[]{100,800},new float[]{350,350});
        send(view,t,t+100,MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT),new float[]{100,800},new float[]{350,350});
        send(view,t,t+120,MotionEvent.ACTION_UP,new float[]{100},new float[]{350});
        assertEquals("pinch must not select a point",0,changes.get());
        tap(view,2000,450,350); assertEquals(0,changes.get());
    }

    @Test public void mapFirstUiAddsRouteNodesAndAmapRequiresConsent() {
        MainActivity activity=Robolectric.buildActivity(MainActivity.class).setup().get();
        completeOsmSetup();
        RouteMapView map=activity.findViewById(R.id.routeMap); map.layout(0,0,900,700);
        activity.findViewById(R.id.rbRoute).performClick();
        map.centerOn(39.91,116.40);activity.findViewById(R.id.btnAddPoint).performClick();
        map.centerOn(39.92,116.41);activity.findViewById(R.id.btnAddPoint).performClick();
        assertTrue(((TextView)activity.findViewById(R.id.tvSelection)).getText().toString().contains("2 个节点"));
        activity.findViewById(R.id.btnUndo).performClick();
        assertTrue(((TextView)activity.findViewById(R.id.tvSelection)).getText().toString().contains("1 个节点"));
        activity.findViewById(R.id.rbSettings).performClick();
        assertEquals(View.VISIBLE,activity.findViewById(R.id.pageSettings).getVisibility());
        assertEquals(View.GONE,activity.findViewById(R.id.pageEditor).getVisibility());
        activity.findViewById(R.id.rbAmap).performClick();
        AlertDialog dialog=latestDialog();
        assertNotNull("AMap must ask for one Web key and consent",dialog);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();
        assertTrue(((android.widget.RadioButton)activity.findViewById(R.id.rbOsm)).isChecked());
        activity.findViewById(R.id.rbSingle).performClick();
        tap(map,2000,450,350);
        assertTrue(((TextView)activity.findViewById(R.id.tvSelection)).getText().toString().contains("以地图中心为准"));
        activity.findViewById(R.id.rbRoute).performClick();
        assertTrue(((TextView)activity.findViewById(R.id.tvSelection)).getText().toString().contains("1 个节点"));
        activity.findViewById(R.id.rbSingle).performClick();
        assertTrue(((TextView)activity.findViewById(R.id.tvSelection)).getText().toString().contains("以地图中心为准"));
        activity.finish();
    }

    @Test public void firstLaunchRequiresExplicitSetupAndRetainsProviderAfterRecreation() {
        var controller=Robolectric.buildActivity(MainActivity.class).setup(); MainActivity activity=controller.get();
        AlertDialog dialog=latestDialog();
        assertFalse(activity.getSharedPreferences(com.wandertrace.Config.PREFS,0).getBoolean("map_setup_complete",false));
        chooseProvider(dialog,0);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertTrue("empty SDK key cannot complete setup",dialog.isShowing());
        assertNotNull(((EditText)dialog.findViewById(R.id.setupMapKey)).getError());
        completeOsmSetup();
        assertTrue(activity.getSharedPreferences(com.wandertrace.Config.PREFS,0).getBoolean("map_setup_complete",false));
        controller.pause().stop().destroy();
        var second=Robolectric.buildActivity(MainActivity.class).setup();
        assertFalse("setup should not recur",dialog.isShowing());
        assertTrue(((android.widget.RadioButton)second.get().findViewById(R.id.rbOsm)).isChecked());
        second.pause().stop().destroy();
    }

    @Test public void tiandituUsesItsOwnKeyAndCanSwitchBackToOsm() {
        var controller=Robolectric.buildActivity(MainActivity.class).setup(); MainActivity activity=controller.get();
        AlertDialog dialog=latestDialog(); chooseProvider(dialog,1);
        ((EditText)dialog.findViewById(R.id.setupMapKey)).setText("test-key-for-config-only");
        ((CheckBox)dialog.findViewById(R.id.setupConsent)).setChecked(true);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(dialog.isShowing());
        var prefs=activity.getSharedPreferences(com.wandertrace.Config.PREFS,0);
        assertEquals("tianditu",prefs.getString("map_provider",""));
        assertEquals("test-key-for-config-only",prefs.getString("tianditu_key",""));
        assertEquals("",prefs.getString("amap_key",""));
        activity.findViewById(R.id.rbSettings).performClick(); activity.findViewById(R.id.rbOsm).performClick();
        assertEquals("osm",prefs.getString("map_provider",""));
        controller.pause().stop().destroy();
    }

    @Test public void editingDraftSurvivesReopeningWithoutApplyingSystemConfiguration() {
        var first=Robolectric.buildActivity(MainActivity.class).setup(); MainActivity activity=first.get();
        completeOsmSetup(); activity.findViewById(R.id.rbRoute).performClick();
        RouteMapView map=activity.findViewById(R.id.routeMap); map.layout(0,0,900,700);
        map.centerOn(39.91,116.40);activity.findViewById(R.id.btnAddPoint).performClick();
        map.centerOn(39.92,116.41);activity.findViewById(R.id.btnAddPoint).performClick();
        assertFalse(com.wandertrace.Config.load(activity).enabled);
        first.pause().stop().destroy();
        var second=Robolectric.buildActivity(MainActivity.class).setup(); MainActivity reopened=second.get();
        assertTrue(((android.widget.RadioButton)reopened.findViewById(R.id.rbRoute)).isChecked());
        assertTrue(((TextView)reopened.findViewById(R.id.tvSelection)).getText().toString().contains("2 个节点"));
        assertNull("route speed is only in the start dialog",reopened.findViewById(R.id.etSpeedRandom));
        assertFalse(com.wandertrace.Config.load(reopened).enabled);
        assertNull(com.wandertrace.Config.load(reopened).points);
        second.pause().stop().destroy();
    }
}
