package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Evidence picture for one marker (1.6.0): the whole dial, upright, with the references the clearly-off check measures
 * against, as the owner judges by eye:
 *   blue lines   each hour tick of the printed minute track, through the centre, to the opposite hour tick
 *   blue rings   where the round markers' centres and the batons' outer ends should sit (this dial's own ring)
 *   circle       the marker: red = clearly off, green = no visible issue, grey = can't check
 *   red cross    the measured centre of a marker that is clearly off
 * DISPLAY ONLY: nothing drawn here feeds the decision.
 */
final class ClearlyOffEvidence {
    static final int SIZE=720;
    static final double HALF=1.12;
    static final Scalar GUIDE=new Scalar(80,200,255,255),RED=new Scalar(255,59,48,255),GREEN=new Scalar(52,199,89,255),
            GREY=new Scalar(160,160,168,255),BLACK=new Scalar(0,0,0,255);

    private ClearlyOffEvidence(){}

    static Bitmap render(Mat rgba,double[] H,ModelSpec model,ClearlyOff.Result r,AlignmentDecision.Marker am){
        Mat m=Alpha98Closeups.render(rgba,H,0,0,HALF);
        Mat big=new Mat();Imgproc.resize(m,big,new Size(SIZE,SIZE),0,0,Imgproc.INTER_CUBIC);m.release();
        int th=Math.max(1,SIZE/480);
        if(r!=null){
            Mat lay=big.clone();
            for(int h=0;h<6;h++){
                Double a=r.lines.get(h),b=r.lines.get(h+6);if(a==null||b==null)continue;
                Imgproc.line(lay,px(ClearlyOff.cpt(0.93,a)),px(ClearlyOff.cpt(0.93,b)),GUIDE,th,Imgproc.LINE_AA);
            }
            double s=Double.isFinite(r.ringScale)?r.ringScale:1;
            for(double rr:new double[]{r.roundCentreR,r.batonEndR})
                if(Double.isFinite(rr))Imgproc.circle(lay,px(new double[]{0,0}),(int)Math.round(rr*s*SIZE/(2*HALF)),GUIDE,th,Imgproc.LINE_AA);
            Core.addWeighted(lay,0.75,big,0.25,0,big);lay.release();
        }
        ModelSpec.Marker mk=model.atHour(am.hour);
        if(mk!=null){
            double[] c=mk.masterPoint();
            double rad=(mk.shape==ModelSpec.Shape.ROUND?0.12:mk.shape==ModelSpec.Shape.BATON?0.17:0.18)*SIZE/(2*HALF);
            Scalar col=am.outcome==AlignmentDecision.Outcome.DEFECT?RED:am.outcome==AlignmentDecision.Outcome.NO_DEFECT?GREEN:GREY;
            Imgproc.circle(big,px(c),(int)Math.round(rad),BLACK,4*th+2,Imgproc.LINE_AA);
            Imgproc.circle(big,px(c),(int)Math.round(rad),col,3*th,Imgproc.LINE_AA);
            ClearlyOff.Marker t=am.track;
            if(t!=null&&t.state==ClearlyOff.State.CLEARLY_OFF&&t.cObs!=null){
                Point p=px(t.cObs);int u=SIZE/90;
                Imgproc.line(big,new Point(p.x-u,p.y),new Point(p.x+u,p.y),RED,2*th,Imgproc.LINE_AA);
                Imgproc.line(big,new Point(p.x,p.y-u),new Point(p.x,p.y+u),RED,2*th,Imgproc.LINE_AA);
            }
            String lab=String.valueOf(am.hour==0?12:am.hour);
            double[] q=ClearlyOff.cpt(Math.min(1.06,HALF-0.04),Math.toRadians(30*(am.hour%12)));
            double fs=SIZE/600.0;int[] base=new int[1];Size ts=Imgproc.getTextSize(lab,Imgproc.FONT_HERSHEY_SIMPLEX,fs,3*th,base);
            Point at=px(q);at=new Point(at.x-ts.width/2,at.y+ts.height/2);
            Imgproc.putText(big,lab,at,Imgproc.FONT_HERSHEY_SIMPLEX,fs,BLACK,6*th,Imgproc.LINE_AA);
            Imgproc.putText(big,lab,at,Imgproc.FONT_HERSHEY_SIMPLEX,fs,col,3*th,Imgproc.LINE_AA);
        }
        Bitmap bm=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(big,bm);big.release();return bm;
    }

    /** canonical -> picture px (the picture spans -HALF..HALF in both axes) */
    static Point px(double[] p){double k=SIZE/(2*HALF);return new Point((p[0]+HALF)*k-0.5,(p[1]+HALF)*k-0.5);}
}
