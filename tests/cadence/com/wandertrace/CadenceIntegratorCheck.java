package com.wandertrace;

/** Standalone deterministic check: javac CadenceIntegrator.java CadenceIntegratorCheck.java. */
public final class CadenceIntegratorCheck {
    public static void main(String[] args) {
        CadenceIntegrator c = new CadenceIntegrator();
        long count = c.advance(0, true, 1, 120);
        check(count == 0, "initial count");
        for (int i = 1; i <= 10; i++) {
            count = c.advance(i * 1000L, true, 1, 120);
            check(c.cadence() == i * 12, "linear acceleration " + i);
        }
        check(count == 10, "acceleration integral");
        for (int i = 11; i <= 20; i++) count = c.advance(i * 1000L, true, 1, 120);
        check(count == 30, "constant cadence integral");
        // A stopped route sets adaptive target to zero; rate approaches zero linearly.
        for (int i = 21; i <= 30; i++) {
            count = c.advance(i * 1000L, true, 1, 0);
            check(c.cadence() == (30 - i) * 12, "linear deceleration " + i);
        }
        check(count == 40, "deceleration integral");
        check(c.advance(31_000, false, 1, 120) == 40, "disabled count");
        check(c.cadence() == 0, "disabled cadence");
        check(c.advance(32_000, true, 2, 120) == 40, "new session resets cadence");
        check(c.advance(33_000, true, 2, 120) == 40 && c.cadence() == 12, "new session ramps");
        System.out.println("CadenceIntegrator checks passed");
    }

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
