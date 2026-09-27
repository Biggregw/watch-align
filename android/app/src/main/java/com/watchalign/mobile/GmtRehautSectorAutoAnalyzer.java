package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Independent local rehaut-sector recovery.
 *
 * Unlike GmtRehautSectorAnalyzer this path does not require global 360-degree
 * edge seeds. Each cardinal sector estimates its own annular edge pair from a
 * sector-median radial gradient profile, then refines that pair ray-by-ray.
 * It exists specifically for screenshots/reflections where the global rehaut
 * model fails but a human can still plainly compare visible rehaut at 12/3/6/9.
 *
 * Alpha36 deliberately excludes the central few degrees of each cardinal sector.
 * At 12 that avoids the triangle/minute tick itself contaminating the radial edge
 * profile; at 3/6/9 it similarly reduces hour-marker/cyclops contamination. A
 * small radius-scale sweep is also allowed because a screenshot can make the
 * circular dial seed slightly too small or large while still preserving useful
 * local rehaut evidence.
 */
final class GmtRehautSectorAutoAnalyzer {
    private static final int HALF_ARC=24;
    private static final int CENTRAL_EXCLUDE=6;
    private static final int STEP=2;
    private static final double[] RADIUS_SCALES={1.00,0.95,1.05,0.90,1.10};

    static GmtRehautSectorAnalyzer.Result analyse(Mat bgr,double cx,double cy,double dialR,double watchTwelveClockDeg){
        if(bgr==null||bgr.empty()||!(dialR>25))
            return new GmtRehautSectorAnalyzer.Result("invalid dial seed for independent sector recovery");
        Mat gray=new Mat();
        try{
            if(bgr.channels()==1)bgr.copyTo(gray); else Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);

            GmtRehautSectorAnalyzer.Result best=null;
            double bestScore=-Double.MAX_VALUE;
            String bestFailure="independent sector recovery could not constrain all cardinal sectors";
            for(double scale:RADIUS_SCALES){
                double rr=dialR*scale;
                Sector s12=sector(gray,cx,cy,rr,watchTwelveClockDeg);
                Sector s3 =sector(gray,cx,cy,rr,watchTwelveClockDeg+90.0);
                Sector s6 =sector(gray,cx,cy,rr,watchTwelveClockDeg+180.0);
                Sector s9 =sector(gray,cx,cy,rr,watchTwelveClockDeg+270.0);
                if(!s12.valid||!s3.valid||!s6.valid||!s9.valid){
                    if(Math.abs(scale-1.0)<1e-9){
                        bestFailure=String.format(java.util.Locale.US,
                                "independent sector recovery incomplete (12 %s, 3 %s, 6 %s, 9 %s)",
                                s12.valid?"ok":"x",s3.valid?"ok":"x",s6.valid?"ok":"x",s9.valid?"ok":"x");
                    }
                    continue;
                }
                GmtRehautSectorAnalyzer.Result candidate=new GmtRehautSectorAnalyzer.Result(
                        s12.width,s3.width,s6.width,s9.width,
                        s12.coverage,s3.coverage,s6.coverage,s9.coverage);
                double meanCoverage=(s12.coverage+s3.coverage+s6.coverage+s9.coverage)/4.0;
                double coherence=Double.isFinite(candidate.minOverMean)?candidate.minOverMean:0.0;
                double score=meanCoverage+0.10*coherence-0.30*Math.abs(scale-1.0);
                if(score>bestScore){bestScore=score;best=candidate;}
            }
            return best!=null?best:new GmtRehautSectorAnalyzer.Result(bestFailure);
        }finally{gray.release();}
    }

    private static final class Sector{
        final boolean valid; final double width,coverage;
        Sector(boolean v,double w,double c){valid=v;width=w;coverage=c;}
    }

    private static boolean useRay(int d){return Math.abs(d)>CENTRAL_EXCLUDE;}

    private static Sector sector(Mat gray,double cx,double cy,double dialR,double targetClock){
        int r0=Math.max(3,(int)Math.floor(.68*dialR));
        int r1=(int)Math.ceil(1.10*dialR);
        double frameR=Math.min(Math.min(cx,cy),Math.min(gray.cols()-1.0-cx,gray.rows()-1.0-cy));
        r1=Math.min(r1,(int)Math.floor(frameR-2));
        if(r1-r0<12)return new Sector(false,Double.NaN,0);

        int nR=r1-r0+1;
        double[] profile=new double[nR];
        int[] count=new int[nR];
        int rays=0;
        for(int d=-HALF_ARC;d<=HALF_ARC;d+=STEP){
            if(!useRay(d))continue;
            rays++;
            double a=norm360(targetClock+d),t=Math.toRadians(a),sx=Math.sin(t),sy=-Math.cos(t);
            for(int r=r0+1;r<r1;r++){
                double g=gradient(gray,cx,cy,sx,sy,r);
                if(Double.isFinite(g)){profile[r-r0]+=g;count[r-r0]++;}
            }
        }
        if(rays<6)return new Sector(false,Double.NaN,0);
        for(int i=0;i<nR;i++)if(count[i]>0)profile[i]/=count[i];

        Pair seed=bestPair(profile,r0,dialR);
        if(!seed.valid)return new Sector(false,Double.NaN,0);

        double sep=seed.outer-seed.inner;
        int win=Math.max(3,(int)Math.round(.42*sep));
        List<Double> widths=new ArrayList<>();int total=0;
        for(int d=-HALF_ARC;d<=HALF_ARC;d+=STEP){
            if(!useRay(d))continue;
            total++;
            double a=norm360(targetClock+d),t=Math.toRadians(a),sx=Math.sin(t),sy=-Math.cos(t);
            Edge ei=bestEdge(gray,cx,cy,sx,sy,seed.inner,win,r0,r1);
            Edge eo=bestEdge(gray,cx,cy,sx,sy,seed.outer,win,r0,r1);
            if(!ei.valid||!eo.valid||ei.strength<2.3||eo.strength<2.3)continue;
            double w=eo.radius-ei.radius;
            if(w<=1||w<.40*sep||w>2.25*sep)continue;
            widths.add(w);
        }
        if(widths.size()<5)return new Sector(false,Double.NaN,widths.size()/(double)Math.max(1,total));
        double[] raw=new double[widths.size()];for(int i=0;i<raw.length;i++)raw[i]=widths.get(i);
        double med=median(raw);
        double[] dev=new double[raw.length];for(int i=0;i<raw.length;i++)dev[i]=Math.abs(raw[i]-med);
        double mad=median(dev),lim=Math.max(1.5,3.0*1.4826*mad);
        double[] kept=new double[raw.length];int k=0;
        for(double x:raw)if(Math.abs(x-med)<=lim)kept[k++]=x;
        if(k<5)return new Sector(false,Double.NaN,k/(double)Math.max(1,total));
        return new Sector(true,median(Arrays.copyOf(kept,k)),k/(double)total);
    }

    private static final class Pair{
        final boolean valid;final int inner,outer;final double score;
        Pair(boolean v,int i,int o,double s){valid=v;inner=i;outer=o;score=s;}
    }

    private static Pair bestPair(double[] p,int r0,double dialR){
        List<Integer> peaks=new ArrayList<>();
        for(int i=2;i<p.length-2;i++){
            if(p[i]>=p[i-1]&&p[i]>=p[i+1]&&p[i]>=2.3)peaks.add(i+r0);
        }
        if(peaks.size()<2)return new Pair(false,-1,-1,Double.NaN);
        Pair best=new Pair(false,-1,-1,-Double.MAX_VALUE);
        for(int a=0;a<peaks.size();a++)for(int b=a+1;b<peaks.size();b++){
            int inner=peaks.get(a),outer=peaks.get(b);double sep=outer-inner;
            if(sep<.020*dialR||sep>.18*dialR)continue;
            double mid=(inner+outer)*.5/dialR;
            if(mid<.72||mid>1.08)continue;
            double gi=p[inner-r0],go=p[outer-r0];
            double sepTarget=.075*dialR;
            double sepPenalty=Math.abs(sep-sepTarget)/Math.max(1.0,sepTarget);
            double centrePenalty=Math.abs(mid-.90);
            double score=gi+go-1.8*sepPenalty-6.5*centrePenalty;
            if(score>best.score)best=new Pair(true,inner,outer,score);
        }
        return best;
    }

    private static final class Edge{
        final boolean valid;final int radius;final double strength;
        Edge(boolean v,int r,double s){valid=v;radius=r;strength=s;}
    }
    private static Edge bestEdge(Mat g,double cx,double cy,double sx,double sy,int seed,int win,int lo,int hi){
        int a=Math.max(lo+1,seed-win),b=Math.min(hi-1,seed+win),bestR=-1;double best=-1;
        for(int r=a;r<=b;r++){double v=gradient(g,cx,cy,sx,sy,r);if(v>best){best=v;bestR=r;}}
        return new Edge(bestR>=0,bestR,best);
    }
    private static double gradient(Mat g,double cx,double cy,double sx,double sy,int r){
        double a=sample(g,cx+sx*(r-1),cy+sy*(r-1)),b=sample(g,cx+sx*(r+1),cy+sy*(r+1));
        return Double.isFinite(a)&&Double.isFinite(b)?Math.abs(b-a)*.5:Double.NaN;
    }
    private static double sample(Mat g,double x,double y){
        int xi=(int)Math.round(x),yi=(int)Math.round(y);
        if(xi<0||yi<0||xi>=g.cols()||yi>=g.rows())return Double.NaN;
        double[]v=g.get(yi,xi);return v==null||v.length==0?Double.NaN:v[0];
    }
    private static double median(double[]x){double[]c=x.clone();Arrays.sort(c);int n=c.length;return n==0?Double.NaN:(n%2==1?c[n/2]:(c[n/2-1]+c[n/2])*.5);}
    private static double norm360(double d){d%=360;if(d<0)d+=360;return d;}
    private GmtRehautSectorAutoAnalyzer(){}
}
