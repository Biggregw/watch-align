package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.File;import java.awt.image.BufferedImage;
/** Prints the raw 6-baton measurements (and 12 gap for context) per photo; optional crop dir as first arg "-crops=DIR". */
public class Six{
 static String fields(GmtHumanSummary.Input s){return String.format(java.util.Locale.US,",%s,%s,%.3f,%.2f,%.1f,%s",s.sixValid,s.sixAttention,s.sixCentring,s.sixRotationDeg,s.sixWidthPx,s.sixStable);}
 public static void main(String[] a)throws Exception{
 nu.pattern.OpenCV.loadLocally();org.opencv.core.Core.setNumThreads(1);
 String crops=null;
 for(String f:a){
  if(f.startsWith("-crops=")){crops=f.substring(7);new File(crops).mkdirs();continue;}
  BufferedImage raw=ImageIO.read(new File(f));if(raw==null)continue;BufferedImage argb=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_INT_ARGB);argb.getGraphics().drawImage(raw,0,0,null);
  Bitmap b=new Bitmap(argb);int max=Math.max(b.getWidth(),b.getHeight());if(max>1600){float s=1600f/max;b=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);}
  GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");
  var x=h.six;String n=f.replaceAll(".*/(rep|gen|e2e)/","$1/");
  if(x==null||!x.valid){System.out.printf("%-58s SIX none (%s) | 12gap %.3f%n",n,x==null?"null":x.reason,h.summary.observedGap);continue;}
  System.out.printf(java.util.Locale.US,"%-58s SIX gap %.3f centring %+.3f rot %+.2f w %.1f len/w %.2f stable %s outer %s | 12gap %.3f 12rot %+.2f%n",n,x.gap,x.centring,x.rotationDeg,x.widthPx,x.lengthPx/x.widthPx,x.stable,x.geometry.outerEdge,h.summary.observedGap,h.summary.rotationDeg);
  if(crops!=null){
   var g=x.geometry;double cx=(g.outerLeft[0]+g.outerRight[0])/2,cy=(g.outerLeft[1]+g.outerRight[1])/2;int half=(int)Math.round(x.widthPx*2.2);
   BufferedImage comp=new BufferedImage(2*half,2*half,BufferedImage.TYPE_INT_RGB);var g2=comp.createGraphics();
   g2.drawImage(b.img,-(int)(cx-half),-(int)(cy-half),null);g2.setColor(java.awt.Color.GREEN);
   double[][] p=g.polygon();for(int i=0;i<4;i++){double[] u=p[i],v=p[(i+1)%4];g2.drawLine((int)(u[0]-cx+half),(int)(u[1]-cy+half),(int)(v[0]-cx+half),(int)(v[1]-cy+half));}
   g2.setColor(java.awt.Color.CYAN);for(double[] t:new double[][]{g.tick31,g.tick30,g.tick29})g2.fillOval((int)(t[0]-cx+half)-2,(int)(t[1]-cy+half)-2,4,4);
   g2.dispose();ImageIO.write(comp,"png",new File(crops+"/"+new File(f).getName().replace(".jpg","")+"_"+Math.abs(f.hashCode()%1000)+".png"));}
 }}}
