package com.watchalign.mobile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Research-only: measures all 12 hour-marker positions on the first-party Rolex catalogue
 * image (media.rolex.com, fetched by fetch_genuine_fixtures.py, the same fixture used by
 * GenuineOfficialImageValidationTest) using the same accurate projected-ROI detector as
 * production QC (GmtMarkerQcRepair), and writes the raw per-marker offsets to external
 * storage so CI can report them.
 *
 * This is deliberately the ONLY source used to recalibrate marker master constants:
 * genuine, first-party manufacturer catalogue imagery, never a user-submitted or
 * market-sourced photo of uncertain provenance.
 */
@RunWith(AndroidJUnit4.class)
public class OfficialMarkerCalibrationTest {
    @BeforeClass public static void initOpenCv(){
        assertTrue("OpenCV failed to initialise",OpenCVLoader.initLocal());
    }

    @Test public void measureOfficialCatalogueImageMarkers()throws Exception{
        Context testContext=InstrumentationRegistry.getInstrumentation().getContext();
        Context targetContext=InstrumentationRegistry.getInstrumentation().getTargetContext();

        Bitmap watch;
        try(InputStream in=testContext.getAssets().open("genuine/126710BLNR/official_00.jpg")){
            watch=BitmapFactory.decodeStream(in);
        }
        assertNotNull("official catalogue fixture failed to decode",watch);

        GmtMarkerQcRepair.MarkerDiagnostic[] diagnostics=GmtMarkerQcRepair.measure(watch);
        assertNotNull("pose acquisition failed on the official catalogue image",diagnostics);

        StringBuilder sb=new StringBuilder("hour\tangular_deg\tradial_pct_r\tmeasured\n");
        for(int h=1;h<=12;h++){
            GmtMarkerQcRepair.MarkerDiagnostic d=diagnostics[h];
            if(d==null){
                sb.append(h).append("\tNA\tNA\tno_marker_at_this_position\n");
                continue;
            }
            sb.append(String.format(Locale.US,"%d\t%.3f\t%.3f\t%s\n",
                    h,d.angularDeg,d.radialPctR,d.measured));
        }

        File dir=new File(targetContext.getExternalFilesDir(null),"marker-calibration");
        assertTrue(dir.mkdirs()||dir.isDirectory());
        try(OutputStreamWriter out=new OutputStreamWriter(
                new FileOutputStream(new File(dir,"official-126710blnr-marker-report.tsv")),
                StandardCharsets.UTF_8)){
            out.write(sb.toString());
        }
    }
}
