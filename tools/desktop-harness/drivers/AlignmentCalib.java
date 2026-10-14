package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Watch Align alignment-only calibration / validation driver (desktop, the app's own code via Alpha99Pipeline).
 *
 *   AlignmentCalib fit    manifest.csv image_root out.csv setups.json       per-marker raw fits with explicit setups
 *   AlignmentCalib decide manifest.csv image_root out.csv reference.json [render_dir id,id,...]
 *                                                                           the app's decision with a reference JSON
 * manifest.csv needs photo_id, local_path (relative to image_root); physical_watch_id is copied when present.
 * setups.json: {"triangle":{"inset_R":..,"fixed_growth_R":..,"growth_nominal_R":..},"baton":{..},"round":{..}}
 * Model: -Dwatchalign.model=<id>. Images are only read; nothing is written next to them.
 */
public class AlignmentCalib {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        String mode=a[0];Path manifest=Path.of(a[1]),root=Path.of(a[2]),out=Path.of(a[3]);
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        AlignmentReference ref=null;AlignmentAnalysis.Setups setups;
        if(mode.equals("fit")){Map<String,double[]> su=setups(Files.readString(Path.of(a[4])));
            setups=s->{double[] v=su.get(AlignmentReference.key(s));if(v==null)return null;
                LuminousAlignment.Setup x=new LuminousAlignment.Setup(v[0],0.003);x.fixedGrowthR=v[1];x.growthNominalR=v[2];x.rejectOutward=v[3]>0;return x;};}
        else{ref=AlignmentReference.parse(Files.readString(Path.of(a[4])));setups=ref::setup;}
        Path renderDir=a.length>5?Path.of(a[5]):null;
        Set<String> render=new HashSet<>();if(a.length>6)for(String x:a[6].split(","))render.add(x.trim());
        if(renderDir!=null)Files.createDirectories(renderDir);
        List<Map<String,String>> rows=csv(Files.readAllLines(manifest));
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
            w.println("photo_id,physical_watch_id,model,hour,shape,dial_r_px,outcome,reason,radial_R,tangential_R,rotation_deg,se_radial_R,se_tangential_R,se_rotation_deg,"
                    +"radial_ok,tangential_ok,rotation_ok,growth_R,growth_free,se_growth_R,photo_growth_R,photo_growth_diff_R,merged_share,inferred,noise_px,samples,"
                    +"lume,dial,round_cover,primary,primary_excess_R,secondary,fit_assessed,half_len_R,check");
            for(Map<String,String> r:rows){
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));
                if(!Files.exists(img)){System.out.println("SKIP "+id+" (no image)");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());
                Alpha99Pipeline.Output o;
                try{o=b==null?null:Alpha99Pipeline.run(b,model,metal,ref,setups);}catch(Throwable t){o=null;}
                if(o==null||!o.ok()||o.alignment==null){System.out.println("NOPOSE "+id);w.println(id+","+r.getOrDefault("physical_watch_id","")+","+model.id+",,,,NOPOSE,,,,,,,,,,,,,,,,,,,,,,,,,,,,");continue;}
                int d=0,n=0,c=0;
                for(AlignmentDecision.Marker m:o.alignment.markers){
                    LuminousAlignment.Result f=m.fit;
                    if(m.outcome==AlignmentDecision.Outcome.DEFECT)d++;else if(m.outcome==AlignmentDecision.Outcome.NO_DEFECT)n++;else c++;
                    String reason=(m.outcome==AlignmentDecision.Outcome.CANNOT_ASSESS?m.reason:f!=null&&!f.assessed?f.reason:"").replace(",",";").replace("\n"," ");
                    w.println(String.join(",",id,r.getOrDefault("physical_watch_id",""),model.id,""+m.hour,AlignmentReference.key(m.shape),
                            f(o.measurement.dialRadiusPx),m.outcome.name(),reason,
                            f(f==null?Double.NaN:f.radialR),f(f==null?Double.NaN:f.tangentialR),f(f==null?Double.NaN:f.rotationDeg),
                            f(f==null?Double.NaN:f.seRadialR),f(f==null?Double.NaN:f.seTangentialR),f(f==null?Double.NaN:f.seRotationDeg),
                            b(f!=null&&f.radialOk),b(f!=null&&f.tangentialOk),b(f!=null&&f.rotationOk),
                            f(f==null?Double.NaN:f.growthR),b(f!=null&&f.growthFree),f(f==null?Double.NaN:f.seGrowthR),
                            f(m.photoGrowthR),f(f==null?Double.NaN:f.photoGrowthDiffR),f(f==null?Double.NaN:f.mergedShare),b(f!=null&&f.inferred),
                            f(f==null?Double.NaN:f.noisePx),""+(f==null?0:f.samples),f(f==null?Double.NaN:f.lume),f(f==null?Double.NaN:f.dial),
                            f(f==null?Double.NaN:f.roundCover),
                            m.primary==null?"":m.primary.param.name()+":"+m.primary.headline().replace(",",";"),m.primary==null?"":f(m.primary.excessR),
                            secondary(m),b(f!=null&&f.assessed),f(f==null?Double.NaN:f.halfLengthR()),
                            o.checks==null||o.checks.get(m.hour)==null?"unchecked":o.checks.get(m.hour).clean?"clean":o.checks.get(m.hour).reason.replace(",",";")));
                    if(renderDir!=null&&(render.contains(id)||render.contains("ALL"))&&o.alignmentCloseups.containsKey(m.hour))
                        javax.imageio.ImageIO.write(o.alignmentCloseups.get(m.hour).img,"png",renderDir.resolve(id+"_"+m.hour+".png").toFile());
                }
                if(renderDir!=null&&(render.contains(id)||render.contains("ALL"))){
                    Files.writeString(renderDir.resolve(id+"_technical.txt"),o.alignmentTechnical==null?"":o.alignmentTechnical);
                }
                System.out.printf(Locale.US,"PHOTO %s R=%.0f defect=%d nodefect=%d cannot=%d%s%n",id,o.measurement.dialRadiusPx,d,n,c,
                        o.alignment.photoReason==null?"":" ("+o.alignment.photoReason+")");
                w.flush();
            }
        }
    }
    static String secondary(AlignmentDecision.Marker m){StringBuilder s=new StringBuilder();for(AlignmentDecision.Issue i:m.secondary)s.append(s.length()==0?"":"|").append(i.param.name());return s.toString();}
    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.6g",v):"";}
    static String b(boolean v){return v?"1":"0";}

    @SuppressWarnings("unchecked")
    static Map<String,double[]> setups(String json){
        Map<String,Object> o=(Map<String,Object>)MiniJson.parse(json);Map<String,double[]> out=new LinkedHashMap<>();
        for(Map.Entry<String,Object> e:o.entrySet()){Map<String,Object> m=(Map<String,Object>)e.getValue();
            out.put(e.getKey(),new double[]{(Double)m.get("inset_R"),(Double)m.get("fixed_growth_R"),(Double)m.get("growth_nominal_R"),m.get("reject")==null?1:(Double)m.get("reject")});}
        return out;
    }

    /** Minimal CSV (quoted fields allowed). */
    static List<Map<String,String>> csv(List<String> lines){
        List<Map<String,String>> out=new ArrayList<>();if(lines.isEmpty())return out;
        List<String> head=split(lines.get(0));
        for(int i=1;i<lines.size();i++){if(lines.get(i).isBlank())continue;List<String> v=split(lines.get(i));Map<String,String> m=new LinkedHashMap<>();
            for(int k=0;k<head.size();k++)m.put(head.get(k),k<v.size()?v.get(k):"");out.add(m);}
        return out;
    }
    static List<String> split(String l){
        List<String> o=new ArrayList<>();StringBuilder b=new StringBuilder();boolean q=false;
        for(int i=0;i<l.length();i++){char c=l.charAt(i);if(c=='"'){if(q&&i+1<l.length()&&l.charAt(i+1)=='"'){b.append('"');i++;}else q=!q;}
            else if(c==','&&!q){o.add(b.toString());b.setLength(0);}else b.append(c);}
        o.add(b.toString());return o;
    }
}
