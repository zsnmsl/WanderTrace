package com.wandertrace;
// Lightweight stand-in, so RouteEngine's pure maths can be checked without Android.
public class Config {
    public static final int MODE_ROUTE = 1;
    public int mode;
    public double[][] points;
    public double accuracy = 5, alt = 45, speedKmh = 20, speedRandomPercent = 50,
            jitterMeters = 2, lat = 39, lon = 116, bearing;
    public boolean loop, wgs84;
    public long startTime;
}
