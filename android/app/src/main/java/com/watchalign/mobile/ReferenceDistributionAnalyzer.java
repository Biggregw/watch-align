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
 * Alpha20 reference-relative QC. Measures the watch and each usable genuine image in the
 * same detector coordinate system, then compares per-marker angular/radial values with a
 * robust genuine baseline (median + MAD). This avoids treating one theoretical geometry
 * or one genuine photograph as absolute truth.
 */
final class ReferenceDistributionAnalyzer {
    static final class Result {
        final String report;
        final int usableReferences;
        Result(String report,int usableReferences){this.report=report;this.usableReferences=usableReferences;}
    }
    private static final class Measure {
        final double quality, perspective;
        final double[] angular=new double[13];
        final double[] radial=new double[13];
        final boolean[] present=new boolean[13];
        Measure(double q,double p){quality=q;perspective=p;for(int i=0;i<13;i++){angular[i]=Double.NaN;radial[i]=Double.NaN;}}
    }

    static Result analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        if(references==null||references.isEmpty())return new Result("",0);
        Measure wm=measure(watch);
        if(wm==null)return new Result("\n\nGenuine baseline comparison unavailable: watch geometry could not be measured reliably.",0);

        List<Measure> refs=new ArrayList<>();
        for(Bitmap b:references){
            if(b==null)continue;
            Measure r=measure(b);
            if(r==null||r.quality<0.56)continue;
            if(Double.isFinite(wm.perspective)&&Double.isFinite(r.perspective)&&Math.abs(wm.perspective-r.perspective)>10.0)continue;
            refs.add(r);
        }
        if(refs.isEmpty())return new Result("\n\nGenuine baseline comparison unavailable: no reference photo passed geometry/perspective validation.",0);

        StringBuilder out=new StringBuilder();
        out.append("\n\nGENUINE BASELINE COMPARISON\n");
        out.append(String.format(Locale.US,"Usable genuine references: %d%s\n",refs.size(),refs.size()>=5?" (distribution mode)":refs.size()>1?" (small-sample robust baseline)":" (single-reference fallback)"));
        out.append("Per-marker deltas are QC minus genuine median. Radial values are relative to each watch's marker-ring consensus, so image scale is removed.\n");

        int findings=0;
        for(int h=1;h<=12;h++){
            if(!wm.present[h])continue;
            double[] av=new double[refs.size()],rv=new double[refs.size()];int an=0,rn=0;
            for(Measure r:refs){if(r.present[h]){av[an++]=r.angular[h];rv[rn++]=r.radial[h];}}
            GenuineBaselineStats.Summary as=GenuineBaselineStats.summarize(av,an);
            GenuineBaselineStats.Summary rs=GenuineBaselineStats.summarize(rv,rn);
            if(as.n==0||rs.n==0)continue;
            double ad=as.delta(wm.angular[h]),rd=rs.delta(wm.radial[h]);
            int asev=GenuineBaselineStats.severity(wm.angular[h],as,0.45);
            int rsev=GenuineBaselineStats.severity(wm.radial[h],rs,0.90);
            int sev=Math.max(asev,rsev);
            String state=sev==2?"OUTSIDE GEN RANGE":sev==1?"CHECK":"normal";
            out.append(String.format(Locale.US,
                    "%2d o'clock: angular Δ %+5.2f°; radial Δ %+5.2f%%  [%s; n=%d]\n",
                    h,ad,rd,state,Math.min(as.n,rs.n)));
            if(sev>0)findings++;
        }
        if(findings==0)out.append("Reference-relative marker result: no statistically unusual marker positions detected.\n");
        else out.append(String.format(Locale.US,"Reference-relative marker result: %d marker%s outside the robust genuine tolerance.\n",findings,findings==1?"":"s"));
        out.append("Tolerance uses median/MAD with minimum floors to avoid false precision from small samples. A larger set of independently photographed genuine watches increases confidence.\n");
        return new Result(out.toString(),refs.size());
    }

    private static Measure measure(Bitmap bitmap){
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(bitmap,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class);Object dial=detect.invoke(null,src);if(dial==null)return null;
            double q=num(dial,"quality");
            Method markersM=method("measureMarkerSet",Mat.class,dial.getClass());Object set=markersM.invoke(null,src,dial);
            Method perspectiveM=method("perspectiveEquivalent",Mat.class,dial.getClass());double p=((Number)perspectiveM.invoke(null,src,dial)).doubleValue();
            @SuppressWarnings("unchecked") List<Object> markers=(List<Object>)field(set,"markers");
            if(markers==null||markers.size()<7)return null;
            Measure m=new Measure(q,p);
            for(Object x:markers){int h=(int)Math.round(num(x,"hour"));if(h<1||h>12)continue;m.present[h]=true;m.angular[h]=num(x,"angular");m.radial[h]=num(x,"radial");}
            return m;
        }catch(Throwable ignored){return null;}finally{src.release();}
    }

    private static Method method(String name,Class<?>...types)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String name)throws Exception{return ((Number)field(o,name)).doubleValue();}
    private ReferenceDistributionAnalyzer(){}
}
