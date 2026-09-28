package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;
public class RoundSeeds{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  Bitmap b=Load.photo(a[0]);GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");
  var d=h.drawing;GmtRoundMarkerAnalyzer.DialFrame f=new GmtRoundMarkerAnalyzer.DialFrame(d.dialCx,d.dialCy,d.dialA,d.dialB,d.dialAngleDeg);
  System.out.printf(Locale.US,"dial %.1f %.1f a %.1f b %.1f ang %.1f%n",d.dialCx,d.dialCy,d.dialA,d.dialB,d.dialAngleDeg);
  java.awt.image.BufferedImage o=new java.awt.image.BufferedImage(b.img.getWidth(),b.img.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
  java.awt.Graphics2D g=o.createGraphics();g.drawImage(b.img,0,0,null);g.setColor(java.awt.Color.MAGENTA);g.setStroke(new java.awt.BasicStroke(3));
  double[] t60=d.twelve!=null?d.twelve.tick60:null;double p12=t60!=null?f.phiOf(t60[0],t60[1]):f.phiOf(f.cx,f.cy-f.r);
  for(int i=0;i<360;i+=2){double[] p=f.at(Math.toRadians(i),1),q=f.at(Math.toRadians(i+2),1);g.drawLine((int)p[0],(int)p[1],(int)q[0],(int)q[1]);}
  for(int hh=0;hh<12;hh++){double[] p=f.at(p12+Math.toRadians(hh*30),0.816);g.drawOval((int)p[0]-6,(int)p[1]-6,12,12);}
  g.dispose();javax.imageio.ImageIO.write(o,"jpg",new java.io.File(a[1]));
 }}
