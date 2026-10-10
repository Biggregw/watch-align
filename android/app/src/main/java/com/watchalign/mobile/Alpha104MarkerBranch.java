package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alpha104: does the frozen pose sit on the right minute branch? The minute lattice repeats every 6 deg, and the 12
 * marker that picks the branch (GmtTwelveLandmarkAnalyzer) can be hidden: with both hands over the 12 a QC video frame
 * locked one minute off, every marker then failed its outline test and the whole dial was withheld as "hand crosses
 * marker" (owner's NecoClock 124060 frame 3, 2026-10-09). Every applied marker votes instead: for the pose turned by k
 * minutes (k = -2..2) the median contrast of marker faces against the dial at the same radius 2.5 minutes away is
 * measured; the correct branch is the one where the faces stand out. Measured on 4 QC frames and 4 genuine photos: the
 * locked branch scores 203-211 grey levels, its neighbours 61-80; the mis-locked frame scores 211 one minute away.
 */
final class Alpha104MarkerBranch {
    static final int MAX_K=2;
    /** decisive: the best branch beats the runner-up by this fraction of its own score and by at least MIN_CONTRAST */
    static final double MIN_MARGIN_FRAC=0.25,MIN_CONTRAST=15;

    static final class Result {
        final double[] score=new double[2*MAX_K+1];
        int best;boolean decisive;
        double scoreAt(int k){return score[k+MAX_K];}
    }

    private Alpha104MarkerBranch(){}

    /** @param H canonical -> image homography (normalised, row-major) */
    static Result analyse(Alpha99MarkerInterference.Sampler img,double[] H,ModelSpec model){
        Result r=new Result();
        if(img==null||H==null){r.best=0;r.decisive=false;return r;}
        double bestAbs=Double.NEGATIVE_INFINITY,second=Double.NEGATIVE_INFINITY;
        for(int k=-MAX_K;k<=MAX_K;k++){
            double s=score(img,H,model,Math.toRadians(6.0*k));r.score[k+MAX_K]=s;
            // luminous markers on a darker dial (every supported model); the turned branches put marker faces on the dial
            // and dial samples on markers, so they score low or negative
            double v=Double.isFinite(s)?s:Double.NEGATIVE_INFINITY;
            if(v>bestAbs){second=bestAbs;bestAbs=v;r.best=k;}else if(v>second)second=v;
        }
        r.decisive=Double.isFinite(bestAbs)&&bestAbs>=MIN_CONTRAST&&bestAbs-second>=MIN_MARGIN_FRAC*bestAbs;
        return r;
    }

    /** Median over the model's applied markers of (mean grey on the face - mean grey on the dial 2.5 minutes either side),
     *  with the pose turned clockwise by {@code turn} radians. */
    static double score(Alpha99MarkerInterference.Sampler img,double[] H,ModelSpec model,double turn){
        List<Double> v=new ArrayList<>();
        for(ModelSpec.Marker mk:model.markers){
            double[] c=mk.masterPoint();double r=Math.hypot(c[0],c[1]);double ang=Math.atan2(c[0],-c[1]);
            double rad=mk.shape==ModelSpec.Shape.ROUND?0.6*mk.outerR:mk.shape==ModelSpec.Shape.BATON?0.6*mk.tangentialHalf:0.4*mk.halfBase;
            double in=disc(img,H,r,ang+turn,rad);
            double bg=0.5*(disc(img,H,r,ang+turn+Math.toRadians(15),rad)+disc(img,H,r,ang+turn-Math.toRadians(15),rad));
            if(Double.isFinite(in)&&Double.isFinite(bg))v.add(in-bg);
        }
        if(v.size()<5)return Double.NaN;
        Collections.sort(v);return v.get(v.size()/2);
    }

    /** Canonical rotation turning a point clockwise by {@code turn} radians (12 at the top, y down): H' = H . R. */
    static double[] turnPose(double[] H,double turn){
        double c=Math.cos(turn),s=Math.sin(turn);double[] R={c,-s,0,s,c,0,0,0,1},o=new double[9];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){double x=0;for(int k=0;k<3;k++)x+=H[i*3+k]*R[k*3+j];o[i*3+j]=x;}
        return o;
    }

    private static double disc(Alpha99MarkerInterference.Sampler img,double[] H,double r,double ang,double rad){
        double sum=0;int n=0;double cx=Math.sin(ang)*r,cy=-Math.cos(ang)*r;
        for(int i=-3;i<=3;i++)for(int j=-3;j<=3;j++){double dx=rad*i/3.0,dy=rad*j/3.0;if(dx*dx+dy*dy>rad*rad)continue;
            double[] p=project(H,cx+dx,cy+dy);if(p==null)continue;double g=img.at(p[0],p[1]);if(Double.isFinite(g)){sum+=g;n++;}}
        return n==0?Double.NaN:sum/n;
    }
    private static double[] project(double[] H,double x,double y){
        double w=H[6]*x+H[7]*y+H[8];if(Math.abs(w)<1e-12)return null;
        return new double[]{(H[0]*x+H[1]*y+H[2])/w,(H[3]*x+H[4]*y+H[5])/w};
    }
}
