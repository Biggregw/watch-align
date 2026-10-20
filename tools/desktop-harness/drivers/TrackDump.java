package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            w.println("photo_id,watch,model,rpx,hour,shape,state,gate,side_pct,inout_pct,tilt_line_deg,ring_turn_pct,bad_ticks,gap_pct,tilt_centroid_deg,rot_left_deg,rot_right_deg,rot_rms_px,rot_note,rot_vs_line_deg,tip_dr_px,source,uncertain,cover,fit_rms_px,bad_frac,se_pos_pct,se_rot_deg,gap_from_fit,reason,edge_bias_px");
            for(Map<String,String> r:rows){
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));
                if(!Files.exists(img))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());
                Alpha99Pipeline.Output o;
                try{o=b==null?null:Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);}catch(Throwable t){o=null;}
                if(o==null||o.clearlyOff==null){System.out.println("NOPOSE "+id+(o!=null&&o.clearlyOffError!=null?" "+o.clearlyOffError:""));
                    if(o!=null&&o.alignment!=null)System.out.println("CLASS "+id+" "+WatchClassification.classify(null,model,o.clearlyOffError).verdict);continue;}
                ClearlyOff.Result c=o.clearlyOff;
                for(ClearlyOff.Marker m:c.markers){
                    w.println(String.join(",",id,r.getOrDefault("physical_watch_id",""),model.id,f(c.rpx),String.valueOf(m.hour),AlignmentReference.key(m.shape),
                            m.state.name(),String.join("; ",m.gate).replace(",",""),f(m.sidePct),f(m.inoutPct),f(m.tiltDeg),f(c.ringTurnPct),String.valueOf(c.badTicks),f(m.gapPct),
                            f(m.tiltCentroidDeg),f(m.rotLeftDeg),f(m.rotRightDeg),f(m.rotRmsPx),m.rotNote.replace(",",";"),f(m.rotLineDeg),f(m.tipDrPx),
                            m.source,m.uncertain.replace(",",";"),f(m.cover),f(m.fitRmsPx),f(m.badFrac),f(m.sePosPct),f(m.seRotDeg),m.gapFromFit?"1":"",m.reason.replace(",",";"),
                            c.edgeBiasPx.containsKey(m.shape)?f(c.edgeBiasPx.get(m.shape)):""));
                }
                // -Dwatchalign.render=DIR: write the evidence close-ups (all markers with -Dwatchalign.renderAll=1, else the
                // flagged ones and those named in -Dwatchalign.renderIds=photo:hour,...)
                String rd=System.getProperty("watchalign.render");
                if(rd!=null){java.nio.file.Path dir=Paths.get(rd);Files.createDirectories(dir);String ids=","+System.getProperty("watchalign.renderIds","")+",";
                    for(ClearlyOff.Marker m:c.markers){boolean want="1".equals(System.getProperty("watchalign.renderAll"))||m.state==ClearlyOff.State.CLEARLY_OFF
                            ||m.state==ClearlyOff.State.POSSIBLE||ids.contains(","+id+":"+m.hour+",")||ids.contains(","+id.substring(0,Math.min(10,id.length()))+":"+m.hour+",");
                        if(want&&o.alignmentCloseups.containsKey(m.hour))javax.imageio.ImageIO.write(o.alignmentCloseups.get(m.hour).img,"png",dir.resolve(id+"_"+m.hour+".png").toFile());}}
                // 1.6.6: the whole-watch alignment classification of this photo (WatchClassification, app code)
                System.out.println("CLASS "+id+" "+WatchClassification.classify(c,model,o.clearlyOffError).verdict);
                w.flush();System.out.println("TRACK "+id);
            }
        }
    }
    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.4f",v):"";}
}
