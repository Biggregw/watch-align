package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 1.6.6 research (read-only): how far a baton's outer-metal fit moves when one side of its outline is left out, on photos
 * where the full fit is reliable. This is the error of the one-side refit MetalFit uses beside a confirmed hand
 * (MetalFit.ALT_U_POS_R / ALT_U_ROT_DEG are set from the verified genuine photos with this driver):
 *
 *   SideDrop manifest.csv image_root out.csv
 *
 * One row per baton and side left out: dpos_pct (% R), dtilt_deg, whether what remains is still clean.
 */
public class SideDrop {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path manifest=Path.of(a[0]),root=Path.of(a[1]),out=Path.of(a[2]);
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=Files.exists(refFile)?AlignmentReference.parse(Files.readString(refFile)):null;
        if(ref!=null){Path metalFile=refFile.resolveSibling("metal_alignment.properties");
            if(Files.exists(metalFile))ref.metalExtra=MetalAlignment.parse(Files.readString(metalFile));}
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(manifest));
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
            w.println("photo_id,model,rpx,hour,side,side_len_r,clean,dpos_pct,dtilt_deg");
            for(Map<String,String> r:rows){
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));
                if(!Files.exists(img))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());
                MetalFit.SIDE_DROP_PROBE=new ArrayList<>();
                Alpha99Pipeline.Output o;
                try{o=b==null?null:Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);}catch(Throwable t){o=null;}
                List<double[]> probe=MetalFit.SIDE_DROP_PROBE;MetalFit.SIDE_DROP_PROBE=null;
                if(o==null||o.clearlyOff==null)continue;
                for(double[] p:probe){   // the final fitAll pass only (MetalFit clears the probe before it)
                    int hour=hourOf(o.clearlyOff,p[6],p[7]);
                    ClearlyOff.Marker m=o.clearlyOff.at(hour);
                    if(m==null||!WatchClassification.reliable(m))continue;   // the marker's own reading must be reliable
                    w.println(String.join(",",id,model.id,f(o.clearlyOff.rpx),String.valueOf(hour),String.valueOf((int)p[0]),f(p[1]),String.valueOf((int)p[2]),
                            f(100*Math.hypot(p[3],p[4])),f(Math.abs(p[5]))));
                }
                w.flush();System.out.println("SIDEDROP "+id);
            }
        }
    }
    static int hourOf(ClearlyOff.Result r,double x,double y){
        int best=-1;double bd=1e9;
        for(ClearlyOff.Marker m:r.markers){if(m.cObs==null)continue;double d=Math.hypot(m.cObs[0]-x,m.cObs[1]-y);if(d<bd){bd=d;best=m.hour;}}
        return bd<0.02?best:-1;
    }
    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.4f",v):"";}
}
