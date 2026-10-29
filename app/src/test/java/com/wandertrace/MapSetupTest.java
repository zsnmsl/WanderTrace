package com.wandertrace;

import static org.junit.Assert.*;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class MapSetupTest {
    private SharedPreferences prefs;
    @Before public void reset() {
        prefs=RuntimeEnvironment.getApplication().getSharedPreferences(com.wandertrace.Config.PREFS,0);
        prefs.edit().clear().commit();
    }
    @Test public void firstUseAndOldIncompleteSetupRequireWizard() {
        assertTrue(MapSetup.required(prefs));
        prefs.edit().putBoolean("map_setup_complete",true).putString("map_provider","osm").commit();
        assertTrue(MapSetup.required(prefs));
    }
    @Test public void oneWebCredentialCompletesEachProviderWithoutAndroidKey() {
        prefs.edit().putBoolean("map_setup_complete",true).putInt("map_setup_schema",MapSetup.SCHEMA).putString("map_provider","amap").commit();
        assertTrue(MapSetup.required(prefs));
        prefs.edit().putString("amap_android_key","old-sdk-key").putBoolean("amap_consent",true).commit();
        assertTrue("an Android SDK key must never be reused as a Web key",MapSetup.required(prefs));
        prefs.edit().putString("amap_key","web-key").commit();assertFalse(MapSetup.required(prefs));
        for(String provider:new String[]{"tianditu","tencent","baidu"}) {
            prefs.edit().putString("map_provider",provider).commit();assertTrue(MapSetup.required(prefs));
            MapProviders.saveKey(prefs,provider,"one-web-key");assertFalse(MapSetup.required(prefs));
            MapProviders.saveKey(prefs,provider,"  ");assertTrue(MapSetup.required(prefs));
        }
    }
    @Test public void explicitKeyFreeProviderIsValidOnlyAfterSetup() {
        prefs.edit().putBoolean("map_setup_complete",true).putInt("map_setup_schema",MapSetup.SCHEMA)
                .putString("map_provider","osm").commit();
        assertFalse(MapSetup.required(prefs));
        prefs.edit().putString("map_provider","unknown").commit(); assertTrue(MapSetup.required(prefs));
    }
}
