package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class QcExtendedMathTest {
    @Test public void axisErrorWrapsAcross180() {
        assertEquals(2.0,QcExtendedMath.smallestAxisError(179,-3),1e-9);
        assertEquals(-2.0,QcExtendedMath.smallestAxisError(-179,3),1e-9);
    }

    @Test public void orientationIsDiagnosticOnlyUntilComponentIsolationIsReliable() {
        assertEquals(0,QcExtendedMath.orientationSeverity(0.5));
        assertEquals(0,QcExtendedMath.orientationSeverity(1.2));
        assertEquals(0,QcExtendedMath.orientationSeverity(4.1));
        assertEquals(0,QcExtendedMath.orientationSeverity(22.63));
        assertEquals(0,QcExtendedMath.orientationSeverity(48.5));
    }

    @Test public void apparentDigitHeightMagnificationIsDiagnosticOnly() {
        assertEquals(0,QcExtendedMath.magnificationMatchSeverity(90.0));
        assertEquals(0,QcExtendedMath.magnificationMatchSeverity(109.7));
        assertEquals(0,QcExtendedMath.magnificationMatchSeverity(130.0));
    }

    @Test public void localTrackSeverityThresholds() {
        assertEquals(0,QcExtendedMath.localTrackSeverity(0.4));
        assertEquals(1,QcExtendedMath.localTrackSeverity(0.9));
        assertEquals(2,QcExtendedMath.localTrackSeverity(1.8));
    }

    @Test public void dateCenterSeverityUsesHorizontalNumeralCenterOnly() {
        assertEquals(0,QcExtendedMath.dateCenterSeverity(3,4));
        assertEquals(1,QcExtendedMath.dateCenterSeverity(7,2));
        assertEquals(0,QcExtendedMath.dateCenterSeverity(2,14));
        assertEquals(2,QcExtendedMath.dateCenterSeverity(14,2));
    }

    @Test public void dateAxisSeverityRequiresARepeatableLargeOffset() {
        assertEquals(0,QcExtendedMath.dateAxisSeverity(3.0));
        assertEquals(1,QcExtendedMath.dateAxisSeverity(5.5));
        assertEquals(2,QcExtendedMath.dateAxisSeverity(8.0));
        assertEquals(5.5/6.0,QcExtendedMath.minuteTrackUnits(5.5),1e-9);
    }

    @Test public void threeOClockHighDateProducesNegativeOffset() {
        double cx=100,cy=100,r=60;
        double angle=Math.toRadians(84.0);
        double x=cx+Math.sin(angle)*r;
        double y=cy-Math.cos(angle)*r;
        assertEquals(-6.0,QcExtendedMath.localDateAxisOffsetDeg(cx,cy,x,y,3,0),1e-6);
        assertEquals(-1.0,QcExtendedMath.minuteTrackUnits(QcExtendedMath.localDateAxisOffsetDeg(cx,cy,x,y,3,0)),1e-6);
    }

    @Test public void nineOClockProfileWorksForLeftHandedSprite() {
        double cx=100,cy=100,r=60;
        double angle=Math.toRadians(270.0);
        double x=cx+Math.sin(angle)*r;
        double y=cy-Math.cos(angle)*r;
        assertEquals(0.0,QcExtendedMath.localDateAxisOffsetDeg(cx,cy,x,y,9,0),1e-6);
    }

    @Test public void cyclopsApertureSeparationIsBounded() {
        assertEquals(0,QcExtendedMath.cyclopsApertureOffsetSeverity(0.02));
        assertEquals(1,QcExtendedMath.cyclopsApertureOffsetSeverity(0.06));
        assertEquals(2,QcExtendedMath.cyclopsApertureOffsetSeverity(0.10));
    }

    @Test public void fineQcIsPerspectiveGated() {
        assertTrue(QcExtendedMath.perspectiveAllowsFineQc(7.3));
        assertFalse(QcExtendedMath.perspectiveAllowsFineQc(14.0));
        assertFalse(QcExtendedMath.perspectiveAllowsFineQc(Double.NaN));
    }

    @Test public void unavailablePerspectiveHasAccurateReason() {
        assertEquals("perspective could not be verified from this photo",QcExtendedMath.fineQcReason(Double.NaN));
        assertEquals("perspective distortion is too high for fine grading",QcExtendedMath.fineQcReason(16.0));
        assertEquals("",QcExtendedMath.fineQcReason(7.3));
    }

    @Test public void strongerFindingAlwaysRanksAheadOfMilderFinding() {
        assertTrue(QcExtendedMath.findingPriority(2,2.1)>QcExtendedMath.findingPriority(1,50.0));
        assertTrue(QcExtendedMath.findingPriority(2,6.1)>QcExtendedMath.findingPriority(2,1.6));
    }
}
