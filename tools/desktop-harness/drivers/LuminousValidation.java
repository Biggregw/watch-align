package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

/** Bounded development/held-out execution of the exact opt-in Android measurement. */
public class LuminousValidation {
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();List<String> lines=Files.readAllLines(Path.of(a[0]));Path dest=Path.of(a[1]);Files.createDirectories(dest);
  boolean score=a.length>2&&a[2].equals("score");LuminousReference ref=score?LuminousReference.load(ModelSpec.directory(HarnessModel.assetsRoot()),HarnessModel.spec().id):null;
  try(PrintWriter csv=new PrintWriter(Files.newBufferedWriter(dest.resolve("markers.csv")))){
   csv.println("photo_id,physical_watch_id,split,model,hour,key,shape,dial_radius_px,source_width,source_height,assessed,reason,boundary_spread_px,rotation_spread_deg,edge_coverage,contrast,expected_area_R2,actual_area_R2,extra_pct,missing_pct,total_pct,rotation_deg,dx_R,dy_R,size_pct,major_span_pct,minor_span_pct,local_polygon,expected_polygon,detected_polygon,outer_status,outer_rotation_deg,outer_area_reason,outer_total_pct");
   for(int row=1;row<lines.size();row++){
    String[] f=lines.get(row).split(",",-1);String id=f[0],watch=f[1],split=f[2];Bitmap photo=Alpha96Calib.loadAlpha96(f[3]);Alpha99Pipeline.Output outer=Alpha99Pipeline.run(photo,HarnessModel.spec(),HarnessModel.ref());
    if(!outer.ok())throw new IllegalStateException("Bounded input pose failed: "+id);String snapshot=snapshot(outer);Map<Integer,LuminousMarkerDetector.Detection> det=LuminousMarkerDetector.detect(photo,outer);
    LuminousMeasurement measurements=score?LuminousMeasurement.run(photo,outer,ref):null;
    if(!snapshot.equals(snapshot(outer)))throw new AssertionError("Experimental view mutated existing QC: "+id);
    // Exercise complete production output before/after on one original per run, not just object identity.
    if(row==1&&score&&!snapshot.equals(snapshot(Alpha99Pipeline.run(photo,HarnessModel.spec(),HarnessModel.ref()))))throw new AssertionError("Outer rerun differs: "+id);
    Mat rgba=new Mat();Utils.bitmapToMat(photo,rgba);
    for(ModelSpec.Marker m:outer.model.markers){LuminousMarkerDetector.Detection d=det.get(m.hour);LuminousMeasurement.Marker measured=score?measurements.markers.get(m.hour):null;
     if(score&&measured.assessed()&&Math.abs(measured.score.extraPct+measured.score.missingPct-measured.score.mismatchPct)>1e-10)throw new AssertionError("Filled denominator");
     MarkerPolygonMismatch.Score s=measured==null?null:measured.score;Alpha99Findings.Finding finding=null;for(Alpha99Findings.Finding q:outer.summary.all)if(!q.dialWide&&q.hour==m.hour)finding=q;
     MarkerMismatchResearch.Result out=outer.mismatch.get(m.hour);Alpha94MarkerMeasurement.Marker om=outer.measurement.atHour(m.hour);
     Object[] values={id,watch,split,outer.model.id,m.hour,LuminousReference.key(m),m.kind(),outer.measurement.dialRadiusPx,photo.getWidth(),photo.getHeight(),score?measured.assessed():d.usable(),score?measured.reason:d.reason,d.boundarySpreadPx,d.rotationSpreadDeg,d.edgeCoverage,d.contrast,s==null?"":s.expectedArea,s==null?"":s.actualArea,s==null?"":s.extraPct,s==null?"":s.missingPct,s==null?"":s.mismatchPct,measured==null?"":measured.rotationDeg,measured==null?"":measured.dxR,measured==null?"":measured.dyR,measured==null?"":measured.sizePct,measured==null?"":measured.lengthPct,measured==null?"":measured.widthPct,d.usable()?polygon(LuminousGeometry.local(d.polygon,m,outer.measurement.ring.model)):"",measured==null?"":polygon(measured.expected),polygon(d.polygon),finding==null?"":finding.status,om==null?"":om.rotationDeg,out==null?"":out.reason,out==null||!out.trusted()?"":out.score.mismatchPct};
     emit(csv,values);
     // First two held-out photos: all shapes, including explicit withholding; replica always all markers.
     if(score&&(split.equals("replica")||split.equals("heldout")&&row<=2)&&(m.hour==12||m.hour==6||m.hour==9||m.hour==2)){
      double[] c=ExpectedMarkerOutline.moved(new double[][]{m.masterPoint()},outer.measurement.ring.model)[0];Mat crop=Alpha98Closeups.render(rgba,outer.pose.homography,c[0],c[1],.19);Bitmap plain=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);Utils.matToBitmap(crop,plain);crop.release();Bitmap marked=plain.copy(Bitmap.Config.ARGB_8888,true);MarkerMismatchOverlay.closeup(marked,c[0],c[1],.19,measured.displayPolygon());
      java.awt.image.BufferedImage gallery=new java.awt.image.BufferedImage(960,540,java.awt.image.BufferedImage.TYPE_INT_RGB);java.awt.Graphics2D g=gallery.createGraphics();g.drawImage(plain.img,0,0,null);g.drawImage(marked.img,480,0,null);g.setColor(java.awt.Color.WHITE);g.drawString(id+" · "+outer.model.id+" · "+m.hour+" · "+measured.reason,12,502);g.drawString(s==null?"Percentage WITHHELD":String.format(Locale.US,"extra %.3f + missing %.3f = %.3f%%",s.extraPct,s.missingPct,s.mismatchPct),12,524);g.dispose();javax.imageio.ImageIO.write(gallery,"png",dest.resolve(id+"_"+m.hour+".png").toFile());
     }
    }rgba.release();csv.flush();System.err.println("validated "+id+" "+split);
   }
  }
 }
 static String snapshot(Alpha99Pipeline.Output o){StringBuilder b=new StringBuilder(o.technical);for(MarkerMismatchResearch.Result q:o.mismatch.values()){b.append(q.reason);if(q.score!=null)b.append(q.score.mismatchPct).append(polygon(q.score.actualPolygon)).append(polygon(q.expected));}return b.toString();}
 static String polygon(double[][] p){if(p==null)return "";StringBuilder b=new StringBuilder("[");for(int i=0;i<p.length;i++){if(i>0)b.append(',');b.append('[').append(p[i][0]).append(',').append(p[i][1]).append(']');}return b.append(']').toString();}
 static void emit(PrintWriter csv,Object[] values){for(int i=0;i<values.length;i++){if(i>0)csv.print(',');String s=String.valueOf(values[i]);csv.print('"'+s.replace("\"","\"\"")+'"');}csv.println();}
}
