package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

/** Research-only: exact production input, traced copy of the numerical fit, mandatory parity and coverage gates. */
public final class MarkerAudit {
    static Map<String,Object> map(Object... kv) {
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],kv[i+1]);
        return m;
    }
    static String json(Object o) {
        if(o==null)return "null";
        if(o instanceof Number)return Double.isFinite(((Number)o).doubleValue())?o.toString():"null";
        if(o instanceof Boolean)return o.toString();
        List<String> parts=new ArrayList<>();
        if(o instanceof Map){for(Object e0:((Map<?,?>)o).entrySet()){
            Map.Entry<?,?> e=(Map.Entry<?,?>)e0;parts.add(json(e.getKey().toString())+":"+json(e.getValue()));}
            return "{"+String.join(",",parts)+"}";}
        if(o instanceof Iterable){for(Object v:(Iterable<?>)o)parts.add(json(v));return "["+String.join(",",parts)+"]";}
        if(o.getClass().isArray()){for(int i=0;i<java.lang.reflect.Array.getLength(o);i++)parts.add(json(java.lang.reflect.Array.get(o,i)));return "["+String.join(",",parts)+"]";}
        return "\""+o.toString().replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t")+"\"";
    }
    static void equal(double a,double b,String label) {
        if(Double.isNaN(a)&&Double.isNaN(b))return;
        if(!Double.isFinite(a)||!Double.isFinite(b)||Math.abs(a-b)>1e-10)
            throw new IllegalStateException("Trace differs from production: "+label+" "+a+" / "+b);
    }
    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path manifest=Path.of(args[0]),root=Path.of(args[1]),out=Path.of(args[2]);Files.createDirectories(out);
        ModelSpec model=HarnessModel.spec();
        Path rp=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        // No fallback: missing selected-family references are fatal.
        AlignmentReference ref=AlignmentReference.parse(Files.readString(rp));
        ref.metalExtra=MetalAlignment.parse(Files.readString(rp.resolveSibling("metal_alignment.properties")));
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(manifest));
        List<Object> coverage=new ArrayList<>();Set<String> ids=new HashSet<>();int done=0;
        for(Map<String,String> row:rows){
            String id=row.get("photo_id");if(!ids.add(id))throw new IllegalStateException("Duplicate photo "+id);
            Path path=root.resolve(row.get("local_path"));
            if(!Files.isRegularFile(path)){coverage.add(map("photo_id",id,"status","missing"));continue;}
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
            if(!sha.equals(row.get("sha256"))){coverage.add(map("photo_id",id,"status","hash_mismatch","got",sha));continue;}
            Bitmap b=Alpha96Calib.loadAlpha96(path.toString());
            Alpha99Pipeline.Output o=Alpha99Pipeline.run(b,model,HarnessModel.ref(),ref,ref::setup);
            if(o.clearlyOff==null||o.alignment==null){coverage.add(map("photo_id",id,"status","no_analysis"));continue;}
            Mat rgba=new Mat(),gray=new Mat(),flt=new Mat();
            try {
                Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);gray.convertTo(flt,CvType.CV_32F);
                ClearlyOff.Img im=new ClearlyOff.Img(flt,o.pose.homography);
                List<ClearlyOff.Input> inputs=Alpha99Pipeline.clearlyOffInputs(o.alignment,o.checks);
                ClearlyOff.Ctx ctx=ClearlyOff.context(im,o.clearlyOff.rpx,inputs);
                TracedMetalFit.Fit[] fits=TracedMetalFit.fitAll(im,inputs,o.clearlyOff.rpx,ctx.hands);
                List<Object> markers=new ArrayList<>();
                for(int i=0;i<inputs.size();i++) {
                    ClearlyOff.Input in=inputs.get(i);ClearlyOff.Marker m=o.clearlyOff.at(in.hour);
                    TracedMetalFit.Fit f=fits[i];
                    if(f!=null&&f.cObs!=null){equal(f.tiltDeg,m.fitTiltDeg,id+":"+in.hour+" tilt");equal(f.cover,m.cover,"cover");
                        for(int k=0;k<f.poly.length;k++)for(int j=0;j<2;j++)equal(f.poly[k][j],m.fitPoly[k][j],"polygon");}
                    Alpha99MarkerInterference.Check c=o.checks.get(in.hour);
                    Map<String,Object> marker=map("hour",in.hour,"shape",in.shape,"state",m.state,
                        "side_pct",m.sidePct,"inout_pct",m.inoutPct,"tilt_deg",m.tiltDeg,"fit_tilt_deg",m.fitTiltDeg,
                        "gap_pct",m.gapPct,"gap_off",m.gapOff,"side_off",m.sideOff,"inout_off",m.inoutOff,"tilt_off",m.tiltOff,
                        "tick59",m.tick59,"tick01",m.tick01,"reference",in.yellow,"gate",m.gate,"uncertain",m.uncertain,
                        "alpha99",map("clean",c.clean,"reason",c.reason,"seconds",c.secondsHand,"seconds_psi",c.secondsPsi,
                            "touch_gap_px",c.touchGapPx,"touch_span",c.touchSpanR,"face_span",c.acrossSpanR,"foreign_fraction",c.foreignFrac));
                    if(f!=null){
                        marker.put("fit",map("polygon",f.poly,"centre",f.cObs,"tilt",f.tiltDeg,"cover",f.cover,"rms_px",f.rmsPx,
                            "bad_fraction",f.badFrac,"se_pos",f.sePos,"se_rot",f.seRot,"note",f.note,
                            "shape_bias",TracedMetalFit.lastBias.get(in.shape)));
                        List<Object> steps=new ArrayList<>();
                        for(TracedMetalFit.Step s:f.steps)steps.add(map("polygon",s.poly,"points",s.edges.o.pts,"normals",s.edges.o.nrm,
                            "side",s.edges.o.side,"offset",s.edges.off,"why",s.edges.why,"jacobian",s.jacobian,
                            "indices",s.indices,"residual",s.residual,"weights",s.weights,"solve_weights",s.solveWeights,"delta",s.delta));
                        marker.put("iterations",steps);
                    }
                    markers.add(marker);
                }
                Files.writeString(out.resolve(id+".json"),json(map("photo_id",id,"sha256",sha,"model",model.id,
                    "homography",o.pose.homography,"rpx",o.clearlyOff.rpx,"metal_hands",ctx.hands,
                    "hour_lines",o.clearlyOff.lines,"markers",markers)));
                ImageIO.write(b.img,"png",out.resolve(id+"_analysis.png").toFile());
                coverage.add(map("photo_id",id,"status","verified_traced","markers",markers.size()));done++;
                System.out.println("TRACED "+id);
            } finally {rgba.release();gray.release();flt.release();}
        }
        Files.writeString(out.resolve("coverage.json"),json(map("expected",rows.size(),"traced",done,"photos",coverage)));
        if(done!=rows.size()||done==0)throw new IllegalStateException("Incomplete verified audit: "+done+" / "+rows.size());
    }
}
