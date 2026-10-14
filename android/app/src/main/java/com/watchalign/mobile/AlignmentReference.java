package com.watchalign.mobile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A model's genuine LUMINOUS alignment reference (assets/models/&lt;id&gt;/alignment/luminous_alignment.json), measured with
 * LuminousAlignment itself on sha256-verified genuine photos, one value per physical watch per marker
 * (tools/research/alignment_only/calibrate.py). Never the outer-metal reference, never a replica or QC photo.
 *
 *   shapes.&lt;triangle|baton|round&gt;: inset_R (outer-metal master -> luminous edge, genuine median), growth nominal / sd /
 *       limit (photographic edge-definition spread), genuine envelopes of radial / tangential (R) and rotation (deg)
 *       deviation from the per-hour genuine nominal, watch count, supported flag
 *   hours.&lt;h&gt;: genuine nominal radial_R, tangential_R, rotation_deg
 *
 * Missing file, shape or hour: that marker is "Cannot assess" (fail closed), never "No defect".
 */
final class AlignmentReference {
    static final String FILE="alignment/luminous_alignment.json";

    static final class ShapeRef {
        double insetR=Double.NaN,growthNominalR=0,growthSdR=Double.NaN,growthLimitR=Double.NaN,photoGrowthLimitR=Double.NaN;
        double radialLimitR=Double.NaN,tangentialLimitR=Double.NaN,rotationLimitDeg=Double.NaN;
        /** photographic repeatability (pooled within-watch SD over genuine watches photographed more than once); 0 if absent */
        double radialPhotoSdR=0,tangentialPhotoSdR=0,rotationPhotoSdDeg=0;
        int watches;boolean supported;String note="";
    }
    static final class HourRef{double radialR=Double.NaN,tangentialR=Double.NaN,rotationDeg=Double.NaN;int watches;}

    final String model;final String provenance;
    final Map<String,ShapeRef> shapes=new HashMap<>();
    final Map<Integer,HourRef> hours=new HashMap<>();
    /** outer-metal alignment allowances that Alpha107's reference lacks (MetalAlignment.FILE); null when absent */
    Map<String,Double> metalExtra;

    AlignmentReference(String model,String provenance){this.model=model;this.provenance=provenance;}

    static String key(ModelSpec.Shape s){return s==ModelSpec.Shape.TRIANGLE?"triangle":s==ModelSpec.Shape.BATON?"baton":"round";}
    ShapeRef shape(ModelSpec.Shape s){return shapes.get(key(s));}

    /** null when the model has no luminous alignment reference (every marker then cannot be assessed). */
    static AlignmentReference load(ModelSpec.Assets assets,ModelSpec model){
        AlignmentReference r;
        try{r=parse(ModelSpec.read(assets,model.dir+"/"+FILE));}catch(IOException|RuntimeException e){return null;}
        r.metalExtra=MetalAlignment.load(assets,model);
        return r;
    }

    @SuppressWarnings("unchecked")
    static AlignmentReference parse(String json){
        Map<String,Object> o=(Map<String,Object>)MiniJson.parse(json);
        AlignmentReference r=new AlignmentReference((String)o.get("model"),String.valueOf(o.get("provenance")));
        Map<String,Object> sh=(Map<String,Object>)o.get("shapes");
        if(sh!=null)for(Map.Entry<String,Object> e:sh.entrySet()){
            Map<String,Object> m=(Map<String,Object>)e.getValue();ShapeRef s=new ShapeRef();
            s.insetR=num(m,"inset_R");s.growthNominalR=num(m,"growth_nominal_R");s.growthSdR=num(m,"growth_sd_R");s.growthLimitR=num(m,"growth_limit_R");s.photoGrowthLimitR=num(m,"photo_growth_limit_R");
            s.radialLimitR=num(m,"radial_limit_R");
            s.radialPhotoSdR=zero(num(m,"radial_photo_sd_R"));s.tangentialPhotoSdR=zero(num(m,"tangential_photo_sd_R"));s.rotationPhotoSdDeg=zero(num(m,"rotation_photo_sd_deg"));s.tangentialLimitR=num(m,"tangential_limit_R");s.rotationLimitDeg=num(m,"rotation_limit_deg");
            s.watches=(int)Math.round(zero(num(m,"watches")));s.supported=Boolean.TRUE.equals(m.get("supported"));
            if(m.get("note")!=null)s.note=String.valueOf(m.get("note"));
            if(!Double.isFinite(s.growthNominalR))s.growthNominalR=0;
            r.shapes.put(e.getKey(),s);
        }
        Map<String,Object> hs=(Map<String,Object>)o.get("hours");
        if(hs!=null)for(Map.Entry<String,Object> e:hs.entrySet()){
            Map<String,Object> m=(Map<String,Object>)e.getValue();HourRef h=new HourRef();
            h.radialR=num(m,"radial_R");h.tangentialR=num(m,"tangential_R");h.rotationDeg=num(m,"rotation_deg");h.watches=(int)Math.round(zero(num(m,"watches")));
            r.hours.put(Integer.parseInt(e.getKey()),h);
        }
        return r;
    }

    /** Setup for fitting one marker of this shape; null when the inset is unknown. */
    LuminousAlignment.Setup setup(ModelSpec.Shape s){
        ShapeRef x=shape(s);if(x==null||!Double.isFinite(x.insetR))return null;
        LuminousAlignment.Setup u=new LuminousAlignment.Setup(x.insetR,Double.isFinite(x.growthSdR)?x.growthSdR:0);
        u.growthLimitR=x.growthLimitR;u.photoGrowthLimitR=x.photoGrowthLimitR;u.growthNominalR=x.growthNominalR;u.fixedGrowthR=x.growthNominalR;return u;
    }

    private static double num(Map<String,Object> m,String k){Object v=m.get(k);return v instanceof Double?(Double)v:Double.NaN;}
    private static double zero(double v){return Double.isFinite(v)?v:0;}
}
