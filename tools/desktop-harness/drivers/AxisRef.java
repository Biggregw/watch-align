package com.watchalign.mobile;
import android.graphics.Bitmap;
/** Compares the 12 rotation against two references: dial centre -> 60 tick (the app) and the square to the 59-01 tick chord (local). */
public class AxisRef{public static void main(String[] a){nu.pattern.OpenCV.loadLocally();
 for(String f:a){Bitmap b=Load.photo(f);var h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");var g=h.drawing.twelve;
  String n=f.replaceAll(".*/","");
  if(g==null){System.out.println(n+" no 12");continue;}
  double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2;
  double ax=Math.toDegrees(Math.atan2(g.triTip[1]-my,g.triTip[0]-mx));              // base mid -> tip (inward)
  double cx=h.drawing.dialCx,cy=h.drawing.dialCy;
  double ref=Math.toDegrees(Math.atan2(cy-g.tick60[1],cx-g.tick60[0]));             // 60 tick -> centre
  double chord=Math.toDegrees(Math.atan2(g.tick01[1]-g.tick59[1],g.tick01[0]-g.tick59[0]));
  double loc=chord+90;                                                                // square to chord, inward (image y down)
  double base=Math.toDegrees(Math.atan2(g.triRight[1]-g.triLeft[1],g.triRight[0]-g.triLeft[0]));
  java.util.function.DoubleUnaryOperator w=d->{while(d>90)d-=180;while(d<=-90)d+=180;return d;};
  System.out.printf(java.util.Locale.US,"%-32s axis-vs-centre %+.2f  axis-vs-chord %+.2f  base-vs-chord %+.2f  chord-vs-centre %+.2f  | app rot %+.2f %s%n",
   n,w.applyAsDouble(ax-ref),w.applyAsDouble(ax-loc),w.applyAsDouble(base-chord),w.applyAsDouble(loc-ref),h.summary.rotationDeg,h.summary.alignment);}}}
