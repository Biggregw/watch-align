package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Round hour markers (alpha61): circle fit, dial frame, affine seeds, decision and summary. */
public class RoundMarkerTest {
    private static final GmtHumanQcMath.PoseLabel GOOD=GmtHumanQcMath.PoseLabel.GOOD;

    @Test public void kasaFitsAnExactCircle(){
        int n=36;double[] x=new double[n],y=new double[n];boolean[] u=new boolean[n];
        for(int i=0;i<n;i++){double t=2*Math.PI*i/n;x[i]=120.3+17.5*Math.cos(t);y[i]=80.7+17.5*Math.sin(t);u[i]=true;}
        double[] f=GmtRoundMarkerAnalyzer.kasa(x,y,u);
        assertEquals(120.3,f[0],1e-6);assertEquals(80.7,f[1],1e-6);assertEquals(17.5,f[2],1e-6);
    }

    @Test public void trimmedFitIgnoresAHandOnAFewRays(){
        int n=72;double[] x=new double[n],y=new double[n];boolean[] ok=new boolean[n];
        for(int i=0;i<n;i++){double t=2*Math.PI*i/n,rr=i<8?26:17;x[i]=50+rr*Math.cos(t);y[i]=60+rr*Math.sin(t);ok[i]=true;}
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(4);
        double[] f=GmtRoundMarkerAnalyzer.trimmedFit(x,y,ok,m);
        assertEquals(50,f[0],0.05);assertEquals(60,f[1],0.05);assertEquals(17,f[2],0.05);
        assertEquals(8.0/72,m.rejectFraction,1e-9);
    }

    @Test public void dialFrameUndoesTheSquash(){
        GmtRoundMarkerAnalyzer.DialFrame d=new GmtRoundMarkerAnalyzer.DialFrame(400,300,220,180,30);
        for(double phi=0;phi<6.28;phi+=0.7){
            double[] p=d.at(phi,0.8),q=d.rect(p[0],p[1]);
            assertEquals(0.8*d.r,Math.hypot(q[0],q[1]),1e-6);
            assertEquals(Math.cos(phi),q[0]/Math.hypot(q[0],q[1]),1e-9);
            assertEquals(phi>Math.PI?phi-2*Math.PI:phi,d.phiOf(p[0],p[1]),1e-9);
        }
    }

    @Test public void affineRecoversTheMapAndDropsAWrongMarker(){
        List<double[]> s=new ArrayList<>(),d=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            double[] q=GmtRoundMarkerAnalyzer.master(h,0.816);s.add(q);
            d.add(new double[]{500+300*q[0]+40*q[1],400-20*q[0]+280*q[1]});
        }
        d.set(3,new double[]{d.get(3)[0]+60,d.get(3)[1]});      // one marker found in the wrong place
        double[][] A=GmtRoundMarkerAnalyzer.robustAffine(s,d,10);
        assertNotNull(A);
        assertEquals(300,A[0][0],1e-6);assertEquals(40,A[0][1],1e-6);assertEquals(500,A[0][2],1e-6);
        assertEquals(-20,A[1][0],1e-6);assertEquals(280,A[1][1],1e-6);assertEquals(400,A[1][2],1e-6);
    }

    @Test public void centredMarkerIsClear(){
        GmtHumanQcMath.RoundDecision d=GmtHumanQcMath.assessRound(0.03,34,1.01,GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,d.attention);
    }

    @Test public void offsetPastTheCheckLevelIsFlagged(){
        GmtHumanQcMath.RoundDecision d=GmtHumanQcMath.assessRound(GmtHumanQcMath.ROUND_OFFSET_CHECK+0.01,34,1.0,GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CHECK,d.attention);assertTrue(d.offCentre);
        d=GmtHumanQcMath.assessRound(-(GmtHumanQcMath.ROUND_OFFSET_STRONG+0.01),34,1.0,GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.STRONG,d.attention);
    }

    @Test public void subPixelOffsetIsNotCalled(){
        // Past the level as a fraction but under 2 px on a small marker.
        GmtHumanQcMath.RoundDecision d=GmtHumanQcMath.assessRound(GmtHumanQcMath.ROUND_OFFSET_CHECK+0.01,GmtHumanQcMath.MIN_ROUND_PX,1.0,GOOD,true);
        assertNotEquals(GmtHumanQcMath.Attention.STRONG,d.attention);
        assertFalse(d.offCentre&&(GmtHumanQcMath.ROUND_OFFSET_CHECK+0.01)*GmtHumanQcMath.MIN_ROUND_PX<2.0);
    }

    @Test public void oddSizeIsCheckOnly(){
        GmtHumanQcMath.RoundDecision d=GmtHumanQcMath.assessRound(0.0,34,1.0-GmtHumanQcMath.ROUND_SIZE_CHECK-0.02,GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CHECK,d.attention);assertTrue(d.sizeOdd);assertFalse(d.offCentre);
    }

    @Test public void tooSmallAngledOrLowConfidenceIsNotCleared(){
        assertTrue(GmtHumanQcMath.assessRound(0.0,GmtHumanQcMath.MIN_ROUND_PX-1,1.0,GOOD,true).tooSmall);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.assessRound(0.0,34,1.0,GmtHumanQcMath.PoseLabel.RETAKE,true).attention);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.assessRound(0.0,34,1.0,GOOD,false).attention);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.assessRound(0.8,34,1.0,GOOD,true).attention);
    }

    @Test public void resizeCheckWithholdsAMovingReading(){
        GmtRoundMarkerAnalyzer.Marker m=marker(4,0.17);
        m.stabilityRun=true;m.stabilitySameEdge=true;m.offMin=0.05;m.offMax=0.17;   // 4 px of travel across the level
        assertFalse(m.resampleStable());
        m.offMin=0.165;assertTrue(m.resampleStable());                            // 0.2 px
        m.offMin=0.01;m.offMax=0.08;assertTrue(m.resampleStable());               // moves, but below the level throughout
        m.stabilitySameEdge=false;assertFalse(m.resampleStable());
    }

    private static GmtRoundMarkerAnalyzer.Marker marker(int h,double offset){
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);
        m.found=true;m.stable=true;m.radiusPx=17;m.offset=offset;m.sizeRatio=1.0;
        return m;
    }
    private static GmtRoundMarkerAnalyzer.Marker judged(int h,GmtHumanQcMath.Attention a){
        GmtRoundMarkerAnalyzer.Marker m=marker(h,0.02);m.attention=a;return m;
    }

    private static GmtHumanSummary.Input clearTwelve(){
        GmtHumanSummary.Input in=new GmtHumanSummary.Input();
        in.pose=GOOD;in.twelveValid=true;in.stableFrame=true;
        in.gap=GmtHumanQcMath.Attention.CLEAR;in.alignment=GmtHumanQcMath.Attention.CLEAR;in.observedGap=0.09;
        in.spacing59=0.14;in.spacing01=0.14;in.rotationDeg=0.2;
        return in;
    }

    @Test public void allRoundMarkersClear(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)in.round.add(judged(h,GmtHumanQcMath.Attention.CLEAR));
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Round markers: all 8 centred on their minute ticks"));
        assertTrue(s,s.contains("Bottom line: nothing flagged at 12 or the round markers."));
        assertFalse(s,s.contains("not checked yet"));
    }

    @Test public void flaggedRoundMarkerIsNamedWithItsDirection(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            GmtRoundMarkerAnalyzer.Marker m=judged(h,h==4?GmtHumanQcMath.Attention.CHECK:GmtHumanQcMath.Attention.CLEAR);
            if(h==4){m.offCentre=true;m.offset=0.15;}
            in.round.add(m);
        }
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("worth a look: the 4 sits clockwise, towards the 21 tick (offset +0.15 of its width)"));
        assertTrue(s,s.contains("Bottom line: 1 thing to check: the 4 marker position."));
    }

    @Test public void handOverRoundMarkersIsNotJudged(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            GmtRoundMarkerAnalyzer.Marker m=judged(h,h==2||h==11?GmtHumanQcMath.Attention.UNASSESSABLE:GmtHumanQcMath.Attention.CLEAR);
            if(h==2||h==11)m.hand=true;
            in.round.add(m);
        }
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Not judged: the 2 and 11 (a hand is over or next to them)."));
        assertTrue(s,s.contains("nothing flagged at 12 or the 6 round markers measured"));
        assertTrue(s,s.contains("the 2 and 11 round markers could not be judged here"));
    }

    @Test public void noRoundMarkersFound(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)in.round.add(new GmtRoundMarkerAnalyzer.Marker(h));
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Round markers: not measured on this photo"));
        assertTrue(s,s.contains("the round markers could not be judged here"));
    }

    @Test public void hoursAndTicks(){
        assertEquals(Arrays.toString(new int[]{1,2,4,5,7,8,10,11}),Arrays.toString(GmtRoundMarkerAnalyzer.HOURS));
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(10);
        assertEquals(49,m.before());assertEquals(51,m.after());
    }
}
