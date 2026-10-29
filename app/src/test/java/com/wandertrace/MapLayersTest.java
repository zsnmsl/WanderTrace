package com.wandertrace;

import static org.junit.Assert.*;
import android.widget.RadioButton;
import android.widget.TextView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MapLayersTest {
    @Before public void reset() {
        RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0).edit().clear()
                .putBoolean("map_setup_complete",true).putInt("map_setup_schema",MapSetup.SCHEMA)
                .putString("map_provider","tianditu").putString("tianditu_key","test-key").commit();
    }
    @Test public void tiandituPairsImageryAndAnnotationsAndCanReturnToRoads() {
        TileSource satellite=new TileSource("tianditu","key&test",true);
        assertTrue(satellite.url(12,34,56,false).contains("/img_w/wmts?"));
        assertTrue(satellite.url(12,34,56,true).contains("/cia_w/wmts?"));
        assertTrue(satellite.url(12,34,56,false).contains("&LAYER=img&"));
        assertTrue(satellite.url(12,34,56,true).contains("&LAYER=cia&"));
        assertTrue(satellite.url(12,34,56,false).endsWith("tk=key%26test"));
        TileSource road=new TileSource("tianditu","key",false);
        assertTrue(road.url(12,34,56,false).contains("/vec_w/wmts?"));
        assertTrue(road.url(12,34,56,true).contains("/cva_w/wmts?"));
        assertEquals(road.maxZoom(),satellite.maxZoom());assertTrue(satellite.hasLabels());
    }
    @Test public void eachProviderRemembersItsOwnLayerAndOsmCannotPretendSatellite() {
        var prefs=RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0);
        assertFalse(MapLayers.satellite(prefs,"amap"));
        MapLayers.save(prefs,"amap",true);MapLayers.save(prefs,"tianditu",true);
        MapLayers.save(prefs,"tianditu",false);MapLayers.save(prefs,"osm",true);
        assertTrue(MapLayers.satellite(prefs,"amap"));assertFalse(MapLayers.satellite(prefs,"tianditu"));assertFalse(MapLayers.satellite(prefs,"osm"));
        TileSource osm=new TileSource("osm","",true);assertFalse(osm.satellite);
        assertEquals("https://tile.openstreetmap.org/12/34/56.png",osm.url(12,34,56,false));
    }
    @Test public void changingLayersPreservesCameraRouteAndRestoresAfterRecreation() {
        var first=Robolectric.buildActivity(MainActivity.class).setup();var a=first.get();
        RouteMapView map=a.findViewById(R.id.routeMap);
        a.findViewById(R.id.rbRoute).performClick();map.setCamera(39.91,116.40,16);a.findViewById(R.id.btnAddPoint).performClick();
        map.centerOn(39.92,116.41);a.findViewById(R.id.btnAddPoint).performClick();
        double[] center=map.centerGcj();int zoom=map.zoomLevel();
        var prefs=a.getSharedPreferences(com.wandertrace.Config.PREFS,0);String draft=prefs.getString("editor_draft",null);
        a.findViewById(R.id.rbSettings).performClick();a.findViewById(R.id.rbSatellite).performClick();
        assertTrue(MapLayers.satellite(prefs,"tianditu"));
        assertArrayEquals(center,map.centerGcj(),1e-9);assertEquals(zoom,map.zoomLevel());
        assertEquals(draft,prefs.getString("editor_draft",null));assertFalse(com.wandertrace.Config.load(a).enabled);
        first.pause().stop().destroy();
        var second=Robolectric.buildActivity(MainActivity.class).setup();var b=second.get();
        assertTrue(((RadioButton)b.findViewById(R.id.rbSatellite)).isChecked());
        b.findViewById(R.id.rbSettings).performClick();b.findViewById(R.id.rbOsm).performClick();
        assertFalse(b.findViewById(R.id.rbSatellite).isEnabled());assertTrue(((RadioButton)b.findViewById(R.id.rbRoad)).isChecked());
        assertTrue(((TextView)b.findViewById(R.id.tvLayerHint)).getText().toString().contains("仅提供道路图"));
        b.findViewById(R.id.rbTianditu).performClick();assertTrue(((RadioButton)b.findViewById(R.id.rbSatellite)).isChecked());
        b.findViewById(R.id.rbRoad).performClick();assertFalse(MapLayers.satellite(prefs,"tianditu"));
        second.pause().stop().destroy();
    }
}
