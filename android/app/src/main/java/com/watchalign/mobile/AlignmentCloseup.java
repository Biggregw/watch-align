package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Close-up of one marker for the alignment-only screens. DISPLAY ONLY: nothing drawn here feeds the decision.
 *   yellow  the existing genuine outer-metal reference, fixed (placed by the other markers, never by this one)
 *   silver  translucent estimated metal surround, drawn around the luminous shape FITTED TO THIS MARKER, with the genuine
 *           typical bevel width and rounded corners; it follows the marker, never the yellow
 *   cyan    the fitted luminous outline: solid where its edge was seen, dashed where inferred from the known shape
 *   red     for a defect, the direction of the dominant misalignment (or the rotated axis)
 * A marker that cannot be assessed shows only the yellow reference over the photo.
 */
final class AlignmentCloseup {
    static final int SIZE=480;
    static final Scalar YELLOW=new Scalar(255,214,10,255),CYAN=new Scalar(80,220,245,255),RED=new Scalar(255,59,48,255),
            SILVER=new Scalar(214,219,226,255);
    static final double SILVER_ALPHA=0.40;

    private AlignmentCloseup(){}

    static double half(ModelSpec.Marker mk){return mk.shape==ModelSpec.Shape.ROUND?0.14:mk.shape==ModelSpec.Shape.BATON?0.21:0.22;}

    static Bitmap render(Mat rgba,double[] H,ModelSpec.Marker mk,AlignmentDecision.Marker m){
        double[] c=mk.masterPoint();double half=half(mk);
        Mat img=Alpha98Closeups.render(rgba,H,c[0],c[1],half);
        draw(img,c[0],c[1],half,m);
        Bitmap b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(img,b);img.release();return b;
    }

    static void draw(Mat img,double cx,double cy,double half,AlignmentDecision.Marker m){
        double s=2*half/SIZE;int th=Math.max(2,SIZE/200);
        LuminousAlignment.Result f=m.fit;
        boolean shown=m.outcome!=AlignmentDecision.Outcome.CANNOT_ASSESS&&f!=null&&(f.fittedPolygon!=null||f.fittedCircle!=null);
        if(shown&&Double.isFinite(m.bevelR)&&m.bevelR>0)silver(img,cx,cy,half,f,m.bevelR);
        if(m.yellow!=null)poly(img,cx,cy,half,m.yellow,YELLOW,th,true);
        if(!shown)return;
        if(f.fittedCircle!=null){
            Imgproc.circle(img,px(cx,cy,half,f.fittedCircle[0],f.fittedCircle[1]),(int)Math.round(f.fittedCircle[2]/s),CYAN,Math.max(1,th-1),Imgproc.LINE_AA);
        }else{
            int n=f.fittedPolygon.length;
            for(int i=0;i<n;i++){
                boolean seen=i<f.sides.size()&&f.sides.get(i).observed;
                Point a=px(cx,cy,half,f.fittedPolygon[i][0],f.fittedPolygon[i][1]),b=px(cx,cy,half,f.fittedPolygon[(i+1)%n][0],f.fittedPolygon[(i+1)%n][1]);
                if(seen)Imgproc.line(img,a,b,CYAN,Math.max(1,th-1),Imgproc.LINE_AA);else dashed(img,a,b,CYAN,Math.max(1,th-1));
            }
        }
        if(m.outcome==AlignmentDecision.Outcome.DEFECT&&m.primary!=null)arrow(img,cx,cy,half,m);
    }

    /** Silver band: the fitted luminous shape grown by the bevel width (round joins), minus the shape itself. */
    private static void silver(Mat img,double cx,double cy,double half,LuminousAlignment.Result f,double bevelR){
        double s=2*half/SIZE;int w=Math.max(2,(int)Math.round(2*bevelR/s));
        Mat outer=Mat.zeros(img.rows(),img.cols(),CvType.CV_8UC1),inner=Mat.zeros(img.rows(),img.cols(),CvType.CV_8UC1);
        if(f.fittedCircle!=null){
            Point p=px(cx,cy,half,f.fittedCircle[0],f.fittedCircle[1]);
            Imgproc.circle(outer,p,(int)Math.round((f.fittedCircle[2]+bevelR)/s),new Scalar(255),-1,Imgproc.LINE_AA);
            Imgproc.circle(inner,p,(int)Math.round(f.fittedCircle[2]/s),new Scalar(255),-1,Imgproc.LINE_AA);
        }else{
            List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(pts(cx,cy,half,f.fittedPolygon)));
            Imgproc.fillPoly(outer,l,new Scalar(255),Imgproc.LINE_AA);
            Imgproc.polylines(outer,l,true,new Scalar(255),w,Imgproc.LINE_AA);   // thick lines end round: rounded corners
            Imgproc.fillPoly(inner,l,new Scalar(255),Imgproc.LINE_AA);
        }
        Mat band=new Mat();Core.subtract(outer,inner,band);
        Mat tint=new Mat(img.size(),img.type(),SILVER);Mat mixed=new Mat();
        Core.addWeighted(img,1-SILVER_ALPHA,tint,SILVER_ALPHA,0,mixed);
        mixed.copyTo(img,band);
        outer.release();inner.release();band.release();tint.release();mixed.release();
    }

    private static void arrow(Mat img,double cx,double cy,double half,AlignmentDecision.Marker m){
        LuminousAlignment.Result f=m.fit;AlignmentDecision.Issue i=m.primary;
        double[] c=f.fittedPolygon!=null?LuminousAlignment.centroid(f.fittedPolygon):new double[]{f.fittedCircle[0],f.fittedCircle[1]};
        int th=Math.max(3,SIZE/140);
        if(i.param==LuminousAlignment.Param.ROTATION&&f.fittedPolygon!=null){
            // the marker's long axis as fitted (red) against the reference axis (yellow, dashed)
            double[] far=axisEnd(f.fittedPolygon),rc=f.refCentre,rf=axisEnd(f.refPolygon);
            dashed(img,px(cx,cy,half,rc[0]-(rf[0]-rc[0])*0.25,rc[1]-(rf[1]-rc[1])*0.25),px(cx,cy,half,rf[0]+(rf[0]-rc[0])*0.15,rf[1]+(rf[1]-rc[1])*0.15),YELLOW,2);
            Imgproc.line(img,px(cx,cy,half,c[0]-(far[0]-c[0])*0.25,c[1]-(far[1]-c[1])*0.25),px(cx,cy,half,far[0]+(far[0]-c[0])*0.15,far[1]+(far[1]-c[1])*0.15),RED,th-1,Imgproc.LINE_AA);
            return;
        }
        if(i.screen==null)return;
        double len=0.35*half;double[] d=i.screen;double l=Math.hypot(d[0],d[1]);if(!(l>0))return;
        Point a=px(cx,cy,half,c[0],c[1]),b=px(cx,cy,half,c[0]+len*d[0]/l,c[1]+len*d[1]/l);
        Imgproc.arrowedLine(img,a,b,RED,th,Imgproc.LINE_AA,0,0.3);
    }

    /** End of the marker's long axis: a triangle's apex (vertex 0), a baton's outer-end midpoint (vertices 1, 2). */
    private static double[] axisEnd(double[][] v){return v.length==4?new double[]{0.5*(v[1][0]+v[2][0]),0.5*(v[1][1]+v[2][1])}:v[0];}
    static Point px(double cx,double cy,double half,double x,double y){double s=2*half/SIZE;return new Point((x-(cx-half))/s-0.5,(y-(cy-half))/s-0.5);}
    private static Point[] pts(double cx,double cy,double half,double[][] v){Point[] p=new Point[v.length];for(int i=0;i<v.length;i++)p[i]=px(cx,cy,half,v[i][0],v[i][1]);return p;}
    private static void poly(Mat m,double cx,double cy,double half,double[][] v,Scalar c,int th,boolean closed){
        List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(pts(cx,cy,half,v)));Imgproc.polylines(m,l,closed,c,th,Imgproc.LINE_AA);
    }
    private static void dashed(Mat m,Point a,Point b,Scalar c,int th){
        double L=Math.hypot(b.x-a.x,b.y-a.y);int n=Math.max(2,(int)(L/8));
        for(int k=0;k<n;k+=2){double t0=k/(double)n,t1=Math.min(1,(k+1)/(double)n);
            Imgproc.line(m,new Point(a.x+(b.x-a.x)*t0,a.y+(b.y-a.y)*t0),new Point(a.x+(b.x-a.x)*t1,a.y+(b.y-a.y)*t1),c,th,Imgproc.LINE_AA);}
    }
}
