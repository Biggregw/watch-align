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

/**
 * Research: upright canonical crop of the region below the 12 (x -0.2..0.2, y -0.62..-0.22) with a grid every 0.05 R and
 * the logo box, plus (_grid.png) the logo region's interference grid: grey dial, white foreign, red hand evidence, blue
 * halo band, dark face (not judged); outer rows at the top. Images stay local (research, never committed).
 */
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
            double bx=0.12,b0=0.38,b1=0.575;
            Imgproc.rectangle(m,new Point((-bx+0.20)/s,(-b1+0.62)/s),new Point((bx+0.20)/s,(-b0+0.62)/s),new Scalar(255,255,0,255),1);
            Imgproc.cvtColor(m,m,Imgproc.COLOR_RGBA2BGR);Imgcodecs.imwrite(out.resolve(f[0]+".png").toString(),m);
            Mat gray=new Mat();Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Alpha101PrintAlignment.Result r=Alpha101PrintAlignment.measure(gray,q.homography,12,new double[]{bx,b0,b1},HarnessModel.spec());
            Alpha99MarkerInterference.Check c=r.region;
            if(c==null||c.grid==null)continue;
            int sc=Math.max(1,400/c.rows);Mat gm=new Mat(c.rows*sc,c.cols*sc,org.opencv.core.CvType.CV_8UC3);
            for(int i=0;i<c.rows;i++)for(int j=0;j<c.cols;j++){int v=c.grid[i*c.cols+j];
                Scalar col=v==-1?new Scalar(0,0,0):v==0?new Scalar(90,90,90):v==1?new Scalar(255,255,255):v==2?new Scalar(0,0,255):v==3?new Scalar(255,120,0):new Scalar(40,40,40);
                int y=(c.rows-1-i)*sc;Imgproc.rectangle(gm,new Point(j*sc,y),new Point(j*sc+sc-1,y+sc-1),col,-1);}
            Imgcodecs.imwrite(out.resolve(f[0]+"_grid.png").toString(),gm);
            System.out.printf("%s clean=%b reason=%s foreign=%.3f touch=%.3f gap=%.1f%n",f[0],c.clean,c.reason,c.foreignFrac,c.touchSpanR,c.touchGapPx);
        }
    }
}
