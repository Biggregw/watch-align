package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class Sub124060ProvisionalQcTest {
    @Test public void frozenRotationBoundariesClassifyClearCheckOutside(){
        Sub124060ProvisionalQc.Band b=Sub124060ProvisionalQc.TWELVE_ROTATION;
        assertEquals(Sub124060ProvisionalQc.Judgement.CLEAR,b.judge(b.clearLow));
        assertEquals(Sub124060ProvisionalQc.Judgement.CLEAR,b.judge(b.clearHigh));
        assertEquals(Sub124060ProvisionalQc.Judgement.CHECK,b.judge((b.clearHigh+b.checkHigh)/2));
        assertEquals(Sub124060ProvisionalQc.Judgement.CHECK,b.judge(b.checkHigh));
        assertEquals(Sub124060ProvisionalQc.Judgement.OUTSIDE,b.judge(b.checkHigh+0.001));
        assertEquals(Sub124060ProvisionalQc.Judgement.UNJUDGED,b.judge(Double.NaN));
    }

    @Test public void exactFrozenLimitsArePresent(){
        assertEquals(-13.88038488935,Sub124060ProvisionalQc.TWELVE_ROTATION.clearLow,0);
        assertEquals(15.22676818935,Sub124060ProvisionalQc.TWELVE_ROTATION.clearHigh,0);
        assertEquals(-0.147616360146,Sub124060ProvisionalQc.TWELVE_CENTRING.clearLow,0);
        assertEquals(0.136472106146,Sub124060ProvisionalQc.TWELVE_CENTRING.clearHigh,0);
        assertEquals(0.7826128775166086,Sub124060ProvisionalQc.ROUND_RING_RHO.clearLow,0);
        assertEquals(0.8526717081695576,Sub124060ProvisionalQc.ROUND_RING_RHO.clearHigh,0);
        assertEquals(6.370183430972006,Sub124060ProvisionalQc.ROUND_SPACING_RMS.clearHigh,0);
        assertEquals(0.09378104194951688,Sub124060ProvisionalQc.BATON_3_9_LINE_OFFSET.clearHigh,0);
        assertEquals(0.1396427365740998,Sub124060ProvisionalQc.AXIS_12_6_LINE_OFFSET.clearHigh,0);
    }

    @Test public void relationalHelpersMatchCalibrationDefinitions(){
        assertEquals(0.82,Sub124060ProvisionalQc.median(Arrays.asList(0.80,0.81,0.83,0.84)),1e-12);
        assertEquals(1.0,Sub124060ProvisionalQc.spacingRms(Arrays.asList(-1.0,1.0,-1.0,1.0)),1e-12);
        assertEquals(0.10,Sub124060ProvisionalQc.lineOffset(new double[]{-0.8,0.1},new double[]{0.8,0.1}),1e-12);
    }

    @Test public void summaryJudgesOnlyReliablePublishedTwelveMeasurements(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.dialSource=Sub124060QcAnalyzer.DialSource.AUTO_EDGE_FIT;
        r.rotationDeg=0.5;r.centringW=0.0;r.gapR=0.04;
        r.rotationResizeStable=true;r.centringResizeStable=true;r.gapResizeStable=true;
        Sub124060Summary.Alignment a=Sub124060Summary.alignment(r);
        assertEquals(Sub124060ProvisionalQc.Judgement.CLEAR,a.rotation);
        assertEquals(Sub124060ProvisionalQc.Judgement.CLEAR,a.centring);
        assertTrue(Sub124060Summary.build(r).contains("PROVISIONAL CLEAR"));
        assertTrue(Sub124060Summary.build(r).contains("no tolerance: calibration found pose/scale sensitivity"));

        r.rotationResizeStable=false;r.centringResizeStable=null;
        a=Sub124060Summary.alignment(r);
        assertEquals(Sub124060ProvisionalQc.Judgement.UNJUDGED,a.rotation);
        assertEquals(Sub124060ProvisionalQc.Judgement.UNJUDGED,a.centring);
    }
}
