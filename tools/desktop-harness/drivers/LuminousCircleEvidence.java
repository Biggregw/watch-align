package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgcodecs.Imgcodecs;
import java.nio.file.*;
import java.util.*;

/** Offline photographic audit using the archived, unchanged pose, ring and source bytes. */
public class LuminousCircleEvidence {
 public static void main(String[] args)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  Map<String,Object> p=null;
  for(String line:Files.readAllLines(Path.of(args[1]))){Map<String,Object> item=LuminousCalibrationStudy.obj(MiniJson.parse(line));if(args[2].equals(item.get("photo_id")))p=item;}
  if(p==null)throw new IllegalArgumentException("photo absent");
  Bitmap b=Alpha96Calib.loadAlpha96(args[0]);int hour=Integer.parseInt(args[3]);ModelSpec.Marker m=HarnessModel.spec().atHour(hour);
  double[] ring=LuminousCalibrationStudy.vec(p.get("ring")),H=LuminousCalibrationStudy.vec(p.get("H"));double R=LuminousCalibrationStudy.num(p.get("dial_radius_px"));
  double[] center=ExpectedMarkerOutline.moved(new double[][]{m.masterPoint()},ring)[0];double half=Math.max(m.halfWidth(),(m.outerEdgeR()-m.innerR())*.65)+.025;
  int n=Math.max(24,Math.min(240,(int)Math.ceil(2*half*R)));double step=2*half/n;
  Mat rgba=new Mat(),gray=new Mat(),crop=new Mat(),T=new Mat(3,3,CvType.CV_64F),hm=new Mat(3,3,CvType.CV_64F),M=new Mat(),empty=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
  T.put(0,0,step,0,center[0]-half+.5*step,0,step,center[1]-half+.5*step,0,0,1);hm.put(0,0,H);Core.gemm(hm,T,1,empty,0,M);Imgproc.warpPerspective(gray,crop,M,new Size(n,n),Imgproc.INTER_LINEAR|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0));
  Path out=Path.of(args[4]);Files.createDirectories(out);Imgcodecs.imwrite(out.resolve(args[2]+"_"+hour+"_gray.png").toString(),crop);byte[] pixels=new byte[n*n];crop.get(0,0,pixels);
  Map<String,Object> data=LuminousCalibrationStudy.map("n",n,"step",step,"R",R,"center",center,"half",half,"seed",n/2);
  for(Object item:(List<?>)p.get("markers")){Map<String,Object> marker=LuminousCalibrationStudy.obj(item);if(((Number)marker.get("hour")).intValue()==hour)data.put("marker",marker);}
  List<Object> profiles=new ArrayList<>();for(int k=0;k<96;k++){
   double a=k*2*Math.PI/96;List<Double> v=new ArrayList<>();for(double r=0;r<n/2.-2;r+=.25)v.add(LuminousSeparator.smooth(pixels,n,n/2+r*Math.cos(a),n/2+r*Math.sin(a)));profiles.add(v);
  }data.put("profiles",profiles);Files.writeString(out.resolve(args[2]+"_"+hour+".json"),LuminousCalibrationStudy.json(data));
  rgba.release();gray.release();crop.release();T.release();hm.release();M.release();empty.release();
 }
}
