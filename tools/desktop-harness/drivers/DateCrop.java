package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH (harness only): export an upright, dial-plane-rectified crop of the 3 o'clock date region.
 *
 * Pose: production AutomaticDialOverlay.build (frozen, fail-closed). Image values: the production Sampler
 * (Alpha94MarkerMeasurement$Sampler, cubic B-spline over the frozen homography), reached by reflection; nothing is
 * re-implemented. Canonical frame: dial centre (0,0), minute-lattice radius 1, 12 at y = -1, 3 at x = +1.
 * Crop: x in [X0, X1], y in [Y0, Y1] at STEP R per pixel, grey, written as PNG. The date window is seen through the
 * cyclops (magnified, above the dial), so the crop shows the magnified window; the measurement step uses only ratios
 * inside that magnified image, so the magnification cancels.
 *
 * Usage: run.sh DateCrop <manifest.csv> <root> <out_dir> <out.csv>
 */
public class DateCrop {
    static final double X0=0.22,X1=1.02,Y0=-0.38,Y1=0.38,STEP=0.0025;

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Class<?> sc=Class.forName("com.watchalign.mobile.Alpha94MarkerMeasurement$Sampler");
        Constructor<?> mk=sc.getDeclaredConstructor(Mat.class,double[].class);mk.setAccessible(true);
        Method at=sc.getDeclaredMethod("at",double.class,double.class);at.setAccessible(true);
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();if(h.equals("local_path")||h.equals("path"))iPath=i;if(h.equals("photo_id"))iId=i;}
        new File(a[2]).mkdirs();
        int w=(int)Math.round((X1-X0)/STEP),h=(int)Math.round((Y1-Y0)/STEP);
        try(PrintWriter out=new PrintWriter(new FileWriter(a[3]))){
            out.println("photo_id,status,dial_radius_px,ellipse_ratio,tick_rms_px,crop,x0,y0,step");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                File img=Path.of(a[1]).resolve(f[iPath]).toFile();
                if(!img.exists()){out.println(id+",missing");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(img.getPath());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b);
                if(q==null||!q.valid||q.homography==null){out.println(id+",pose_rejected");continue;}
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                Object smp=mk.newInstance(gray,q.homography);
                byte[] px=new byte[w*h];
                for(int y=0;y<h;y++)for(int x=0;x<w;x++){double v=(double)at.invoke(smp,X0+(x+0.5)*STEP,Y0+(y+0.5)*STEP);
                    px[y*w+x]=(byte)Math.max(0,Math.min(255,Double.isFinite(v)?Math.round(v):0));}
                Mat m=new Mat(h,w,CvType.CV_8UC1);m.put(0,0,px);
                String name=id+".png";Imgcodecs.imwrite(Path.of(a[2],name).toString(),m);
                out.println(String.join(",",id,"accepted",n(q.dialRadius),n(q.ellipseRatio),n(q.fitAfter),name,n(X0),n(Y0),n(STEP)));
                out.flush();System.err.println(li+" "+id);
            }
        }
    }
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.6f",v):"";}
}
