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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reddit-informed QC checks. Alpha16 adds local minute-track anchoring for date
 * geometry and apparent date-numeral magnification comparison against the chosen
 * exact-model genuine reference. Cyclops position remains perspective-sensitive.
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

    private static final class TrackAnchor {
        final double angleDeg;
        final double confidence;
        TrackAnchor(double a,double c){angleDeg=a;confidence=c;}
    }

    private static final class DateResult {
        Rect apertureBox,cyclopsBox;
        double apertureCenterX,apertureCenterY,inkX,inkY,xPct,yPct,apertureAxisOffsetDeg;
        double inkHeightOverDial,inkWidthOverDial;
        double cyclopsCenterX,cyclopsCenterY,cyclopsAxisOffsetDeg,cyclopsRotationDeg,cyclopsApertureOffsetR;
        double cyclopsTangentialOffsetR,cyclopsRadialOffsetR;
        double localTrackAngleDeg;
        boolean localTrackDetected;
    }

    static Result analyse(Bitmap watch, String modelRef) {
        return analyse(watch,null,modelRef);
    }

    static Result analyse(Bitmap watch, Bitmap reference, String modelRef) {
        Mat src=new Mat();
        try {
            ModelCatalog.Profile profile=ModelCatalog.require(modelRef);
            Utils.bitmapToMat(watch,src); Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle dial=DialAnalysisEngine.detectDial(src);
            if(dial==null) return new Result(watch.copy(Bitmap.Config.ARGB_8888,false),"Extended QC unavailable: dial geometry not verified.");
            DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(src,dial);
            double tilt=DialAnalysisEngine.perspectiveEquivalent(src,dial);
            boolean fine=QcExtendedMath.perspectiveAllowsFineQc(tilt);
            String fineReason=QcExtendedMath.fineQcReason(tilt);
            String advisorySuffix=fine?"":" (advisory: "+fineReason+")";
            double cx=dial.x,cy=dial.y,dr=dial.r;
            double global=set.globalRotation;if(!Double.isFinite(global))global=0.0;
            List<DialAnalysisEngine.Marker> markers=set.markers;

            Mat out=src.clone();
            Scalar green=new Scalar(120,220,120), amber=new Scalar(40,180,255), red=new Scalar(70,70,255);
            Scalar neutral=new Scalar(180,180,180), magenta=new Scalar(220,80,220), cyan=new Scalar(220,210,70);
            StringBuilder detail=new StringBuilder();
            List<Finding> findings=new ArrayList<>();

            for(int h:new int[]{12,6,9}) {
                DialAnalysisEngine.Marker m=findHour(markers,h);if(m==null)continue;
                double angular=m.angular,actualR=m.radius;
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
                    if(os>0) findings.add(new Finding(QcExtendedMath.findingPriority(os,orient)+25.0,
                            String.format(Locale.US,"%d marker body rotation %+4.2f° (%s)%s",h,orient,os==2?"strong detected deviation":"detected deviation",advisorySuffix)));
                }
                detail.append(String.format(Locale.US,"%d marker vs minute track: position %+4.2f°",h,angular));
                if(orientOk)detail.append(String.format(Locale.US,", body rotation %+4.2f°",orient));
                detail.append(advisorySuffix).append("\n");
            }

            if(profile.bezelTwelveCheck) {
                double pipOffset=bezelTwelveOffset(src,cx,cy,dr,global);
                if(Double.isFinite(pipOffset)) {
                    int ps=QcExtendedMath.localTrackSeverity(pipOffset);
                    Scalar s=!fine?neutral:ps==0?green:ps==1?amber:red;
                    Point pp=polar(cx,cy,dr*1.23,global+pipOffset); Imgproc.circle(out,pp,8,s,2,Imgproc.LINE_AA,0);
                    detail.append(String.format(Locale.US,"Bezel/pip 12 alignment: %+4.2f° relative to dial 12%s\n",pipOffset,advisorySuffix));
                    if(ps>0)findings.add(new Finding(QcExtendedMath.findingPriority(ps,pipOffset),String.format(Locale.US,"Bezel/pip 12 offset %+4.2f°%s",pipOffset,advisorySuffix)));
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
                    drawLocalAxisCue(out,cx,cy,d.apertureCenterX,d.apertureCenterY,d.localTrackAngleDeg,dr,s);
                    Point anchor=polar(cx,cy,dr*0.94,d.localTrackAngleDeg);
                    Imgproc.drawMarker(out,anchor,s,Imgproc.MARKER_DIAMOND,12,2,Imgproc.LINE_AA);

                    int minuteIndex=QcExtendedMath.minuteIndexForHour(profile.dateHour);
                    detail.append(String.format(Locale.US,
                            "Date aperture vs local %02d-minute marker: %+4.2f° (%+4.2f marker divisions)%s%s\n",
                            minuteIndex,d.apertureAxisOffsetDeg,QcExtendedMath.minuteTrackUnits(d.apertureAxisOffsetDeg),
                            d.localTrackDetected?"":" [local marker not isolated; fitted dial axis used]",advisorySuffix));
                    detail.append(String.format(Locale.US,"Date numeral centring in aperture: horizontal %+4.1f%%, vertical %+4.1f%%%s\n",d.xPct,d.yPct,advisorySuffix));

                    if(axisSeverity>0)findings.add(new Finding(QcExtendedMath.findingPriority(axisSeverity,d.apertureAxisOffsetDeg)+30.0,
                            String.format(Locale.US,"Date aperture %+4.2f marker divisions from local %02d-minute reference%s",QcExtendedMath.minuteTrackUnits(d.apertureAxisOffsetDeg),minuteIndex,advisorySuffix)));
                    if(numeralSeverity>0){double mag=Math.max(Math.abs(d.xPct),Math.abs(d.yPct));findings.add(new Finding(QcExtendedMath.findingPriority(numeralSeverity,mag),
                            String.format(Locale.US,"Date numeral off-centre: horizontal %+4.1f%%, vertical %+4.1f%%%s",d.xPct,d.yPct,advisorySuffix)));}

                    DateResult refDate=null; double refTilt=Double.NaN;
                    if(reference!=null) {
                        Mat rm=new Mat();
                        try {
                            Utils.bitmapToMat(reference,rm);Imgproc.cvtColor(rm,rm,Imgproc.COLOR_RGBA2BGR);
                            DialAnalysisEngine.Circle rd=DialAnalysisEngine.detectDial(rm);
                            if(rd!=null) {
                                DialAnalysisEngine.MarkerSet rs=DialAnalysisEngine.measureMarkerSet(rm,rd);
                                double rcx=rd.x,rcy=rd.y,rdr=rd.r;
                                double rglobal=rs.globalRotation;if(!Double.isFinite(rglobal))rglobal=0.0;
                                refTilt=DialAnalysisEngine.perspectiveEquivalent(rm,rd);
                                refDate=measureDate(rm,rcx,rcy,rdr,rglobal,profile);
                            }
                        } finally {rm.release();}
                    }
                    if(refDate!=null) {
                        double magPct=QcExtendedMath.apparentMagnificationPercent(d.inkHeightOverDial,refDate.inkHeightOverDial);
                        double mismatch=(Double.isFinite(tilt)&&Double.isFinite(refTilt))?Math.abs(tilt-refTilt):Double.NaN;
                        boolean magFine=fine&&Double.isFinite(mismatch)&&mismatch<6.0;
                        String magSuffix=magFine?"":" (advisory: perspective mismatch can alter apparent numeral height)";
                        int ms=QcExtendedMath.magnificationMatchSeverity(magPct);
                        detail.append(String.format(Locale.US,"Apparent date numeral magnification vs genuine: %.1f%% (100%% = genuine, height normalized to dial radius)%s\n",magPct,magSuffix));
                        if(ms>0)findings.add(new Finding(QcExtendedMath.findingPriority(ms,magPct-100.0)+45.0,
                                String.format(Locale.US,"Date magnification %.1f%% of genuine%s",magPct,magSuffix)));
                    } else if(reference!=null) {
                        detail.append("Apparent date numeral magnification vs genuine: reference date geometry could not be measured reliably.\n");
                    } else {
                        detail.append("Apparent date numeral magnification vs genuine: unavailable without a genuine reference.\n");
                    }

                    if(profile.cyclops) {
                        if(d.cyclopsBox!=null) {
                            int os=QcExtendedMath.orientationSeverity(d.cyclopsRotationDeg);
                            int rs=QcExtendedMath.cyclopsApertureOffsetSeverity(d.cyclopsApertureOffsetR);
                            int worst=Math.max(os,rs);
                            Scalar lens=!fine?neutral:worst==0?green:worst==1?amber:red;
                            Imgproc.rectangle(out,d.cyclopsBox.tl(),d.cyclopsBox.br(),lens,2,Imgproc.LINE_AA,0);
                            Imgproc.drawMarker(out,new Point(d.cyclopsCenterX,d.cyclopsCenterY),lens,Imgproc.MARKER_CROSS,14,2,Imgproc.LINE_AA);
                            detail.append(String.format(Locale.US,
                                    "Cyclops vs date aperture: tangential %+4.1f%% R, radial %+4.1f%% R, rotation %+4.2f°; apparent track offset %+4.2f marker divisions (perspective-sensitive)%s\n",
                                    d.cyclopsTangentialOffsetR*100.0,d.cyclopsRadialOffsetR*100.0,d.cyclopsRotationDeg,
                                    QcExtendedMath.minuteTrackUnits(d.cyclopsAxisOffsetDeg),advisorySuffix));
                            if(worst>0)findings.add(new Finding(QcExtendedMath.findingPriority(worst,Math.max(Math.abs(d.cyclopsRotationDeg),d.cyclopsApertureOffsetR*100.0))+35.0,
                                    String.format(Locale.US,"Cyclops-to-aperture alignment merits inspection%s",advisorySuffix)));
                        } else detail.append("Cyclops lens boundary: not confidently detected; no cyclops box or automatic lens-position verdict produced.\n");
                    }
                } else detail.append("Date aperture: not confidently isolated near the model's expected date position; no automatic date/cyclops verdict produced.\n");
            }

            if(profile.rehautCheck) {
                int x0=(int)Math.max(0,cx-dr*0.78),x1=(int)Math.min(src.cols()-1,cx+dr*0.78);
                int y0=(int)Math.max(0,cy-dr*0.93),y1=(int)Math.min(src.rows()-1,cy-dr*0.61);
                if(x1>x0&&y1>y0)Imgproc.rectangle(out,new Point(x0,y0),new Point(x1,y1),magenta,1,Imgproc.LINE_AA,0);
                detail.append("Rehaut/crown-at-12: highlighted inspection band; no automatic pass/fail because engraving visibility and genuine tolerance vary with focus and angle.\n");
            }

            Rect textZone=safeRect((int)(cx-dr*0.45),(int)(cy-dr*0.25),(int)(dr*0.90),(int)(dr*0.62),src.cols(),src.rows());
            if(textZone!=null){Mat sub=src.submat(textZone);double sharp=textSharpness(sub);sub.release();Imgproc.rectangle(out,textZone.tl(),textZone.br(),cyan,1,Imgproc.LINE_AA,0);detail.append(String.format(Locale.US,"Dial-print zone sharpness: %.1f (photo-quality aid only; typography/authenticity is not inferred).\n",sharp));}

            if(profile.selCheck) {
                double leftGap=selGapEvidence(src,cx-dr*0.63,cy+dr*1.05,dr*0.17,dr*0.20),rightGap=selGapEvidence(src,cx+dr*0.63,cy+dr*1.05,dr*0.17,dr*0.20);
                drawSelBox(out,cx-dr*0.63,cy+dr*1.05,dr,leftGap);drawSelBox(out,cx+dr*0.63,cy+dr*1.05,dr,rightGap);
                detail.append(String.format(Locale.US,"SEL dark-gap evidence: left %.2f, right %.2f (lighting-sensitive; inspect highlighted zones visually).\n",leftGap,rightGap));
                if(leftGap>0.22||rightGap>0.22){double worst=Math.max(leftGap,rightGap);findings.add(new Finding(90.0+worst,String.format(Locale.US,"SEL gap area merits visual inspection (left %.2f, right %.2f; lighting-sensitive)",leftGap,rightGap)));}
            }

            detail.append("Lume consistency: not graded from a normal-light QC photo. Use a dedicated lume shot before any lume verdict.\n");
            if(!fine)detail.append("Fine QC is advisory because ").append(fineReason).append(".\n");

            Collections.sort(findings);
            StringBuilder report=new StringBuilder("\n\n");
            if(!findings.isEmpty()){
                report.append(fine?"Top QC findings\n":"Top QC observations — advisory only\n");
                for(int i=0;i<Math.min(3,findings.size());i++)report.append(i+1).append(". ").append(findings.get(i).text).append("\n");
                report.append("\n");
            } else report.append(fine?"Top QC findings: no material geometric deviation detected.\n\n":"Top QC observations: no material deviation ranked because "+fineReason+".\n\n");
            report.append("Extended QC checks\n").append(detail);

            Bitmap b=Bitmap.createBitmap(out.cols(),out.rows(),Bitmap.Config.ARGB_8888);
            Mat rgba=new Mat();Imgproc.cvtColor(out,rgba,Imgproc.COLOR_BGR2RGBA);Utils.matToBitmap(rgba,b);rgba.release();out.release();
            return new Result(b,report.toString());
        } catch(Throwable e) {
            return new Result(watch.copy(Bitmap.Config.ARGB_8888,false),"Extended QC unavailable: "+e.getClass().getSimpleName()+".");
        } finally {src.release();}
    }

    private static double markerAxisError(Mat bgr,Point center,double half,double expectedRadialDeg){
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
        int x0=(int)Math.max(0,center.x-half),x1=(int)Math.min(gray.cols()-1,center.x+half),y0=(int)Math.max(0,center.y-half),y1=(int)Math.min(gray.rows()-1,center.y+half);
        double sw=0,mx=0,my=0;
        for(int y=y0;y<=y1;y+=2)for(int x=x0;x<=x1;x+=2){double v=gray.get(y,x)[0];if(v<150)continue;double w=v-140;sw+=w;mx+=w*x;my+=w*y;}
        if(sw<200){gray.release();return Double.NaN;}mx/=sw;my/=sw;double cxx=0,cyy=0,cxy=0;
        for(int y=y0;y<=y1;y+=2)for(int x=x0;x<=x1;x+=2){double v=gray.get(y,x)[0];if(v<150)continue;double w=v-140,dx=x-mx,dy=y-my;cxx+=w*dx*dx;cyy+=w*dy*dy;cxy+=w*dx*dy;}
        gray.release();double axis=Math.toDegrees(0.5*Math.atan2(2*cxy,cxx-cyy));return QcExtendedMath.smallestAxisError(axis,expectedRadialDeg-90.0);
    }

    private static double bezelTwelveOffset(Mat bgr,double cx,double cy,double dr,double global){
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);double best=-1,bestOff=Double.NaN;
        for(double off=-8;off<=8;off+=0.25){Point p=polar(cx,cy,dr*1.23,global+off);double sum=0;int n=0;for(int yy=-4;yy<=4;yy+=2)for(int xx=-4;xx<=4;xx+=2){int x=(int)Math.round(p.x+xx),y=(int)Math.round(p.y+yy);if(x<0||x>=gray.cols()||y<0||y>=gray.rows())continue;sum+=gray.get(y,x)[0];n++;}if(n>0&&sum/n>best){best=sum/n;bestOff=off;}}
        gray.release();return best>=115?bestOff:Double.NaN;
    }

    private static DateResult measureDate(Mat bgr,double cx,double cy,double dr,double global,ModelCatalog.Profile profile){
        double expectedAngle=QcExtendedMath.hourAngleDeg(profile.dateHour)+global;
        TrackAnchor anchor=detectMinuteTrackAnchor(bgr,cx,cy,dr,expectedAngle);
        double localAngle=anchor!=null?anchor.angleDeg:expectedAngle;
        Point expected=polar(cx,cy,dr*profile.dateRadiusRatio,expectedAngle);
        Rect roi=safeRect((int)Math.round(expected.x-dr*0.42),(int)Math.round(expected.y-dr*0.32),(int)Math.round(dr*0.84),(int)Math.round(dr*0.64),bgr.cols(),bgr.rows());if(roi==null)return null;
        Mat roiMat=bgr.submat(roi),gray=new Mat();Imgproc.cvtColor(roiMat,gray,Imgproc.COLOR_BGR2GRAY);roiMat.release();
        Mat bin=new Mat();Imgproc.threshold(gray,bin,155,255,Imgproc.THRESH_BINARY);
        List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();Imgproc.findContours(bin,cs,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
        Rect best=null;double bestScore=-1;
        for(MatOfPoint c:cs){Rect q=Imgproc.boundingRect(c);double ar=q.width/(double)Math.max(1,q.height),area=q.area(),wr=q.width/dr,hr=q.height/dr;if(ar<1.0||ar>2.8||wr<0.17||wr>0.54||hr<0.12||hr>0.40||area<dr*dr*0.012||area>dr*dr*0.16)continue;double gx=roi.x+q.x+q.width/2.0,gy=roi.y+q.y+q.height/2.0,dist=Math.hypot(gx-expected.x,gy-expected.y)/dr;if(dist>0.30)continue;double rectangularity=Math.min(1.0,Math.abs(Imgproc.contourArea(c))/Math.max(1.0,area)),aspectFit=1.0-Math.min(1.0,Math.abs(ar-1.55)/1.2),sizeFit=1.0-Math.min(1.0,Math.abs(wr-0.36)/0.25),score=(1.0-dist/0.30)*3.0+rectangularity+aspectFit+sizeFit;if(score>bestScore){bestScore=score;best=q;}}
        for(MatOfPoint c:cs)c.release();hier.release();bin.release();if(best==null){gray.release();return null;}
        Rect inner=new Rect(Math.min(gray.cols()-1,best.x+Math.max(1,best.width/8)),Math.min(gray.rows()-1,best.y+Math.max(1,best.height/8)),Math.max(1,best.width-Math.max(2,best.width/4)),Math.max(1,best.height-Math.max(2,best.height/4)));inner.width=Math.min(inner.width,gray.cols()-inner.x);inner.height=Math.min(inner.height,gray.rows()-inner.y);
        double sw=0,sx=0,sy=0;int[] rowInk=new int[inner.height],colInk=new int[inner.width];
        for(int y=inner.y;y<inner.y+inner.height;y++)for(int x=inner.x;x<inner.x+inner.width;x++){double v=gray.get(y,x)[0];if(v>125)continue;double w=126-v;sw+=w;sx+=w*x;sy+=w*y;if(v<110){rowInk[y-inner.y]++;colInk[x-inner.x]++;}}
        if(sw<40){gray.release();return null;}double ix=sx/sw,iy=sy/sw,bx=best.x+best.width/2.0,by=best.y+best.height/2.0;
        int yLo=quantileIndex(rowInk,0.05),yHi=quantileIndex(rowInk,0.95),xLo=quantileIndex(colInk,0.05),xHi=quantileIndex(colInk,0.95);
        DateResult d=new DateResult();d.apertureBox=new Rect(roi.x+best.x,roi.y+best.y,best.width,best.height);d.apertureCenterX=roi.x+bx;d.apertureCenterY=roi.y+by;d.inkX=roi.x+ix;d.inkY=roi.y+iy;d.xPct=(ix-bx)/(best.width/2.0)*100.0;d.yPct=(iy-by)/(best.height/2.0)*100.0;d.localTrackAngleDeg=localAngle;d.localTrackDetected=anchor!=null;d.apertureAxisOffsetDeg=QcExtendedMath.localDateAxisOffsetFromAnchorDeg(cx,cy,d.apertureCenterX,d.apertureCenterY,localAngle);d.inkHeightOverDial=(yHi>=yLo?(yHi-yLo+1):0)/Math.max(1.0,dr);d.inkWidthOverDial=(xHi>=xLo?(xHi-xLo+1):0)/Math.max(1.0,dr);gray.release();if(profile.cyclops)detectCyclops(bgr,cx,cy,dr,global,profile,d);return d;
    }

    private static TrackAnchor detectMinuteTrackAnchor(Mat bgr,double cx,double cy,double dr,double expectedAngle){
        Mat gray=new Mat();Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(gray,gray,new Size(3,3),0);double best=-1e9,bestOff=0,bestContrast=0;
        for(double off=-4.0;off<=4.0;off+=0.15){double angle=expectedAngle+off,center=radialBrightness(gray,cx,cy,dr,angle),left=radialBrightness(gray,cx,cy,dr,angle-1.2),right=radialBrightness(gray,cx,cy,dr,angle+1.2);double contrast=center-0.5*(left+right),score=contrast-0.8*Math.abs(off);if(score>best){best=score;bestOff=off;bestContrast=contrast;}}
        gray.release();if(bestContrast<10.0)return null;return new TrackAnchor(expectedAngle+bestOff,Math.min(1.0,bestContrast/45.0));
    }

    private static double radialBrightness(Mat gray,double cx,double cy,double dr,double angle){double sum=0;int n=0;for(double rf=0.88;rf<=1.00;rf+=0.015){Point p=polar(cx,cy,dr*rf,angle);int x=(int)Math.round(p.x),y=(int)Math.round(p.y);if(x<0||x>=gray.cols()||y<0||y>=gray.rows())continue;sum+=gray.get(y,x)[0];n++;}return n>0?sum/n:0;}

    private static int quantileIndex(int[] counts,double q){long total=0;for(int v:counts)total+=v;if(total==0)return -1;long target=Math.max(1,Math.round(total*q)),sum=0;for(int i=0;i<counts.length;i++){sum+=counts[i];if(sum>=target)return i;}return counts.length-1;}

    private static void detectCyclops(Mat bgr,double cx,double cy,double dr,double global,ModelCatalog.Profile profile,DateResult d){
        Rect roi=safeRect((int)Math.round(d.apertureCenterX-dr*0.52),(int)Math.round(d.apertureCenterY-dr*0.42),(int)Math.round(dr*1.04),(int)Math.round(dr*0.84),bgr.cols(),bgr.rows());if(roi==null)return;
        Mat sub=bgr.submat(roi),gray=new Mat(),edges=new Mat();Imgproc.cvtColor(sub,gray,Imgproc.COLOR_BGR2GRAY);sub.release();Imgproc.GaussianBlur(gray,gray,new Size(5,5),0);Imgproc.Canny(gray,edges,35,110);
        List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();Imgproc.findContours(edges,cs,hier,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);Rect best=null;RotatedRect bestRot=null;double bestScore=-1;
        for(MatOfPoint c:cs){Rect q=Imgproc.boundingRect(c);double wr=q.width/dr,hr=q.height/dr,ar=q.width/(double)Math.max(1,q.height);if(wr<0.46||wr>1.00||hr<0.28||hr>0.72||ar<1.05||ar>2.8)continue;double gx=roi.x+q.x+q.width/2.0,gy=roi.y+q.y+q.height/2.0,centreDist=Math.hypot(gx-d.apertureCenterX,gy-d.apertureCenterY)/dr;if(centreDist>0.22||!containsExpanded(q,d.apertureBox,roi,2))continue;double area=Math.abs(Imgproc.contourArea(c)),rect=area/Math.max(1.0,q.area()),sizeFit=1.0-Math.min(1.0,Math.abs(wr-0.72)/0.40),score=(1.0-centreDist/0.22)*3.0+Math.min(1.0,rect)+sizeFit;if(score>bestScore){MatOfPoint2f f=new MatOfPoint2f(c.toArray());RotatedRect rr=Imgproc.minAreaRect(f);f.release();best=q;bestRot=rr;bestScore=score;}}
        for(MatOfPoint c:cs)c.release();hier.release();edges.release();gray.release();if(best==null||bestRot==null||bestScore<2.0)return;
        d.cyclopsBox=new Rect(roi.x+best.x,roi.y+best.y,best.width,best.height);d.cyclopsCenterX=roi.x+best.x+best.width/2.0;d.cyclopsCenterY=roi.y+best.y+best.height/2.0;d.cyclopsAxisOffsetDeg=QcExtendedMath.localDateAxisOffsetFromAnchorDeg(cx,cy,d.cyclopsCenterX,d.cyclopsCenterY,d.localTrackAngleDeg);
        double longAxis=bestRot.angle;if(bestRot.size.width<bestRot.size.height)longAxis+=90.0;double expectedImageAxis=QcExtendedMath.hourAngleDeg(profile.dateHour)+global-90.0;d.cyclopsRotationDeg=QcExtendedMath.smallestAxisError(longAxis,expectedImageAxis);
        double dx=d.cyclopsCenterX-d.apertureCenterX,dy=d.cyclopsCenterY-d.apertureCenterY,theta=Math.toRadians(d.localTrackAngleDeg);d.cyclopsTangentialOffsetR=(dx*Math.cos(theta)+dy*Math.sin(theta))/Math.max(1.0,dr);d.cyclopsRadialOffsetR=(dx*Math.sin(theta)-dy*Math.cos(theta))/Math.max(1.0,dr);d.cyclopsApertureOffsetR=Math.hypot(dx,dy)/Math.max(1.0,dr);
    }

    private static boolean containsExpanded(Rect localCyclops,Rect apertureAbs,Rect roi,int margin){int ax=apertureAbs.x-roi.x,ay=apertureAbs.y-roi.y,ar=ax+apertureAbs.width,ab=ay+apertureAbs.height;return localCyclops.x<=ax+margin&&localCyclops.y<=ay+margin&&localCyclops.x+localCyclops.width>=ar-margin&&localCyclops.y+localCyclops.height>=ab-margin;}
    private static void drawLocalAxisCue(Mat out,double cx,double cy,double x,double y,double anchorDeg,double dr,Scalar color){double r=Math.hypot(x-cx,y-cy);Point ideal=polar(cx,cy,r,anchorDeg);Imgproc.drawMarker(out,ideal,color,Imgproc.MARKER_TILTED_CROSS,14,2,Imgproc.LINE_AA);Imgproc.circle(out,new Point(x,y),7,color,2,Imgproc.LINE_AA,0);Imgproc.line(out,ideal,new Point(x,y),color,2,Imgproc.LINE_AA,0);}
    private static double textSharpness(Mat roi){Mat g=new Mat(),lap=new Mat();Imgproc.cvtColor(roi,g,Imgproc.COLOR_BGR2GRAY);Imgproc.Laplacian(g,lap,CvType.CV_64F);org.opencv.core.MatOfDouble mean=new org.opencv.core.MatOfDouble(),sd=new org.opencv.core.MatOfDouble();Core.meanStdDev(lap,mean,sd);double v=sd.get(0,0)[0];g.release();lap.release();mean.release();sd.release();return v*v;}
    private static double selGapEvidence(Mat bgr,double cx,double cy,double hw,double hh){Rect r=safeRect((int)(cx-hw),(int)(cy-hh),(int)(2*hw),(int)(2*hh),bgr.cols(),bgr.rows());if(r==null)return 0;Mat sub=bgr.submat(r),g=new Mat();Imgproc.cvtColor(sub,g,Imgproc.COLOR_BGR2GRAY);sub.release();double dark=0;for(int y=0;y<g.rows();y+=2)for(int x=0;x<g.cols();x+=2)if(g.get(y,x)[0]<45)dark++;double sampled=Math.ceil(g.rows()/2.0)*Math.ceil(g.cols()/2.0);g.release();return sampled>0?dark/sampled:0;}
    private static void drawSelBox(Mat out,double cx,double cy,double dr,double evidence){Scalar s=evidence>0.22?new Scalar(40,180,255):new Scalar(180,180,180);Point a=new Point(cx-dr*0.17,cy-dr*0.20),b=new Point(cx+dr*0.17,cy+dr*0.20);Imgproc.rectangle(out,a,b,s,1,Imgproc.LINE_AA,0);}
    private static Rect safeRect(int x,int y,int w,int h,int cols,int rows){x=Math.max(0,x);y=Math.max(0,y);if(x>=cols||y>=rows)return null;w=Math.min(w,cols-x);h=Math.min(h,rows-y);return w>2&&h>2?new Rect(x,y,w,h):null;}
    private static Point polar(double cx,double cy,double r,double deg){double a=Math.toRadians(deg);return new Point(cx+Math.sin(a)*r,cy-Math.cos(a)*r);}
    private static DialAnalysisEngine.Marker findHour(List<DialAnalysisEngine.Marker> list,int h){for(DialAnalysisEngine.Marker m:list)if(m.hour==h)return m;return null;}
    private QcExtendedAnalyzer(){}
}
