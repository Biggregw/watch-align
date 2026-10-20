package com.watchalign.mobile;
import java.nio.file.*;import java.util.*;
/** The same photo measured with one family's configuration: every marker's reading. Research only. Cross image id */
public class Cross {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=AlignmentReference.parse(Files.readString(refFile));
        Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));
        Alpha99Pipeline.Output o=Alpha99Pipeline.run(Alpha96Calib.loadAlpha96(a[0]),model,metal,ref,ref::setup);
        if(o.clearlyOff==null){System.out.println("CROSS,"+a[1]+","+model.id+",NO_RESULT");return;}
        for(ClearlyOff.Marker m:o.clearlyOff.markers)
            System.out.println(String.format(Locale.US,"CROSS,%s,%s,%.2f,%d,%s,%s,%.4f,%.4f,%.4f,%.4f,%s",a[1],model.id,o.measurement.dialRadiusPx,m.hour,AlignmentReference.key(m.shape),
                m.state.name(),m.sidePct,m.inoutPct,m.tiltDeg,m.gapPct,m.uncertain.isEmpty()?"clean":"uncertain"));
    }
}
