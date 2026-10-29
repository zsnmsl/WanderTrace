package com.wandertrace;
public class EngineCheck {
    public static void main(String[] args) {
        Config route = new Config();
        route.mode = 1; route.loop = true; route.points = new double[][]{{39, 116}, {39.1, 116}};
        route.startTime = 1000000;
        double previous = -1;
        for (long t = route.startTime; t < route.startTime + 700000; t += 100) {
            RouteEngine.Snap a = RouteEngine.compute(route, t), b = RouteEngine.compute(route, t);
            require(a.lat == b.lat && a.lon == b.lon && a.speedMps == b.speedMps, "determinism");
            require(a.speedMps >= 20.0 / 3.6 * .5 - 1e-7 && a.speedMps <= 20.0 / 3.6 * 1.5 + 1e-7, "speed bounds");
            if (previous >= 0) require(Math.abs(a.speedMps - previous) < .1, "speed continuity");
            previous = a.speedMps;
        }
        Config single = new Config();
        for (long t = 0; t < 100000; t += 100) {
            RouteEngine.Snap s = RouteEngine.compute(single, t);
            require(RouteEngine.distance(single.lat, single.lon, s.lat, s.lon) < 2.01, "jitter bound");
        }
        // A short non-looping route brakes from the first point to rest.
        route.loop = false; route.speedRandomPercent = 0; route.points = new double[][]{{39, 116}, {39.0001, 116}};
        double initial = RouteEngine.compute(route, route.startTime).speedMps;
        double last = initial;
        double lastDistance = 0;
        for (long dt = 100; dt <= 10000; dt += 100) {
            RouteEngine.Snap s = RouteEngine.compute(route, route.startTime + dt);
            Config reference = new Config();
            RouteEngine.Snap jitter = RouteEngine.compute(reference, route.startTime + dt);
            // Subtract the same deterministic latitude error to isolate route displacement.
            double position = RouteEngine.distance(39, 116, s.lat - (jitter.lat - 39), 116);
            require(s.speedMps <= last + 1e-8, "braking speed");
            double expected = (last + s.speedMps) * .05;
            require(Math.abs((position - lastDistance) - expected) < .005, "speed and distance integration");
            last = s.speedMps; lastDistance = position;
        }
        require(last == 0, "braking stop");
        require(Math.abs(lastDistance - RouteEngine.totalDistance(route.points)) < .005, "route end");
        System.out.println("RouteEngine checks passed: deterministic speed, continuity, bounds, jitter and integrated braking.");
    }
    private static void require(boolean test, String label) {
        if (!test) throw new AssertionError(label);
    }
}
