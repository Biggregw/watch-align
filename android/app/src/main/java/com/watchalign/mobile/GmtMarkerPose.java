package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Camera tilt from the layout of the round hour markers (alpha63).
 *
 * The round-marker centres are fitted with an affine map to where they sit on the master dial;
 * a tilted camera squashes the dial along one axis, so the ratio of the map's two singular values
 * is cos(tilt). The 60 tick is not used (it sits at a different height and radius and moved a
 * near-frontal WOS photo from 2.2 to 6.3 deg). The fit's own uncertainty comes from a
 * leave-one-marker-out jackknife, and the rating uses the upper bound.
 *
 * Why: the rehaut ring is a raised, sloped wall whose visible width also depends on its unknown
 * slope, the edge search and lighting. On genuine near-frontal photos its side-to-side asymmetry
 * varies by about +/-0.19 (+/-10-13 deg of apparent tilt), and one mismeasured sector forced
 * RETAKE on a WOS photo whose markers show 2.0 deg (docs/research/gmt_dial_pose_policy_2026-09-29.md).
 */
final class GmtMarkerPose {
    static final int MIN_MARKERS=6;
    /** RMS fit residual over the dial radius; perspective at 10-15 deg leaves about 0.003-0.005. */
    static final double MAX_RESIDUAL=0.006;
    /** Above this residual, the single worst marker is dropped if that removes 40% of the residual. */
    static final double DROP_RESIDUAL=0.003;
    /** Affine scale against the fitted dial radius (a gross misfit or misnumbered markers otherwise). */
    static final double MAX_SCALE_DEVIATION=0.10;
    /** Upper tilt bound under which the rehaut alone may not reject the photo. */
    static final double NEAR_FRONTAL_MAX_DEG=5.0;

    static final class Result {
        boolean valid;String reason="";
        int markers;String dropped="";
        double tiltDeg=Double.NaN,tiltHighDeg=Double.NaN,residual=Double.NaN,squashSd=Double.NaN;
        /** Confident, and the camera is within NEAR_FRONTAL_MAX_DEG of straight on even at the upper bound. */
        boolean nearFrontal(){return valid&&tiltHighDeg<NEAR_FRONTAL_MAX_DEG;}
        String describe(){
            if(!valid)return "Marker-layout angle: not available ("+reason+").";
            return String.format(Locale.US,"Marker-layout angle: %.1f° (at most %.1f°) from %d round markers%s; fit residual %.4f of the radius.",
                    tiltDeg,tiltHighDeg,markers,dropped.isEmpty()?"":", the "+dropped+" left out as misplaced",residual);
        }
    }

    /** Fit of the given hours; null when degenerate. {ratio, squashX, squashY, residual/radius, scale}. */
    static double[] fit(List<Integer> hours,List<double[]> pts){
        List<double[]> src=new ArrayList<>();
        for(int h:hours)src.add(GmtRoundMarkerAnalyzer.master(h,Gmt126710BlnrMaster.ROUND_CENTER_R));
        double[][] A=GmtRoundMarkerAnalyzer.affine(src,pts);
        if(A==null)return null;
        double m00=A[0][0],m01=A[0][1],m10=A[1][0],m11=A[1][1];
        // Singular values of the 2x2 linear part from the eigenvalues of M^T M.
        double p=m00*m00+m10*m10,q=m00*m01+m10*m11,t=m01*m01+m11*m11;
        double tr=p+t,det=p*t-q*q,disc=Math.sqrt(Math.max(0,tr*tr/4-det));
        double l1=tr/2+disc,l2=tr/2-disc;
        if(!(l1>0)||!(l2>0))return null;
        double s1=Math.sqrt(l1),s2=Math.sqrt(l2),ratio=s2/s1;
        // Compressed master direction: eigenvector of M^T M for l2.
        double vx=q,vy=l2-p;if(Math.hypot(vx,vy)<1e-12){vx=l2-t;vy=q;}
        double phi=Math.atan2(vy,vx),e=1-ratio;
        double ss=0;
        for(int i=0;i<src.size();i++){
            double x=A[0][0]*src.get(i)[0]+A[0][1]*src.get(i)[1]+A[0][2],y=A[1][0]*src.get(i)[0]+A[1][1]*src.get(i)[1]+A[1][2];
            ss+=Math.pow(x-pts.get(i)[0],2)+Math.pow(y-pts.get(i)[1],2);
        }
        double scale=Math.sqrt(s1*s2);
        return new double[]{ratio,e*Math.cos(2*phi),e*Math.sin(2*phi),Math.sqrt(ss/src.size())/scale,scale};
    }

    /** @param radiusPx fitted dial radius; markers: only found, confidently traced ones are used. */
    static Result estimate(List<GmtRoundMarkerAnalyzer.Marker> markers,double radiusPx){
        Result r=new Result();
        List<Integer> hs=new ArrayList<>();List<double[]> pts=new ArrayList<>();
        if(markers!=null)for(GmtRoundMarkerAnalyzer.Marker m:markers)
            if(m.found&&m.stable&&Double.isFinite(m.x)&&Double.isFinite(m.y)){hs.add(m.hour);pts.add(new double[]{m.x,m.y});}
        r.markers=hs.size();
        if(hs.size()<MIN_MARKERS){r.reason=hs.size()+" round markers traced cleanly, "+MIN_MARKERS+" needed";return r;}
        double[] f=fit(hs,pts);
        if(f==null){r.reason="marker fit degenerate";return r;}
        if(f[3]>DROP_RESIDUAL&&hs.size()>MIN_MARKERS){
            int best=-1;double[] bf=null;
            for(int i=0;i<hs.size();i++){
                List<Integer> h2=new ArrayList<>(hs);List<double[]> p2=new ArrayList<>(pts);h2.remove(i);p2.remove(i);
                double[] g=fit(h2,p2);
                if(g!=null&&(bf==null||g[3]<bf[3])){bf=g;best=i;}
            }
            if(bf!=null&&bf[3]<=0.6*f[3]){r.dropped=String.valueOf(hs.get(best));hs.remove(best);pts.remove(best);f=bf;}
        }
        r.markers=hs.size();r.residual=f[3];
        boolean[] q=new boolean[4];for(int h:hs)q[(h-1)/3]=true;
        if(!(q[0]&&q[1]&&q[2]&&q[3])){r.reason="the round markers found do not cover all four quarters of the dial";return r;}
        if(f[3]>MAX_RESIDUAL){r.reason=String.format(Locale.US,"markers do not fit the dial layout closely (residual %.4f)",f[3]);return r;}
        if(radiusPx>0&&Math.abs(f[4]/radiusPx-1)>MAX_SCALE_DEVIATION){r.reason=String.format(Locale.US,"marker layout is %.2fx the fitted dial size",f[4]/radiusPx);return r;}
        // Jackknife over markers: spread of the squash vector.
        int n=hs.size();double[][] jk=new double[n][];double mx=0,my=0;
        for(int i=0;i<n;i++){
            List<Integer> h2=new ArrayList<>(hs);List<double[]> p2=new ArrayList<>(pts);h2.remove(i);p2.remove(i);
            double[] g=fit(h2,p2);
            if(g==null){r.reason="marker fit unstable without one marker";return r;}
            jk[i]=new double[]{g[1],g[2]};mx+=g[1];my+=g[2];
        }
        mx/=n;my/=n;double v=0;
        for(double[] j:jk)v+=Math.pow(j[0]-mx,2)+Math.pow(j[1]-my,2);
        double sd=Math.sqrt((n-1.0)/n*v);
        double e=1-f[0],eHi=Math.min(0.5,e+2*sd);
        r.squashSd=sd;r.tiltDeg=Math.toDegrees(Math.acos(Math.min(1,f[0])));r.tiltHighDeg=Math.toDegrees(Math.acos(1-eHi));
        r.valid=true;
        return r;
    }

    private GmtMarkerPose(){}
}
