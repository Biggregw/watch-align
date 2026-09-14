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
 * QC coordinate system belongs on the smaller / inner member of that pair. Candidate pairs are not
 * accepted independently per direction: opposite sides must agree on both the normalized inner
 * radius and the rehaut-pair separation. This prevents a local reflection/minute-track edge at 6 or
 * 9 o'clock from being mistaken for the physical dial boundary.
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
    private static final class PairCandidate {
        final Peak inner,outer;final double predicted,innerRatio,sepRatio,score;
        PairCandidate(Peak i,Peak o,double p,double s){inner=i;outer=o;predicted=p;innerRatio=i.r/p;sepRatio=(o.r-i.r)/p;score=s;}
    }
    private GmtInnerDialEdgeDetector(){}

    static Refinement refineCardinals(Mat image, Point centre, Point[] predicted){
        if(image==null||image.empty()||centre==null||predicted==null||predicted.length<4)return null;
        Mat gray=new Mat();
        try{
            toGray(image,gray);
            @SuppressWarnings("unchecked") List<PairCandidate>[] options=new List[4];
            double[] ux=new double[4],uy=new double[4],pr=new double[4];
            for(int i=0;i<4;i++){
                Point q=predicted[i];double vx=q.x-centre.x,vy=q.y-centre.y;pr[i]=Math.hypot(vx,vy);
                if(pr[i]<20){options[i]=new ArrayList<>();continue;}
                ux[i]=vx/pr[i];uy[i]=vy/pr[i];double tx=-uy[i],ty=ux[i];
                List<Peak> peaks=rayPeaks(gray,centre,ux[i],uy[i],tx,ty,pr[i]*.86,pr[i]*1.12,Math.max(1.0,pr[i]/220.0));
                options[i]=pairCandidates(peaks,pr[i]);
            }

            PairCandidate[] chosen=new PairCandidate[4];
            chooseOpposite(options,chosen,0,2); // 12 versus 6
            chooseOpposite(options,chosen,1,3); // 3 versus 9

            Point[] out=new Point[4];int pairs=0;double quality=0;
            for(int i=0;i<4;i++){
                PairCandidate pc=chosen[i];
                if(pc!=null){
                    out[i]=new Point(centre.x+ux[i]*pc.inner.r,centre.y+uy[i]*pc.inner.r);pairs++;
                    quality+=pc.score;
                }else{
                    // Never silently claim a verified direction from a single ambiguous edge. Keep
                    // the marker-homography prediction and let confidence/auto-lock fall instead.
                    out[i]=predicted[i];
                }
            }
            double pairFrac=pairs/4.0,meanQ=pairs>0?quality/pairs:0;
            double score=clamp(.70*pairFrac+.30*meanQ);
            return new Refinement(out,pairs,score);
        }finally{gray.release();}
    }

    /** Select an opposite pair only when both sides describe the same physical concentric pair. */
    private static void chooseOpposite(List<PairCandidate>[] options,PairCandidate[] chosen,int a,int b){
        if(options[a]==null||options[b]==null||options[a].isEmpty()||options[b].isEmpty())return;
        PairCandidate bestA=null,bestB=null;double best=-1;
        int na=Math.min(8,options[a].size()),nb=Math.min(8,options[b].size());
        for(int i=0;i<na;i++)for(int j=0;j<nb;j++){
            PairCandidate x=options[a].get(i),y=options[b].get(j);
            double radiusAgreement=clamp(1-Math.abs(x.innerRatio-y.innerRatio)/.035);
            double sepAgreement=clamp(1-Math.abs(x.sepRatio-y.sepRatio)/.025);
            double prediction=0.5*(clamp(1-Math.abs(x.innerRatio-1.0)/.065)+clamp(1-Math.abs(y.innerRatio-1.0)/.065));
            double individual=(x.score+y.score)*.5;
            double score=.43*individual+.27*radiusAgreement+.15*sepAgreement+.15*prediction;
            if(score>best){best=score;bestA=x;bestB=y;}
        }
        if(best>=.48){chosen[a]=bestA;chosen[b]=bestB;}
    }

    private static List<PairCandidate> pairCandidates(List<Peak> peaks,double predicted){
        List<PairCandidate> out=new ArrayList<>();int limit=Math.min(12,peaks.size());
        for(int i=0;i<limit;i++)for(int j=i+1;j<limit;j++){
            Peak a=peaks.get(i),b=peaks.get(j),inner=a.r<b.r?a:b,outer=inner==a?b:a;
            double sep=(outer.r-inner.r)/predicted;
            // The rehaut pair is close. Wider pairs are usually the minute-track / rehaut or bezel.
            if(sep<.014||sep>.085)continue;
            double innerRatio=inner.r/predicted;
            if(innerRatio<.91||innerRatio>1.07)continue;
            double strength=clamp(Math.min(inner.strength,outer.strength)/24.0);
            double innerClose=clamp(1-Math.abs(innerRatio-1.0)/.070);
            double sepShape=clamp(1-Math.abs(sep-.042)/.045);
            double score=.48*strength+.38*innerClose+.14*sepShape;
            out.add(new PairCandidate(inner,outer,predicted,score));
        }
        Collections.sort(out,Comparator.comparingDouble((PairCandidate p)->p.score).reversed());
        return out;
    }

    static Verification verifyCanonicalPair(Mat image,double cx,double cy,double radius){
        if(image==null||image.empty()||radius<=0)return new Verification(false,0,Double.NaN,Double.NaN);
        Mat gray=new Mat();
        try{
            toGray(image,gray);
            List<Peak> profile=new ArrayList<>();
            for(double f=.88;f<=1.12;f+=.0025){double s=meanRadialGradient(gray,cx,cy,radius*f,Math.max(1.5,radius*.0045));profile.add(new Peak(f,s));}
            List<Peak> peaks=localMaxima(profile);if(peaks.isEmpty())return new Verification(false,0,Double.NaN,Double.NaN);
            Peak anchor=bestIn(peaks,.975,1.025),outside=bestIn(peaks,1.018,1.105),inside=bestIn(peaks,.895,.982);
            if(anchor==null)return new Verification(false,0,Double.NaN,Double.NaN);
            double global=0;for(Peak p:peaks)global=Math.max(global,p.strength);if(global<=1e-6)return new Verification(false,0,anchor.r,Double.NaN);
            double anchorQ=clamp(anchor.strength/global),outerQ=outside==null?0:clamp(outside.strength/global),insideQ=inside==null?0:clamp(inside.strength/global);
            double anchorPos=clamp(1-Math.abs(anchor.r-1.0)/.025),sepQ=outside==null?0:clamp((outside.r-anchor.r-.010)/.035);
            double orientation=outside==null?0:clamp(.55+(outerQ-insideQ)*1.35);
            double score=clamp(.30*anchorQ+.25*outerQ+.20*anchorPos+.15*sepQ+.10*orientation);
            boolean observed=outside!=null&&anchor.strength>=6.0&&outside.strength>=5.0;
            if(!observed)score=Math.min(score,.35);if(insideQ>outerQ+.12)score=Math.min(score,.20);
            return new Verification(observed,score,anchor.r,outside==null?Double.NaN:outside.r);
        }finally{gray.release();}
    }

    private static void toGray(Mat image,Mat gray){if(image.channels()==1)image.copyTo(gray);else if(image.channels()==4)Imgproc.cvtColor(image,gray,Imgproc.COLOR_RGBA2GRAY);else Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);}
    private static List<Peak> rayPeaks(Mat gray,Point c,double ux,double uy,double tx,double ty,double lo,double hi,double step){List<Peak> samples=new ArrayList<>();for(double r=lo;r<=hi;r+=step)samples.add(new Peak(r,rayGradient(gray,c,ux,uy,tx,ty,r,Math.max(1.5,step*1.5))));return localMaxima(samples);}
    private static double rayGradient(Mat g,Point c,double ux,double uy,double tx,double ty,double r,double dr){double sum=0,n=0;for(double t=-4;t<=4;t+=2){double a=sample(g,c.x+ux*(r-dr)+tx*t,c.y+uy*(r-dr)+ty*t),b=sample(g,c.x+ux*(r+dr)+tx*t,c.y+uy*(r+dr)+ty*t);if(Double.isFinite(a)&&Double.isFinite(b)){sum+=Math.abs(b-a);n++;}}return n>0?sum/n:0;}
    private static double meanRadialGradient(Mat g,double cx,double cy,double r,double dr){double sum=0,n=0;for(int deg=0;deg<360;deg+=4){if(deg>=340||deg<=20)continue;double a=Math.toRadians(deg),ca=Math.cos(a),sa=Math.sin(a);double x1=cx+ca*(r-dr),y1=cy+sa*(r-dr),x2=cx+ca*(r+dr),y2=cy+sa*(r+dr);double v1=sample(g,x1,y1),v2=sample(g,x2,y2);if(Double.isFinite(v1)&&Double.isFinite(v2)){sum+=Math.abs(v2-v1);n++;}}return n>0?sum/n:0;}
    private static List<Peak> localMaxima(List<Peak> samples){List<Peak> out=new ArrayList<>();for(int i=1;i+1<samples.size();i++){Peak a=samples.get(i-1),b=samples.get(i),c=samples.get(i+1);if(b.strength>=a.strength&&b.strength>=c.strength)out.add(new Peak(b.r,b.strength));}Collections.sort(out,Comparator.comparingDouble((Peak p)->p.strength).reversed());return out;}
    private static Peak bestIn(List<Peak> peaks,double lo,double hi){Peak best=null;for(Peak p:peaks)if(p.r>=lo&&p.r<=hi&&(best==null||p.strength>best.strength))best=p;return best;}
    private static double sample(Mat g,double x,double y){int xi=(int)Math.round(x),yi=(int)Math.round(y);if(xi<0||yi<0||xi>=g.cols()||yi>=g.rows())return Double.NaN;double[]v=g.get(yi,xi);return v==null||v.length==0?Double.NaN:v[0];}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
