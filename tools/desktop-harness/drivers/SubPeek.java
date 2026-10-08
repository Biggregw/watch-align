package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Research: upright canonical close-up of one dial region per photo (default: the lower half, centre (0, 0.45),
 * half-size 0.55), on the frozen pose of the harness model, for visual review in CI logs only (never committed).
 * Usage: run.sh SubPeek <manifest.csv> <root> <out_dir> [cx cy half]
 */
public class SubPeek {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        double cx=a.length>5?Double.parseDouble(a[3]):0,cy=a.length>5?Double.parseDouble(a[4]):0.45,half=a.length>5?Double.parseDouble(a[5]):0.55;
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        Path out=Path.of(a[2]);Files.createDirectories(out);
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);Path p=Path.of(a[1]).resolve(f[iPath]);if(!Files.exists(p))continue;
            Bitmap b=Alpha96Calib.loadAlpha96(p.toString());
            AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
            if(q==null||!q.valid||q.homography==null)continue;
            Mat rgba=new Mat();Utils.bitmapToMat(b,rgba);
            Mat m=Alpha98Closeups.render(rgba,q.homography,cx,cy,half);
            Imgproc.cvtColor(m,m,Imgproc.COLOR_RGBA2BGR);Imgcodecs.imwrite(out.resolve(f[iId]+".jpg").toString(),m);
        }
    }
}
