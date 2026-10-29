package com.wandertrace;

/** Repeatable checks for provider-coordinate conversion and two-finger focal zoom. */
public final class MapMathCheck {
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        for (double[] p : new double[][]{{39.908823,116.397470},{31.23,121.47},{22.54,114.06},{48.85,2.35}}) {
            double[] gcj = CoordTransform.wgs2gcj(p[0], p[1]);
            double[] back = CoordTransform.gcj2wgs(gcj[0], gcj[1]);
            check(Math.abs(back[0]-p[0]) < 1e-7 && Math.abs(back[1]-p[1]) < 1e-7, "GCJ/WGS round trip");
        }
        double lat=39.9,lon=116.4; int from=14, to=15,w=900,h=700; float x=680,y=170;
        double originalX=MapProjection.worldX(lon,from)+x-w/2.0;
        double originalY=MapProjection.worldY(lat,from)+y-h/2.0;
        double[] center=MapProjection.zoomCenter(lat,lon,from,to,x,y,w,h);
        double afterX=MapProjection.worldX(center[1],to)+x-w/2.0;
        double afterY=MapProjection.worldY(center[0],to)+y-h/2.0;
        check(Math.abs(afterX-originalX*2) < 1e-6 && Math.abs(afterY-originalY*2) < 1e-6, "pinch focal point");
        MapProjection.Pinch pinch=new MapProjection.Pinch();
        check(pinch.update(1.2f)==0 && pinch.update(1.35f)==1, "pinch zoom in");
        check(pinch.update(1.01f)==0 && pinch.update(1f)==0, "no immediate zoom reversal");
        pinch.reset(); check(pinch.update(0.6f)==-1 && pinch.update(1f)==0, "pinch zoom out");
        check(pinch.update(Float.NaN)==0, "invalid gesture");
        System.out.println("Map checks passed: GCJ/WGS conversion, focal zoom, pinch thresholds and direction.");
    }
}
