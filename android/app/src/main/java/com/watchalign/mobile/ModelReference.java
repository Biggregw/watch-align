package com.watchalign.mobile;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * A model's genuine reference, loaded from assets/models/&lt;id&gt;/reference/ (exported from the research outputs by
 * tools/research/alpha96_calibration/export_model_reference.py; ModelReferenceTest keeps the copies identical):
 *
 *   genuine_reference.csv     feature,physical_watch_id,source,far,dial_radius_px   per genuine watch, how far it reads
 *                             from the genuine nominal (signed features) or its magnitude; one row per watch per feature
 *   nominal.properties        genuine nominals of the signed features
 *   triangle_nominal.properties, triangle_reference.csv   robust triangle (12) readout: nominal and per-watch
 *                             leave-one-watch-out lateral / centreline / sides
 *   uncertainty.properties    single-photo measurement uncertainty per family (photo-to-photo spread of genuine watches)
 *
 * Feature names are "&lt;marker key&gt;_rot", "&lt;marker key&gt;_off" (batons, e.g. six_rot), "rounds_off", "ring_rot",
 * "ring_shift", "date_tilt". A missing file or feature means "no genuine reference": the findings layer then reports the
 * feature as not assessed, never outside, so a half-added model cannot produce findings.
 */
final class ModelReference {
    static final String GENUINE="genuine_reference.csv",NOMINAL="nominal.properties",TRI_NOMINAL="triangle_nominal.properties",
            TRI_REFERENCE="triangle_reference.csv",UNCERTAINTY="uncertainty.properties";

    /** Robust triangle readout reference (Alpha97). */
    static final class Triangle {
        final int nWatches;
        final double nominalRadialR,nominalTangentialR,nominalLeftSideDeg,nominalRightSideDeg,nominalRotationDeg;
        final double[] lateralR,centrelineDeg,sidesDeg;
        /** Alpha105: per-watch signed radial offset from the leave-one-watch-out nominal (+ = outward, towards the minute
         *  track); null when the reference predates the radial_signed_R column (the 12's track distance is then not assessed). */
        double[] radialSignedR;
        Triangle(int n,double radial,double tangential,double left,double right,double rotation,double[] lateral,double[] centreline,double[] sides){
            nWatches=n;nominalRadialR=radial;nominalTangentialR=tangential;nominalLeftSideDeg=left;nominalRightSideDeg=right;
            nominalRotationDeg=rotation;lateralR=lateral;centrelineDeg=centreline;sidesDeg=sides;
        }
        /** Furthest genuine reading towards (outward = true) or away from the minute track, as a positive number; NaN
         *  without the signed column. */
        double radialMax(boolean outward){
            if(radialSignedR==null)return Double.NaN;double m=0;
            for(double v:radialSignedR)if(Double.isFinite(v))m=Math.max(m,outward?v:-v);
            return m;
        }
    }

    private final Map<String,double[]> far=new HashMap<>(),radius=new HashMap<>();
    private final Map<String,String[]> watch=new HashMap<>();
    /** optional per-row upper limit on the photo's dial radius (genuine_reference.csv max_photo_r; rows from shrunk genuine
     *  photos are only for photos below the full-resolution coverage); +inf when absent. */
    private final Map<String,double[]> maxPhoto=new HashMap<>();
    private final Map<String,Double> nominal=new HashMap<>(),sigma=new HashMap<>();
    private final Map<String,Integer> sigmaWatches=new HashMap<>();
    /** null when the model has no triangle reference. */
    final Triangle triangle;
    /** K in "clear when the excess exceeds K sigma"; NaN without an uncertainty file (nothing can then be clear). */
    final double kSigma;

    private ModelReference(Triangle t,double k){triangle=t;kSigma=k;}

    /** A reference with no genuine data at all (every feature not assessed). */
    static ModelReference empty(){return new ModelReference(null,Double.NaN);}

    static ModelReference load(ModelSpec.Assets assets,ModelSpec model){
        String dir=model.dir+"/"+model.referenceDir+"/";
        Properties unc=props(assets,dir+UNCERTAINTY);
        double k=unc==null?Double.NaN:d(unc.getProperty("k_sigma"));
        Triangle t=null;
        Properties tn=props(assets,dir+TRI_NOMINAL);
        List<Map<String,String>> tr=csv(assets,dir+TRI_REFERENCE);
        if(tn!=null&&tr!=null){
            double[] lat=new double[tr.size()],cen=new double[tr.size()],sid=new double[tr.size()];
            for(int i=0;i<tr.size();i++){lat[i]=d(tr.get(i).get("lateral_R"));cen[i]=d(tr.get(i).get("centreline_deg"));sid[i]=d(tr.get(i).get("sides_deg"));}
            t=new Triangle((int)Math.round(d(tn.getProperty("n_watches"))),d(tn.getProperty("radial_R")),d(tn.getProperty("tangential_R")),
                    d(tn.getProperty("left_side_deg")),d(tn.getProperty("right_side_deg")),d(tn.getProperty("rotation_deg")),lat,cen,sid);
            if(tr.get(0).containsKey("radial_signed_R")){double[] rs=new double[tr.size()];
                for(int i=0;i<tr.size();i++)rs[i]=d(tr.get(i).get("radial_signed_R"));t.radialSignedR=rs;}
        }
        ModelReference r=new ModelReference(t,k);
        List<Map<String,String>> g=csv(assets,dir+GENUINE);
        if(g!=null){
            Map<String,List<double[]>> by=new LinkedHashMap<>();Map<String,List<String>> ids=new HashMap<>();
            for(Map<String,String> row:g){
                double mp=d(row.get("max_photo_r"));
                by.computeIfAbsent(row.get("feature"),x->new ArrayList<>()).add(new double[]{d(row.get("far")),d(row.get("dial_radius_px")),Double.isNaN(mp)?Double.POSITIVE_INFINITY:mp});
                ids.computeIfAbsent(row.get("feature"),x->new ArrayList<>()).add(row.get("physical_watch_id"));
            }
            for(Map.Entry<String,List<double[]>> e:by.entrySet()){
                double[] f=new double[e.getValue().size()],R=new double[f.length],M=new double[f.length];
                for(int i=0;i<f.length;i++){f[i]=e.getValue().get(i)[0];R[i]=e.getValue().get(i)[1];M[i]=e.getValue().get(i)[2];}
                r.far.put(e.getKey(),f);r.radius.put(e.getKey(),R);r.maxPhoto.put(e.getKey(),M);r.watch.put(e.getKey(),ids.get(e.getKey()).toArray(new String[0]));
            }
        }
        Properties nom=props(assets,dir+NOMINAL);
        if(nom!=null)for(String key:nom.stringPropertyNames())r.nominal.put(key,d(nom.getProperty(key)));
        if(unc!=null)for(String key:unc.stringPropertyNames()){
            if(key.equals("k_sigma"))continue;
            if(key.endsWith(".watches"))r.sigmaWatches.put(key.substring(0,key.length()-8),(int)Math.round(d(unc.getProperty(key))));
            else r.sigma.put(key,d(unc.getProperty(key)));
        }
        return r;
    }

    /** Per-watch genuine "far" values of a feature (empty when the model has none). */
    double[] far(String feature){double[] v=far.get(feature);return v==null?new double[0]:v;}
    /** Dial radius (px) of each reference watch's photos, aligned with far(); NaN where not recorded. */
    double[] radius(String feature){double[] v=radius.get(feature);return v==null?new double[0]:v;}
    /** Number of distinct genuine watches with a reference row for the feature photographed at R_ref <= 1.3 x R (a watch
     *  can have rows at several resolutions, e.g. its photos re-measured shrunk; it still counts once). */
    int matchedWatches(String feature,double R){
        double[] rr=radius(feature);String[] id=watch.get(feature);java.util.Set<String> s=new java.util.HashSet<>();
        for(int i=0;i<rr.length;i++)if(matches(feature,i,R))s.add(id==null||id[i]==null?"#"+i:id[i]);
        return s.size();
    }
    /** Furthest genuine reading among the reference rows photographed at R_ref <= 1.3 x R (and usable at this R). */
    double matchedMax(String feature,double R){
        double[] f=far(feature);double m=Double.NEGATIVE_INFINITY;
        for(int i=0;i<f.length;i++)if(matches(feature,i,R))m=Math.max(m,f[i]);
        return m;
    }
    /** True when rows from shrunk genuine photos (finite max_photo_r) are used at this photo's R: the photo is below the
     *  feature's full-resolution coverage. */
    boolean usesShrunkRows(String feature,double R){
        double[] mp=maxPhoto.get(feature);if(mp==null)return false;
        for(int i=0;i<mp.length;i++)if(Double.isFinite(mp[i])&&matches(feature,i,R))return true;
        return false;
    }
    private boolean matches(String feature,int i,double R){
        double rr=radius(feature)[i];double[] mp=maxPhoto.get(feature);
        return Double.isFinite(rr)&&rr<=Alpha98Findings.RES_MATCH*R&&(mp==null||R<=mp[i]);
    }
    boolean has(String feature){return far.containsKey(feature)&&far.get(feature).length>0;}
    /** Genuine nominal of a signed feature, NaN when missing. */
    double nominal(String feature){Double v=nominal.get(feature);return v==null?Double.NaN:v;}
    /** Single-photo uncertainty of a family in a unit ("deg", "degR", "R", "px"), NaN when missing. */
    double sigma(String family,String unit){Double v=sigma.get(family+"."+unit);return v==null?Double.NaN:v;}
    /** A genuine-derived limit (uncertainty.properties "<name>.limit"), NaN when the model has none. */
    double limit(String name){return sigma(name,"limit");}
    int sigmaWatches(String family,String unit){Integer v=sigmaWatches.get(family+"."+unit);return v==null?0:v;}

    // ------------------------------------------------------------------ file helpers (missing file -> null)
    private static Properties props(ModelSpec.Assets a,String path){
        try{Properties p=new Properties();p.load(new StringReader(ModelSpec.read(a,path)));return p;}catch(IOException e){return null;}
    }
    private static List<Map<String,String>> csv(ModelSpec.Assets a,String path){
        String text;try{text=ModelSpec.read(a,path);}catch(IOException e){return null;}
        String[] lines=text.split("\r?\n");List<Map<String,String>> out=new ArrayList<>();
        if(lines.length==0)return out;
        String[] h=lines[0].split(",",-1);
        for(int i=1;i<lines.length;i++){if(lines[i].isEmpty())continue;String[] f=lines[i].split(",",-1);Map<String,String> m=new HashMap<>();
            for(int k=0;k<h.length&&k<f.length;k++)m.put(h[k],f[k]);out.add(m);}
        return out;
    }
    private static double d(String s){return s==null||s.isEmpty()?Double.NaN:Double.parseDouble(s.trim());}
}
