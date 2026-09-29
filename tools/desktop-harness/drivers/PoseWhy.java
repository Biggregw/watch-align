package com.watchalign.mobile;
import android.graphics.Bitmap;
/** PoseWhy <image>... : the photo-angle rating line from the app's report. */
public class PoseWhy{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String p:a){
   Bitmap b=Load.photo(p);Load.Human hu=Load.human(b,p);
   var d=hu.h.drawing;double q=(d.dialA>0&&d.dialB>0)?Math.min(d.dialA,d.dialB)/Math.max(d.dialA,d.dialB):Double.NaN;
   System.out.printf(java.util.Locale.US,"%s pose=%s edgeRatio=%.4f edgeTilt=%.1f%n",p.substring(p.lastIndexOf('/')+1),hu.h.poseLabel,q,Math.toDegrees(Math.acos(Math.min(1,q))));
   for(String l:hu.h.report.split("\n"))if(l.startsWith("Perspective")||l.contains("rehaut")||l.contains("ellipse"))System.out.println("  "+l);
  }
 }}
