package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** Fits the physical dial edge from a BGR image around a seed circle (OpenCV adapter for DialEdgeEllipseFit). */
final class DialEdgeFitter {
    private DialEdgeFitter(){}

    static DialEdgeEllipseFit.Fit fitBgr(Mat bgr,double x,double y,double r){
        Mat gray=new Mat(),blur=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            final int w=blur.cols(),h=blur.rows();
            if(w<8||h<8)return null;
            final byte[] px=new byte[w*h];
            blur.get(0,0,px);
            DialEdgeEllipseFit.Intensity img=(fx0,fy0)->{
                int x0=(int)Math.floor(fx0),y0=(int)Math.floor(fy0);
                double fx=fx0-x0,fy=fy0-y0;
                int i=y0*w+x0;
                double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+w]&0xff,d=px[i+w+1]&0xff;
                return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;
            };
            return DialEdgeEllipseFit.fit(img,w,h,x,y,r);
        }catch(Throwable t){return null;}
        finally{gray.release();blur.release();}
    }
}
