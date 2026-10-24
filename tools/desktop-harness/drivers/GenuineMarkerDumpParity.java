package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Parity proof for GenuineMarkerDump (research): for every measured photo of a dump, the same photo is measured again
 * (1) through the established harness route (HarnessModel spec/reference + parsed luminous alignment reference +
 * Alpha99Pipeline.run, as TopEdge and AlignmentCalib run it) and (2) a second time through the production entry point,
 * and every reading in the dump must equal both exactly (state, strict-genuine state, side, in/out, top edge, tilt,
 * gap, position, relative position). Exit status 1 on any difference.
 *
 *   GenuineMarkerDumpParity manifest.csv image_root dump.csv        (-Dwatchalign.model=<app model id>)
 */
public class GenuineMarkerDumpParity {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path root = Path.of(a[1]);
        String modelId = System.getProperty("watchalign.model", "");
        ModelRegistry.Family family = ModelRegistry.check(ModelSpec.directory(HarnessModel.assetsRoot()), modelId).family;
        ModelSpec spec = HarnessModel.spec();
        ModelReference ref = HarnessModel.ref();
        AlignmentReference align = GenuineMarkerDump.alignment(spec);
        List<Map<String,String>> dump = AlignmentCalib.csv(Files.readAllLines(Path.of(a[2])));
        int compared = 0, diffs = 0;
        for (Map<String,String> r : AlignmentCalib.csv(Files.readAllLines(Path.of(a[0])))) {
            String id = r.get("photo_id");
            List<Map<String,String>> rows = dump.stream().filter(x -> id.equals(x.get("photo_id")) && "MEASURED".equals(x.get("status"))).toList();
            if (rows.isEmpty()) continue;
            Bitmap b = Alpha96Calib.loadAlpha96(root.resolve(r.get("local_path")).toString());
            Alpha99Pipeline.Output harness = Alpha99Pipeline.run(b, spec, ref, align, null);
            Alpha99Pipeline.Output again = UnifiedMarkerReview.run(b, family);
            for (Map<String,String> row : rows) {
                int hour = Integer.parseInt(row.get("hour"));
                for (Alpha99Pipeline.Output o : new Alpha99Pipeline.Output[]{harness, again}) {
                    String reason = o.alignment == null ? "alignment layer failed" : o.alignment.photoReason;
                    String expected = GenuineMarkerDump.markerRow(id, row.get("sha256"), spec, reason,
                            o.pose.ellipseRatio, o.clearlyOff, o.clearlyOff.at(hour));
                    String got = String.join(",", row.values());
                    compared++;
                    if (!expected.equals(got)) {
                        diffs++;
                        System.out.println("PARITY DIFF " + id + " hour " + hour + "\n  dump:     " + got + "\n  expected: " + expected);
                    }
                }
            }
        }
        System.out.println("PARITY compared=" + compared + " differences=" + diffs);
        if (compared == 0 || diffs > 0) System.exit(1);
    }

}
