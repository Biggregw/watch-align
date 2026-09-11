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
 * End-to-end acceptance tests using exact-model manufacturer assets fetched by
 * the CI host immediately before the Android test APK is built. A genuine image
 * compared with itself must not create a major QC defect.
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
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String base = "genuine/" + modelRef;
        String[] names = context.getAssets().list(base);
        assertNotNull(names);
        Arrays.sort(names);

        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String name : names) {
            if (checked >= required) break;
            if (!name.startsWith("official_") || !name.endsWith(".img")) continue;
            Bitmap b;
            try (InputStream in = context.getAssets().open(base + "/" + name)) {
                b = BitmapFactory.decodeStream(in);
            }
            if (b == null || Math.min(b.getWidth(), b.getHeight()) < 450) continue;
            try {
                WatchAlignCoreV13.AnalysisResult r = WatchAlignCoreV13.analyse(b, b, modelRef);
                checked++;
                String rep = r.report == null ? "" : r.report;
                if (r.registrationConfidence < 0.90) failures.add("registration=" + r.registrationConfidence + " @ " + name);
                if (!rep.startsWith("QC SUMMARY\nNo major defects detected.")) failures.add("summary flagged genuine image @ " + name + "\n" + rep);
                if (rep.contains("strong detected deviation")) failures.add("strong marker defect @ " + name + "\n" + rep);
                if (rep.contains("merits inspection")) failures.add("inspection defect @ " + name + "\n" + rep);
                if (expectDate && !rep.contains("Apparent date numeral magnification vs genuine: 100.0%")) {
                    failures.add("self magnification not 100% @ " + name + "\n" + rep);
                }
            } finally {
                b.recycle();
            }
        }
        assertTrue("Only validated " + checked + " official images for " + modelRef, checked >= required);
        assertTrue("Genuine self-check failures for " + modelRef + ":\n" + String.join("\n\n", failures), failures.isEmpty());
    }
}
