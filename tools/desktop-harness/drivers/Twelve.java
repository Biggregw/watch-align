package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;
/** Twelve <image>... : 12 geometry along the 59-01 chord (alpha61 off-centre check). */
public class Twelve{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String p:a){
   Bitmap b=Load.photo(p);Load.Human hu=Load.human(b,p);var d=hu.h.drawing;var g=d.twelve;
   if(g==null){System.out.println(p+": no 12");continue;}
   final double L=Math.hypot(g.tick01[0]-g.tick59[0],g.tick01[1]-g.tick59[1]),ux=(g.tick01[0]-g.tick59[0])/L,uy=(g.tick01[1]-g.tick59[1])/L;
   java.util.function.Function<double[],Double> t=q->(q[0]-g.tick59[0])*ux+(q[1]-g.tick59[1])*uy;
   double w=Math.hypot(g.triRight[0]-g.triLeft[0],g.triRight[1]-g.triLeft[1]);
   System.out.printf(Locale.US,"%s%n  along chord (preview px): 59 0.0, 60 %.1f, 01 %.1f | tri L %.1f R %.1f tip %.1f | mid %.1f, width %.1f%n  60 vs chord mid %+.2f px; tri mid vs 60 %+.2f, vs chord mid %+.2f; tip vs 60 %+.2f%n",
    p.substring(p.lastIndexOf('/')+1),t.apply(g.tick60),L,t.apply(g.triLeft),t.apply(g.triRight),t.apply(g.triTip),(t.apply(g.triLeft)+t.apply(g.triRight))/2,w,
    t.apply(g.tick60)-L/2,(t.apply(g.triLeft)+t.apply(g.triRight))/2-t.apply(g.tick60),(t.apply(g.triLeft)+t.apply(g.triRight))/2-L/2,t.apply(g.triTip)-t.apply(g.tick60));
  }
 }}
