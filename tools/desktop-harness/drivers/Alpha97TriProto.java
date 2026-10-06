package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH PROTOTYPE: production Alpha96 path + the genuine-calibrated 12 reference (Alpha97TriangleNominal).
 *
 * Per photo: AutomaticDialOverlay.build -> Alpha94MarkerMeasurement.analyse (unchanged), then re-centres the
 * 12 triangle on the nominal. Prints the production 12 line next to the prototype line, and writes a CSV.
 *
 * Usage: run.sh Alpha97TriProto <manifest.csv> <root> <m12_nominal.properties> <out.csv>
 */
public class Alpha97TriProto {
    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        nu.pattern.OpenCV.loadLocally();
        Alpha97TriangleNominal nom=Alpha97TriangleNominal.load(a[2]);
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);
        int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();
            if(h.equals("local_path")||h.equals("path")||h.equals("image_path"))iPath=i;if(h.equals("photo_id"))iId=i;}
        System.out.printf(Locale.US,"nominal (%d genuine watches): radial %+.4f R, tangential %+.4f R, L/R %+.2f/%+.2f deg, base %+.2f, centreline %+.2f%n",
                nom.nWatches,nom.radialR,nom.tangentialR,nom.leftSideDeg,nom.rightSideDeg,nom.baseTiltDeg,nom.rotationDeg);
        try(PrintWriter out=new PrintWriter(new FileWriter(a[3]))){
            out.println("photo_id,status,dial_radius_px,m12_usable,m12_local_radial_px,m12_local_tangential_px,m12_local_px,"
                    +"ref_usable,ref_reason,ref_right_px,ref_down_px,ref_offset_px,ref_offset_R,ref_rotation_deg,ref_left_side_deg,ref_right_side_deg,ref_base_tilt_deg");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);
                String rel=f[iPath],id=iId>=0?f[iId]:rel;
                Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(rel).toString());
                if(b==null){out.println(id+",unreadable");continue;}
                AutomaticDialOverlay.Result q=AutomaticDialOverlay.build(b);
                if(!q.valid||q.homography==null){out.println(id+",pose_rejected");System.out.println(id+": pose rejected ("+q.reason+")");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography);
                Alpha97TriangleNominal.Result t=nom.apply(r);
                Alpha94MarkerMeasurement.Marker m=r.triangle;
                boolean mu=m!=null&&m.usable;
                out.println(String.join(",",id,"accepted",n(r.dialRadiusPx),Boolean.toString(mu),
                        n(mu?m.localRadialPx:Double.NaN),n(mu?m.localTangentialPx:Double.NaN),n(mu?m.localOffsetPx:Double.NaN),
                        Boolean.toString(t.usable),t.reason.replace(',',';'),n(t.rightPx),n(t.downPx),n(t.offsetPx),n(t.offsetR),
                        n(t.rotationDeg),n(t.leftSideDeg),n(t.rightSideDeg),n(t.baseTiltDeg)));
                String prod=r.compactSummary().split("\n")[1];
                System.out.println(id+"\n  production:  "+prod+"\n  prototype:   "+t.line());
            }
        }
    }
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}
}
