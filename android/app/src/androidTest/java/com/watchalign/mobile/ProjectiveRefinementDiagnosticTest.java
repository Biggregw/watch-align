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

/**
 * Debug-only: investigates a suspected under-correction in the projective refinement
 * step (PerspectiveGmtOverlay/DialProjectiveRefiner) on a real, significantly tilted,
 * user-supplied genuine photo. Calls MinuteTrackRescueOverlay.build, the same entry
 * point WatchAlignCoreV13 uses for the real automatic pose, so this reproduces the
 * actual production report the app shows. Not a calibration source and not wired
 * into any production code path itself -- purely diagnostic, to capture the full
 * VISUAL QC MASTER report for offline comparison against the front-on official
 * catalogue fixture.
 */
@RunWith(AndroidJUnit4.class)
public class ProjectiveRefinementDiagnosticTest {
    @BeforeClass public static void initOpenCv(){
        assertTrue("OpenCV failed to initialise",OpenCVLoader.initLocal());
    }

    @Test public void reportProjectiveRefinementOnTiltedAndFrontOnPhotos()throws Exception{
        Context testContext=InstrumentationRegistry.getInstrumentation().getContext();
        Context targetContext=InstrumentationRegistry.getInstrumentation().getTargetContext();

        StringBuilder sb=new StringBuilder();

        int overlayColor=android.graphics.Color.rgb(255,45,45);

        Bitmap tilted=decode(testContext,"debug/community-tilted-126710blnr-01.jpg");
        assertNotNull("tilted community fixture failed to decode",tilted);
        PerspectiveGmtOverlay.Result tiltedResult=
                MinuteTrackRescueOverlay.build(tilted,"126710BLNR",null,overlayColor);
        sb.append("=== TILTED COMMUNITY PHOTO ===\n");
        sb.append(tiltedResult==null?"build() returned null\n":tiltedResult.report);
        sb.append("\n");

        Bitmap frontOn=decode(testContext,"genuine/126710BLNR/official_00.jpg");
        assertNotNull("official catalogue fixture failed to decode",frontOn);
        PerspectiveGmtOverlay.Result frontOnResult=
                MinuteTrackRescueOverlay.build(frontOn,"126710BLNR",null,overlayColor);
        sb.append("=== FRONT-ON OFFICIAL CATALOGUE PHOTO ===\n");
        sb.append(frontOnResult==null?"build() returned null\n":frontOnResult.report);
        sb.append("\n");

        File dir=new File(targetContext.getExternalFilesDir(null),"projective-diagnostic");
        assertTrue(dir.mkdirs()||dir.isDirectory());
        try(OutputStreamWriter out=new OutputStreamWriter(
                new FileOutputStream(new File(dir,"projective-refinement-report.txt")),
                StandardCharsets.UTF_8)){
            out.write(sb.toString());
        }
    }

    private static Bitmap decode(Context context,String assetPath)throws Exception{
        try(InputStream in=context.getAssets().open(assetPath)){
            return BitmapFactory.decodeStream(in);
        }
    }
}
