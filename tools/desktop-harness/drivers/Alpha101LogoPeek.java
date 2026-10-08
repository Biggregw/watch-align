package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Research: upright canonical crop of the region below the 12 (x -0.2..0.2, y -0.62..-0.22) with a grid every 0.05 R. */
public class Alpha101LogoPeek {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));Path out=Path.of(a[2]);Files.createDirectories(out);
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);
            Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[1]).toString());
            AutomaticDialOverlay.Result q=AutomaticDialOverlay.build(b,HarnessModel.spec());
            if(q==null||!q.valid)continue;
            Mat rgba=new Mat();Utils.bitmapToMat(b,rgba);
            Mat m=Alpha98Closeups.render(rgba,q.homography,0,-0.42,0.20);
            double s=2*0.20/Alpha98Closeups.SIZE;
            for(double y=-0.60;y<=-0.24;y+=0.05){int py=(int)Math.round((y-(-0.62))/s);Imgproc.line(m,new Point(0,py),new Point(10,py),new Scalar(255,0,0,255),1);
                Imgproc.putText(m,String.format("%.2f",y),new Point(12,py+4),Imgproc.FONT_HERSHEY_SIMPLEX,0.35,new Scalar(255,0,0,255),1);}
            Imgproc.line(m,new Point(Alpha98Closeups.SIZE/2.0,0),new Point(Alpha98Closeups.SIZE/2.0,Alpha98Closeups.SIZE),new Scalar(0,255,0,255),1);
            Imgproc.cvtColor(m,m,Imgproc.COLOR_RGBA2BGR);Imgcodecs.imwrite(out.resolve(f[0]+".png").toString(),m);
        }
    }
}
