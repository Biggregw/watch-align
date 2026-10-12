package com.watchalign.mobile;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;
/** Native checks with independently drawn inner polygons; no genuine reference affects detection. */
public class LuminousSyntheticChecks {
 public static void main(String[] args)throws Exception{
  nu.pattern.OpenCV.loadLocally();int checks=0;
  for(ModelSpec.Shape shape:ModelSpec.Shape.values())for(int R:new int[]{250,500}){
   ModelSpec.Marker m=new ModelSpec.Marker(12,shape,"test",.75,.09,.15,.06,.59,.90,.12,.7967,.5);
   int size=2*R+100;double[] H={R,0,size/2.0,0,R,size/2.0,0,0,1};double[] ring={0,0,0,0};
   Mat image=new Mat(size,size,CvType.CV_8UC1,new Scalar(25));double[][] outer=shape==ModelSpec.Shape.ROUND?MarkerPolygonMismatch.circle(0,-.75,.09,128):shape==ModelSpec.Shape.BATON?m.batonPolygon():m.trianglePolygon();
   double[][] inner=shape==ModelSpec.Shape.ROUND?MarkerPolygonMismatch.circle(0,-.75,.062,128):shape==ModelSpec.Shape.BATON?new double[][]{{-.038,-.86},{.038,-.86},{.038,-.64},{-.038,-.64}}:new double[][]{{0,-.64},{.088,-.872},{-.088,-.872}};
   fill(image,outer,H,240);fill(image,inner,H,185);
   // Dark isolation outside the independently specified inner face, with a bright surround farther away.
   MatOfPoint edge=points(inner,H);Imgproc.polylines(image,Collections.singletonList(edge),true,new Scalar(25),Math.max(4,R/75));edge.release();
   // Refill the real face so the isolation stroke does not manufacture an arbitrary inset.
   fill(image,inner,H,185);
   LuminousMarkerDetector.Detection d=LuminousMarkerDetector.detectOne(image,H,m,ring,R);
   if(!d.usable())throw new AssertionError(shape+" R="+R+" "+d.reason);
   double ratio=MarkerPolygonMismatch.area(d.polygon)/MarkerPolygonMismatch.area(inner);
   if(Math.abs(ratio-1)>.08)throw new AssertionError("Wrong luminous footprint "+shape+" ratio="+ratio);checks++;
   if(d.alignment==null||d.alignment.ideal==null)throw new AssertionError("Accepted physical contour lost independently measurable geometry "+shape+" R="+R);checks++;
   double[] master=m.masterPoint(),center={R*master[0]+size/2.0,R*master[1]+size/2.0};
   Imgproc.line(image,new Point(0,center[1]),new Point(size-1,center[1]),new Scalar(230),4);
   if(LuminousMarkerDetector.detectOne(image,H,m,ring,R).usable())throw new AssertionError("Hand-connected mask accepted "+shape);checks++;image.release();
  }
  System.out.println(checks+" native luminous shape/resolution/hand checks passed");
 }
 static MatOfPoint points(double[][] p,double[] H){double[][] projected=MarkerMismatchOverlay.project(p,H);Point[] q=new Point[p.length];for(int i=0;i<q.length;i++)q[i]=new Point(Math.round(projected[i][0]),Math.round(projected[i][1]));return new MatOfPoint(q);}
 static void fill(Mat image,double[][] p,double[] H,double color){MatOfPoint pts=points(p,H);Imgproc.fillConvexPoly(image,pts,new Scalar(color));pts.release();}
}
