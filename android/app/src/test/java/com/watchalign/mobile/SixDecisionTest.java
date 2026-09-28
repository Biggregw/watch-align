package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SixDecisionTest {
    private static GmtHumanQcMath.SixDecision d(double c, double rot, double w, boolean stable) {
        return GmtHumanQcMath.assessSix(c, rot, w, 2.5 * w, GmtHumanQcMath.PoseLabel.GOOD, stable);
    }

    @Test public void centredStraightBatonIsClear() {
        assertEquals(GmtHumanQcMath.Attention.CLEAR, d(0.02, 0.3, 30, true).attention);
    }

    @Test public void offsetOverTheCheckLevelIsFlagged() {
        GmtHumanQcMath.SixDecision x = d(-0.14, 0.2, 30, true);
        assertEquals(GmtHumanQcMath.Attention.CHECK, x.attention);
        assertTrue(x.offCentre);
    }

    @Test public void largeOffsetIsStrong() {
        assertEquals(GmtHumanQcMath.Attention.STRONG, d(0.25, 0.0, 30, true).attention);
    }

    @Test public void tinyBatonIsNotJudged() {
        GmtHumanQcMath.SixDecision x = d(0.3, 0, 15, true);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, x.attention);
        assertTrue(x.tooSmall);
    }

    @Test public void lowConfidenceNeverClears() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, d(0.02, 0.1, 30, false).attention);
        assertEquals(GmtHumanQcMath.Attention.CHECK, d(0.25, 0.1, 30, false).attention);
        // A rotation read from an outline that could not be traced cleanly is not kept (alpha61).
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, d(0.02, 5.0, 30, false).attention);
    }

    @Test public void rotationNeedsAVisibleRise() {
        assertEquals(GmtHumanQcMath.Attention.CHECK, d(0.0, 2.0, 30, true).attention);
        assertEquals(GmtHumanQcMath.Attention.STRONG, d(0.0, 3.5, 30, true).attention);
    }
}
