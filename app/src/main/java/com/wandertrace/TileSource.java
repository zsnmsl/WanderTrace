package com.wandertrace;

import java.net.URLEncoder;

/** WMTS / public raster endpoints. AMap/Tencent URL patterns adapted from
 * Leaflet.ChineseTmsProviders, MIT, Tao Huang (2014), commit bafcb9a4c76ca702e02c81ddd25e986ab667ec88.
 * See THIRD_PARTY.md. No upstream demo credential is included or used. */
final class TileSource {
    final String provider, key;
    final boolean satellite;
    TileSource(String provider, String key) {
        this(provider,key,false);
    }
    TileSource(String provider, String key, boolean satellite) {
        this.provider = MapProviders.known(provider) ? provider : "osm";
        this.key = key == null ? "" : key.trim();
        this.satellite=satellite && MapLayers.supportsSatellite(this.provider);
    }
    boolean isGcj() {return "amap".equals(provider) || "tencent".equals(provider) || "baidu".equals(provider);}
    boolean hasLabels() {return "tianditu".equals(provider) || (satellite && isGcj());}
    int maxZoom() {return "osm".equals(provider) ? 19 : 18;}
    String attribution() {
        return switch(provider) {case "amap","baidu" -> "© 高德地图";case "tencent" -> "© 腾讯地图";case "tianditu" -> "© 天地图 · 自然资源部";default -> "© OpenStreetMap contributors";};
    }
    double[] fromGcj(double lat,double lon) {return isGcj() ? new double[]{lat,lon} : CoordTransform.gcj2wgs(lat,lon);}
    double[] toGcj(double lat,double lon) {return isGcj() ? new double[]{lat,lon} : CoordTransform.wgs2gcj(lat,lon);}
    String url(int z, int x, int y, boolean labels) {
        int host="amap".equals(provider) || "baidu".equals(provider) ? 1+((x+y)&3) : "tencent".equals(provider) ? (x+y)&3 : (x+y)&7;
        int inverse=(1<<z)-1-y;
        return pattern(labels).replace("{s}",String.valueOf(host)).replace("{z}",String.valueOf(z))
                .replace("{x}",String.valueOf(x)).replace("{y}",String.valueOf(y)).replace("{iy}",String.valueOf(inverse))
                .replace("{gx}",String.valueOf(x>>4)).replace("{gy}",String.valueOf(inverse>>4));
    }
    String pattern(boolean labels) {
        if("osm".equals(provider)) return "https://tile.openstreetmap.org/{z}/{x}/{y}.png";
        if("amap".equals(provider) || "baidu".equals(provider)) {
            int style=satellite && !labels ? 6 : 8;
            return "https://"+(satellite ? "webst0" : "webrd0")+"{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style="+style+"&x={x}&y={y}&z={z}";
        }
        if("tencent".equals(provider)) {
            return satellite && !labels ? "https://p{s}.map.gtimg.com/sateTiles/{z}/{gx}/{gy}/{x}_{iy}.jpg"
                    : "https://rt{s}.map.gtimg.com/tile?z={z}&x={x}&y={iy}&type=vector&styleid="+(satellite ? 2 : 1);
        }
        if (key.isEmpty()) throw new IllegalStateException("天地图需要服务 Key");
        String layer = satellite ? (labels ? "cia" : "img") : (labels ? "cva" : "vec");
        return "https://t{s}.tianditu.gov.cn/" + layer + "_w/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0"
                + "&LAYER=" + layer + "&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}"
                + "&TILEROW={y}&TILECOL={x}&tk=" + encode(key);
    }
    private static String encode(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (java.io.UnsupportedEncodingException impossible) { throw new AssertionError(impossible); }
    }
}
