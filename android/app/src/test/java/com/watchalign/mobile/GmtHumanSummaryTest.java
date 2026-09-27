package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmtHumanSummaryTest {
    private static GmtHumanSummary.Input base() {
        GmtHumanSummary.Input in = new GmtHumanSummary.Input();
        in.twelveValid = true; in.stableFrame = true; in.overlayDrawn = true;
        in.gapTrend = GmtHumanQcMath.GapTrend.NEUTRAL;
        return in;
    }

    /** alpha44 field report, genuine photo: everything CLEAR. */
    @Test public void genuinePhotoSaysNothingFlagged() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CLEAR; in.observedGap = 0.180;
        in.alignment = GmtHumanQcMath.Attention.CLEAR; in.rotationDeg = -0.09;
        in.spacing59 = 0.157; in.spacing01 = 0.157;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.startsWith("SUMMARY\n"));
        assertTrue(s, s.contains("12 gap: normal"));
        assertTrue(s, s.contains("Measured 0.18"));
        assertTrue(s, s.contains("12 alignment: straight and centred"));
        assertTrue(s, s.contains("Bottom line: nothing flagged at 12"));
        assertFalse(s, s.toLowerCase().contains("genuine watch"));
    }

    /** alpha44 field report, rep: small gap CHECK, weak alignment CHECK. */
    @Test public void repPhotoNamesBothThingsToCheck() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.CORRECTABLE;
        in.gap = GmtHumanQcMath.Attention.CHECK; in.observedGap = 0.062;
        in.alignment = GmtHumanQcMath.Attention.CHECK; in.rotationDeg = 1.02;
        in.spacing59 = 0.102; in.spacing01 = 0.134;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("12 gap: small"));
        assertTrue(s, s.contains("Measured 0.06"));
        assertTrue(s, s.contains("a hand touching the triangle"));
        assertTrue(s, s.contains("Bottom line: 2 things to check: the gap at 12 and the 12 marker alignment."));
        assertTrue(s, s.contains("does not prove a watch is genuine or fake"));
    }

    @Test public void missingLandmarksAreNeverAPass() {
        GmtHumanSummary.Input in = base();
        in.twelveValid = false;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("This is not a pass"));
        assertTrue(s, s.contains("could not be checked"));
        assertFalse(s, s.contains("nothing flagged"));
    }

    @Test public void tooAngledPhotoAsksForRetakeEvenWhenClear() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.RETAKE;
        in.gap = GmtHumanQcMath.Attention.CLEAR; in.alignment = GmtHumanQcMath.Attention.CLEAR;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("too angled to rely on that. Retake straight-on."));
    }

    @Test public void backupMethodResultsCarryACaution() {
        GmtHumanSummary.Input in = base();
        in.stableFrame = false; in.pose = GmtHumanQcMath.PoseLabel.CORRECTABLE;
        in.gap = GmtHumanQcMath.Attention.CHECK; in.observedGap = 0.10;
        in.alignment = GmtHumanQcMath.Attention.CLEAR; in.rotationDeg = 0.1; in.spacing59 = 0.15; in.spacing01 = 0.15;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("12 gap: small"));
        assertTrue(s, s.contains("low confidence"));
    }

    @Test public void lowConfidenceFlagsSayHowToConfirm() {
        GmtHumanSummary.Input in = base();
        in.stableFrame = false; in.pose = GmtHumanQcMath.PoseLabel.CORRECTABLE;
        in.gap = GmtHumanQcMath.Attention.UNASSESSABLE; in.alignment = GmtHumanQcMath.Attention.CHECK;
        in.rotationDeg = 8.4; in.spacing59 = 0.07; in.spacing01 = 0.32;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("Bottom line: 1 thing to check: the 12 marker alignment. Measured with low confidence"));
        assertTrue(s, s.contains("12 gap: could not be judged reliably on this photo.\n"));
    }

    /** alpha48 field report on a replica: 0.06 on a ~55 px triangle is within a pixel of the limit. */
    @Test public void resolutionLimitedGapSaysTooCloseToCallNotTouching() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.CORRECTABLE;
        in.gap = GmtHumanQcMath.Attention.UNASSESSABLE; in.gapResolutionLimited = true;
        in.observedGap = 0.062; in.pxPerGap = 1.0 / 55.0; in.gapPx = 0.062 * 55.0;
        in.alignment = GmtHumanQcMath.Attention.CLEAR; in.rotationDeg = 0.2; in.spacing59 = 0.15; in.spacing01 = 0.15;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("12 gap: too close to call at this photo's resolution. Measured 0.06"));
        assertTrue(s, s.contains("1 pixel is 0.018 of gap"));
        assertTrue(s, s.contains("too close to call at this resolution; a closer photo would settle it."));
        assertFalse(s, s.contains("touching"));
    }

    @Test public void strongGapOnlySaysTouchingUnderAPixel() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD; in.gap = GmtHumanQcMath.Attention.STRONG;
        in.observedGap = 0.03; in.gapPx = 3.0; in.pxPerGap = 0.01;
        in.alignment = GmtHumanQcMath.Attention.CLEAR;
        assertFalse(GmtHumanSummary.build(in).contains("touching"));
        in.observedGap = 0.01; in.gapPx = 0.6;
        assertTrue(GmtHumanSummary.build(in).contains("touching or almost touching"));
    }
}
