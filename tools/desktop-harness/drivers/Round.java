package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;
/** Round <image>... : prints each round marker's measurement (alpha61). */
public class Round{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String p:a){
   Bitmap b=Load.photo(p);long t0=System.nanoTime();
   Load.Human hu=Load.human(b,p);GmtHumanQcAnalyzerV2.Result h=hu.h;if(hu.crop!=null){System.out.println(hu.crop.describe());}
   System.out.printf(Locale.US,"== %s  %d ms  pose %s%n",p,(System.nanoTime()-t0)/1000000,h.poseLabel);
   for(GmtRoundMarkerAnalyzer.Marker m:h.round)System.out.println(line(m));
   String out=System.getProperty("wa.round.png");if(out!=null)strip(b,h,out.replace("%",new java.io.File(p).getParentFile().getName()+"_"+new java.io.File(p).getName().replace(".jpg","")));
  }
 }
 static void strip(Bitmap b,GmtHumanQcAnalyzerV2.Result h,String out)throws Exception{
  int Z=5,half=0;for(GmtRoundMarkerAnalyzer.Marker m:h.round)if(m.found)half=Math.max(half,(int)Math.round(m.radiusPx*1.9));
  if(half==0)return;int W=2*half*Z;
  java.awt.image.BufferedImage o=new java.awt.image.BufferedImage(W*4,W*2,java.awt.image.BufferedImage.TYPE_INT_RGB);
  java.awt.Graphics2D g=o.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
  int k=0;for(GmtRoundMarkerAnalyzer.Marker m:h.round){
   int ox=(k%4)*W,oy=(k/4)*W;k++;if(!m.found){g.setColor(java.awt.Color.RED);g.drawString(m.hour+" "+m.reason,ox+5,oy+20);continue;}
   double x0=m.x-half,y0=m.y-half;
   g.drawImage(b.img,ox,oy,ox+W,oy+W,(int)Math.round(x0),(int)Math.round(y0),(int)Math.round(x0)+2*half,(int)Math.round(y0)+2*half,null);
   double sx=Math.round(x0),sy=Math.round(y0);
   java.util.function.DoubleUnaryOperator X=v->ox+(v-sx)*Z,Y=v->oy+(v-sy)*Z;
   g.setColor(m.stable?java.awt.Color.GREEN:java.awt.Color.ORANGE);g.setStroke(new java.awt.BasicStroke(1.5f));
   g.draw(new java.awt.geom.Ellipse2D.Double(X.applyAsDouble(m.x-m.radiusPx),Y.applyAsDouble(m.y-m.radiusPx),2*m.radiusPx*Z,2*m.radiusPx*Z));
   g.setColor(java.awt.Color.CYAN);
   for(double[] t:new double[][]{m.tickBefore,m.tickCentre,m.tickAfter})g.fill(new java.awt.geom.Ellipse2D.Double(X.applyAsDouble(t[0])-4,Y.applyAsDouble(t[1])-4,8,8));
   g.draw(new java.awt.geom.Line2D.Double(X.applyAsDouble(m.tickBefore[0]),Y.applyAsDouble(m.tickBefore[1]),X.applyAsDouble(m.tickAfter[0]),Y.applyAsDouble(m.tickAfter[1])));
   g.setColor(java.awt.Color.YELLOW);g.drawString(String.format(Locale.US,"%d off %+.3f gap %.3f",m.hour,m.offset,m.gap),ox+5,oy+15);
  }
  g.dispose();javax.imageio.ImageIO.write(o,"png",new java.io.File(out));
 }
 static String line(GmtRoundMarkerAnalyzer.Marker m){
  if(!m.found)return String.format(Locale.US,"%2d  NOT FOUND %s",m.hour,m.reason);
  return String.format(Locale.US,"%2d  d %5.1fpx R/r %.3f off %+.3f (T %+.3f) gap %+.3f ins %.3f size %.3f rej %.2f con %3.0f ang %+.1f tick %.0f/%.2f/%.0f %s | rs off %+.3f..%+.3f gap %+.3f..%+.3f %s",
   m.hour,m.diameterPx(),m.outerOverDialR,m.offset,m.offsetFromCentreTick,m.gap,m.inset,m.sizeRatio,m.rejectFraction,m.contrast,m.angleFromExpectedDeg,m.tickScore,m.tickPitchDeg,m.ticksInferred,
   m.stable?"stable":"LOW "+m.lowReason,m.offMin,m.offMax,m.gapMin,m.gapMax,m.stabilitySameEdge?"same":"DIFF");
 }
}
