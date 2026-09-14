package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Locates the dial-side edge of the rehaut pair.
 *
 * A GMT photo normally contains two strong, nearby, concentric boundaries around the dial. The
 * QC coordinate system belongs on the smaller / inner member of that pair. This helper deliberately
 * treats pair orientation as independent evidence: if the homography has accidentally been placed
 * on the larger outer boundary, the rectified image will show the companion edge inside radius 1
 * rather than outside it and verification will fail.
 */
final class GmtInnerDialEdgeDetector {
    static final class Refinement {
        final Point[] cardinals;
        final int pairedDirections;
        final double score;
        Refinement(Point[] p,int pairs,double s){cardinals=p;pairedDirections=pairs;score=s;}
    }
    static final class Verification {
        final boolean observed;
        final double score;
        final double anchorPeakRadius;
        final double companionRadius;
        Verification(boolean o,double s,double a,double c){observed=o;score=s;anchorPeakRadius=a;companionRadius=c;}
    }
    private static final class Peak {double r,strength;Peak(double r,double s){this.r=r;strength=s;}}
    private GmtInnerDialEdgeDetector(){}

    static Refinement refineCardinals(Mat image, Point centre, Point[] predicted){
        if(image==null||image.empty()||centre==null||predicted==null||predicted.length<4)return null;
        Mat gray=new Mat();
        try{
            if(image.channels()==1)image.copyTo(gray);else Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);
            Point[] out=new Point[4];int pairs=0;double quality=0;
            for(int i=0;i<4;i++){
                Point q=predicted[i];double vx=q.x-centre.x,vy=q.y-centre.y,pr=Math.hypot(vx,vy);
                if(pr<20){out[i]=q;continue;}double ux=vx/pr,uy=vy/pr,tx=-uy,ty=ux;
                List<Peak> peaks=rayPeaks(gray,centre,ux,uy,tx,ty,pr*.86,pr*1.12,Math.max(1.0,pr/220.0));
                Peak[] pair=bestPair(peaks,pr);
                if(pair!=null){
                    // The dial edge is always the smaller-radius member of the relevant rehaut pair.
                    Peak inner=pair[0].r<pair[1].r?pair[0]:pair[1];Peak outer=inner==pair[0]?pair[1]:pair[0];
                    out[i]=new Point(centre.x+ux*inner.r,centre.y+uy*inner.r);pairs++;
                    double closeness=clamp(1-Math.abs((inner.r+outer.r)*.5-pr)/(pr*.12));
                    double strength=clamp(Math.min(inner.strength,outer.strength)/24.0);
                    quality+=.55*closeness+.45*strength;
                }else{
                    Peak one=nearestStrongPeak(peaks,pr);
                    if(one!=null){out[i]=new Point(centre.x+ux*one.r,centre.y+uy*one.r);quality+=.20*clamp(one.strength/24.0);}else out[i]=q;
                }
            }
            double pairFrac=pairs/4.0,score=clamp(.72*pairFrac+.28*(quality/4.0));
            return new Refinement(out,pairs,score);
        }finally{gray.release();}
    }

    /**
     * Verifies that radius 1 in a rectified dial is the INNER edge of the pair. High confidence
     * requires a strong edge near 1.0 and a second strong companion OUTSIDE it. A stronger companion
     * inside 1.0 is evidence that the outer boundary was selected by mistake.
     */
    static Verification verifyCanonicalPair(Mat image,double cx,double cy,double radius){
        if(image==null||image.empty()||radius<=0)return new Verification(false,0,Double.NaN,Double.NaN);
        Mat gray=new Mat();
        try{
            if(image.channels()==1)image.copyTo(gray);else Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);
            List<Peak> profile=new ArrayList<>();
            for(double f=.88;f<=1.12;f+=.0025){double s=meanRadialGradient(gray,cx,cy,radius*f,Math.max(1.5,radius*.0045));profile.add(new Peak(f,s));}
            List<Peak> peaks=localMaxima(profile);if(peaks.isEmpty())return new Verification(false,0,Double.NaN,Double.NaN);
            Peak anchor=bestIn(peaks,.975,1.025),outside=bestIn(peaks,1.018,1.105),inside=bestIn(peaks,.895,.982);
            if(anchor==null)return new Verification(false,0,Double.NaN,Double.NaN);
            double global=0;for(Peak p:peaks)global=Math.max(global,p.strength);if(global<=1e-6)return new Verification(false,0,anchor.r,Double.NaN);
            double anchorQ=clamp(anchor.strength/global);
            double outerQ=outside==null?0:clamp(outside.strength/global);
            double insideQ=inside==null?0:clamp(inside.strength/global);
            double anchorPos=clamp(1-Math.abs(anchor.r-1.0)/.025);
            double sepQ=outside==null?0:clamp((outside.r-anchor.r-.010)/.035);
            // An inside companion stronger than the outside companion strongly suggests we mapped
            // the larger rehaut boundary to radius 1, which is exactly the failure seen in build 471.
            double orientation=outside==null?0:clamp(.55+(outerQ-insideQ)*1.35);
            double score=clamp(.30*anchorQ+.25*outerQ+.20*anchorPos+.15*sepQ+.10*orientation);
            boolean observed=outside!=null&&anchor.strength>=6.0&&outside.strength>=5.0;
            if(!observed)score=Math.min(score,.35);
            if(insideQ>outerQ+.12)score=Math.min(score,.20);
            return new Verification(observed,score,anchor.r,outside==null?Double.NaN:outside.r);
        }finally{gray.release();}
    }

    private static List<Peak> rayPeaks(Mat gray,Point c,double ux,double uy,double tx,double ty,double lo,double hi,double step){
        List<Peak> samples=new ArrayList<>();for(double r=lo;r<=hi;r+=step)samples.add(new Peak(r,rayGradient(gray,c,ux,uy,tx,ty,r,Math.max(1.5,step*1.5))));return localMaxima(samples);
    }
    private static double rayGradient(Mat g,Point c,double ux,double uy,double tx,double ty,double r,double dr){
        double sum=0,n=0;for(double t=-4;t<=4;t+=2){double a=sample(g,c.x+ux*(r-dr)+tx*t,c.y+uy*(r-dr)+ty*t),b=sample(g,c.x+ux*(r+dr)+tx*t,c.y+uy*(r+dr)+ty*t);if(Double.isFinite(a)&&Double.isFinite(b)){sum+=Math.abs(b-a);n++;}}return n>0?sum/n:0;
    }
    private static double meanRadialGradient(Mat g,double cx,double cy,double r,double dr){
        double sum=0,n=0;for(int deg=0;deg<360;deg+=4){if(deg>=72&&deg<=108)continue;double a=Math.toRadians(deg),ca=Math.cos(a),sa=Math.sin(a);double x1=cx+ca*(r-dr),y1=cy+sa*(r-dr),x2=cx+ca*(r+dr),y2=cy+sa*(r+dr);double v1=sample(g,x1,y1),v2=sample(g,x2,y2);if(Double.isFinite(v1)&&Double.isFinite(v2)){sum+=Math.abs(v2-v1);n++;}}return n>0?sum/n:0;
    }
    private static List<Peak> localMaxima(List<Peak> samples){
        List<Peak> out=new ArrayList<>();for(int i=1;i+1<samples.size();i++){Peak a=samples.get(i-1),b=samples.get(i),c=samples.get(i+1);if(b.strength>=a.strength&&b.strength>=c.strength)out.add(new Peak(b.r,b.strength));}Collections.sort(out,Comparator.comparingDouble((Peak p)->p.strength).reversed());return out;
    }
    private static Peak[] bestPair(List<Peak> peaks,double predicted){
        Peak[] best=null;double bestScore=-1;int limit=Math.min(10,peaks.size());for(int i=0;i<limit;i++)for(int j=i+1;j<limit;j++){Peak a=peaks.get(i),b=peaks.get(j);double lo=Math.min(a.r,b.r),hi=Math.max(a.r,b.r),sep=(hi-lo)/predicted;if(sep<.012||sep>.115)continue;double mid=(lo+hi)*.5,close=clamp(1-Math.abs(mid-predicted)/(predicted*.14)),strength=clamp(Math.min(a.strength,b.strength)/24.0),score=.58*strength+.42*close;if(score>bestScore){bestScore=score;best=new Peak[]{a,b};}}return bestScore>=.28?best:null;
    }
    private static Peak nearestStrongPeak(List<Peak> peaks,double predicted){Peak best=null;double d=Double.POSITIVE_INFINITY;for(Peak p:peaks){if(p.strength<5)continue;double x=Math.abs(p.r-predicted);if(x<d){d=x;best=p;}}return best;}
    private static Peak bestIn(List<Peak> peaks,double lo,double hi){Peak best=null;for(Peak p:peaks)if(p.r>=lo&&p.r<=hi&&(best==null||p.strength>best.strength))best=p;return best;}
    private static double sample(Mat g,double x,double y){int xi=(int)Math.round(x),yi=(int)Math.round(y);if(xi<0||yi<0||xi>=g.cols()||yi>=g.rows())return Double.NaN;double[]v=g.get(yi,xi);return v==null||v.length==0?Double.NaN:v[0];}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
