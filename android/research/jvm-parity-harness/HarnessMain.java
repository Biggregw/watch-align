package com.watchalign.mobile;
import android.graphics.Bitmap;
import org.opencv.core.*; import org.opencv.imgcodecs.Imgcodecs; import org.opencv.imgproc.Imgproc;
import java.io.*; import java.util.*;
public class HarnessMain {
  public static void main(String[] a) throws Exception {
    nu.pattern.OpenCV.loadLocally();
    // args: image path, name, [seedH9 comma list for fitter-only check]
    String path=a[0],name=a[1];
    Mat bgr=Imgcodecs.imread(path),rgba=new Mat(),gray=new Mat();
    Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA); Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
    long t0=System.nanoTime();
    AutomaticDialOverlay.Result r=AutomaticDialOverlay.build(new Bitmap(rgba));
    double ms=(System.nanoTime()-t0)/1e6;
    StringBuilder o=new StringBuilder();
    o.append("FULL ").append(name).append(" valid=").append(r.valid).append(" phaseUsed=").append(r.twelvePhaseUsed)
     .append(" ticks=").append(r.detectedTicks).append(" sectors=").append(r.completePairs).append(" rms=").append(r.fitAfter)
     .append(" ms=").append((int)ms).append(" reason=").append(r.reason.replace(' ','_')).append('\n');
    if(r.homography!=null){o.append("H_FULL ");for(double v:r.homography)o.append(v).append(',');o.append('\n');}
    if(a.length>2){
      String[] p=a[2].split(",");Mat seed=new Mat(3,3,CvType.CV_64F);double[] v=new double[9];for(int i=0;i<9;i++)v[i]=Double.parseDouble(p[i]);seed.put(0,0,v);
      t0=System.nanoTime();
      Alpha91MinuteLatticeFitter.Result f=Alpha91MinuteLatticeFitter.fit(gray,seed);
      ms=(System.nanoTime()-t0)/1e6;
      o.append("FIT ").append(name).append(" accepted=").append(f.accepted).append(" ticks=").append(f.ticksUsed).append(" sectors=").append(f.sectorsUsed)
       .append(" rms=").append(f.tickRmsPx).append(" median=").append(f.tickMedianPx).append(" ms=").append((int)ms).append(" reason=").append(f.reason.replace(' ','_')).append('\n');
      if(f.homography!=null&&!f.homography.empty()){double[] m=new double[9];f.homography.get(0,0,m);o.append("H_FIT ");for(double x:m)o.append(x/m[8]).append(',');o.append('\n');}
      // measurement on the PYTHON-frozen H so it is comparable to the research layer
      Alpha94MarkerMeasurement.Report rep=Alpha94MarkerMeasurement.analyse(new Bitmap(rgba),v);
      for(Alpha94MarkerMeasurement.Marker m:rep.markers)
        o.append(String.format(Locale.US,"M %s %d %s usable=%b dx=%.3f dy=%.3f rad=%.3f tan=%.3f rot=%.3f local=%.3f score=%.3f support=%.3f reason=%s%n",
          name,m.hour,m.kind,m.usable,m.rawDxPx,m.rawDyPx,m.radialPx,m.tangentialPx,m.rotationDeg,m.localOffsetPx,m.fitScorePx,m.fitSupport,m.reason.replace(' ','_')));
      Alpha94MarkerMeasurement.Marker t=rep.triangle;
      if(t!=null)o.append(String.format(Locale.US,"T %s 12 triangle usable=%b dx=%.3f dy=%.3f rad=%.3f tan=%.3f rot=%.3f left=%.3f right=%.3f base=%.3f local=%.3f reason=%s%n",
          name,t.usable,t.rawDxPx,t.rawDyPx,t.radialPx,t.tangentialPx,t.rotationDeg,t.leftSideErrDeg,t.rightSideErrDeg,t.baseTiltDeg,t.localOffsetPx,t.reason.replace(' ','_')));
      o.append("SUMMARY\n").append(rep.compactSummary()).append("\nEND\n");
      Alpha94MarkerMeasurement.Ring g=rep.ring;
      o.append(String.format(Locale.US,"RING %s usable=%b n=%d shift=(%.3f,%.3f) scale=%.3f rot=%.3f%n",name,g.usable,g.n,g.shiftXPx,g.shiftYPx,g.scalePct,g.rotationDeg));
    }
    System.out.print(o);
  }
}
