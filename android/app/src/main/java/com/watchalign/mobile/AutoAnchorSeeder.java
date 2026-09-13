package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces a conservative starting suggestion for the four dial-edge anchors.
 * The result is never treated as ground truth: the user can always correct all
 * four points with the normal loupe and nudge controls.
 */
final class AutoAnchorSeeder {
    static final class Result {
        final PointF p12,p3,p6,p9;
        final float confidence;
        final String note;
        Result(PointF a,PointF b,PointF c,PointF d,float conf,String n){p12=a;p3=b;p6=c;p9=d;confidence=conf;note=n;}
    }

    private static final class DialSeed {
        final double x,y,r,quality,rollDeg;
        DialSeed(double x,double y,double r,double q,double roll){this.x=x;this.y=y;this.r=r;this.quality=q;this.rollDeg=roll;}
    }

    private AutoAnchorSeeder() {}

    static Result seed(Bitmap input){
        if(input==null)return null;
        Mat src=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(input,src);
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);
            DialSeed seed=detectSeed(src);
            if(seed==null||seed.r<40)return null;
            RotatedRect ellipse=findDialEllipse(edges,seed);
            if(ellipse==null)return null;

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            Point[] card=ellipseCardinalPoints(ellipse,seed.rollDeg);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double q=Math.max(0,Math.min(1,(seed.quality-0.40)/0.45));
            double c=Math.max(0,1-centerErr/0.20);
            double a=Math.max(0,Math.min(1,(axisRatio-0.68)/0.25));
            float conf=(float)Math.max(0,Math.min(1,0.45*q+0.35*c+0.20*a));
            String level=conf>=0.72f?"strong":conf>=0.45f?"usable":"uncertain";
            return new Result(pf(card[0]),pf(card[1]),pf(card[2]),pf(card[3]),conf,"Auto seed: "+level+". Adjust every anchor that does not sit on the yellow dial edge.");
        }catch(Throwable ignored){
            return null;
        }finally{
            src.release();gray.release();blur.release();edges.release();
        }
    }

    private static DialSeed detectSeed(Mat rgba){
        Mat bgr=new Mat();
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle d=DialAnalysisEngine.detectDial(bgr);if(d==null)return null;
            double roll=0.0;
            try{
                DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,d);double r=set.globalRotation;if(Double.isFinite(r)&&Math.abs(r)<30)roll=r;
            }catch(Throwable ignored){}
            return new DialSeed(d.x,d.y,d.r,d.quality,roll);
        }finally{bgr.release();}
    }

    private static RotatedRect findDialEllipse(Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Mat in=edges.clone();
        try{
            Imgproc.findContours(in,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;
            for(MatOfPoint c:contours){
                if(c.rows()<40)continue;
                MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);
                    double maj=Math.max(e.size.width,e.size.height),min=Math.min(e.size.width,e.size.height);
                    if(maj<1.50*s.r||maj>2.30*s.r||min<1.30*s.r||min>2.25*s.r)continue;
                    double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/s.r;if(dc>0.24)continue;
                    double ratio=min/Math.max(1.0,maj);if(ratio<0.68)continue;
                    double sizeErr=Math.abs(maj/(2*s.r)-1)+Math.abs(min/(2*s.r)-1);
                    double score=2.8*dc+sizeErr+Math.max(0,0.82-ratio)*0.5;
                    if(score<bestScore){bestScore=score;best=e;}
                }catch(Throwable ignored){}finally{f.release();}
            }
            return best;
        }finally{in.release();hierarchy.release();for(MatOfPoint c:contours)c.release();}
    }

    private static Point[] ellipseCardinalPoints(RotatedRect e,double rollDeg){
        return new Point[]{
                rayEllipseIntersection(e,Math.toRadians(rollDeg-90)),
                rayEllipseIntersection(e,Math.toRadians(rollDeg)),
                rayEllipseIntersection(e,Math.toRadians(rollDeg+90)),
                rayEllipseIntersection(e,Math.toRadians(rollDeg+180))};
    }

    private static Point rayEllipseIntersection(RotatedRect e,double angle){
        double rx=Math.max(1e-6,e.size.width/2),ry=Math.max(1e-6,e.size.height/2),t=Math.toRadians(e.angle);
        double dx=Math.cos(angle),dy=Math.sin(angle),lx=dx*Math.cos(t)+dy*Math.sin(t),ly=-dx*Math.sin(t)+dy*Math.cos(t);
        double den=Math.sqrt((lx*lx)/(rx*rx)+(ly*ly)/(ry*ry));double s=den>1e-9?1/den:0;
        return new Point(e.center.x+s*dx,e.center.y+s*dy);
    }

    private static PointF pf(Point p){return new PointF((float)p.x,(float)p.y);}
}
