package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Research: crown-logo alignment (Alpha101PrintAlignment) per photo, with the seconds-hand line check.
 * Usage: run.sh Alpha101Logo <manifest.csv> <root> <out.csv>
 */
public class Alpha101Logo {
    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        try(PrintWriter csv=new PrintWriter(new FileWriter(a[2]))){
            csv.println("photo_id,status,dial_radius_px,usable,reason,offset_R,tilt_deg,symmetry,logo_clean,logo_reason,hand_detected,seconds_hand,foreign_frac,twelve_clean,twelve_reason");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                Path p=Path.of(a[1]).resolve(f[iPath]);if(!Files.exists(p)){csv.println(id+",missing,,,,,,,,,,,,,");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(p.toString());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
                if(q==null||!q.valid||q.homography==null){csv.println(id+",pose_rejected,,,,,,,,,,,,,");continue;}
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                Alpha101PrintAlignment.Result r=Alpha101PrintAlignment.measure(gray,q.homography,12,new double[]{0.12,0.38,0.575},HarnessModel.spec());
                Alpha99MarkerInterference.Check lg=r.region;
                double R=Alpha99MarkerInterference.pxPerR(q.homography);
                // for comparison only: the 12 marker's own check (the logo is gated on its own region above)
                Alpha99MarkerInterference.Check c=null;try{c=Alpha99MarkerInterference.analyse(gray,q.homography,HarnessModel.spec()).get(12);}catch(Throwable t){c=null;}
                String line=String.format(Locale.US,"%s,accepted,%.1f,%b,\"%s\",%.6f,%.4f,%.4f,%s,\"%s\",%b,%s,%s,%s,\"%s\"",id,R,r.usable,r.reason,r.offsetR,r.tiltDeg,r.symmetry,
                        lg==null?"":String.valueOf(lg.clean),lg==null?"":lg.reason,r.handDetected,lg==null?"":String.valueOf(lg.secondsHand),
                        lg==null?"":String.format(Locale.US,"%.3f",lg.foreignFrac),c==null?"":String.valueOf(c.clean),c==null?"unavailable":c.clean?"":c.reason);
                csv.println(line);System.out.println(line);
            }
        }
    }
}
