package com.watchalign.mobile;
import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.io.*;import java.util.*;

/**
 * DateWin <out.csv> <patchdir|-> <image>... : research only (2026-09-30). Measures the date window as
 * the app sees it (through the cyclops) against the minute ticks at the date side, at the analysis
 * scale and at 94% / 88% (resize check). Nothing here is used by the app.
 *
 * Local frame at the date side: u = tangential (clockwise), v = radial (outward), both in dial radii,
 * origin on the dial centre line through the date position. The window is the bright region between
 * 0.40 and 0.93 R; the numeral is the dark ink inside it.
 */
public class DateWin{
 static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}
 static final double PX_PER_R=220;               // patch resolution
 static final double V0=0.35,V1=0.98,U=0.36;      // patch extent (dial radii)

 static final class M{double tickU=Double.NaN,tickV=Double.NaN,tickAngErr=Double.NaN,chordDeg=Double.NaN;
  double wu0=Double.NaN,wu1,wv0,wv1,rect=Double.NaN,angle=Double.NaN,bright=Double.NaN;
  double au0=Double.NaN,au1,av0,av1;
  double nu0=Double.NaN,nu1,nv0,nv1;int numParts;String why="";}

 static M measure(Mat bgr,double cx,double cy,double r,double clockDeg,Mat patchOut){
  M m=new M();
  Mat gray=new Mat(),enh=new Mat();
  try{
   Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
   Imgproc.createCLAHE(2.0,new Size(8,8)).apply(gray,enh);
   double t=Math.toRadians(clockDeg),rx=Math.sin(t),ry=-Math.cos(t);   // radial unit (image)
   double tx=-ry,ty=rx;                                               // clockwise tangential (image)
   // Minute ticks at the date side (14/15/16 or 44/45/46).
   // The tick finder expects its marker at the top: turn the image a quarter so the date side is up
   // (as GmtSixLandmarkAnalyzer does for the 3 and 9) and map the ticks back.
   boolean at3=Math.sin(t)*Math.cos(Math.toRadians(0))>0;   // date towards image right
   double[][] fr=null;
   {
    Mat turned=new Mat();int Wo=enh.cols(),Ho=enh.rows();
    Core.rotate(enh,turned,at3?Core.ROTATE_90_COUNTERCLOCKWISE:Core.ROTATE_90_CLOCKWISE);
    double fx=at3?cy:Ho-1-cy,fy=at3?Wo-1-cx:cx;
    double[] o={cx+rx*0.925*r,cy+ry*0.925*r};
    double ox=at3?o[1]:Ho-1-o[1],oy=at3?Wo-1-o[0]:o[0];
    double[][] q=GmtTwelveLandmarkAnalyzer.tickFrameNear(turned,fx,fy,r,ox,oy);
    turned.release();
    if(q!=null){
     fr=new double[3][];
     for(int k=0;k<3;k++)fr[k]=at3?new double[]{Wo-1-q[k][1],q[k][0]}:new double[]{q[k][1],Ho-1-q[k][0]};
    }
   }
   if(fr!=null){
    double[] c=fr[1];double dx=c[0]-cx,dy=c[1]-cy;
    m.tickU=(dx*tx+dy*ty)/r;m.tickV=(dx*rx+dy*ry)/r;
    m.tickAngErr=Math.toDegrees(Math.atan2(m.tickU,m.tickV));
    double kx=fr[2][0]-fr[0][0],ky=fr[2][1]-fr[0][1];
    m.chordDeg=Math.toDegrees(Math.atan2(kx*rx+ky*ry,kx*tx+ky*ty));   // chord vs tangential
    if(Math.abs(m.tickAngErr)>4){m.why="ticks found away from the date position";m.tickU=m.tickV=Double.NaN;}
   }else m.why="date-side ticks not found";
   // Patch: rows = v from V1 (top) to V0, cols = u from -U to U.
   int W=(int)Math.round(2*U*PX_PER_R),H=(int)Math.round((V1-V0)*PX_PER_R);
   // patch (col,row) -> image: p = c + r*(u*t + v*rad), u = -U + col/PX, v = V1 - row/PX
   double s=r/PX_PER_R;
   Mat A=new Mat(2,3,CvType.CV_64F);
   A.put(0,0,tx*s,-rx*s,cx+r*(-U*tx+V1*rx));
   A.put(1,0,ty*s,-ry*s,cy+r*(-U*ty+V1*ry));
   Mat patch=new Mat();
   Imgproc.warpAffine(gray,patch,A,new Size(W,H),Imgproc.INTER_LINEAR+Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0));
   if(patchOut!=null)patch.copyTo(patchOut);
   Mat bin=new Mat();
   Imgproc.GaussianBlur(patch,bin,new Size(3,3),0);
   Imgproc.threshold(bin,bin,0,255,Imgproc.THRESH_BINARY+Imgproc.THRESH_OTSU);
   Imgproc.morphologyEx(bin,bin,Imgproc.MORPH_CLOSE,Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(5,5)));
   List<MatOfPoint> cs=new ArrayList<>();
   Imgproc.findContours(bin.clone(),cs,new Mat(),Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
   // Candidate: largest bright region whose box centre is near the date direction (|u| < 0.12)
   // and inside 0.5-0.9 R, at least 0.08 R on a side.
   Rect best=null;double bestA=0;MatOfPoint bestC=null;
   for(MatOfPoint c:cs){
    Rect b=Imgproc.boundingRect(c);
    double uc=-U+(b.x+b.width/2.0)/PX_PER_R,vc=V1-(b.y+b.height/2.0)/PX_PER_R;
    if(Math.abs(uc)>0.12||vc<0.5||vc>0.9)continue;
    if(b.width<0.08*PX_PER_R||b.height<0.08*PX_PER_R)continue;
    double a=Imgproc.contourArea(c);
    if(a>bestA){bestA=a;best=b;bestC=c;}
   }
   if(best==null){m.why+=(m.why.isEmpty()?"":"; ")+"no window-sized bright region at the date position";return m;}
   m.wu0=-U+best.x/PX_PER_R;m.wu1=-U+(best.x+best.width)/PX_PER_R;
   m.wv1=V1-best.y/PX_PER_R;m.wv0=V1-(best.y+best.height)/PX_PER_R;
   m.rect=bestA/(best.width*(double)best.height);
   RotatedRect rr=Imgproc.minAreaRect(new MatOfPoint2f(bestC.toArray()));
   double ang=rr.angle;if(rr.size.width<rr.size.height)ang+=90;while(ang>45)ang-=90;while(ang<=-45)ang+=90;m.angle=ang;
   // Aperture: the white date disc seen through the lens, the brightest region inside the lens box.
   Mat lens=patch.submat(best).clone();
   MatOfDouble mu=new MatOfDouble(),sd=new MatOfDouble();
   Mat sorted=new Mat();lens.reshape(1,1).copyTo(sorted);Core.sort(sorted,sorted,Core.SORT_ASCENDING);
   double p95=sorted.get(0,(int)(0.95*(sorted.cols()-1)))[0];
   Mat ap=new Mat();Imgproc.threshold(lens,ap,0.86*p95,255,Imgproc.THRESH_BINARY);
   // Aperture: the largest bright region (closed, so the numeral does not split it).
   Mat apc=new Mat();
   Imgproc.morphologyEx(ap,apc,Imgproc.MORPH_CLOSE,Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(9,9)));
   List<MatOfPoint> as=new ArrayList<>();
   Imgproc.findContours(apc.clone(),as,new Mat(),Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
   int ai=-1;double aA=0;
   for(int k=0;k<as.size();k++){double a=Imgproc.contourArea(as.get(k));if(a>aA){aA=a;ai=k;}}
   if(ai<0||aA<0.15*best.area()){m.why+=(m.why.isEmpty()?"":"; ")+"no bright date aperture inside the lens";return m;}
   Rect abox=Imgproc.boundingRect(as.get(ai));
   int ax=best.x+abox.x,ay=best.y+abox.y;
   m.au0=-U+ax/PX_PER_R;m.au1=-U+(ax+abox.width)/PX_PER_R;m.av1=V1-ay/PX_PER_R;m.av0=V1-(ay+abox.height)/PX_PER_R;
   Mat filled=Mat.zeros(lens.size(),CvType.CV_8U);
   Imgproc.drawContours(filled,as,ai,new Scalar(255),-1);
   m.bright=Core.mean(lens,filled).val[0];
   // Numeral: dark pieces inside the aperture outline whose surroundings are mostly the white disc
   // (ink); shadow along the edge borders the dark frame or dial instead.
   Mat ink=new Mat();Core.bitwise_not(ap,ink);Core.bitwise_and(ink,filled,ink);
   List<MatOfPoint> ds=new ArrayList<>();
   Imgproc.findContours(ink.clone(),ds,new Mat(),Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
   int x0=Integer.MAX_VALUE,y0=Integer.MAX_VALUE,x1=-1,y1=-1;
   double minA=0.004*abox.area();
   int ringR=Math.max(2,(int)(0.03*Math.min(abox.width,abox.height)));
   Mat k2=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(2*ringR+1,2*ringR+1));
   for(MatOfPoint c:ds){
    if(Imgproc.contourArea(c)<minA)continue;
    Mat one=Mat.zeros(lens.size(),CvType.CV_8U);
    Imgproc.drawContours(one,java.util.Collections.singletonList(c),0,new Scalar(255),-1);
    Mat ring=new Mat();Imgproc.dilate(one,ring,k2);Core.subtract(ring,one,ring);
    double ringN=Core.countNonZero(ring);
    Mat rb=new Mat();Core.bitwise_and(ring,ap,rb);
    double white=ringN>0?Core.countNonZero(rb)/ringN:0;
    one.release();ring.release();rb.release();
    if(white<0.75)continue;
    Rect b=Imgproc.boundingRect(c);
    m.numParts++;x0=Math.min(x0,b.x);y0=Math.min(y0,b.y);x1=Math.max(x1,b.x+b.width);y1=Math.max(y1,b.y+b.height);
   }
   if(m.numParts>0){
    m.nu0=-U+(best.x+x0)/PX_PER_R;m.nu1=-U+(best.x+x1)/PX_PER_R;m.nv1=V1-(best.y+y0)/PX_PER_R;m.nv0=V1-(best.y+y1)/PX_PER_R;
   }else m.why+=(m.why.isEmpty()?"":"; ")+"no numeral found in the aperture";
   return m;
  }finally{gray.release();enh.release();}
 }

 static String row(M m){
  double lc=(m.wu0+m.wu1)/2,lw=m.wu1-m.wu0,lv=(m.wv0+m.wv1)/2,lh=m.wv1-m.wv0;
  double ac=(m.au0+m.au1)/2,aw=m.au1-m.au0,av=(m.av0+m.av1)/2,ah=m.av1-m.av0;
  double nc=(m.nu0+m.nu1)/2,nv=(m.nv0+m.nv1)/2,nw=m.nu1-m.nu0,nh=m.nv1-m.nv0;
  double ref=Double.isFinite(m.tickU)?m.tickU:Double.NaN;
  return String.join(",",f(m.tickU),f(m.tickV),f(m.chordDeg),
   f(lc),f(lv),f(lw),f(lh),f((lc-ref)/lw),f(m.wv1),f(m.rect),f(m.angle),
   f(ac),f(av),f(aw),f(ah),f((ac-ref)/aw),f((ac-lc)/lw),f((av-lv)/lh),
   f((nc-ac)/aw),f((nv-av)/ah),f(nw/aw),f(nh/ah),f(nw/lw),f(nh/lh),f(nw),f(nh),f(m.bright),String.valueOf(m.numParts),'"'+m.why+'"');
 }
 static final String COLS="tick_u,tick_v,chord_deg,lens_u,lens_v,lens_w,lens_h,lens_u_vs_tick,lens_outer_v,lens_rect,lens_angle,ap_u,ap_v,ap_w,ap_h,ap_u_vs_tick,ap_u_in_lens,ap_v_in_lens,num_u_in_ap,num_v_in_ap,num_w_over_ap,num_h_over_ap,num_w_over_lens,num_h_over_lens,num_w,num_h,ap_bright,num_parts,why";

 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  String pdir=a[1].equals("-")?null:a[1];
  try(PrintWriter out=new PrintWriter(new FileWriter(a[0]))){
   out.println("file,pose,layout,stable,r_px,marker_tilt,marker_tilt_hi,"+COLS.replace(",",",")+","+pref("s94_")+","+pref("s88_"));
   for(int i=2;i<a.length;i++){
    String p=a[i];
    try{
     Bitmap prev=Load.photo(p);
     GmtDialCrop.Crop crop=GmtDialCrop.make(prev,Load.fullSource(p));
     Bitmap b=crop!=null?crop.bitmap:prev;
     GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");
     var d=h.drawing;GmtHumanSummary.Input s=h.summary;
     if(d==null||d.twelve==null||!Double.isFinite(d.dialCx)||s==null){out.println(p+","+h.poseLabel+",,,,,,"+"\"no 12 or no dial\"");continue;}
     double cx=d.dialCx,cy=d.dialCy,r=Math.sqrt(d.dialA*d.dialB);
     double twelve=Math.toDegrees(Math.atan2(d.twelve.tick60[0]-cx,cy-d.twelve.tick60[1]));
     String lay=String.valueOf(s.layout);
     GmtMarkerPose.Result mp=GmtMarkerPose.estimate(h.round,r);
     String head=String.join(",",p,String.valueOf(h.poseLabel),lay,String.valueOf(s.stableFrame),f(r),f(mp.valid?mp.tiltDeg:Double.NaN),f(mp.valid?mp.tiltHighDeg:Double.NaN));
     if(!"DATE_AT_3".equals(lay)&&!"DATE_AT_9".equals(lay)){out.println(head+",\"date side not determined\"");continue;}
     double clock=twelve+("DATE_AT_3".equals(lay)?90:-90);
     Mat src=new Mat();Utils.bitmapToMat(b,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
     Mat patch=new Mat();
     M m0=measure(src,cx,cy,r,clock,patch);
     if(pdir!=null&&!patch.empty())org.opencv.imgcodecs.Imgcodecs.imwrite(pdir+"/"+i+"_"+new File(p).getName().replaceAll("\\.[a-zA-Z]+$","")+".png",patch);
     StringBuilder sb=new StringBuilder(head+","+row(m0));
     for(double sc:new double[]{0.94,0.88}){
      Mat rs=new Mat();Imgproc.resize(src,rs,new Size(Math.round(src.cols()*sc),Math.round(src.rows()*sc)),0,0,Imgproc.INTER_AREA);
      sb.append(",").append(row(measure(rs,cx*sc,cy*sc,r*sc,clock,null)));rs.release();
     }
     out.println(sb);out.flush();src.release();
    }catch(Throwable t){out.println(p+",ERROR "+t.getClass().getSimpleName()+" "+String.valueOf(t.getMessage()).replace(',',';'));out.flush();}
   }
  }
 }
 static String pref(String p){StringBuilder b=new StringBuilder();for(String c:COLS.split(",")){if(b.length()>0)b.append(',');b.append(p).append(c);}return b.toString();}
}
