package com.wandertrace;

import android.content.SharedPreferences;

/** Layer preferences belong to the provider, independently from simulation or route data. */
final class MapLayers {
    private MapLayers() {}
    static boolean supportsSatellite(String provider) {return MapProviders.known(provider) && !"osm".equals(provider);}
    static boolean satellite(SharedPreferences prefs,String provider) {
        return supportsSatellite(provider) && prefs.getBoolean("map_satellite_"+provider,false);
    }
    static void save(SharedPreferences prefs,String provider,boolean satellite) {
        if(supportsSatellite(provider)) prefs.edit().putBoolean("map_satellite_"+provider,satellite).apply();
    }
}
