package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

public class DateMagnificationMathTest {
    @Test public void minuteIndexMatchesDateHour() {
        assertEquals(15,QcExtendedMath.minuteIndexForHour(3));
        assertEquals(45,QcExtendedMath.minuteIndexForHour(9));
        assertEquals(0,QcExtendedMath.minuteIndexForHour(12));
    }

    @Test public void oneMinuteTrackDivisionIsSixDegrees() {
        assertEquals(1.0,QcExtendedMath.minuteTrackUnits(6.0),1e-9);
        assertEquals(-1.0,QcExtendedMath.minuteTrackUnits(-6.0),1e-9);
    }

    @Test public void localAnchorOffsetUsesDetectedTrackNotTheoreticalHour() {
        double cx=100,cy=100;
        double r=50;
        double anchor=92.0;
        double a=Math.toRadians(98.0);
        double x=cx+Math.sin(a)*r;
        double y=cy-Math.cos(a)*r;
        assertEquals(6.0,QcExtendedMath.localDateAxisOffsetFromAnchorDeg(cx,cy,x,y,anchor),1e-6);
    }

    @Test public void apparentMagnificationIsRelativeToGenuineDialNormalizedHeight() {
        assertEquals(100.0,QcExtendedMath.apparentMagnificationPercent(0.22,0.22),1e-9);
        assertEquals(110.0,QcExtendedMath.apparentMagnificationPercent(0.242,0.22),1e-9);
        assertEquals(90.0,QcExtendedMath.apparentMagnificationPercent(0.198,0.22),1e-9);
    }

    @Test public void magnificationSeverityHasUsefulTolerance() {
        assertEquals(0,QcExtendedMath.magnificationMatchSeverity(95.0));
        assertEquals(1,QcExtendedMath.magnificationMatchSeverity(92.0));
        assertEquals(2,QcExtendedMath.magnificationMatchSeverity(86.0));
        assertEquals(2,QcExtendedMath.magnificationMatchSeverity(114.0));
    }

    @Test public void invalidMagnificationInputsAreRejected() {
        assertTrue(Double.isNaN(QcExtendedMath.apparentMagnificationPercent(0.0,0.2)));
        assertTrue(Double.isNaN(QcExtendedMath.apparentMagnificationPercent(0.2,0.0)));
    }
}
