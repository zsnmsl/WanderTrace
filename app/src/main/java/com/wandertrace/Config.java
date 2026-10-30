package com.wandertrace;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 模拟配置。应用通过 ROOT 写入系统目录，system_server 加载同一份 JSON。
 */
public class Config {
    public static final int SYSTEM_CONFIG_SCHEMA = 1;

    public static final int MODE_SINGLE = 0;
    public static final int MODE_ROUTE = 1;

    public static final String PKG = "com.wandertrace";
    public static final String AUTHORITY = PKG + ".provider";
    public static final String PREFS = "spoof";

    /** 是否正在模拟 */
    public boolean enabled = false;
    /** 模拟模式：单点 / 路径 */
    public int mode = MODE_SINGLE;

    /** 单点模式坐标（GCJ-02，除非开启 wgs84 输出） */
    public double lat = 39.908823;
    public double lon = 116.397470;
    /** 是否已在地图上选择单点。旧配置中的坐标视为已选。 */
    public boolean singlePointSelected = false;
    /** 海拔（米） */
    public double alt = 45.0;
    /** 定位精度（米） */
    public double accuracy = 5.0;
    /** 固定航向（单点模式使用，度） */
    public double bearing = 0.0;

    /** 路径模式速度 km/h */
    public double speedKmh = 20.0;
    /** 速度随机幅度（基准速度的百分比，0–50；新配置默认 10，已有 0 保持匀速）。 */
    public double speedRandomPercent = 10.0;
    /** GPS 误差半径（米，1–3）。 */
    public double jitterMeters = 2.0;
    /** 步频模式：0 关闭，1 根据速度自适应，2 自定义。 */
    public int cadenceMode = 0;
    /** 自定义步频（步/分钟）。 */
    public double cadenceSpm = 100.0;
    /** 路径模式的起始时间戳（毫秒），所有进程用它计算当前应处在路径的哪个位置 */
    public long startTime = 0L;
    /** 是否循环走这条路径 */
    public boolean loop = true;
    /** 定位回调间隔（毫秒），被 Hook 进程据此持续补发位置 */
    public int intervalMs = 1000;

    /** 输出前是否把 GCJ-02 转成 WGS-84（谷歌地图等海外应用需要） */
    public boolean wgs84 = false;

    public String routeName = "";
    /** UI travel preset; speedKmh remains the stable system protocol, converted at the UI boundary. */
    public String travelProfile = "custom";
    /** 路径点，每项为 [lat, lon] */
    public double[][] points;

    // ------------------------------------------------------------------ 持久化

    public static Config load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = sp.getString("config_json", null);
        if (json == null) return new Config();
        return fromJson(json);
    }

    public void save(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int version = sp.getInt("config_version", 0) + 1;
        sp.edit()
                .putString("config_json", toJson())
                .putInt("config_version", version)
                .commit();
    }

    // ------------------------------------------------------------------ JSON

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("enabled", enabled);
            o.put("mode", mode);
            o.put("lat", lat);
            o.put("lon", lon);
            o.put("singlePointSelected", singlePointSelected);
            o.put("alt", alt);
            o.put("accuracy", accuracy);
            o.put("bearing", bearing);
            o.put("speedKmh", speedKmh);
            o.put("speedRandomPercent", speedRandomPercent);
            o.put("jitterMeters", jitterMeters);
            o.put("cadenceMode", cadenceMode);
            o.put("cadenceSpm", cadenceSpm);
            o.put("startTime", startTime);
            o.put("loop", loop);
            o.put("intervalMs", intervalMs);
            o.put("wgs84", wgs84);
            o.put("routeName", routeName == null ? "" : routeName);
            o.put("travelProfile",travelProfile);
            if (points != null && points.length > 0) {
                JSONArray arr = new JSONArray();
                for (double[] p : points) {
                    if (p == null || p.length < 2) continue;
                    JSONArray one = new JSONArray();
                    one.put(p[0]);
                    one.put(p[1]);
                    arr.put(one);
                }
                o.put("points", arr);
            }
            return o.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    public static Config fromJson(String json) {
        Config c = new Config();
        if (json == null) return c;
        try {
            JSONObject o = new JSONObject(json);
            c.enabled = o.optBoolean("enabled", false);
            c.mode = o.optInt("mode", MODE_SINGLE);
            c.lat = o.optDouble("lat", c.lat);
            c.lon = o.optDouble("lon", c.lon);
            c.singlePointSelected = o.optBoolean("singlePointSelected", o.has("lat") && o.has("lon"));
            c.alt = o.optDouble("alt", c.alt);
            c.accuracy = o.optDouble("accuracy", c.accuracy);
            c.bearing = o.optDouble("bearing", c.bearing);
            c.speedKmh = o.optDouble("speedKmh", c.speedKmh);
            c.speedRandomPercent = o.optDouble("speedRandomPercent", c.speedRandomPercent);
            c.jitterMeters = o.optDouble("jitterMeters", c.jitterMeters);
            c.cadenceMode = o.optInt("cadenceMode", c.cadenceMode);
            c.cadenceSpm = o.optDouble("cadenceSpm", c.cadenceSpm);
            c.startTime = o.optLong("startTime", 0L);
            c.loop = o.optBoolean("loop", true);
            c.intervalMs = o.optInt("intervalMs", 1000);
            c.wgs84 = o.optBoolean("wgs84", false);
            c.routeName = o.optString("routeName", "");
            c.travelProfile = o.optString("travelProfile", "custom");

            JSONArray arr = o.optJSONArray("points");
            if (arr != null && arr.length() > 0) {
                double[][] pts = new double[arr.length()][];
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray one = arr.optJSONArray(i);
                    if (one == null || one.length() < 2) {
                        pts = null;
                        break;
                    }
                    pts[i] = new double[]{one.optDouble(0), one.optDouble(1)};
                }
                c.points = pts;
            }
        } catch (Exception ignored) {
        }
        return c;
    }
}
