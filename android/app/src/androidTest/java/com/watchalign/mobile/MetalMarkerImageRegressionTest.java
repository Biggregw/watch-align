package com.watchalign.mobile;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import static org.junit.Assert.*;

/** Real SHA-256-pinned photos through the Android decoder, native OpenCV and production pipeline.
 * Missing images fail; Date is loaded explicitly for research and remains hidden in ModelRegistry. */
@RunWith(AndroidJUnit4.class)
public class MetalMarkerImageRegressionTest {
    @BeforeClass public static void openCv(){assertTrue(OpenCVLoader.initLocal());}
    @Test public void gmtControl() throws Exception {validate("49ba843c0a7e19d7");}
    @Test public void noDateControl() throws Exception {validate("d15ed6cf309dec1f");}
    @Test public void dateNineAdjacentSeconds() throws Exception {validate("2802b4a30cfb277b");}
    @Test public void dateSixAdjacentSeconds() throws Exception {validate("ee07ceedbb6f0bd3");}

    static byte[] asset(Context context,String path) throws Exception {
        try(InputStream in=context.getAssets().open(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buf=new byte[16384];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);return out.toByteArray();}
    }
    static String text(Context c,String path) throws Exception {return new String(asset(c,path),StandardCharsets.UTF_8);}
    static void validate(String id) throws Exception {
        Context test=InstrumentationRegistry.getInstrumentation().getContext();
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONArray manifest=new JSONArray(text(test,"marker_regression/manifest.json"));
        assertEquals("all three families and both original batons required",4,manifest.length());
        JSONObject row=null;
        for(int i=0;i<manifest.length();i++)if(id.equals(manifest.getJSONObject(i).getString("photo_id")))row=manifest.getJSONObject(i);
        assertNotNull("missing fixture "+id,row);
        byte[] bytes=asset(test,"marker_regression/"+row.getString("local_path"));
        StringBuilder hash=new StringBuilder();
        for(byte b:MessageDigest.getInstance("SHA-256").digest(bytes))hash.append(String.format(java.util.Locale.US,"%02x",b&255));
        assertEquals("unverified image",row.getString("sha256"),hash.toString());
        BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        Bitmap photo=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);assertNotNull(photo);
        String modelId=row.getString("analysis_model");
        ModelSpec model=ModelSpec.load(app.getAssets()::open,modelId);
        assertEquals(modelId,model.id);
        ModelReference genuine=ModelReference.load(app.getAssets()::open,model);
        String path="models/"+modelId+"/alignment/";
        AlignmentReference ref=AlignmentReference.parse(text(app,path+"luminous_alignment.json"));
        ref.metalExtra=MetalAlignment.parse(text(app,path+"metal_alignment.properties"));
        try {
            Alpha99Pipeline.Output result=Alpha99Pipeline.run(photo,model,genuine,ref,ref::setup);
            assertNotNull("pipeline failed for "+id,result.clearlyOff);
            ClearlyOff.Marker marker=result.clearlyOff.at(row.getInt("hour"));assertNotNull(marker);
            assertEquals(marker.reason,ClearlyOff.State.NO_VISIBLE_ISSUE,marker.state);
            // Android and desktop JPEG decoders can differ by grey-level rounding.
            assertEquals(id+" rotation",row.getDouble("expected_tilt"),marker.tiltDeg,.2);
            if(!row.isNull("expected_side"))assertEquals(id+" sideways",row.getDouble("expected_side"),marker.sidePct,.15);
            if(!row.isNull("expected_inout"))assertEquals(id+" radial",row.getDouble("expected_inout"),marker.inoutPct,.15);
            if(modelId.equals("submariner_126610"))assertTrue("seconds evidence must be exercised",result.checks.get(marker.hour).secondsHand);
            android.util.Log.i("METAL_REGRESSION",id+" "+modelId+" hour="+marker.hour+" state="+marker.state+
                " tilt="+marker.tiltDeg+" side="+marker.sidePct+" inout="+marker.inoutPct+" cover="+marker.cover+" uncertain="+marker.uncertain);
        } finally {photo.recycle();}
    }
}
