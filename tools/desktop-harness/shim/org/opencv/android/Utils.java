package org.opencv.android;
import android.graphics.Bitmap;import org.opencv.core.*;
public class Utils{
 public static void bitmapToMat(Bitmap b,Mat m){int w=b.getWidth(),h=b.getHeight();int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);byte[] d=new byte[w*h*4];
  for(int i=0;i<px.length;i++){int c=px[i];d[4*i]=(byte)((c>>16)&255);d[4*i+1]=(byte)((c>>8)&255);d[4*i+2]=(byte)(c&255);d[4*i+3]=(byte)((c>>>24)&255);}
  m.create(h,w,CvType.CV_8UC4);m.put(0,0,d);}
 public static void bitmapToMat(Bitmap b,Mat m,boolean u){bitmapToMat(b,m);}
 public static void matToBitmap(Mat m,Bitmap b){Mat r=new Mat();int ch=m.channels();
  if(ch==4)r=m;else if(ch==3)org.opencv.imgproc.Imgproc.cvtColor(m,r,org.opencv.imgproc.Imgproc.COLOR_RGB2RGBA);else org.opencv.imgproc.Imgproc.cvtColor(m,r,org.opencv.imgproc.Imgproc.COLOR_GRAY2RGBA);
  int w=r.cols(),h=r.rows();byte[] d=new byte[w*h*4];r.get(0,0,d);int[] px=new int[w*h];
  for(int i=0;i<px.length;i++)px[i]=((d[4*i+3]&255)<<24)|((d[4*i]&255)<<16)|((d[4*i+1]&255)<<8)|(d[4*i+2]&255);b.setPixels(px,0,w,0,0,w,h);}
 public static void matToBitmap(Mat m,Bitmap b,boolean u){matToBitmap(m,b);}
}
