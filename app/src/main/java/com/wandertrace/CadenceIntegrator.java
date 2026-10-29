package com.wandertrace;

/** Integrates a piecewise-linear cadence, independent of Android for repeatable tests. */
final class CadenceIntegrator {
    private long lastTime = -1;
    private long session = Long.MIN_VALUE;
    private double cadence;
    private double fraction;
    private long total;

    long advance(long time, boolean active, long startTime, double target) {
        if (!active || startTime != session || lastTime < 0 || time < lastTime) {
            cadence = 0;
            fraction = 0;
            session = startTime;
            lastTime = time;
            return total;
        }
        // No listeners polling the provider for a long interval means no active stream.
        double seconds = Math.min(2, Math.max(0, (time - lastTime) / 1000.0));
        lastTime = time;
        double boundedTarget = Math.max(0, Math.min(240, target));
        double next = cadence + Math.max(-12 * seconds, Math.min(12 * seconds, boundedTarget - cadence));
        fraction += (cadence + next) * 0.5 * seconds / 60;
        cadence = next;
        long steps = (long) fraction;
        fraction -= steps;
        total += steps;
        return total;
    }

    double cadence() { return cadence; }
}
