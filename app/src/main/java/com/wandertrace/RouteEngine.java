package com.wandertrace;

/**
 * 路径插值引擎。
 *
 * <p>关键设计：路径当前的坐标不通过 IPC 每秒推送，而是由「配置文件 + 起始时间 + 速度」
 * 在所有进程中各自计算出同一结果。因此无论多少个进程在跑，坐标都是严格同步的。</p>
 */
public final class RouteEngine {

    public static final class Snap {
        public double lat;
        public double lon;
        public double alt;
        public double accuracy;
        public double speedMps;
        public double bearing;
        public long time;
    }

    private static double[][] cachedPts;
    private static double[] cachedCum;

    private RouteEngine() {
    }

    public static Snap compute(Config c, long now) {
        Snap s = new Snap();
        s.time = now;
        s.accuracy = c.accuracy > 0 ? c.accuracy : 5.0;
        s.alt = c.alt;

        boolean route = c.mode == Config.MODE_ROUTE && c.points != null && c.points.length >= 2;
        if (route) {
            double[] cum = cumulative(c.points);
            double total = cum[cum.length - 1];
            if (total > 0.5) {
                double base = finiteClamp(c.speedKmh, 0.0, 500.0, 0.0) / 3.6;
                double random = finiteClamp(c.speedRandomPercent, 0.0, 50.0, 0.0) / 100.0;
                double elapsed = c.startTime>0 ? Math.max(0.0, (now - (double) c.startTime) / 1000.0) : 0;
                long seed = mix(c.startTime ^ 0x632be59bd9b4e019L);
                double v = speedAt(base, random, elapsed,seed);
                double d = distanceAt(base, random, elapsed,seed);
                if (c.loop) {
                    d = d % total;
                } else if (base > 0.0) {
                    // Brake over the final four seconds (or the whole path if short).
                    // The integrated speed reaches exactly the last point at zero speed.
                    Brake brake = brakeFor(base, random, total,seed);
                    if (elapsed >= brake.start) {
                        double t = Math.min(brake.duration, elapsed - brake.start);
                        d = brake.distance + brake.speed * t * (1.0 - t / (2.0 * brake.duration));
                        v = brake.speed * Math.max(0.0, 1.0 - t / brake.duration);
                        if (t >= brake.duration) d = total;
                    }
                }

                int idx = 1;
                while (idx < cum.length && cum[idx] < d) idx++;
                if (idx >= cum.length) idx = cum.length - 1;

                double segStart = cum[idx - 1];
                double segLen = cum[idx] - segStart;
                double t = segLen > 1e-6 ? (d - segStart) / segLen : 0.0;
                if (t < 0) t = 0;
                if (t > 1) t = 1;

                double[] a = c.points[idx - 1];
                double[] b = c.points[idx];
                s.lat = a[0] + (b[0] - a[0]) * t;
                s.lon = a[1] + (b[1] - a[1]) * t;
                s.speedMps = v;
                s.bearing = bearing(a[0], a[1], b[0], b[1]);
                applyCoord(c, s);
                applyJitter(c, s, now);
                return s;
            }
        }

        // 单点（或路径数据异常时的兜底）
        s.lat = c.lat;
        s.lon = c.lon;
        s.speedMps = 0.0;
        s.bearing = c.bearing;
        applyCoord(c, s);
        applyJitter(c, s, now);
        return s;
    }

    private static final class Brake {
        final double start, distance, speed, duration;
        Brake(double start, double distance, double speed, double duration) {
            this.start = start;
            this.distance = distance;
            this.speed = speed;
            this.duration = duration;
        }
    }

    private static double brakeBase = -1, brakeRandom, brakeTotal;
    private static long brakeSeed;
    private static Brake cachedBrake;

    private static synchronized Brake brakeFor(double base, double random, double total,long seed) {
        if (cachedBrake != null && brakeBase == base && brakeRandom == random && brakeTotal == total && brakeSeed==seed) {
            return cachedBrake;
        }
        double start = 0.0;
        // At speed v, a four-second linear deceleration travels 2v metres.
        if (speedAt(base, random, 0.0,seed) * 2.0 < total) {
            double lo = 0.0, hi = Math.max(1.0, total / base * 2.0 + 4.0);
            // The function is strictly increasing for the allowed speed range and interval.
            for (int i = 0; i < 48; i++) {
                double mid = (lo + hi) / 2.0;
                if (distanceAt(base, random, mid,seed) + 2.0 * speedAt(base, random, mid,seed) < total) lo = mid;
                else hi = mid;
            }
            start = (lo + hi) / 2.0;
        }
        double distance = distanceAt(base, random, start,seed);
        double speed = speedAt(base, random, start,seed);
        double duration = 2.0 * (total - distance) / speed;
        cachedBrake = new Brake(start, distance, speed, duration);
        brakeBase = base;
        brakeRandom = random;
        brakeTotal = total;
        brakeSeed = seed;
        return cachedBrake;
    }

    // Seeded multiscale Haar noise: fast changes plus longer effort variations.
    // Full blocks have zero sum, so prefix sums and exact trapezoid integrals
    // take constant work even when seeking far into a session. Block amplitudes
    // are hashed independently; there is no repeated 64-target speed table.
    private static final double SPEED_INTERVAL = 10.0;
    private static final int[] SPEED_HALVES={1,4,16,64};
    private static final double[] SPEED_WEIGHTS={.18,.22,.30,.30};

    private static double amplitude(long seed,long block,int level) {
        return uniform(seed ^ block*0x9e3779b97f4a7c15L ^ (level+1)*0x94d049bb133111ebL)*2-1;
    }
    private static long phase(long seed,int level) {return mix(seed+level)&(SPEED_HALVES[level]*2-1);}
    private static double target(long seed,long index) {
        double value=0;
        for(int level=0;level<SPEED_HALVES.length;level++) {
            int half=SPEED_HALVES[level],size=half*2;
            long shifted=index+phase(seed,level);
            value+=SPEED_WEIGHTS[level]*amplitude(seed,shifted/size,level)*(shifted%size<half ? 1 : -1);
        }
        return value;
    }
    private static double blockPrefix(long seed,long index,int level) {
        int half=SPEED_HALVES[level],size=half*2;
        long remainder=index%size;
        return amplitude(seed,index/size,level)*Math.min(remainder,size-remainder);
    }
    private static double targetSum(long seed,long count) {
        double value=0;
        for(int level=0;level<SPEED_HALVES.length;level++) {
            long offset=phase(seed,level);
            value+=SPEED_WEIGHTS[level]*(blockPrefix(seed,count+offset,level)-blockPrefix(seed,offset,level));
        }
        return value;
    }
    private static double speedAt(double base,double amplitude,double seconds,long seed) {
        if(amplitude==0 || base==0) return base;
        double pos=seconds/SPEED_INTERVAL;
        long index=(long)pos;
        double f = pos - index;
        double variation=target(seed,index)*(1-f)+target(seed,index+1)*f;
        return base * (1.0 + amplitude * variation);
    }
    private static double distanceAt(double base,double amplitude,double seconds,long seed) {
        if(amplitude==0 || base==0) return base*seconds;
        double pos=seconds/SPEED_INTERVAL;
        long whole=(long)pos;
        double f=pos-whole,a=target(seed,whole),b=target(seed,whole+1);
        double variationSeconds=SPEED_INTERVAL*(targetSum(seed,whole)+.5*(a-target(seed,0))+a*f+.5*(b-a)*f*f);
        return Math.max(0.0, base * (seconds + amplitude * variationSeconds));
    }

    // Correlated synthetic drift, not a calibrated GNSS receiver model.
    // Convex mixtures of bounded disk samples preserve the configured radius.
    static double[] jitterOffset(Config c,long now) {
        long seed=mix(c.startTime ^ 0xd1b54a32d192ed03L);
        double seconds=Math.max(0,(now-(c.startTime>0 ? (double)c.startTime : 0))/1000.0);
        double[] intervals={61,13,2.3},weights={.70,.24,.06};
        double x=0,y=0;
        for(int level=0;level<intervals.length;level++) {
            long salt=seed ^ (level+1)*0x9e3779b97f4a7c15L;
            double pos=seconds/intervals[level]+uniform(salt),f=pos-Math.floor(pos);
            long index=(long)Math.floor(pos);
            double blend=f*f*f*(10+f*(-15+6*f));
            double[] a=disk(salt,index),b=disk(salt,index+1);
            x+=weights[level]*(a[0]*(1-blend)+b[0]*blend);
            y+=weights[level]*(a[1]*(1-blend)+b[1]*blend);
        }
        double radius=finiteClamp(c.jitterMeters,1,3,2);
        return new double[]{x*radius,y*radius};
    }
    private static double[] disk(long seed,long index) {
        double u=Math.max(0x1.0p-53,uniform(seed ^ index*0x94d049bb133111ebL));
        double radius=Math.tanh(.6*Math.sqrt(-2*Math.log(u)));
        double angle=2*Math.PI*uniform(seed ^ index*0xbf58476d1ce4e5b9L ^ 0x632be59bd9b4e019L);
        return new double[]{radius*Math.cos(angle),radius*Math.sin(angle)};
    }
    private static void applyJitter(Config c, Snap s, long now) {
        double[] offset=jitterOffset(c,now);
        s.lat = Math.max(-90.0, Math.min(90.0, s.lat + offset[1] / 111320.0));
        double cos = Math.max(0.001, Math.abs(Math.cos(Math.toRadians(s.lat))));
        s.lon += offset[0] / (111320.0 * cos);
        s.lon = ((s.lon + 540.0) % 360.0) - 180.0;
    }

    private static long mix(long x) {
        x ^= x >>> 30;
        x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27;
        x *= 0x94d049bb133111ebL;
        x ^= x >>> 31;
        return x;
    }
    private static double uniform(long value) {return (mix(value)>>>11)*0x1.0p-53;}

    private static double finiteClamp(double value, double min, double max, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static void applyCoord(Config c, Snap s) {
        if (!c.wgs84) return;
        double[] w = CoordTransform.gcj2wgs(s.lat, s.lon);
        s.lat = w[0];
        s.lon = w[1];
    }

    /** 路径总长度（米） */
    public static double totalDistance(double[][] pts) {
        if (pts == null || pts.length < 2) return 0;
        double[] cum = cumulative(pts);
        return cum[cum.length - 1];
    }

    private static synchronized double[] cumulative(double[][] pts) {
        if (cachedPts == pts && cachedCum != null) return cachedCum;
        double[] cum = new double[pts.length];
        for (int i = 1; i < pts.length; i++) {
            cum[i] = cum[i - 1] + distance(pts[i - 1][0], pts[i - 1][1], pts[i][0], pts[i][1]);
        }
        cachedPts = pts;
        cachedCum = cum;
        return cum;
    }

    public static double distance(double lat1, double lon1, double lat2, double lon2) {
        final double r = 6371000.0;
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        if (a < 0) a = 0;
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    public static double bearing(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        double deg = Math.toDegrees(Math.atan2(y, x));
        return (deg + 360.0) % 360.0;
    }
}
