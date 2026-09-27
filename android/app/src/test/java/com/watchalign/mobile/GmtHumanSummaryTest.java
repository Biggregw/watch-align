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

    /** r/RepTimeQC p3hHVMB: point leans clockwise, top edge level -> "skewed", not "rotated". */
    @Test public void pointLeaningWithLevelTopEdgeIsCalledSkewed() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.alignment = GmtHumanQcMath.Attention.CHECK; in.rotationDeg = 1.63; in.baseTiltDeg = 0.2;
        in.spacing59 = 0.15; in.spacing01 = 0.16;
        String s = GmtHumanSummary.alignmentLine(in);
        assertTrue(s, s.contains("skewed: the point leans clockwise by about 1.6° but the top edge is level"));
    }

    @Test public void wholeTriangleTurnedIsCalledRotated() {
        GmtHumanSummary.Input in = base();
        in.alignment = GmtHumanQcMath.Attention.STRONG; in.rotationDeg = -2.4; in.baseTiltDeg = -2.1;
        String s = GmtHumanSummary.alignmentLine(in);
        assertTrue(s, s.contains("rotated: the whole triangle is turned anticlockwise by about 2.4°"));
    }

    @Test public void unevenSpacingWithoutLeanIsCalledOffCentre() {
        GmtHumanSummary.Input in = base();
        in.alignment = GmtHumanQcMath.Attention.CHECK; in.rotationDeg = 0.3; in.baseTiltDeg = 0.1;
        in.spacing59 = 0.10; in.spacing01 = 0.19;
        String s = GmtHumanSummary.alignmentLine(in);
        assertTrue(s, s.contains("off-centre: the triangle sits closer to the 59 tick than the 01 tick"));
    }

    @Test public void sixBatonOffToTheLeftIsNamed() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CLEAR; in.alignment = GmtHumanQcMath.Attention.CLEAR;
        in.sixValid = true; in.sixStable = true; in.sixAttention = GmtHumanQcMath.Attention.CHECK;
        in.sixOffCentre = true; in.sixCentring = -0.14; in.sixRotationDeg = 0.3; in.sixWidthPx = 30;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("6 baton: possibly off-centre: it sits to the left (towards the 31 tick)"));
        assertTrue(s, s.contains("Bottom line: 1 thing to check: the 6 baton position."));
    }

    @Test public void clearTwelveAndSixSaysBoth() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CLEAR; in.alignment = GmtHumanQcMath.Attention.CLEAR;
        in.sixValid = true; in.sixStable = true; in.sixAttention = GmtHumanQcMath.Attention.CLEAR;
        in.sixCentring = 0.01; in.sixRotationDeg = -0.1; in.sixWidthPx = 30;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("6 baton: centred between the 29 and 31 ticks and straight"));
        assertTrue(s, s.contains("Bottom line: nothing flagged at 12 or 6."));
    }

    @Test public void threeFlagsAreListed() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CHECK; in.observedGap = 0.05;
        in.alignment = GmtHumanQcMath.Attention.CHECK; in.rotationDeg = 1.2;
        in.sixValid = true; in.sixStable = true; in.sixAttention = GmtHumanQcMath.Attention.STRONG;
        in.sixRotated = true; in.sixCentring = 0.0; in.sixRotationDeg = 3.4; in.sixWidthPx = 30;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("3 things to check: the gap at 12, the 12 marker alignment and the 6 baton position."));
        assertTrue(s, s.contains("6 baton: visibly rotated clockwise."));
    }

    @Test public void sixNotFoundIsNeverAPass() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CLEAR; in.alignment = GmtHumanQcMath.Attention.CLEAR;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("6 baton: not measured"));
        assertTrue(s, s.contains("Bottom line: nothing flagged at 12."));
        assertTrue(s, s.contains("The 6 baton could not be judged here"));
    }

    /** rep_cf_6I00d8w image_01 (alpha56): the gap read 0.07 on the phone and 0.14 on the desktop. */
    @Test public void readingThatMovesWithResizingIsNotJudged() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.CORRECTABLE;
        in.gap = GmtHumanQcMath.Attention.UNASSESSABLE; in.observedGap = 0.074;
        in.alignment = GmtHumanQcMath.Attention.UNASSESSABLE; in.rotationDeg = -1.2;
        in.gapUnstable = true; in.rotUnstable = true; in.gapMin = 0.074; in.gapMax = 0.226; in.rotMin = -1.5; in.rotMax = -0.7;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("12 gap: not judged: the reading changes when the photo is resized slightly (it read between 0.07 and 0.23)"));
        assertTrue(s, s.contains("12 alignment: not judged: the reading changes when the photo is resized slightly"));
        assertTrue(s, s.contains("Bottom line: nothing flagged, but the 12 reading changes when the photo is resized slightly"));
        assertFalse(s, s.contains("nothing flagged at 12"));
    }

    @Test public void agreedConcernSurvivesButIsQualified() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CHECK; in.observedGap = 0.050;
        in.alignment = GmtHumanQcMath.Attention.UNASSESSABLE;
        in.gapUnstable = true; in.rotUnstable = true; in.gapMin = 0.045; in.gapMax = 0.062; in.rotMin = -0.2; in.rotMax = 1.9;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("12 gap: small in every re-measurement (it read between 0.05 and 0.06)"));
        assertTrue(s, s.contains("Bottom line: 1 thing to check: the gap at 12. The 12 reading changes when the photo is resized slightly"));
    }

    @Test public void onlyTheUnstablePartIsWithheld() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.gap = GmtHumanQcMath.Attention.CLEAR; in.observedGap = 0.091;
        in.alignment = GmtHumanQcMath.Attention.UNASSESSABLE; in.rotationDeg = 1.65;
        in.rotUnstable = true; in.gapMin = 0.088; in.gapMax = 0.092; in.rotMin = 0.5; in.rotMax = 1.7;
        String s = GmtHumanSummary.build(in);
        assertTrue(s, s.contains("12 gap: normal."));
        assertTrue(s, s.contains("12 alignment: not judged: the reading changes when the photo is resized slightly (it read between +0.5° and +1.7°)"));
        assertTrue(s, s.contains("Bottom line: nothing flagged, but the 12 rotation reading changes when the photo is resized slightly"));
    }

    /** Emulator, 6I00d8w image_01: gap too close to call and rotation withheld is not "nothing flagged at 12". */
    @Test public void withheldRotationWithUnresolvedGapIsNotAPass() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.CORRECTABLE;
        in.gap = GmtHumanQcMath.Attention.UNASSESSABLE; in.observedGap = 0.074; in.gapResolutionLimited = true; in.pxPerGap = 0.022;
        in.alignment = GmtHumanQcMath.Attention.UNASSESSABLE; in.rotationDeg = -1.17;
        in.rotUnstable = true; in.gapMin = 0.074; in.gapMax = 0.148; in.rotMin = -1.17; in.rotMax = 0.19;
        String s = GmtHumanSummary.build(in);
        assertFalse(s, s.contains("nothing flagged at 12"));
        assertTrue(s, s.contains("Bottom line: nothing flagged, but the 12 rotation reading changes when the photo is resized slightly"));
    }

    /** User photo (alpha57): 0.084 vs 0.156 was described as "even spacing either side". */
    @Test public void unevenSpacingIsNeverCalledEven() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.alignment = GmtHumanQcMath.Attention.CLEAR; in.rotationDeg = 0.2; in.spacing59 = 0.14; in.spacing01 = 0.18;
        String s = GmtHumanSummary.alignmentLine(in);
        assertFalse(s, s.contains("even spacing"));
        assertTrue(s, s.contains("slightly uneven, within what genuine photos show"));
    }

    @Test public void offCentreFlagIsNamed() {
        GmtHumanSummary.Input in = base();
        in.pose = GmtHumanQcMath.PoseLabel.GOOD;
        in.alignment = GmtHumanQcMath.Attention.CHECK; in.rotationDeg = 0.53; in.baseTiltDeg = -0.42;
        in.spacing59 = 0.084; in.spacing01 = 0.156;
        String s = GmtHumanSummary.alignmentLine(in);
        assertTrue(s, s.contains("possibly off-centre: the triangle sits closer to the 59 tick than the 01 tick"));
        in.gap = GmtHumanQcMath.Attention.STRONG; in.observedGap = 0.031;
        assertTrue(GmtHumanSummary.build(in), GmtHumanSummary.build(in).contains("2 things to check: the gap at 12 and the 12 marker position (off-centre)."));
    }
}
