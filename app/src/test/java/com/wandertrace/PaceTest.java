package com.wandertrace;

import static org.junit.Assert.*;
import org.junit.Test;

public class PaceTest {
    @Test public void speedInMetresPerSecondConvertsToMinutesPerKilometre() {
        assertEquals("5′33″",Pace.format(3));assertEquals("6′40″",Pace.format(2.5));
        assertEquals("10′00″",Pace.format(1000.0/599.6));
    }
    @Test public void stationaryAndInvalidInputsDoNotInventAPace() {
        assertEquals("—",Pace.format(0));assertEquals("—",Pace.format(Double.NaN));
        assertTrue(Pace.summary(0,10).contains("静止"));
        assertTrue(Pace.summary(-1,10).contains("有效速度"));
    }
    @Test public void randomPaceRangeIsOrderedFastestToSlowest() {
        assertTrue(Pace.summary(3,10).contains("5′03″–6′10″"));
        assertTrue(Pace.summary(3,0).contains("匀速"));
        assertFalse(Pace.summary(3,51).contains("随机段参考"));
    }
}
