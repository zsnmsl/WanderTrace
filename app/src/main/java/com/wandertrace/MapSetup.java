package com.wandertrace;

import android.content.SharedPreferences;

/** First-use provider/credential validation, independent from ROOT and module readiness. */
final class MapSetup {
    static final int SCHEMA=3;
    private MapSetup() {}
    static boolean required(SharedPreferences prefs) {
        if (!prefs.getBoolean("map_setup_complete",false) || prefs.getInt("map_setup_schema",0)!=SCHEMA) return true;
        String provider=prefs.getString("map_provider","");
        return !MapProviders.known(provider) || (MapProviders.needsKey(provider) && MapProviders.key(prefs,provider).isEmpty());
    }
}
