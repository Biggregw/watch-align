package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

/** alpha64: the words on the overlay and under the close-ups say what was found. */
public class OverlayWordsTest {
    private static final GmtHumanQcMath.Attention OK=GmtHumanQcMath.Attention.CLEAR,CHECK=GmtHumanQcMath.Attention.CHECK,
            STRONG=GmtHumanQcMath.Attention.STRONG,NJ=GmtHumanQcMath.Attention.UNASSESSABLE;

    @Test public void twelveSaysWhatWasFlagged(){
        MeasuredOverlayRenderer.Drawing d=new MeasuredOverlayRenderer.Drawing();
        d.twelve=new GmtTwelveLandmarkAnalyzer.Geometry(new double[]{0,0},new double[]{1,0},new double[]{0.5,1},new double[]{0,-1},new double[]{0.5,-1},new double[]{1,-1},true);
        d.gap=STRONG;d.alignment=OK;d.gapValue=0.03;
        assertEquals("gap small",MeasuredOverlayRenderer.twelveWord(d));
        assertEquals(STRONG,MeasuredOverlayRenderer.twelveAttention(d));
        assertEquals("12: CHECK CLOSELY · gap 0.03 (genuine about 0.08-0.11)",MeasuredOverlayRenderer.statusText(d));
        d.gap=OK;d.alignment=CHECK;d.twelveKind="rotated";d.twelveRotationDeg=-1.8;
        assertEquals("rotated",MeasuredOverlayRenderer.twelveWord(d));
        assertEquals("12: WORTH A LOOK · rotated 1.8°",MeasuredOverlayRenderer.statusText(d));
        d.alignment=OK;
        assertNull(MeasuredOverlayRenderer.twelveWord(d));
        d.notJudged="a hand is at 12";
        assertEquals("hand in the way",MeasuredOverlayRenderer.twelveWord(d));
    }

    @Test public void batonAndRoundWords(){
        assertEquals("rotated",MeasuredOverlayRenderer.batonWord(CHECK,false,true,null));
        assertEquals("off-centre + rotated",MeasuredOverlayRenderer.batonWord(STRONG,true,true,null));
        assertNull(MeasuredOverlayRenderer.batonWord(OK,false,false,null));
        assertNull(MeasuredOverlayRenderer.batonWord(NJ,false,false,null));   // generic: badge only
        assertEquals("too small",MeasuredOverlayRenderer.batonWord(NJ,false,false,"6 baton too small"));
        assertEquals("rotated 2.2°",MeasuredOverlayRenderer.batonReason(false,true,0.01,-2.2));

        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(7);
        m.found=true;m.attention=CHECK;m.offCentre=true;
        assertEquals("off-centre",MeasuredOverlayRenderer.roundWord(m));
        m.attention=OK;m.offCentre=false;
        assertNull(MeasuredOverlayRenderer.roundWord(m));
        m.hand=true;m.attention=NJ;
        assertEquals("hand in the way",MeasuredOverlayRenderer.roundWord(m));
    }

    private static MeasuredOverlayRenderer.Drawing clearDial(){
        MeasuredOverlayRenderer.Drawing d=new MeasuredOverlayRenderer.Drawing();
        d.dialCx=500;d.dialCy=500;d.dialA=300;d.dialB=300;
        d.twelve=new GmtTwelveLandmarkAnalyzer.Geometry(new double[]{470,270},new double[]{530,270},new double[]{500,320},new double[]{470,225},new double[]{500,222},new double[]{530,225},true);
        d.gap=OK;d.alignment=OK;
        double[] z={0,0};
        d.six=new GmtSixLandmarkAnalyzer.Geometry(z,z,z,z,z,z,z,true);d.sixAttention=OK;
        d.nine=new GmtSixLandmarkAnalyzer.Geometry(z,z,z,z,z,z,z,true);d.nineAttention=OK;
        for(int h:GmtRoundMarkerAnalyzer.HOURS){GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);m.found=true;m.attention=OK;d.round.add(m);}
        return d;
    }

    @Test public void closeUpsShowOnlyWhatNeedsALook(){
        MeasuredOverlayRenderer.Drawing d=clearDial();
        assertEquals(java.util.Arrays.asList("12"),MeasuredOverlayRenderer.closeUpPlan(d));   // all clear: the 12 as a reference
        d.sixAttention=CHECK;d.round.get(4).attention=STRONG;d.round.get(1).attention=NJ;d.round.get(1).hand=true;d.round.get(6).attention=NJ;
        // check closely first, then worth a look, then a hand, then generic not judged
        assertEquals(java.util.Arrays.asList("r7","6","r2","r10"),MeasuredOverlayRenderer.closeUpPlan(d));
        d.photoNote="Photo too angled: most markers not judged";
        assertEquals(java.util.Arrays.asList("r7","6","r2"),MeasuredOverlayRenderer.closeUpPlan(d));   // generic ones left to the banner
        for(GmtRoundMarkerAnalyzer.Marker m:d.round){m.attention=CHECK;}
        assertEquals(MeasuredOverlayRenderer.MAX_CLOSE_UPS,MeasuredOverlayRenderer.closeUpPlan(d).size());
    }
}
