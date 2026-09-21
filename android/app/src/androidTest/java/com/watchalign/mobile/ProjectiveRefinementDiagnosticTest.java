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
 * user-supplied genuine photo, and separately checks the app's marker measurements
 * against a real, independently-verdicted r/RepTimeQC community QC thread. Calls the
 * same entry points WatchAlignCoreV13 uses for the real automatic pose and marker
 * checks (MinuteTrackRescueOverlay.build, GmtMarkerQcRepair.measure), through the
 * same 1600px-longest-side resize MainActivity.readBitmap always applies, so this
 * reproduces the actual production report the app shows. Not a calibration source
 * and not wired into any production code path itself -- purely diagnostic.
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

        // r/RepTimeQC thread "VSF Batgirl please help QC for wedding GL?": four independent
        // commenters separately flagged the 12 o'clock triangle as slightly CW-tilted/"crooked"/
        // "not aligning with the crown" (one instead flagged 9), though overall consensus leaned
        // GL (4 GL vs 2 RL). Check whether the app's own angular/body-rotation numbers for 9 and
        // 12 land in the same direction and a similarly modest, non-alarming magnitude.
        Bitmap batgirl=decode(testContext,"debug/community-vsf-batgirl-crooked12-01.jpg");
        assertNotNull("VSF Batgirl community fixture failed to decode",batgirl);
        GmtMarkerQcRepair.MarkerDiagnostic[] batgirlMarkers=GmtMarkerQcRepair.measure(batgirl);
        sb.append("=== VSF BATGIRL (r/RepTimeQC, community-flagged 12/9 tilt) ===\n");
        if(batgirlMarkers==null){
            sb.append("GmtMarkerQcRepair.measure() returned null (pose acquisition failed)\n");
        }else{
            for(int hour=1;hour<=12;hour++){
                GmtMarkerQcRepair.MarkerDiagnostic d=batgirlMarkers[hour];
                if(d==null)continue;
                sb.append(d.measured?String.format(java.util.Locale.US,
                        "%d marker: angular offset %+.2f°, radial %+.2f%% R, body rotation %s%s\n",
                        d.hour,d.angularDeg,d.radialPctR,
                        Double.isFinite(d.bodyRotationDeg)?String.format(java.util.Locale.US,"%+.2f°",d.bodyRotationDeg):"unavailable",
                        Double.isFinite(d.triangleOutwardDeltaPctR)?String.format(java.util.Locale.US,", base-to-minute-track %+.2f%% R",d.triangleOutwardDeltaPctR):"")
                        :String.format(java.util.Locale.US,"%d marker: not confidently isolated\n",d.hour));
            }
        }
        sb.append("\n");

        File dir=new File(targetContext.getExternalFilesDir(null),"projective-diagnostic");
        assertTrue(dir.mkdirs()||dir.isDirectory());
        try(OutputStreamWriter out=new OutputStreamWriter(
                new FileOutputStream(new File(dir,"projective-refinement-report.txt")),
                StandardCharsets.UTF_8)){
            out.write(sb.toString());
        }
    }

    /**
     * MainActivity.readBitmap always caps the picked photo's longest side at 1600px before
     * running any detection. Decoding a test asset directly, without this same cap, gives the
     * pipeline a different effective resolution than production ever sees -- match it here so
     * this test's candidate counts and pose actually reproduce what the app does.
     */
    private static Bitmap decode(Context context,String assetPath)throws Exception{
        Bitmap raw;
        try(InputStream in=context.getAssets().open(assetPath)){
            raw=BitmapFactory.decodeStream(in);
        }
        int currentMax=Math.max(raw.getWidth(),raw.getHeight());
        if(currentMax<=1600)return raw;
        float scale=1600f/currentMax;
        return Bitmap.createScaledBitmap(raw,Math.round(raw.getWidth()*scale),Math.round(raw.getHeight()*scale),true);
    }
}
