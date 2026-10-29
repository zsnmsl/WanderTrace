package com.wandertrace;

import static org.junit.Assert.*;
import android.app.AlertDialog;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
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
public class SingleWebKeyTest {
    @Before public void reset() {RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0).edit().clear().commit();}
    private static void idle() {org.robolectric.shadow.api.Shadow.<org.robolectric.shadows.ShadowLooper>extract(Looper.getMainLooper()).idle();}
    private static int fields(View v) {int n=v instanceof EditText ? 1 : 0;if(v instanceof ViewGroup group) for(int i=0;i<group.getChildCount();i++) n+=fields(group.getChildAt(i));return n;}
    @Test public void amapWebKeyAloneCompletesSetupAndReopensWithNoSdkCredential() {
        var c=Robolectric.buildActivity(MainActivity.class).setup();var a=c.get();idle();
        AlertDialog setup=ShadowAlertDialog.getLatestAlertDialog();
        assertEquals(1,fields(setup.getWindow().getDecorView()));
        assertTrue(((EditText)setup.findViewById(R.id.setupMapKey)).getHint().toString().contains("Web 服务 Key"));
        ((EditText)setup.findViewById(R.id.setupMapKey)).setText("test-amap-web-key");
        ((CheckBox)setup.findViewById(R.id.setupConsent)).setChecked(true);setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        assertFalse(setup.isShowing());var prefs=a.getSharedPreferences(com.wandertrace.Config.PREFS,0);
        assertEquals("test-amap-web-key",MapProviders.key(prefs,"amap"));assertFalse(MapSetup.required(prefs));
        assertFalse(prefs.contains("amap_android_key"));
        for(String removed:new String[]{"etAndroidKey","etTiandituKey","setupWebKey","tvSdkHint"})
            assertEquals(0,a.getResources().getIdentifier(removed,"id",a.getPackageName()));
        a.findViewById(R.id.rbRoute).performClick();RouteMapView map=a.findViewById(R.id.routeMap);
        map.centerOn(39.91,116.4);a.findViewById(R.id.btnAddPoint).performClick();map.centerOn(39.92,116.41);a.findViewById(R.id.btnAddPoint).performClick();
        double[] center=map.centerGcj();a.findViewById(R.id.rbSettings).performClick();a.findViewById(R.id.rbSatellite).performClick();
        assertArrayEquals(center,map.centerGcj(),1e-9);assertFalse(com.wandertrace.Config.load(a).enabled);
        c.pause().stop().destroy();var second=Robolectric.buildActivity(MainActivity.class).setup();
        assertEquals("test-amap-web-key",((EditText)second.get().findViewById(R.id.etKey)).getText().toString());
        assertFalse(MapSetup.required(prefs));second.pause().stop().destroy();
    }
    @Test public void tencentAndBaiduOfferOneIndependentKeyAndRetainAmapKey() {
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0);
        prefs.edit().putBoolean("map_setup_complete",true).putInt("map_setup_schema",MapSetup.SCHEMA)
                .putString("map_provider","amap").putString("amap_key","amap-only-key").commit();
        var c=Robolectric.buildActivity(MainActivity.class).setup();var a=c.get();a.findViewById(R.id.rbSettings).performClick();
        for(String provider:new String[]{"tencent","baidu"}) {
            a.findViewById("tencent".equals(provider) ? R.id.rbTencent : R.id.rbBaidu).performClick();idle();
            AlertDialog setup=ShadowAlertDialog.getLatestAlertDialog();assertEquals(1,fields(setup.getWindow().getDecorView()));
            assertEquals(MapProviders.index(provider),((Spinner)setup.findViewById(R.id.setupProvider)).getSelectedItemPosition());
            ((EditText)setup.findViewById(R.id.setupMapKey)).setText(provider+"-web-key");
            ((CheckBox)setup.findViewById(R.id.setupConsent)).setChecked(true);setup.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertEquals(provider,prefs.getString("map_provider",""));assertEquals(provider+"-web-key",((EditText)a.findViewById(R.id.etKey)).getText().toString());
        }
        assertTrue(MapProviders.description("baidu").contains("高德瓦片"));
        a.findViewById(R.id.rbAmap).performClick();assertEquals("amap-only-key",((EditText)a.findViewById(R.id.etKey)).getText().toString());
        assertFalse(com.wandertrace.Config.load(a).enabled);c.pause().stop().destroy();
    }
    @Test public void providerProjectionSwitchingDoesNotShiftGcjRouteOrCamera() {
        var c=Robolectric.buildActivity(android.app.Activity.class).setup();RouteMapView map=new RouteMapView(c.get(),null);
        map.setCamera(31.2304,121.4737,16);double[] expected=map.centerGcj();
        for(String provider:new String[]{"amap","tencent","baidu","tianditu","osm","amap"}) {
            map.configureTiles(provider,"test-key",true);assertArrayEquals(expected,map.centerGcj(),1e-8);assertEquals(16,map.zoomLevel());
        }
        TileSource amap=new TileSource("amap","private-web-key",true);
        assertTrue(amap.url(14,13489,6206,false).contains("style=6"));assertTrue(amap.url(14,13489,6206,true).contains("style=8"));
        assertFalse(amap.url(14,13489,6206,false).contains("private-web-key"));
        TileSource tencent=new TileSource("tencent","private-web-key",true);
        assertTrue(tencent.url(14,13489,6206,false).endsWith("/13489_10177.jpg"));
        assertTrue(tencent.hasLabels());assertTrue(tencent.url(14,13489,6206,true).endsWith("styleid=2"));
        assertTrue(new TileSource("tencent","",false).url(14,13489,6206,false).endsWith("styleid=1"));
        assertTrue(new TileSource("baidu","",false).url(14,13489,6206,false).contains("autonavi.com"));
        c.pause().stop().destroy();
    }
}
