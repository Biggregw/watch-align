package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Canonical GMT marker geometry for 126710-family dials.
 *
 * Angular geometry is mathematically fixed: hour centres are exactly 30 degrees apart.
 * Radial placement is measured independently against the detected dial radius, not the
 * marker-ring consensus, so a high/low marker cannot move the datum used to judge itself.
 * Genuine references calibrate detector bias and real-world tolerance around that geometry.
 */
final class CanonicalGmtGeometryAnalyzer {
    static final class Result {
        final String report;
        final int usableReferences;
        Result(String report,int usableReferences){this.report=report;this.usableReferences=usableReferences;}
    }

    private static final class Measure {
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

    static boolean supports(String modelRef){
        if(modelRef==null)return false;
        String s=modelRef.toUpperCase(Locale.US);
        return s.startsWith("126710");
    }

    static Result analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        if(!supports(modelRef))return new Result("",0);
        Measure wm=measure(watch);
        if(wm==null)return new Result("\n\nCANONICAL GMT GEOMETRY\nUnavailable: watch marker geometry could not be measured reliably.",0);

        List<Measure> refs=new ArrayList<>();
        if(references!=null){
            for(Bitmap b:references){
                if(b==null)continue;
                Measure r=measure(b);
                if(r==null||r.quality<0.56)continue;
                if(Double.isFinite(wm.perspective)&&Double.isFinite(r.perspective)&&Math.abs(wm.perspective-r.perspective)>10.0)continue;
                refs.add(r);
            }
        }

        StringBuilder out=new StringBuilder();
        out.append("\n\nCANONICAL GMT GEOMETRY\n");
        out.append("Angular datum: exact 30° hour grid after photo-roll removal. Radial datum: marker centroid radius as % of independently detected dial radius. The marker ring is not used as the radial datum.\n");
        out.append(String.format(Locale.US,"Usable genuine references for tolerance calibration: %d%s\n",
                refs.size(),refs.size()>=5?" (distribution)":refs.size()>=3?" (small sample)":refs.size()>0?" (insufficient for statistical radial verdicts)":""));

        int flagged=0, assessed=0, insufficient=0;
        for(int h=1;h<=12;h++){
            if(h==3||!wm.present[h])continue;

            // Exact angular geometry: after global roll removal the canonical residual is zero.
            double angular=wm.angularResidualDeg[h];
            double[] aa=new double[Math.max(1,refs.size()*2)];
            int an=0;
            for(Measure r:refs)if(r.present[h])aa[an++]=r.angularResidualDeg[h];
            GenuineBaselineStats.Summary as=GenuineBaselineStats.summarize(aa,an);
            double angularBias=(as.n>0&&Double.isFinite(as.median))?as.median:0.0;
            double correctedAngular=angular-angularBias;
            int angularSeverity=canonicalAngularSeverity(correctedAngular,as);

            // Independent radial geometry. Mirror-pair pooling is valid for circular GMT markers
            // and improves sample size without letting the watch under test define its own ring.
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
        if(insufficient>0)out.append("Markers with fewer than 3 usable genuine radial samples are reported numerically but are not given a radial pass/fail.\n");
        out.append("Method note: exact angular spacing comes from the 12-hour dial geometry. Radial target values are model-calibrated from genuine images because Rolex does not publish marker centroid radii; genuine samples define detector bias/tolerance, not the angular layout itself.\n");
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
        // Units are percentage points of dial radius. Keep a detector-repeatability floor,
        // but do not use the much wider marker-ring-relative floor from alpha20.
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

    private static Measure measure(Bitmap bitmap){
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(bitmap,src); Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class); Object dial=detect.invoke(null,src); if(dial==null)return null;
            double q=num(dial,"quality"),dr=num(dial,"r"); if(!(dr>0))return null;
            Method markersM=method("measureMarkerSet",Mat.class,dial.getClass()); Object set=markersM.invoke(null,src,dial);
            Method perspectiveM=method("perspectiveEquivalent",Mat.class,dial.getClass()); double p=((Number)perspectiveM.invoke(null,src,dial)).doubleValue();
            @SuppressWarnings("unchecked") List<Object> markers=(List<Object>)field(set,"markers");
            if(markers==null||markers.size()<7)return null;
            Measure m=new Measure(q,p);
            for(Object x:markers){
                int h=(int)Math.round(num(x,"hour")); if(h<1||h>12)continue;
                double radius=num(x,"radius");
                m.present[h]=true;
                m.angularResidualDeg[h]=num(x,"angular");
                m.radialPctDial[h]=100.0*radius/dr;
            }
            return m;
        }catch(Throwable ignored){return null;}finally{src.release();}
    }

    private static Method method(String name,Class<?>...types)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String name)throws Exception{return ((Number)field(o,name)).doubleValue();}
    private CanonicalGmtGeometryAnalyzer(){}
}
