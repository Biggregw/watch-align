package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha7 core: model geometry drives registration. Raw-pixel ECC is deliberately
 * not used as a validity gate because different hands/reflections can make two
 * correctly registered watches correlate poorly.
 */
final class DialAnalysisEngine {
    public static final String CORE_VERSION = "1.3.0-alpha7";
    private static final double MIN_DIAL_QUALITY = 0.56;

    public static final class AnalysisResult {
        public final Bitmap annotated;
        public final Bitmap reference;
        public final Bitmap aligned;
        public final String report;
        public final double registrationConfidence;
        AnalysisResult(Bitmap annotated, Bitmap reference, Bitmap aligned, String report, double registrationConfidence) {
            this.annotated = annotated; this.reference = reference; this.aligned = aligned;
            this.report = report; this.registrationConfidence = registrationConfidence;
        }
        public Bitmap overlay(float alpha) {
            if (reference == null || aligned == null) return annotated;
            Bitmap out = Bitmap.createBitmap(reference.getWidth(), reference.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference, 0, 0, p);
            p.setAlpha(Math.max(0, Math.min(255, Math.round(alpha * 255))));
            c.drawBitmap(aligned, 0, 0, p);
            return out;
        }
    }

    static final class Circle {
        double x, y, r, quality, darkFraction, boundaryContrast;
        Circle(double x, double y, double r, double quality, double darkFraction, double boundaryContrast) {
            this.x=x; this.y=y; this.r=r; this.quality=quality;
            this.darkFraction=darkFraction; this.boundaryContrast=boundaryContrast;
        }
    }

    private static final class RawMarker {
        int hour; double angleError, radius, strength;
        RawMarker(int hour, double angleError, double radius, double strength) {
            this.hour=hour; this.angleError=angleError; this.radius=radius; this.strength=strength;
        }
    }

    static final class Marker {
        int hour; double angular, radial, radius, strength;
        Marker(int hour, double angular, double radial, double radius, double strength) {
            this.hour=hour; this.angular=angular; this.radial=radial; this.radius=radius; this.strength=strength;
        }
    }

    static final class MarkerSet {
        List<Marker> markers = new ArrayList<>();
        double globalRotation = Double.NaN;
        double medianRadius = Double.NaN;
    }

    public static double referenceScore(Bitmap watch, Bitmap reference) {
        Mat a = new Mat(), b = new Mat();
        try {
            Utils.bitmapToMat(watch, a); Imgproc.cvtColor(a, a, Imgproc.COLOR_RGBA2BGR);
            Utils.bitmapToMat(reference, b); Imgproc.cvtColor(b, b, Imgproc.COLOR_RGBA2BGR);
            Circle ca = detectDial(a), cb = detectDial(b);
            if (ca == null || cb == null || ca.quality < MIN_DIAL_QUALITY || cb.quality < MIN_DIAL_QUALITY)
                return Double.POSITIVE_INFINITY;
            MarkerSet ma = measureMarkerSet(a, ca), mb = measureMarkerSet(b, cb);
            if (ma.markers.size() < 7 || mb.markers.size() < 7) return Double.POSITIVE_INFINITY;

            double ax=ca.x/a.cols(), ay=ca.y/a.rows(), bx=cb.x/b.cols(), by=cb.y/b.rows();
            double ar=ca.r/Math.min(a.cols(),a.rows()), br=cb.r/Math.min(b.cols(),b.rows());
            double center=Math.hypot(ax-bx, ay-by);
            double scale=Math.abs(ar-br);
            double pa=perspectiveEquivalent(a,ca), pb=perspectiveEquivalent(b,cb);
            double persp=(Double.isFinite(pa)&&Double.isFinite(pb)) ? Math.abs(pa-pb)/25.0 : 0.25;
            double aspect=Math.abs(((double)a.cols()/a.rows()) - ((double)b.cols()/b.rows()));
            double roll=Math.abs(GeometryRegistration.wrap180(mb.globalRotation-ma.globalRotation))/30.0;
            double markerRms=commonMarkerRms(ma,mb)/5.0;
            double qualityPenalty=(2.0-ca.quality-cb.quality)*0.50;
            return center*1.2 + scale*2.0 + persp*3.0 + aspect*0.10 + roll*0.08 + markerRms*0.70 + Math.max(0,qualityPenalty);
        } finally { a.release(); b.release(); }
    }

    public static AnalysisResult analyse(Bitmap watch, Bitmap reference, String modelRef) {
        Mat src = new Mat(); Utils.bitmapToMat(watch, src); Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);
        Circle dial = detectDial(src);
        if (dial == null || dial.quality < MIN_DIAL_QUALITY) {
            src.release();
            throw new IllegalArgumentException("Dial geometry is not reliable enough to measure. Use a clearer photo with the full dial visible.");
        }
        MarkerSet markerSet = measureMarkerSet(src, dial);
        double tilt = perspectiveEquivalent(src, dial);

        Mat annotated = src.clone();
        Imgproc.circle(annotated, new Point(dial.x,dial.y), (int)Math.round(dial.r), new Scalar(50,213,242), 3);
        for (Marker m : markerSet.markers) {
            double theta=Math.toRadians((m.hour==12?0:m.hour*30.0) + markerSet.globalRotation + m.angular);
            Point p=new Point(dial.x+Math.sin(theta)*m.radius, dial.y-Math.cos(theta)*m.radius);
            Imgproc.circle(annotated,p,7,new Scalar(127,225,170),2);
        }
        Bitmap ann = toBitmap(annotated);

        Bitmap alignedBitmap=null, refBitmap=null;
        double perspectiveMismatch=Double.NaN, registrationConfidence=Double.NaN;
        String overlayReason=null;
        if (reference != null) {
            refBitmap=reference.copy(Bitmap.Config.ARGB_8888,false);
            Mat ref=new Mat(); Utils.bitmapToMat(refBitmap,ref); Imgproc.cvtColor(ref,ref,Imgproc.COLOR_RGBA2BGR);
            Circle rd=detectDial(ref);
            if (rd==null || rd.quality<MIN_DIAL_QUALITY) {
                overlayReason="Reference dial geometry could not be verified; overlay withheld.";
            } else {
                MarkerSet refMarkers=measureMarkerSet(ref,rd);
                double rt=perspectiveEquivalent(ref,rd);
                if (Double.isFinite(tilt)&&Double.isFinite(rt)) perspectiveMismatch=Math.abs(tilt-rt);
                int common=commonMarkerCount(markerSet,refMarkers);
                double rms=commonMarkerRms(markerSet,refMarkers);
                GeometryRegistration.Solution sol=GeometryRegistration.solve(
                        dial.r,rd.r,
                        markerSet.medianRadius,refMarkers.medianRadius,
                        markerSet.globalRotation,refMarkers.globalRotation,
                        dial.quality,rd.quality,
                        common,rms,perspectiveMismatch);
                registrationConfidence=sol.confidence;
                if (sol.usable) {
                    Mat affine=Imgproc.getRotationMatrix2D(new Point(dial.x,dial.y),sol.rotationDeg,sol.scale);
                    addTo(affine,0,2,rd.x-dial.x); addTo(affine,1,2,rd.y-dial.y);
                    Mat aligned=new Mat();
                    Imgproc.warpAffine(src,aligned,affine,new Size(ref.cols(),ref.rows()),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0,0,0));

                    Circle check=detectDial(aligned);
                    if (check!=null) {
                        MarkerSet checkMarkers=measureMarkerSet(aligned,check);
                        double centreError=Math.hypot(check.x-rd.x,check.y-rd.y)/Math.max(1.0,rd.r);
                        double radiusError=Math.abs(check.r-rd.r)/Math.max(1.0,rd.r);
                        double finalRms=commonMarkerRms(checkMarkers,refMarkers);
                        boolean sane=centreError<=0.06 && radiusError<=0.13 && commonMarkerCount(checkMarkers,refMarkers)>=7 && finalRms<=3.0;
                        if (sane) alignedBitmap=toBitmap(aligned);
                        else overlayReason=String.format(Locale.US,
                                "Geometry alignment failed final validation (centre %.1f%%, radius %.1f%%, marker RMS %.2f°); overlay withheld.",
                                centreError*100.0,radiusError*100.0,finalRms);
                    } else overlayReason="Aligned watch dial could not be re-detected; overlay withheld.";
                    aligned.release(); affine.release();
                } else overlayReason=sol.reason!=null?sol.reason:"Geometry registration confidence is too low; overlay withheld.";
            }
            ref.release();
        }

        StringBuilder report=new StringBuilder();
        report.append(modelRef).append(" · Watch Align Core ").append(CORE_VERSION).append("\n\n");
        report.append(String.format(Locale.US,"Dial detection confidence: %.2f\n",dial.quality));
        report.append(String.format(Locale.US,"Dial boundary contrast: %.2f\n",dial.boundaryContrast));
        if (Double.isFinite(tilt)) {
            report.append("Photo suitability: ").append(tilt<14?"GOOD":tilt<28?"PARTIAL":"POOR").append("\n");
            report.append(String.format(Locale.US,"Perspective distortion estimate: %.1f° equivalent\n",tilt));
        } else report.append("Photo suitability: PARTIAL\nPerspective distortion estimate: unavailable\n");
        if (Double.isFinite(perspectiveMismatch)) report.append(String.format(Locale.US,"Reference perspective mismatch: %.1f°\n",perspectiveMismatch));
        if (Double.isFinite(registrationConfidence)) report.append(String.format(Locale.US,"Geometry registration confidence: %.2f\n",registrationConfidence));
        if (Double.isFinite(markerSet.globalRotation)) report.append(String.format(Locale.US,"Photo roll removed from marker QC: %+4.2f°\n",markerSet.globalRotation));

        report.append("\nHour-marker geometry (relative to this watch's marker-ring consensus)\n");
        double maxAbs=0;
        for (Marker m : markerSet.markers) {
            maxAbs=Math.max(maxAbs,Math.abs(m.angular));
            report.append(String.format(Locale.US,"%2d o'clock  angular %+5.2f°   radial %+5.2f%%\n",m.hour,m.angular,m.radial));
        }
        if (markerSet.markers.size()<8) report.append("\nMarker assessment: INSUFFICIENT RELIABLE MARKERS\n");
        else report.append("\nMarker assessment: ").append(maxAbs<1.0?"LOOKS GOOD":maxAbs<2.0?"CHECK VISUALLY":"POSSIBLE ISSUE").append("\n");

        if (reference==null) report.append("\nNo reference selected.");
        else if (alignedBitmap!=null) report.append("\nReference comparison ready. Overlay passed dial, marker-ring and perspective geometry checks.");
        else report.append("\n").append(overlayReason!=null?overlayReason:"Overlay withheld because registration was not reliable enough.");

        src.release(); annotated.release();
        return new AnalysisResult(ann,refBitmap,alignedBitmap,report.toString(),registrationConfidence);
    }

    static Circle detectDial(Mat bgr) {
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY); Imgproc.GaussianBlur(gray,gray,new Size(7,7),0);
        int min=Math.min(bgr.cols(),bgr.rows()); Circle best=null; double bestQ=-1;
        Mat circles=new Mat();
        Imgproc.HoughCircles(gray,circles,Imgproc.HOUGH_GRADIENT,1.12,min/12.0,120,24,(int)(min*0.105),(int)(min*0.28));
        if(circles.cols()>0) {
            for(int i=0;i<circles.cols();i++) {
                double[] c=circles.get(0,i); if(c==null||c.length<3)continue;
                Circle scored=scoreDialCandidate(gray,c[0],c[1],c[2],min);
                if(scored!=null&&scored.quality>bestQ){best=scored;bestQ=scored.quality;}
            }
        }
        circles.release();
        if(best!=null){Circle inner=refineInnerDial(gray,best,min);if(inner!=null)best=inner;}
        gray.release(); return best;
    }

    private static Circle scoreDialCandidate(Mat gray,double cx,double cy,double r,int min) {
        double rn=r/min; if(rn<0.105||rn>0.28)return null;
        double nx=cx/gray.cols(),ny=cy/gray.rows(); if(nx<0.10||nx>0.90||ny<0.08||ny>0.88)return null;
        double centerDist=Math.hypot(nx-0.5,ny-0.46); if(centerDist>0.44)return null;
        double darkCore=sampleDarkFraction(gray,cx,cy,r*0.58),darkWide=sampleDarkFraction(gray,cx,cy,r*0.91);
        double annulusDark=sampleAnnulusDarkFraction(gray,cx,cy,r*0.66,r*0.90);
        double ringEdge=sampleRingEdge(gray,cx,cy,r),contrast=sampleBoundaryContrast(gray,cx,cy,r);
        int markerHits=markerRingHits(gray,cx,cy,r);
        if(darkCore<0.50||darkWide<0.43||annulusDark<0.38||markerHits<7||ringEdge<0.05)return null;
        double centerFit=1.0-Math.min(1.0,centerDist/0.44),sizeFit=1.0-Math.min(1.0,Math.abs(rn-0.145)/0.12);
        double markerFit=Math.min(1.0,markerHits/11.0),darkFit=Math.min(1.0,(0.45*darkCore+0.35*darkWide+0.20*annulusDark)/0.72);
        double edgeFit=Math.min(1.0,ringEdge/0.18),contrastFit=Math.max(0,Math.min(1.0,(contrast+0.02)/0.20));
        double q=0.27*darkFit+0.24*markerFit+0.17*contrastFit+0.13*edgeFit+0.10*centerFit+0.09*sizeFit;
        if(contrast<0.025)q*=0.82;
        return new Circle(cx,cy,r,q,darkWide,contrast);
    }

    private static Circle refineInnerDial(Mat gray,Circle outer,int min) {
        Circle best=null; double bestScore=-1;
        for(double rf=0.62;rf<=0.88;rf+=0.02) {
            double r=outer.r*rf;
            for(double dx=-0.025;dx<=0.025;dx+=0.025) for(double dy=-0.025;dy<=0.025;dy+=0.025) {
                Circle c=scoreDialCandidate(gray,outer.x+dx*outer.r,outer.y+dy*outer.r,r,min); if(c==null)continue;
                double ratioFit=1.0-Math.min(1.0,Math.abs(rf-0.76)/0.14);
                double gain=c.boundaryContrast-outer.boundaryContrast;
                double score=c.quality+0.16*ratioFit+0.35*Math.max(0,gain);
                if(c.boundaryContrast>=0.040&&score>bestScore){bestScore=score;best=c;}
            }
        }
        if(best==null)return outer;
        boolean strongerBoundary=best.boundaryContrast>=outer.boundaryContrast+0.018;
        boolean smaller=best.r<=outer.r*0.86;
        boolean qualityClose=best.quality>=outer.quality-0.10;
        return (strongerBoundary&&smaller&&qualityClose)?best:outer;
    }

    static MarkerSet measureMarkerSet(Mat bgr,Circle c) {
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
        int w=gray.cols(),h=gray.rows(); double inner=c.r*0.66,outer=c.r*0.94;
        List<RawMarker> raw=new ArrayList<>();
        for(int hour=1;hour<=12;hour++) {
            double target=hour==12?0:hour*30.0,sw=0,sd=0,sr=0; int count=0;
            int x0=Math.max(0,(int)(c.x-outer-2)),x1=Math.min(w-1,(int)(c.x+outer+2));
            int y0=Math.max(0,(int)(c.y-outer-2)),y1=Math.min(h-1,(int)(c.y+outer+2));
            for(int y=y0;y<=y1;y+=2) for(int x=x0;x<=x1;x+=2) {
                double dx=x-c.x,dy=y-c.y,r=Math.hypot(dx,dy); if(r<inner||r>outer)continue;
                double a=Math.toDegrees(Math.atan2(dx,-dy)); if(a<0)a+=360;
                double d=GeometryRegistration.wrap180(a-target); if(Math.abs(d)>5.0)continue;
                double[] gv=gray.get(y,x); if(gv==null||gv[0]<155)continue;
                double wt=Math.max(1.0,(gv[0]-145.0)/18.0); sw+=wt;sd+=d*wt;sr+=r*wt;count++;
            }
            if(sw>10&&count>=4)raw.add(new RawMarker(hour,sd/sw,sr/sw,count));
        }
        gray.release();
        MarkerSet set=new MarkerSet(); if(raw.isEmpty())return set;
        double[] angles=new double[raw.size()],radii=new double[raw.size()];
        for(int i=0;i<raw.size();i++){angles[i]=raw.get(i).angleError;radii[i]=raw.get(i).radius;}
        set.globalRotation=GeometryRegistration.median(angles);
        set.medianRadius=GeometryRegistration.median(radii);
        for(RawMarker rm:raw){
            double angular=GeometryRegistration.wrap180(rm.angleError-set.globalRotation);
            double radial=(rm.radius/set.medianRadius-1.0)*100.0;
            set.markers.add(new Marker(rm.hour,angular,radial,rm.radius,rm.strength));
        }
        return set;
    }

    private static int markerRingHits(Mat gray,double cx,double cy,double r) {
        int hits=0;
        for(int h=1;h<=12;h++) {
            double a=Math.toRadians(h==12?0:h*30.0),best=0,bestDark=255;
            for(double rf=0.70;rf<=0.88;rf+=0.03) for(double da=-4;da<=4;da+=2) {
                double aa=a+Math.toRadians(da);
                int x=(int)Math.round(cx+Math.sin(aa)*r*rf),y=(int)Math.round(cy-Math.cos(aa)*r*rf);
                int xi=(int)Math.round(cx+Math.sin(aa)*r*Math.max(0.55,rf-0.10)),yi=(int)Math.round(cy-Math.cos(aa)*r*Math.max(0.55,rf-0.10));
                if(x<0||x>=gray.cols()||y<0||y>=gray.rows()||xi<0||xi>=gray.cols()||yi<0||yi>=gray.rows())continue;
                double[]v=gray.get(y,x),vi=gray.get(yi,xi); if(v!=null&&vi!=null){best=Math.max(best,v[0]);bestDark=Math.min(bestDark,vi[0]);}
            }
            if(best>=165&&bestDark<=145)hits++;
        }
        return hits;
    }

    static int commonMarkerCount(MarkerSet a,MarkerSet b) {
        int n=0; for(Marker x:a.markers) if(find(b,x.hour)!=null)n++; return n;
    }

    static double commonMarkerRms(MarkerSet a,MarkerSet b) {
        double ss=0; int n=0;
        for(Marker x:a.markers){Marker y=find(b,x.hour);if(y==null)continue;double d=GeometryRegistration.wrap180(x.angular-y.angular);ss+=d*d;n++;}
        return n>0?Math.sqrt(ss/n):99.0;
    }

    private static Marker find(MarkerSet set,int hour){for(Marker m:set.markers)if(m.hour==hour)return m;return null;}

    private static double sampleDarkFraction(Mat gray,double cx,double cy,double radius) {
        int step=Math.max(2,(int)Math.round(radius/45.0));double dark=0,total=0;
        int x0=Math.max(0,(int)(cx-radius)),x1=Math.min(gray.cols()-1,(int)(cx+radius));
        int y0=Math.max(0,(int)(cy-radius)),y1=Math.min(gray.rows()-1,(int)(cy+radius));
        for(int y=y0;y<=y1;y+=step)for(int x=x0;x<=x1;x+=step){if(Math.hypot(x-cx,y-cy)>radius)continue;double[]v=gray.get(y,x);if(v==null)continue;total++;if(v[0]<135)dark++;}
        return total>0?dark/total:0;
    }

    private static double sampleAnnulusDarkFraction(Mat gray,double cx,double cy,double inner,double outer) {
        int step=Math.max(2,(int)Math.round(outer/55.0));double dark=0,total=0;
        int x0=Math.max(0,(int)(cx-outer)),x1=Math.min(gray.cols()-1,(int)(cx+outer));
        int y0=Math.max(0,(int)(cy-outer)),y1=Math.min(gray.rows()-1,(int)(cy+outer));
        for(int y=y0;y<=y1;y+=step)for(int x=x0;x<=x1;x+=step){double d=Math.hypot(x-cx,y-cy);if(d<inner||d>outer)continue;double[]v=gray.get(y,x);if(v==null)continue;total++;if(v[0]<135)dark++;}
        return total>0?dark/total:0;
    }

    private static double sampleRingEdge(Mat gray,double cx,double cy,double r) {
        double sum=0;int n=0;
        for(int deg=0;deg<360;deg+=4){double a=Math.toRadians(deg),best=0;for(double f=0.94;f<=1.06;f+=0.02){
            int xi=(int)Math.round(cx+Math.cos(a)*r*(f-0.025)),yi=(int)Math.round(cy+Math.sin(a)*r*(f-0.025));
            int xo=(int)Math.round(cx+Math.cos(a)*r*(f+0.025)),yo=(int)Math.round(cy+Math.sin(a)*r*(f+0.025));
            if(xi<0||xi>=gray.cols()||yi<0||yi>=gray.rows()||xo<0||xo>=gray.cols()||yo<0||yo>=gray.rows())continue;
            double[]vi=gray.get(yi,xi),vo=gray.get(yo,xo);if(vi!=null&&vo!=null)best=Math.max(best,Math.abs(vo[0]-vi[0])/255.0);
        }sum+=best;n++;}return n>0?sum/n:0;
    }

    private static double sampleBoundaryContrast(Mat gray,double cx,double cy,double r) {
        double sum=0;int n=0;
        for(int deg=0;deg<360;deg+=5){if(deg>=70&&deg<=110)continue;double a=Math.toRadians(deg);
            int xi=(int)Math.round(cx+Math.cos(a)*r*0.93),yi=(int)Math.round(cy+Math.sin(a)*r*0.93);
            int xo=(int)Math.round(cx+Math.cos(a)*r*1.04),yo=(int)Math.round(cy+Math.sin(a)*r*1.04);
            if(xi<0||xi>=gray.cols()||yi<0||yi>=gray.rows()||xo<0||xo>=gray.cols()||yo<0||yo>=gray.rows())continue;
            double[]vi=gray.get(yi,xi),vo=gray.get(yo,xo);if(vi!=null&&vo!=null){sum+=(vo[0]-vi[0])/255.0;n++;}
        }
        return n>0?sum/n:0;
    }

    static double perspectiveEquivalent(Mat bgr,Circle c) {
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Mat edges=new Mat();Imgproc.Canny(gray,edges,50,150);
        List<org.opencv.core.MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
        double bestScore=0,bestRatio=Double.NaN;
        for(org.opencv.core.MatOfPoint contour:contours){double area=Math.abs(Imgproc.contourArea(contour));if(area<Math.PI*c.r*c.r*0.35||area>Math.PI*c.r*c.r*1.35||contour.rows()<30)continue;
            org.opencv.core.MatOfPoint2f c2=new org.opencv.core.MatOfPoint2f(contour.toArray());
            try{org.opencv.core.RotatedRect e=Imgproc.fitEllipse(c2);double dc=Math.hypot(e.center.x-c.x,e.center.y-c.y)/c.r,major=Math.max(e.size.width,e.size.height),minor=Math.min(e.size.width,e.size.height);if(major<=0||dc>0.12){c2.release();continue;}double mr=major/(2*c.r);if(mr<0.80||mr>1.20){c2.release();continue;}double score=area*(1.0-dc);if(score>bestScore){bestScore=score;bestRatio=Math.max(0.0,Math.min(1.0,minor/major));}}catch(Exception ignored){}c2.release();}
        for(org.opencv.core.MatOfPoint x:contours)x.release();hierarchy.release();edges.release();gray.release();
        return Double.isFinite(bestRatio)?Math.toDegrees(Math.acos(bestRatio)):Double.NaN;
    }

    private static void addTo(Mat m,int row,int col,double delta){double[]v=m.get(row,col);m.put(row,col,(v!=null&&v.length>0?v[0]:0)+delta);}
    private static Bitmap toBitmap(Mat bgr){Mat rgba=new Mat();Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA);Bitmap out=Bitmap.createBitmap(rgba.cols(),rgba.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(rgba,out);rgba.release();return out;}
    private DialAnalysisEngine() {}
}
