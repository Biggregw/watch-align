package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH PROTOTYPE: production Alpha96 path + the lighting-robust 12 report (Alpha97TwelveAngles).
 * Prints the production 12 line next to the prototype line and writes a CSV.
 *
 * Usage: run.sh Alpha97TwelveProto <manifest.csv> <root> <m12_nominal.properties> <m12_genuine_reference.csv> <out.csv>
 */
public class Alpha97TwelveProto {
    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        nu.pattern.OpenCV.loadLocally();
        Alpha97TwelveAngles proto=Alpha97TwelveAngles.load(a[2],a[3]);
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();if(h.equals("local_path")||h.equals("path"))iPath=i;if(h.equals("photo_id"))iId=i;}
        System.out.printf(Locale.US,"genuine nominal from %d watches; context from %d per-watch values%n",proto.nom.nWatches,proto.lateralR.length);
        try(PrintWriter out=new PrintWriter(new FileWriter(a[4]))){
            out.println("photo_id,status,dial_radius_px,usable,reason,centreline_deg,left_side_deg,right_side_deg,sides_deg,lateral_px,lateral_R,"
                    +"radial_px_note,n_genuine,at_least_centreline,at_least_sides,at_least_lateral");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String rel=f[iPath],id=iId>=0?f[iId]:rel;
                Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(rel).toString());
                if(b==null){out.println(id+",unreadable");continue;}
                AutomaticDialOverlay.Result q=AutomaticDialOverlay.build(b);
                if(!q.valid||q.homography==null){out.println(id+",pose_rejected");System.out.println(id+": pose rejected ("+q.reason+")");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography);
                Alpha97TwelveAngles.Result t=proto.apply(r);
                out.println(String.join(",",id,"accepted",n(r.dialRadiusPx),Boolean.toString(t.usable),t.reason.replace(',',';'),
                        n(t.centrelineDeg),n(t.leftSideDeg),n(t.rightSideDeg),n(t.sidesDeg),n(t.lateralPx),n(t.lateralR),n(t.radialPx),
                        Integer.toString(t.usable?t.nGenuine:0),Integer.toString(t.atLeastCentreline),Integer.toString(t.atLeastSides),
                        Integer.toString(t.atLeastLateral)));
                System.out.println(id+"\n  production:  "+r.compactSummary().split("\n")[1]+"\n  prototype:   "+t.line());
            }
        }
    }
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}
}
