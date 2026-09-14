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
 * Produces an automatic starting pose for the four INNER DIAL-EDGE perspective anchors.
 *
 * Non-12 marker geometry supplies orientation and perspective. The projected edge is then refined
 * independently on each cardinal ray and the smaller member of the nearby rehaut-edge pair is used.
 * 12 is never used to register itself.
 */
final class AutoAnchorSeeder {
    static final class Result {
        final PointF p12,p3,p6,p9;
        final float confidence;
        final boolean boundaryVerified;
        final String note;
        Result(PointF a,PointF b,PointF c,PointF d,float conf,boolean verified,String n){p12=a;p3=b;p6=c;p9=d;confidence=conf;boundaryVerified=verified;note=n;}
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

            // Conservative fallback. The ellipse only proposes directions; the physical scale is
            // still refined to the smaller member of the rehaut pair on each ray.
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);
            RotatedRect ellipse=findDialEllipse(edges,seed);
            if(ellipse==null)return markerFit;

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            Point[] card=ellipseCardinalPoints(ellipse,seed.rollDeg);
            GmtInnerDialEdgeDetector.Refinement refined=GmtInnerDialEdgeDetector.refineCardinals(src,ellipse.center,card);
            Point[] chosen=refined!=null?refined.cardinals:card;
            int paired=refined!=null?refined.pairedDirections:0;
            double pairScore=refined!=null?refined.score:0;
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double q=clamp((seed.quality-0.40)/0.45),c=clamp(1-centerErr/0.20),a=clamp((axisRatio-0.68)/0.25);
            float conf=(float)clamp(0.30*q+0.22*c+0.13*a+0.35*pairScore);
            if(paired<3)conf=Math.min(conf,.69f);
            boolean verified=paired>=3;
            String level=conf>=0.78f&&verified?"strong":conf>=0.45f?"usable":"uncertain";
            return new Result(pf(chosen[0]),pf(chosen[1]),pf(chosen[2]),pf(chosen[3]),conf,verified,
                    "Automatic inner dial-edge fit: "+level+". Inner/outer rehaut pair verified on "+paired+"/4 directions; the SMALLER edge is used. Review the yellow edge before accepting.");
        }catch(Throwable ignored){return null;}
        finally{src.release();gray.release();blur.release();edges.release();}
    }

    /**
     * Preferred GMT registration. Canonical marker radii come from the 126710 image calibration,
     * not from the detected circular edge. This prevents a mistakenly chosen outer rehaut boundary
     * from defining the scale of the overlay.
     */
    private static Result markerGeometrySeed(Mat rgba,DialSeed seed){
        Mat bgr=new Mat(),mask=new Mat(),h=null;MatOfPoint2f canonical=null,observed=null,edge=null,projected=null;
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle dial=DialAnalysisEngine.detectDial(bgr);if(dial==null||dial.r<=0)return null;
            DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,dial);
            if(set==null||set.markers==null||set.markers.size()<7||!Double.isFinite(set.globalRotation)||!Double.isFinite(set.medianRadius))return null;

            List<Point> srcPts=new ArrayList<>(),dstPts=new ArrayList<>();
            for(DialAnalysisEngine.Marker m:set.markers){
                int hour=m.hour;if(hour==3||hour==12)continue;
                double ideal=Math.toRadians(hour*30.0),referenceR=Gmt126710IdealOverlay.markerCenterRadius(hour);
                srcPts.add(new Point(referenceR*Math.sin(ideal),-referenceR*Math.cos(ideal)));
                double actual=Math.toRadians(hour*30.0+set.globalRotation+m.angular);
                dstPts.add(new Point(dial.x+Math.sin(actual)*m.radius,dial.y-Math.cos(actual)*m.radius));
            }
            if(srcPts.size()<6)return null;
            canonical=new MatOfPoint2f(srcPts.toArray(new Point[0]));observed=new MatOfPoint2f(dstPts.toArray(new Point[0]));
            h=Calib3d.findHomography(canonical,observed,Calib3d.RANSAC,Math.max(2.5,set.medianRadius*0.016),mask,2500,0.995);
            if(h==null||h.empty())return null;

            edge=new MatOfPoint2f(new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0),new Point(0,0));
            projected=new MatOfPoint2f();Core.perspectiveTransform(edge,projected,h);
            Point[] p=projected.toArray();if(p.length<5)return null;for(Point x:p)if(!Double.isFinite(x.x)||!Double.isFinite(x.y))return null;

            double meanEdge=(dist(p[4],p[0])+dist(p[4],p[1])+dist(p[4],p[2])+dist(p[4],p[3]))/4.0;
            double edgeToMarker=meanEdge/Math.max(1.0,set.medianRadius);if(edgeToMarker<1.14||edgeToMarker>1.48)return null;
            double centreErr=Math.hypot(p[4].x-dial.x,p[4].y-dial.y)/Math.max(1.0,set.medianRadius);if(centreErr>0.24)return null;

            MatOfPoint2f repro=new MatOfPoint2f();Core.perspectiveTransform(canonical,repro,h);Point[] rp=repro.toArray();repro.release();
            double ss=0;int n=Math.min(rp.length,dstPts.size()),inliers=0;
            for(int i=0;i<n;i++){double d=dist(rp[i],dstPts.get(i));ss+=d*d;double[] mv=mask.empty()?null:mask.get(i,0);if(mv==null||mv.length==0||mv[0]>0)inliers++;}
            double rms=n>0?Math.sqrt(ss/n):99.0,inlierRatio=n>0?inliers/(double)n:0;

            Point[] predicted={p[0],p[1],p[2],p[3]};
            GmtInnerDialEdgeDetector.Refinement refined=GmtInnerDialEdgeDetector.refineCardinals(bgr,p[4],predicted);
            if(refined==null)return null;
            Point[] chosen=refined.cardinals;int paired=refined.pairedDirections;
            double q=clamp((dial.quality-0.42)/0.40),rmsQ=clamp(1-rms/Math.max(2.0,set.medianRadius*0.045)),centreQ=clamp(1-centreErr/0.20);
            float conf=(float)clamp(0.22*q+0.25*inlierRatio+0.16*rmsQ+0.10*centreQ+0.27*refined.score);
            if(paired<3)conf=Math.min(conf,.69f);
            boolean verified=paired>=3;
            String level=conf>=0.78f&&verified?"strong":conf>=0.58f?"good":"usable";
            return new Result(pf(chosen[0]),pf(chosen[1]),pf(chosen[2]),pf(chosen[3]),conf,verified,
                    "Automatic GMT perspective fit: "+level+" ("+inliers+"/"+n+" registration sectors support the fit; rehaut pair verified on "+paired+"/4 directions). The SMALLER edge is the inner dial edge.");
        }catch(Throwable ignored){return null;}
        finally{bgr.release();mask.release();if(h!=null)h.release();if(canonical!=null)canonical.release();if(observed!=null)observed.release();if(edge!=null)edge.release();if(projected!=null)projected.release();}
    }

    private static DialSeed detectSeed(Mat rgba){
        Mat bgr=new Mat();
        try{Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);DialAnalysisEngine.Circle d=DialAnalysisEngine.detectDial(bgr);if(d==null)return null;double roll=0.0;try{DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,d);double r=set.globalRotation;if(Double.isFinite(r)&&Math.abs(r)<30)roll=r;}catch(Throwable ignored){}return new DialSeed(d.x,d.y,d.r,d.quality,roll);}finally{bgr.release();}
    }

    private static RotatedRect findDialEllipse(Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Mat in=edges.clone();
        try{Imgproc.findContours(in,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;for(MatOfPoint c:contours){if(c.rows()<40)continue;MatOfPoint2f f=new MatOfPoint2f(c.toArray());try{RotatedRect e=Imgproc.fitEllipse(f);double maj=Math.max(e.size.width,e.size.height),min=Math.min(e.size.width,e.size.height);if(maj<1.50*s.r||maj>2.30*s.r||min<1.30*s.r||min>2.25*s.r)continue;double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/s.r;if(dc>0.24)continue;double ratio=min/Math.max(1.0,maj);if(ratio<0.68)continue;double sizeErr=Math.abs(maj/(2*s.r)-1)+Math.abs(min/(2*s.r)-1);double score=2.8*dc+sizeErr+Math.max(0,0.82-ratio)*0.5;if(score<bestScore){bestScore=score;best=e;}}catch(Throwable ignored){}finally{f.release();}}return best;}finally{in.release();hierarchy.release();for(MatOfPoint c:contours)c.release();}
    }

    private static Point[] ellipseCardinalPoints(RotatedRect e,double rollDeg){return new Point[]{rayEllipseIntersection(e,Math.toRadians(rollDeg-90)),rayEllipseIntersection(e,Math.toRadians(rollDeg)),rayEllipseIntersection(e,Math.toRadians(rollDeg+90)),rayEllipseIntersection(e,Math.toRadians(rollDeg+180))};}
    private static Point rayEllipseIntersection(RotatedRect e,double angle){double rx=Math.max(1e-6,e.size.width/2),ry=Math.max(1e-6,e.size.height/2),t=Math.toRadians(e.angle);double dx=Math.cos(angle),dy=Math.sin(angle),lx=dx*Math.cos(t)+dy*Math.sin(t),ly=-dx*Math.sin(t)+dy*Math.cos(t);double den=Math.sqrt((lx*lx)/(rx*rx)+(ly*ly)/(ry*ry));double s=den>1e-9?1/den:0;return new Point(e.center.x+s*dx,e.center.y+s*dy);}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static PointF pf(Point p){return new PointF((float)p.x,(float)p.y);}
}
