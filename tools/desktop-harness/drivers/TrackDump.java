package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Clearly-off check (ClearlyOff, app 1.6.0) on each photo of a manifest, for parity with tools/research/track and for
 * the genuine regression:
 *
 *   TrackDump manifest.csv image_root out.csv
 *
 * One row per marker: the app's state and every measured number.
 */
public class TrackDump {
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
            w.println("photo_id,watch,model,rpx,hour,shape,state,gate,side_pct,inout_pct,tilt_line_deg,ring_turn_pct,bad_ticks,gap_pct");
            for(Map<String,String> r:rows){
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));
                if(!Files.exists(img))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());
                Alpha99Pipeline.Output o;
                try{o=b==null?null:Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);}catch(Throwable t){o=null;}
                if(o==null||o.clearlyOff==null){System.out.println("NOPOSE "+id);continue;}
                ClearlyOff.Result c=o.clearlyOff;
                for(ClearlyOff.Marker m:c.markers){
                    w.println(String.join(",",id,r.getOrDefault("physical_watch_id",""),model.id,f(c.rpx),String.valueOf(m.hour),AlignmentReference.key(m.shape),
                            m.state.name(),String.join("; ",m.gate).replace(",",""),f(m.sidePct),f(m.inoutPct),f(m.tiltDeg),f(c.ringTurnPct),String.valueOf(c.badTicks),f(m.gapPct)));
                }
                w.flush();System.out.println("TRACK "+id);
            }
        }
    }
    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.4f",v):"";}
}
