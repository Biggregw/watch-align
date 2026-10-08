package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Alpha98 date-window parity: the APP class Alpha98DateWindow against the validated research prototype
 * (date_window.py results CSV computed on DateCrop crops). Checks (1) the app crop is pixel-identical to the DateCrop PNG,
 * (2) usable / withheld decisions agree, (3) window tilt agrees within tol. Exits non-zero on any mismatch.
 *
 * Usage: run.sh Alpha98DateParity <manifest.csv> <root> <crop_dir> <date_window.csv> [tol_deg]
 */
public class Alpha98DateParity {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        double tol=a.length>4?Double.parseDouble(a[4]):0.01;
        Map<String,String[]> py=new HashMap<>();List<String> pl=Files.readAllLines(Path.of(a[3]));String[] ph=pl.get(0).split(",",-1);
        int iu=-1,it=-1,ir=-1;for(int i=0;i<ph.length;i++){if(ph[i].equals("usable"))iu=i;if(ph[i].equals("window_tilt_deg"))it=i;if(ph[i].equals("reason"))ir=i;}
        for(int i=1;i<pl.size();i++){String[] f=pl.get(i).split(",",-1);py.put(f[0],f);}
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        int n=0,bad=0,usable=0,cropSame=0;
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
            Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[iPath]).toString());
            AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
            if(q==null||!q.valid||q.homography==null)continue;
            Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Mat crop=Alpha98DateWindow.crop(gray,q.homography,HarnessModel.spec().date);
            Mat ref=Imgcodecs.imread(Path.of(a[2],id+".png").toString(),Imgcodecs.IMREAD_GRAYSCALE);
            Mat diff=new Mat();org.opencv.core.Core.absdiff(crop,ref,diff);double mx=org.opencv.core.Core.minMaxLoc(diff).maxVal;
            if(mx==0)cropSame++;else{System.out.println("CROP DIFF "+id+" max "+mx);bad++;}
            Alpha98DateWindow.Result r=Alpha98DateWindow.measure(crop,HarnessModel.spec().date);
            String[] p=py.get(id);n++;
            if(p==null){System.out.println("no python row "+id);bad++;continue;}
            boolean pu="True".equals(p[iu]);
            if(pu!=r.usable){System.out.println("USABLE MISMATCH "+id+" java="+r.usable+" ("+r.reason+") python="+pu+" ("+p[ir]+")");bad++;continue;}
            if(r.usable){usable++;double d=Math.abs(r.windowTiltDeg-Double.parseDouble(p[it]));
                if(d>tol){System.out.println(String.format(java.util.Locale.US,"TILT MISMATCH %s java %.4f python %s",id,r.windowTiltDeg,p[it]));bad++;}}
        }
        System.out.println("Alpha98 date-window parity: "+n+" photos, "+cropSame+" crops pixel-identical, "+usable+" usable, "+bad+" mismatches");
        if(bad>0||n==0)System.exit(1);
    }
}
