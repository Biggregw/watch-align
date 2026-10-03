package com.watchalign.mobile;
import android.graphics.Bitmap;
import java.io.*;import java.nio.file.*;import java.util.*;

/**
 * Calibrator measurement through the configured PRODUCTION model route.
 *
 * The exact model is an explicit argument. This prevents a future Submariner reference from being
 * silently measured as a 124060. A model is accepted here only when WatchAlignCoreV13 routes it
 * through the Submariner production analyser; unsupported models fail before any CSV is written.
 *
 * Only values that pass the app's own reliability gates are written
 * (Sub124060Calibration.assess(r,false): same gates, no verdict). A gated-out value is left blank,
 * so the calibrator can never fit limits to measurements the app would not report.
 *
 *   tools/desktop-harness/run.sh CalibMeasure <model> <list.tsv: physical_watch_id \t image_path> <out.csv>
 *
 * Column names are the app keys used in calibration/models/*.json.
 */
public class CalibMeasure{
 static final String[] KEYS={"twelve.rotation_deg","twelve.gap_r","twelve.centring_w","round.ring_rho","round.spacing_rms_deg","baton.3_9_line_offset_r","axis.12_6_line_offset_r"};
 public static void main(String[] a)throws Exception{
  if(a.length!=3)throw new IllegalArgumentException("usage: CalibMeasure <model> <list.tsv> <out.csv>");
  String model=a[0].trim().toUpperCase(Locale.US);
  if(model.isEmpty())throw new IllegalArgumentException("model is required");
  if(!Sub124060QcAnalyzer.supports(model))
   throw new IllegalArgumentException("model "+model+" is not yet routed through the Submariner production analyser");
  nu.pattern.OpenCV.loadLocally();
  try(PrintWriter w=new PrintWriter(new FileWriter(a[2]))){
   w.println("physical_watch_id,model,path,dial_source,dial_reproducible,pose_tilt_deg,"+String.join(",",KEYS));
   for(String line:Files.readAllLines(Path.of(a[1]))){
    String[] f=line.split("\t");if(f.length<2||f[1].trim().isEmpty())continue;
    String id=f[0].trim(),p=f[1].trim();
    double[] v=new double[KEYS.length];Arrays.fill(v,Double.NaN);
    String src="unreadable",repro="";double tilt=Double.NaN;
    try{
     Bitmap b=Load.photo(p);
     if(b!=null){
      FullResSource full=Load.fullSource(p);
      WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(b,Collections.<Bitmap>emptyList(),model,full);
      Sub124060QcAnalyzer.Result s=r.sub124060;
      if(s==null)throw new IllegalStateException("configured model did not return Submariner measurements");
      src=String.valueOf(s.dialSource);repro=String.valueOf(s.dialReproducible);
      if(s.markerPose!=null&&s.markerPose.valid)tilt=s.markerPose.tiltDeg;
      Sub124060Calibration.Assessment c=Sub124060Calibration.assess(s,false);
      if(c.rotationMeasured)v[0]=s.rotationDeg;
      if(s.gapWithheld==null&&Boolean.TRUE.equals(s.gapResizeStable)&&Double.isFinite(s.gapR))v[1]=s.gapR;
      if(c.centringMeasured)v[2]=s.centringW;
      if(c.roundRingMeasured)v[3]=c.roundRingRho;
      if(c.roundSpacingMeasured)v[4]=c.roundSpacingRmsDeg;
      if(c.baton39Measured)v[5]=c.baton39LineOffsetR;
      if(c.axis126Measured)v[6]=c.axis126LineOffsetR;
     }
    }catch(Throwable t){src="error:"+t.getClass().getSimpleName();}
    StringBuilder o=new StringBuilder();
    o.append(id).append(',').append(model).append(',').append(p.replace(',',';')).append(',').append(src).append(',').append(repro).append(',').append(fmt(tilt));
    for(double x:v)o.append(',').append(fmt(x));
    w.println(o);w.flush();
   }
  }
 }
 static String fmt(double x){return Double.isFinite(x)?String.format(Locale.US,"%.6f",x):"";}
}
