package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;
import com.watchalign.mobile.qc.RectificationConfidenceService;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import java.util.ArrayList;
import java.util.List;

/** One reusable canonical GMT dial. All planar fine-geometry modules consume this warp. */
final class CanonicalGmtDial implements AutoCloseable {
    static final int SIZE=1200;static final double CENTER=SIZE/2.0,RADIUS=500.0;
    final Mat bgr;final Mat gray;final PerspectiveMasterRenderer.Pose pose;final RectificationConfidenceService.Assessment rectification;

    private CanonicalGmtDial(Mat b,Mat g,PerspectiveMasterRenderer.Pose p,RectificationConfidenceService.Assessment r){bgr=b;gray=g;pose=p.copy();rectification=r;}

    static CanonicalGmtDial create(Bitmap source,PerspectiveMasterRenderer.Pose pose){
        if(source==null||pose==null||!pose.anchorMode||!pose.perspectiveMode)return null;
        Mat rgba=new Mat(),src=new Mat(),dst=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(source,rgba);Imgproc.cvtColor(rgba,src,Imgproc.COLOR_RGBA2BGR);
            MatOfPoint2f from=new MatOfPoint2f(new Point(pose.anchor12X,pose.anchor12Y),new Point(pose.anchor3X,pose.anchor3Y),new Point(pose.anchor6X,pose.anchor6Y),new Point(pose.anchor9X,pose.anchor9Y));
            MatOfPoint2f to=new MatOfPoint2f(new Point(CENTER,CENTER-RADIUS),new Point(CENTER+RADIUS,CENTER),new Point(CENTER,CENTER+RADIUS),new Point(CENTER-RADIUS,CENTER));
            Mat h=Imgproc.getPerspectiveTransform(from,to);Imgproc.warpPerspective(src,dst,h,new Size(SIZE,SIZE),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE);Imgproc.cvtColor(dst,gray,Imgproc.COLOR_BGR2GRAY);
            double sourceCircle=boundaryCircularity(src);double rectCircle=boundaryCircularity(gray);
            double axis=Math.min(Math.hypot(pose.anchor6X-pose.anchor12X,pose.anchor6Y-pose.anchor12Y),Math.hypot(pose.anchor9X-pose.anchor3X,pose.anchor9Y-pose.anchor3Y))/Math.max(1e-9,Math.max(Math.hypot(pose.anchor6X-pose.anchor12X,pose.anchor6Y-pose.anchor12Y),Math.hypot(pose.anchor9X-pose.anchor3X,pose.anchor9Y-pose.anchor3Y)));
            double repro=reprojectionRms(h,pose)/RADIUS;
            boolean observed=Double.isFinite(sourceCircle)&&Double.isFinite(rectCircle);
            RectificationConfidenceService.Validation validation=new RectificationConfidenceService.Validation(repro,axis,sourceCircle,rectCircle,observed);
            RectificationConfidenceService.Assessment confidence=RectificationConfidenceService.assess(pose.anchor12X,pose.anchor12Y,pose.anchor3X,pose.anchor3Y,pose.anchor6X,pose.anchor6Y,pose.anchor9X,pose.anchor9Y,validation);
            from.release();to.release();h.release();src.release();rgba.release();return new CanonicalGmtDial(dst,gray,pose,confidence);
        }catch(Throwable t){rgba.release();src.release();dst.release();gray.release();return null;}
    }

    Point sourcePoint(double canonicalX,double canonicalY){PointF p=PerspectiveMasterRenderer.projectPoint(pose,(canonicalX-CENTER)/RADIUS,(canonicalY-CENTER)/RADIUS);return new Point(p.x,p.y);}
    Bitmap bitmap(){Bitmap out=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Mat rgba=new Mat();Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA);Utils.matToBitmap(rgba,out);rgba.release();return out;}

    private static double reprojectionRms(Mat h,PerspectiveMasterRenderer.Pose p){
        Point[] in={new Point(p.anchor12X,p.anchor12Y),new Point(p.anchor3X,p.anchor3Y),new Point(p.anchor6X,p.anchor6Y),new Point(p.anchor9X,p.anchor9Y)};Point[] target={new Point(CENTER,CENTER-RADIUS),new Point(CENTER+RADIUS,CENTER),new Point(CENTER,CENTER+RADIUS),new Point(CENTER-RADIUS,CENTER)};double ss=0;
        for(int i=0;i<4;i++){double[] a=h.get(0,0),b=h.get(1,0),c=h.get(2,0);double den=c[0]*in[i].x+c[1]*in[i].y+c[2];double x=(a[0]*in[i].x+a[1]*in[i].y+a[2])/den,y=(b[0]*in[i].x+b[1]*in[i].y+b[2])/den;ss+=(x-target[i].x)*(x-target[i].x)+(y-target[i].y)*(y-target[i].y);}return Math.sqrt(ss/4.0);
    }

    private static double boundaryCircularity(Mat image){
        Mat gray=new Mat(),blur=new Mat(),edges=new Mat();List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();
        try{if(image.channels()==1)image.copyTo(gray);else Imgproc.cvtColor(image,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(gray,blur,new Size(7,7),1.5);Imgproc.Canny(blur,edges,45,130);Imgproc.findContours(edges,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);double best=Double.NaN,bestArea=0,total=image.cols()*(double)image.rows();for(MatOfPoint c:contours){double area=Math.abs(Imgproc.contourArea(c));if(area<total*.08||area>total*.90||c.rows()<20)continue;MatOfPoint2f f=new MatOfPoint2f(c.toArray());RotatedRect e=Imgproc.fitEllipse(f);f.release();double major=Math.max(e.size.width,e.size.height),minor=Math.min(e.size.width,e.size.height);if(area>bestArea&&major>0){bestArea=area;best=minor/major;}}return best;}finally{for(MatOfPoint c:contours)c.release();gray.release();blur.release();edges.release();hierarchy.release();}}
    @Override public void close(){bgr.release();gray.release();}
}
