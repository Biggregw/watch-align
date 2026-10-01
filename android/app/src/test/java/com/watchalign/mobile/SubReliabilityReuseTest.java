package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Reuse-first safety rules added around the existing 124060 detectors. */
public class SubReliabilityReuseTest {
    @Test public void batonRepeatabilityUsesPixelMovementAndSameEdgeNotGmtQcThresholds(){
        GmtSixLandmarkAnalyzer.Result b=new GmtSixLandmarkAnalyzer.Result(0.1,0.0,0.0,20,60,true,null);
        b.stabilityRun=true;b.stabilitySameEdge=true;
        b.centringMin=-0.01;b.centringMax=0.03;       // 0.8 px
        b.rotMin=0.0;b.rotMax=0.5;                   // about 0.52 px at 60 px
        assertTrue(Sub124060QcAnalyzer.batonRepeatable(b));

        b.centringMax=0.06;                          // 1.4 px
        assertFalse(Sub124060QcAnalyzer.batonRepeatable(b));
        b.centringMax=0.03;b.stabilitySameEdge=false;
        assertFalse(Sub124060QcAnalyzer.batonRepeatable(b));
    }

    @Test public void roundCentreCanRepeatEvenWhenLumeVsSurroundEdgeChanges(){
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(1);
        m.radiusPx=20;m.stabilityRun=true;m.stabilitySameEdge=false;
        m.offMin=-0.01;m.offMax=0.01;                 // 0.8 px on 40 px diameter
        assertTrue(Sub124060QcAnalyzer.roundOffsetRepeatable(m));

        m.offMax=0.03;                               // 1.6 px
        assertFalse(Sub124060QcAnalyzer.roundOffsetRepeatable(m));
    }

    @Test public void referenceDisagreementIsDiagnosticNotAThreshold(){
        assertTrue(Math.abs(Sub124060Summary.angleDifference(0.4,0.1)-0.3)<1e-9);
        assertTrue(Math.abs(Sub124060Summary.angleDifference(89,-89)-2.0)<1e-9);
    }
}
