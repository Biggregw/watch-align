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
}
