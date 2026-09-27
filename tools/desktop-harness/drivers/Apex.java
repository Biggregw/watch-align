package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.File;import java.awt.image.BufferedImage;
public class Apex{public static void main(String[] a)throws Exception{
 nu.pattern.OpenCV.loadLocally();
 for(String f:a){
  BufferedImage raw=ImageIO.read(new File(f));BufferedImage argb=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_INT_ARGB);argb.getGraphics().drawImage(raw,0,0,null);
  Bitmap b=new Bitmap(argb);int max=Math.max(b.getWidth(),b.getHeight());if(max>1600){float s=1600f/max;b=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);}
  GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");var g=h.drawing.twelve;
  if(g==null){System.out.println(f+" no triangle");continue;}
  double al=Math.toDegrees(Math.atan2(g.triLeft[1]-g.triTip[1],g.triLeft[0]-g.triTip[0])),ar=Math.toDegrees(Math.atan2(g.triRight[1]-g.triTip[1],g.triRight[0]-g.triTip[0]));
  double apex=Math.abs(al-ar);if(apex>180)apex=360-apex;
  double mx=(g.triLeft[0]+g.triRight[0])/2,my=(g.triLeft[1]+g.triRight[1])/2;
  double axA=Math.toDegrees(Math.atan2(g.triTip[1]-my,g.triTip[0]-mx)),bA=Math.toDegrees(Math.atan2(g.triRight[1]-g.triLeft[1],g.triRight[0]-g.triLeft[0]));
  double perp=axA-bA;while(perp>180)perp-=360;while(perp<-180)perp+=360;
  System.out.printf("%-60s apex %.1f  square %+.2f  outer=%s rot %.2f%n",f.replaceAll(".*/(rep|gen)/","$1/"),apex,perp-90,g.outerEdge,h.summary.rotationDeg);
 }}}
