package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Robust local rehaut visibility around 12/3/6/9.
 *
 * This deliberately does not require a clean 360-degree harmonic fit. The global
 * rehaut analyser supplies only the two annular edge seeds; each watch-relative
 * sector then re-measures its own local visible width using medians and MAD
 * rejection. This mirrors the human observation "how much rehaut can I see here
 * versus the opposite side?" and remains useful when engraving or reflections
 * make the global fit noisy.
 */
final class GmtRehautSectorAnalyzer {
    static final class Result {
        final boolean valid;
        final String reason;
        final double width12,width3,width6,width9;
        final double coverage12,coverage3,coverage6,coverage9;
        final double verticalAsymmetry,horizontalAsymmetry,minOverMean;
        Result(String reason){
            valid=false;this.reason=reason;
            width12=width3=width6=width9=Double.NaN;
            coverage12=coverage3=coverage6=coverage9=0.0;
            verticalAsymmetry=horizontalAsymmetry=minOverMean=Double.NaN;
        }
        Result(double w12,double w3,double w6,double w9,
               double c12,double c3,double c6,double c9){
            valid=true;reason="";
            width12=w12;width3=w3;width6=w6;width9=w9;
            coverage12=c12;coverage3=c3;coverage6=c6;coverage9=c9;
            verticalAsymmetry=safeAsym(w12,w6);
            horizontalAsymmetry=safeAsym(w3,w9);
            double mean=(w12+w3+w6+w9)/4.0;
            minOverMean=mean>0?Math.min(Math.min(w12,w3),Math.min(w6,w9))/mean:Double.NaN;
        }

        boolean verticalReliable(){return valid&&coverage12>=0.35&&coverage6>=0.35&&Double.isFinite(verticalAsymmetry);}
        boolean horizontalReliable(){return valid&&coverage3>=0.35&&coverage9>=0.35&&Double.isFinite(horizontalAsymmetry);}
        double meanCoverage(){return valid?(coverage12+coverage3+coverage6+coverage9)/4.0:0.0;}

        GmtHumanQcMath.GapTrend gapTrendAt12(){
            if(!verticalReliable())return GmtHumanQcMath.GapTrend.UNKNOWN;
            // Direction only, never a numeric correction. For a recessed dial the
            // far-side rehaut wall is more visible. If the 6-side wall is wider than
            // the 12-side wall (negative V), the camera is nearer 12 and local
            // perspective tends to make the normalized 12-side radial gap look more
            // generous. The opposite sign tends to compress it. A straight-on real
            // baseline measured V=+0.043, while the earlier gap-enhancing QC pose was
            // about V=-0.094, so Alpha40 uses a deliberately modest dead band.
            final double directionalThreshold=0.075;
            if(verticalAsymmetry<=-directionalThreshold)return GmtHumanQcMath.GapTrend.INFLATED;
            if(verticalAsymmetry>= directionalThreshold)return GmtHumanQcMath.GapTrend.COMPRESSED;
            return GmtHumanQcMath.GapTrend.NEUTRAL;
        }
    }

    private static final int HALF_ARC_DEG=18;
    private static final int STEP_DEG=2;

    static Result analyse(Mat bgr,double cx,double cy,double innerSeed,double outerSeed,double watchTwelveClockDeg){
        if(bgr==null||bgr.empty()||!(outerSeed>innerSeed+2)||!(innerSeed>5))
            return new Result("rehaut annular seeds unavailable");
        Mat gray=new Mat();
        try{
            if(bgr.channels()==1)bgr.copyTo(gray);else Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);
            double separation=outerSeed-innerSeed;
            int win=Math.max(3,(int)Math.round(0.55*separation));
            Sector s12=sector(gray,cx,cy,innerSeed,outerSeed,watchTwelveClockDeg,win);
            Sector s3 =sector(gray,cx,cy,innerSeed,outerSeed,watchTwelveClockDeg+90.0,win);
            Sector s6 =sector(gray,cx,cy,innerSeed,outerSeed,watchTwelveClockDeg+180.0,win);
            Sector s9 =sector(gray,cx,cy,innerSeed,outerSeed,watchTwelveClockDeg+270.0,win);
            if(!s12.valid||!s3.valid||!s6.valid||!s9.valid)
                return new Result("one or more cardinal rehaut sectors could not be constrained");
            return new Result(s12.width,s3.width,s6.width,s9.width,
                    s12.coverage,s3.coverage,s6.coverage,s9.coverage);
        }finally{gray.release();}
    }

    private static final class Sector{
        final boolean valid;final double width,coverage;
        Sector(boolean v,double w,double c){valid=v;width=w;coverage=c;}
    }

    private static Sector sector(Mat gray,double cx,double cy,double inner,double outer,double targetClockDeg,int win){
        List<Double> widths=new ArrayList<>();
        int total=0;
        for(int d=-HALF_ARC_DEG;d<=HALF_ARC_DEG;d+=STEP_DEG){
            total++;
            double clock=norm360(targetClockDeg+d),t=Math.toRadians(clock);
            double sx=Math.sin(t),sy=-Math.cos(t);
            Edge ei=bestEdge(gray,cx,cy,sx,sy,inner,win);
            Edge eo=bestEdge(gray,cx,cy,sx,sy,outer,win);
            if(!ei.valid||!eo.valid||ei.strength<3.0||eo.strength<3.0)continue;
            double w=eo.radius-ei.radius;
            double sep=outer-inner;
            if(w<=1.0||w>sep*2.8)continue;
            widths.add(w);
        }
        if(widths.size()<4)return new Sector(false,Double.NaN,widths.size()/(double)Math.max(1,total));
        double[] raw=new double[widths.size()];for(int i=0;i<raw.length;i++)raw[i]=widths.get(i);
        double med=median(raw);
        double[] dev=new double[raw.length];for(int i=0;i<raw.length;i++)dev[i]=Math.abs(raw[i]-med);
        double mad=median(dev),lim=Math.max(1.5,3.0*1.4826*mad);
        double[] kept=new double[raw.length];int n=0;
        for(double x:raw)if(Math.abs(x-med)<=lim)kept[n++]=x;
        if(n<4)return new Sector(false,Double.NaN,n/(double)Math.max(1,total));
        return new Sector(true,median(Arrays.copyOf(kept,n)),n/(double)Math.max(1,total));
    }

    private static final class Edge{final boolean valid;final double radius,strength;Edge(boolean v,double r,double s){valid=v;radius=r;strength=s;}}

    private static Edge bestEdge(Mat gray,double cx,double cy,double sx,double sy,double seed,int win){
        int c=(int)Math.round(seed),a=Math.max(2,c-win),b=c+win;
        double best=-1;int bestR=-1;
        for(int r=a;r<=b;r++){
            double g=gradient(gray,cx,cy,sx,sy,r);
            if(g>best){best=g;bestR=r;}
        }
        return bestR>=0?new Edge(true,bestR,best):new Edge(false,Double.NaN,0);
    }

    private static double gradient(Mat gray,double cx,double cy,double sx,double sy,int r){
        double a=sample(gray,cx+sx*(r-1),cy+sy*(r-1));
        double b=sample(gray,cx+sx*(r+1),cy+sy*(r+1));
        return Double.isFinite(a)&&Double.isFinite(b)?Math.abs(b-a)*0.5:0.0;
    }

    private static double sample(Mat gray,double x,double y){
        int xi=(int)Math.round(x),yi=(int)Math.round(y);
        if(xi<0||yi<0||xi>=gray.cols()||yi>=gray.rows())return Double.NaN;
        double[]v=gray.get(yi,xi);return v==null||v.length==0?Double.NaN:v[0];
    }

    private static double median(double[]x){double[]c=x.clone();Arrays.sort(c);int n=c.length;return n==0?Double.NaN:(n%2==1?c[n/2]:(c[n/2-1]+c[n/2])*0.5);}
    private static double safeAsym(double a,double b){double d=a+b;return Math.abs(d)<1e-9?Double.NaN:(a-b)/d;}
    private static double norm360(double d){d%=360.0;if(d<0)d+=360.0;return d;}
    private GmtRehautSectorAnalyzer(){}
}
