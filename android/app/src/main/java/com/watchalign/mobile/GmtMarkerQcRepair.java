package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repairs the shaped-marker part of the legacy extended QC report for 126710-family GMTs.
 *
 * The accepted minute-track pose is the datum. Angular offset, radial offset and marker-body
 * rotation are deliberately separate quantities so a high/low marker cannot be hidden by an
 * angular-only measurement. Radial values remain diagnostic until the visual-master constants
 * have been calibrated against a sufficiently large genuine cohort.
 */
final class GmtMarkerQcRepair {
    static final class MarkerDiagnostic {
        final int hour;
        final double angularDeg;
        final double radialPctR;
        final double bodyRotationDeg;
        final boolean minuteTrackAnchored;

        MarkerDiagnostic(int hour,double angularDeg,double radialPctR,
                         double bodyRotationDeg,boolean minuteTrackAnchored){
            this.hour=hour;
            this.angularDeg=angularDeg;
            this.radialPctR=radialPctR;
            this.bodyRotationDeg=bodyRotationDeg;
            this.minuteTrackAnchored=minuteTrackAnchored;
        }
    }

    private static final Pattern DETAIL=Pattern.compile("(?m)^(12|6|9) marker vs minute track:.*$");
    private static final Pattern TOP_MARKER=Pattern.compile("(?m)^(\\d+)\\. (12|6|9) marker (?:local position|angular offset).*?$");
    private static final Pattern TOP_BODY=Pattern.compile("(?m)^(\\d+)\\. (12|6|9) marker body rotation .*?$");

    static String repair(Bitmap watch,String report,String modelRef){
        if(report==null||watch==null||!CanonicalGmtGeometryAnalyzer.supports(modelRef))return report;
        MarkerDiagnostic[] diagnostics=measure(watch);
        if(diagnostics==null)return report.replace("marker local position","marker angular offset");
        return rewriteReport(report,diagnostics);
    }

    static double expectedRadiusRatio(int hour){
        return hour==12?Gmt126710BlnrMaster.TRI_CENTER_R:Gmt126710BlnrMaster.MARKER_CENTER_R;
    }

    static double radialOffsetPctR(double normalizedRadius,int hour){
        return 100.0*(normalizedRadius-expectedRadiusRatio(hour));
    }

    static String rewriteReport(String report,MarkerDiagnostic[] diagnostics){
        String out=report==null?"":report.replace("marker local position","marker angular offset");
        for(int hour:new int[]{12,6,9}){
            MarkerDiagnostic d=find(diagnostics,hour);
            if(d==null)continue;
            Pattern detailForHour=Pattern.compile("(?m)^"+hour+" marker vs minute track:.*$");
            Matcher dm=detailForHour.matcher(out);
            if(dm.find())out=dm.replaceFirst(Matcher.quoteReplacement(detailLine(d)));

            Pattern topForHour=Pattern.compile("(?m)^(\\d+)\\. "+hour+" marker angular offset .*?$");
            Matcher tm=topForHour.matcher(out);
            if(tm.find()){
                if(QcExtendedMath.localTrackSeverity(d.angularDeg)>0){
                    String replacement=tm.group(1)+". "+topFinding(d);
                    out=tm.replaceFirst(Matcher.quoteReplacement(replacement));
                }else out=tm.replaceFirst("");
            }

            Pattern bodyForHour=Pattern.compile("(?m)^(\\d+)\\. "+hour+" marker body rotation .*?$");
            Matcher bm=bodyForHour.matcher(out);
            if(bm.find())out=bm.replaceFirst("");
        }

        if(!DETAIL.matcher(out).find()&&out.contains("Extended QC checks")){
            StringBuilder insertion=new StringBuilder("Extended QC checks\n");
            for(int h:new int[]{12,6,9}){
                MarkerDiagnostic d=find(diagnostics,h);
                if(d!=null)insertion.append(detailLine(d)).append('\n');
            }
            out=out.replaceFirst("Extended QC checks\\n",Matcher.quoteReplacement(insertion.toString()));
        }

        String note="Marker radial sign: positive = outward/high, negative = inward/low. " +
                "Radial values are relative to the current visual master and are diagnostic only until genuine-cohort calibration is complete.";
        if(!out.contains("Marker radial sign:")){
            Matcher nine=Pattern.compile("(?m)^9 marker vs minute track:.*$").matcher(out);
            if(nine.find())out=nine.replaceFirst(Matcher.quoteReplacement(nine.group()+"\n"+note));
        }

        out=renumberTopFindings(out);
        return out.replaceAll("(?m)^[ \\t]+$","").replaceAll("\\n{3,}","\n\n");
    }

    private static String detailLine(MarkerDiagnostic d){
        String source=d.minuteTrackAnchored?"":" [minute-track pose unavailable; fitted-grid fallback]";
        String body=Double.isFinite(d.bodyRotationDeg)?
                String.format(Locale.US,", body rotation %+.2f°",d.bodyRotationDeg):
                ", body rotation unavailable (component isolation confidence low)";
        return String.format(Locale.US,
                "%d marker vs minute track: angular offset %+.2f°, radial %+.2f%% R vs visual master%s%s",
                d.hour,d.angularDeg,d.radialPctR,body,source);
    }

    private static String topFinding(MarkerDiagnostic d){
        return String.format(Locale.US,"%d marker angular offset %+.2f° vs minute track%s",
                d.hour,d.angularDeg,d.minuteTrackAnchored?"":" [fitted-grid fallback]");
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
            double cx=num(dial,"x"),cy=num(dial,"y"),dr=num(dial,"r");
            if(!(dr>40.0))return null;
            Method measure=method("measureMarkerSet",Mat.class,dial.getClass());
            Object set=measure.invoke(null,bgr,dial);
            double global=num(set,"globalRotation");if(!Double.isFinite(global))global=0.0;
            @SuppressWarnings("unchecked") List<Object> markers=(List<Object>)field(set,"markers");
            if(markers==null)return null;

            MinuteTrackDialFinder.Result pose=MinuteTrackDialFinder.find(edges,cx,cy,dr);
            RotatedRect ellipse=pose==null?null:pose.dialEllipse;
            double roll=pose==null?0.0:pose.rollDeg;
            double dialRadiusPx=ellipse==null?dr:(Math.max(ellipse.size.width,ellipse.size.height)
                    +Math.min(ellipse.size.width,ellipse.size.height))/4.0;

            MarkerDiagnostic[] out=new MarkerDiagnostic[13];
            for(int hour:new int[]{12,6,9}){
                Object marker=findHour(markers,hour);if(marker==null)continue;
                double legacyAngular=num(marker,"angular"),actualR=num(marker,"radius");
                double imageClock=QcExtendedMath.hourAngleDeg(hour)+global+legacyAngular;
                Point center=polar(cx,cy,actualR,imageClock);
                double angular=legacyAngular;
                double normalizedRadius=actualR/Math.max(1.0,dr);
                double bodyAxisExpected=imageClock-90.0;
                boolean anchored=false;

                if(ellipse!=null){
                    Point corrected=PerspectiveGmtOverlay.undoEllipseDistortion(ellipse,
                            center.x-ellipse.center.x,center.y-ellipse.center.y);
                    normalizedRadius=Math.hypot(corrected.x,corrected.y);
                    double correctedClock=QcExtendedMath.clockAngleDeg(0,0,corrected.x,corrected.y);
                    double expectedClock=wrap360(QcExtendedMath.hourAngleDeg(hour)+roll);
                    angular=QcExtendedMath.wrap180(correctedClock-expectedClock);
                    double canonicalClock=wrap360(correctedClock-roll);
                    bodyAxisExpected=projectedRadialAxisDeg(ellipse,roll,canonicalClock,normalizedRadius);
                    anchored=pose.topPhaseAccepted;
                }

                double radial=radialOffsetPctR(normalizedRadius,hour);
                double body=hour==12?
                        componentAxisError(bgr,center,dialRadiusPx,bodyAxisExpected,-0.18,0.13,0.11,1.18):
                        componentAxisError(bgr,center,dialRadiusPx,bodyAxisExpected,-0.13,0.13,0.065,1.25);
                out[hour]=new MarkerDiagnostic(hour,angular,radial,body,anchored);
            }
            return out;
        }catch(Throwable ignored){return null;}
        finally{rgba.release();bgr.release();gray.release();blur.release();edges.release();}
    }

    /**
     * PCA only inside a marker-shaped window aligned to the expected projected radial axis.
     * This avoids the old square ROI at 12 o'clock, which admitted hands/coronet/minute-track
     * pixels and produced impossible values such as -48 degrees for a visually upright triangle.
     */
    private static double componentAxisError(Mat bgr,Point center,double dialRadiusPx,
                                             double expectedAxisDeg,double radialMinR,
                                             double radialMaxR,double tangentialHalfR,
                                             double minAnisotropy){
        Mat gray=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            double a=Math.toRadians(expectedAxisDeg),ux=Math.cos(a),uy=Math.sin(a);
            double vx=-uy,vy=ux;
            double extent=dialRadiusPx*Math.max(Math.max(Math.abs(radialMinR),Math.abs(radialMaxR)),tangentialHalfR)+3.0;
            int x0=(int)Math.max(0,Math.floor(center.x-extent)),x1=(int)Math.min(gray.cols()-1,Math.ceil(center.x+extent));
            int y0=(int)Math.max(0,Math.floor(center.y-extent)),y1=(int)Math.min(gray.rows()-1,Math.ceil(center.y+extent));
            double sw=0,mx=0,my=0;int count=0;
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                double dx=x-center.x,dy=y-center.y;
                double radial=(dx*ux+dy*uy)/dialRadiusPx;
                double tangent=(dx*vx+dy*vy)/dialRadiusPx;
                if(radial<radialMinR||radial>radialMaxR||Math.abs(tangent)>tangentialHalfR)continue;
                double value=gray.get(y,x)[0];if(value<155.0)continue;
                double w=Math.max(1.0,value-145.0);sw+=w;mx+=w*x;my+=w*y;count++;
            }
            if(sw<220.0||count<18)return Double.NaN;
            mx/=sw;my/=sw;
            double cxx=0,cyy=0,cxy=0;
            for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
                double dx0=x-center.x,dy0=y-center.y;
                double radial=(dx0*ux+dy0*uy)/dialRadiusPx;
                double tangent=(dx0*vx+dy0*vy)/dialRadiusPx;
                if(radial<radialMinR||radial>radialMaxR||Math.abs(tangent)>tangentialHalfR)continue;
                double value=gray.get(y,x)[0];if(value<155.0)continue;
                double w=Math.max(1.0,value-145.0),dx=x-mx,dy=y-my;
                cxx+=w*dx*dx;cyy+=w*dy*dy;cxy+=w*dx*dy;
            }
            double trace=cxx+cyy,disc=Math.sqrt(Math.max(0.0,(cxx-cyy)*(cxx-cyy)+4.0*cxy*cxy));
            double l1=(trace+disc)/2.0,l2=Math.max(1e-9,(trace-disc)/2.0);
            if(!(l1/l2>=minAnisotropy))return Double.NaN;
            double axis=Math.toDegrees(0.5*Math.atan2(2.0*cxy,cxx-cyy));
            return QcExtendedMath.smallestAxisError(axis,expectedAxisDeg);
        }finally{gray.release();}
    }

    private static double projectedRadialAxisDeg(RotatedRect ellipse,double rollDeg,
                                                  double canonicalClockDeg,double radius){
        double math=Math.toRadians(canonicalClockDeg-90.0);
        double r0=Math.max(0.05,radius-0.04),r1=radius+0.04;
        Point p0=map(ellipse,rollDeg,r0*Math.cos(math),r0*Math.sin(math));
        Point p1=map(ellipse,rollDeg,r1*Math.cos(math),r1*Math.sin(math));
        return Math.toDegrees(Math.atan2(p1.y-p0.y,p1.x-p0.x));
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
    private static Point polar(double cx,double cy,double r,double deg){
        double a=Math.toRadians(deg);return new Point(cx+Math.sin(a)*r,cy-Math.cos(a)*r);
    }
    private static Object findHour(List<Object> list,int h)throws Exception{
        for(Object m:list)if((int)Math.round(num(m,"hour"))==h)return m;return null;
    }
    private static Method method(String n,Class<?>...t)throws Exception{
        Method m=WatchAlignCoreV7.class.getDeclaredMethod(n,t);m.setAccessible(true);return m;
    }
    private static Object field(Object o,String n)throws Exception{
        Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);
    }
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private GmtMarkerQcRepair(){}
}
