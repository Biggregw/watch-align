package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * RESEARCH ONLY: the app's side-by-side research comparison (ResearchComparison) for a list of photos, exactly as the
 * results screen shows it when the research switch is on: the existing result (WatchClassification on the unchanged
 * analysis) and, per marker and for the ring, the new MarkerGeometry reading against the two research candidates.
 *
 *   ResearchCompare items.csv (photo_id,path)     prints one CMP block per photo
 */
public class ResearchCompare {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        MarkerGeometry.ENABLED=true;
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=Files.exists(refFile)?AlignmentReference.parse(Files.readString(refFile)):null;
        if(ref!=null){Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));}
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(Path.of(a[0])));
        for(Map<String,String> r:rows){
            String id=r.get("photo_id"),p=r.get("path");
            System.out.println("CMP-BEGIN "+id);
            try{
                Bitmap b=Alpha96Calib.loadAlpha96(p);
                Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);
                if(o==null||o.clearlyOff==null){System.out.println("no analysis");System.out.println("CMP-END");continue;}
                WatchClassification.Result cls=WatchClassification.classify(o.clearlyOff,model,o.clearlyOffError,o.alignment==null?null:o.alignment.photoReason);
                System.out.println("existing result: "+cls.title());
                System.out.println(ResearchComparison.text(cls,o.geometry));
            }catch(Throwable t){System.out.println("error "+t.getClass().getSimpleName());}
            System.out.println("CMP-END");
        }
    }
}
