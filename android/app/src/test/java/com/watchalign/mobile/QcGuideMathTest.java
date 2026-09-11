package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class QcGuideMathTest {
    @Test public void twelveOClockIsTop() {
        QcGuideMath.P p=QcGuideMath.polar(100,100,50,QcGuideMath.idealAngleDeg(12,0));
        assertEquals(100,p.x,1e-9); assertEquals(50,p.y,1e-9);
    }
    @Test public void threeOClockIsRight() {
        QcGuideMath.P p=QcGuideMath.polar(100,100,50,QcGuideMath.idealAngleDeg(3,0));
        assertEquals(150,p.x,1e-9); assertEquals(100,p.y,1e-9);
    }
    @Test public void globalRotationMovesWholeGuide() {
        QcGuideMath.P p=QcGuideMath.polar(0,0,10,QcGuideMath.idealAngleDeg(12,30));
        assertEquals(5,p.x,1e-9); assertEquals(-8.6602540378,p.y,1e-9);
    }
    @Test public void severityUsesAngularAndRadial() {
        assertEquals(0,QcGuideMath.severity(0.5,2.0));
        assertEquals(1,QcGuideMath.severity(1.2,2.0));
        assertEquals(1,QcGuideMath.severity(0.4,4.0));
        assertEquals(2,QcGuideMath.severity(2.1,0.2));
        assertEquals(2,QcGuideMath.severity(0.2,5.1));
    }
    @Test public void perspectiveGateMatchesQcPolicy() {
        assertTrue(QcGuideMath.alignmentGuideReliable(7.3));
        assertTrue(QcGuideMath.alignmentGuideReliable(13.9));
        assertFalse(QcGuideMath.alignmentGuideReliable(14.0));
        assertFalse(QcGuideMath.alignmentGuideReliable(24.0));
        assertTrue(QcGuideMath.alignmentGuideReliable(Double.NaN));
    }
}
