package com.watchalign.mobile.qc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Confidence in the corrected four-anchor homography, independent of component detection. */
public final class RectificationConfidenceService {
    private static final double EPS=1e-9;

    public static final class Validation {
        public final double anchorReprojectionRms;
        public final double oppositeAxisRatio;
        public final double sourceCircularity;
        /** Independent score that the selected edge is the INNER member of the rehaut/dial-edge pair. */
        public final double rectifiedCircularity;
        public final boolean boundaryObserved;

        public Validation(double reprojection,double axes,double source,double rectified,boolean observed){
            anchorReprojectionRms=reprojection;oppositeAxisRatio=axes;sourceCircularity=source;rectifiedCircularity=rectified;boundaryObserved=observed;
        }
        public static Validation anchorsOnly(double axes){return new Validation(0,axes,Double.NaN,Double.NaN,false);}
    }

    public static final class Assessment {
        private final QcModuleResult.Confidence confidence;private final double score;private final List<String> evidence;
        private Assessment(QcModuleResult.Confidence c,double s,List<String> e){confidence=c;score=s;evidence=Collections.unmodifiableList(new ArrayList<>(e));}
        public QcModuleResult.Confidence confidence(){return confidence;} public double score(){return score;} public List<String> evidence(){return evidence;}
        public QcModuleResult.Confidence cap(QcModuleResult.Confidence component){return component.ordinal()>confidence.ordinal()?confidence:component;}
    }

    private RectificationConfidenceService(){}

    public static Assessment assess(double x12,double y12,double x3,double y3,double x6,double y6,double x9,double y9){
        double a=Math.hypot(x6-x12,y6-y12),b=Math.hypot(x9-x3,y9-y3);
        return assess(x12,y12,x3,y3,x6,y6,x9,y9,Validation.anchorsOnly(Math.min(a,b)/Math.max(EPS,Math.max(a,b))));
    }

    public static Assessment assess(double x12,double y12,double x3,double y3,double x6,double y6,double x9,double y9,Validation validation){
        double[][] p={{x12,y12},{x3,y3},{x6,y6},{x9,y9}};for(double[] q:p)for(double v:q)if(!Double.isFinite(v))return low("Rectification anchors contain non-finite coordinates");
        double sign=0,minSide=Double.POSITIVE_INFINITY,maxSide=0,area=0;
        for(int i=0;i<4;i++){double[] a=p[i],b=p[(i+1)%4],c=p[(i+2)%4];double cross=(b[0]-a[0])*(c[1]-b[1])-(b[1]-a[1])*(c[0]-b[0]);if(Math.abs(cross)<EPS||sign!=0&&Math.signum(cross)!=sign)return low("Rectification anchors are crossed, non-convex or nearly collinear");sign=Math.signum(cross);double side=Math.hypot(b[0]-a[0],b[1]-a[1]);minSide=Math.min(minSide,side);maxSide=Math.max(maxSide,side);area+=a[0]*b[1]-a[1]*b[0];}
        double d12=Math.hypot(x6-x12,y6-y12),d39=Math.hypot(x9-x3,y9-y3);if(minSide<EPS||d12<EPS||d39<EPS)return low("Rectification anchor geometry is degenerate");
        double sideScore=ramp(minSide/maxSide,.18,.58);double axisScore=ramp(validation.oppositeAxisRatio,.35,.78);double areaScore=ramp(Math.abs(area)*.5/(d12*d39),.20,.42);double reproScore=Double.isFinite(validation.anchorReprojectionRms)?1-ramp(validation.anchorReprojectionRms,.003,.02):.5;

        // Anchor fit can never create HIGH confidence on its own. The four anchors are the data used
        // to construct the homography, so a tiny reprojection residual is not independent evidence.
        // HIGH therefore requires a separately observed inner/outer dial-edge pair after rectification.
        double boundaryScore=.60;
        if(validation.boundaryObserved&&Double.isFinite(validation.rectifiedCircularity))boundaryScore=clamp(validation.rectifiedCircularity);

        double score=Math.min(Math.min(sideScore,axisScore),Math.min(areaScore,Math.min(reproScore,boundaryScore)));
        QcModuleResult.Confidence c=score>=.72?QcModuleResult.Confidence.HIGH:score>=.42?QcModuleResult.Confidence.MEDIUM:QcModuleResult.Confidence.LOW;
        List<String> evidence=new ArrayList<>();evidence.add(String.format(Locale.US,"Rectification confidence %.2f (%s): anchor reprojection %.4f DR, opposite-axis consistency %.2f",score,c.name().toLowerCase(Locale.US),validation.anchorReprojectionRms,validation.oppositeAxisRatio));
        evidence.add(validation.boundaryObserved?String.format(Locale.US,"Independent inner dial-edge / outer rehaut pair verification %.2f",validation.rectifiedCircularity):"Independent inner/outer dial-edge pair was not verified; rectification confidence is capped at medium");
        if(c!=QcModuleResult.Confidence.HIGH)evidence.add("Fine planar measurements are withheld or downgraded because the rectification is not fully constrained");
        return new Assessment(c,score,evidence);
    }

    private static Assessment low(String reason){return new Assessment(QcModuleResult.Confidence.LOW,0,Collections.singletonList(reason));}
    private static double ramp(double v,double lo,double hi){return v<=lo?0:v>=hi?1:(v-lo)/(hi-lo);}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
}
