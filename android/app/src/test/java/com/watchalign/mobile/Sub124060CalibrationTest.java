package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

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
        assertEquals(-13.88038488935,Sub124060Calibration.TWELVE_ROTATION.clearLow,0);
        assertEquals(15.22676818935,Sub124060Calibration.TWELVE_ROTATION.clearHigh,0);
        assertEquals(-21.157173159025,Sub124060Calibration.TWELVE_ROTATION.checkLow,0);
        assertEquals(22.503556459025,Sub124060Calibration.TWELVE_ROTATION.checkHigh,0);

        assertEquals(-0.147616360146,Sub124060Calibration.TWELVE_CENTRING.clearLow,0);
        assertEquals(0.136472106146,Sub124060Calibration.TWELVE_CENTRING.clearHigh,0);
        assertEquals(-0.233275680438,Sub124060Calibration.TWELVE_CENTRING.checkLow,0);
        assertEquals(0.222131426438,Sub124060Calibration.TWELVE_CENTRING.checkHigh,0);

        assertEquals(0.7826128775166086,Sub124060Calibration.ROUND_RING_RHO.clearLow,0);
        assertEquals(0.8526717081695576,Sub124060Calibration.ROUND_RING_RHO.clearHigh,0);
        assertEquals(0.7650981698533712,Sub124060Calibration.ROUND_RING_RHO.checkLow,0);
        assertEquals(0.870186415832795,Sub124060Calibration.ROUND_RING_RHO.checkHigh,0);

        assertEquals(-5.587231836527973,Sub124060Calibration.ROUND_SPACING_RMS.clearLow,0);
        assertEquals(6.370183430972006,Sub124060Calibration.ROUND_SPACING_RMS.clearHigh,0);
        assertEquals(-9.573036925694634,Sub124060Calibration.ROUND_SPACING_RMS.checkLow,0);
        assertEquals(10.355988520138666,Sub124060Calibration.ROUND_SPACING_RMS.checkHigh,0);

        assertEquals(-0.08170984795356724,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearLow,0);
        assertEquals(0.09378104194951688,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearHigh,0);
        assertEquals(-0.14020681125459528,Sub124060Calibration.BATON_3_9_LINE_OFFSET.checkLow,0);
        assertEquals(0.1522780052505449,Sub124060Calibration.BATON_3_9_LINE_OFFSET.checkHigh,0);

        assertEquals(-0.1343132175899482,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.clearLow,0);
        assertEquals(0.1396427365740998,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.clearHigh,0);
        assertEquals(-0.21825249276636563,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.checkLow,0);
        assertEquals(0.22358201175051726,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.checkHigh,0);
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
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.rotation);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,a.centring);

        r.rotationResizeStable=false;r.centringResizeStable=null;
        a=Sub124060Calibration.assess(r);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.rotation);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.centring);
    }

    @Test public void relationalMathMatchesCalibratorDefinitions(){
        assertEquals(0.82,Sub124060Calibration.median(Arrays.asList(0.80,0.81,0.83,0.84)),1e-12);
        assertEquals(1.0,Sub124060Calibration.spacingRms(Arrays.asList(-1.0,1.0,-1.0,1.0)),1e-12);
        assertEquals(0.10,Sub124060Calibration.lineOffset(new double[]{-0.8,0.1},new double[]{0.8,0.1}),1e-12);
    }

    @Test public void gapHasNoCalibrationBand(){
        // Deliberately no TWELVE_GAP constant. The public contract is measured-only.
        assertEquals("MEASURED / NOT JUDGED",Sub124060Summary.MEASURED);
    }
}
