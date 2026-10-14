package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * The whole dial, upright, with one badge per marker: green tick = No defect, red ! = Defect detected, grey dash =
 * Cannot assess. Every marker gets a badge. No numbers on the picture. Display only.
 */
final class AlignmentOverview {
    static final int SIZE=Alpha99Overview.SIZE;
    static final double HALF=Alpha99Overview.HALF;
    static final Scalar AMBER=new Scalar(255,190,60,255),GREEN=new Scalar(52,199,89,255),RED=new Scalar(255,59,48,255),GREY=new Scalar(142,142,147,255),
            WHITE=new Scalar(255,255,255,255),BLACK=new Scalar(0,0,0,255);

    private AlignmentOverview(){}

    static Bitmap render(Mat rgba,double[] H,AlignmentAnalysis.Output o,ModelSpec model){
        Mat m=Alpha98Closeups.render(rgba,H,0,0,HALF);
        Mat big=new Mat();Imgproc.resize(m,big,new org.opencv.core.Size(SIZE,SIZE),0,0,Imgproc.INTER_CUBIC);m.release();
        double scale=SIZE/(2*HALF);
        for(AlignmentDecision.Marker a:o.markers){
            double[] p=badgeAt(model.atHour(a.hour));if(p==null)continue;
            badge(big,new Point((p[0]+HALF)*scale,(p[1]+HALF)*scale),a.outcome,a.worthALook());
        }
        Bitmap b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(big,b);big.release();return b;
    }

    /** just inside the marker, towards the centre */
    static double[] badgeAt(ModelSpec.Marker mk){
        if(mk==null)return null;double r=mk.innerR()-0.09,a=Math.toRadians(mk.hour*30.0);
        return new double[]{Math.sin(a)*r,-Math.cos(a)*r};
    }

    static void badge(Mat m,Point c,AlignmentDecision.Outcome o,boolean worth){
        int rad=SIZE/34,th=Math.max(2,SIZE/240);double x=c.x,y=c.y,u=rad*0.5;
        Imgproc.circle(m,c,rad+th,BLACK,-1,Imgproc.LINE_AA);
        Imgproc.circle(m,c,rad,worth?AMBER:o==AlignmentDecision.Outcome.DEFECT?RED:o==AlignmentDecision.Outcome.NO_DEFECT?GREEN:GREY,-1,Imgproc.LINE_AA);
        if(worth){   // "?" : borderline, not a confirmed defect
            double fs=rad/22.0;int[] base=new int[1];org.opencv.core.Size ts=Imgproc.getTextSize("?",Imgproc.FONT_HERSHEY_SIMPLEX,fs,th+1,base);
            Imgproc.putText(m,"?",new Point(x-ts.width/2,y+ts.height/2),Imgproc.FONT_HERSHEY_SIMPLEX,fs,WHITE,th+1,Imgproc.LINE_AA);return;
        }
        switch(o){
            case NO_DEFECT:
                Imgproc.line(m,new Point(x-u,y),new Point(x-u*0.25,y+u*0.7),WHITE,th+1,Imgproc.LINE_AA);
                Imgproc.line(m,new Point(x-u*0.25,y+u*0.7),new Point(x+u,y-u*0.7),WHITE,th+1,Imgproc.LINE_AA);break;
            case DEFECT:
                Imgproc.line(m,new Point(x,y-u),new Point(x,y+u*0.3),WHITE,th+1,Imgproc.LINE_AA);
                Imgproc.circle(m,new Point(x,y+u*0.8),Math.max(1,th),WHITE,-1,Imgproc.LINE_AA);break;
            default:Imgproc.line(m,new Point(x-u,y),new Point(x+u,y),WHITE,th+1,Imgproc.LINE_AA);
        }
    }

    /** The marker nearest a tap at overview pixel (x, y) (its badge or the marker itself), or null. */
    static AlignmentDecision.Marker at(AlignmentAnalysis.Output o,ModelSpec model,double x,double y){
        double scale=SIZE/(2*HALF),cx=x/scale-HALF,cy=y/scale-HALF,best=0.13;AlignmentDecision.Marker hit=null;
        for(AlignmentDecision.Marker a:o.markers){
            ModelSpec.Marker mk=model.atHour(a.hour);if(mk==null)continue;
            double[] p=badgeAt(mk),q=mk.masterPoint();
            double d=Math.min(Math.hypot(cx-p[0],cy-p[1]),Math.hypot(cx-q[0],cy-q[1]));
            if(d<best){best=d;hit=a;}
        }
        return hit;
    }
}
