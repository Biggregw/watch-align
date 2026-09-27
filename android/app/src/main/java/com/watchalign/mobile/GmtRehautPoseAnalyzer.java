package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Research-backed rehaut pose signal for GMT photographs.
 *
 * Measures the visible width between two persistent annular edges around 360°.
 * The first harmonic gives a signed near/far direction while collapse of the
 * narrowest fitted section is a strong poor-angle cue. No Rolex dimension is
 * assumed and no production master geometry is changed.
 */
final class GmtRehautPoseAnalyzer {
    static final class Result {
        final boolean valid;
        final String reason;
        final double topWidthPx,bottomWidthPx,leftWidthPx,rightWidthPx,meanWidthPx;
        final double verticalAsymmetry,horizontalAsymmetry;
        final double watchVerticalAsymmetry,watchHorizontalAsymmetry;
        final double firstHarmonicStrength,widestClockDeg,minWidthOverMean,edgeCoverage,fitResidual;
        final double innerSeedPx,outerSeedPx;
        Result(String reason) {
            this.valid=false; this.reason=reason;
            topWidthPx=bottomWidthPx=leftWidthPx=rightWidthPx=meanWidthPx=Double.NaN;
            verticalAsymmetry=horizontalAsymmetry=watchVerticalAsymmetry=watchHorizontalAsymmetry=Double.NaN;
            firstHarmonicStrength=widestClockDeg=minWidthOverMean=edgeCoverage=fitResidual=Double.NaN;
            innerSeedPx=outerSeedPx=Double.NaN;
        }
        Result(double top,double bottom,double left,double right,double mean,
               double v,double h,double watchV,double watchH,double strength,
               double widest,double minMean,double coverage,double residual,
               double inner,double outer) {
            valid=true; reason="";
            topWidthPx=top;bottomWidthPx=bottom;leftWidthPx=left;rightWidthPx=right;meanWidthPx=mean;
            verticalAsymmetry=v;horizontalAsymmetry=h;watchVerticalAsymmetry=watchV;watchHorizontalAsymmetry=watchH;
            firstHarmonicStrength=strength;widestClockDeg=widest;minWidthOverMean=minMean;
            edgeCoverage=coverage;fitResidual=residual;innerSeedPx=inner;outerSeedPx=outer;
        }
    }

    private static final int ANGLE_STEP_DEG=2;
    private static final int N_ANGLE=360/ANGLE_STEP_DEG;

    static Result analyse(Mat bgr,double cx,double cy,double seedR,double watchRollClockDeg) {
        if(bgr==null||bgr.empty()||!(seedR>20))return new Result("invalid dial seed");
        Mat gray=new Mat();
        try{
            if(bgr.channels()==1)bgr.copyTo(gray); else Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);
            double frameR=Math.min(Math.min(cx,cy),Math.min(gray.cols()-1.0-cx,gray.rows()-1.0-cy));
            int maxR=(int)Math.floor(Math.min(frameR*0.98,seedR*1.30));
            int minR=Math.max(2,(int)Math.floor(seedR*0.50));
            if(maxR<=minR+10)return new Result("insufficient image area around dial");

            double[][] grad=new double[N_ANGLE][maxR+1];
            double[] radialMean=new double[maxR+1];
            int[] radialCount=new int[maxR+1];
            for(int ai=0;ai<N_ANGLE;ai++){
                double deg=ai*ANGLE_STEP_DEG;
                double t=Math.toRadians(deg),sx=Math.sin(t),sy=-Math.cos(t);
                for(int rr=minR;rr<=maxR;rr++){
                    double v=sample(gray,cx+sx*rr,cy+sy*rr);
                    if(Double.isFinite(v)){radialMean[rr]+=v;radialCount[rr]++;}
                }
                for(int rr=minR+1;rr<maxR;rr++){
                    double a=sample(gray,cx+sx*(rr-1),cy+sy*(rr-1));
                    double b=sample(gray,cx+sx*(rr+1),cy+sy*(rr+1));
                    grad[ai][rr]=(Double.isFinite(a)&&Double.isFinite(b))?Math.abs(b-a)*0.5:0.0;
                }
            }
            for(int r=minR;r<=maxR;r++)if(radialCount[r]>0)radialMean[r]/=radialCount[r];

            double[] persistent=new double[maxR+1];
            double[] tmp=new double[N_ANGLE];
            for(int rr=minR+1;rr<maxR;rr++){
                for(int ai=0;ai<N_ANGLE;ai++)tmp[ai]=grad[ai][rr];
                persistent[rr]=median(tmp);
            }

            int lo=Math.max(minR+2,(int)Math.floor(0.55*seedR));
            int hi=Math.min(maxR-2,(int)Math.ceil(1.15*seedR));
            List<Integer> peaks=new ArrayList<>();
            double maxG=0;
            for(int r=lo;r<hi;r++){
                if(persistent[r]>=persistent[r-1]&&persistent[r]>persistent[r+1]&&persistent[r]>=4.0){
                    peaks.add(r);maxG=Math.max(maxG,persistent[r]);
                }
            }
            if(peaks.isEmpty())return new Result("no persistent annular edges");

            List<Integer> innerCandidates=new ArrayList<>();
            for(int r:peaks){
                int a0=Math.max(minR,(int)Math.floor(r-0.10*seedR));
                int a1=Math.max(a0+1,(int)Math.floor(r-0.03*seedR));
                int b1=Math.min(maxR+1,(int)Math.ceil(r+0.10*seedR));
                double inside=median(radialMean,a0,a1),outside=median(radialMean,r,b1);
                double gain=outside-inside;
                if(persistent[r]>=0.28*maxG&&inside<90.0&&gain>15.0)innerCandidates.add(r);
            }
            if(innerCandidates.isEmpty())return new Result("rehaut inner edge not constrained");
            List<Integer> notTooInner=new ArrayList<>();
            for(int r:innerCandidates)if(r>=0.62*seedR)notTooInner.add(r);
            int inner=minimum(notTooInner.isEmpty()?innerCandidates:notTooInner);

            int outer=-1;
            for(int r:peaks){
                double sep=(r-inner)/Math.max(seedR,1.0);
                if(sep>=0.04&&sep<=0.095&&persistent[r]>=0.22*maxG){outer=r;break;}
            }
            if(outer<0)return new Result("rehaut outer edge not constrained");
            double separation=outer-inner;
            if(separation<=2)return new Result("degenerate rehaut edge pair");
            int win=Math.max(3,(int)Math.round(0.35*separation));

            double[] innerPos=new double[N_ANGLE],outerPos=new double[N_ANGLE];
            double[] innerStrength=new double[N_ANGLE],outerStrength=new double[N_ANGLE];
            for(int ai=0;ai<N_ANGLE;ai++){
                int bi=bestGradient(grad[ai],inner,win,minR,maxR);
                int bo=bestGradient(grad[ai],outer,win,minR,maxR);
                innerPos[ai]=bi;outerPos[ai]=bo;
                innerStrength[ai]=bi>=0?grad[ai][bi]:0;
                outerStrength[ai]=bo>=0?grad[ai][bo]:0;
            }
            double innerCut=Math.max(3.0,percentile(innerStrength,0.35));
            double outerCut=Math.max(3.0,percentile(outerStrength,0.35));

            double[] widths=new double[N_ANGLE];Arrays.fill(widths,Double.NaN);
            double[] validRaw=new double[N_ANGLE];int vn=0;
            for(int ai=0;ai<N_ANGLE;ai++){
                if(innerStrength[ai]<innerCut||outerStrength[ai]<outerCut)continue;
                double w=outerPos[ai]-innerPos[ai];
                if(w<=1||w>separation*2.6)continue;
                widths[ai]=w;validRaw[vn++]=w;
            }
            if(vn<Math.max(24,N_ANGLE/5))return new Result("insufficient rehaut edge coverage");

            double med=median(Arrays.copyOf(validRaw,vn));
            double[] dev=new double[vn];for(int i=0;i<vn;i++)dev[i]=Math.abs(validRaw[i]-med);
            double mad=median(dev),lim=Math.max(2.0,3.0*1.4826*mad);
            int kept=0;
            for(int ai=0;ai<N_ANGLE;ai++)if(Double.isFinite(widths[ai])){
                if(Math.abs(widths[ai]-med)>lim)widths[ai]=Double.NaN; else kept++;
            }
            if(kept<Math.max(20,N_ANGLE/6))return new Result("rehaut profile rejected too many outliers");

            double sum=0,sc=0,ss=0;int n=0;
            for(int ai=0;ai<N_ANGLE;ai++)if(Double.isFinite(widths[ai])){
                double t=Math.toRadians(ai*ANGLE_STEP_DEG),w=widths[ai];
                sum+=w;sc+=w*Math.cos(t);ss+=w*Math.sin(t);n++;
            }
            double mean=sum/n;
            if(!(mean>1))return new Result("mean visible rehaut width too small");
            // Orthogonal first-harmonic coefficients for approximately uniform samples.
            double c=2.0*sc/n,s=2.0*ss/n;
            double amp=Math.hypot(c,s);
            double top=mean+c,bottom=mean-c,right=mean+s,left=mean-s;
            double v=safeAsym(top,bottom),h=safeAsym(right,left);
            double widest=Math.toDegrees(Math.atan2(s,c));if(widest<0)widest+=360.0;
            double minMean=(mean-amp)/mean;
            double[] residuals=new double[n];int ri=0;
            for(int ai=0;ai<N_ANGLE;ai++)if(Double.isFinite(widths[ai])){
                double t=Math.toRadians(ai*ANGLE_STEP_DEG);
                double fit=mean+c*Math.cos(t)+s*Math.sin(t);
                residuals[ri++]=Math.abs(widths[ai]-fit);
            }
            double residual=median(residuals)/mean;
            double coverage=n/(double)N_ANGLE;

            double roll=Math.toRadians(watchRollClockDeg);
            double watchV=v*Math.cos(roll)+h*Math.sin(roll);
            double watchH=-v*Math.sin(roll)+h*Math.cos(roll);
            return new Result(top,bottom,left,right,mean,v,h,watchV,watchH,amp/mean,widest,minMean,coverage,residual,inner,outer);
        }finally{gray.release();}
    }

    private static int bestGradient(double[] g,int seed,int win,int lo,int hi){
        int a=Math.max(lo+1,seed-win),b=Math.min(hi-1,seed+win),best=-1;double bg=-1;
        for(int r=a;r<=b;r++)if(g[r]>bg){bg=g[r];best=r;}return best;
    }

    private static double sample(Mat gray,double x,double y){
        int xi=(int)Math.round(x),yi=(int)Math.round(y);
        if(xi<0||yi<0||xi>=gray.cols()||yi>=gray.rows())return Double.NaN;
        double[]v=gray.get(yi,xi);return v==null||v.length==0?Double.NaN:v[0];
    }

    private static double safeAsym(double a,double b){double d=a+b;return Math.abs(d)<1e-9?Double.NaN:(a-b)/d;}
    private static int minimum(List<Integer>x){int m=Integer.MAX_VALUE;for(int v:x)m=Math.min(m,v);return m;}
    private static double median(double[] x){double[]c=x.clone();Arrays.sort(c);int n=c.length;return n==0?Double.NaN:(n%2==1?c[n/2]:(c[n/2-1]+c[n/2])*0.5);}
    private static double median(double[]x,int from,int to){if(to<=from)return Double.NaN;double[]c=Arrays.copyOfRange(x,from,to);return median(c);}
    private static double percentile(double[]x,double q){double[]c=x.clone();Arrays.sort(c);if(c.length==0)return Double.NaN;int i=(int)Math.floor(Math.max(0,Math.min(1,q))*(c.length-1));return c[i];}
    private GmtRehautPoseAnalyzer(){}
}
