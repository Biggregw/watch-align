package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class Sub124060CalibrationTest {
    @Test public void frozenBandsClassifyClearCheckAndOutside(){
        Sub124060Calibration.Band b=Sub124060Calibration.TWELVE_ROTATION;
        assertEquals(Sub124060Calibration.Verdict.CLEAR,b.judge(b.clearLow));
        assertEquals(Sub124060Calibration.Verdict.CLEAR,b.judge(b.clearHigh));
        assertEquals(Sub124060Calibration.Verdict.CHECK,b.judge((b.clearHigh+b.checkHigh)/2.0));
        assertEquals(Sub124060Calibration.Verdict.CHECK,b.judge(b.checkHigh));
        assertEquals(Sub124060Calibration.Verdict.OUTSIDE,b.judge(b.checkHigh+0.001));
        assertEquals(Sub124060Calibration.Verdict.UNJUDGED,b.judge(Double.NaN));
    }

    @Test public void exactFrozenLimitsArePresent(){
        assertEquals(-13.88038488935,Sub124060Calibration.TWELVE_ROTATION.clearLow,0);
        assertEquals(15.22676818935,Sub124060Calibration.TWELVE_ROTATION.clearHigh,0);
        assertEquals(-0.147616360146,Sub124060Calibration.TWELVE_CENTRING.clearLow,0);
        assertEquals(0.136472106146,Sub124060Calibration.TWELVE_CENTRING.clearHigh,0);
        assertEquals(0.7826128775166086,Sub124060Calibration.ROUND_RING_RHO.clearLow,0);
        assertEquals(0.8526717081695576,Sub124060Calibration.ROUND_RING_RHO.clearHigh,0);
        assertEquals(6.370183430972006,Sub124060Calibration.ROUND_SPACING_RMS.clearHigh,0);
        assertEquals(0.09378104194951688,Sub124060Calibration.BATON_3_9_LINE_OFFSET.clearHigh,0);
        assertEquals(0.1396427365740998,Sub124060Calibration.AXIS_12_6_LINE_OFFSET.clearHigh,0);
    }

    @Test public void existingReliabilityGateStillControlsTwelveJudgement(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.rotationDeg=0.5;r.centringW=0.0;r.gapR=0.03;
        r.rotationResizeStable=true;r.centringResizeStable=true;r.gapResizeStable=true;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r);
        assertEquals(Sub124060Calibration.Verdict.CLEAR,a.rotation);
        assertEquals(Sub124060Calibration.Verdict.CLEAR,a.centring);
        assertTrue(Sub124060Summary.build(r).contains("PROVISIONAL CLEAR"));

        r.rotationResizeStable=false;r.centringResizeStable=null;
        a=Sub124060Calibration.assess(r);
        assertEquals(Sub124060Calibration.Verdict.UNJUDGED,a.rotation);
        assertEquals(Sub124060Calibration.Verdict.UNJUDGED,a.centring);
    }

    @Test public void relationalMathMatchesFrozenCalibrationDefinitions(){
        assertEquals(0.82,Sub124060Calibration.median(Arrays.asList(0.80,0.81,0.83,0.84)),1e-12);
        assertEquals(1.0,Sub124060Calibration.spacingRms(Arrays.asList(-1.0,1.0,-1.0,1.0)),1e-12);
        assertEquals(0.10,Sub124060Calibration.lineOffset(new double[]{-0.8,0.1},new double[]{0.8,0.1}),1e-12);
    }
}
