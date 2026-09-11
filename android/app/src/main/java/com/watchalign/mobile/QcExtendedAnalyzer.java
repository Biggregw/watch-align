package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adds QC checks commonly used by RepTimeQC reviewers. Fine-grained checks are
 * perspective gated and are deliberately advisory when single-photo geometry
 * is not reliable enough for a strong verdict.
 */
final class QcExtendedAnalyzer {
    static final class Result {
        final Bitmap annotated;
        final String report;
        Result(Bitmap annotated, String report){this.annotated=annotated;this.report=report;}
    }

    static Result analyse(Bitmap watch, String modelRef) {
        Mat src=new Mat();
        try {
            Utils.bitmapToMat(watch,src); Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class);
            Object dial=detect.invoke(null,src);
            if(dial==null) return new Result(watch.copy(Bitmap.Config.ARGB_8888,false),"Extended QC unavailable: dial geometry not verified.");
            Method measure=method("measureMarkerSet",Mat.class,dial.getClass());
            Method perspective=method("perspectiveEquivalent",Mat.class,dial.getClass());
            Object set=measure.invoke(null,src,dial);
            double tilt=((Number)perspective.invoke(null,src,dial)).doubleValue();
            boolean fine=QcExtendedMath.perspectiveAllowsFineQc(tilt);
            double cx=num(dial,"x"), cy=num(dial,"y"), dr=num(dial,"r");
            double global=num(set,"globalRotation"), median=num(set,"medianRadius");
            @SuppressWarnings("unchecked") List<Object> markers=(List<Object>)field(set,"markers");

            Mat out=src.clone();
            Scalar green=new Scalar(120,220,120), amber=new Scalar(40,180,255), red=new Scalar(70,70,255);
            Scalar neutral=new Scalar(180,180,180), magenta=new Scalar(220,80,220), cyan=new Scalar(220,210,70);
            StringBuilder r=new StringBuilder("\n\nExtended QC checks\n");

            // Local marker-to-minute-track and marker body orientation checks.
            for(int h:new int[]{12,6,9}) {
                Object m=findHour(markers,h);
                if(m==null) continue;
                double angular=num(m,"angular");
                double actualR=num(m,"radius");
                double idealDeg=(h==12?0:h*30.0)+global;
                double local=angular;
                int sev=QcExtendedMath.localTrackSeverity(local);
                Scalar s=!fine?neutral:sev==0?green:sev==1?amber:red;
                Point p=polar(cx,cy,actualR,idealDeg+angular);
                double orient=markerAxisError(src,p,Math.max(12.0,dr*0.11),idealDeg);
                boolean orientOk=Double.isFinite(orient);
                if(orientOk) {
                    int os=QcExtendedMath.orientationSeverity(orient);
                    Scalar so=!fine?neutral:os==0?green:os==1?amber:red;
                    double a=Math.toRadians(idealDeg+orient);
                    Point a0=new Point(p.x-Math.sin(a)*dr*0.07,p.y+Math.cos(a)*dr*0.07);
                    Point a1=new Point(p.x+Math.sin(a)*dr*0.07,p.y-Math.cos(a)*dr*0.07);
                    Imgproc.line(out,a0,a1,so,2,Imgproc.LINE_AA,0);
                }
                r.append(String.format(Locale.US,"%d marker vs minute track: position %+4.2f°",h,local));
                if(orientOk) r.append(String.format(Locale.US,", body rotation %+4.2f°",orient));
                r.append(fine?"\n":" (advisory: perspective too high for fine grading)\n");
            }

            // Bezel/pip 12 alignment: brightest/strongest local feature in the 12 annulus.
            double pipOffset=bezelTwelveOffset(src,cx,cy,dr,global);
            if(Double.isFinite(pipOffset)) {
                int ps=QcExtendedMath.localTrackSeverity(pipOffset);
                Scalar s=!fine?neutral:ps==0?green:ps==1?amber:red;
                double rr=dr*1.23, a=Math.toRadians(global+pipOffset);
                Point pp=polar(cx,cy,rr,global+pipOffset);
                Imgproc.circle(out,pp,8,s,2,Imgproc.LINE_AA,0);
                r.append(String.format(Locale.US,"Bezel/pip 12 alignment: %+4.2f° relative to dial 12%s\n",pipOffset,fine?"":" (advisory)"));
            } else r.append("Bezel/pip 12 alignment: not confidently measurable in this photo.\n");

            // Date / cyclops region for GMT only. We measure the date aperture and numeral ink centroid,
            // not lens magnification, because reflections can make automatic cyclops edge detection unsafe.
            if("126710BLNR".equals(modelRef)) {
                DateResult d=measureDate(src,cx,cy,dr);
                if(d!=null) {
                    Scalar s=!fine?neutral:QcExtendedMath.dateCenterSeverity(d.xPct,d.yPct)==0?green:QcExtendedMath.dateCenterSeverity(d.xPct,d.yPct)==1?amber:red;
                    Imgproc.rectangle(out,d.box.tl(),d.box.br(),s,2,Imgproc.LINE_AA,0);
                    Imgproc.drawMarker(out,new Point(d.inkX,d.inkY),s,Imgproc.MARKER_CROSS,12,2,Imgproc.LINE_AA);
                    r.append(String.format(Locale.US,"Date numeral centring in aperture: horizontal %+4.1f%%, vertical %+4.1f%%%s\n",d.xPct,d.yPct,fine?"":" (advisory)"));
                    r.append("Cyclops: aperture zone located; magnification/optical centring still requires visual confirmation because reflections can move detected lens edges.\n");
                } else r.append("Date/cyclops: aperture not confidently isolated; visual confirmation required.\n");
            }

            // Rehaut and dial-print zones are shown as inspection bands rather than fabricated automatic verdicts.
            int x0=(int)Math.max(0,cx-dr*0.78), x1=(int)Math.min(src.cols()-1,cx+dr*0.78);
            int y0=(int)Math.max(0,cy-dr*0.93), y1=(int)Math.min(src.rows()-1,cy-dr*0.61);
            if(x1>x0&&y1>y0) Imgproc.rectangle(out,new Point(x0,y0),new Point(x1,y1),magenta,1,Imgproc.LINE_AA,0);
            r.append("Rehaut/crown-at-12: highlighted inspection band; no automatic pass/fail because engraving visibility and genuine tolerance vary with focus and angle.\n");

            Rect textZone=safeRect((int)(cx-dr*0.45),(int)(cy-dr*0.25),(int)(dr*0.90),(int)(dr*0.62),src.cols(),src.rows());
            if(textZone!=null) {
                double sharp=textSharpness(src.submat(textZone));
                Imgproc.rectangle(out,textZone.tl(),textZone.br(),cyan,1,Imgproc.LINE_AA,0);
                r.append(String.format(Locale.US,"Dial-print zone sharpness: %.1f (use as photo-quality aid only; typography/authenticity is not inferred).\n",sharp));
            }

            // SEL inspection zones around lower lug/end-link interfaces. We mark both and quantify dark-gap evidence only.
            double leftGap=selGapEvidence(src,cx-dr*0.63,cy+dr*1.05,dr*0.17,dr*0.20);
            double rightGap=selGapEvidence(src,cx+dr*0.63,cy+dr*1.05,dr*0.17,dr*0.20);
            drawSelBox(out,cx-dr*0.63,cy+dr*1.05,dr, leftGap);
            drawSelBox(out,cx+dr*0.63,cy+dr*1.05,dr, rightGap);
            r.append(String.format(Locale.US,"SEL dark-gap evidence: left %.2f, right %.2f (lighting-sensitive; inspect highlighted zones visually).\n",leftGap,rightGap));

            r.append("Lume consistency: not graded from a normal-light QC photo. Use a dedicated lume shot before any lume verdict.\n");
            if(!fine) r.append("Fine QC is advisory because perspective distortion is too high for strong local geometry claims.\n");

            Bitmap b=Bitmap.createBitmap(out.cols(),out.rows(),Bitmap.Config.ARGB_8888);
            Mat rgba=new Mat(); Imgproc.cvtColor(out,rgba,Imgproc.COLOR_BGR2RGBA); Utils.matToBitmap(rgba,b); rgba.release(); out.release();
            return new Result(b,r.toString());
        } catch(Throwable e) {
            return new Result(watch.copy(Bitmap.Config.ARGB_8888,false),"Extended QC unavailable: "+e.getClass().getSimpleName()+".");
        } finally {src.release();}
    }

    private static double markerAxisError(Mat bgr, Point center, double half, double expectedRadialDeg) {
        Mat gray=new Mat(); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
        int x0=(int)Math.max(0,center.x-half), x1=(int)Math.min(gray.cols()-1,center.x+half);
        int y0=(int)Math.max(0,center.y-half), y1=(int)Math.min(gray.rows()-1,center.y+half);
        double sw=0,mx=0,my=0;
        for(int y=y0;y<=y1;y+=2) for(int x=x0;x<=x1;x+=2) {double v=gray.get(y,x)[0]; if(v<150)continue; double w=v-140; sw+=w;mx+=w*x;my+=w*y;}
        if(sw<200){gray.release();return Double.NaN;} mx/=sw;my/=sw;
        double cxx=0,cyy=0,cxy=0;
        for(int y=y0;y<=y1;y+=2) for(int x=x0;x<=x1;x+=2){double v=gray.get(y,x)[0];if(v<150)continue;double w=v-140,dx=x-mx,dy=y-my;cxx+=w*dx*dx;cyy+=w*dy*dy;cxy+=w*dx*dy;}
        gray.release();
        double axis=Math.toDegrees(0.5*Math.atan2(2*cxy,cxx-cyy)); // image x-axis convention
        double radialImage=expectedRadialDeg-90.0; // clockwise from 12 -> mathematical-ish image x convention
        return QcExtendedMath.smallestAxisError(axis,radialImage);
    }

    private static double bezelTwelveOffset(Mat bgr,double cx,double cy,double dr,double global) {
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);
        double best=-1,bestOff=Double.NaN;
        for(double off=-8;off<=8;off+=0.25){double a=global+off;Point p=polar(cx,cy,dr*1.23,a);double sum=0;int n=0;
            for(int yy=-4;yy<=4;yy+=2)for(int xx=-4;xx<=4;xx+=2){int x=(int)Math.round(p.x+xx),y=(int)Math.round(p.y+yy);if(x<0||x>=gray.cols()||y<0||y>=gray.rows())continue;sum+=gray.get(y,x)[0];n++;}
            if(n>0&&sum/n>best){best=sum/n;bestOff=off;}}
        gray.release();return best>=115?bestOff:Double.NaN;
    }

    private static final class DateResult {Rect box;double inkX,inkY,xPct,yPct;DateResult(Rect b,double x,double y,double xp,double yp){box=b;inkX=x;inkY=y;xPct=xp;yPct=yp;}}
    private static DateResult measureDate(Mat bgr,double cx,double cy,double dr) {
        Rect roi=safeRect((int)(cx+dr*0.30),(int)(cy-dr*0.34),(int)(dr*0.75),(int)(dr*0.68),bgr.cols(),bgr.rows());if(roi==null)return null;
        Mat gray=new Mat();Imgproc.cvtColor(bgr.submat(roi),gray,Imgproc.COLOR_BGR2GRAY);Mat bin=new Mat();Imgproc.threshold(gray,bin,145,255,Imgproc.THRESH_BINARY);
        List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();Imgproc.findContours(bin,cs,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        Rect best=null;double bestScore=0;
        for(MatOfPoint c:cs){Rect q=Imgproc.boundingRect(c);double ar=q.width/(double)Math.max(1,q.height),area=q.area();if(ar<1.0||ar>2.8||area<dr*dr*0.015||area>dr*dr*0.18)continue;double sc=area*(1.0-Math.min(0.8,Math.abs(ar-1.55)/2.0));if(sc>bestScore){bestScore=sc;best=q;}}
        for(MatOfPoint c:cs)c.release();hier.release();bin.release();
        if(best==null){gray.release();return null;}
        Rect inner=new Rect(Math.min(gray.cols()-1,best.x+Math.max(1,best.width/8)),Math.min(gray.rows()-1,best.y+Math.max(1,best.height/8)),Math.max(1,best.width-Math.max(2,best.width/4)),Math.max(1,best.height-Math.max(2,best.height/4)));
        inner.width=Math.min(inner.width,gray.cols()-inner.x);inner.height=Math.min(inner.height,gray.rows()-inner.y);
        double sw=0,sx=0,sy=0;
        for(int y=inner.y;y<inner.y+inner.height;y++)for(int x=inner.x;x<inner.x+inner.width;x++){double v=gray.get(y,x)[0];if(v>120)continue;double w=121-v;sw+=w;sx+=w*x;sy+=w*y;}
        gray.release();if(sw<50)return null;double ix=sx/sw,iy=sy/sw;double bx=best.x+best.width/2.0,by=best.y+best.height/2.0;
        double xp=(ix-bx)/(best.width/2.0)*100.0,yp=(iy-by)/(best.height/2.0)*100.0;
        Rect abs=new Rect(roi.x+best.x,roi.y+best.y,best.width,best.height);
        return new DateResult(abs,roi.x+ix,roi.y+iy,xp,yp);
    }

    private static double textSharpness(Mat roi){Mat g=new Mat(),lap=new Mat();Imgproc.cvtColor(roi,g,Imgproc.COLOR_BGR2GRAY);Imgproc.Laplacian(g,lap,Core.CV_64F);org.opencv.core.MatOfDouble mean=new org.opencv.core.MatOfDouble(),sd=new org.opencv.core.MatOfDouble();Core.meanStdDev(lap,mean,sd);double v=sd.get(0,0)[0];g.release();lap.release();mean.release();sd.release();return v*v;}
    private static double selGapEvidence(Mat bgr,double cx,double cy,double hw,double hh){Rect r=safeRect((int)(cx-hw),(int)(cy-hh),(int)(2*hw),(int)(2*hh),bgr.cols(),bgr.rows());if(r==null)return 0;Mat g=new Mat();Imgproc.cvtColor(bgr.submat(r),g,Imgproc.COLOR_BGR2GRAY);double dark=0,total=g.rows()*g.cols();for(int y=0;y<g.rows();y+=2)for(int x=0;x<g.cols();x+=2)if(g.get(y,x)[0]<45)dark++;double sampled=Math.ceil(g.rows()/2.0)*Math.ceil(g.cols()/2.0);g.release();return sampled>0?dark/sampled:0;}
    private static void drawSelBox(Mat out,double cx,double cy,double dr,double evidence){Scalar s=evidence>0.22?new Scalar(40,180,255):new Scalar(180,180,180);Point a=new Point(cx-dr*0.17,cy-dr*0.20),b=new Point(cx+dr*0.17,cy+dr*0.20);Imgproc.rectangle(out,a,b,s,1,Imgproc.LINE_AA,0);}
    private static Rect safeRect(int x,int y,int w,int h,int cols,int rows){x=Math.max(0,x);y=Math.max(0,y);if(x>=cols||y>=rows)return null;w=Math.min(w,cols-x);h=Math.min(h,rows-y);return w>2&&h>2?new Rect(x,y,w,h):null;}
    private static Point polar(double cx,double cy,double r,double deg){double a=Math.toRadians(deg);return new Point(cx+Math.sin(a)*r,cy-Math.cos(a)*r);}
    private static Object findHour(List<Object> list,int h)throws Exception{for(Object m:list)if((int)Math.round(num(m,"hour"))==h)return m;return null;}
    private static Method method(String n,Class<?>...t)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(n,t);m.setAccessible(true);return m;}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private QcExtendedAnalyzer(){}
}
