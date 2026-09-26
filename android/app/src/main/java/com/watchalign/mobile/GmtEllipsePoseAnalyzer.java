package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/** Independent planar perspective cue from a physical annular/dial boundary. */
final class GmtEllipsePoseAnalyzer {
    static final class Result {
        final boolean valid;
        final double axisRatio,tiltDeg,minorAxisClockDeg,centerErrorOverR,majorRadiusRatio;
        final String reason;
        Result(String reason){valid=false;axisRatio=tiltDeg=minorAxisClockDeg=centerErrorOverR=majorRadiusRatio=Double.NaN;this.reason=reason;}
        Result(double ratio,double tilt,double minorClock,double centerErr,double majorRatio){
            valid=true;axisRatio=ratio;tiltDeg=tilt;minorAxisClockDeg=minorClock;centerErrorOverR=centerErr;majorRadiusRatio=majorRatio;reason="";
        }
    }

    static Result analyse(Mat bgr,double cx,double cy,double r){
        if(bgr==null||bgr.empty()||!(r>20))return new Result("invalid dial seed");
        Mat gray=new Mat(),edges=new Mat(),hier=new Mat();List<MatOfPoint> contours=new ArrayList<>();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.Canny(gray,edges,50,150);
            Imgproc.findContours(edges,contours,hier,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            double bestScore=0;RotatedRect best=null;double bestDc=Double.NaN,bestMr=Double.NaN;
            for(MatOfPoint contour:contours){
                double area=Math.abs(Imgproc.contourArea(contour));
                if(area<Math.PI*r*r*0.35||area>Math.PI*r*r*1.35||contour.rows()<30)continue;
                MatOfPoint2f c2=new MatOfPoint2f(contour.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(c2);
                    double dc=Math.hypot(e.center.x-cx,e.center.y-cy)/r;
                    double major=Math.max(e.size.width,e.size.height),minor=Math.min(e.size.width,e.size.height);
                    if(major<=0||minor<=0||dc>0.12)continue;
                    double mr=major/(2*r);if(mr<0.80||mr>1.20)continue;
                    double score=area*(1.0-dc);
                    if(score>bestScore){bestScore=score;best=e;bestDc=dc;bestMr=mr;}
                }catch(Exception ignored){}finally{c2.release();}
            }
            if(best==null)return new Result("stable dial ellipse not found");
            double major=Math.max(best.size.width,best.size.height),minor=Math.min(best.size.width,best.size.height);
            double ratio=Math.max(0.0,Math.min(1.0,minor/major));
            double tilt=Math.toDegrees(Math.acos(ratio));

            Point[] p=new Point[4];best.points(p);
            double d01=dist(p[0],p[1]),d12=dist(p[1],p[2]);
            Point a,b;
            if(d01<=d12){a=p[0];b=p[1];}else{a=p[1];b=p[2];}
            double dx=b.x-a.x,dy=b.y-a.y;
            double clock=Math.toDegrees(Math.atan2(dx,-dy));
            clock%=180.0;if(clock<0)clock+=180.0;
            return new Result(ratio,tilt,clock,bestDc,bestMr);
        }finally{
            for(MatOfPoint c:contours)c.release();hier.release();edges.release();gray.release();
        }
    }

    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private GmtEllipsePoseAnalyzer(){}
}
