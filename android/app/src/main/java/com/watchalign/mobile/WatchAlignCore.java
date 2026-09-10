package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.TermCriteria;
import org.opencv.imgproc.Imgproc;
import org.opencv.video.Video;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class WatchAlignCore {
    public static final String CORE_VERSION = "1.3.0-alpha3";
    private static final double MIN_CIRCLE_QUALITY = 0.30;
    private static final double MIN_OVERLAY_ECC = 0.55;

    public static final class AnalysisResult {
        public final Bitmap annotated;
        public final Bitmap reference;
        public final Bitmap aligned;
        public final String report;
        public final double registrationConfidence;
        AnalysisResult(Bitmap annotated, Bitmap reference, Bitmap aligned, String report, double registrationConfidence){
            this.annotated=annotated; this.reference=reference; this.aligned=aligned; this.report=report; this.registrationConfidence=registrationConfidence;
        }
        public Bitmap overlay(float alpha){
            if(reference==null||aligned==null)return annotated;
            Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference,0,0,p);
            p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));
            c.drawBitmap(aligned,0,0,p);
            return out;
        }
    }

    private static final class Circle {
        double x,y,r,quality,darkFraction;
        Circle(double x,double y,double r,double quality,double darkFraction){this.x=x;this.y=y;this.r=r;this.quality=quality;this.darkFraction=darkFraction;}
    }
    private static final class Marker { int hour; double angular; double radial; double strength; Marker(int h,double a,double r,double s){hour=h;angular=a;radial=r;strength=s;} }

    public static double referenceScore(Bitmap watch, Bitmap reference) {
        Mat a=new Mat(), b=new Mat();
        try {
            Utils.bitmapToMat(watch,a); Imgproc.cvtColor(a,a,Imgproc.COLOR_RGBA2BGR);
            Utils.bitmapToMat(reference,b); Imgproc.cvtColor(b,b,Imgproc.COLOR_RGBA2BGR);
            Circle ca=detectCircle(a), cb=detectCircle(b);
            if(ca==null||cb==null||ca.quality<MIN_CIRCLE_QUALITY||cb.quality<MIN_CIRCLE_QUALITY) return Double.POSITIVE_INFINITY;
            double ax=ca.x/a.cols(), ay=ca.y/a.rows(), bx=cb.x/b.cols(), by=cb.y/b.rows();
            double ar=ca.r/Math.min(a.cols(),a.rows()), br=cb.r/Math.min(b.cols(),b.rows());
            double center=Math.hypot(ax-bx,ay-by);
            double scale=Math.abs(ar-br);
            double pa=perspectiveEquivalent(a,ca), pb=perspectiveEquivalent(b,cb);
            double persp=(Double.isFinite(pa)&&Double.isFinite(pb)) ? Math.abs(pa-pb)/30.0 : 0.30;
            double aspect=Math.abs(((double)a.cols()/a.rows())-((double)b.cols()/b.rows()));
            double qualityPenalty=(2.0-ca.quality-cb.quality)*0.45;
            return center*1.7 + scale*2.6 + persp*2.2 + aspect*0.2 + Math.max(0,qualityPenalty);
        } finally { a.release(); b.release(); }
    }

    public static AnalysisResult analyse(Bitmap watch, Bitmap reference, String modelRef) {
        Mat src = new Mat(); Utils.bitmapToMat(watch, src); Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);
        Circle circle = detectCircle(src);
        if(circle==null || circle.quality<MIN_CIRCLE_QUALITY) {
            src.release();
            throw new IllegalArgumentException("Watch face geometry is not reliable enough to measure. Use a clearer, more front-on photo with the complete watch head visible.");
        }

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
        double perspectiveMismatch=Double.NaN; double ecc=Double.NaN;
        String overlayReason=null;
        if(reference!=null){
            refBitmap=reference.copy(Bitmap.Config.ARGB_8888,false);
            Mat ref=new Mat(); Utils.bitmapToMat(refBitmap,ref); Imgproc.cvtColor(ref,ref,Imgproc.COLOR_RGBA2BGR);
            Circle rc=detectCircle(ref);
            if(rc!=null && rc.quality>=MIN_CIRCLE_QUALITY){
                double s=rc.r/circle.r;
                if(s<0.45 || s>2.20) {
                    overlayReason="Detected watch geometry differs too much in scale; overlay withheld.";
                } else {
                    Mat initial=new Mat(2,3,CvType.CV_64F);
                    initial.put(0,0,s,0,rc.x-s*circle.x,0,s,rc.y-s*circle.y);
                    Mat pre=new Mat(); Imgproc.warpAffine(src,pre,initial,new Size(ref.cols(),ref.rows()),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0,0,0));

                    Mat refGray=new Mat(), preGray=new Mat();
                    Imgproc.cvtColor(ref,refGray,Imgproc.COLOR_BGR2GRAY); Imgproc.cvtColor(pre,preGray,Imgproc.COLOR_BGR2GRAY);
                    Imgproc.GaussianBlur(refGray,refGray,new Size(5,5),0); Imgproc.GaussianBlur(preGray,preGray,new Size(5,5),0);
                    Mat warp=Mat.eye(2,3,CvType.CV_32F);
                    try {
                        ecc=Video.findTransformECC(refGray,preGray,warp,Video.MOTION_EUCLIDEAN,new TermCriteria(TermCriteria.COUNT+TermCriteria.EPS,100,1e-6));
                        if(Double.isFinite(ecc) && ecc>=MIN_OVERLAY_ECC){
                            // Reject implausibly large residual rotation/translation even if ECC happens to be high.
                            double[] row0=warp.get(0,0), row1=warp.get(1,0);
                            double a=row0!=null&&row0.length>0?row0[0]:1.0;
                            double b=row0!=null&&row0.length>1?row0[1]:0.0;
                            double tx=row0!=null&&row0.length>2?row0[2]:0.0;
                            double ty=row1!=null&&row1.length>2?row1[2]:0.0;
                            double rot=Math.abs(Math.toDegrees(Math.atan2(b,a)));
                            double shift=Math.hypot(tx,ty)/Math.max(1.0,rc.r);
                            if(rot<=8.0 && shift<=0.30){
                                Mat refined=new Mat();
                                Imgproc.warpAffine(pre,refined,warp,new Size(ref.cols(),ref.rows()),Imgproc.INTER_LINEAR+Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0,0,0));
                                alignedBitmap=toBitmap(refined);
                                refined.release();
                            } else {
                                overlayReason=String.format(Locale.US,"Residual registration was implausible (%.1f° / %.0f%% radius); overlay withheld.",rot,shift*100.0);
                            }
                        } else {
                            overlayReason=Double.isFinite(ecc) ? String.format(Locale.US,"Registration confidence %.2f is below the %.2f safety threshold; overlay withheld.",ecc,MIN_OVERLAY_ECC) : "Registration could not be verified; overlay withheld.";
                        }
                    } catch(Exception ignored) {
                        ecc=Double.NaN; overlayReason="Registration could not be verified; overlay withheld.";
                    }
                    warp.release(); refGray.release(); preGray.release(); initial.release(); pre.release();
                }
                double rt=perspectiveEquivalent(ref,rc);
                if(Double.isFinite(tilt)&&Double.isFinite(rt)) perspectiveMismatch=Math.abs(tilt-rt);
            } else {
                overlayReason="Reference watch face geometry could not be verified; overlay withheld.";
            }
            ref.release();
        }

        StringBuilder report=new StringBuilder();
        report.append(modelRef).append(" · Watch Align Core ").append(CORE_VERSION).append("\n\n");
        report.append(String.format(Locale.US,"Watch detection confidence: %.2f\n",circle.quality));
        if(Double.isFinite(tilt)) {
            report.append("Photo suitability: ").append(tilt<14?"GOOD":tilt<28?"PARTIAL":"POOR").append("\n");
            report.append(String.format(Locale.US,"Perspective distortion estimate: %.1f° equivalent\n",tilt));
        } else {
            report.append("Photo suitability: PARTIAL\nPerspective distortion estimate: unavailable\n");
        }
        if(!Double.isNaN(perspectiveMismatch)) report.append(String.format(Locale.US,"Reference perspective mismatch: %.1f°\n",perspectiveMismatch));
        if(!Double.isNaN(ecc)) report.append(String.format(Locale.US,"Overlay registration confidence: %.2f\n",ecc));

        report.append("\nHour-marker geometry\n");
        double maxAbs=0;
        for(Marker m:markers){maxAbs=Math.max(maxAbs,Math.abs(m.angular)); report.append(String.format(Locale.US,"%2d o'clock  angular %+5.2f°   radial %+5.2f%%\n",m.hour,m.angular,m.radial));}
        report.append("\nMarker assessment: ").append(maxAbs<1.0?"LOOKS GOOD":maxAbs<2.0?"CHECK VISUALLY":"POSSIBLE ISSUE").append("\n");
        if(reference==null) report.append("\nNo reference selected.");
        else if(alignedBitmap!=null) report.append("\nReference comparison ready. Overlay passed the registration safety gate.");
        else report.append("\n").append(overlayReason!=null?overlayReason:"Overlay withheld because registration was not reliable enough.");

        src.release(); annotated.release();
        return new AnalysisResult(ann,refBitmap,alignedBitmap,report.toString(),ecc);
    }

    private static Circle detectCircle(Mat bgr){
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY); Imgproc.medianBlur(gray,gray,7);
        Mat edges=new Mat(); Imgproc.Canny(gray,edges,55,150);
        Mat circles=new Mat(); int min=Math.min(bgr.cols(),bgr.rows());
        Imgproc.HoughCircles(gray,circles,Imgproc.HOUGH_GRADIENT,1.15,min/8.0,125,34,(int)(min*0.14),(int)(min*0.42));

        Circle best=null; double bestCost=Double.POSITIVE_INFINITY;
        if(circles.cols()>0){
            for(int i=0;i<circles.cols();i++){
                double[] c=circles.get(0,i); if(c==null||c.length<3)continue;
                double x=c[0], y=c[1], r=c[2], rn=r/min;
                double nx=x/bgr.cols(), ny=y/bgr.rows();
                if(nx<0.08||nx>0.92||ny<0.08||ny>0.92||rn<0.14||rn>0.42) continue;

                double centerDist=Math.hypot(nx-0.5,ny-0.5);
                if(centerDist>0.48) continue;

                double dark=0, total=0, edgeHits=0, edgeTotal=0;
                int step=Math.max(2,(int)Math.round(r/55.0));
                int x0=Math.max(0,(int)Math.floor(x-r*1.05)), x1=Math.min(gray.cols()-1,(int)Math.ceil(x+r*1.05));
                int y0=Math.max(0,(int)Math.floor(y-r*1.05)), y1=Math.min(gray.rows()-1,(int)Math.ceil(y+r*1.05));
                for(int yy=y0;yy<=y1;yy+=step){
                    for(int xx=x0;xx<=x1;xx+=step){
                        double rr=Math.hypot(xx-x,yy-y)/r;
                        if(rr<=0.62){
                            double[] gv=gray.get(yy,xx); if(gv!=null){ total++; if(gv[0]<105) dark++; }
                        }
                        if(rr>=0.72&&rr<=1.04){
                            edgeTotal++; double[] ev=edges.get(yy,xx); if(ev!=null&&ev[0]>0) edgeHits++;
                        }
                    }
                }
                if(total<30||edgeTotal<30) continue;
                double darkFraction=dark/total;
                double edgeDensity=edgeHits/edgeTotal;
                // Both currently supported watches have black dials. This rejects glove/background circles.
                if(darkFraction<0.18 || edgeDensity<0.035) continue;

                double sizeFit=1.0-Math.min(1.0,Math.abs(rn-0.27)/0.18);
                double centerFit=1.0-Math.min(1.0,centerDist/0.48);
                double darkFit=Math.min(1.0,darkFraction/0.55);
                double edgeFit=Math.min(1.0,edgeDensity/0.18);
                double quality=0.34*darkFit+0.28*edgeFit+0.22*centerFit+0.16*sizeFit;
                double cost=(1.0-quality)+centerDist*0.15;
                if(cost<bestCost){bestCost=cost;best=new Circle(x,y,r,quality,darkFraction);}
            }
        }
        circles.release(); edges.release(); gray.release(); return best;
    }

    private static List<Marker> measureMarkers(Mat bgr,Circle c){
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
        int w=gray.cols(),h=gray.rows(); double inner=c.r*0.58,outer=c.r*0.84;
        List<Marker> out=new ArrayList<>();
        for(int hour=1;hour<=12;hour++){
            double target=hour==12?0:hour*30.0; double sw=0,sd=0,sr=0; int count=0;
            int x0=Math.max(0,(int)(c.x-outer-2)),x1=Math.min(w-1,(int)(c.x+outer+2));
            int y0=Math.max(0,(int)(c.y-outer-2)),y1=Math.min(h-1,(int)(c.y+outer+2));
            for(int y=y0;y<=y1;y+=2) for(int x=x0;x<=x1;x+=2){
                double dx=x-c.x,dy=y-c.y,r=Math.hypot(dx,dy); if(r<inner||r>outer)continue;
                double a=Math.toDegrees(Math.atan2(dx,-dy)); if(a<0)a+=360;
                double d=((a-target+540)%360)-180; if(Math.abs(d)>6.0)continue;
                double[] gv=gray.get(y,x); if(gv==null||gv[0]<145)continue;
                double wt=Math.max(1.0,(gv[0]-135.0)/20.0);
                sw+=wt; sd+=d*wt; sr+=r*wt; count++;
            }
            if(sw>8 && count>=3){
                double angular=sd/sw; double radial=((sr/sw)/(c.r*0.715)-1.0)*100.0;
                out.add(new Marker(hour,angular,radial,count));
            }
        }
        gray.release(); return out;
    }

    private static double perspectiveEquivalent(Mat bgr,Circle c){
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Mat edges=new Mat();Imgproc.Canny(gray,edges,50,150);
        List<org.opencv.core.MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
        double bestArea=0,bestRatio=Double.NaN;
        for(org.opencv.core.MatOfPoint contour:contours){
            double area=Math.abs(Imgproc.contourArea(contour));
            if(area<Math.PI*c.r*c.r*0.25||area>Math.PI*c.r*c.r*1.45||contour.rows()<30)continue;
            org.opencv.core.MatOfPoint2f c2=new org.opencv.core.MatOfPoint2f(contour.toArray());
            try{
                org.opencv.core.RotatedRect e=Imgproc.fitEllipse(c2);
                double dist=Math.hypot(e.center.x-c.x,e.center.y-c.y);
                double major=Math.max(e.size.width,e.size.height),minor=Math.min(e.size.width,e.size.height);
                if(major>0&&minor>0&&dist<c.r*0.22&&area>bestArea){bestArea=area;bestRatio=Math.max(0.0,Math.min(1.0,minor/major));}
            }catch(Exception ignored){}
            c2.release();
        }
        for(org.opencv.core.MatOfPoint x:contours)x.release(); hierarchy.release();edges.release();gray.release();
        return Double.isFinite(bestRatio) ? Math.toDegrees(Math.acos(bestRatio)) : Double.NaN;
    }

    private static Bitmap toBitmap(Mat bgr){
        Mat rgba=new Mat();Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA);
        Bitmap out=Bitmap.createBitmap(rgba.cols(),rgba.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(rgba,out);rgba.release();return out;
    }
    private WatchAlignCore(){}
}
