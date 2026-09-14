package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Canonical GMT marker geometry for 126710-family dials.
 *
 * Angular geometry is measured after removing both photo roll and dial perspective. Radial
 * placement is measured from the perspective-corrected dial centre in normalized dial units,
 * then calibrated against genuine-watch reference geometry. Repeated photos of the same
 * physical watch are collapsed before they influence independent-watch statistics.
 */
final class CanonicalGmtGeometryAnalyzer {
    static final class Result {
        final String report;
        final int usableReferences;
        Result(String report,int usableReferences){this.report=report;this.usableReferences=usableReferences;}
    }

    static final class Measure {
        final double quality, perspective;
        final double[] angularResidualDeg=new double[13];
        final double[] radialPctDial=new double[13];
        final boolean[] present=new boolean[13];
        Measure(double q,double p){
            quality=q; perspective=p;
            for(int i=0;i<13;i++){
                angularResidualDeg[i]=Double.NaN;
                radialPctDial[i]=Double.NaN;
            }
        }
    }

    private static final class Pose {
        final double[] homography;
        final double[] inverseHomography;
        Pose(double[] h,double[] inv){homography=h;inverseHomography=inv;}
    }

    static boolean supports(String modelRef){
        if(modelRef==null)return false;
        String s=modelRef.toUpperCase(Locale.US);
        return s.startsWith("126710");
    }

    static Result analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        if(!supports(modelRef))return new Result("",0);
        Measure wm=measure(watch);
        if(wm==null)return new Result("\n\nCANONICAL GMT GEOMETRY\nUnavailable: watch marker geometry could not be measured reliably.",0);

        List<Measure> refPhotos=new ArrayList<>();
        if(references!=null){
            for(Bitmap b:references){
                if(b==null)continue;
                Measure r=measure(b);
                if(r==null||r.quality<0.56)continue;
                if(Double.isFinite(wm.perspective)&&Double.isFinite(r.perspective)&&Math.abs(wm.perspective-r.perspective)>10.0)continue;
                refPhotos.add(r);
            }
        }
        List<Measure> refs=collapseDuplicateReferences(refPhotos);

        StringBuilder out=new StringBuilder();
        out.append("\n\nCANONICAL GMT GEOMETRY\n");
        out.append("Angular datum: exact 30° hour grid after photo-roll and dial-perspective correction. Radial datum: perspective-corrected marker-centre radius as % of normalized dial radius.\n");
        out.append(String.format(Locale.US,"Usable genuine references for tolerance calibration: %d photo%s across %d independent-watch group%s%s\n",
                refPhotos.size(),refPhotos.size()==1?"":"s",
                refs.size(),refs.size()==1?"":"s",
                refs.size()>=5?" (distribution)":refs.size()>=3?" (small sample)":refs.size()>0?" (insufficient for statistical radial verdicts)":""));

        int flagged=0, assessed=0, insufficient=0;
        for(int h=1;h<=12;h++){
            if(h==3||!wm.present[h])continue;

            double angular=wm.angularResidualDeg[h];
            double[] aa=new double[Math.max(1,refs.size()*2)];
            int an=0;
            for(Measure r:refs)if(r.present[h])aa[an++]=r.angularResidualDeg[h];
            GenuineBaselineStats.Summary as=GenuineBaselineStats.summarize(aa,an);
            double angularBias=(as.n>0&&Double.isFinite(as.median))?as.median:0.0;
            double correctedAngular=angular-angularBias;
            int angularSeverity=canonicalAngularSeverity(correctedAngular,as);

            double[] rr=new double[Math.max(2,refs.size()*2)];
            int rn=0;
            int pair=mirrorPair(h);
            for(Measure r:refs){
                if(r.present[h])rr[rn++]=r.radialPctDial[h];
                if(pair>0&&pair!=h&&r.present[pair])rr[rn++]=r.radialPctDial[pair];
            }
            GenuineBaselineStats.Summary rs=GenuineBaselineStats.summarize(rr,rn);
            double radialDelta=Double.NaN;
            int radialSeverity=0;
            boolean radialVerdict=rs.n>=3&&Double.isFinite(rs.median);
            if(Double.isFinite(wm.radialPctDial[h])&&Double.isFinite(rs.median)){
                radialDelta=wm.radialPctDial[h]-rs.median;
                if(radialVerdict)radialSeverity=canonicalRadialSeverity(wm.radialPctDial[h],rs);
            }

            int sev=Math.max(angularSeverity,radialSeverity);
            String state;
            if(!radialVerdict){
                state=angularSeverity>0?"ANGULAR CHECK; radial sample insufficient":"radial sample insufficient";
                insufficient++;
            } else {
                assessed++;
                state=sev==2?"OUTSIDE GMT RANGE":sev==1?"CHECK":"normal";
                if(sev>0)flagged++;
            }

            String radialText=Double.isFinite(radialDelta)?String.format(Locale.US,"%+5.2f%%",radialDelta):" n/a ";
            out.append(String.format(Locale.US,
                    "%2d o'clock: angle %+5.2f° from canonical; radial Δ %s of dial radius  [%s; radial n=%d]\n",
                    h,correctedAngular,radialText,state,rs.n));
        }

        if(assessed>0){
            if(flagged==0)out.append("Canonical GMT result: no assessed marker lies outside the calibrated canonical tolerance.\n");
            else out.append(String.format(Locale.US,"Canonical GMT result: %d assessed marker%s merit inspection.\n",flagged,flagged==1?"":"s"));
        }
        if(insufficient>0)out.append("Markers with fewer than 3 independent genuine radial groups are reported numerically but are not given a radial pass/fail.\n");
        out.append("Method note: marker centres are first unwarped into normalized dial coordinates, then compared with genuine-watch reference geometry. Genuine references define detector bias/tolerance; they do not let the watch under test define its own radial datum.\n");
        return new Result(out.toString(),refs.size());
    }

    static int canonicalAngularSeverity(double correctedDeg,GenuineBaselineStats.Summary ref){
        if(!Double.isFinite(correctedDeg))return 0;
        double sigma=(ref!=null&&Double.isFinite(ref.sigma))?ref.sigma:0.0;
        double tol=Math.max(0.45,2.5*sigma);
        double a=Math.abs(correctedDeg);
        if(a>Math.max(1.40,tol*1.75))return 2;
        if(a>tol)return 1;
        return 0;
    }

    static int canonicalRadialSeverity(double value,GenuineBaselineStats.Summary ref){
        if(ref==null||ref.n<3||!Double.isFinite(value)||!Double.isFinite(ref.median))return 0;
        double sigma=Double.isFinite(ref.sigma)?ref.sigma:0.0;
        double floor=ref.n>=5?0.55:0.70;
        double tol=Math.max(floor,2.5*sigma);
        double a=Math.abs(value-ref.median);
        if(a>Math.max(1.45,tol*1.75))return 2;
        if(a>tol)return 1;
        return 0;
    }

    static int mirrorPair(int hour){
        switch(hour){
            case 1:return 11;
            case 11:return 1;
            case 2:return 10;
            case 10:return 2;
            case 4:return 8;
            case 8:return 4;
            case 5:return 7;
            case 7:return 5;
            default:return hour;
        }
    }

    static List<Measure> collapseDuplicateReferences(List<Measure> refs){
        List<List<Measure>> clusters=new ArrayList<>();
        for(Measure m:refs){
            boolean placed=false;
            for(List<Measure> cluster:clusters){
                if(samePhysicalWatchGeometry(cluster.get(0),m)){
                    cluster.add(m);
                    placed=true;
                    break;
                }
            }
            if(!placed){
                List<Measure> cluster=new ArrayList<>();
                cluster.add(m);
                clusters.add(cluster);
            }
        }
        List<Measure> out=new ArrayList<>();
        for(List<Measure> cluster:clusters)out.add(average(cluster));
        return out;
    }

    static boolean samePhysicalWatchGeometry(Measure a,Measure b){
        if(a==null||b==null)return false;
        if(Double.isFinite(a.perspective)&&Double.isFinite(b.perspective)&&Math.abs(a.perspective-b.perspective)>6.0)return false;
        double angSs=0,radSs=0; int common=0;
        for(int h=1;h<=12;h++){
            if(h==3||!a.present[h]||!b.present[h])continue;
            angSs+=sq(QcExtendedMath.wrap180(a.angularResidualDeg[h]-b.angularResidualDeg[h]));
            radSs+=sq(a.radialPctDial[h]-b.radialPctDial[h]);
            common++;
        }
        if(common<7)return false;
        double angRms=Math.sqrt(angSs/common);
        double radRms=Math.sqrt(radSs/common);
        return angRms<=0.35&&radRms<=0.45;
    }

    static Measure average(List<Measure> cluster){
        Measure out=new Measure(cluster.isEmpty()?Double.NaN:cluster.get(0).quality,cluster.isEmpty()?Double.NaN:cluster.get(0).perspective);
        for(int h=1;h<=12;h++){
            double aq=0,av=0,rv=0;
            for(Measure m:cluster){
                if(!m.present[h])continue;
                out.present[h]=true;
                aq++;
                av+=m.angularResidualDeg[h];
                rv+=m.radialPctDial[h];
            }
            if(aq>0){
                out.angularResidualDeg[h]=av/aq;
                out.radialPctDial[h]=rv/aq;
            }
        }
        return out;
    }

    static double[] invert3x3(double[] h){
        if(h==null||h.length!=9)return null;
        double a=h[0],b=h[1],c=h[2],d=h[3],e=h[4],f=h[5],g=h[6],i=h[7],j=h[8];
        double A=e*j-f*i,B=-(d*j-f*g),C=d*i-e*g;
        double D=-(b*j-c*i),E=a*j-c*g,F=-(a*i-b*g);
        double G=b*f-c*e,H=-(a*f-c*d),I=a*e-b*d;
        double det=a*A+b*B+c*C;
        if(Math.abs(det)<1e-12)return null;
        double inv=1.0/det;
        return new double[]{A*inv,D*inv,G*inv,B*inv,E*inv,H*inv,C*inv,F*inv,I*inv};
    }

    static double[] project(double[] h,double x,double y){
        if(h==null||h.length!=9)return null;
        double w=h[6]*x+h[7]*y+h[8];
        if(Math.abs(w)<1e-12)return null;
        return new double[]{(h[0]*x+h[1]*y+h[2])/w,(h[3]*x+h[4]*y+h[5])/w};
    }

    private static Measure measure(Bitmap bitmap){
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(bitmap,src);
            Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class);
            Object dial=detect.invoke(null,src);
            if(dial==null)return null;
            double q=num(dial,"quality"),cx=num(dial,"x"),cy=num(dial,"y"),dr=num(dial,"r");
            if(!(dr>0))return null;
            Method markersM=method("measureMarkerSet",Mat.class,dial.getClass());
            Object set=markersM.invoke(null,src,dial);
            Method perspectiveM=method("perspectiveEquivalent",Mat.class,dial.getClass());
            double p=((Number)perspectiveM.invoke(null,src,dial)).doubleValue();
            @SuppressWarnings("unchecked") List<Object> markers=(List<Object>)field(set,"markers");
            if(markers==null||markers.size()<7)return null;
            double global=num(set,"globalRotation");
            Pose pose=solvePose(src,cx,cy,dr,global);
            if(pose==null)return null;
            Measure m=new Measure(q,p);
            int usable=0;
            for(Object x:markers){
                int h=(int)Math.round(num(x,"hour"));
                if(h<1||h>12)continue;
                double radius=num(x,"radius");
                double angular=num(x,"angular");
                double actualDeg=QcExtendedMath.hourAngleDeg(h)+global+angular;
                double rr=Math.toRadians(actualDeg);
                double[] norm=project(pose.inverseHomography,cx+Math.sin(rr)*radius,cy-Math.cos(rr)*radius);
                if(norm==null||!Double.isFinite(norm[0])||!Double.isFinite(norm[1]))continue;
                m.present[h]=true;
                m.angularResidualDeg[h]=QcExtendedMath.wrap180(normalizedClockAngleDeg(norm[0],norm[1])-QcExtendedMath.hourAngleDeg(h));
                m.radialPctDial[h]=100.0*Math.hypot(norm[0],norm[1]);
                usable++;
            }
            return usable>=7?m:null;
        }catch(Throwable ignored){return null;}finally{src.release();}
    }

    private static Pose solvePose(Mat bgr,double cx,double cy,double dr,double rollDeg){
        Mat gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);
            RotatedRect ellipse=findDialEllipse(edges,cx,cy,dr);
            if(ellipse==null)return null;
            double[] h=homographyFromUnitSquare(ellipseCardinalPoints(ellipse,rollDeg));
            double[] inv=invert3x3(h);
            return h!=null&&inv!=null?new Pose(h,inv):null;
        }finally{gray.release();blur.release();edges.release();}
    }

    private static RotatedRect findDialEllipse(Mat edges,double cx,double cy,double dr){
        List<MatOfPoint> contours=new ArrayList<>();
        Mat hierarchy=new Mat();
        Mat contourInput=edges.clone();
        try{
            Imgproc.findContours(contourInput,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;
            for(MatOfPoint c:contours){
                if(c.rows()<40)continue;
                MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);
                    double a=Math.max(e.size.width,e.size.height),b=Math.min(e.size.width,e.size.height);
                    if(a<1.55*dr||a>2.35*dr||b<1.35*dr||b>2.30*dr)continue;
                    double dc=Math.hypot(e.center.x-cx,e.center.y-cy)/dr;
                    if(dc>0.22)continue;
                    double sizeErr=Math.abs(a/(2.0*dr)-1.0)+Math.abs(b/(2.0*dr)-1.0);
                    double ratio=b/Math.max(1.0,a);
                    if(ratio<0.72)continue;
                    double score=2.4*dc+sizeErr;
                    if(score<bestScore){bestScore=score;best=e;}
                }catch(Throwable ignored){}finally{f.release();}
            }
            return best;
        }finally{
            contourInput.release();
            for(MatOfPoint c:contours)c.release();
            hierarchy.release();
        }
    }

    private static double[] homographyFromUnitSquare(Point[] dst){
        MatOfPoint2f srcPts=new MatOfPoint2f(new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0));
        MatOfPoint2f dstPts=new MatOfPoint2f(dst);
        Mat h=new Mat();
        try{
            h=Calib3d.findHomography(srcPts,dstPts,0);
            return h.empty()?null:matToArray(h);
        }finally{
            srcPts.release();
            dstPts.release();
            h.release();
        }
    }

    private static Point[] ellipseCardinalPoints(RotatedRect e,double rollDeg){
        return new Point[]{
                rayEllipseIntersection(e,Math.toRadians(rollDeg-90.0)),
                rayEllipseIntersection(e,Math.toRadians(rollDeg)),
                rayEllipseIntersection(e,Math.toRadians(rollDeg+90.0)),
                rayEllipseIntersection(e,Math.toRadians(rollDeg+180.0))
        };
    }

    private static Point rayEllipseIntersection(RotatedRect e,double imageAngle){
        double rx=Math.max(1e-6,e.size.width/2.0),ry=Math.max(1e-6,e.size.height/2.0);
        double t=Math.toRadians(e.angle);
        double dx=Math.cos(imageAngle),dy=Math.sin(imageAngle);
        double lx=dx*Math.cos(t)+dy*Math.sin(t);
        double ly=-dx*Math.sin(t)+dy*Math.cos(t);
        double denom=Math.sqrt((lx*lx)/(rx*rx)+(ly*ly)/(ry*ry));
        double s=denom>1e-9?1.0/denom:0.0;
        return new Point(e.center.x+s*dx,e.center.y+s*dy);
    }

    private static double[] matToArray(Mat m){
        if(m==null||m.empty())return null;
        double[] out=new double[9];
        m.get(0,0,out);
        return out;
    }

    private static double normalizedClockAngleDeg(double x,double y){
        double a=Math.toDegrees(Math.atan2(x,-y));
        if(a<0)a+=360.0;
        return a;
    }

    private static double sq(double x){return x*x;}
    private static Method method(String name,Class<?>...types)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String name)throws Exception{return ((Number)field(o,name)).doubleValue();}
    private CanonicalGmtGeometryAnalyzer(){}
}
