package com.watchalign.mobile;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Runs every photo in androidTest assets "e2e/" through the same path as the app's
 * Check button (decode, cap at 1600 px, WatchAlignCoreV13.analyse) on a device or
 * emulator, and saves the report, the timing, the close-ups and the overlaid photo to
 * the app's files dir under "e2e/" (CI pulls them with run-as).
 *
 * Photos are fetched by CI just before the run; nothing third-party is committed. The
 * test only fails on a crash or exception, so one bad photo does not hide the rest.
 */
@RunWith(AndroidJUnit4.class)
public class EmulatorPhotoRunTest {
    private static final String TAG = "WA_E2E";

    @BeforeClass public static void initOpenCv() {
        assertTrue("OpenCV failed to initialise", OpenCVLoader.initLocal());
    }

    @Test public void runAllPhotos() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String[] names = test.getAssets().list("e2e");
        if (names == null || names.length == 0) { Log.i(TAG, "no photos in assets/e2e"); return; }
        Arrays.sort(names);
        File out = new File(app.getFilesDir(), "e2e");
        out.mkdirs();
        StringBuilder all = new StringBuilder("Watch Align " + WatchAlignCoreV13.CORE_VERSION + " on "
                + android.os.Build.MODEL + " (API " + android.os.Build.VERSION.SDK_INT + ", " + android.os.Build.SUPPORTED_ABIS[0] + ")\n\n");
        List<String> errors = new ArrayList<>();
        for (String name : names) {
            if (!(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) continue;
            String stem = name.replaceAll("\\.[^.]+$", "");
            try {
                Bitmap watch = load(test, "e2e/" + name);
                long t0 = System.nanoTime();
                WatchAlignCoreV13.AnalysisResult r = WatchAlignCoreV13.analyse(watch, Collections.<Bitmap>emptyList(), "126710BLNR");
                long ms = (System.nanoTime() - t0) / 1_000_000;
                String rep = r.report == null ? "" : r.report;
                String head = String.format(java.util.Locale.US, "===== %s (%dx%d, %d ms)\n", name, watch.getWidth(), watch.getHeight(), ms);
                all.append(head).append(summary(rep)).append("\n\n");
                write(new File(out, stem + "_report.txt"), head + rep);
                if (r.twelveCloseUp != null) save(r.twelveCloseUp, new File(out, stem + "_closeup.jpg"), 1400);
                if (r.perspectiveOverlay != null) {
                    Bitmap comp = watch.copy(Bitmap.Config.ARGB_8888, true);
                    new Canvas(comp).drawBitmap(r.perspectiveOverlay, 0, 0, null);
                    save(comp, new File(out, stem + "_overlay.jpg"), 1200);
                }
                Log.i(TAG, head + summary(rep));
            } catch (Throwable t) {
                errors.add(name + ": " + t);
                all.append("===== ").append(name).append("\nERROR: ").append(Log.getStackTraceString(t)).append("\n\n");
                Log.e(TAG, "failed on " + name, t);
            }
        }
        write(new File(out, "summaries.txt"), all.toString());
        assertTrue("exceptions: " + errors, errors.isEmpty());
    }

    /** Same decode as MainActivity: power-of-two sample, then scale so the long side is at most 1600 px. */
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
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true).copy(Bitmap.Config.ARGB_8888, false);
    }

    private static String summary(String rep) {
        int a = rep.indexOf("SUMMARY\n");
        if (a < 0) return rep.length() > 1500 ? rep.substring(0, 1500) : rep;
        int b = rep.indexOf("It does not prove", a);
        int e = b < 0 ? Math.min(rep.length(), a + 2500) : rep.indexOf('\n', b);
        return rep.substring(a, e < 0 ? rep.length() : e).trim();
    }

    private static void save(Bitmap b, File f, int maxSide) throws Exception {
        int m = Math.max(b.getWidth(), b.getHeight());
        Bitmap s = m > maxSide ? Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * maxSide / (float) m), Math.round(b.getHeight() * maxSide / (float) m), true) : b;
        try (FileOutputStream o = new FileOutputStream(f)) { s.compress(Bitmap.CompressFormat.JPEG, 88, o); }
    }

    private static void write(File f, String s) throws Exception {
        try (FileOutputStream o = new FileOutputStream(f)) { o.write(s.getBytes(StandardCharsets.UTF_8)); }
    }
}
