package com.watchalign.mobile;
import android.graphics.Bitmap;
import org.opencv.core.Mat;import org.opencv.core.Size;import org.opencv.imgcodecs.Imgcodecs;import org.opencv.imgproc.Imgproc;
import javax.imageio.ImageIO;import java.io.File;import java.awt.image.BufferedImage;

/**
 * Loads a photo the way MainActivity.decode does on the phone, so harness results match
 * the device: libjpeg decode (OpenCV uses the same libjpeg-turbo family as Android),
 * inSampleSize as libjpeg DCT scaling (IMREAD_REDUCED_*), then a bilinear resize so the
 * long side is at most 1600 px (Skia's filtered createScaledBitmap).
 *
 * Before alpha56 the harness used ImageIO + Java2D scaling. That decodes and resamples
 * slightly differently, and on borderline photos the 12 result differed from the phone
 * (emulator run, rep_cf_6I00d8w image_01: gap 0.14 desktop vs 0.07 phone).
 * -Dwa.load=imageio restores the old loader for comparisons.
 */
final class Load {
    private Load(){}
    static Bitmap photo(String path){
        if("imageio".equals(System.getProperty("wa.load")))return imageio(path);
        Mat full=Imgcodecs.imread(path,Imgcodecs.IMREAD_COLOR);
        if(full.empty())return null;
        int max=Math.max(full.cols(),full.rows()),s=1;while(max/(s*2)>=1600)s*=2;
        Mat m=full;
        if(s>1&&!path.toLowerCase().endsWith(".png")){
            m=Imgcodecs.imread(path,s==2?Imgcodecs.IMREAD_REDUCED_COLOR_2:s==4?Imgcodecs.IMREAD_REDUCED_COLOR_4:Imgcodecs.IMREAD_REDUCED_COLOR_8);
        }else if(s>1){Mat o=new Mat();Imgproc.resize(full,o,new Size(full.cols()/s,full.rows()/s),0,0,Imgproc.INTER_NEAREST);m=o;}
        int cur=Math.max(m.cols(),m.rows());
        if(cur>1600){double k=1600.0/cur;Mat o=new Mat();Imgproc.resize(m,o,new Size(Math.round(m.cols()*k),Math.round(m.rows()*k)),0,0,Imgproc.INTER_LINEAR);m=o;}
        return fromBgr(m);
    }
    static Bitmap fromBgr(Mat bgr){int w=bgr.cols(),h=bgr.rows();byte[] px=new byte[w*h*3];bgr.get(0,0,px);
        BufferedImage o=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);int[] a=new int[w*h];
        for(int i=0;i<w*h;i++)a[i]=0xff000000|((px[3*i+2]&0xff)<<16)|((px[3*i+1]&0xff)<<8)|(px[3*i]&0xff);
        o.setRGB(0,0,w,h,a,0,w);return new Bitmap(o);}
    static Bitmap imageio(String path){
        try{BufferedImage raw=ImageIO.read(new File(path));if(raw==null)return null;
            BufferedImage argb=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_INT_ARGB);argb.getGraphics().drawImage(raw,0,0,null);
            Bitmap b=new Bitmap(argb);int max=Math.max(b.getWidth(),b.getHeight());
            if(max>1600){float s=1600f/max;b=Bitmap.createScaledBitmap(b,Math.round(b.getWidth()*s),Math.round(b.getHeight()*s),true);}
            return b;}catch(Exception e){return null;}
    }
}
