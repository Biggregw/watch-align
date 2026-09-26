package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

public class GmtHumanQcMathTest {
    @Test public void subLikeSlightVisibleRotationIsHighlighted() {
        GmtHumanQcMath.RotationDecision r=GmtHumanQcMath.assessRotation(
                -1.8,-1.6,0.07,34.0,GmtHumanQcMath.PoseLabel.GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CHECK,r.attention);
        assertTrue(r.visibleRisePx>1.0);
    }

    @Test public void tinyUnresolvedRotationCanClear() {
        GmtHumanQcMath.RotationDecision r=GmtHumanQcMath.assessRotation(
                0.6,0.4,0.02,30.0,GmtHumanQcMath.PoseLabel.GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,r.attention);
    }

    @Test public void unequalSideSpacingAloneStillGetsAttention() {
        GmtHumanQcMath.RotationDecision r=GmtHumanQcMath.assessRotation(
                0.4,0.2,0.14,40.0,GmtHumanQcMath.PoseLabel.GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CHECK,r.attention);
    }

    @Test public void poorPoseDoesNotSilentlyClearSubtleRotation() {
        GmtHumanQcMath.RotationDecision r=GmtHumanQcMath.assessRotation(
                1.4,1.2,0.04,42.0,GmtHumanQcMath.PoseLabel.RETAKE,true);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,r.attention);
    }

    @Test public void inflatedPerspectiveMakesLowGapStrongerEvidence() {
        GmtHumanQcMath.ClearanceDecision r=GmtHumanQcMath.assessLowClearance(
                0.115,1.04,GmtHumanQcMath.PoseLabel.GOOD);
        assertEquals(GmtHumanQcMath.Attention.STRONG,r.attention);
        assertEquals(GmtHumanQcMath.GapTrend.INFLATED,r.trend);
        assertTrue(r.correctedEstimate<r.observedGap);
    }

    @Test public void compressedPerspectiveMakesLowGapAmbiguous() {
        GmtHumanQcMath.ClearanceDecision r=GmtHumanQcMath.assessLowClearance(
                0.115,0.96,GmtHumanQcMath.PoseLabel.CORRECTABLE);
        assertEquals(GmtHumanQcMath.Attention.CHECK,r.attention);
        assertEquals(GmtHumanQcMath.GapTrend.COMPRESSED,r.trend);
        assertTrue(r.correctedEstimate>r.observedGap);
    }

    @Test public void largeGapIsNotTreatedAsDefect() {
        GmtHumanQcMath.ClearanceDecision r=GmtHumanQcMath.assessLowClearance(
                0.220,1.00,GmtHumanQcMath.PoseLabel.GOOD);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,r.attention);
    }

    @Test public void zeroGapCannotBeRescuedByPerspective() {
        GmtHumanQcMath.ClearanceDecision r=GmtHumanQcMath.assessLowClearance(
                0.0,0.90,GmtHumanQcMath.PoseLabel.CORRECTABLE);
        assertEquals(GmtHumanQcMath.Attention.STRONG,r.attention);
    }

    @Test public void rehautCollapseRequestsRetake() {
        GmtHumanQcMath.PoseDecision p=GmtHumanQcMath.classifyPose(
                0.42,0.80,0.08,0.30,12.0,5.0);
        assertEquals(GmtHumanQcMath.PoseLabel.RETAKE,p.label);
    }

    @Test public void nearFrontalPoseIsGood() {
        GmtHumanQcMath.PoseDecision p=GmtHumanQcMath.classifyPose(
                0.91,0.80,0.04,0.06,5.0,4.0);
        assertEquals(GmtHumanQcMath.PoseLabel.GOOD,p.label);
    }

    @Test public void localPerspectiveScaleShowsHorizontalTiltInflatesRadialGap() {
        // Minor axis at 3 o'clock compresses tangential marker width more than
        // the 12-o'clock radial gap, so normalized gap appears larger.
        double s=GmtHumanQcMath.normalizedClearancePerspectiveScale(0.96,90.0,0.0);
        assertTrue(s>1.0);
    }

    @Test public void localPerspectiveScaleShowsVerticalTiltCompressesRadialGap() {
        double s=GmtHumanQcMath.normalizedClearancePerspectiveScale(0.96,0.0,0.0);
        assertTrue(s<1.0);
    }
}
