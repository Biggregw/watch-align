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

    @Test public void exactGenuineEnvelopeLimitsArePresent(){
        // Calibrator run 37054338303: 40 genuine 124060s, six acquired sources, zero rejected
        // photo-level outliers for all seven metrics. Every retained genuine observation is CLEAR.
        assertEquals(-1.438497,Sub124060Calibration.TWELVE_ROTATION.clearLow,0);
        assertEquals(3.555616,Sub124060Calibration.TWELVE_ROTATION.clearHigh,0);
        assertEquals(-1.638497,Sub124060Calibration.TWELVE_ROTATION.checkLow,0);
        assertEquals(3.755616,Sub124060Calibration.TWELVE_ROTATION.checkHigh,0);
        assertEquals(0.0155867,Sub124060Calibration.TWELVE_GAP.clearLow,0);
        assertEquals(0.0463993,Sub124060Calibration.TWELVE_GAP.clearHigh,0);
        assertEquals(0.0131864,Sub124060Calibration.TWELVE_GAP.checkLow,0);
        assertEquals(0.0487996,Sub124060Calibration.TWELVE_GAP.checkHigh,0);
        assertEquals(-0.080044,Sub124060Calibration.TWELVE_CENTRING.clearLow,0);
        assertEquals(0.050926,Sub124060Calibration.TWELVE_CENTRING.clearHigh,0);
        assertEquals(-0.090044,Sub124060Calibration.TWELVE_CENTRING.checkLow,0);
        assertEquals(0.060926,Sub124060Calibration.TWELVE_CENTRING.checkHigh,0);
        assertEquals(0.784912,Sub124060Calibration.ROUND_RING_RHO.clearLow,0);
        assertEquals(0.827718,Sub124060Calibration.ROUND_RING_RHO.clearHigh,0);
        assertEquals(0.782912,Sub124060Calibration.ROUND_RING_RHO.checkLow,0);
        assertEquals(0.829718,Sub124060Calibration.ROUND_RING_RHO.checkHigh,0);
        // One-sided metrics have no lower warning boundary.
        assertEquals(Double.NEGATIVE_INFINITY,Sub124060Calibration.ROUND_SPACING_RMS.clearLow,0);
        assertEquals(4.033474,Sub124060Calibration.ROUND_SPACING_RMS.clearHigh,0);
        assertEquals(4.133474,Sub124060Calibration.ROUND_SPACING_RMS.checkHigh,0);
        assertEquals(Double.NEGATIVE_INFINITY,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearLow,0);
        assertEquals(0.023287,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearHigh,0);
        assertEquals(0.025287,Sub124060Calibration.BATON_3_9_LINE_OFFSET.checkHigh,0);
        assertEquals(Double.NEGATIVE_INFINITY,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.checkLow,0);
        assertEquals(0.014611,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.clearHigh,0);
        assertEquals(0.016611,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.checkHigh,0);
    }

    @Test public void observedGenuineExtremesStayClear(){
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.TWELVE_ROTATION.judge(3.355616));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.TWELVE_ROTATION.judge(-1.238497));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.TWELVE_GAP.judge(0.017987));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.TWELVE_GAP.judge(0.043999));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.TWELVE_CENTRING.judge(-0.070044));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.TWELVE_CENTRING.judge(0.040926));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.ROUND_RING_RHO.judge(0.786912));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.ROUND_RING_RHO.judge(0.825718));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.ROUND_SPACING_RMS.judge(3.933474));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.BATON_3_9_LINE_OFFSET.judge(0.021287));
        assertEquals(GmtHumanQcMath.Attention.CLEAR,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.judge(0.012611));
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

    @Test public void mixedBatonEdgePassIsOffOutsideThe124060Route(){
        // GMT calls GmtSixLandmarkAnalyzer without this pass; only Sub124060QcAnalyzer enables it.
        assertFalse(GmtSixLandmarkAnalyzer.MIXED_EDGE_PASS.get());
    }

    @Test public void gapAndRingAreJudgedFromTheDiverseGenuineEnvelope(){
        assertTrue(Sub124060Calibration.GAP_JUDGED);
        assertTrue(Sub124060Calibration.RING_JUDGED);
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.gapR=0.023;r.gapResizeStable=true;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r);
        assertTrue(a.gapMeasured);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.gap);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.twelve());
    }

    @Test public void genuineEnvelopeBandsOnlyFlagBeyondSupportedVariation(){
        assertTrue(Sub124060Calibration.VERDICTS_ENABLED);
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.rotationResizeStable=true;r.centringResizeStable=true;
        r.rotationDeg=0.3;r.centringW=0.005;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.twelve());
        r.rotationDeg=3.65;   // beyond CLEAR but still within CHECK
        assertEquals(GmtHumanQcMath.Attention.CHECK,Sub124060Calibration.assess(r).rotation);
        r.rotationDeg=4.0;    // beyond the outer CHECK guard
        assertEquals(GmtHumanQcMath.Attention.STRONG,Sub124060Calibration.assess(r).twelve());
        r.rotationDeg=0.3;r.centringW=0.055;
        assertEquals(GmtHumanQcMath.Attention.CHECK,Sub124060Calibration.assess(r).centring);
        r.centringW=0.08;
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
