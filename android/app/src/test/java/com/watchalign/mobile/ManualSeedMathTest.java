package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ManualSeedMathTest {
    @Test public void verticalDialEdgesRecoverCenterRadiusAndZeroRoll() {
        double[] s = ManualSeedMath.fromOppositeDialEdges(500, 200, 500, 800);
        assertNotNull(s);
        assertEquals(500.0, s[0], 1e-9);
        assertEquals(500.0, s[1], 1e-9);
        assertEquals(300.0, s[2], 1e-9);
        assertEquals(0.0, s[3], 1e-9);
    }

    @Test public void tiltedDialEdgesRecoverRollWithoutNeedingCenterTap() {
        double r = 240.0;
        double roll = 5.0;
        double a = Math.toRadians(roll - 90.0);
        double cx = 620.0, cy = 510.0;
        double x12 = cx + r * Math.cos(a);
        double y12 = cy + r * Math.sin(a);
        double x6 = cx - r * Math.cos(a);
        double y6 = cy - r * Math.sin(a);

        double[] s = ManualSeedMath.fromOppositeDialEdges(x12, y12, x6, y6);
        assertNotNull(s);
        assertEquals(cx, s[0], 1e-9);
        assertEquals(cy, s[1], 1e-9);
        assertEquals(r, s[2], 1e-9);
        assertEquals(roll, s[3], 1e-9);
    }

    @Test public void identicalPointsAreRejected() {
        assertNull(ManualSeedMath.fromOppositeDialEdges(100, 100, 100, 100));
    }
}
