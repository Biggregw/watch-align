package com.watchalign.mobile;
import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.nio.file.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public class LuminousBoundaryAudit {
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();Bitmap b=Alpha96Calib.loadAlpha96(a[0]);Alpha99Pipeline.Output o=Alpha99Pipeline.run(b,HarnessModel.spec(),HarnessModel.ref());ModelSpec.Marker m=o.model.atHour(a.length>2?Integer.parseInt(a[2]):12);
  double[] center=ExpectedMarkerOutline.moved(new double[][]{m.masterPoint()},o.measurement.ring.model)[0];double half=Math.max(m.halfWidth(),(m.outerEdgeR()-m.innerR())*.65)+.025;int n=Math.max(24,Math.min(240,(int)Math.ceil(2*half*o.measurement.dialRadiusPx)));double step=2*half/n;
  Mat rgba=new Mat(),gray=new Mat(),crop=new Mat(),T=new Mat(3,3,CvType.CV_64F),H=new Mat(3,3,CvType.CV_64F),M=new Mat(),empty=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
  T.put(0,0,step,0,center[0]-half+.5*step,0,step,center[1]-half+.5*step,0,0,1);H.put(0,0,o.pose.homography);Core.gemm(H,T,1,empty,0,M);Imgproc.warpPerspective(gray,crop,M,new Size(n,n),Imgproc.INTER_LINEAR|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0));byte[] p=new byte[n*n];crop.get(0,0,p);
  LuminousSeparator.Result trace=LuminousSeparator.trace(p,n,n/2);System.out.println("R="+o.measurement.dialRadiusPx+" n="+n+" "+trace.code+" "+trace.reason+" rays="+trace.supportedRays);
  if(trace.observed!=null)for(int level=0;level<3;level++){double[][] points=trace.observed[level],hull=LuminousGeometry.hull(points);double maximum=0;for(double[] point:points)maximum=Math.max(maximum,LuminousSeparator.distanceToBoundary(point,hull));System.out.println("Level "+level+" convex deviation "+maximum);}
  BufferedImage raw=new BufferedImage(n,n,BufferedImage.TYPE_INT_RGB);for(int y=0;y<n;y++)for(int x=0;x<n;x++){int v=p[y*n+x]&255;raw.setRGB(x,y,(v<<16)|(v<<8)|v);}
  BufferedImage out=new BufferedImage(600,600,BufferedImage.TYPE_INT_RGB);Graphics2D g=out.createGraphics();g.drawImage(raw,0,0,600,600,null);
  if(trace.observed!=null){g.setColor(Color.RED);double[][] contour=trace.observed[1];for(int i=0;i<contour.length;i++){double[] u=contour[i],v=contour[(i+1)%contour.length];g.drawLine((int)((u[0]+.5)*600/n),(int)((u[1]+.5)*600/n),(int)((v[0]+.5)*600/n),(int)((v[1]+.5)*600/n));}}
  g.dispose();ImageIO.write(out,"png",Path.of(a[1]).toFile());rgba.release();gray.release();crop.release();T.release();H.release();M.release();empty.release();
 }
}
