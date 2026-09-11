package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
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

    private static final class Finding implements Comparable<Finding> {
        final double priority;
        final String text;
        Finding(double priority, String text){this.priority=priority;this.text=text;}
        @Override public int compareTo(Finding other){return Double.compare(other.priority, priority);}
    }

    static Result analyse(Bitmap watch, String modelRef) {
        Mat src=new Mat();
        try {
            ModelCatalog.Profile profile=ModelCatalog.require(modelRef);
            Utils.bitmapToMat(watch,src); Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class);
            Object dial=detect.invoke(null,src);
            if(dial==null) return new Result(watch.copy(Bitmap.Config.ARGB_8888,false),"Extended QC unavailable: dial geometry not verified.");
            Method measure=method("measureMarkerSet",Mat.class,dial.getClass());
            Method perspective=method("perspectiveEquivalent",Mat.class,dial.getClass());
            Object set=measure.invoke(null,src,dial);
            double tilt=((Number)perspective.invoke(null,src,dial)).doubleValue();
            boolean fine=QcExtendedMath.perspectiveAllowsFineQc(tilt);
            String fineReason=QcExtendedMath.fineQcReason(tilt);
            String advisorySuffix=fine?"":" (advisory: "+fineReason+")";
            double cx=num(dial,"x"), cy=num(dial,"y"), dr=num(dial,"r");
            double global=num(set,"globalRotation");
            if(!Double.isFinite(global)) global=0.0;
            @SuppressWarnings("unchecked") List<Object> markers=(List<Object>)field(set,"markers");

            Mat out=src.clone();
            Scalar green=new Scalar(120,220,120), amber=new Scalar(40,180,255), red=new Scalar(70,70,255);
            Scalar neutral=new Scalar(180,180,180), magenta=new Scalar(220,80,220), cyan=new Scalar(220,210,70);
            StringBuilder detail=new StringBuilder();
            List<Finding> findings=new ArrayList<>();

            for(int h:new int[]{12,6,9}) {
                Object m=findHour(markers,h);
                if(m==null) continue;
                double angular=num(m,"angular");
                double actualR=num(m,"radius");
                double idealDeg=QcExtendedMath.hourAngleDeg(h)+global;
                int sev=QcExtendedMath.localTrackSeverity(angular);
                Point p=polar(cx,cy,actualR,idealDeg+angular);
                double orient=markerAxisError(src,p,Math.max(12.0,dr*0.11),idealDeg);
                boolean orientOk=Double.isFinite(orient);

                if(sev>0) findings.add(new Finding(QcExtendedMath.findingPriority(sev,angular),
                        String.format(Locale.US,"%d marker local position %+4.2f° vs minute track%s",h,angular,advisorySuffix)));

                if(orientOk) {
                    int os=QcExtendedMath.orientationSeverity(orient);
                    Scalar so=!fine?neutral:os==0?green:os==1?amber:red;
                    double a=Math.toRadians(idealDeg+orient);
                    Point a0=new Point(p.x-Math.sin(a)*dr*0.07,p.y+Math.cos(a)*dr*0.07);
                    Point a1=new Point(p.x+Math.sin(a)*dr*0.07,p.y-Math.cos(a)*dr*0.07);
                    Imgproc.line(out,a0,a1,so,2,Imgproc.LINE_AA,0);
                    if(os>0) {
                        String strength=os==2?"strong detected deviation":"detected deviation";
                        findings.add(new Finding(QcExtendedMath.findingPriority(os,orient)+25.0,
                                String.format(Locale.US,"%d marker body rotation %+4.2f° (%s)%s",h,orient,strength,advisorySuffix)));
                    }
                }

                detail.append(String.format(Locale.US,"%d marker vs minute track: position %+4.2f°",h,angular));
                if(orientOk) detail.append(String.format(Locale.US,", body rotation %+4.2f°",orient));
                detail.append(advisorySuffix).append("\n");
            }

            if(profile.bezelTwelveCheck) {
                double pipOffset=bezelTwelveOffset(src,cx,cy,dr,global);
                if(Double.isFinite(pipOffset)) {
                    int ps=QcExtendedMath.localTrackSeverity(pipOffset);
                    Scalar s=!fine?neutral:ps==0?green:ps==1?amber:red;
                    Point pp=polar(cx,cy,dr*1.23,global+pipOffset);
                    Imgproc.circle(out,pp,8,s,2,Imgproc.LINE_AA,0);
                    detail.append(String.format(Locale.US,"Bezel/pip 12 alignment: %+4.2f° relative to dial 12%s\n",pipOffset,advisorySuffix));
                    if(ps>0) findings.add(new Finding(QcExtendedMath.findingPriority(ps,pipOffset),
                            String.format(Locale.US,"Bezel/pip 12 offset %+4.2f°%s",pipOffset,advisorySuffix)));
                } else detail.append("Bezel/pip 12 alignment: not confidently measurable in this photo.\n");
            }

            if(profile.hasDate()) {
                DateResult d=measureDate(src,cx,cy,dr,global,profile);
                if(d!=null) {
                    int numeralSeverity=QcExtendedMath.dateCenterSeverity(d.xPct,d.yPct);
                    int axisSeverity=QcExtendedMath.dateAxisSeverity(d.apertureAxisOffsetDeg);
                    int combined=Math.max(numeralSeverity,axisSeverity);
                    Scalar s=!fine?neutral:combined==0?green:combined==1?amber:red;
                    Imgproc.rectangle(out,d.apertureBox.tl(),d.apertureBox.br(),s,2,Imgproc.LINE_AA,0);
                    Imgproc.drawMarker(out,new Point(d.inkX,d.inkY),s,Imgproc.MARKER_CROSS,12,2,Imgproc.LINE_AA);
                    drawLocalAxisCue(out,cx,cy,d.apertureCenterX,d.apertureCenterY,profile.dateHour,global,dr,s);

                    detail.append(String.format(Locale.US,
                            "Date aperture alignment to %d o'clock track: %+4.2f° (%+4.2f minute-track divisions)%s\n",
                            profile.dateHour,d.apertureAxisOffsetDeg,QcExtendedMath.minuteTrackUnits(d.apertureAxisOffsetDeg),advisorySuffix));
                    detail.append(String.format(Locale.US,
                            "Date numeral centring in aperture: horizontal %+4.1f%%, vertical %+4.1f%%%s\n",
                            d.xPct,d.yPct,advisorySuffix));

                    if(axisSeverity>0) findings.add(new Finding(QcExtendedMath.findingPriority(axisSeverity,d.apertureAxisOffsetDeg)+30.0,
                            String.format(Locale.US,"Date aperture %+4.2f° (%+4.2f track divisions) from the %d o'clock axis%s",
                                    d.apertureAxisOffsetDeg,QcExtendedMath.minuteTrackUnits(d.apertureAxisOffsetDeg),profile.dateHour,advisorySuffix)));
                    if(numeralSeverity>0) {
                        double mag=Math.max(Math.abs(d.xPct),Math.abs(d.yPct));
                        findings.add(new Finding(QcExtendedMath.findingPriority(numeralSeverity,mag),
                                String.format(Locale.US,"Date numeral off-centre: horizontal %+4.1f%%, vertical %+4.1f%%%s",d.xPct,d.yPct,advisorySuffix)));
                    }

                    if(profile.cyclops) {
                        if(d.cyclopsBox!=null) {
                            int cs=QcExtendedMath.dateAxisSeverity(d.cyclopsAxisOffsetDeg);
                            int os=QcExtendedMath.orientationSeverity(d.cyclopsRotationDeg);
                            int rs=QcExtendedMath.cyclopsApertureOffsetSeverity(d.cyclopsApertureOffsetR);
                            int worst=Math.max(cs,Math.max(os,rs));
                            Scalar lens=!fine?neutral:worst==0?green:worst==1?amber:red;
                            Imgproc.rectangle(out,d.cyclopsBox.tl(),d.cyclopsBox.br(),lens,2,Imgproc.LINE_AA,0);
                            Imgproc.drawMarker(out,new Point(d.cyclopsCenterX,d.cyclopsCenterY),lens,Imgproc.MARKER_CROSS,14,2,Imgproc.LINE_AA);
                            detail.append(String.format(Locale.US,
                                    "Cyclops lens alignment to %d o'clock track: %+4.2f° (%+4.2f track divisions), rotation %+4.2f°, lens/aperture centre separation %.1f%% of dial radius%s\n",
                                    profile.dateHour,d.cyclopsAxisOffsetDeg,QcExtendedMath.minuteTrackUnits(d.cyclopsAxisOffsetDeg),
                                    d.cyclopsRotationDeg,d.cyclopsApertureOffsetR*100.0,advisorySuffix));
                            if(worst>0) findings.add(new Finding(QcExtendedMath.findingPriority(worst,
                                            Math.max(Math.abs(d.cyclopsAxisOffsetDeg),Math.abs(d.cyclopsRotationDeg)))+35.0,
                                    String.format(Locale.US,"Cyclops alignment merits inspection: axis %+4.2f°, rotation %+4.2f°%s",
                                            d.cyclopsAxisOffsetDeg,d.cyclopsRotationDeg,advisorySuffix)));
                        } else {
                            detail.append("Cyclops lens boundary: not confidently detected; no cyclops box or automatic lens verdict produced.\n");
                        }
                    }
                } else {
                    detail.append("Date aperture: not confidently isolated near the model's expected date position; no automatic date/cyclops verdict produced.\n");
                }
            }

            if(profile.rehautCheck) {
                int x0=(int)Math.max(0,cx-dr*0.78), x1=(int)Math.min(src.cols()-1,cx+dr*0.78);
                int y0=(int)Math.max(0,cy-dr*0.93), y1=(int)Math.min(src.rows()-1,cy-dr*0.61);
                if(x1>x0&&y1>y0) Imgproc.rectangle(out,new Point(x0,y0),new Point(x1,y1),magenta,1,Imgproc.LINE_AA,0);
                detail.append("Rehaut/crown-at-12: highlighted inspection band; no automatic pass/fail because engraving visibility and genuine tolerance vary with focus and angle.\n");
            }

            Rect textZone=safeRect((int)(cx-dr*0.45),(int)(cy-dr*0.25),(int)(dr*0.90),(int)(dr*0.62),src.cols(),src.rows());
            if(textZone!=null) {
                Mat sub=src.submat(textZone); double sharp=textSharpness(sub); sub.release();
                Imgproc.rectangle(out,textZone.tl(),textZone.br(),cyan,1,Imgproc.LINE_AA,0);
                detail.append(String.format(Locale.US,"Dial-print zone sharpness: %.1f (photo-quality aid only; typography/authenticity is not inferred).\n",sharp));
            }

            if(profile.selCheck) {
                double leftGap=selGapEvidence(src,cx-dr*0.63,cy+dr*1.05,dr*0.17,dr*0.20);
                double rightGap=selGapEvidence(src,cx+dr*0.63,cy+dr*1.05,dr*0.17,dr*0.20);
                drawSelBox(out,cx-dr*0.63,cy+dr*1.05,dr,leftGap);
                drawSelBox(out,cx+dr*0.63,cy+dr*1.05,dr,rightGap);
                detail.append(String.format(Locale.US,"SEL dark-gap evidence: left %.2f, right %.2f (lighting-sensitive; inspect highlighted zones visually).\n",leftGap,rightGap));
                if(leftGap>0.22 || rightGap>0.22) {
                    double worst=Math.max(leftGap,rightGap);
                    findings.add(new Finding(90.0+worst,
                            String.format(Locale.US,"SEL gap area merits visual inspection (left %.2f, right %.2f; lighting-sensitive)",leftGap,rightGap)));
                }
            }

            detail.append("Lume consistency: not graded from a normal-light QC photo. Use a dedicated lume shot before any lume verdict.\n");
            if(!fine) detail.append("Fine QC is advisory because ").append(fineReason).append(".\n");

            Collections.sort(findings);
            StringBuilder report=new StringBuilder("\n\n");
            if(!findings.isEmpty()) {
                report.append(fine?"Top QC findings\n":"Top QC observations — advisory only\n");
                int limit=Math.min(3,findings.size());
                for(int i=0;i<limit;i++) report.append(i+1).append(". ").append(findings.get(i).text).append("\n");
                report.append("\n");
            } else {
                report.append(fine?"Top QC findings: no material geometric deviation detected.\n\n":"Top QC observations: no material deviation ranked because "+fineReason+".\n\n");
            }
            report.append("Extended QC checks\n").append(detail);

            Bitmap b=Bitmap.createBitmap(out.cols(),out.rows(),Bitmap.Config.ARGB_8888);
            Mat rgba=new Mat(); Imgproc.cvtColor(out,rgba,Imgproc.COLOR_BGR2RGBA); Utils.matToBitmap(rgba,b); rgba.release(); out.release();
            return new Result(b,report.toString());
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
        double axis=Math.toDegrees(0.5*Math.atan2(2*cxy,cxx-cyy));
        double radialImage=expectedRadialDeg-90.0;
        return QcExtendedMath.smallestAxisError(axis,radialImage);
    }

    private static double bezelTwelveOffset(Mat bgr,double cx,double cy,double dr,double global) {
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);
        double best=-1,bestOff=Double.NaN;
        for(double off=-8;off<=8;off+=0.25){Point p=polar(cx,cy,dr*1.23,global+off);double sum=0;int n=0;
            for(int yy=-4;yy<=4;yy+=2)for(int xx=-4;xx<=4;xx+=2){int x=(int)Math.round(p.x+xx),y=(int)Math.round(p.y+yy);if(x<0||x>=gray.cols()||y<0||y>=gray.rows())continue;sum+=gray.get(y,x)[0];n++;}
            if(n>0&&sum/n>best){best=sum/n;bestOff=off;}}
        gray.release();return best>=115?bestOff:Double.NaN;
    }

    private static final class DateResult {
        Rect apertureBox,cyclopsBox;
        double apertureCenterX,apertureCenterY,inkX,inkY,xPct,yPct,apertureAxisOffsetDeg;
        double cyclopsCenterX,cyclopsCenterY,cyclopsAxisOffsetDeg,cyclopsRotationDeg,cyclopsApertureOffsetR;
    }

    private static DateResult measureDate(Mat bgr,double cx,double cy,double dr,double global,ModelCatalog.Profile profile) {
        double expectedAngle=QcExtendedMath.hourAngleDeg(profile.dateHour)+global;
        Point expected=polar(cx,cy,dr*profile.dateRadiusRatio,expectedAngle);
        Rect roi=safeRect((int)Math.round(expected.x-dr*0.42),(int)Math.round(expected.y-dr*0.32),
                (int)Math.round(dr*0.84),(int)Math.round(dr*0.64),bgr.cols(),bgr.rows());
        if(roi==null)return null;

        Mat roiMat=bgr.submat(roi),gray=new Mat();Imgproc.cvtColor(roiMat,gray,Imgproc.COLOR_BGR2GRAY);roiMat.release();
        Mat bin=new Mat();Imgproc.threshold(gray,bin,155,255,Imgproc.THRESH_BINARY);
        List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();Imgproc.findContours(bin,cs,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        Rect best=null;double bestScore=-1;
        for(MatOfPoint c:cs){
            Rect q=Imgproc.boundingRect(c); double ar=q.width/(double)Math.max(1,q.height),area=q.area();
            double wr=q.width/dr,hr=q.height/dr;
            if(ar<1.0||ar>2.8||wr<0.17||wr>0.54||hr<0.12||hr>0.40||area<dr*dr*0.012||area>dr*dr*0.16)continue;
            double gx=roi.x+q.x+q.width/2.0,gy=roi.y+q.y+q.height/2.0;
            double dist=Math.hypot(gx-expected.x,gy-expected.y)/dr;if(dist>0.30)continue;
            double rectangularity=Math.min(1.0,Math.abs(Imgproc.contourArea(c))/Math.max(1.0,area));
            double aspectFit=1.0-Math.min(1.0,Math.abs(ar-1.55)/1.2);
            double sizeFit=1.0-Math.min(1.0,Math.abs(wr-0.36)/0.25);
            double score=(1.0-dist/0.30)*3.0+rectangularity+aspectFit+sizeFit;
            if(score>bestScore){bestScore=score;best=q;}
        }
        for(MatOfPoint c:cs)c.release();hier.release();bin.release();
        if(best==null){gray.release();return null;}

        Rect inner=new Rect(Math.min(gray.cols()-1,best.x+Math.max(1,best.width/8)),
                Math.min(gray.rows()-1,best.y+Math.max(1,best.height/8)),
                Math.max(1,best.width-Math.max(2,best.width/4)),Math.max(1,best.height-Math.max(2,best.height/4)));
        inner.width=Math.min(inner.width,gray.cols()-inner.x);inner.height=Math.min(inner.height,gray.rows()-inner.y);
        double sw=0,sx=0,sy=0;
        for(int y=inner.y;y<inner.y+inner.height;y++)for(int x=inner.x;x<inner.x+inner.width;x++){
            double v=gray.get(y,x)[0];if(v>125)continue;double w=126-v;sw+=w;sx+=w*x;sy+=w*y;
        }
        if(sw<40){gray.release();return null;}
        double ix=sx/sw,iy=sy/sw,bx=best.x+best.width/2.0,by=best.y+best.height/2.0;
        DateResult d=new DateResult();
        d.apertureBox=new Rect(roi.x+best.x,roi.y+best.y,best.width,best.height);
        d.apertureCenterX=roi.x+bx;d.apertureCenterY=roi.y+by;
        d.inkX=roi.x+ix;d.inkY=roi.y+iy;
        d.xPct=(ix-bx)/(best.width/2.0)*100.0;d.yPct=(iy-by)/(best.height/2.0)*100.0;
        d.apertureAxisOffsetDeg=QcExtendedMath.localDateAxisOffsetDeg(cx,cy,d.apertureCenterX,d.apertureCenterY,profile.dateHour,global);
        gray.release();

        if(profile.cyclops) detectCyclops(bgr,cx,cy,dr,global,profile,d);
        return d;
    }

    private static void detectCyclops(Mat bgr,double cx,double cy,double dr,double global,ModelCatalog.Profile profile,DateResult d) {
        Rect roi=safeRect((int)Math.round(d.apertureCenterX-dr*0.52),(int)Math.round(d.apertureCenterY-dr*0.42),
                (int)Math.round(dr*1.04),(int)Math.round(dr*0.84),bgr.cols(),bgr.rows());
        if(roi==null)return;
        Mat sub=bgr.submat(roi),gray=new Mat(),edges=new Mat();Imgproc.cvtColor(sub,gray,Imgproc.COLOR_BGR2GRAY);sub.release();
        Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);Imgproc.Canny(gray,edges,35,110);
        List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();Imgproc.findContours(edges,cs,hier,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
        Rect best=null;RotatedRect bestRot=null;double bestScore=-1;
        for(MatOfPoint c:cs){
            Rect q=Imgproc.boundingRect(c);double wr=q.width/dr,hr=q.height/dr,ar=q.width/(double)Math.max(1,q.height);
            if(wr<0.46||wr>1.00||hr<0.28||hr>0.72||ar<1.05||ar>2.8)continue;
            double gx=roi.x+q.x+q.width/2.0,gy=roi.y+q.y+q.height/2.0;
            double centreDist=Math.hypot(gx-d.apertureCenterX,gy-d.apertureCenterY)/dr;if(centreDist>0.22)continue;
            if(!containsExpanded(q,d.apertureBox,roi,2))continue;
            double area=Math.abs(Imgproc.contourArea(c));double rect=area/Math.max(1.0,q.area());
            double sizeFit=1.0-Math.min(1.0,Math.abs(wr-0.72)/0.40);
            double score=(1.0-centreDist/0.22)*3.0+Math.min(1.0,rect)+sizeFit;
            if(score>bestScore){
                MatOfPoint2f f=new MatOfPoint2f(c.toArray());RotatedRect rr=Imgproc.minAreaRect(f);f.release();
                best=q;bestRot=rr;bestScore=score;
            }
        }
        for(MatOfPoint c:cs)c.release();hier.release();edges.release();gray.release();
        if(best==null||bestRot==null||bestScore<2.0)return;
        d.cyclopsBox=new Rect(roi.x+best.x,roi.y+best.y,best.width,best.height);
        d.cyclopsCenterX=roi.x+best.x+best.width/2.0;d.cyclopsCenterY=roi.y+best.y+best.height/2.0;
        d.cyclopsAxisOffsetDeg=QcExtendedMath.localDateAxisOffsetDeg(cx,cy,d.cyclopsCenterX,d.cyclopsCenterY,profile.dateHour,global);
        double longAxis=bestRot.angle;
        if(bestRot.size.width<bestRot.size.height)longAxis+=90.0;
        double expectedImageAxis=QcExtendedMath.hourAngleDeg(profile.dateHour)+global-90.0;
        d.cyclopsRotationDeg=QcExtendedMath.smallestAxisError(longAxis,expectedImageAxis);
        d.cyclopsApertureOffsetR=Math.hypot(d.cyclopsCenterX-d.apertureCenterX,d.cyclopsCenterY-d.apertureCenterY)/Math.max(1.0,dr);
    }

    private static boolean containsExpanded(Rect localCyclops,Rect apertureAbs,Rect roi,int margin) {
        int ax=apertureAbs.x-roi.x,ay=apertureAbs.y-roi.y;
        int ar=ax+apertureAbs.width,ab=ay+apertureAbs.height;
        return localCyclops.x<=ax+margin&&localCyclops.y<=ay+margin&&
                localCyclops.x+localCyclops.width>=ar-margin&&localCyclops.y+localCyclops.height>=ab-margin;
    }

    private static void drawLocalAxisCue(Mat out,double cx,double cy,double x,double y,int hour,double global,double dr,Scalar color) {
        double r=Math.hypot(x-cx,y-cy);Point ideal=polar(cx,cy,r,QcExtendedMath.hourAngleDeg(hour)+global);
        Imgproc.drawMarker(out,ideal,color,Imgproc.MARKER_TILTED_CROSS,14,2,Imgproc.LINE_AA);
        Imgproc.circle(out,new Point(x,y),7,color,2,Imgproc.LINE_AA,0);
        Imgproc.line(out,ideal,new Point(x,y),color,2,Imgproc.LINE_AA,0);
    }

    private static double textSharpness(Mat roi){Mat g=new Mat(),lap=new Mat();Imgproc.cvtColor(roi,g,Imgproc.COLOR_BGR2GRAY);Imgproc.Laplacian(g,lap,CvType.CV_64F);org.opencv.core.MatOfDouble mean=new org.opencv.core.MatOfDouble(),sd=new org.opencv.core.MatOfDouble();Core.meanStdDev(lap,mean,sd);double v=sd.get(0,0)[0];g.release();lap.release();mean.release();sd.release();return v*v;}
    private static double selGapEvidence(Mat bgr,double cx,double cy,double hw,double hh){Rect r=safeRect((int)(cx-hw),(int)(cy-hh),(int)(2*hw),(int)(2*hh),bgr.cols(),bgr.rows());if(r==null)return 0;Mat sub=bgr.submat(r),g=new Mat();Imgproc.cvtColor(sub,g,Imgproc.COLOR_BGR2GRAY);sub.release();double dark=0;for(int y=0;y<g.rows();y+=2)for(int x=0;x<g.cols();x+=2)if(g.get(y,x)[0]<45)dark++;double sampled=Math.ceil(g.rows()/2.0)*Math.ceil(g.cols()/2.0);g.release();return sampled>0?dark/sampled:0;}
    private static void drawSelBox(Mat out,double cx,double cy,double dr,double evidence){Scalar s=evidence>0.22?new Scalar(40,180,255):new Scalar(180,180,180);Point a=new Point(cx-dr*0.17,cy-dr*0.20),b=new Point(cx+dr*0.17,cy+dr*0.20);Imgproc.rectangle(out,a,b,s,1,Imgproc.LINE_AA,0);}
    private static Rect safeRect(int x,int y,int w,int h,int cols,int rows){x=Math.max(0,x);y=Math.max(0,y);if(x>=cols||y>=rows)return null;w=Math.min(w,cols-x);h=Math.min(h,rows-y);return w>2&&h>2?new Rect(x,y,w,h):null;}
    private static Point polar(double cx,double cy,double r,double deg){double a=Math.toRadians(deg);return new Point(cx+Math.sin(a)*r,cy-Math.cos(a)*r);}
    private static Object findHour(List<Object> list,int h)throws Exception{for(Object m:list)if((int)Math.round(num(m,"hour"))==h)return m;return null;}
    private static Method method(String n,Class<?>...t)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(n,t);m.setAccessible(true);return m;}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private QcExtendedAnalyzer(){}
}
