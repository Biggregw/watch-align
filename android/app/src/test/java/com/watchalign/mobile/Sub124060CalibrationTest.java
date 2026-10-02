package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class Sub124060CalibrationTest {
    @Test public void rotationBandClassifiesClearCheckStrongAndUnavailable(){
        Sub124060Calibration.Band b=Sub124060Calibration.TWELVE_ROTATION;
        assertEquals(GmtHumanQcMath.Attention.CLEAR,b.judge(b.clearLow));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,b.judge(b.clearHigh));
        assertEquals(GmtHumanQcMath.Attention.CHECK,b.judge((b.clearHigh+b.checkHigh)/2.0));
        assertEquals(GmtHumanQcMath.Attention.CHECK,b.judge(b.checkHigh));
        assertEquals(GmtHumanQcMath.Attention.STRONG,b.judge(b.checkHigh+0.001));
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,b.judge(Double.NaN));
    }

    @Test public void exactFrozenLimitsArePresent(){
        // Repaired calibrator run 37008521836 (44 genuine listings; provisional, accepted 2026-10-02).
        assertEquals(-1.18473115495,Sub124060Calibration.TWELVE_ROTATION.clearLow,0);
        assertEquals(1.58789615495,Sub124060Calibration.TWELVE_ROTATION.clearHigh,0);
        assertEquals(2.51210525825,Sub124060Calibration.TWELVE_ROTATION.checkHigh,0);
        assertEquals(0.0247193049,Sub124060Calibration.TWELVE_GAP.clearLow,0);
        assertEquals(0.0523356951,Sub124060Calibration.TWELVE_GAP.clearHigh,0);
        assertEquals(-0.0204445564,Sub124060Calibration.TWELVE_CENTRING.clearLow,0);
        assertEquals(0.031007594,Sub124060Calibration.TWELVE_CENTRING.checkHigh,0);
        assertEquals(0.8112316335,Sub124060Calibration.ROUND_RING_RHO.clearLow,0);
        assertEquals(0.8233963665,Sub124060Calibration.ROUND_RING_RHO.clearHigh,0);
        // One-sided metrics have no lower limit.
        assertEquals(Double.NEGATIVE_INFINITY,Sub124060Calibration.ROUND_SPACING_RMS.clearLow,0);
        assertEquals(0.8017853513,Sub124060Calibration.ROUND_SPACING_RMS.clearHigh,0);
        assertEquals(Double.NEGATIVE_INFINITY,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearLow,0);
        assertEquals(0.0094181512,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearHigh,0);
        assertEquals(Double.NEGATIVE_INFINITY,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.checkLow,0);
        assertEquals(0.0056572452,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.clearHigh,0);
    }

    @Test public void worstUsesTheMatureGmtSeverityOrdering(){
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.worst(
                GmtHumanQcMath.Attention.CLEAR,GmtHumanQcMath.Attention.UNASSESSABLE));
        assertEquals(GmtHumanQcMath.Attention.CHECK,Sub124060Calibration.worst(
                GmtHumanQcMath.Attention.CLEAR,GmtHumanQcMath.Attention.CHECK));
        assertEquals(GmtHumanQcMath.Attention.STRONG,Sub124060Calibration.worst(
                GmtHumanQcMath.Attention.CHECK,GmtHumanQcMath.Attention.STRONG));
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,Sub124060Calibration.worst(
                GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE));
    }

    @Test public void existingResizeReliabilityGateControlsTwelveJudgement(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.rotationDeg=0.5;r.centringW=0.0;
        r.rotationResizeStable=true;r.centringResizeStable=true;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r,true);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.rotation);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.centring);

        r.rotationResizeStable=false;r.centringResizeStable=null;
        a=Sub124060Calibration.assess(r,true);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.rotation);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.centring);
    }

    @Test public void relationalMathMatchesCalibratorDefinitions(){
        assertEquals(0.82,Sub124060Calibration.median(Arrays.asList(0.80,0.81,0.83,0.84)),1e-12);
        assertEquals(1.0,Sub124060Calibration.spacingRms(Arrays.asList(-1.0,1.0,-1.0,1.0)),1e-12);
        assertEquals(0.10,Sub124060Calibration.lineOffset(new double[]{-0.8,0.1},new double[]{0.8,0.1}),1e-12);
    }

    @Test public void gapIsJudgedOnlyWhenResizeStable(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.gapR=0.035;r.gapResizeStable=true;
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.assess(r).gap);
        r.gapR=0.08;
        assertEquals(GmtHumanQcMath.Attention.STRONG,Sub124060Calibration.assess(r).twelve());
        r.gapResizeStable=false;
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,Sub124060Calibration.assess(r).gap);
    }

    @Test public void provisionalBandsJudgeAndFlagRealDeviations(){
        assertTrue(Sub124060Calibration.VERDICTS_ENABLED);
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.rotationResizeStable=true;r.centringResizeStable=true;
        r.rotationDeg=0.3;r.centringW=0.005;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.twelve());
        r.rotationDeg=1.8;   // between clear and check
        assertEquals(GmtHumanQcMath.Attention.CHECK,Sub124060Calibration.assess(r).rotation);
        r.rotationDeg=3.0;   // beyond check
        assertEquals(GmtHumanQcMath.Attention.STRONG,Sub124060Calibration.assess(r).twelve());
        r.rotationDeg=0.3;r.centringW=0.05;
        assertEquals(GmtHumanQcMath.Attention.STRONG,Sub124060Calibration.assess(r).centring);
        // An unstable value is never judged.
        r.rotationResizeStable=false;
        a=Sub124060Calibration.assess(r);
        assertFalse(a.rotationMeasured);
        assertEquals("NOT JUDGED",Sub124060Calibration.label(a.rotationMeasured,a.rotation));
    }

    @Test public void judgeFalseStillMeasuresWithoutVerdicts(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.rotationDeg=5;r.rotationResizeStable=true;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r,false);
        assertTrue(a.rotationMeasured);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.rotation);
        assertEquals("MEASURED / NOT YET JUDGED",Sub124060Calibration.label(a.rotationMeasured,a.rotation));
    }
}
