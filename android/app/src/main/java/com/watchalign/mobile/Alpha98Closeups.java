package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Alpha98 close-ups: an upright, perspective-corrected view of a finding's region (frozen pose H: canonical dial
 * coordinates -> image), magnified, with the genuine reference outline drawn on top so the user can inspect it.
 * Display only.
 */
final class Alpha98Closeups {
    static final int SIZE=480;
    private static final Scalar YELLOW=new Scalar(255,214,10,255),CYAN=new Scalar(50,213,242,255);

    private Alpha98Closeups(){}

    /** RGBA close-up of a canonical square [cx +/- half] x [cy +/- half]. */
    static Mat render(Mat rgba,double[] H,double cx,double cy,double half){
        double s=2*half/SIZE;
        Mat T=new Mat(3,3,CvType.CV_64F);T.put(0,0,s,0,cx-half+0.5*s, 0,s,cy-half+0.5*s, 0,0,1);
        Mat Hm=new Mat(3,3,CvType.CV_64F);Hm.put(0,0,H);
        Mat M=new Mat(),empty=new Mat();Core.gemm(Hm,T,1,empty,0,M);
        Mat out=new Mat();
        Imgproc.warpPerspective(rgba,out,M,new Size(SIZE,SIZE),Imgproc.INTER_CUBIC|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0,0,0,255));
        T.release();Hm.release();M.release();empty.release();
        return out;
    }

    /**
     * @param ring the measured ring model (Alpha94 Report.ring.model: tx, ty, scale-1, rotation rad) or null. Marker
     *             findings are measured relative to the other markers, so their outline is drawn where the ring of
     *             markers predicts it; the ring finding itself is drawn on the unmoved master.
     */
    static Bitmap forFinding(Mat rgba,double[] H,Alpha98Findings.Finding f,Alpha98DateWindow.Result date,double[] ring,ModelSpec model,ModelReference ref){
        return forRegion(rgba,H,f,date,ring,model,ref);
    }

    /** Alpha99 findings: optionally show the precise four detector-fitted edges in cyan, separate from yellow. */
    static Bitmap forFinding(Mat rgba,double[] H,Alpha99Findings.Finding g,Alpha98DateWindow.Result date,double[] ring,ModelSpec model,ModelReference ref,
                             Alpha94MarkerMeasurement.Marker fitted){
        return forFinding(rgba,H,g,date,ring,model,ref,fitted,null);
    }

    static Bitmap forFinding(Mat rgba,double[] H,Alpha99Findings.Finding g,Alpha98DateWindow.Result date,double[] ring,ModelSpec model,ModelReference ref,
                             Alpha94MarkerMeasurement.Marker fitted,MarkerMismatchResearch.Result comparison){
        Alpha98Findings.Finding f=new Alpha98Findings.Finding(g.key,g.title);
        f.cx=g.cx;f.cy=g.cy;f.half=g.half;f.hour=g.hour;f.shape=Alpha98Findings.Shape.valueOf(g.shape.name());
        return forRegion(rgba,H,f,date,ring,model,ref,fitted,comparison);
    }

    private static Bitmap forRegion(Mat rgba,double[] H,Alpha98Findings.Finding f,Alpha98DateWindow.Result date,double[] ring,ModelSpec model,ModelReference ref){
        return forRegion(rgba,H,f,date,ring,model,ref,null);
    }

    private static Bitmap forRegion(Mat rgba,double[] H,Alpha98Findings.Finding f,Alpha98DateWindow.Result date,double[] ring,ModelSpec model,ModelReference ref,
                                    Alpha94MarkerMeasurement.Marker fitted){
        return forRegion(rgba,H,f,date,ring,model,ref,fitted,null);
    }

    private static Bitmap forRegion(Mat rgba,double[] H,Alpha98Findings.Finding f,Alpha98DateWindow.Result date,double[] ring,ModelSpec model,ModelReference ref,
                                    Alpha94MarkerMeasurement.Marker fitted,MarkerMismatchResearch.Result comparison){
        ModelSpec.Marker spec=model.atHour(f.hour);
        Mat m=render(rgba,H,f.cx,f.cy,f.half);
        if(comparison!=null&&comparison.expected!=null){
            Bitmap b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(m,b);m.release();
            return MarkerMismatchOverlay.closeup(b,f.cx,f.cy,f.half,comparison);
        }
        int th=Math.max(2,SIZE/160);
        switch(f.shape){
            case BATON:
                if(spec!=null){
                    poly(m,f,ExpectedMarkerOutline.polygon(spec,ref,ring),th);
                    // Actual detector polygon, measured independently after the frozen perspective fit.
                    // Draw cyan last so even a near-perfect fit remains visible inside the yellow line.
                    if(fitted!=null&&fitted.usable&&fitted.fitVertices!=null&&fitted.fitVertices.length==4)
                        poly(m,f,fitted.fitVertices,2,CYAN);
                }
                break;
            case TRIANGLE:if(spec!=null)poly(m,f,ExpectedMarkerOutline.polygon(spec,ref,ring),th);break;
            case ROUND:if(spec!=null)poly(m,f,ExpectedMarkerOutline.polygon(spec,ref,ring),th);break;
            case RING:
                for(ModelSpec.Marker mk:model.markers){
                    if(mk.shape==ModelSpec.Shape.BATON)poly(m,f,mk.batonPolygon(),th);
                    else if(mk.shape==ModelSpec.Shape.ROUND){double a=Math.toRadians(mk.hour*30.0);
                        circle(m,f,Math.sin(a)*mk.centreR,-Math.cos(a)*mk.centreR,mk.outerR,th);}
                }
                for(ModelSpec.Marker mk:model.markers)if(mk.shape==ModelSpec.Shape.TRIANGLE)poly(m,f,nominalTriangle(mk,ref),th);
                break;
            case DATE:
                if(date!=null&&Double.isFinite(date.wR)){
                    double a=Math.toRadians(date.windowTiltDeg),hw=date.wR/2,hh=date.hR/2;double[][] box=new double[4][];
                    double[][] c={{-hw,-hh},{hw,-hh},{hw,hh},{-hw,hh}};
                    for(int i=0;i<4;i++)box[i]=new double[]{date.cx+c[i][0]*Math.cos(a)-c[i][1]*Math.sin(a),date.cy+c[i][0]*Math.sin(a)+c[i][1]*Math.cos(a)};
                    poly(m,f,box,th);
                    // level reference line through the window centre (dial horizontal)
                    Point p0=px(f,date.cx-0.28,date.cy-hh-0.03),p1=px(f,date.cx+0.28,date.cy-hh-0.03);Imgproc.line(m,p0,p1,CYAN,th,Imgproc.LINE_AA);
                }
                break;
        }
        Bitmap b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(m,b);m.release();return b;
    }

    /** Apply the ring similarity model (as Alpha94 setLocal does) to canonical points; identity when ring is null. */
    static double[][] moved(double[][] v,double[] x){
        return ExpectedMarkerOutline.moved(v,x);
    }

    // ------------------------------------------------------------------ outlines (canonical dial units)
    /** Master triangle moved to the genuine-calibrated nominal position (the model's triangle reference); the master
     *  itself when the model has no triangle reference. Nominal offsets are radial (+ = outward) / tangential. */
    static double[][] nominalTriangle(ModelSpec.Marker spec,ModelReference ref){
        return ExpectedMarkerOutline.nominalTriangle(spec,ref);
    }

    private static Point px(Alpha98Findings.Finding f,double x,double y){
        double s=2*f.half/SIZE;return new Point((x-(f.cx-f.half))/s-0.5,(y-(f.cy-f.half))/s-0.5);
    }
    private static void poly(Mat m,Alpha98Findings.Finding f,double[][] v,int th){
        poly(m,f,v,th,YELLOW);
    }
    private static void poly(Mat m,Alpha98Findings.Finding f,double[][] v,int th,Scalar colour){
        Point[] p=new Point[v.length];for(int i=0;i<v.length;i++)p[i]=px(f,v[i][0],v[i][1]);
        List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(p));Imgproc.polylines(m,l,true,colour,th,Imgproc.LINE_AA);
    }
    private static void circle(Mat m,Alpha98Findings.Finding f,double x,double y,double r,int th){
        double s=2*f.half/SIZE;Imgproc.circle(m,px(f,x,y),(int)Math.round(r/s),YELLOW,th,Imgproc.LINE_AA);
    }
}
