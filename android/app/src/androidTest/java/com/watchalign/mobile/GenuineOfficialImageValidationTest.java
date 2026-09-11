package com.watchalign.mobile;

import static org.junit.Assert.*;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceptance tests that source exact-model images from the manufacturer pages and
 * feed the genuine image back through Watch Align as both candidate and genuine
 * reference. These are intentionally end-to-end: a genuine image compared with
 * itself must not create a major QC defect.
 */
@RunWith(AndroidJUnit4.class)
public class GenuineOfficialImageValidationTest {
    @BeforeClass public static void initOpenCv() {
        assertTrue("OpenCV failed to initialise", OpenCVLoader.initLocal());
    }

    @Test public void batgirlOfficialImagesDoNotCreateMajorDefects() throws Exception {
        validateOfficialSelfComparisons("126710BLNR", 2, true);
    }

    @Test public void submarinerOfficialImagesDoNotCreateMajorDefects() throws Exception {
        validateOfficialSelfComparisons("124060", 2, false);
    }

    private static void validateOfficialSelfComparisons(String modelRef, int required, boolean expectDate) throws Exception {
        ModelCatalog.Profile profile = ModelCatalog.require(modelRef);
        assertNotNull(profile.officialPage);
        String html = getText(profile.officialPage);
        List<String> urls = OnlineReferenceFinder.extractImageUrls(html, modelRef);
        assertFalse("No exact-model official images discovered for " + modelRef, urls.isEmpty());

        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String url : urls) {
            if (checked >= required) break;
            Bitmap b;
            try { b = getBitmap(url); } catch (Exception e) { continue; }
            if (b == null || Math.min(b.getWidth(), b.getHeight()) < 450) continue;
            try {
                WatchAlignCoreV13.AnalysisResult r = WatchAlignCoreV13.analyse(b, b, modelRef);
                checked++;
                String rep = r.report == null ? "" : r.report;
                if (r.registrationConfidence < 0.90) failures.add("registration=" + r.registrationConfidence + " @ " + url);
                if (rep.contains("strong detected deviation")) failures.add("strong marker defect @ " + url + "\n" + rep);
                if (rep.contains("merits inspection")) failures.add("inspection defect @ " + url + "\n" + rep);
                if (expectDate && !rep.contains("Apparent date numeral magnification vs genuine: 100.0%")) {
                    failures.add("self magnification not 100% @ " + url + "\n" + rep);
                }
            } finally {
                b.recycle();
            }
        }
        assertTrue("Only validated " + checked + " official images for " + modelRef, checked >= required);
        assertTrue("Genuine self-check failures for " + modelRef + ":\n" + String.join("\n\n", failures), failures.isEmpty());
    }

    private static String getText(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384]; int n;
            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8.name());
        } finally { c.disconnect(); }
    }

    private static Bitmap getBitmap(String url) throws Exception {
        HttpURLConnection c = open(url);
        try (InputStream in = new BufferedInputStream(c.getInputStream())) {
            return BitmapFactory.decodeStream(in);
        } finally { c.disconnect(); }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(25000); c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36");
        c.setRequestProperty("Accept", "text/html,image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9");
        return c;
    }
}
