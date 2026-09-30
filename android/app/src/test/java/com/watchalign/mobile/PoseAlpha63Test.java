package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** alpha63: the round-marker layout overrules a rehaut-only RETAKE. */
public class PoseAlpha63Test {
    private static final double R=250,CX=600,CY=500;

    /** Image of a master point (x -> 3, y -> 6, dial radii) seen with the dial squashed by cos(tilt) along `dirDeg`. */
    private static double[] project(double[] p,double tiltDeg,double dirDeg){
        double d=Math.toRadians(dirDeg),ux=Math.cos(d),uy=Math.sin(d),c=Math.cos(Math.toRadians(tiltDeg));
        double along=p[0]*ux+p[1]*uy,x=p[0]+(c-1)*along*ux,y=p[1]+(c-1)*along*uy;
        return new double[]{CX+R*x,CY+R*y};
    }
    private static List<GmtRoundMarkerAnalyzer.Marker> markers(double tiltDeg,double dirDeg,int... skip){
        List<GmtRoundMarkerAnalyzer.Marker> ms=new ArrayList<>();
        outer:
        for(int h:GmtRoundMarkerAnalyzer.HOURS){
            for(int s:skip)if(s==h)continue outer;
            GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);
            double[] q=project(GmtRoundMarkerAnalyzer.master(h,Gmt126710BlnrMaster.ROUND_CENTER_R),tiltDeg,dirDeg);
            // About 0.1 px of detection noise (fixed pattern), as on clean real fits (WOS 40411271: residual 0.15 px).
            m.found=true;m.stable=true;m.x=q[0]+0.12*Math.sin(h*1.7);m.y=q[1]+0.12*Math.cos(h*2.3);m.radiusPx=22;
            ms.add(m);
        }
        return ms;
    }

    @Test public void markerLayoutMeasuresTheTilt(){
        GmtMarkerPose.Result f=GmtMarkerPose.estimate(markers(0,0),R);
        assertTrue(f.reason,f.valid);assertTrue(f.describe(),f.nearFrontal());assertTrue(f.tiltDeg<3);
        GmtMarkerPose.Result t=GmtMarkerPose.estimate(markers(12,30),R);
        assertTrue(t.valid);assertEquals(12,t.tiltDeg,1.0);assertFalse(t.nearFrontal());
    }

    @Test public void oneMisplacedMarkerIsLeftOut(){
        // WOS 40411271: the 5 was traced on the wrong edge and sat several px off; with it the fit read 7.8 deg.
        List<GmtRoundMarkerAnalyzer.Marker> ms=markers(0,0);
        for(GmtRoundMarkerAnalyzer.Marker m:ms)if(m.hour==5){m.x+=6;m.y-=5;}
        GmtMarkerPose.Result f=GmtMarkerPose.estimate(ms,R);
        assertTrue(f.reason,f.valid);assertEquals(f.describe(),"5",f.dropped);assertTrue(f.describe(),f.nearFrontal());
    }

    @Test public void tooFewOrOneSidedMarkersGiveNoAngle(){
        assertFalse(GmtMarkerPose.estimate(markers(0,0,1,2,4),R).valid);             // 5 markers
        assertFalse(GmtMarkerPose.estimate(markers(0,0,10,11),R).valid);             // no marker in the 10-11 quarter
        List<GmtRoundMarkerAnalyzer.Marker> ms=markers(0,0);
        for(GmtRoundMarkerAnalyzer.Marker m:ms)if(m.hour<=2)m.stable=false;          // low-confidence markers are not used
        assertFalse(GmtMarkerPose.estimate(ms,R).valid);
    }

    private static GmtRehautSectorAnalyzer.Result wosSectors(){
        // WOS 40411271: 12 13 px, 3 20, 6 15, 9 6; min/mean 0.444.
        return new GmtRehautSectorAnalyzer.Result(13.0,20.0,15.0,6.0,1.0,0.53,1.0,0.79);
    }

    @Test public void rehautAloneCannotRejectANearFrontalMarkerLayout(){
        GmtEllipsePoseAnalyzer.Result noEllipse=new GmtEllipsePoseAnalyzer.Result("stable dial ellipse not found");
        GmtHumanQcMath.PoseDecision before=GmtHumanPosePolicy.classify(null,wosSectors(),noEllipse,Double.NaN);
        assertEquals(GmtHumanQcMath.PoseLabel.RETAKE,before.label);
        GmtMarkerPose.Result mp=GmtMarkerPose.estimate(markers(2,65),R);
        assertTrue(mp.describe(),mp.nearFrontal());
        GmtHumanQcMath.PoseDecision after=GmtHumanPosePolicy.classify(null,wosSectors(),noEllipse,Double.NaN,mp);
        assertEquals(GmtHumanQcMath.PoseLabel.CORRECTABLE,after.label);
        assertTrue(after.reason,after.reason.contains("not rejected on the rehaut alone"));
    }

    @Test public void rehautRetakeStandsWhenTheMarkersShowARealTiltOrNoAngle(){
        GmtEllipsePoseAnalyzer.Result noEllipse=new GmtEllipsePoseAnalyzer.Result("stable dial ellipse not found");
        assertEquals(GmtHumanQcMath.PoseLabel.RETAKE,
                GmtHumanPosePolicy.classify(null,wosSectors(),noEllipse,Double.NaN,GmtMarkerPose.estimate(markers(12,90),R)).label);
        assertEquals(GmtHumanQcMath.PoseLabel.RETAKE,
                GmtHumanPosePolicy.classify(null,wosSectors(),noEllipse,Double.NaN,GmtMarkerPose.estimate(markers(0,0,1,2,4),R)).label);
    }

    @Test public void severeDialEllipseStillRejects(){
        GmtEllipsePoseAnalyzer.Result steep=new GmtEllipsePoseAnalyzer.Result(0.95,18.2,90.0,0.0,1.0);
        assertEquals(GmtHumanQcMath.PoseLabel.RETAKE,
                GmtHumanPosePolicy.classify(null,wosSectors(),steep,Double.NaN,GmtMarkerPose.estimate(markers(0,0),R)).label);
    }
}
