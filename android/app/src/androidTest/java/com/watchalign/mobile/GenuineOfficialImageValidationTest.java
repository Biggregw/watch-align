package com.watchalign.mobile;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * End-to-end acceptance tests using exact-model manufacturer brochure imagery
 * fetched by CI immediately before the Android test APK is built. A genuine
 * image compared with itself must not create a major QC defect.
 */
@RunWith(AndroidJUnit4.class)
public class GenuineOfficialImageValidationTest {
    @BeforeClass public static void initOpenCv() {
        assertTrue("OpenCV failed to initialise", OpenCVLoader.initLocal());
    }

    @Test public void batgirlOfficialImageDoesNotCreateMajorDefects() throws Exception {
        validateOfficialSelfComparisons("126710BLNR", 1, true);
    }

    @Test public void submarinerOfficialImageDoesNotCreateMajorDefects() throws Exception {
        validateOfficialSelfComparisons("124060", 1, false);
    }

    private static void validateOfficialSelfComparisons(String modelRef, int required, boolean expectDate) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String base = "genuine/" + modelRef;
        String[] names = context.getAssets().list(base);
        assertNotNull(names);
        Arrays.sort(names);

        boolean visualFirstGmt = CanonicalGmtGeometryAnalyzer.supports(modelRef);
        // The experimental 124060 route (alpha69+) has its own report and no GMT registration score.
        boolean sub124060 = Sub124060Layout.supports(modelRef);
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String name : names) {
            if (checked >= required) break;
            if (!name.startsWith("official_") || !(name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png"))) continue;
            Bitmap b;
            try (InputStream in = context.getAssets().open(base + "/" + name)) {
                b = BitmapFactory.decodeStream(in);
            }
            if (b == null || Math.min(b.getWidth(), b.getHeight()) < 450) continue;
            try {
                WatchAlignCoreV13.AnalysisResult r = WatchAlignCoreV13.analyse(b, b, modelRef);
                checked++;
                String rep = r.report == null ? "" : r.report;
                if (!sub124060 && (!Double.isFinite(r.registrationConfidence) || r.registrationConfidence < 0.90)) {
                    failures.add("registration=" + r.registrationConfidence + " @ " + name);
                }

                if (sub124060) {
                    // Measured-only contract: a summary exists, the dial is found, and nothing is judged.
                    if (!rep.contains("SUMMARY\n") || !rep.contains(Sub124060Summary.EXPERIMENTAL)) {
                        failures.add("124060 summary missing @ " + name + "\n" + rep);
                    }
                    if (rep.contains("Dial: not assessable")) {
                        failures.add("124060 dial not found on genuine official image @ " + name + "\n" + rep);
                    }
                    if (rep.contains("CHECK CLOSELY") || rep.contains("· CHECK")) {
                        failures.add("124060 verdict shown while verdicts are disabled @ " + name + "\n" + rep);
                    }
                } else if (visualFirstGmt) {
                    // Current GMT report contract (alpha45+): plain-English summary first, the
                    // measured 12-marker section below. A genuine official image must not be
                    // flagged at 12. (The old "VISUAL INSPECTION MODE" / "VISUAL QC MASTER"
                    // sections no longer exist.)
                    if (!rep.contains("SUMMARY\n")) {
                        failures.add("GMT summary missing @ " + name + "\n" + rep);
                    }
                    if (!rep.contains("HUMAN 12-MARKER QC")) {
                        failures.add("GMT 12-marker section missing @ " + name + "\n" + rep);
                    }
                    if (!rep.contains("Bottom line: nothing flagged at 12")) {
                        failures.add("genuine official image flagged at 12 @ " + name + "\n" + rep);
                    }
                } else {
                    if (!rep.startsWith("QC SUMMARY\nNo major defects detected.")) {
                        failures.add("summary flagged genuine image @ " + name + "\n" + rep);
                    }
                    if (expectDate && !rep.contains("Apparent date numeral magnification vs genuine: 100.0%")) {
                        failures.add("self magnification not 100% @ " + name + "\n" + rep);
                    }
                }

                if (rep.contains("strong detected deviation")) failures.add("strong marker defect @ " + name + "\n" + rep);
                if (rep.contains("merits inspection")) failures.add("inspection defect @ " + name + "\n" + rep);
            } finally {
                b.recycle();
            }
        }
        assertTrue("Only validated " + checked + " official images for " + modelRef, checked >= required);
        assertTrue("Genuine self-check failures for " + modelRef + ":\n" + String.join("\n\n", failures), failures.isEmpty());
    }
}
