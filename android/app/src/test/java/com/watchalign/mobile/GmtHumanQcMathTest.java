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

    @Test public void challengeImageStyleSmallGapPlusFavourableRehautIsStrong() {
        GmtHumanQcMath.ClearanceDecision r=GmtDirectionalClearancePolicy.assess(
                0.066,1.001,GmtHumanQcMath.GapTrend.INFLATED,GmtHumanQcMath.PoseLabel.CORRECTABLE);
        assertEquals(GmtHumanQcMath.Attention.STRONG,r.attention);
        assertEquals(GmtHumanQcMath.GapTrend.INFLATED,r.trend);
    }

    @Test public void favourableRehautCanStrengthenEvenWhenPhotoNeedsRetake() {
        GmtHumanQcMath.ClearanceDecision r=GmtDirectionalClearancePolicy.assess(
                0.080,Double.NaN,GmtHumanQcMath.GapTrend.INFLATED,GmtHumanQcMath.PoseLabel.RETAKE);
        assertEquals(GmtHumanQcMath.Attention.STRONG,r.attention);
    }

    @Test public void compressedRehautDoesNotOverstateSmallGap() {
        GmtHumanQcMath.ClearanceDecision r=GmtDirectionalClearancePolicy.assess(
                0.080,1.00,GmtHumanQcMath.GapTrend.COMPRESSED,GmtHumanQcMath.PoseLabel.CORRECTABLE);
        assertEquals(GmtHumanQcMath.Attention.CHECK,r.attention);
        assertEquals(GmtHumanQcMath.GapTrend.COMPRESSED,r.trend);
    }

    @Test public void favourablePerspectiveDoesNotCreateLargeGapDefect() {
        GmtHumanQcMath.ClearanceDecision r=GmtDirectionalClearancePolicy.assess(
                0.220,1.00,GmtHumanQcMath.GapTrend.INFLATED,GmtHumanQcMath.PoseLabel.CORRECTABLE);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,r.attention);
    }

    @Test public void realGapEnhancingPoseBottomRehautWiderMeansInflatedAt12() {
        GmtRehautSectorAnalyzer.Result s=new GmtRehautSectorAnalyzer.Result(
                24.0,20.0,29.0,20.0,0.8,0.8,0.8,0.8);
        assertEquals(GmtHumanQcMath.GapTrend.INFLATED,s.gapTrendAt12());
        assertTrue(s.verticalAsymmetry<-0.075);
    }

    @Test public void oppositePoseTopRehautWiderMeansCompressedAt12() {
        GmtRehautSectorAnalyzer.Result s=new GmtRehautSectorAnalyzer.Result(
                29.0,20.0,24.0,20.0,0.8,0.8,0.8,0.8);
        assertEquals(GmtHumanQcMath.GapTrend.COMPRESSED,s.gapTrendAt12());
        assertTrue(s.verticalAsymmetry>0.075);
    }

    @Test public void realStraightBaselineRemainsNeutral() {
        GmtRehautSectorAnalyzer.Result s=new GmtRehautSectorAnalyzer.Result(
                24.0,20.0,22.0,16.0,1.0,1.0,0.95,1.0);
        assertEquals(GmtHumanQcMath.GapTrend.NEUTRAL,s.gapTrendAt12());
        assertEquals(0.043478,s.verticalAsymmetry,0.001);
    }

    @Test public void noisyGlobalRehautDoesNotHideStrongLocalSectors() {
        GmtRehautPoseAnalyzer.Result global=new GmtRehautPoseAnalyzer.Result(
                12,6,5,13,9,0.33,0.44,0.33,0.44,0.70,45,0.28,0.33,0.41,100,108);
        GmtRehautSectorAnalyzer.Result sectors=new GmtRehautSectorAnalyzer.Result(
                6.0,10.0,12.0,10.0,0.8,0.8,0.8,0.8);
        GmtEllipsePoseAnalyzer.Result ellipse=new GmtEllipsePoseAnalyzer.Result(0.992,7.3,130.0,0.0,1.0);
        GmtHumanQcMath.PoseDecision p=GmtHumanPosePolicy.classify(global,sectors,ellipse,78.0);
        assertEquals(GmtHumanQcMath.PoseLabel.CORRECTABLE,p.label);
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
        double s=GmtHumanQcMath.normalizedClearancePerspectiveScale(0.96,90.0,0.0);
        assertTrue(s>1.0);
    }

    @Test public void localPerspectiveScaleShowsVerticalTiltCompressesRadialGap() {
        double s=GmtHumanQcMath.normalizedClearancePerspectiveScale(0.96,0.0,0.0);
        assertTrue(s<1.0);
    }
}
