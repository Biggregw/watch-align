package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Solves dial rotation from minor minute-track ticks and validates the final pose
 * on a disjoint set of minor ticks that are not used by the rotation search.
 * Applied hour markers are deliberately excluded from both phases.
 */
final class MinuteTrackPoseValidator {
    private static final double TRACK_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double TICK_RADIAL_HALF = 0.025;
    private static final double TICK_ANGULAR_HALF = Math.toRadians(0.34);
    private static final double LOSS_CAP_PX = 10.0;
    private static final double FINE_ROTATION_LIMIT_DEG = 2.0;

    static final class RotationResult {
        final Mat homography;
        final double deltaDeg;
        final double fitMedianPx;
        final int fitTicks;
        RotationResult(Mat h,double delta,double median,int ticks){
            homography=h;deltaDeg=delta;fitMedianPx=median;fitTicks=ticks;
        }
    }

    static final class ValidationResult {
        final boolean accepted;
        final double medianPx;
        final double p90Px;
        final double inlierFraction;
        final int holdoutTicks;
        final double medianLimitPx;
        final double p90LimitPx;
        final double inlierLimitPx;
        ValidationResult(boolean accepted,double median,double p90,double inlier,int ticks,
                         double medianLimit,double p90Limit,double inlierLimit){
            this.accepted=accepted;medianPx=median;p90Px=p90;inlierFraction=inlier;
            holdoutTicks=ticks;medianLimitPx=medianLimit;p90LimitPx=p90Limit;inlierLimitPx=inlierLimit;
        }
    }

    static RotationResult solveRotation(Mat edges,Mat baseH,double maxAbsDeg){
        Mat distance=distanceField(edges);
        try{
            double bestDeg=0.0;
            double best=rotationScore(distance,baseH,0.0,false);
            for(double d=-maxAbsDeg;d<=maxAbsDeg+1e-9;d+=0.25){
                double score=rotationScore(distance,baseH,d,false);
                if(score<best){best=score;bestDeg=d;}
            }
            double coarse=bestDeg;
            for(double d=coarse-0.35;d<=coarse+0.35+1e-9;d+=0.025){
                if(Math.abs(d)>maxAbsDeg)continue;
                double score=rotationScore(distance,baseH,d,false);
                if(score<best){best=score;bestDeg=d;}
            }
            return new RotationResult(composeRotation(baseH,bestDeg),bestDeg,best,countTicks(false));
        }finally{distance.release();}
    }

    static ValidationResult validate(Mat edges,Mat h,double dialRadiusPx){
        Mat distance=distanceField(edges);
        try{
            List<Double> scores=tickScores(distance,h,true,0.0);
            if(scores.isEmpty())return new ValidationResult(false,LOSS_CAP_PX,LOSS_CAP_PX,0.0,0,0,0,0);
            Collections.sort(scores);
            double median=percentileSorted(scores,0.50);
            double p90=percentileSorted(scores,0.90);
            double radius=Math.max(60.0,dialRadiusPx);
            double medianLimit=Math.max(1.5,radius*0.015);
            double p90Limit=Math.max(3.0,radius*0.032);
            double inlierLimit=Math.max(2.0,radius*0.022);
            int inliers=0;for(double v:scores)if(v<=inlierLimit)inliers++;
            double fraction=inliers/(double)scores.size();
            boolean accepted=scores.size()>=8&&median<=medianLimit&&p90<=p90Limit&&fraction>=0.65;
            return new ValidationResult(accepted,median,p90,fraction,scores.size(),medianLimit,p90Limit,inlierLimit);
        }finally{distance.release();}
    }

    /**
     * Final small correction after projective refinement. The absolute 6 degree branch has
     * already been fixed by the image-up anchor, so this must never search a full tick pitch.
     */
    static RotationResult fineTuneRotation(Mat edges,Mat baseH){
        return solveRotation(edges,baseH,FINE_ROTATION_LIMIT_DEG);
    }

    static double fineRotationLimitDeg(){return FINE_ROTATION_LIMIT_DEG;}

    private static Mat distanceField(Mat edges){
        Mat inverted=new Mat(),distance=new Mat();
        Imgproc.threshold(edges,inverted,0.0,255.0,Imgproc.THRESH_BINARY_INV);
        Imgproc.distanceTransform(inverted,distance,Imgproc.DIST_L2,Imgproc.DIST_MASK_PRECISE);
        inverted.release();
        return distance;
    }

    private static double rotationScore(Mat distance,Mat h,double deltaDeg,boolean holdout){
        List<Double> scores=tickScores(distance,h,holdout,deltaDeg);
        if(scores.isEmpty())return LOSS_CAP_PX;
        Collections.sort(scores);
        return percentileSorted(scores,0.50);
    }

    private static List<Double> tickScores(Mat distance,Mat h,boolean holdout,double deltaDeg){
        List<Double> out=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0)continue;
            boolean thisHoldout=minute%4==2;
            if(thisHoldout!=holdout)continue;
            double angle=Math.toRadians(minute*6.0-90.0+deltaDeg);
            out.add(tickScore(distance,h,angle));
        }
        return out;
    }

    private static int countTicks(boolean holdout){
        int n=0;for(int minute=0;minute<60;minute++)if(minute%5!=0&&(minute%4==2)==holdout)n++;return n;
    }

    private static double tickScore(Mat distance,Mat h,double angle){
        double sum=0.0;int count=0;
        for(double side:new double[]{-TICK_ANGULAR_HALF,TICK_ANGULAR_HALF}){
            for(int i=0;i<4;i++){
                double r=TRACK_R-TICK_RADIAL_HALF+2.0*TICK_RADIAL_HALF*i/3.0;
                sum+=sample(distance,h,r*Math.cos(angle+side),r*Math.sin(angle+side));count++;
            }
        }
        for(double r:new double[]{TRACK_R-TICK_RADIAL_HALF,TRACK_R+TICK_RADIAL_HALF}){
            for(int i=-1;i<=1;i++){
                double a=angle+i*TICK_ANGULAR_HALF;
                sum+=sample(distance,h,r*Math.cos(a),r*Math.sin(a));count++;
            }
        }
        return sum/Math.max(1,count);
    }

    private static double sample(Mat distance,Mat h,double x,double y){
        double[] m=new double[9];h.get(0,0,m);
        double w=m[6]*x+m[7]*y+m[8];if(Math.abs(w)<1e-8)return LOSS_CAP_PX;
        double px=(m[0]*x+m[1]*y+m[2])/w,py=(m[3]*x+m[4]*y+m[5])/w;
        if(px<1||py<1||px>=distance.cols()-1||py>=distance.rows()-1)return LOSS_CAP_PX;
        int x0=(int)Math.floor(px),y0=(int)Math.floor(py);double fx=px-x0,fy=py-y0;
        double d00=distance.get(y0,x0)[0],d10=distance.get(y0,x0+1)[0];
        double d01=distance.get(y0+1,x0)[0],d11=distance.get(y0+1,x0+1)[0];
        double d=(d00*(1.0-fx)+d10*fx)*(1.0-fy)+(d01*(1.0-fx)+d11*fx)*fy;
        return Math.min(LOSS_CAP_PX,Math.max(0.0,d));
    }

    private static Mat composeRotation(Mat h,double degrees){
        double a=Math.toRadians(degrees),c=Math.cos(a),s=Math.sin(a);
        Mat r=Mat.eye(3,3,CvType.CV_64F);r.put(0,0,c);r.put(0,1,-s);r.put(1,0,s);r.put(1,1,c);
        Mat out=new Mat(),zero=new Mat();Core.gemm(h,r,1.0,zero,0.0,out);r.release();zero.release();
        double scale=out.get(2,2)[0];if(Math.abs(scale)>1e-9)Core.multiply(out,new org.opencv.core.Scalar(1.0/scale),out);
        return out;
    }

    private static double percentileSorted(List<Double> sorted,double q){
        if(sorted.isEmpty())return LOSS_CAP_PX;
        double pos=q*(sorted.size()-1);int lo=(int)Math.floor(pos),hi=(int)Math.ceil(pos);
        if(lo==hi)return sorted.get(lo);double f=pos-lo;return sorted.get(lo)*(1.0-f)+sorted.get(hi)*f;
    }

    private MinuteTrackPoseValidator(){}
}
