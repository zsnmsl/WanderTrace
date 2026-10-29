package com.wandertrace;

import static org.junit.Assert.*;
import android.app.Activity;
import android.webkit.WebView;
import android.widget.FrameLayout;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Java adapter contracts; real map coverage and touch behavior require browser/device validation. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="mdpi")
public class MapRenderingRegressionTest {
    private static FrameLayout attach(Activity activity,RouteMapView view) {
        FrameLayout parent=new FrameLayout(activity);parent.addView(view,new FrameLayout.LayoutParams(400,400));activity.setContentView(parent);
        parent.layout(0,0,400,400);view.layout(0,0,400,400);return parent;
    }
    @Test public void fractionalCameraChangeKeepsTheFocusPointStable() {
        var c=Robolectric.buildActivity(Activity.class).setup();RouteMapView view=new RouteMapView(c.get(),null);attach(c.get(),view);
        view.configureTiles("amap","test-key",false);view.setCamera(39.9,116.4,17);
        float fx=90,fy=120;double[] before=view.centerGcj();
        double lon=MapProjection.longitude(MapProjection.worldX(before[1],17)+fx-200,17);
        double lat=MapProjection.latitude(MapProjection.worldY(before[0],17)+fy-200,17);
        view.zoomAt(.1,fx,fy);assertEquals(17.1,view.cameraZoom(),1e-5);double[] after=view.centerGcj();
        assertEquals(lon,MapProjection.longitude(MapProjection.worldX(after[1],17.1)+fx-200,17.1),1e-9);
        assertEquals(lat,MapProjection.latitude(MapProjection.worldY(after[0],17.1)+fy-200,17.1),1e-9);
        c.pause().stop().destroy();
    }
    @Test public void sceneRetainsEveryRouteNodeAndUsesNative18AtDisplay20() throws Exception {
        var c=Robolectric.buildActivity(Activity.class).setup();RouteMapView view=new RouteMapView(c.get(),null);
        view.configureTiles("amap","one-web-key",true);view.setRouteMode(true);view.setCamera(39.9,116.4,20);
        double[][] points=new double[137][2];for(int i=0;i<points.length;i++) points[i]=new double[]{39.9+i*.00001,116.4+i*.00001};
        view.setPoints(points);JSONObject scene=view.scene();
        assertEquals(137,scene.getJSONArray("points").length());assertTrue(scene.getBoolean("fit"));
        assertEquals(18,scene.getInt("maxNativeZoom"));assertEquals(20,scene.getInt("maxZoom"));assertEquals(18,view.dataZoom());
        assertTrue(scene.getString("base").contains("style=6"));assertTrue(scene.getString("labels").contains("style=8"));
        assertFalse(scene.toString().contains("one-web-key"));c.pause().stop().destroy();
    }
    @Test public void obsoleteCameraMessagesCannotOverwriteLatestCamera() throws Exception {
        var c=Robolectric.buildActivity(Activity.class).setup();RouteMapView view=new RouteMapView(c.get(),null);
        view.configureTiles("amap","key",false);JSONObject old=view.scene();view.setCamera(31.23,121.47,16.25f);
        RouteMapView.Bridge bridge=view.new Bridge((WebView)view.getChildAt(0));
        bridge.camera(old.getInt("revision"),old.getLong("sequence"),20,100,12);
        assertArrayEquals(new double[]{31.23,121.47},view.centerGcj(),1e-9);assertEquals(16.25,view.cameraZoom(),0);
        JSONObject current=view.scene();bridge.camera(current.getInt("revision"),current.getLong("sequence"),31.24,121.48,16.4);
        assertArrayEquals(new double[]{31.24,121.48},view.centerGcj(),1e-9);assertEquals(16.4,view.cameraZoom(),1e-5);
        c.pause().stop().destroy();
    }
    @Test public void detachReattachRecreatesRendererAndRejectsOldBridge() throws Exception {
        var c=Robolectric.buildActivity(Activity.class).setup();RouteMapView view=new RouteMapView(c.get(),null);FrameLayout parent=attach(c.get(),view);
        view.configureTiles("amap","key",true);WebView old=(WebView)view.getChildAt(0);RouteMapView.Bridge bridge=view.new Bridge(old);
        parent.removeView(view);parent.addView(view,new FrameLayout.LayoutParams(400,400));
        assertEquals(1,view.getChildCount());assertNotSame(old,view.getChildAt(0));JSONObject scene=view.scene();
        double[] before=view.centerGcj();bridge.camera(scene.getInt("revision"),scene.getLong("sequence"),20,100,12);
        assertArrayEquals(before,view.centerGcj(),0);assertEquals("file:///android_asset/map/index.html",((WebView)view.getChildAt(0)).getUrl());
        c.pause().stop().destroy();
    }
}
