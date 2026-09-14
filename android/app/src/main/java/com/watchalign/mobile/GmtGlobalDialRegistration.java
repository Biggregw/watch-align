package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Global GMT registration from one coherent physical inner-rehaut boundary plus the 60-minute grid.
 *
 * The marker-based homography is used only as a coarse initializer. We then sample the complete
 * circumference, identify the smaller member of the nearby rehaut pair in many independent sectors,
 * reject sectors that do not agree with the global physical pair, and refit ONE homography from all
 * surviving inner-boundary observations. Finally the 60-position minute track supplies the absolute
 * angular phase. Applied hour markers are not used in this final angular refinement, so their QC
 * errors are not deliberately corrected away.
 */
final class GmtGlobalDialRegistration {
    static final class Result {
        final PointF p12,p3,p6,p9;
        final float confidence;
        final boolean boundaryVerified;
        final int sectorsUsed;
        final int quadrantsCovered;
        final double normalizedRms;
        final double minutePhaseDeg;
        final double minuteSupport;
        final String note;
        Result(PointF a,PointF b,PointF c,PointF d,float conf,boolean verified,int sectors,int quadrants,
               double rms,double phase,double support,String n){
            p12=a;p3=b;p6=c;p9=d;confidence=conf;boundaryVerified=verified;sectorsUsed=sectors;
            quadrantsCovered=quadrants;normalizedRms=rms;minutePhaseDeg=phase;minuteSupport=support;note=n;
        }
    }

    private static final int SECTORS=72; // 5 degree sectors; dense enough for robust circumference consensus.
    private static final class Peak {final double r,strength;Peak(double r,double s){this.r=r;strength=s;}}
    private static final class Observation {
        final int sector;final double angleDeg,predictedR,innerR,outerR,innerRatio,sepRatio,strength;
        final Point canonical,observed;
        Observation(int s,double a,double pr,double ir,double or,double st,Point c,Point o){
            sector=s;angleDeg=a;predictedR=pr;innerR=ir;outerR=or;innerRatio=ir/pr;sepRatio=(or-ir)/pr;
            strength=st;canonical=c;observed=o;
        }
    }

    private GmtGlobalDialRegistration(){}

    static Result refine(Bitmap source, Mat image, Mat initialH){
        if(source==null||image==null||image.empty()||initialH==null||initialH.empty())return null;
        Mat gray=new Mat(),mask=new Mat(),refinedH=null;MatOfPoint2f canonical=null,observed=null;
        try{
            toGray(image,gray);
            Point centre=project(initialH,0,0);if(centre==null)return null;
            List<Observation> raw=new ArrayList<>();
            for(int i=0;i<SECTORS;i++){
                double deg=i*(360.0/SECTORS);
                // Cyclops/date can obscure the 3 o'clock boundary. Skip a modest sector around it.
                if(deg>=78&&deg<=102)continue;
                double a=Math.toRadians(deg),x=Math.sin(a),y=-Math.cos(a);
                Point predicted=project(initialH,x,y);if(predicted==null)continue;
                Observation q=observePair(gray,centre,predicted,i,deg,new Point(x,y));
                if(q!=null)raw.add(q);
            }
            if(raw.size()<24)return null;

            double medR=median(raw,false),madR=mad(raw,medR,false);
            double medS=median(raw,true),madS=mad(raw,medS,true);
            List<Observation> keep=new ArrayList<>();boolean[] quadrants=new boolean[4];
            double rTol=Math.max(.020,3.0*madR),sTol=Math.max(.014,3.0*madS);
            for(Observation q:raw){
                if(Math.abs(q.innerRatio-medR)>rTol||Math.abs(q.sepRatio-medS)>sTol)continue;
                if(q.innerRatio<.91||q.innerRatio>1.07||q.sepRatio<.014||q.sepRatio>.080)continue;
                keep.add(q);quadrants[((int)Math.floor((q.angleDeg+45.0)/90.0))&3]=true;
            }
            int quad=0;for(boolean b:quadrants)if(b)quad++;
            if(keep.size()<22||quad<3)return null;

            Point[] cp=new Point[keep.size()],op=new Point[keep.size()];for(int i=0;i<keep.size();i++){cp[i]=keep.get(i).canonical;op[i]=keep.get(i).observed;}
            canonical=new MatOfPoint2f(cp);observed=new MatOfPoint2f(op);
            double meanPred=0;for(Observation q:keep)meanPred+=q.predictedR;meanPred/=keep.size();
            refinedH=Calib3d.findHomography(canonical,observed,Calib3d.RANSAC,Math.max(1.8,meanPred*.008),mask,3500,.997);
            if(refinedH==null||refinedH.empty())return null;

            MatOfPoint2f repro=new MatOfPoint2f();Core.perspectiveTransform(canonical,repro,refinedH);Point[] rp=repro.toArray();repro.release();
            double ss=0;int inliers=0,n=Math.min(rp.length,keep.size());
            for(int i=0;i<n;i++){
                double d=Math.hypot(rp[i].x-keep.get(i).observed.x,rp[i].y-keep.get(i).observed.y);ss+=d*d;
                double[] mv=mask.empty()?null:mask.get(i,0);if(mv==null||mv.length==0||mv[0]>0)inliers++;
            }
            if(n<1)return null;double rms=Math.sqrt(ss/n)/Math.max(1.0,meanPred),inlierRatio=inliers/(double)n;

            // First build a boundary-only pose, then use the independent 60-minute periodic grid to
            // estimate absolute phase. Rotating canonical cardinal points does not alter the fitted
            // conic; it only fixes which position on that conic is 12/3/6/9.
            PerspectiveMasterRenderer.Pose boundaryPose=poseFromH(refinedH,0);if(boundaryPose==null)return null;
            double phase=0,support=0;boolean minuteOk=false;
            try(CanonicalGmtDial dial=CanonicalGmtDial.create(source,boundaryPose)){
                if(dial!=null){GlobalMinuteTrackPhase.Result mt=GlobalMinuteTrackPhase.estimate(dial);if(mt.available&&mt.support>=1.0){phase=mt.phaseDeg;support=mt.support;minuteOk=true;}}
            }catch(Throwable ignored){}
            PerspectiveMasterRenderer.Pose finalPose=poseFromH(refinedH,minuteOk?phase:0);if(finalPose==null)return null;

            double coverage=keep.size()/(double)(SECTORS-5),coverageQ=clamp((coverage-.34)/.46);
            double inlierQ=clamp((inlierRatio-.55)/.40),rmsQ=clamp(1-rms/.025),quadQ=quad/4.0;
            double minuteQ=minuteOk?clamp((support-.8)/1.8):0;
            float conf=(float)clamp(.30*coverageQ+.25*inlierQ+.20*rmsQ+.12*quadQ+.13*minuteQ);
            boolean verified=keep.size()>=30&&quad==4&&inlierRatio>=.70&&rms<=.022;
            if(!verified)conf=Math.min(conf,.74f);
            if(!minuteOk)conf=Math.min(conf,.72f);
            String note=String.format(Locale.US,
                    "Global inner-boundary fit: %d/%d coherent sectors, %d/4 quadrants, %.1f%% RANSAC inliers, %.3f DR RMS. %s",
                    keep.size(),SECTORS-5,quad,100*inlierRatio,rms,
                    minuteOk?String.format(Locale.US,"60-minute grid phase %+.2f° (support %.2f).",phase,support):"60-minute grid phase not strong enough; confidence capped.");
            return new Result(new PointF(finalPose.anchor12X,finalPose.anchor12Y),new PointF(finalPose.anchor3X,finalPose.anchor3Y),
                    new PointF(finalPose.anchor6X,finalPose.anchor6Y),new PointF(finalPose.anchor9X,finalPose.anchor9Y),
                    conf,verified,keep.size(),quad,rms,phase,support,note);
        }catch(Throwable ignored){return null;}
        finally{gray.release();mask.release();if(refinedH!=null)refinedH.release();if(canonical!=null)canonical.release();if(observed!=null)observed.release();}
    }

    private static Observation observePair(Mat gray,Point centre,Point predicted,int sector,double deg,Point canonical){
        double vx=predicted.x-centre.x,vy=predicted.y-centre.y,pr=Math.hypot(vx,vy);if(pr<30)return null;
        double ux=vx/pr,uy=vy/pr,tx=-uy,ty=ux,step=Math.max(.8,pr/300.0);
        List<Peak> samples=new ArrayList<>();
        for(double r=pr*.90;r<=pr*1.10;r+=step)samples.add(new Peak(r,rayGradient(gray,centre,ux,uy,tx,ty,r,Math.max(1.2,step*1.4))));
        List<Peak> peaks=localMaxima(samples);if(peaks.size()<2)return null;
        Observation best=null;double bestScore=-1;int limit=Math.min(10,peaks.size());
        for(int i=0;i<limit;i++)for(int j=i+1;j<limit;j++){
            Peak a=peaks.get(i),b=peaks.get(j),inner=a.r<b.r?a:b,outer=inner==a?b:a;
            double ir=inner.r/pr,sep=(outer.r-inner.r)/pr;if(ir<.91||ir>1.07||sep<.014||sep>.080)continue;
            double strength=Math.min(inner.strength,outer.strength),strengthQ=clamp(strength/26.0);
            double innerQ=clamp(1-Math.abs(ir-1.0)/.070),sepQ=clamp(1-Math.abs(sep-.042)/.040);
            double score=.52*strengthQ+.34*innerQ+.14*sepQ;
            if(score>bestScore){Point obs=new Point(centre.x+ux*inner.r,centre.y+uy*inner.r);best=new Observation(sector,deg,pr,inner.r,outer.r,strength,canonical,obs);bestScore=score;}
        }
        return best;
    }

    private static PerspectiveMasterRenderer.Pose poseFromH(Mat h,double phaseDeg){
        double[] deg={phaseDeg,90+phaseDeg,180+phaseDeg,270+phaseDeg};Point[] p=new Point[4];
        for(int i=0;i<4;i++){double a=Math.toRadians(deg[i]);p[i]=project(h,Math.sin(a),-Math.cos(a));if(p[i]==null)return null;}
        PerspectiveMasterRenderer.Pose q=new PerspectiveMasterRenderer.Pose();q.anchorMode=true;q.perspectiveMode=true;
        q.anchor12X=(float)p[0].x;q.anchor12Y=(float)p[0].y;q.anchor3X=(float)p[1].x;q.anchor3Y=(float)p[1].y;
        q.anchor6X=(float)p[2].x;q.anchor6Y=(float)p[2].y;q.anchor9X=(float)p[3].x;q.anchor9Y=(float)p[3].y;return q;
    }

    private static Point project(Mat h,double x,double y){
        if(h==null||h.empty())return null;double h00=h.get(0,0)[0],h01=h.get(0,1)[0],h02=h.get(0,2)[0],h10=h.get(1,0)[0],h11=h.get(1,1)[0],h12=h.get(1,2)[0],h20=h.get(2,0)[0],h21=h.get(2,1)[0],h22=h.get(2,2)[0];double d=h20*x+h21*y+h22;if(Math.abs(d)<1e-10)return null;double u=(h00*x+h01*y+h02)/d,v=(h10*x+h11*y+h12)/d;return Double.isFinite(u)&&Double.isFinite(v)?new Point(u,v):null;
    }
    private static double median(List<Observation> q,boolean sep){List<Double>x=new ArrayList<>();for(Observation o:q)x.add(sep?o.sepRatio:o.innerRatio);Collections.sort(x);return x.get(x.size()/2);}
    private static double mad(List<Observation> q,double med,boolean sep){List<Double>x=new ArrayList<>();for(Observation o:q)x.add(Math.abs((sep?o.sepRatio:o.innerRatio)-med));Collections.sort(x);return x.get(x.size()/2);}
    private static List<Peak> localMaxima(List<Peak> samples){List<Peak> out=new ArrayList<>();for(int i=1;i+1<samples.size();i++){Peak a=samples.get(i-1),b=samples.get(i),c=samples.get(i+1);if(b.strength>=a.strength&&b.strength>=c.strength)out.add(b);}Collections.sort(out,Comparator.comparingDouble((Peak p)->p.strength).reversed());return out;}
    private static double rayGradient(Mat g,Point c,double ux,double uy,double tx,double ty,double r,double dr){double sum=0,n=0;for(double t=-4;t<=4;t+=2){double a=sample(g,c.x+ux*(r-dr)+tx*t,c.y+uy*(r-dr)+ty*t),b=sample(g,c.x+ux*(r+dr)+tx*t,c.y+uy*(r+dr)+ty*t);if(Double.isFinite(a)&&Double.isFinite(b)){sum+=Math.abs(b-a);n++;}}return n>0?sum/n:0;}
    private static double sample(Mat g,double x,double y){int xi=(int)Math.round(x),yi=(int)Math.round(y);if(xi<0||yi<0||xi>=g.cols()||yi>=g.rows())return Double.NaN;double[]v=g.get(yi,xi);return v==null||v.length==0?Double.NaN:v[0];}
    private static void toGray(Mat image,Mat gray){if(image.channels()==1)image.copyTo(gray);else if(image.channels()==4)Imgproc.cvtColor(image,gray,Imgproc.COLOR_RGBA2GRAY);else Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
