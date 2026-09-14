package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
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
 * Produces an automatic starting pose for the four DIAL-EDGE perspective anchors.
 *
 * For a GMT the preferred path does not use the 12 triangle as a registration point. It fits a
 * robust homography from the other detected hour markers to their exact 30-degree mathematical
 * positions, then projects the ideal dial-edge 12/3/6/9 points into the photo. This keeps the
 * 12 triangle independent so it can subsequently be measured as QC evidence rather than used to
 * force its own alignment. A dial-ellipse seed remains as a conservative fallback.
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
            DialSeed seed=detectSeed(src);
            if(seed==null||seed.r<40)return null;

            Result markerFit=markerGeometrySeed(src,seed);
            if(markerFit!=null&&markerFit.confidence>=0.52f)return markerFit;

            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);
            RotatedRect ellipse=findDialEllipse(edges,seed);
            if(ellipse==null)return markerFit;

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
            return new Result(pf(card[0]),pf(card[1]),pf(card[2]),pf(card[3]),conf,
                    "Automatic dial-edge fit: "+level+". The 12/3/6/9 handles belong on the yellow DIAL EDGE, not on the hour markers or bezel. Adjust only if the yellow edge is visibly misplaced.");
        }catch(Throwable ignored){
            return null;
        }finally{
            src.release();gray.release();blur.release();edges.release();
        }
    }

    /**
     * Preferred GMT registration. Uses the detected 1/2/4/5/6/7/8/9/10/11 marker population;
     * 12 is deliberately excluded and 3 is the date aperture. RANSAC prevents one misplaced or
     * poorly detected marker from controlling the perspective transform.
     */
    private static Result markerGeometrySeed(Mat rgba,DialSeed seed){
        Mat bgr=new Mat(),mask=new Mat(),h=null;MatOfPoint2f canonical=null,observed=null,edge=null,projected=null;
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle dial=DialAnalysisEngine.detectDial(bgr);if(dial==null||dial.r<=0)return null;
            DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,dial);
            if(set==null||set.markers==null||set.markers.size()<7||!Double.isFinite(set.globalRotation)||!Double.isFinite(set.medianRadius))return null;

            double ringR=set.medianRadius/Math.max(1.0,dial.r);
            if(!Double.isFinite(ringR)||ringR<0.58||ringR>0.90)return null;
            List<Point> srcPts=new ArrayList<>(),dstPts=new ArrayList<>();
            for(DialAnalysisEngine.Marker m:set.markers){
                int hour=m.hour;if(hour==3||hour==12)continue;
                double ideal=Math.toRadians(hour*30.0);
                srcPts.add(new Point(ringR*Math.sin(ideal),-ringR*Math.cos(ideal)));
                double actual=Math.toRadians(hour*30.0+set.globalRotation+m.angular);
                dstPts.add(new Point(dial.x+Math.sin(actual)*m.radius,dial.y-Math.cos(actual)*m.radius));
            }
            if(srcPts.size()<6)return null;
            canonical=new MatOfPoint2f(srcPts.toArray(new Point[0]));
            observed=new MatOfPoint2f(dstPts.toArray(new Point[0]));
            h=Calib3d.findHomography(canonical,observed,Calib3d.RANSAC,Math.max(2.5,dial.r*0.012),mask,2500,0.995);
            if(h==null||h.empty())return null;

            edge=new MatOfPoint2f(new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0),new Point(0,0));
            projected=new MatOfPoint2f();Core.perspectiveTransform(edge,projected,h);
            Point[] p=projected.toArray();if(p.length<5)return null;
            for(Point x:p)if(!Double.isFinite(x.x)||!Double.isFinite(x.y))return null;

            double area=quadArea(p[0],p[1],p[2],p[3]);if(area<Math.PI*dial.r*dial.r*0.35)return null;
            double centreErr=Math.hypot(p[4].x-dial.x,p[4].y-dial.y)/Math.max(1.0,dial.r);if(centreErr>0.18)return null;
            double meanEdge=(dist(p[4],p[0])+dist(p[4],p[1])+dist(p[4],p[2])+dist(p[4],p[3]))/4.0;
            double scaleErr=Math.abs(meanEdge/dial.r-1.0);if(scaleErr>0.22)return null;

            MatOfPoint2f repro=new MatOfPoint2f();Core.perspectiveTransform(canonical,repro,h);Point[] rp=repro.toArray();repro.release();
            double ss=0;int n=Math.min(rp.length,dstPts.size()),inliers=0;
            for(int i=0;i<n;i++){double d=dist(rp[i],dstPts.get(i));ss+=d*d;double[] mv=mask.empty()?null:mask.get(i,0);if(mv==null||mv.length==0||mv[0]>0)inliers++;}
            double rms=n>0?Math.sqrt(ss/n):99.0,inlierRatio=n>0?inliers/(double)n:0;
            double q=clamp((dial.quality-0.42)/0.40),rmsQ=clamp(1-rms/Math.max(2.0,dial.r*0.035)),centreQ=clamp(1-centreErr/0.16),scaleQ=clamp(1-scaleErr/0.18);
            float conf=(float)clamp(0.34*q+0.30*inlierRatio+0.18*rmsQ+0.10*centreQ+0.08*scaleQ);
            String level=conf>=0.78f?"strong":conf>=0.58f?"good":"usable";
            return new Result(pf(p[0]),pf(p[1]),pf(p[2]),pf(p[3]),conf,
                    "Automatic GMT perspective fit: "+level+" ("+inliers+"/"+n+" non-12 markers agree). 12/3/6/9 are DIAL-EDGE anchors. No adjustment is needed unless the yellow dial edge is visibly off.");
        }catch(Throwable ignored){return null;}
        finally{bgr.release();mask.release();if(h!=null)h.release();if(canonical!=null)canonical.release();if(observed!=null)observed.release();if(edge!=null)edge.release();if(projected!=null)projected.release();}
    }

    private static DialSeed detectSeed(Mat rgba){
        Mat bgr=new Mat();
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle d=DialAnalysisEngine.detectDial(bgr);if(d==null)return null;
            double roll=0.0;
            try{DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,d);double r=set.globalRotation;if(Double.isFinite(r)&&Math.abs(r)<30)roll=r;}catch(Throwable ignored){}
            return new DialSeed(d.x,d.y,d.r,d.quality,roll);
        }finally{bgr.release();}
    }

    private static RotatedRect findDialEllipse(Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Mat in=edges.clone();
        try{
            Imgproc.findContours(in,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;
            for(MatOfPoint c:contours){
                if(c.rows()<40)continue;MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);double maj=Math.max(e.size.width,e.size.height),min=Math.min(e.size.width,e.size.height);
                    if(maj<1.50*s.r||maj>2.30*s.r||min<1.30*s.r||min>2.25*s.r)continue;
                    double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/s.r;if(dc>0.24)continue;
                    double ratio=min/Math.max(1.0,maj);if(ratio<0.68)continue;
                    double sizeErr=Math.abs(maj/(2*s.r)-1)+Math.abs(min/(2*s.r)-1);
                    double score=2.8*dc+sizeErr+Math.max(0,0.82-ratio)*0.5;if(score<bestScore){bestScore=score;best=e;}
                }catch(Throwable ignored){}finally{f.release();}
            }
            return best;
        }finally{in.release();hierarchy.release();for(MatOfPoint c:contours)c.release();}
    }

    private static Point[] ellipseCardinalPoints(RotatedRect e,double rollDeg){return new Point[]{rayEllipseIntersection(e,Math.toRadians(rollDeg-90)),rayEllipseIntersection(e,Math.toRadians(rollDeg)),rayEllipseIntersection(e,Math.toRadians(rollDeg+90)),rayEllipseIntersection(e,Math.toRadians(rollDeg+180))};}
    private static Point rayEllipseIntersection(RotatedRect e,double angle){double rx=Math.max(1e-6,e.size.width/2),ry=Math.max(1e-6,e.size.height/2),t=Math.toRadians(e.angle);double dx=Math.cos(angle),dy=Math.sin(angle),lx=dx*Math.cos(t)+dy*Math.sin(t),ly=-dx*Math.sin(t)+dy*Math.cos(t);double den=Math.sqrt((lx*lx)/(rx*rx)+(ly*ly)/(ry*ry));double s=den>1e-9?1/den:0;return new Point(e.center.x+s*dx,e.center.y+s*dy);}
    private static double quadArea(Point a,Point b,Point c,Point d){Point[]p={a,b,c,d};double s=0;for(int i=0;i<4;i++){Point x=p[i],y=p[(i+1)%4];s+=x.x*y.y-y.x*x.y;}return Math.abs(s)*0.5;}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static PointF pf(Point p){return new PointF((float)p.x,(float)p.y);}
}
