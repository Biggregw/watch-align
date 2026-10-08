package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH ONLY: Alpha96 genuine-calibration batch runner.
 *
 * Runs the exact production Alpha96 path per photo, with no measurement maths of its own:
 *   AutomaticDialOverlay.build(bitmap)            frozen minute-lattice pose H (fail-closed)
 *   Alpha94MarkerMeasurement.analyse(bitmap, H)   ring, rounds, 6/9 batons, 12 triangle (research)
 * and writes every field the production objects expose to one CSV row.
 *
 * Photos are loaded with the Alpha96 app rule (PerspectiveOverlayPocActivity.readBitmap): power-of-two
 * subsampling while the long side stays >= 3200 px, then a scale-down to 3200 px.
 * Occluded / unassessable markers are written as usable=false with the production reason; no value
 * is estimated for them.
 *
 * Usage: run.sh Alpha96Calib <manifest.csv> <dataset_root> <out.csv>
 * Manifest columns used (others are copied through by the Python aggregator): local_path|path|image_path,
 * photo_id (optional).
 */
public class Alpha96Calib {
    static final int[] ROUNDS={1,2,4,5,7,8,10,11};

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);
        int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();
            if(h.equals("local_path")||h.equals("path")||h.equals("image_path"))iPath=i;
            if(h.equals("photo_id"))iId=i;}
        if(iPath<0)throw new IllegalArgumentException("manifest needs local_path/path/image_path");
        Path base=Path.of(a[1]);
        try(PrintWriter out=new PrintWriter(new FileWriter(a[2]))){
            out.println(String.join(",",header()));
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);
                String rel=f[iPath],id=iId>=0?f[iId]:rel;
                File img=base.resolve(rel).toFile();
                List<String> row=new ArrayList<>();row.add(csv(id));row.add(csv(rel));
                if(!img.exists()){row.add("missing");pad(row);out.println(String.join(",",row));continue;}
                long t0=System.nanoTime();
                try{
                    Bitmap b=loadAlpha96(img.getPath());
                    if(b==null){row.add("unreadable");pad(row);out.println(String.join(",",row));continue;}
                    AutomaticDialOverlay.Result q=AutomaticDialOverlay.build(b,HarnessModel.spec());
                    row.add(q.valid?"accepted":"pose_rejected");
                    row.add(Integer.toString(b.getWidth()));row.add(Integer.toString(b.getHeight()));
                    row.add(csv(q.reason));row.add(Boolean.toString(q.twelvePhaseUsed));
                    row.add(n(q.dialRadius));row.add(n(q.ellipseRatio));row.add(n(q.fitAfter));
                    row.add(Integer.toString(q.detectedTicks));row.add(Integer.toString(q.completePairs));
                    if(!q.valid||q.homography==null){pad(row);out.println(String.join(",",row));continue;}
                    Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography,HarnessModel.spec());
                    row.add(n(r.dialRadiusPx));
                    Alpha94MarkerMeasurement.Ring g=r.ring;
                    row.add(Boolean.toString(g!=null&&g.usable));row.add(g==null?"":Integer.toString(g.n));
                    row.add(n(g==null?Double.NaN:g.shiftXPx));row.add(n(g==null?Double.NaN:g.shiftYPx));
                    row.add(n(g==null?Double.NaN:g.shiftPx));row.add(n(g==null?Double.NaN:g.scalePct));
                    row.add(n(g==null?Double.NaN:g.rotationDeg));
                    marker(row,r.triangle,true);
                    marker(row,r.atHour(6),false);marker(row,r.atHour(9),false);
                    for(int h:ROUNDS)marker(row,r.atHour(h),false);
                    row.add(Long.toString((System.nanoTime()-t0)/1000000));
                    out.println(String.join(",",row));
                }catch(Throwable t){
                    List<String> e=new ArrayList<>();e.add(csv(id));e.add(csv(rel));e.add("error");e.add("");e.add("");
                    e.add(csv(t.getClass().getSimpleName()+": "+t.getMessage()));pad(e);out.println(String.join(",",e));
                }
                out.flush();
                System.err.println(li+"/"+(lines.size()-1)+" "+rel);
            }
        }
    }

    /** Alpha96 PerspectiveOverlayPocActivity.readBitmap: inSampleSize while long/(s*2)>=3200, then scale to 3200. */
    static Bitmap loadAlpha96(String path){
        Mat full=Imgcodecs.imread(path,Imgcodecs.IMREAD_COLOR);
        if(full.empty())return null;
        int max=Math.max(full.cols(),full.rows()),s=1;while(max/(s*2)>=3200)s*=2;
        Mat m=full;
        if(s>1&&!path.toLowerCase().endsWith(".png")){
            m=Imgcodecs.imread(path,s==2?Imgcodecs.IMREAD_REDUCED_COLOR_2:s==4?Imgcodecs.IMREAD_REDUCED_COLOR_4:Imgcodecs.IMREAD_REDUCED_COLOR_8);
        }else if(s>1){Mat o=new Mat();Imgproc.resize(full,o,new Size(full.cols()/s,full.rows()/s),0,0,Imgproc.INTER_AREA);m=o;}
        int cur=Math.max(m.cols(),m.rows());
        if(cur>3200){double k=3200.0/cur;Mat o=new Mat();Imgproc.resize(m,o,new Size(Math.round(m.cols()*k),Math.round(m.rows()*k)),0,0,Imgproc.INTER_LINEAR);m=o;}
        return Load.fromBgr(m);
    }

    static final String[] MF={"usable","reason","right_px","down_px","raw_dx_px","raw_dy_px","raw_px","radial_px","tangential_px",
            "local_px","local_radial_px","local_tangential_px","rotation_deg","radius_err_px","edge_support","outline_integrity"};
    static final String[] TF={"left_side_err_deg","right_side_err_deg","base_tilt_deg"};

    static List<String> header(){
        List<String> h=new ArrayList<>(List.of("photo_id","path","status","width","height","pose_reason","twelve_phase_used",
                "dial_radius_px_edge","ellipse_ratio","tick_rms_px","ticks_used","tick_sectors","dial_radius_px",
                "ring_usable","ring_n","ring_shift_x_px","ring_shift_y_px","ring_shift_px","ring_scale_pct","ring_rotation_deg"));
        for(String p:new String[]{"m12","m6","m9"}){for(String f:MF)h.add(p+"_"+f);if(p.equals("m12"))for(String f:TF)h.add(p+"_"+f);}
        for(int r:ROUNDS)for(String f:MF)h.add("m"+r+"_"+f);
        h.add("ms");
        return h;
    }

    static void marker(List<String> row,Alpha94MarkerMeasurement.Marker m,boolean tri){
        if(m==null){row.add("false");row.add("unavailable");for(int i=2;i<MF.length;i++)row.add("");if(tri)for(String x:TF)row.add("");return;}
        row.add(Boolean.toString(m.usable));row.add(csv(m.reason));
        boolean u=m.usable;
        row.add(u?n(m.dialRightPx):"");row.add(u?n(m.dialDownPx):"");
        row.add(u?n(m.rawDxPx):"");row.add(u?n(m.rawDyPx):"");row.add(u?n(m.rawOffsetPx):"");
        row.add(u?n(m.radialPx):"");row.add(u?n(m.tangentialPx):"");
        row.add(u?n(m.localOffsetPx):"");row.add(u?n(m.localRadialPx):"");row.add(u?n(m.localTangentialPx):"");
        row.add(u&&!"round".equals(m.kind)?n(m.rotationDeg):"");
        row.add(u?n(m.radiusErrPx):"");
        row.add(n(m.fitSupport));
        row.add(Double.isFinite(m.fitScorePx)?n(1.0-m.fitScorePx):"");
        if(tri){row.add(u?n(m.leftSideErrDeg):"");row.add(u?n(m.rightSideErrDeg):"");row.add(u?n(m.baseTiltDeg):"");}
    }

    static void pad(List<String> row){int want=header().size();while(row.size()<want)row.add("");}
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}
    static String csv(String s){if(s==null)return "";return s.replace(',',';').replace('\n',' ');}
}
