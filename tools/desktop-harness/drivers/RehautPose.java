package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;import java.io.*;
import org.opencv.core.Mat;import org.opencv.imgproc.Imgproc;import org.opencv.android.Utils;
/**
 * RehautPose <out.csv> <image>... : per photo, the app's rehaut pose inputs and an independent
 * dial-plane pose from the round-marker layout (research, 2026-09-29). Runs the same components
 * as GmtHumanQcAnalyzerV2 on the same (preview) image, plus the app's own photo-angle label.
 */
public class RehautPose{
 static String mk(List<GmtRoundMarkerAnalyzer.Marker> ms){StringBuilder b=new StringBuilder();for(var m:ms)if(m.found&&m.stable)b.append(m.hour).append(':').append(f(m.x)).append(':').append(f(m.y)).append(':').append(f(m.radiusPx)).append(';');return b.toString();}
 static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  try(PrintWriter out=new PrintWriter(new FileWriter(a[0]))){
   out.println("file,app_pose,crop,r_px,roll,stable,g_valid,g_inner,g_outer,g_mean,g_watchV,g_watchH,g_harm,g_widest,g_minmean,g_cov,g_resid,"
    +"s_valid,s_self,w12,w3,w6,w9,c12,c3,c6,c9,s_V,s_H,s_minmean,ell_valid,ell_ratio,ell_tilt,ell_minor,edge_ratio,edge_angle,"
    +"aff_n,aff_ratio,aff_tilt,aff_minor_watch,aff_scale_over_r,tri_w,gap,rot_axis,rot_top,tick_ok,cx,cy,edge_a,edge_b,t60x,t60y,t59x,t59y,t01x,t01y,markers");
   for(int i=1;i<a.length;i++){
    String p=a[i];
    try{
     Bitmap b=Load.photo(p);Load.Human hu=Load.human(b,p);
     Mat src=new Mat();Utils.bitmapToMat(b,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
     GmtDialSeedAnalyzer.Result dial=GmtDialSeedAnalyzer.analyse(src);
     if(!dial.valid){out.println(p+","+hu.h.poseLabel+",,,,,no_dial");continue;}
     double cx=dial.x,cy=dial.y,r=dial.r;
     DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(src,cx,cy,r);
     double edgeRatio=Double.NaN,edgeAng=Double.NaN;
     if(edge!=null){cx=edge.cx;cy=edge.cy;r=edge.meanRadius();edgeRatio=Math.min(edge.axisA,edge.axisB)/Math.max(edge.axisA,edge.axisB);edgeAng=edge.angleDeg;}
     GmtTwelveLandmarkAnalyzer.Result tw=GmtTwelveLandmarkAnalyzer.analyse(src,cx,cy,r);
     boolean stable=tw.valid&&tw.detectorStable&&Double.isFinite(tw.minuteFrameScore)&&tw.minuteFrameScore>=10.0;
     double roll=stable?tw.trackRollClockDeg:0.0;
     GmtRehautPoseAnalyzer.Result g=GmtRehautPoseAnalyzer.analyse(src,cx,cy,r,roll);
     GmtRehautSectorAnalyzer.Result s;boolean self=false;
     if(g.valid){s=GmtRehautSectorAnalyzer.analyse(src,cx,cy,g.innerSeedPx,g.outerSeedPx,roll);if(!s.valid){s=GmtRehautSectorAutoAnalyzer.analyse(src,cx,cy,r,roll);self=s.valid;}}
     else{s=GmtRehautSectorAutoAnalyzer.analyse(src,cx,cy,r,roll);self=s.valid;}
     GmtEllipsePoseAnalyzer.Result el=GmtEllipsePoseAnalyzer.analyse(src,cx,cy,r);
     // Independent dial-plane pose: affine from the master layout to the round markers found.
     double[] tick60=tw.valid&&tw.geometry!=null?tw.geometry.tick60:null;
     GmtRoundMarkerAnalyzer.DialFrame fr=edge!=null?new GmtRoundMarkerAnalyzer.DialFrame(edge.cx,edge.cy,edge.axisA,edge.axisB,edge.angleDeg):GmtRoundMarkerAnalyzer.DialFrame.circle(cx,cy,r);
     List<GmtRoundMarkerAnalyzer.Marker> ms=GmtRoundMarkerAnalyzer.analyse(src,fr,tick60);
     List<double[]> sP=new ArrayList<>(),dP=new ArrayList<>();
     for(var m:ms)if(m.found&&m.stable){sP.add(GmtRoundMarkerAnalyzer.master(m.hour,Gmt126710BlnrMaster.ROUND_CENTER_R));dP.add(new double[]{m.x,m.y});}
     int affN=sP.size();
     double affRatio=Double.NaN,affTilt=Double.NaN,affMinor=Double.NaN,affScale=Double.NaN;
     if(tick60!=null&&affN>=4){sP.add(GmtRoundMarkerAnalyzer.master(0,Gmt126710BlnrMaster.MINUTE_TRACK_R-0.010));dP.add(tick60);}
     if(affN>=4){
      double[][] A=GmtRoundMarkerAnalyzer.affine(sP,dP);
      if(A!=null){
       double m00=A[0][0],m01=A[0][1],m10=A[1][0],m11=A[1][1];
       // SVD of the 2x2 linear part: eigen of M^T M gives the right singular vectors (master/watch frame).
       double pp=m00*m00+m10*m10,q=m00*m01+m10*m11,t=m01*m01+m11*m11;
       double tr=pp+t,det=pp*t-q*q,disc=Math.sqrt(Math.max(0,tr*tr/4-det));
       double l1=tr/2+disc,l2=tr/2-disc;
       double s1=Math.sqrt(Math.max(0,l1)),s2=Math.sqrt(Math.max(0,l2));
       affRatio=s2/s1;affTilt=Math.toDegrees(Math.acos(Math.min(1,affRatio)));affScale=Math.sqrt(s1*s2)/r;
       // eigenvector for l2 (the compressed master direction): (q, l2-p) or (l2-t, q)
       double vx=q,vy=l2-pp;if(Math.hypot(vx,vy)<1e-9){vx=l2-t;vy=q;}
       // master frame: +x towards 3, +y towards 6. Clock angle of that direction, modulo 180.
       double clk=Math.toDegrees(Math.atan2(vx,-vy));clk=((clk%180)+180)%180;affMinor=clk;
      }
     }
     double triW=Double.NaN;
     if(tw.valid&&tw.geometry!=null)triW=Math.hypot(tw.geometry.triRight[0]-tw.geometry.triLeft[0],tw.geometry.triRight[1]-tw.geometry.triLeft[1]);
     out.println(String.join(",",p,String.valueOf(hu.h.poseLabel),String.valueOf(hu.crop!=null),f(r),f(roll),String.valueOf(stable),
      String.valueOf(g.valid),f(g.innerSeedPx),f(g.outerSeedPx),f(g.meanWidthPx),f(g.watchVerticalAsymmetry),f(g.watchHorizontalAsymmetry),f(g.firstHarmonicStrength),f(g.widestClockDeg),f(g.minWidthOverMean),f(g.edgeCoverage),f(g.fitResidual),
      String.valueOf(s.valid),String.valueOf(self),f(s.width12),f(s.width3),f(s.width6),f(s.width9),f(s.coverage12),f(s.coverage3),f(s.coverage6),f(s.coverage9),f(s.verticalAsymmetry),f(s.horizontalAsymmetry),f(s.minOverMean),
      String.valueOf(el.valid),f(el.axisRatio),f(el.tiltDeg),f(el.minorAxisClockDeg),f(edgeRatio),f(edgeAng),
      String.valueOf(affN),f(affRatio),f(affTilt),f(affMinor),f(affScale),f(triW),f(tw.valid?tw.topClearance:Double.NaN),f(tw.valid?tw.wholeAxisErrorDeg:Double.NaN),f(tw.valid?tw.topEdgeErrorDeg:Double.NaN),String.valueOf(tw.valid),f(cx),f(cy),f(edge!=null?edge.axisA:Double.NaN),f(edge!=null?edge.axisB:Double.NaN),
      f(tick60!=null?tick60[0]:Double.NaN),f(tick60!=null?tick60[1]:Double.NaN),
      f(tw.valid&&tw.geometry!=null?tw.geometry.tick59[0]:Double.NaN),f(tw.valid&&tw.geometry!=null?tw.geometry.tick59[1]:Double.NaN),
      f(tw.valid&&tw.geometry!=null?tw.geometry.tick01[0]:Double.NaN),f(tw.valid&&tw.geometry!=null?tw.geometry.tick01[1]:Double.NaN),
      mk(ms)));
     out.flush();src.release();
    }catch(Throwable t){out.println(p+",ERROR "+t.getClass().getSimpleName());out.flush();}
   }
  }
 }}
