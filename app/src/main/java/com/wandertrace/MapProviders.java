package com.wandertrace;

import android.content.SharedPreferences;

/** Each selected service has one Web-service credential, never an Android SDK key. */
final class MapProviders {
    static final String[] IDS={"amap","tianditu","osm","tencent","baidu"};
    static final String[] NAMES={"高德地图","天地图","OpenStreetMap（免 Key 底图）","腾讯地图","百度搜索 · 高德底图"};
    private MapProviders() {}
    static int index(String provider) {for(int i=0;i<IDS.length;i++) if(IDS[i].equals(provider)) return i;return 0;}
    static boolean known(String provider) {for(String id:IDS) if(id.equals(provider)) return true;return false;}
    static String keyName(String provider) {
        return switch(provider) {case "tianditu" -> "tianditu_key";case "tencent" -> "tencent_key";case "baidu" -> "baidu_key";default -> "amap_key";};
    }
    static String key(SharedPreferences prefs,String provider) {return prefs.getString(keyName(provider),"").trim();}
    static void saveKey(SharedPreferences prefs,String provider,String value) {prefs.edit().putString(keyName(provider),value.trim()).apply();}
    static boolean needsKey(String provider) {return !"osm".equals(provider);}
    static String keyHint(String provider) {
        return switch(provider) {
            case "tianditu" -> "天地图服务端 Key（tk）";
            case "tencent" -> "腾讯 WebService Key";
            case "baidu" -> "百度服务端 AK";
            case "osm" -> "可选：高德 Web 服务 Key（用于搜索）";
            default -> "高德 Web 服务 Key";
        };
    }
    static String description(String provider) {
        return switch(provider) {
            case "tianditu" -> "一个服务端 Key 用于道路图、卫星影像、注记和地点搜索。";
            case "tencent" -> "填写已开通 WebService 地理编码的 Key。底图使用腾讯公开瓦片，无需 Android SDK Key。";
            case "baidu" -> "AK 用于百度地点搜索；道路图与卫星图使用高德瓦片，搜索坐标统一转换为 GCJ-02。";
            case "osm" -> "道路底图免 Key；需要地点搜索时，可填写一把高德 Web 服务 Key。";
            default -> "只填一把 Web 服务 Key 即可使用地点搜索。道路图、卫星图使用公开瓦片，无需 Android SDK Key 或签名。";
        };
    }
}
