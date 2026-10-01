package com.watchalign.mobile;
import android.graphics.Bitmap;
import org.opencv.android.Utils;import org.opencv.core.Mat;import org.opencv.imgproc.Imgproc;
import java.util.*;

/**
 * GmtSharedGeometryCheck <image>...
 * Proves TwelveLocalGeometry reproduces the mature GMT 12 geometry from the exact same landmarks.
 * It does not touch production GMT code. Any non-zero numeric delta is printed and exits 1.
 */
public class GmtSharedGeometryCheck{
 static final double EPS=1e-9;
 static boolean same(double a,double b){return Double.doubleToLongBits(a)==Double.doubleToLongBits(b)||Math.abs(a-b)<=EPS;}
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();int valid=0,bad=0,skip=0;
  for(String p:a){
   Bitmap preview=Load.photo(p);if(preview==null){skip++;continue;}
   GmtDialCrop.Crop crop=GmtDialCrop.make(preview,Load.fullSource(p));
   Bitmap srcBmp=crop!=null?crop.bitmap:preview;
   Mat src=new Mat();
   try{
    Utils.bitmapToMat(srcBmp,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
    GmtDialSeedAnalyzer.Result seed=GmtDialSeedAnalyzer.analyse(src);
    if(!seed.valid||!(seed.r>20)||seed.quality<0.45){skip++;continue;}
    double cx=seed.x,cy=seed.y,r=seed.r;
    DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(src,cx,cy,r);
    if(edge!=null){cx=edge.cx;cy=edge.cy;r=edge.meanRadius();}
    GmtTwelveLandmarkAnalyzer.Result g=GmtTwelveLandmarkAnalyzer.analyse(src,cx,cy,r);
    if(!g.valid||g.geometry==null){skip++;continue;}
    TwelveLocalGeometry.Result q=TwelveLocalGeometry.measure(g.geometry.triLeft,g.geometry.triRight,g.geometry.triTip,
      g.geometry.tick59,g.geometry.tick60,g.geometry.tick01,cx,cy);
    valid++;
    boolean ok=q.valid&&same(q.gapOverWidth,g.topClearance)&&same(q.centringOverWidth,g.horizontalOffset)
      &&same(q.rotationDeg,g.wholeAxisErrorDeg)&&same(q.baseEdgeDeg,g.topEdgeErrorDeg)
      &&same(q.leftClearanceOverWidth,g.leftClearance)&&same(q.rightClearanceOverWidth,g.rightClearance)
      &&same(q.sideAsymmetry,g.sideAsymmetry)&&same(q.markerWidth,g.triangleWidthPx)
      &&same(q.axisReferenceDisagreementDeg,g.axisReferenceDisagreementDeg);
    if(!ok){bad++;System.out.printf(Locale.US,"MISMATCH %s gap %.12f/%.12f cent %.12f/%.12f rot %.12f/%.12f base %.12f/%.12f side %.12f/%.12f axis %.12f/%.12f%n",
      p,q.gapOverWidth,g.topClearance,q.centringOverWidth,g.horizontalOffset,q.rotationDeg,g.wholeAxisErrorDeg,q.baseEdgeDeg,g.topEdgeErrorDeg,q.sideAsymmetry,g.sideAsymmetry,q.axisReferenceDisagreementDeg,g.axisReferenceDisagreementDeg);}
   }finally{src.release();}
  }
  System.out.printf(Locale.US,"shared GMT geometry: valid=%d skipped=%d mismatches=%d%n",valid,skip,bad);
  if(valid==0||bad>0)System.exit(1);
 }
}
