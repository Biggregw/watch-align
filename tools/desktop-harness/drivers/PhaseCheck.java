package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH. Does the frozen pose sit on the right minute branch? For the pose and for the pose turned by k minutes
 * (k = -3..3, 6 deg each), samples every applied marker's face against the dial at the same radius half-way to the next
 * marker, and prints the median face-minus-dial contrast per k. A correct pose peaks at k = 0; a pose locked one minute
 * off (12 triangle hidden by the hands) peaks at k = +-1.
 *
 * Usage: run.sh PhaseCheck <manifest.csv> <root>
 */
public class PhaseCheck {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        ModelSpec model=HarnessModel.spec();
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);String id=f[iId];
            Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[iPath]).toString());
            AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,model);
            if(q==null||!q.valid){System.out.println(id+" no pose: "+(q==null?"load":q.reason));continue;}
            Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Alpha99MarkerInterference.Sampler img=Alpha99MarkerInterference.sampler(gray,q.homography);
            StringBuilder s=new StringBuilder(String.format(Locale.US,"%s",id));
            double best=Double.NEGATIVE_INFINITY;int bk=0;double[] sc=new double[7];
            for(int k=-3;k<=3;k++){sc[k+3]=score(img,q.homography,model,Math.toRadians(6.0*k));if(sc[k+3]>best){best=sc[k+3];bk=k;}}
            for(int k=-3;k<=3;k++)s.append(String.format(Locale.US," %+d:%5.1f",k,sc[k+3]));
            double second=Double.NEGATIVE_INFINITY;for(int k=-3;k<=3;k++)if(k!=bk)second=Math.max(second,sc[k+3]);
            s.append(String.format(Locale.US,"  best %+d (margin %.1f)",bk,best-second));
            s.append(" | app pose turned by "+q.markerBranchTurn+" min");System.out.println(s);
        }
    }

    static double score(Alpha99MarkerInterference.Sampler img,double[] H,ModelSpec model,double turn){return Alpha104MarkerBranch.score(img,H,model,turn);}
}
