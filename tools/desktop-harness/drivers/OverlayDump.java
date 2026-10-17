package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Overlay-gap prototype (research only): exports what the app already places on each photo, so the prototype measures
 * against exactly the overlay the owner sees.
 *
 *   OverlayDump manifest.csv image_root out.jsonl
 *
 * One JSON line per photo: analysis bitmap size, dial pose H (canonical -> bitmap px, row-major 3x3), dial radius px and,
 * per marker, the yellow outer-metal reference placed by the OTHER markers (canonical), the hand/glare check and the
 * app's current outcome. Nothing here decides anything.
 */
public class OverlayDump {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path manifest=Path.of(a[0]),root=Path.of(a[1]),out=Path.of(a[2]);
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=AlignmentReference.parse(Files.readString(refFile));
        Path metalFile=refFile.resolveSibling("metal_alignment.properties");
        if(Files.exists(metalFile))ref.metalExtra=MetalAlignment.parse(Files.readString(metalFile));
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(manifest));
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
            for(Map<String,String> r:rows){
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));
                if(!Files.exists(img))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());
                Alpha99Pipeline.Output o;
                try{o=b==null?null:Alpha99Pipeline.run(b,model,metal,ref,ref::setup);}catch(Throwable t){o=null;}
                StringBuilder s=new StringBuilder();
                s.append("{\"photo_id\":").append(q(id)).append(",\"watch\":").append(q(r.getOrDefault("physical_watch_id","")))
                 .append(",\"path\":").append(q(r.get("local_path"))).append(",\"model\":").append(q(model.id));
                if(b!=null)s.append(",\"w\":").append(b.getWidth()).append(",\"h\":").append(b.getHeight());
                if(o==null||!o.ok()||o.alignment==null){s.append(",\"pose\":false}");w.println(s);continue;}
                double[] H=o.pose.homography;
                s.append(",\"pose\":true,\"H\":").append(arr(H)).append(",\"rpx\":").append(f(o.measurement.dialRadiusPx)).append(",\"markers\":[");
                boolean first=true;
                for(AlignmentDecision.Marker m:o.alignment.markers){
                    Alpha99MarkerInterference.Check c=o.checks==null?null:o.checks.get(m.hour);
                    if(!first)s.append(',');first=false;
                    s.append("{\"hour\":").append(m.hour).append(",\"shape\":").append(q(AlignmentReference.key(m.shape)))
                     .append(",\"outcome\":").append(q(m.outcome.name()))
                     .append(",\"reason\":").append(q(m.outcome==AlignmentDecision.Outcome.CANNOT_ASSESS?m.reason:m.fit!=null&&!m.fit.assessed?m.fit.reason:""))
                     .append(",\"primary\":").append(q(m.primary==null?"":m.primary.headline()))
                     .append(",\"excess_R\":").append(m.primary==null?"null":f(m.primary.excessR))
                     .append(",\"hand_clean\":").append(c!=null&&c.clean)
                     .append(",\"hand_reason\":").append(q(c==null?"unchecked":c.reason))
                     .append(",\"yellow\":[");
                    if(m.yellow!=null)for(int i=0;i<m.yellow.length;i++){if(i>0)s.append(',');s.append(arr(m.yellow[i]));}
                    s.append("]}");
                }
                s.append("]}");w.println(s);w.flush();
                System.out.println("DUMP "+id);
            }
        }
    }
    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.8g",v):"null";}
    static String arr(double[] v){StringBuilder s=new StringBuilder("[");for(int i=0;i<v.length;i++){if(i>0)s.append(',');s.append(f(v[i]));}return s.append(']').toString();}
    static String q(String v){if(v==null)v="";return "\""+v.replace("\\","\\\\").replace("\"","\\\"").replace("\n"," ")+"\"";}
}
