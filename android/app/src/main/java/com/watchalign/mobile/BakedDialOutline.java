package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Converts the baked photographic dial into a single-colour transparent outline.
 * The geometry still comes from the real dial image, but no photographic fill is shown.
 */
final class BakedDialOutline {
    private BakedDialOutline(){}

    static Bitmap bitmap(){
        Bitmap ref=BakedDialOverlay.bitmap();
        Mat rgba=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat(),dilated=new Mat();
        try{
            Utils.bitmapToMat(ref,rgba);
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(3,3),0.7);
            Imgproc.Canny(blur,edges,28,85);

            // Make the line visible after the reference is scaled to a phone photo.
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(2,2));
            try{Imgproc.dilate(edges,dilated,kernel);}finally{kernel.release();}

            int w=dilated.cols(),h=dilated.rows();
            byte[] e=new byte[w*h];dilated.get(0,0,e);
            Mat out=new Mat(h,w,CvType.CV_8UC4);
            byte[] px=new byte[w*h*4];
            for(int i=0;i<e.length;i++){
                if((e[i]&0xff)==0)continue;
                int j=i*4;
                px[j]=(byte)70;      // R
                px[j+1]=(byte)245;   // G
                px[j+2]=(byte)255;   // B
                px[j+3]=(byte)255;   // A
            }
            out.put(0,0,px);
            Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(out,result);
            out.release();
            return result;
        }finally{
            dilated.release();edges.release();blur.release();gray.release();rgba.release();
        }
    }
}
