package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.File;
public class E2E{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();org.opencv.core.Core.setNumThreads(1);
  java.awt.image.BufferedImage raw=ImageIO.read(new File(a[0]));
  java.awt.image.BufferedImage argb=new java.awt.image.BufferedImage(raw.getWidth(),raw.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
  argb.getGraphics().drawImage(raw,0,0,null);
  Bitmap b=new Bitmap(argb);
  // Same working-size rule as MainActivity.decode: longest side capped at 1600.
  int max=Math.max(b.getWidth(),b.getHeight());
  if(max>1600){float s=1600f/max;b=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);}
  System.out.println("input "+raw.getWidth()+"x"+raw.getHeight()+" -> working "+b.getWidth()+"x"+b.getHeight());
  String model="126710BLNR";
  WatchAlignCoreV13.AnalysisResult full=null;
  GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,model);
  Bitmap measured=MeasuredOverlayRenderer.render(b,h.drawing);
  GmtHumanSummary.Input sum=h!=null&&h.summary!=null?h.summary:new GmtHumanSummary.Input();
  sum.overlayDrawn=h.drawing!=null&&h.drawing.twelve!=null;
  System.out.println("=====\n"+GmtHumanSummary.build(sum)+"=====");
  System.out.println(h==null?"(no human QC)":h.report);
  if(measured!=null){ImageIO.write(measured.img,"png",new File(a[1]));
   java.awt.image.BufferedImage comp=new java.awt.image.BufferedImage(b.getWidth(),b.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
   java.awt.Graphics2D g2=comp.createGraphics();g2.drawImage(b.img,0,0,null);g2.drawImage(measured.img,0,0,null);g2.dispose();
   ImageIO.write(comp,"png",new File(a[1].replace(".png","_comp.png")));}
  Bitmap cu=MeasuredOverlayRenderer.closeUp(b,measured,h.drawing,540);
  if(cu!=null)ImageIO.write(cu.img,"png",new File(a[1].replace(".png","_12.png")));
 }}
