package com.wandertrace;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Saved routes are editing data only and never enable system simulation. */
final class RouteStore {
    private static final String KEY="saved_routes_v1";
    record Entry(String id,String name,Config config) {}
    private final SharedPreferences prefs;
    RouteStore(SharedPreferences prefs) {this.prefs=prefs;}
    List<Entry> all() {
        List<Entry> entries=new ArrayList<>();
        try {
            JSONArray values=new JSONArray(prefs.getString(KEY,"[]"));
            for(int i=0;i<values.length();i++) {
                JSONObject item=values.optJSONObject(i);if(item==null) continue;
                Config config=Config.fromJson(item.optString("config"));
                if(!valid(config.points)) continue;
                config.enabled=false;config.startTime=0;config.mode=Config.MODE_ROUTE;
                entries.add(new Entry(item.getString("id"),item.getString("name"),config));
            }
        } catch(Exception ignored) {}
        return entries;
    }
    boolean save(String name,Config config) {
        if(name.trim().isEmpty() || !valid(config.points)) return false;
        Config copy=Config.fromJson(config.toJson());copy.enabled=false;copy.startTime=0;copy.mode=Config.MODE_ROUTE;copy.routeName=name.trim();
        List<Entry> entries=all();entries.add(new Entry(UUID.randomUUID().toString(),name.trim(),copy));return write(entries);
    }
    boolean delete(String id) {List<Entry> entries=all();entries.removeIf(entry -> entry.id().equals(id));return write(entries);}
    private boolean write(List<Entry> entries) {
        try {
            JSONArray values=new JSONArray();
            for(Entry entry:entries) values.put(new JSONObject().put("id",entry.id()).put("name",entry.name()).put("config",entry.config().toJson()));
            return prefs.edit().putString(KEY,values.toString()).commit();
        } catch(Exception failure) {return false;}
    }
    private static boolean valid(double[][] points) {
        if(points==null || points.length<2) return false;
        for(double[] point:points) if(point==null || point.length<2 || !Double.isFinite(point[0]) || !Double.isFinite(point[1])
                || Math.abs(point[0])>90 || Math.abs(point[1])>180) return false;
        return true;
    }
}
