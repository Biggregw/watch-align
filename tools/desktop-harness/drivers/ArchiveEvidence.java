package com.watchalign.mobile;
import android.graphics.Bitmap;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
/**
 * Read-only image-archive evidence (tools/image_archive): what the production 1.6.x pipeline (Alpha99Pipeline, the same
 * route as TrackDump) already finds on a photo, as one JSON line per photo:
 *
 *   ArchiveEvidence manifest.csv image_root out.jsonl
 *
 * The dial pose (AutomaticDialOverlay: dial-edge ellipse, minute-tick lattice, canonical-to-image homography, whether
 * the hour markers confirmed the 12 direction) and, per marker, the clearly-off state, gate, uncertainty and whether
 * its numbers are finite, with the app's own WatchClassification.reliable() / credible() (owner rules, 21-22 Oct 2026).
 * Phase B (22 Oct 2026): per marker also the recommended two-level measurement (MarkerGeometry: the outer-metal centre
 * against the ring of the other eligible markers, mg_*) and the ring's placement on the dial (mg_ring).
 * Nothing is decided here and no QC threshold or measurement is changed.
 */
public class ArchiveEvidence {
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
            for(Map<String,String> r:rows){
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));
                StringBuilder j=new StringBuilder("{");kv(j,"photo_id",id);kv(j,"model",model.id);
                if(!Files.exists(img)){kv(j,"error","missing");w.println(j.append("}"));continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());
                if(b==null){kv(j,"error","unreadable");w.println(j.append("}"));continue;}
                num(j,"work_w",b.getWidth());num(j,"work_h",b.getHeight());
                Alpha99Pipeline.Output o;String err="";
                try{o=Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);}catch(Throwable t){o=null;err=t.getClass().getSimpleName();}
                if(!err.isEmpty())kv(j,"error",err);
                AutomaticDialOverlay.Result p=o==null?null:o.pose;
                if(p!=null){
                    bool(j,"pose_valid",p.valid);kv(j,"pose_reason",p.reason==null?"":p.reason);
                    num(j,"dial_cx",p.dialCx);num(j,"dial_cy",p.dialCy);num(j,"dial_radius",p.dialRadius);
                    num(j,"edge_ellipse_ratio",p.ellipseRatio);num(j,"edge_rms_px",p.edgeRms);num(j,"tick_rms_px",p.fitAfter);
                    num(j,"detected_ticks",p.detectedTicks);num(j,"complete_pairs",p.completePairs);
                    bool(j,"projective_accepted",p.projectiveAccepted);num(j,"marker_branch_turn",p.markerBranchTurn);
                    if(p.homography!=null){j.append("\"homography\":[");for(int i=0;i<9;i++)j.append(i>0?",":"").append(f(p.homography[i]));j.append("],");}
                }
                if(o!=null&&o.clearlyOffError!=null)kv(j,"clearly_off_error",o.clearlyOffError);
                if(o!=null&&o.alignment!=null&&o.alignment.photoReason!=null)kv(j,"photo_reason",o.alignment.photoReason);
                ClearlyOff.Result c=o==null?null:o.clearlyOff;
                // Phase B: the recommended two-level measurement on the same analysis (research reading; decides nothing)
                MarkerGeometry.Result g=null;
                if(c!=null&&p!=null&&p.homography!=null)try{g=MarkerGeometry.fromPipeline(c,o.measurement,model,metal,p.homography);}catch(Throwable t){g=null;}
                if(g!=null&&g.ring.ok){
                    j.append("\"mg_ring\":{");num(j,"place_x_pct",g.ring.placeXPct);num(j,"place_y_pct",g.ring.placeYPct);
                    num(j,"rot_deg",g.ring.rotDeg);num(j,"scale_pct",g.ring.scalePct);num(j,"n",g.ring.n);
                    j.append("\"markers\":[");for(int i=0;i<g.ring.hours.length;i++)j.append(i>0?",":"").append(g.ring.hours[i]);j.append("],");
                    trim(j);j.append("},");
                }
                if(c!=null){
                    num(j,"rpx",c.rpx);num(j,"bad_ticks",c.badTicks);num(j,"ring_turn_pct",c.ringTurnPct);num(j,"track_scale",c.trackScale);num(j,"hour_lines",c.lines.size());
                    j.append("\"markers\":[");boolean first=true;
                    for(ClearlyOff.Marker m:c.markers){
                        j.append(first?"":",").append('{');first=false;
                        num(j,"hour",m.hour);kv(j,"shape",AlignmentReference.key(m.shape));kv(j,"state",m.state.name());
                        kv(j,"gate",String.join("; ",m.gate));kv(j,"uncertain",m.uncertain);bool(j,"hand_confirmed",m.handConfirmed);
                        bool(j,"finite",Double.isFinite(m.sidePct)&&Double.isFinite(m.inoutPct)&&Double.isFinite(m.tiltDeg));
                        bool(j,"reliable",WatchClassification.reliable(m));bool(j,"credible",WatchClassification.credible(m));
                        num(j,"side_pct",m.sidePct);num(j,"inout_pct",m.inoutPct);num(j,"tilt_deg",m.tiltDeg);num(j,"pos_pct",m.posPct);num(j,"gap_pct",m.gapPct);
                        num(j,"fit_rms_px",m.fitRmsPx);num(j,"cover",m.cover);num(j,"bad_frac",m.badFrac);kv(j,"source",m.source==null?"":m.source);
                        kv(j,"reason",m.reason);
                        MarkerGeometry.Marker mg=g==null?null:g.at(m.hour);
                        if(mg!=null){num(j,"mg_tang_pct",mg.tangPct);num(j,"mg_rad_pct",mg.radPct);num(j,"mg_tilt_deg",mg.tiltDeg);bool(j,"mg_eligible",mg.eligible);}
                        trim(j);j.append('}');
                    }
                    j.append("],");
                }
                trim(j);w.println(j.append("}"));w.flush();System.out.println("EVIDENCE "+id);
            }
        }
    }
    static void trim(StringBuilder j){if(j.charAt(j.length()-1)==',')j.setLength(j.length()-1);}
    static void kv(StringBuilder j,String k,String v){j.append('"').append(k).append("\":\"").append(v.replace("\\","\\\\").replace("\"","\\\"")).append("\",");}
    static void num(StringBuilder j,String k,double v){j.append('"').append(k).append("\":").append(Double.isFinite(v)?f(v):"null").append(',');}
    static void bool(StringBuilder j,String k,boolean v){j.append('"').append(k).append("\":").append(v).append(',');}
    static String f(double v){return String.format(Locale.US,"%.6g",v);}
}
