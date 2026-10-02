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
        m.stabilitySameEdge=false;assertTrue(m.resampleStable());                   // radius changed, offset steady
        m.offMax=Double.NaN;assertFalse(m.resampleStable());                        // not found again
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

    @Test public void lowConfidenceReasonsAreShortAndGrouped(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            GmtRoundMarkerAnalyzer.Marker m=judged(h,h==1||h==8?GmtHumanQcMath.Attention.UNASSESSABLE:GmtHumanQcMath.Attention.CLEAR);
            if(h==1){m.stable=false;m.lowReason="26% of the outline is not on a circle (a hand, glare or a damaged edge)";}
            if(h==8){m.stable=false;m.lowReason="35% of the outline is not on a circle (a hand, glare or a damaged edge)";}
            in.round.add(m);
        }
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Not judged: the 1 and 8 (outline not clean: a hand, glare or a damaged edge)."));
    }

    @Test public void sizeIsNotClaimedWhenNotCompared(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){GmtRoundMarkerAnalyzer.Marker m=judged(h,GmtHumanQcMath.Attention.CLEAR);m.sizeRatio=Double.NaN;in.round.add(m);}
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Round markers: all 8 centred on their minute ticks.\n"));
    }

    @Test public void sizesAreComparedOnlyOnLargeEnoughMarkers(){
        List<GmtRoundMarkerAnalyzer.Marker> ms=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)ms.add(h==4?ringMarker(h,11.5,11.5,15.0):ringMarker(h,10.0,10.0,13.0));
        GmtRoundMarkerAnalyzer.sizeRatios(ms);
        for(GmtRoundMarkerAnalyzer.Marker m:ms)assertTrue(Double.isNaN(m.sizeRatio));   // 26 px median surround: too small
        ms.clear();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)ms.add(h==4?ringMarker(h,23.0,23.0,23.0*1.3):ringMarker(h,20.0,20.0,26.0));
        GmtRoundMarkerAnalyzer.sizeRatios(ms);
        assertEquals(1.15,ms.get(2).sizeRatio,0.01);
    }

    @Test public void fittedEdgesAreNeverComparedWhenTooFewMarkersShowBothRings(){
        // alpha62 review: with fewer than 4 markers whose surround is identified, the old fallback
        // compared fitted edges, some on the lume (25 px) and some on the surround (30 px). That is
        // the false-size mechanism, so size must be left unassessable.
        List<GmtRoundMarkerAnalyzer.Marker> ms=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            if(h==1||h==2||h==4)ms.add(ringMarker(h,25.0,25.0,30.0));   // both rings: surround identified
            else if(h==5||h==7)ms.add(ringMarker(h,30.0,30.0));         // one ring, fitted on the surround
            else ms.add(ringMarker(h,25.0,25.0));                       // one ring, fitted on the lume
        }
        GmtRoundMarkerAnalyzer.sizeRatios(ms);
        for(GmtRoundMarkerAnalyzer.Marker m:ms){
            assertTrue("hour "+m.hour,Double.isNaN(m.sizeRatio));
            GmtHumanQcMath.RoundDecision d=GmtHumanQcMath.assessRound(0.02,m.diameterPx(),m.sizeRatio,GOOD,true);
            assertFalse("hour "+m.hour,d.sizeOdd);
            assertEquals("hour "+m.hour,GmtHumanQcMath.Attention.CLEAR,d.attention);
        }
    }

    /** A marker at (100,100) whose rays all show edges at the given radii, fitted on radius fitR. */
    private static GmtRoundMarkerAnalyzer.Marker ringMarker(int h,double fitR,double... rings){
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);
        m.found=true;m.stable=true;m.x=100;m.y=100;m.radiusPx=fitR;
        m.edgePts=new double[72][];
        for(int k=0;k<72;k++){
            double a=2*Math.PI*k/72;double[] q=new double[2*rings.length];
            for(int i=0;i<rings.length;i++){q[2*i]=100+Math.cos(a)*rings[i];q[2*i+1]=100+Math.sin(a)*rings[i];}
            m.edgePts[k]=q;
        }
        return m;
    }

    @Test public void sizeComparesOuterRingsWhenOneMarkerWasTracedOnItsSurround(){
        // Genuine Bob's Watches 126720VTNR 182860: the 8 traced on its surround (60 px), the rest on
        // their lume (50 px). Both edges are on every marker, so the sizes are the same.
        List<GmtRoundMarkerAnalyzer.Marker> ms=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)ms.add(h==8?ringMarker(h,30.0,25.0,30.0):ringMarker(h,25.0,25.0,30.0));
        GmtRoundMarkerAnalyzer.sizeRatios(ms);
        for(GmtRoundMarkerAnalyzer.Marker m:ms)assertEquals("hour "+m.hour,1.0,m.sizeRatio,0.01);
    }

    @Test public void aMarkerThatIsReallyLargerStillReadsLarger(){
        List<GmtRoundMarkerAnalyzer.Marker> ms=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)ms.add(h==4?ringMarker(h,30.0,30.0,36.6):ringMarker(h,25.0,25.0,30.5));
        GmtRoundMarkerAnalyzer.sizeRatios(ms);
        for(GmtRoundMarkerAnalyzer.Marker m:ms)assertEquals("hour "+m.hour,m.hour==4?1.2:1.0,m.sizeRatio,0.01);
    }

    @Test public void aMarkerWithOnlyOneEdgeFoundIsNotSizeJudged(){
        // One ring only: it can't be told whether it is the lume or the surround.
        List<GmtRoundMarkerAnalyzer.Marker> ms=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)ms.add(h==11?ringMarker(h,25.0,25.0):ringMarker(h,25.0,25.0,30.5));
        GmtRoundMarkerAnalyzer.sizeRatios(ms);
        for(GmtRoundMarkerAnalyzer.Marker m:ms)
            if(m.hour==11)assertTrue(Double.isNaN(m.sizeRatio));else assertEquals(1.0,m.sizeRatio,0.01);
    }

    @Test public void everyHourPositionIsReported(){
        GmtHumanSummary.Input in=clearTwelve();
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            GmtRoundMarkerAnalyzer.Marker m=h==7?new GmtRoundMarkerAnalyzer.Marker(7):judged(h,GmtHumanQcMath.Attention.CLEAR);
            if(h==2){m.attention=GmtHumanQcMath.Attention.UNASSESSABLE;m.hand=true;}
            in.round.add(m);
        }
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("All markers: 12 OK · 1 OK · 2 hand in the way · 3 date window (not a marker) · 4 OK · 5 OK · 6 not found · 7 not found · 8 OK · 9 not found · 10 OK · 11 OK."));
    }

    @Test public void otherMarkersAreReportedWhenTheTwelveIsNotChecked(){
        GmtHumanSummary.Input in=clearTwelve();in.handAtTwelve=true;
        for(int h:GmtRoundMarkerAnalyzer.HOURS)in.round.add(judged(h,GmtHumanQcMath.Attention.CLEAR));
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Nothing flagged at all 8 round markers."));
        assertTrue(s,s.contains("All markers: 12 hand in the way"));
    }

    @Test public void genuineEnvelopeTurnIsClearEvenAtHighResolution(){
        // Genuine WOS CPO on a full-resolution crop: -1.6 deg, top edge agreeing, 105 px triangle.
        GmtHumanQcMath.RotationDecision r=GmtHumanQcMath.assessRotation(-1.6,-1.4,-0.02,105,GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CLEAR,r.attention);
        // A larger corroborated turn beyond the supported genuine envelope still receives attention.
        r=GmtHumanQcMath.assessRotation(-2.4,-2.2,-0.02,105,GOOD,true);
        assertEquals(GmtHumanQcMath.Attention.CHECK,r.attention);
    }

    @Test public void lumeAndSurroundEdgesCountAsOneOutline(){
        // 72 rays from a centre 1 px off the true one; 45 rays see the lume edge (r 17), 27 only
        // the surround's outer edge (r 23): the user's ONE Batgirl photo pattern.
        List<List<Double>> cand=new ArrayList<>();
        double tx=51,ty=60,ox=50,oy=60;
        for(int k=0;k<72;k++){
            double a=2*Math.PI*k/72,dx=Math.cos(a),dy=Math.sin(a),R=k%8<5?17:23;
            // distance t along the ray from (ox,oy) to the circle about (tx,ty)
            double bx=ox-tx,by=oy-ty,bb=bx*dx+by*dy,t=-bb+Math.sqrt(bb*bb-(bx*bx+by*by-R*R));
            List<Double> c=new ArrayList<>();c.add(t);cand.add(c);
        }
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(1);
        double[] f=GmtRoundMarkerAnalyzer.concentric(cand,ox,oy,new double[]{51.2,60.1,17.1},m);
        assertEquals(0.0,m.rejectFraction,1e-9);
        assertNotNull(f);
        assertEquals(51,f[0],0.01);assertEquals(60,f[1],0.01);assertEquals(17,f[2],0.01);
    }

    @Test public void noReadableDialSaysSoAndNothingElse(){
        GmtHumanSummary.Input in=clearTwelve();in.noDial=true;
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("Photo: no readable watch dial found."));
        assertTrue(s,s.contains("Bottom line: nothing was checked on this photo."));
        assertFalse(s,s.contains("12 gap"));
    }

    @Test public void noReadableDialNeverHidesAResolvedFinding(){
        // alpha62 review: a low-evidence photo is only "no readable dial" when nothing is flagged.
        GmtHumanQcMath.Attention C=GmtHumanQcMath.Attention.CHECK,S=GmtHumanQcMath.Attention.STRONG,U=GmtHumanQcMath.Attention.UNASSESSABLE,OK=GmtHumanQcMath.Attention.CLEAR;
        List<GmtRoundMarkerAnalyzer.Marker> none=new ArrayList<>();
        assertTrue(GmtHumanQcAnalyzerV2.noReadableDial(true,U,U,null,null,none));
        assertTrue(GmtHumanQcAnalyzerV2.noReadableDial(true,OK,null,U,null,none));
        assertFalse(GmtHumanQcAnalyzerV2.noReadableDial(false,U,U,U,U,none));
        assertFalse(GmtHumanQcAnalyzerV2.noReadableDial(true,C,U,null,null,none));      // 12 gap
        assertFalse(GmtHumanQcAnalyzerV2.noReadableDial(true,U,S,null,null,none));      // 12 alignment
        assertFalse(GmtHumanQcAnalyzerV2.noReadableDial(true,null,null,C,null,none));   // 6
        assertFalse(GmtHumanQcAnalyzerV2.noReadableDial(true,null,null,null,S,none));   // side baton
        List<GmtRoundMarkerAnalyzer.Marker> round=new ArrayList<>();
        for(int h:GmtRoundMarkerAnalyzer.HOURS)round.add(judged(h,h==7?C:U));
        assertFalse(GmtHumanQcAnalyzerV2.noReadableDial(true,null,null,null,null,round)); // a round marker
        // The summary keeps the finding even if noDial arrives set.
        GmtHumanSummary.Input in=clearTwelve();in.noDial=true;in.round=round;
        String s=GmtHumanSummary.build(in);
        assertFalse(s,s.contains("nothing was checked"));
        assertTrue(s,s.contains("the 7 marker"));
        in=clearTwelve();in.noDial=true;in.gap=S;
        s=GmtHumanSummary.build(in);
        assertFalse(s,s.contains("nothing was checked"));assertTrue(s,s.contains("the gap at 12"));
    }

    @Test public void hoursAndTicks(){
        assertEquals(Arrays.toString(new int[]{1,2,4,5,7,8,10,11}),Arrays.toString(GmtRoundMarkerAnalyzer.HOURS));
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(10);
        assertEquals(49,m.before());assertEquals(51,m.after());
    }
}
