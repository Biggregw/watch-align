package com.watchalign.mobile;

import android.graphics.Bitmap;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RESEARCH ONLY (read-only): every individual hour-marker reading exactly as the production app computes it.
 *
 *   GenuineMarkerDump manifest.csv image_root out.csv        (-Dwatchalign.model=<app model id>)
 *
 * The photo goes through the app's own route, unchanged: the app's decode rule (Alpha96Calib.loadAlpha96: halve while
 * >= 3200 px, cap 3200, EXIF upright), the model family exactly as the app validates and loads it (ModelRegistry.check
 * on the app's assets: an unvalidated family is refused with the app's reason) and UnifiedMarkerReview.run, the app's
 * single measurement entry point. Readings come from the production ClearlyOff markers (after the
 * owner-approved 12 o'clock top-edge calibration, or its legacy centre fallback, and after the production relative
 * position step). Nothing is recomputed, re-thresholded or summarised here.
 *
 * manifest.csv: photo_id, sha256, local_path (relative to image_root). The file's SHA-256 is re-checked before
 * measuring; a mismatch is written as an INTEGRITY row and never measured. One output row per marker (or one
 * photo-level row when the photo could not be analysed).
 */
public class GenuineMarkerDump {
    static final String HEAD = String.join(",",
            "photo_id","sha256","model","status","photo_reason","pose_ellipse_ratio","dial_radius_px","track_scale",
            "ring_turn_pct","ring_scale","bad_ticks","hour_line_found","hour","shape","source","state","credible",
            "withheld_reason","gate","uncertain","hand_confirmed",
            "side_pct","inout_pct","radial_from_top_edge","top_edge_raw_pct","top_edge_note","top_edge_ambiguous",
            "tilt_deg","tilt_note","gap_pct","gap_from_fit","pos_pct","relative_position_pct","relative_position_used",
            "se_pos_pct","se_rot_deg","cover","fit_rms_px","bad_frac",
            "side_limit_pct","inout_limit_pct","pos_limit_pct","tilt_limit_deg","gap_limit_pct",
            "replica_qc_state","strict_genuine_state","strict_side_limit_pct","strict_inout_limit_pct",
            "strict_pos_limit_pct","strict_tilt_limit_deg","strict_gap_limit_pct","strict_pos_pct",
            // production's own per-component decisions for THIS photo (its own limits incl. the resolution allowance)
            "qc_side_off","qc_inout_off","qc_pos_off","qc_tilt_off","qc_gap_off",
            "strict_side_off","strict_inout_off","strict_pos_off","strict_tilt_off","strict_gap_off");

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path manifest = Path.of(a[0]), root = Path.of(a[1]), out = Path.of(a[2]);
        String modelId = System.getProperty("watchalign.model", "");
        ModelRegistry.Check check = ModelRegistry.check(ModelSpec.directory(HarnessModel.assetsRoot()), modelId);
        if (!check.ok()) throw new IllegalStateException("model " + modelId + " is not a validated app family: " + check.problem);
        ModelRegistry.Family family = check.family;
        ModelSpec model = family.model;
        List<Map<String,String>> rows = AlignmentCalib.csv(Files.readAllLines(manifest));
        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out))) {
            w.println(HEAD);
            for (Map<String,String> r : rows) {
                String id = r.get("photo_id"), sha = r.getOrDefault("sha256", "").toLowerCase(Locale.ROOT);
                Path img = root.resolve(r.get("local_path")).normalize();
                if (!img.startsWith(root.normalize()) || !Files.isRegularFile(img)) { photoRow(w, id, sha, model, "MISSING", "image not found", null); continue; }
                String actual = sha256(Files.readAllBytes(img));
                if (!actual.equals(sha)) { photoRow(w, id, sha, model, "INTEGRITY", "file SHA-256 " + actual + " differs from the record", null); continue; }
                Bitmap b = Alpha96Calib.loadAlpha96(img.toString());
                if (b == null) { photoRow(w, id, sha, model, "UNREADABLE", "image could not be decoded", null); continue; }
                Alpha99Pipeline.Output o;
                try { o = UnifiedMarkerReview.run(b, family); }
                catch (Throwable t) { photoRow(w, id, sha, model, "ERROR", "analysis failed: " + t.getClass().getSimpleName(), null); continue; }
                if (o.pose == null || !o.pose.valid) { photoRow(w, id, sha, model, "NOPOSE", o.pose == null ? "no pose" : o.pose.reason, o); continue; }
                if (o.clearlyOff == null) { photoRow(w, id, sha, model, "NOMEASURE", o.clearlyOffError == null ? "marker check not run" : o.clearlyOffError, o); continue; }
                ClearlyOff.Result res = o.clearlyOff;
                String photoReason = o.alignment == null ? "alignment layer failed" : o.alignment.photoReason;
                for (ClearlyOff.Marker m : res.markers) w.println(markerRow(id, sha, model, photoReason, o.pose.ellipseRatio, res, m));
                System.out.println("MEASURED " + id + " markers=" + res.markers.size() + " rpx=" + f(res.rpx));
                w.flush();
            }
        }
    }

    /** the harness route used by the existing research drivers (TopEdge, AlignmentCalib): for the parity check */
    static AlignmentReference alignment(ModelSpec model) throws Exception {
        Path refFile = HarnessModel.assetsRoot().toPath().resolve("models/" + model.id + "/alignment/luminous_alignment.json");
        if (!Files.exists(refFile)) return null;
        AlignmentReference ref = AlignmentReference.parse(Files.readString(refFile));
        Path mf = refFile.resolveSibling("metal_alignment.properties");
        if (Files.exists(mf)) ref.metalExtra = MetalAlignment.parse(Files.readString(mf));
        return ref;
    }

    static String markerRow(String id, String sha, ModelSpec model, String photoReason, double ellipseRatio,
                            ClearlyOff.Result res, ClearlyOff.Marker m) {
        boolean credible = photoReason == null && WatchClassification.credible(m);
        String withheld = photoReason != null ? photoReason
                : !m.gate.isEmpty() || m.state == ClearlyOff.State.CANT_CHECK ? m.reason
                : !m.measured() ? "not measured" : "";
        ClearlyOff.Marker s = WatchClassification.strict(m, res.rpx);   // the frozen strict genuine standard, unchanged
        TriangleTopEdge.Result te = m.topEdge;
        List<String> v = new ArrayList<>(List.of(
                id, sha, model.id, "MEASURED", q(photoReason), f(ellipseRatio), f(res.rpx), f(res.trackScale),
                f(res.ringTurnPct), f(res.ringScale), "" + res.badTicks, b(res.lines.containsKey(m.hour % 12)),
                "" + m.hour, AlignmentReference.key(m.shape), q(m.source), m.state.name(), b(credible),
                q(withheld), q(String.join("; ", m.gate)), q(m.uncertain), b(m.handConfirmed),
                f(m.sidePct), f(m.inoutPct), b(m.radialFromTopEdge), f(te == null ? Double.NaN : te.radialPct),
                q(te == null ? "" : te.note), b(te != null && te.ambiguous()),
                f(m.tiltDeg), q(m.rotNote), f(m.gapPct), b(m.gapFromFit), f(m.posPct), f(m.relativePositionPct),
                b(m.relativePositionUsed), f(m.sePosPct), f(m.seRotDeg), f(m.cover), f(m.fitRmsPx), f(m.badFrac),
                f(m.sideLimitPct), f(m.inoutLimitPct), f(m.posLimitPct), f(m.tiltLimitDeg), f(m.gapLimitPct),
                m.state.name(), s.state.name(), f(s.sideLimitPct), f(s.inoutLimitPct), f(s.posLimitPct),
                f(s.tiltLimitDeg), f(s.gapLimitPct), f(s.posPct),
                b(m.sideOff), b(m.inoutOff), b(m.posOff), b(m.tiltOff), b(m.gapOff),
                b(s.sideOff), b(s.inoutOff), b(s.posOff), b(s.tiltOff), b(s.gapOff)));
        return String.join(",", v);
    }

    static void photoRow(PrintWriter w, String id, String sha, ModelSpec model, String status, String reason,
                         Alpha99Pipeline.Output o) {
        int cols = HEAD.split(",").length;
        List<String> v = new ArrayList<>(List.of(id, sha, model.id, status, q(reason),
                f(o == null || o.pose == null ? Double.NaN : o.pose.ellipseRatio)));
        while (v.size() < cols) v.add("");
        w.println(String.join(",", v));
        System.out.println(status + " " + id + " (" + reason + ")");
    }

    static String sha256(byte[] data) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder s = new StringBuilder();
        for (byte x : d) s.append(String.format("%02x", x));
        return s.toString();
    }
    static String q(String s) { return s == null ? "" : s.replace(",", ";").replace("\n", " ").replace("\"", "'"); }
    static String f(double v) { return Double.isFinite(v) ? String.format(Locale.US, "%.6f", v) : ""; }
    static String b(boolean v) { return v ? "1" : "0"; }
}
