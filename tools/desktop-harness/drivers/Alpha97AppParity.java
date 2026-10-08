package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Alpha97 parity on real photos: the APP class Alpha97TwelveReadout (frozen constants) against the validated desktop
 * prototype Alpha97TwelveAngles (reads m12_nominal.properties / m12_genuine_reference.csv), both fed the same unchanged
 * production Report. Also checks that every non-12 line of the Alpha97 summary is byte-identical to Alpha96's
 * compactSummary. Exits non-zero on any mismatch.
 *
 * Usage: run.sh Alpha97AppParity <manifest.csv> <root> <m12_nominal.properties> <m12_genuine_reference.csv>
 */
public class Alpha97AppParity {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Alpha97TwelveAngles proto=Alpha97TwelveAngles.load(a[2],a[3]);
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();if(h.equals("local_path")||h.equals("path"))iPath=i;if(h.equals("photo_id"))iId=i;}
        int n=0,usable=0,bad=0;
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
            Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[iPath]).toString());
            if(b==null)continue;
            AutomaticDialOverlay.Result q=AutomaticDialOverlay.build(b,HarnessModel.spec());
            if(!q.valid||q.homography==null)continue;
            Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography,HarnessModel.spec());
            Alpha97TwelveReadout.Result app=Alpha97TwelveReadout.from(r,HarnessModel.ref());
            Alpha97TwelveAngles.Result pro=proto.apply(r);
            n++;
            if(app.usable!=pro.usable){System.out.println("MISMATCH usable "+id);bad++;continue;}
            if(app.usable){
                usable++;
                double[][] pairs={{app.centrelineDeg,pro.centrelineDeg},{app.leftSideDeg,pro.leftSideDeg},{app.rightSideDeg,pro.rightSideDeg},
                        {app.sidesDeg,pro.sidesDeg},{app.lateralPx,pro.lateralPx},{app.lateralR,pro.lateralR},{app.radialPx,pro.radialPx}};
                for(double[] p:pairs)if(Math.abs(p[0]-p[1])>1e-9){System.out.println("MISMATCH value "+id+" "+p[0]+" vs "+p[1]);bad++;}
                if(app.atLeastCentreline!=pro.atLeastCentreline||app.atLeastSides!=pro.atLeastSides||app.atLeastLateral!=pro.atLeastLateral){
                    System.out.println("MISMATCH counts "+id);bad++;}
            }
            String[] a96=r.compactSummary().split("\n",-1),a97=Alpha97TwelveReadout.summary(r,HarnessModel.ref()).split("\n",-1);
            if(a96.length!=a97.length){System.out.println("MISMATCH summary length "+id);bad++;}
            else for(int i=0;i<a96.length;i++)if(i!=1&&!a96[i].equals(a97[i])){System.out.println("MISMATCH summary line "+i+" "+id);bad++;}
        }
        System.out.println("Alpha97 app vs prototype parity: "+n+" photos ("+usable+" with a usable 12), "+bad+" mismatches");
        if(bad>0||n==0)System.exit(1);
    }
}
