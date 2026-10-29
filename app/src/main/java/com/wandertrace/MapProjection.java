package com.wandertrace;

/**
 * Web Mercator forward/inverse formulas adapted from osmdroid TileSystemWebMercator,
 * author Fabrice Fontaine, Apache-2.0. Upstream b491ee4b70dc8bf1e51493fca2c5acc8d02d491a.
 * Modified for WanderTrace: pixel scaling, latitude clamp and focused pinch zoom.
 * See assets/licenses/osmdroid-Apache-2.0.txt and THIRD_PARTY.md.
 */
public final class MapProjection {
    private static final int TILE = 256;
    private MapProjection() {}
    public static double worldX(double lon, double zoom) { return (lon + 180.0) / 360.0 * (TILE * Math.pow(2,zoom)); }
    public static double worldY(double lat, double zoom) {
        lat = Math.max(-85.05112878, Math.min(85.05112878, lat));
        final double sinus = Math.sin(lat * Math.PI / 180);
        return (0.5 - Math.log((1 + sinus) / (1 - sinus)) / (4 * Math.PI)) * (TILE * Math.pow(2,zoom));
    }
    public static double longitude(double x, double zoom) { return x / (TILE * Math.pow(2,zoom)) * 360 - 180; }
    public static double latitude(double y, double zoom) { return 90 - 360 * Math.atan(Math.exp((y / (TILE * Math.pow(2,zoom)) - 0.5) * 2 * Math.PI)) / Math.PI; }
    public static double[] zoomCenter(double lat, double lon, double from, double to, float focusX, float focusY, int width, int height) {
        double fx = worldX(lon, from) + focusX - width / 2.0;
        double fy = worldY(lat, from) + focusY - height / 2.0;
        double factor = Math.pow(2, to - from), size = TILE * Math.pow(2,to);
        double cx = ((fx * factor - focusX + width / 2.0) % size + size) % size;
        double cy = Math.max(0, Math.min(size, fy * factor - focusY + height / 2.0));
        return new double[]{latitude(cy, to), longitude(cx, to)};
    }
    public static final class Pinch {
        private float accumulated = 1f;
        public void reset() { accumulated = 1f; }
        public int update(float factor) {
            if (!Float.isFinite(factor) || factor <= 0) return 0;
            accumulated *= factor;
            int steps = 0;
            while (accumulated >= 1.6f && steps < 8) { steps++; accumulated /= 2f; }
            while (accumulated <= 0.625f && steps > -8) { steps--; accumulated *= 2f; }
            return steps;
        }
    }
}
