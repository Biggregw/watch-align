package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase B calibrator measurement (adapter marker_geometry_v1, tools/watch_calibrator/production_measure.py): the app's
 * own analysis (Alpha99Pipeline: minute-track pose, MetalFit outer-metal centres) read through the recommended two-level
 * measurement (MarkerGeometry), for any supported layout (-Dwatchalign.model). Replaces CalibMeasure, which measured
 * with the legacy 124060-only WatchAlignCoreV13 engine the app no longer uses. A value is written only for a marker the
 * archive rules make ELIGIBLE (no hand, outline >= 90 % seen, reliable or a harmless reflection); everything else is
 * blank and can never reach a calibration statistic.
 *
 *   tools/desktop-harness/run.sh GeometryMeasure <list.tsv: physical_watch_id \t image_path> <out.csv>
 *
 * Columns: physical_watch_id, path, dial_source, dial_reproducible, pose_tilt_deg, then marker.<hour>.tang / .rad (% R)
 * and .tilt (deg, batons and the triangle) for hours 1-12, then ring.place_x / ring.place_y (% R, translation minus
 * parallax), ring.rot_deg and ring.scale_pct.
 */
public class GeometryMeasure {
    static final String[] Q={"tang","rad","tilt"};

    static List<String> keys(){
        List<String> k=new ArrayList<>();
        for(int h=1;h<=12;h++)for(String q:Q)k.add("marker."+h+"."+q);
        k.add("ring.place_x");k.add("ring.place_y");k.add("ring.rot_deg");k.add("ring.scale_pct");
        return k;
    }

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=Files.exists(refFile)?AlignmentReference.parse(Files.readString(refFile)):null;
        if(ref!=null){Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));}
        List<String> keys=keys();
        try(PrintWriter w=new PrintWriter(new FileWriter(a[1]))){
            w.println("physical_watch_id,path,dial_source,dial_reproducible,pose_tilt_deg,"+String.join(",",keys));
            for(String line:Files.readAllLines(Path.of(a[0]))){
                String[] f=line.split("\t");if(f.length<2||f[1].trim().isEmpty())continue;
                String id=f[0].trim(),p=f[1].trim();
                String[] v=new String[keys.size()];java.util.Arrays.fill(v,"");
                String src="unreadable";double tilt=Double.NaN;
                try{
                    Bitmap b=Alpha96Calib.loadAlpha96(p);
                    if(b!=null){
                        Alpha99Pipeline.Output o=Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);
                        if(o.pose!=null&&o.pose.valid&&o.pose.homography!=null&&o.clearlyOff!=null){
                            src="minute_track";
                            MarkerGeometry.Result g=MarkerGeometry.fromPipeline(o.clearlyOff,o.measurement,model,metal,o.pose.homography);
                            tilt=Math.toDegrees(MarkerGeometry.pose(o.pose.homography)[0]);
                            for(MarkerGeometry.Marker m:g.markers){
                                if(!m.eligible||m.hour<1||m.hour>12)continue;
                                int i=keys.indexOf("marker."+m.hour+".tang");
                                v[i]=fmt(m.tangPct);v[i+1]=fmt(m.radPct);v[i+2]=fmt(m.tiltDeg);
                            }
                            if(g.ring.ok){int i=keys.indexOf("ring.place_x");
                                v[i]=fmt(g.ring.placeXPct);v[i+1]=fmt(g.ring.placeYPct);v[i+2]=fmt(g.ring.rotDeg);v[i+3]=fmt(g.ring.scalePct);}
                        }else src="no_pose";
                    }
                }catch(Throwable t){src="error:"+t.getClass().getSimpleName();}
                w.println(id+","+p.replace(',',';')+","+src+",,"+fmt(tilt)+","+String.join(",",v));w.flush();
            }
        }
    }
    static String fmt(double x){return Double.isFinite(x)?String.format(Locale.US,"%.6f",x):"";}
}
