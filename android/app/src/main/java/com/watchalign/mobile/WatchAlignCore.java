package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WatchAlignCore {
    public static final String CORE_VERSION = "1.3.0-alpha1";

    public static final class AnalysisResult {
        public final Bitmap annotated;
        public final Bitmap reference;
        public final Bitmap aligned;
        public final String report;
        AnalysisResult(Bitmap annotated, Bitmap reference, Bitmap aligned, String report){this.annotated=annotated;this.reference=reference;this.aligned=aligned;this.report=report;}
        public Bitmap overlay(float alpha){
            if(reference==null||aligned==null)return annotated;
            Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG); c.drawBitmap(reference,0,0,p); p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255)))); c.drawBitmap(aligned,0,0,p); return out;
        }
    }

    private static final class Circle { double x,y,r; Circle(double x,double y,double r){this.x=x;this.y=y;this.r=r;} }

    private static final class Marker { int hour; double angular; double radial; double strength; Marker(int h,double a,double r,double s){hour=h;angular=a;radial=r;strength=s;} }

    public static AnalysisResult analyse(Bitmap watch, Bitmap reference, String modelRef) {
        Mat src = new Mat(); Utils.bitmapToMat(watch, src); Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);
        Circle circle = detectCircle(src);
        if(circle==null) throw new IllegalArgumentException("Watch face could not be detected. Use a clearer, more front-on photo.");
        List<Marker> markers = measureMarkers(src,circle);
        double tilt = perspectiveEquivalent(src,circle);
        Mat annotated = src.clone();
        Imgproc.circle(annotated,new Point(circle.x,circle.y),(int)Math.round(circle.r),new Scalar(50,213,242),3);
        for(Marker m:markers){
            double theta=Math.toRadians(m.hour==12?0:m.hour*30.0+m.angular); double rr=circle.r*0.72;
            Point p=new Point(circle.x+Math.sin(theta)*rr,circle.y-Math.cos(theta)*rr);
            Imgproc.circle(annotated,p,7,new Scalar(127,225,170),2);
        }
        Bitmap ann=toBitmap(annotated);

        Bitmap alignedBitmap=null; Bitmap refBitmap=null;
        double perspectiveMismatch=Double.NaN;
        if(reference!=null){
            refBitmap=reference.copy(Bitmap.Config.ARGB_8888,false);
            Mat ref=new Mat(); Utils.bitmapToMat(refBitmap,ref); Imgproc.cvtColor(ref,ref,Imgproc.COLOR_RGBA2BGR);
            Circle rc=detectCircle(ref);
            if(rc!=null){
                double s=rc.r/circle.r;
                Mat M=new Mat(2,3,CvType.CV_64F);
                M.put(0,0,s,0,rc.x-s*circle.x,0,s,rc.y-s*circle.y);
                Mat warped=new Mat(); Imgproc.warpAffine(src,warped,M,new Size(ref.cols(),ref.rows()),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0,0,0));
                alignedBitmap=toBitmap(warped);
                perspectiveMismatch=Math.abs(tilt-perspectiveEquivalent(ref,rc));
                M.release(); warped.release(); ref.release();
            }
        }

        StringBuilder report=new StringBuilder();
        report.append(modelRef).append(" · Watch Align Core ").append(CORE_VERSION).append("\n\n");
        report.append("Photo suitability: ").append(tilt<14?"GOOD":tilt<28?"PARTIAL":"POOR").append("\n");
        report.append(String.format(Locale.US,"Perspective distortion estimate: %.1f° equivalent\n",tilt));
        if(!Double.isNaN(perspectiveMismatch)) report.append(String.format(Locale.US,"Reference perspective mismatch: %.1f°\n",perspectiveMismatch));
        report.append("\nHour-marker geometry\n");
        double maxAbs=0;
        for(Marker m:markers){maxAbs=Math.max(maxAbs,Math.abs(m.angular)); report.append(String.format(Locale.US,"%2d o'clock  angular %+5.2f°   radial %+5.2f%%\n",m.hour,m.angular,m.radial));}
        report.append("\nMarker assessment: ").append(maxAbs<1.0?"LOOKS GOOD":maxAbs<2.0?"CHECK VISUALLY":"POSSIBLE ISSUE").append("\n");
        if(reference==null) report.append("\nNo genuine/reference photo selected. Analysis is QC-only and fully offline.");
        else if(alignedBitmap!=null) report.append("\nReference comparison ready. Use Genuine / Overlay to inspect the circle-aligned result.");
        else report.append("\nReference image was loaded but its watch face could not be detected, so overlay was withheld.");

        src.release(); annotated.release();
        return new AnalysisResult(ann,refBitmap,alignedBitmap,report.toString());
    }

    private static Circle detectCircle(Mat bgr){
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY); Imgproc.medianBlur(gray,gray,7);
        Mat circles=new Mat(); int min=Math.min(bgr.cols(),bgr.rows());
        Imgproc.HoughCircles(gray,circles,Imgproc.HOUGH_GRADIENT,1.2,min/6.0,130,45,(int)(min*0.18),(int)(min*0.48));
        Circle best=null; double bestScore=Double.MAX_VALUE;
        if(circles.cols()>0){
            for(int i=0;i<circles.cols();i++){double[] c=circles.get(0,i); if(c==null||c.length<3)continue; double dx=c[0]-bgr.cols()/2.0,dy=c[1]-bgr.rows()/2.0; double score=Math.hypot(dx,dy)-c[2]*0.05; if(score<bestScore){bestScore=score;best=new Circle(c[0],c[1],c[2]);}}
        }
        circles.release();gray.release();return best;
    }

    private static List<Marker> measureMarkers(Mat bgr,Circle c){
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY); Mat edges=new Mat(); Imgproc.Canny(gray,edges,60,160);
        byte[] data=new byte[(int)(edges.total()*edges.channels())]; edges.get(0,0,data);
        int w=edges.cols(),h=edges.rows(); double inner=c.r*0.55,outer=c.r*0.86;
        List<Marker> out=new ArrayList<>();
        for(int hour=1;hour<=12;hour++){
            double target=hour==12?0:hour*30.0; double sw=0,sd=0,sr=0; int count=0;
            int x0=Math.max(0,(int)(c.x-outer-2)),x1=Math.min(w-1,(int)(c.x+outer+2)); int y0=Math.max(0,(int)(c.y-outer-2)),y1=Math.min(h-1,(int)(c.y+outer+2));
            for(int y=y0;y<=y1;y++) for(int x=x0;x<=x1;x++){
                if((data[y*w+x]&0xff)==0)continue; double dx=x-c.x,dy=y-c.y,r=Math.hypot(dx,dy); if(r<inner||r>outer)continue;
                double a=Math.toDegrees(Math.atan2(dx,-dy)); if(a<0)a+=360; double d=((a-target+540)%360)-180; if(Math.abs(d)>8.5)continue;
                double wt=1.0; sw+=wt; sd+=d*wt; sr+=r*wt; count++;
            }
            if(sw>0){double angular=sd/sw;double radial=((sr/sw)/(c.r*0.705)-1.0)*100.0;out.add(new Marker(hour,angular,radial,count));}
        }
        edges.release();gray.release();return out;
    }

    private static double perspectiveEquivalent(Mat bgr,Circle c){
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Mat edges=new Mat();Imgproc.Canny(gray,edges,50,150);
        List<org.opencv.core.MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
        double bestArea=0,bestRatio=1.0;
        for(org.opencv.core.MatOfPoint contour:contours){double area=Math.abs(Imgproc.contourArea(contour)); if(area<Math.PI*c.r*c.r*0.20||area>Math.PI*c.r*c.r*1.8||contour.rows()<20)continue; org.opencv.core.MatOfPoint2f c2=new org.opencv.core.MatOfPoint2f(contour.toArray()); try{org.opencv.core.RotatedRect e=Imgproc.fitEllipse(c2); double major=Math.max(e.size.width,e.size.height),minor=Math.min(e.size.width,e.size.height); if(major>0&&area>bestArea){bestArea=area;bestRatio=Math.max(0.0,Math.min(1.0,minor/major));}}catch(Exception ignored){} c2.release();}
        for(org.opencv.core.MatOfPoint x:contours)x.release(); hierarchy.release();edges.release();gray.release();
        return Math.toDegrees(Math.acos(bestRatio));
    }

    private static Bitmap toBitmap(Mat bgr){Mat rgba=new Mat();Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA);Bitmap out=Bitmap.createBitmap(rgba.cols(),rgba.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(rgba,out);rgba.release();return out;}

    private WatchAlignCore(){}
}
