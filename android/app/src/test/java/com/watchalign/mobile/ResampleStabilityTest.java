package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The resize check (alpha56): which re-measurement spreads withhold a verdict. */
public class ResampleStabilityTest {
    private static GmtTwelveLandmarkAnalyzer.Result r(double width, double g0, double g1, double r0, double r1, boolean same) {
        GmtTwelveLandmarkAnalyzer.Result x = new GmtTwelveLandmarkAnalyzer.Result(
                g0, 0, r0, 0, 0.15, 0.15, 0, width, 0, 6.0, 20, 0, true);
        x.stabilityRun = true; x.stabilitySameEdge = same;
        x.gapMin = g0; x.gapMax = g1; x.rotMin = r0; x.rotMax = r1;
        return x;
    }

    @Test public void notRunCountsAsStable() {
        GmtTwelveLandmarkAnalyzer.Result x = r(50, 0.09, 0.09, 0, 0, true);
        x.stabilityRun = false;
        assertTrue(x.resampleGapStable()); assertTrue(x.resampleRotStable());
    }

    @Test public void subPixelMovementIsStable() {
        GmtTwelveLandmarkAnalyzer.Result x = r(50, 0.090, 0.100, 0.2, 0.9, true);   // 0.5 px, 0.75 px
        assertTrue(x.resampleGapStable()); assertTrue(x.resampleRotStable());
    }

    /** 6I00d8w image_01: 0.07 to 0.23 on a 45 px triangle, and a different edge found. */
    @Test public void differentEdgeIsUnstable() {
        GmtTwelveLandmarkAnalyzer.Result x = r(45, 0.074, 0.226, -1.5, -0.7, false);
        assertFalse(x.resampleGapStable()); assertFalse(x.resampleRotStable());
    }

    @Test public void largeGapMovementAcrossTheLimitIsUnstable() {
        assertFalse(r(50, 0.060, 0.110, 0, 0, true).resampleGapStable());
    }

    @Test public void largeGapMovementWellAboveTheLimitCannotChangeTheVerdict() {
        assertTrue(r(50, 0.100, 0.160, 0, 0, true).resampleGapStable());
    }

    /** Official render at full size: +0.04 to +0.90 deg on 61 px, all below the visible level. */
    @Test public void rotationMovementBelowTheVisibleLevelIsStable() {
        assertTrue(r(61.3, 0.09, 0.09, 0.04, 0.90, true).resampleRotStable());
    }

    /** p3hHVMB image_00: +0.5 to +1.7 deg on 59 px crosses 1.0 deg. */
    @Test public void rotationCrossingTheVisibleLevelIsUnstable() {
        assertFalse(r(59.4, 0.09, 0.09, 0.5, 1.7, true).resampleRotStable());
    }
}
