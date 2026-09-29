package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;
/** EdgeDbg <image> : histogram of edge-candidate radii per round marker. */
public class EdgeDbg{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  Bitmap b=Load.photo(a[0]);Load.Human hu=Load.human(b,a[0]);
  for(GmtRoundMarkerAnalyzer.Marker m:hu.h.round){
   if(!m.found||m.edgePts==null){System.out.println(m.hour+" -");continue;}
   int[] h=new int[40];int rays=0;
   for(double[] q:m.edgePts){rays++;for(int i=0;i+1<q.length;i+=2){int k=(int)Math.round(Math.hypot(q[i]-m.x,q[i+1]-m.y));if(k>=0&&k<40)h[k]++;}}
   StringBuilder sb=new StringBuilder();for(int k=10;k<40;k++)sb.append(k).append(':').append(h[k]).append(' ');
   System.out.printf(Locale.US,"%2d r=%.1f rays=%d  %s%n",m.hour,m.radiusPx,rays,sb);
  }
 }}
