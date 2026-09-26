package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GMT marker QC using the accepted minute-track pose as the datum.
 *
 * Marker positions are NOT taken from WatchAlignCoreV7.measureMarkerSet. The legacy detector
 * averages bright pixels across a broad hour-sector annulus and can latch onto hands, minute
 * ticks or reflections. Instead, this class projects a small shape-specific ROI from a
 * genuine-derived detection datum and only measures a connected bright component that is
 * geometrically plausible there. If the component cannot be isolated, the marker is reported
 * as not measurable rather than emitting an extreme false value.
 */
final class GmtMarkerQcRepair {
    static final class MarkerDiagnostic {
        final int hour;
        final double angularDeg;
        final double radialPctR;
        final double bodyRotationDeg;
        final boolean minuteTrackAnchored;
        final boolean measured;

        MarkerDiagnostic(int hour,double angularDeg,double radialPctR,
                         double bodyRotationDeg,boolean minuteTrackAnchored){
            this(hour,angularDeg,radialPctR,bodyRotationDeg,minuteTrackAnchored,true);
        }

        MarkerDiagnostic(int hour,double angularDeg,double radialPctR,
                         double bodyRotationDeg,boolean minuteTrackAnchored,boolean measured){
            this.hour=hour;
            this.angularDeg=angularDeg;
            this.radialPctR=radialPctR;
            this.bodyRotationDeg=bodyRotationDeg;
            this.minuteTrackAnchored=minuteTrackAnchored;
            this.measured=measured;
        }
    }

    private static final class Basis {
        final Point center;
        final double rx,ry,tx,ty,det;
        Basis(Point center,double rx,double ry,double tx,double ty){
            this.center=center;this.rx=rx;this.ry=ry;this.tx=tx;this.ty=ty;
            this.det=rx*ty-ry*tx;
        }
        Point local(double x,double y){
            double dx=x-center.x,dy=y-center.y;
            return new Point((dx*ty-dy*tx)/det,(-dx*ry+dy*rx)/det);
        }
    }

    private static final class Candidate {
        final Point center;
        final double radialLocal,tangentLocal,areaNorm,rotationDeg,anisotropy;
        Candidate(Point center,double radial,double tangent,double area,
                  double rotation,double anisotropy){
            this.center=center;this.radialLocal=radial;this.tangentLocal=tangent;
            this.areaNorm=area;this.rotationDeg=rotation;this.anisotropy=anisotropy;
        }
    }

    private static final Pattern DETAIL=Pattern.compile("(?m)^(12|6|9) marker vs minute track:.*$");
    private static final Pattern TOP_MARKER=Pattern.compile("(?m)^\\d+\\. (12|6|9) marker (?:local position|angular offset).*?$");
    private static final Pattern TOP_BODY=Pattern.compile("(?m)^\\d+\\. (12|6|9) marker body rotation .*?$");

    static String repair(Bitmap watch,String report,String modelRef){
        if(report==null||watch==null||!CanonicalGmtGeometryAnalyzer.supports(modelRef))return report;
        MarkerDiagnostic[] diagnostics=measure(watch);
        if(diagnostics==null)return report.replace("marker local position","marker angular offset");
        return rewriteReport(report,diagnostics);
    }

    /**
     * Expected bright-component centroid used for radial QC. This is deliberately separate
     * from the visual triangle anchor because a triangle's image centroid is not its geometric
     * centre. 6/9 use their own baton centre rather than the round-marker centre.
     */
    static double expectedRadiusRatio(int hour){
        if(hour==12)return Gmt126710BlnrMaster.TRI_DETECTION_CENTER_R;
        if(hour==6||hour==9)return Gmt126710BlnrMaster.BATON_CENTER_R;
        return Gmt126710BlnrMaster.ROUND_CENTER_R;
    }

    static double visualRadiusRatio(int hour){
        if(hour==12)return Gmt126710BlnrMaster.TRI_CENTER_R;
        if(hour==6||hour==9)return Gmt126710BlnrMaster.BATON_CENTER_R;
        return Gmt126710BlnrMaster.ROUND_CENTER_R;
    }

    static double radialOffsetPctR(double normalizedRadius,int hour){
        return 100.0*(normalizedRadius-expectedRadiusRatio(hour));
    }

    static String rewriteReport(String report,MarkerDiagnostic[] diagnostics){
        String out=report==null?"":report.replace("marker local position","marker angular offset");

        // Remove every legacy 12/6/9 value first. If the replacement detector fails, no stale
        // legacy number is allowed to leak back into the report.
        out=DETAIL.matcher(out).replaceAll("");
        out=TOP_MARKER.matcher(out).replaceAll("");
        out=TOP_BODY.matcher(out).replaceAll("");

        StringBuilder block=new StringBuilder();
        for(int h:new int[]{12,6,9}){
            MarkerDiagnostic d=find(diagnostics,h);
            if(d!=null)block.append(detailLine(d)).append('\n');
        }
        block.append("Marker radial sign: positive = outward/high, negative = inward/low. ")
                .append("Radial values are relative to the current genuine-derived marker centroid datum and remain diagnostic until genuine-cohort calibration is complete.\n");

        if(out.contains("Extended QC checks\n")){
            out=out.replaceFirst("Extended QC checks\\n",
                    Matcher.quoteReplacement("Extended QC checks\n"+block));
        }else{
            out += "\nExtended QC checks\n"+block;
        }

        out=renumberTopFindings(out);
        return out.replaceAll("(?m)^[ \\t]+$","").replaceAll("\\n{3,}","\n\n");
    }

    private static String detailLine(MarkerDiagnostic d){
        if(!d.measured){
            return String.format(Locale.US,
                    "%d marker: not confidently isolated inside projected master ROI; no positional/orientation value reported.",d.hour);
        }
        String source=d.minuteTrackAnchored?"":" [minute-track pose unavailable]";
        String body=Double.isFinite(d.bodyRotationDeg)?
                String.format(Locale.US,", body rotation %+.2f°",d.bodyRotationDeg):
                ", body rotation unavailable (component isolation confidence low)";
        return String.format(Locale.US,
                "%d marker vs minute track: angular offset %+.2f°, radial %+.2f%% R vs calibrated marker datum%s%s",
                d.hour,d.angularDeg,d.radialPctR,body,source);
    }

    private static String renumberTopFindings(String report){
        String[] lines=report.split("\\n",-1);
        boolean inTop=false;int n=1;
        StringBuilder out=new StringBuilder();
        for(String line:lines){
            if(line.equals("Top QC findings")||line.startsWith("Top QC observations")){
                inTop=true;n=1;
            }else if(line.equals("Extended QC checks"))inTop=false;
            if(inTop&&line.matches("^\\d+\\. .+"))line=line.replaceFirst("^\\d+\\. ",n++ + ". ");
            if(out.length()>0)out.append('\n');out.append(line);
        }
        return out.toString();
    }

    private static MarkerDiagnostic find(MarkerDiagnostic[] ds,int hour){
        if(ds==null)return null;
        for(MarkerDiagnostic d:ds)if(d!=null&&d.hour==hour)return d;
        return null;
    }

    private static MarkerDiagnostic[] measure(Bitmap watch){
        Mat rgba=new Mat(),bgr=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(watch,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);

            Method detect=method("detectDial",Mat.class);
            Object dial=detect.invoke(null,bgr);if(dial==null)return null;
            double seedX=num(dial,"x"),seedY=num(dial,"y"),seedR=num(dial,"r");
            if(!(seedR>40.0))return null;

            MinuteTrackDialFinder.Result pose=MinuteTrackDialFinder.find(edges,seedX,seedY,seedR);
            if(pose==null||pose.dialEllipse==null||!pose.topPhaseAccepted)return null;
            RotatedRect ellipse=pose.dialEllipse;
            double roll=pose.rollDeg;
            double dialRadiusPx=(Math.max(ellipse.size.width,ellipse.size.height)
                    +Math.min(ellipse.size.width,ellipse.size.height))/4.0;

            MarkerDiagnostic[] out=new MarkerDiagnostic[13];
            for(int hour:new int[]{12,6,9}){
                Candidate c=measureProjectedMarker(gray,ellipse,roll,dialRadiusPx,hour);
                if(c==null){
                    out[hour]=new MarkerDiagnostic(hour,Double.NaN,Double.NaN,Double.NaN,true,false);
                    continue;
                }

                Point corrected=PerspectiveGmtOverlay.undoEllipseDistortion(ellipse,
                        c.center.x-ellipse.center.x,c.center.y-ellipse.center.y);
                double normalizedRadius=Math.hypot(corrected.x,corrected.y);
                double correctedClock=QcExtendedMath.clockAngleDeg(0,0,corrected.x,corrected.y);
                double expectedClock=wrap360(QcExtendedMath.hourAngleDeg(hour)+roll);
                double angular=QcExtendedMath.wrap180(correctedClock-expectedClock);
                double radial=radialOffsetPctR(normalizedRadius,hour);

                // These are detector sanity limits, not QC tolerances. Anything outside them is
                // overwhelmingly more likely to be the wrong image structure than a watch defect.
                if(Math.abs(angular)>4.0||Math.abs(radial)>8.0){
                    out[hour]=new MarkerDiagnostic(hour,Double.NaN,Double.NaN,Double.NaN,true,false);
                    continue;
                }

                double body=(Math.abs(c.rotationDeg)<=12.0&&dialRadiusPx>=55.0)?c.rotationDeg:Double.NaN;
                out[hour]=new MarkerDiagnostic(hour,angular,radial,body,true,true);
            }
            return out;
        }catch(Throwable ignored){return null;}
        finally{rgba.release();bgr.release();gray.release();blur.release();edges.release();}
    }

    /**
     * Isolate one marker inside a small ROI projected from its genuine-derived detection datum.
     * The marker under test never moves the ROI or the pose. Shape/area/centroid bounds are
     * detection sanity checks only; they intentionally cause a safe "not measurable" result on
     * ambiguous images.
     */
    private static Candidate measureProjectedMarker(Mat gray,RotatedRect ellipse,double roll,
                                                     double dialRadiusPx,int hour){
        double radius=expectedRadiusRatio(hour);
        double angle=Gmt126710BlnrMaster.angleForHour(hour);
        Basis basis=basisAt(ellipse,roll,radius,angle);
        if(basis==null||Math.abs(basis.det)<1e-8)return null;

        double radialMin=hour==12?-0.17:-0.13;
        double radialMax=hour==12? 0.11: 0.13;
        double tangentHalf=hour==12?0.10:0.075;
        double minAreaNorm=hour==12?0.010:0.008;
        double maxAreaNorm=hour==12?0.060:0.040;
        double targetAreaNorm=hour==12?0.025:0.016;

        double maxBasis=Math.max(Math.hypot(basis.rx,basis.ry),Math.hypot(basis.tx,basis.ty));
        int extent=(int)Math.ceil(maxBasis*0.20+5.0);
        int x0=Math.max(0,(int)Math.floor(basis.center.x-extent));
        int x1=Math.min(gray.cols()-1,(int)Math.ceil(basis.center.x+extent));
        int y0=Math.max(0,(int)Math.floor(basis.center.y-extent));
        int y1=Math.min(gray.rows()-1,(int)Math.ceil(basis.center.y+extent));
        if(x1<=x0||y1<=y0)return null;

        Mat mask=Mat.zeros(y1-y0+1,x1-x0+1,CvType.CV_8UC1);
        try{
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                Point local=basis.local(x,y);
                if(local.x<radialMin||local.x>radialMax||Math.abs(local.y)>tangentHalf)continue;
                double[] v=gray.get(y,x);if(v==null||v[0]<150.0)continue;
                mask.put(y-y0,x-x0,255.0);
            }

            List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();
            try{
                Imgproc.findContours(mask,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
                Candidate best=null;double bestScore=Double.POSITIVE_INFINITY;
                for(MatOfPoint contour:contours){
                    double area=Math.abs(Imgproc.contourArea(contour));
                    double areaNorm=area/Math.max(1.0,dialRadiusPx*dialRadiusPx);
                    if(areaNorm<minAreaNorm||areaNorm>maxAreaNorm)continue;
                    Moments m=Imgproc.moments(contour);
                    double m00=m.get_m00();
                    if(!(m00>0.0))continue;
                    Point center=new Point(x0+m.get_m10()/m00,y0+m.get_m01()/m00);
                    Point local=basis.local(center.x,center.y);
                    if(Math.abs(local.x)>0.080||Math.abs(local.y)>0.060)continue;

                    double mu20=m.get_mu20(),mu02=m.get_mu02(),mu11=m.get_mu11();
                    double trace=mu20+mu02;
                    double disc=Math.sqrt(Math.max(0.0,(mu20-mu02)*(mu20-mu02)+4.0*mu11*mu11));
                    double l1=(trace+disc)/2.0,l2=Math.max(1e-9,(trace-disc)/2.0);
                    double anisotropy=l1/l2;
                    double axis=Math.toDegrees(0.5*Math.atan2(2.0*mu11,mu20-mu02));
                    double expectedAxis=Math.toDegrees(Math.atan2(basis.ry,basis.rx));
                    double rotation=QcExtendedMath.smallestAxisError(axis,expectedAxis);
                    double minAnisotropy=hour==12?1.35:2.0;
                    if(anisotropy<minAnisotropy||Math.abs(rotation)>25.0)continue;

                    double score=4.0*Math.hypot(local.x,local.y)
                            +1.5*Math.abs(areaNorm-targetAreaNorm);
                    if(score<bestScore){
                        bestScore=score;
                        best=new Candidate(center,local.x,local.y,areaNorm,rotation,anisotropy);
                    }
                }
                return best;
            }finally{
                hierarchy.release();for(MatOfPoint c:contours)c.release();
            }
        }finally{mask.release();}
    }

    /** Projected radial/tangential basis in pixels per one normalized dial-radius unit. */
    private static Basis basisAt(RotatedRect ellipse,double roll,double radius,double angle){
        double ca=Math.cos(angle),sa=Math.sin(angle);
        Point center=map(ellipse,roll,radius*ca,radius*sa);
        double eps=0.04;
        Point r0=map(ellipse,roll,(radius-eps)*ca,(radius-eps)*sa);
        Point r1=map(ellipse,roll,(radius+eps)*ca,(radius+eps)*sa);
        double tx=-sa,ty=ca;
        Point t0=map(ellipse,roll,radius*ca-eps*tx,radius*sa-eps*ty);
        Point t1=map(ellipse,roll,radius*ca+eps*tx,radius*sa+eps*ty);
        return new Basis(center,(r1.x-r0.x)/(2.0*eps),(r1.y-r0.y)/(2.0*eps),
                (t1.x-t0.x)/(2.0*eps),(t1.y-t0.y)/(2.0*eps));
    }

    private static Point map(RotatedRect e,double rollDeg,double x,double y){
        double roll=Math.toRadians(rollDeg),cr=Math.cos(roll),sr=Math.sin(roll);
        double xr=cr*x-sr*y,yr=sr*x+cr*y;
        double axis=Math.toRadians(e.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double localX=ca*xr+sa*yr,localY=-sa*xr+ca*yr;
        double rx=e.size.width*0.5,ry=e.size.height*0.5;
        double sx=rx*localX,sy=ry*localY;
        return new Point(e.center.x+ca*sx-sa*sy,e.center.y+sa*sx+ca*sy);
    }

    private static double wrap360(double d){double x=d%360.0;if(x<0)x+=360.0;return x;}
    private static Method method(String n,Class<?>...t)throws Exception{
        Method m=WatchAlignCoreV7.class.getDeclaredMethod(n,t);m.setAccessible(true);return m;
    }
    private static Object field(Object o,String n)throws Exception{
        Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);
    }
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private GmtMarkerQcRepair(){}
}
