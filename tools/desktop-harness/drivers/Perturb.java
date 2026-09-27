package com.watchalign.mobile;
import android.graphics.Bitmap;
import org.opencv.core.*;import org.opencv.imgcodecs.Imgcodecs;import org.opencv.imgproc.Imgproc;
import javax.imageio.ImageIO;import java.io.File;import java.awt.image.BufferedImage;
/** Same photo, several near-identical loads: prints how much the 12/6 readings move. */
public class Perturb{
 static int n=0;
 static Bitmap fromMat(Mat bgr){int w=bgr.cols(),h=bgr.rows();byte[] px=new byte[w*h*3];bgr.get(0,0,px);
  BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);int[] a=new int[w*h];
  for(int i=0;i<w*h;i++)a[i]=0xff000000|((px[3*i+2]&0xff)<<16)|((px[3*i+1]&0xff)<<8)|(px[3*i]&0xff);
  o.setRGB(0,0,w,h,a,0,w);return new Bitmap(o);}
 /** Android-like: libjpeg DCT downscale for inSampleSize, then bilinear to <=1600 (Skia filter). */
 static Mat androidLike(String f,int interp,int target){
  Mat full=Imgcodecs.imread(f);int max=Math.max(full.cols(),full.rows()),s=1;while(max/(s*2)>=1600)s*=2;
  Mat m=s==1?full:Imgcodecs.imread(f,s==2?Imgcodecs.IMREAD_REDUCED_COLOR_2:s==4?Imgcodecs.IMREAD_REDUCED_COLOR_4:Imgcodecs.IMREAD_REDUCED_COLOR_8);
  int cur=Math.max(m.cols(),m.rows());if(cur<=target)return m;
  double k=target/(double)cur;Mat o=new Mat();Imgproc.resize(m,o,new Size(Math.round(m.cols()*k),Math.round(m.rows()*k)),0,0,interp);return o;}
 static String row(String tag,Bitmap b){
  GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");var s=h.summary;
  String out=System.getProperty("wa.cu");
  if(out!=null)try{Bitmap m=MeasuredOverlayRenderer.render(b,h.drawing);Bitmap cu=MeasuredOverlayRenderer.closeUp(b,m,h.drawing,400);
   if(cu!=null)ImageIO.write(cu.img,"png",new File(out+"/"+(n++)+"_"+tag.replace(' ','_').replaceAll("[()]","")+".png"));}catch(Exception e){throw new RuntimeException(e);}
  return String.format(java.util.Locale.US,"  %-22s %4dx%-4d pose %-12s 12 %-5s st %-5s gap %.3f %-12s tri %.1fpx rot %+.2f %-12s band %-5s | 6 %-12s c %+.3f r %+.2f | rs gap %.3f-%.3f rot %+.2f..%+.2f same %s",
   tag,b.getWidth(),b.getHeight(),s.pose,s.twelveValid,s.stableFrame,s.observedGap,s.gap,s.trianglePx,s.rotationDeg,s.alignment,TriangleEdgeRefiner.lastUsedBand,s.sixAttention,s.sixCentring,s.sixRotationDeg,s.gapMin,s.gapMax,s.rotMin,s.rotMax,s.stabilitySameEdge);}
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String f:a){
   System.out.println(f.replaceAll(".*/(rep|gen)/[a-z]*/",""));
   if(f.endsWith(".png")){ // already-decoded working image (e.g. dumped from the emulator)
    BufferedImage raw=ImageIO.read(new File(f));BufferedImage argb=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_INT_ARGB);argb.getGraphics().drawImage(raw,0,0,null);
    System.out.println(row("png as-is",new Bitmap(argb)));continue;}
   BufferedImage raw=ImageIO.read(new File(f));BufferedImage argb=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_INT_ARGB);argb.getGraphics().drawImage(raw,0,0,null);
   Bitmap b=new Bitmap(argb);int max=Math.max(b.getWidth(),b.getHeight());
   if(max>1600){float k=1600f/max;b=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*k),Math.round(b.getHeight()*k),true);}
   System.out.println(row("imageio+java2d",b));
   System.out.println(row("cv linear (android)",fromMat(androidLike(f,Imgproc.INTER_LINEAR,1600))));
   System.out.println(row("cv area",fromMat(androidLike(f,Imgproc.INTER_AREA,1600))));
   System.out.println(row("cv cubic",fromMat(androidLike(f,Imgproc.INTER_CUBIC,1600))));
   System.out.println(row("cv linear 1500",fromMat(androidLike(f,Imgproc.INTER_LINEAR,1500))));
   System.out.println(row("cv linear 1400",fromMat(androidLike(f,Imgproc.INTER_LINEAR,1400))));
  }}}
