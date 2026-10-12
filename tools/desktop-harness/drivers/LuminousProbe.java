package com.watchalign.mobile;
import android.graphics.Bitmap;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
public class LuminousProbe {
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();Bitmap b=Alpha96Calib.loadAlpha96(a[0]);Alpha99Pipeline.Output outer=Alpha99Pipeline.run(b,HarnessModel.spec(),HarnessModel.ref());
  if(!outer.ok())throw new IllegalStateException("pose failed");Path dir=Path.of(a[1]);Files.createDirectories(dir);
  Map<Integer,LuminousMarkerDetector.Detection> detections=LuminousMarkerDetector.detect(b,outer);Mat rgba=new Mat();Utils.bitmapToMat(b,rgba);
  for(ModelSpec.Marker m:outer.model.markers){LuminousMarkerDetector.Detection d=detections.get(m.hour);System.out.println(m.hour+" "+d.reason+" spread="+d.boundarySpreadPx+" edges="+d.edgeCoverage);
   double[] c=m.masterPoint();Mat crop=Alpha98Closeups.render(rgba,outer.pose.homography,c[0],c[1],.18);Bitmap image=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);Utils.matToBitmap(crop,image);crop.release();
   if(d.usable()){MarkerPolygonMismatch.Score s=MarkerPolygonMismatch.compare(d.polygon,d.polygon);MarkerMismatchOverlay.closeup(image,c[0],c[1],.18,new MarkerMismatchResearch.Result(s,"measured"));}
   javax.imageio.ImageIO.write(image.img,"png",dir.resolve(m.hour+".png").toFile());
  }rgba.release();
 }
}
