package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;import org.opencv.core.*;import org.opencv.imgproc.Imgproc;import org.opencv.android.Utils;
/** Layout <image>...: dark-interior fraction at 3 and 9 (date-side probe, alpha61). */
public class Layout{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String p:a){
   try{
   Bitmap b=Load.photo(p);Load.Human hu=Load.human(b,p);var d=hu.h.drawing;
   if(d.twelve==null){System.out.println("NO12 "+p);continue;}
   Mat src=new Mat();Utils.bitmapToMat(b,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2GRAY);
   double cx=d.dialCx,cy=d.dialCy,r=Math.sqrt(d.dialA*d.dialB);
   double t12=Math.toDegrees(Math.atan2(d.twelve.tick60[0]-cx,cy-d.twelve.tick60[1]));
   System.out.printf(Locale.US,"%s 3: %s 9: %s%n",p.substring(p.lastIndexOf('/',p.lastIndexOf('/')-1)+1),probe(src,cx,cy,r,t12+90),probe(src,cx,cy,r,t12-90));
   }catch(Throwable t){System.out.println("ERR "+p+" "+t);}
  }
 }
 static String probe(Mat g,double cx,double cy,double r,double clk){
  double t=Math.toRadians(clk);double ux=Math.sin(t),uy=-Math.cos(t),vx=-uy,vy=ux;
  List<Double> v=new ArrayList<>();
  for(double rf=0.66;rf<=0.85;rf+=0.004)for(double s=-0.03;s<=0.03;s+=0.004){
   double x=cx+ux*rf*r+vx*s*r,y=cy+uy*rf*r+vy*s*r;int xi=(int)Math.round(x),yi=(int)Math.round(y);
   if(xi<0||yi<0||xi>=g.cols()||yi>=g.rows())continue;v.add(g.get(yi,xi)[0]);}
  Collections.sort(v);if(v.size()<20)return "n/a";
  double p90=v.get((int)(0.9*(v.size()-1)));int dark=0;for(double q:v)if(q<0.5*p90)dark++;
  return String.format(Locale.US,"p90 %3.0f dark %.2f",p90,dark/(double)v.size());
 }
}
