package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha22 proof-of-concept: estimate the photographed GMT dial plane from a fitted dial ellipse,
 * derive a planar homography, then project a canonical 126710 layout into the native photo.
 * Hour markers are never used to fit the pose, so the overlay cannot align itself to a bad marker.
 */
final class PerspectiveGmtOverlay {
    static final class Result {
        final Bitmap nativeOverlay;
        final Bitmap rectified;
        final String report;
        final double confidence;
        Result(Bitmap n, Bitmap r, String s, double c){nativeOverlay=n;rectified=r;report=s;confidence=c;}
    }

    private static final class DialSeed {
        final double x,y,r,quality;
        DialSeed(double x,double y,double r,double q){this.x=x;this.y=y;this.r=r;this.quality=q;}
    }

    static boolean supports(String modelRef){return CanonicalGmtGeometryAnalyzer.supports(modelRef);}

    static Result build(Bitmap input,String modelRef){
        if(input==null||!supports(modelRef))return null;
        Mat src=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(input,src);
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);
            DialSeed seed=seed(src);
            if(seed==null||!(seed.r>40))return null;
            RotatedRect ellipse=findDialEllipse(edges,seed);
            if(ellipse==null)return null;

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));

            Point[] card=ellipseCardinalPoints(ellipse);
            Mat H=homographyFromUnitSquare(card);
            if(H==null||H.empty())return null;

            double reproj=reprojectionError(H,card);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double confidence=confidence(seed.quality,reproj,centerErr,axisRatio);

            Bitmap overlay=renderNative(input,H,confidence,tiltDeg,reproj);
            Bitmap rectified=rectify(src,H,input);
            String report=String.format(Locale.US,
                    "\n\nPERSPECTIVE GMT OVERLAY\n"+
                    "Pose source: fitted dial ellipse only; hour markers are inspection targets, not registration anchors.\n"+
                    "Ellipse axes: %.1f × %.1f px; apparent tilt %.1f°; ellipse angle %.1f°.\n"+
                    "Dial-centre agreement: %.2f%% of dial radius. Homography reprojection residual: %.2f px.\n"+
                    "Overlay confidence: %.0f%%.\n"+
                    "Use Native Template to inspect the photographed watch in-place, or Rectified to inspect a front-on normalized dial. This alpha22 solver is intentionally visual-first and does not issue a GL/RL score.\n",
                    major,minor,tiltDeg,ellipse.angle,centerErr*100.0,reproj,confidence*100.0);
            H.release();
            return new Result(overlay,rectified,report,confidence);
        }catch(Throwable ignored){return null;}
        finally{src.release();gray.release();blur.release();edges.release();}
    }

    private static DialSeed seed(Mat rgba)throws Exception{
        Mat bgr=new Mat();
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            Method detect=WatchAlignCoreV7.class.getDeclaredMethod("detectDial",Mat.class);detect.setAccessible(true);
            Object d=detect.invoke(null,bgr);if(d==null)return null;
            return new DialSeed(num(d,"x"),num(d,"y"),num(d,"r"),num(d,"quality"));
        }finally{bgr.release();}
    }

    private static RotatedRect findDialEllipse(Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();
        try{
            Imgproc.findContours(edges.clone(),contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;
            for(MatOfPoint c:contours){
                if(c.rows()<40)continue;
                MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);
                    double a=Math.max(e.size.width,e.size.height),b=Math.min(e.size.width,e.size.height);
                    if(a<1.55*s.r||a>2.35*s.r||b<1.35*s.r||b>2.30*s.r)continue;
                    double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/s.r;
                    if(dc>0.22)continue;
                    double sizeErr=Math.abs(a/(2.0*s.r)-1.0)+Math.abs(b/(2.0*s.r)-1.0);
                    double ratio=b/Math.max(1.0,a);
                    if(ratio<0.72)continue;
                    double score=2.4*dc+sizeErr;
                    if(score<bestScore){bestScore=score;best=e;}
                }catch(Throwable ignored){}finally{f.release();}
            }
            return best;
        }finally{for(MatOfPoint c:contours)c.release();hierarchy.release();}
    }

    // Canonical coordinates are a square touching the dial circle at top/right/bottom/left.
    private static Mat homographyFromUnitSquare(Point[] dst){
        MatOfPoint2f srcPts=new MatOfPoint2f(
                new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0));
        MatOfPoint2f dstPts=new MatOfPoint2f(dst);
        try{return Calib3d.findHomography(srcPts,dstPts,0);}
        finally{srcPts.release();dstPts.release();}
    }

    private static Point[] ellipseCardinalPoints(RotatedRect e){
        double rx=e.size.width/2.0,ry=e.size.height/2.0,t=Math.toRadians(e.angle);
        // Local ellipse points corresponding to canonical top/right/bottom/left.
        return new Point[]{rot(e.center,0,-ry,t),rot(e.center,rx,0,t),rot(e.center,0,ry,t),rot(e.center,-rx,0,t)};
    }
    private static Point rot(Point c,double x,double y,double t){return new Point(c.x+x*Math.cos(t)-y*Math.sin(t),c.y+x*Math.sin(t)+y*Math.cos(t));}

    private static double reprojectionError(Mat H,Point[] expected){
        Point[] canonical={new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0)};
        double sum=0;for(int i=0;i<4;i++){Point p=project(H,canonical[i].x,canonical[i].y);sum+=Math.hypot(p.x-expected[i].x,p.y-expected[i].y);}return sum/4.0;
    }

    private static double confidence(double q,double reproj,double centerErr,double axisRatio){
        double a=Math.max(0,Math.min(1,(q-0.45)/0.45));
        double b=Math.max(0,1-reproj/3.0);
        double c=Math.max(0,1-centerErr/0.16);
        double d=Math.max(0,Math.min(1,(axisRatio-0.72)/0.20));
        return Math.max(0,Math.min(1,0.35*a+0.20*b+0.30*c+0.15*d));
    }

    private static Bitmap renderNative(Bitmap source,Mat H,double confidence,double tilt,double reproj){
        Bitmap out=source.copy(Bitmap.Config.ARGB_8888,true);Canvas c=new Canvas(out);
        float scale=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        Paint cyan=paint(Color.rgb(38,220,235),1.6f*scale,150);Paint faint=paint(Color.rgb(38,220,235),1.0f*scale,82);Paint amber=paint(Color.rgb(255,185,35),2.0f*scale,205);Paint white=paint(Color.WHITE,1.2f*scale,145);
        // Dial and minute-track rings.
        drawProjectedCircle(c,H,1.00,cyan);drawProjectedCircle(c,H,0.90,faint);drawProjectedCircle(c,H,0.82,faint);
        // Exact 30-degree GMT axes. 3 o'clock remains an axis even though occupied by date.
        for(int h=1;h<=12;h++){double a=Math.toRadians(h*30.0-90.0);Point p0=project(H,0.18*Math.cos(a),0.18*Math.sin(a));Point p1=project(H,0.96*Math.cos(a),0.96*Math.sin(a));c.drawLine((float)p0.x,(float)p0.y,(float)p1.x,(float)p1.y,(h==12||h==6)?white:faint);}
        // Canonical target shapes, deliberately independent of observed marker centres.
        for(int h=1;h<=12;h++){if(h==3)continue;double a=Math.toRadians(h*30.0-90.0);if(h==12)drawTriangle(c,H,0.76,0.10,a,amber);else if(h==6||h==9)drawRectTarget(c,H,0.73,0.095,0.055,a,amber);else drawCircleTarget(c,H,0.73,0.055,a,cyan);}
        // Date target box in canonical 3 o'clock sector.
        drawQuad(c,H,new double[][]{{0.52,-0.10},{0.82,-0.10},{0.82,0.12},{0.52,0.12}},amber);
        // Centre pinion target.
        Point pc=project(H,0,0);c.drawCircle((float)pc.x,(float)pc.y,5f*scale,amber);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setTextSize(18f*scale);text.setAlpha(210);
        c.drawText(String.format(Locale.US,"GMT perspective template · confidence %.0f%% · tilt %.1f° · residual %.2f px",confidence*100,tilt,reproj),18f*scale,28f*scale,text);
        return out;
    }

    private static Bitmap rectify(Mat rgba,Mat H,Bitmap source){
        int side=Math.max(700,Math.min(1200,Math.max(source.getWidth(),source.getHeight())));
        // Canonical [-1,1] square to destination pixels.
        Mat S=Mat.eye(3,3,CvType.CV_64F);S.put(0,0,side/2.15);S.put(1,1,side/2.15);S.put(0,2,side/2.0);S.put(1,2,side/2.0);
        Mat Hinv=new Mat(),M=new Mat();Core.invert(H,Hinv);Core.gemm(S,Hinv,1,new Mat(),0,M);
        Mat dst=new Mat();Imgproc.warpPerspective(rgba,dst,M,new Size(side,side),Imgproc.INTER_CUBIC,Core.BORDER_CONSTANT,new Scalar(8,17,31,255));
        Bitmap b=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,b);S.release();Hinv.release();M.release();dst.release();return b;
    }

    private static void drawProjectedCircle(Canvas c,Mat H,double r,Paint p){Path path=new Path();for(int i=0;i<=180;i++){double a=2*Math.PI*i/180.0;Point q=project(H,r*Math.cos(a),r*Math.sin(a));if(i==0)path.moveTo((float)q.x,(float)q.y);else path.lineTo((float)q.x,(float)q.y);}c.drawPath(path,p);}
    private static void drawCircleTarget(Canvas c,Mat H,double rr,double size,double a,Paint p){double cx=rr*Math.cos(a),cy=rr*Math.sin(a);Path path=new Path();for(int i=0;i<=48;i++){double q=2*Math.PI*i/48.0;Point x=project(H,cx+size*Math.cos(q),cy+size*Math.sin(q));if(i==0)path.moveTo((float)x.x,(float)x.y);else path.lineTo((float)x.x,(float)x.y);}c.drawPath(path,p);}
    private static void drawRectTarget(Canvas c,Mat H,double rr,double w,double h,double a,Paint p){double cx=rr*Math.cos(a),cy=rr*Math.sin(a);double ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;double[][] pts={{cx-ux*h-vx*w,cy-uy*h-vy*w},{cx-ux*h+vx*w,cy-uy*h+vy*w},{cx+ux*h+vx*w,cy+uy*h+vy*w},{cx+ux*h-vx*w,cy+uy*h-vy*w}};drawQuad(c,H,pts,p);}
    private static void drawTriangle(Canvas c,Mat H,double rr,double size,double a,Paint p){double ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;double cx=rr*ux,cy=rr*uy;double[][] pts={{cx+ux*size,cy+uy*size},{cx-ux*size*0.75+vx*size*0.72,cy-uy*size*0.75+vy*size*0.72},{cx-ux*size*0.75-vx*size*0.72,cy-uy*size*0.75-vy*size*0.72}};drawQuad(c,H,pts,p);}
    private static void drawQuad(Canvas c,Mat H,double[][] pts,Paint p){Path path=new Path();for(int i=0;i<pts.length;i++){Point q=project(H,pts[i][0],pts[i][1]);if(i==0)path.moveTo((float)q.x,(float)q.y);else path.lineTo((float)q.x,(float)q.y);}path.close();c.drawPath(path,p);}
    private static Paint paint(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);p.setAlpha(alpha);return p;}
    private static Point project(Mat H,double x,double y){double[] h=new double[9];H.get(0,0,h);double w=h[6]*x+h[7]*y+h[8];if(Math.abs(w)<1e-9)w=1e-9;return new Point((h[0]*x+h[1]*y+h[2])/w,(h[3]*x+h[4]*y+h[5])/w);}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private PerspectiveGmtOverlay(){}
}
