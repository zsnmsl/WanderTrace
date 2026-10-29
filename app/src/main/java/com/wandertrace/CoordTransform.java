package com.wandertrace;

/**
 * GCJ-02（国测局火星坐标，高德/腾讯/微信使用）与 WGS-84（GPS 原始坐标，谷歌地图使用）互转。
 * 中国境内两者偏差通常在 300~600 米，选错坐标系会明显偏移。
 */
public final class CoordTransform {

    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private CoordTransform() {
    }

    /** GCJ-02 -> WGS-84 */
    public static double[] gcj2wgs(double lat, double lon) {
        if (outOfChina(lat, lon)) return new double[]{lat, lon};
        double wLat = lat, wLon = lon;
        for (int i = 0; i < 8; i++) {
            double[] projected = wgs2gcj(wLat, wLon);
            double eLat = projected[0] - lat, eLon = projected[1] - lon;
            wLat -= eLat; wLon -= eLon;
            if (Math.abs(eLat) + Math.abs(eLon) < 1e-10) break;
        }
        return new double[]{wLat, wLon};
    }

    /** WGS-84 -> GCJ-02 */
    public static double[] wgs2gcj(double lat, double lon) {
        if (outOfChina(lat, lon)) return new double[]{lat, lon};
        double[] d = delta(lat, lon);
        return new double[]{lat + d[0], lon + d[1]};
    }

    private static double[] delta(double lat, double lon) {
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{dLat, dLon};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    private static boolean outOfChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }
}
