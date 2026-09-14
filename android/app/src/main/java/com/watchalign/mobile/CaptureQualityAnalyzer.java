package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

/** Fast, local pre-capture checks. Results guide the photographer and never alter QC geometry. */
final class CaptureQualityAnalyzer {
    static final class Result {
        final boolean ready; final String guidance; final double framing,tilt,roll,blur,glare;
        Result(boolean r,String g,double f,double t,double ro,double b,double gl){ready=r;guidance=g;framing=f;tilt=t;roll=ro;blur=b;glare=gl;}
    }
    static Result analyse(Bitmap bitmap){
        Mat rgba=new Mat(),bgr=new Mat(),gray=new Mat(),lap=new Mat();MatOfDouble mean=new MatOfDouble(),sd=new MatOfDouble();
        try{
            Utils.bitmapToMat(bitmap,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            DialAnalysisEngine.Circle d=DialAnalysisEngine.detectDial(bgr);
            Imgproc.Laplacian(gray,lap,CvType.CV_64F);Core.meanStdDev(lap,mean,sd);double blur=Math.pow(sd.get(0,0)[0],2);
            int bright=0,sampled=0;for(int y=0;y<gray.rows();y+=4)for(int x=0;x<gray.cols();x+=4){sampled++;if(gray.get(y,x)[0]>=248)bright++;}
            double glare=sampled==0?1:bright/(double)sampled;
            if(d==null)return new Result(false,"Centre the full dial inside the guide",0,99,99,blur,glare);
            double min=Math.min(bgr.cols(),bgr.rows()),diameter=2*d.r/min;
            double dx=(d.x-bgr.cols()/2.0)/min,dy=(d.y-bgr.rows()/2.0)/min;
            double tilt=DialAnalysisEngine.perspectiveEquivalent(bgr,d);
            DialAnalysisEngine.MarkerSet markers=DialAnalysisEngine.measureMarkerSet(bgr,d);double roll=markers.globalRotation;
            String guide;
            if(dx>0.06)guide="Move camera right";else if(dx<-0.06)guide="Move camera left";
            else if(dy>0.06)guide="Move camera down";else if(dy<-0.06)guide="Move camera up";
            else if(diameter<0.55)guide="Move closer";else if(diameter>0.88)guide="Move farther away";
            else if(Double.isFinite(tilt)&&tilt>18)guide="Reduce off-axis tilt";
            else if(Double.isFinite(roll)&&Math.abs(roll)>5)guide="Rotate camera "+(roll>0?"counter-clockwise":"clockwise");
            else if(blur<85)guide="Hold steady";else if(glare>0.08)guide="Reduce glare near the dial";else guide="Ready to capture";
            boolean ready="Ready to capture".equals(guide);
            return new Result(ready,guide,diameter,tilt,roll,blur,glare);
        }finally{rgba.release();bgr.release();gray.release();lap.release();mean.release();sd.release();}
    }
    private CaptureQualityAnalyzer(){}
}
