package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class QcExtendedMathTest {
    @Test public void axisErrorWrapsAcross180() {
        assertEquals(2.0,QcExtendedMath.smallestAxisError(179,-3),1e-9);
        assertEquals(-2.0,QcExtendedMath.smallestAxisError(-179,3),1e-9);
    }

    @Test public void orientationSeverityThresholds() {
        assertEquals(0,QcExtendedMath.orientationSeverity(0.5));
        assertEquals(1,QcExtendedMath.orientationSeverity(1.2));
        assertEquals(2,QcExtendedMath.orientationSeverity(2.2));
    }

    @Test public void localTrackSeverityThresholds() {
        assertEquals(0,QcExtendedMath.localTrackSeverity(0.4));
        assertEquals(1,QcExtendedMath.localTrackSeverity(0.9));
        assertEquals(2,QcExtendedMath.localTrackSeverity(1.8));
    }

    @Test public void dateCenterSeverityUsesWorstAxis() {
        assertEquals(0,QcExtendedMath.dateCenterSeverity(3,4));
        assertEquals(1,QcExtendedMath.dateCenterSeverity(7,2));
        assertEquals(2,QcExtendedMath.dateCenterSeverity(2,14));
    }

    @Test public void fineQcIsPerspectiveGated() {
        assertTrue(QcExtendedMath.perspectiveAllowsFineQc(7.3));
        assertFalse(QcExtendedMath.perspectiveAllowsFineQc(14.0));
        assertFalse(QcExtendedMath.perspectiveAllowsFineQc(Double.NaN));
    }
}
