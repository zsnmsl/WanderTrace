package com.wandertrace;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RouteStoreTest {
    @Test public void multipleSavedRoutesKeepAllPointsAndNeverAutoStart() {
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("route-library-test",0);prefs.edit().clear().commit();
        RouteStore store=new RouteStore(prefs);com.wandertrace.Config config=new com.wandertrace.Config();
        config.points=new double[][]{{39.9,116.4},{39.91,116.41},{39.92,116.42}};config.enabled=true;config.startTime=123;
        config.speedKmh=10.8;config.travelProfile="running";
        assertTrue(store.save("晨跑",config));assertTrue(store.save("晚跑",config));config.points[0][0]=0;
        var reopened=new RouteStore(prefs).all();assertEquals(2,reopened.size());
        assertEquals(3,reopened.get(0).config().points.length);assertEquals(39.9,reopened.get(0).config().points[0][0],0);
        assertFalse(reopened.get(0).config().enabled);assertEquals(0,reopened.get(0).config().startTime);
        assertEquals("running",reopened.get(0).config().travelProfile);
        assertTrue(store.delete(reopened.get(0).id()));assertEquals("晚跑",store.all().get(0).name());
    }
    @Test public void invalidRoutesCannotBeSavedOrLoaded() {
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences("route-invalid-test",0);prefs.edit().clear().commit();
        RouteStore store=new RouteStore(prefs);com.wandertrace.Config config=new com.wandertrace.Config();
        assertFalse(store.save("无节点",config));config.points=new double[][]{{Double.NaN,116},{39,117}};assertFalse(store.save("异常",config));
        config.points=new double[][]{{39,116},{40,117}};assertFalse(store.save("  ",config));
        prefs.edit().putString("saved_routes_v1","malformed").commit();assertTrue(store.all().isEmpty());
    }
}
