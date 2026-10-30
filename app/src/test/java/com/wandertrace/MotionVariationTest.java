package com.wandertrace;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class MotionVariationTest {
    private Config route() {
        Config c=new Config();c.mode=Config.MODE_ROUTE;c.startTime=1000000;
        c.points=new double[][]{{39,116},{39.3,116}};c.loop=false;
        c.speedKmh=10.8;c.speedRandomPercent=10;return c;
    }
    private double distance(Config c,long elapsed) {
        var s=RouteEngine.compute(c,c.startTime+elapsed);
        return RouteEngine.distance(39,116,s.lat-RouteEngine.jitterOffset(c,c.startTime+elapsed)[1]/111320.0,116);
    }
    @Test public void distanceMatchesTheIntegralOfTheReportedLinearSpeed() {
        Config c=route();c.speedRandomPercent=50;
        double priorDistance=distance(c,0),priorSpeed=RouteEngine.compute(c,c.startTime).speedMps;
        for(long t=100;t<=1500000;t+=100) {
            var s=RouteEngine.compute(c,c.startTime+t);double d=distance(c,t);
            assertEquals((priorSpeed+s.speedMps)*.05,d-priorDistance,1e-6);
            assertTrue(s.speedMps>=1.5 && s.speedMps<=4.5);
            priorDistance=d;priorSpeed=s.speedMps;
        }
    }
    @Test public void kilometreSplitsVaryWithoutRestartingAtEachKilometre() {
        Config c=route();double previous=0,min=Double.POSITIVE_INFINITY,max=0;
        for(int kilometre=1;kilometre<=6;kilometre++) {
            long low=0,high=3000000;
            while(high-low>1) {long mid=(low+high)/2;if(distance(c,mid)<kilometre*1000) low=mid;else high=mid;}
            double split=high/1000.0-previous;previous=high/1000.0;
            min=Math.min(min,split);max=Math.max(max,split);
        }
        assertTrue("kilometre times should vary",max-min>2);
    }
    @Test public void sessionsDoNotReuseTheOld640SecondSpeedTable() {
        Config c=route();double a=RouteEngine.compute(c,c.startTime+123000).speedMps;
        double b=RouteEngine.compute(c,c.startTime+763000).speedMps;
        assertNotEquals(a,b,1e-9);c.startTime+=123456;
        assertNotEquals(a,RouteEngine.compute(c,c.startTime+123000).speedMps,1e-9);
    }
    @Test public void zeroRandomnessRemainsConstantAndMissingStartTimeStaysAtTheStart() {
        Config c=route();c.speedRandomPercent=0;
        assertEquals(3,RouteEngine.compute(c,c.startTime+500000).speedMps,1e-12);
        assertEquals(1500,distance(c,500000),1e-6);
        c.startTime=0;assertEquals(0,distance(c,1700000000000L),1e-6);
        c.loop=true;c.startTime=1;assertTrue(Double.isFinite(RouteEngine.compute(c,Long.MAX_VALUE).lat));
    }
    @Test public void driftIsContinuousBoundedAndSharedWithinASession() {
        Config c=route();c.mode=Config.MODE_SINGLE;c.lat=39;c.lon=116;
        for(double radius:new double[]{1,2,3}) {
            c.jitterMeters=radius;double[] previous=null,previousDelta=null;
            for(long t=0;t<300000;t+=100) {
                double[] xy=RouteEngine.jitterOffset(c,c.startTime+t);
                assertArrayEquals(xy,RouteEngine.jitterOffset(c,c.startTime+t),0);
                assertTrue(Math.hypot(xy[0],xy[1])<=radius+1e-9);
                if(previous!=null) {
                    double[] delta={xy[0]-previous[0],xy[1]-previous[1]};
                    assertTrue(Math.hypot(delta[0],delta[1])<.12);
                    if(previousDelta!=null) assertTrue("drift velocity should not kink at noise knots",
                            Math.hypot(delta[0]-previousDelta[0],delta[1]-previousDelta[1])/.1<.1);
                    previousDelta=delta;
                }
                previous=xy;
            }
        }
        double[] old=RouteEngine.jitterOffset(c,c.startTime+123000);c.startTime+=1000;
        double[] next=RouteEngine.jitterOffset(c,c.startTime+123000);
        assertTrue(Math.hypot(old[0]-next[0],old[1]-next[1])>.01);
    }
    @Test public void legacyZeroRandomnessIsPreservedAndNewConfigsUseTenPercent() {
        assertEquals(10,new Config().speedRandomPercent,0);
        assertEquals(0,Config.fromJson("{\"speedRandomPercent\":0}").speedRandomPercent,0);
    }
}
