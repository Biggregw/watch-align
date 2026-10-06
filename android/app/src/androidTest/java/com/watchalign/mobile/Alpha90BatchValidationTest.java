package com.watchalign.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Batch validation harness for the exact frozen Alpha90 overlay path.
 *
 * IMPORTANT: this class is test-only. It calls AutomaticDialOverlay.build(Bitmap) directly.
 * It does not alter pose, thresholds, master geometry, rendering, or rejection behaviour.
 * The output is evidence for human review, not an automated RL/GL authenticity verdict.
 */
@RunWith(AndroidJUnit4.class)
public class Alpha90BatchValidationTest {
    private static final String TAG = "WA_A90_BATCH";
    private static final String ASSET_DIR = "alpha90_validation";

    private static final class Meta {
        String caseId = "";
        String qcClass = "";
        String threadId = "";
        String target = "";
        String notes = "";
        String asset = "";
        String sourceUrl = "";
        String fetchStatus = "";
    }

    @BeforeClass public static void initOpenCv() {
        assertTrue("OpenCV failed to initialise", OpenCVLoader.initLocal());
    }

    @Test public void runFrozenAlpha90AcrossFixtureSet() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String[] names = test.getAssets().list(ASSET_DIR);
        assertTrue("No Alpha90 validation assets found", names != null && names.length > 0);
        Arrays.sort(names);

        Map<String, Meta> metaByAsset = readManifest(test);
        File out = new File(app.getFilesDir(), "alpha90-batch");
        deleteRecursively(out);
        out.mkdirs();

        List<String> failures = new ArrayList<>();
        StringBuilder csv = new StringBuilder();
        csv.append("case_id,qc_class,thread_id,target,asset,status,reason,detected_ticks,complete_pairs,inliers,fit_before,fit_after,dial_cx,dial_cy,dial_radius,ellipse_ratio,edge_rms,source_url\n");
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html><head><meta charset='utf-8'><title>Alpha90 batch validation</title>")
            .append("<style>body{font-family:sans-serif;margin:24px;background:#111;color:#eee} .case{border:1px solid #555;padding:16px;margin:18px 0;border-radius:10px} .ok{border-color:#4a7}.bad{border-color:#a66}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:12px}.grid img{width:100%;height:auto;border:1px solid #555}.crop{position:relative}.controls button{margin-right:8px;padding:8px 12px}.mono{font-family:monospace;white-space:pre-wrap}.small{opacity:.8;font-size:.9em}</style>")
            .append("<script>function blink(id){const a=document.getElementById(id+'a'),b=document.getElementById(id+'b');let n=0;window.clearInterval(window[id]);window[id]=setInterval(()=>{const on=(n++%2)==0;a.style.display=on?'none':'block';b.style.display=on?'block':'none';if(n>=8){clearInterval(window[id]);a.style.display='none';b.style.display='block';}},700)}function stopBlink(id){clearInterval(window[id]);document.getElementById(id+'a').style.display='none';document.getElementById(id+'b').style.display='block'}</script></head><body>")
            .append("<h1>Frozen Alpha90 batch validation</h1><p>Same <code>AutomaticDialOverlay.build(Bitmap)</code> path as Alpha90. No candidate feature is allowed to re-fit the master. Acceptance/rejection is automated; RL/GL interpretation remains human review.</p>")
            .append("<p>Close-up framing is presentation-only. Where a yellow projected master marker exists, the crop is centred on that projected master marker, not on a detected candidate marker. The crop therefore cannot pull the genuine reference toward a defect.</p>");

        int processed = 0;
        for (String name : names) {
            String lower = name.toLowerCase(Locale.US);
            if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp"))) continue;
            processed++;
            Meta meta = metaByAsset.get(name);
            if (meta == null) { meta = new Meta(); meta.asset = name; meta.caseId = stem(name); }
            String stem = stem(name);
            File caseDir = new File(out, stem);
            caseDir.mkdirs();
            try {
                Bitmap watch = load(test, ASSET_DIR + "/" + name);
                savePng(watch, new File(caseDir, "candidate.png"));
                long t0 = System.nanoTime();
                AutomaticDialOverlay.Result r = AutomaticDialOverlay.build(watch);
                long ms = (System.nanoTime() - t0) / 1_000_000L;

                String status = r.valid ? "ACCEPTED" : "UNASSESSABLE";
                String reason = r.valid ? "" : r.reason;
                csv.append(csv(meta.caseId)).append(',').append(csv(meta.qcClass)).append(',').append(csv(meta.threadId)).append(',')
                   .append(csv(meta.target)).append(',').append(csv(name)).append(',').append(status).append(',').append(csv(reason)).append(',')
                   .append(r.detectedTicks).append(',').append(r.completePairs).append(',').append(r.inliers).append(',')
                   .append(num(r.fitBefore)).append(',').append(num(r.fitAfter)).append(',')
                   .append(num(r.dialCx)).append(',').append(num(r.dialCy)).append(',').append(num(r.dialRadius)).append(',')
                   .append(num(r.ellipseRatio)).append(',').append(num(r.edgeRms)).append(',').append(csv(meta.sourceUrl)).append('\n');

                html.append("<section class='case ").append(r.valid ? "ok" : "bad").append("'><h2>")
                    .append(esc(meta.caseId)).append(" · ").append(esc(meta.qcClass)).append(" · ").append(esc(name)).append("</h2>")
                    .append("<p><b>Target:</b> ").append(esc(meta.target)).append("</p>")
                    .append("<p><b>Alpha90:</b> ").append(status).append(" in ").append(ms).append(" ms");
                if (!r.valid) html.append("<br><b>Reason:</b> ").append(esc(reason));
                html.append("</p><p class='small'>ticks=").append(r.detectedTicks).append(" pairs=").append(r.completePairs).append(" inliers=").append(r.inliers)
                    .append(" fit=").append(num(r.fitBefore)).append("→").append(num(r.fitAfter)).append("</p>")
                    .append("<div class='grid'><div><h3>Candidate</h3><img src='").append(stem).append("/candidate.png'></div>");

                if (r.valid && r.overlay != null) {
                    savePng(r.overlay, new File(caseDir, "overlay.png"));
                    Bitmap comp = watch.copy(Bitmap.Config.ARGB_8888, true);
                    new Canvas(comp).drawBitmap(r.overlay, 0, 0, null);
                    savePng(comp, new File(caseDir, "composite.png"));
                    html.append("<div><h3>Alpha90 overlay</h3><img src='").append(stem).append("/composite.png'></div></div>");

                    for (int hour = 1; hour <= 12; hour++) {
                        int[] rect = hourCropRect(r.overlay, r, hour);
                        Bitmap c0 = cropRect(watch, rect);
                        Bitmap c1 = cropRect(comp, rect);
                        String base = String.format(Locale.US, "h%02d", hour);
                        savePng(c0, new File(caseDir, base + "_candidate.png"));
                        savePng(c1, new File(caseDir, base + "_overlay.png"));
                        String id = stem + "_" + base;
                        html.append("<div class='crop'><h3>").append(hour).append(" o'clock</h3>")
                            .append("<div class='controls'><button onclick=\"blink('").append(id).append("')\">Blink 4×</button><button onclick=\"stopBlink('").append(id).append("')\">Stop</button></div>")
                            .append("<img id='").append(id).append("a' style='display:none' src='").append(stem).append('/').append(base).append("_candidate.png'>")
                            .append("<img id='").append(id).append("b' src='").append(stem).append('/').append(base).append("_overlay.png'></div>");
                    }
                } else {
                    html.append("</div>");
                }
                html.append("<p class='small'>Thread: ").append(esc(meta.threadId)).append(" · ").append(esc(meta.notes)).append("</p></section>");
                Log.i(TAG, meta.caseId + " " + name + " -> " + status + (reason.isEmpty() ? "" : " / " + reason));
            } catch (Throwable t) {
                failures.add(name + ": " + t);
                html.append("<section class='case bad'><h2>").append(esc(meta.caseId)).append(" · ERROR</h2><pre>").append(esc(Log.getStackTraceString(t))).append("</pre></section>");
                Log.e(TAG, "failed on " + name, t);
            }
        }

        html.append("<h2>Saved review prompt</h2><p>The artifact also includes <code>VISION_REVIEW_PROMPT.md</code>. It is deliberately a second-stage visual reviewer and does not influence Alpha90 pose.</p></body></html>");
        write(new File(out, "results.csv"), csv.toString());
        write(new File(out, "index.html"), html.toString());
        copyAsset(test, "alpha90_validation/VISION_REVIEW_PROMPT.md", new File(out, "VISION_REVIEW_PROMPT.md"));
        copyAsset(test, "alpha90_validation/runtime_manifest.tsv", new File(out, "runtime_manifest.tsv"));
        copyAssetIfExists(test, "alpha90_validation/fetch_errors.txt", new File(out, "fetch_errors.txt"));

        assertTrue("No images processed", processed > 0);
        assertTrue("exceptions: " + failures, failures.isEmpty());
    }

    private static Map<String, Meta> readManifest(Context c) throws Exception {
        Map<String, Meta> out = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getAssets().open(ASSET_DIR + "/runtime_manifest.tsv"), StandardCharsets.UTF_8))) {
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = line.split("\\t", -1);
                if (p.length < 8 || p[5].isEmpty()) continue;
                Meta m = new Meta();
                m.caseId=p[0]; m.qcClass=p[1]; m.threadId=p[2]; m.target=p[3]; m.notes=p[4]; m.asset=p[5]; m.sourceUrl=p[6]; m.fetchStatus=p[7];
                out.put(m.asset, m);
            }
        }
        return out;
    }

    /** Same working-image rule as the app: decode and cap long side at 1600 px. */
    private static Bitmap load(Context c, String path) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        try (InputStream in = c.getAssets().open(path)) { BitmapFactory.decodeStream(in, null, o); }
        int max = Math.max(o.outWidth, o.outHeight), s = 1;
        while (max / (s * 2) >= 1600) s *= 2;
        o.inJustDecodeBounds = false; o.inSampleSize = s; o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b;
        try (InputStream in = c.getAssets().open(path)) { b = BitmapFactory.decodeStream(in, null, o); }
        if (b == null) throw new IllegalArgumentException("not a readable image");
        int cur = Math.max(b.getWidth(), b.getHeight());
        if (cur <= 1600) return b.copy(Bitmap.Config.ARGB_8888, false);
        float k = 1600f / cur;
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth()*k), Math.round(b.getHeight()*k), true).copy(Bitmap.Config.ARGB_8888, false);
    }

    /**
     * Presentation-only crop. It first looks for yellow projected-master pixels in the
     * marker annulus and in the relevant hour sector. That centres the crop on the
     * already-projected genuine marker itself, even under perspective. Candidate pixels
     * are never inspected to choose the crop centre, so a defective marker cannot steer it.
     *
     * Hour 3 has no yellow applied-marker outline in Alpha90 because the date occupies that
     * position; for any hour where no suitable master component is found we fall back to the
     * old broad image-space sector crop.
     */
    private static int[] hourCropRect(Bitmap overlay, AutomaticDialOverlay.Result r, int hour) {
        double expected = Math.toRadians(hour * 30.0 - 90.0);
        double sx = 0.0, sy = 0.0, sw = 0.0;
        int xmin = clamp((int)Math.floor(r.dialCx-r.dialRadius), 0, overlay.getWidth()-1);
        int xmax = clamp((int)Math.ceil (r.dialCx+r.dialRadius), 0, overlay.getWidth()-1);
        int ymin = clamp((int)Math.floor(r.dialCy-r.dialRadius), 0, overlay.getHeight()-1);
        int ymax = clamp((int)Math.ceil (r.dialCy+r.dialRadius), 0, overlay.getHeight()-1);
        double maxDa = Math.toRadians(13.0);

        for (int y=ymin; y<=ymax; y++) {
            for (int x=xmin; x<=xmax; x++) {
                int c = overlay.getPixel(x,y);
                if (Color.alpha(c) < 80 || Color.red(c) < 180 || Color.green(c) < 180 || Color.blue(c) > 140) continue;
                double dx=x-r.dialCx, dy=y-r.dialCy;
                double rr=Math.hypot(dx,dy)/r.dialRadius;
                // Applied marker geometry lives inside the minute track. This excludes the
                // yellow dial edge and all 60 yellow minute ticks from crop-centre selection.
                if (rr < 0.55 || rr > 0.915) continue;
                double a=Math.atan2(dy,dx);
                double da=wrapPi(a-expected);
                if (Math.abs(da) > maxDa) continue;
                double w=Color.alpha(c)/255.0;
                sx+=w*x; sy+=w*y; sw+=w;
            }
        }

        double cx,cy;
        if (sw >= 8.0) {
            cx=sx/sw; cy=sy/sw;
        } else {
            double rr=r.dialRadius*0.72;
            cx=r.dialCx+Math.cos(expected)*rr;
            cy=r.dialCy+Math.sin(expected)*rr;
        }

        int size=Math.max(120,(int)Math.round(r.dialRadius*0.46));
        int x0=clamp((int)Math.round(cx-size/2.0),0,Math.max(0,overlay.getWidth()-1));
        int y0=clamp((int)Math.round(cy-size/2.0),0,Math.max(0,overlay.getHeight()-1));
        int x1=clamp(x0+size,x0+1,overlay.getWidth());
        int y1=clamp(y0+size,y0+1,overlay.getHeight());
        return new int[]{x0,y0,x1,y1};
    }

    private static Bitmap cropRect(Bitmap src,int[] r){
        return Bitmap.createBitmap(src,r[0],r[1],r[2]-r[0],r[3]-r[1]);
    }

    private static double wrapPi(double a){
        while(a<=-Math.PI)a+=2.0*Math.PI;
        while(a>Math.PI)a-=2.0*Math.PI;
        return a;
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    private static String stem(String s) { return s.replaceAll("\\.[^.]+$", ""); }
    private static String num(double d) { return Double.isFinite(d) ? String.format(Locale.US, "%.5f", d) : ""; }
    private static String csv(String s) { if (s == null) return ""; return "\"" + s.replace("\"", "\"\"") + "\""; }
    private static String esc(String s) { if (s == null) return ""; return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;"); }

    private static void savePng(Bitmap b, File f) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) { b.compress(Bitmap.CompressFormat.PNG, 100, o); }
    }
    private static void write(File f, String s) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
    }
    private static void copyAsset(Context c, String path, File f) throws Exception {
        try (InputStream in = c.getAssets().open(path); FileOutputStream o = new FileOutputStream(f)) {
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        }
    }
    private static void copyAssetIfExists(Context c, String path, File f) {
        try { copyAsset(c, path, f); } catch (Throwable ignored) { }
    }
    private static void deleteRecursively(File f) {
        if (!f.exists()) return;
        if (f.isDirectory()) { File[] xs=f.listFiles(); if (xs!=null) for (File x:xs) deleteRecursively(x); }
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }
}
