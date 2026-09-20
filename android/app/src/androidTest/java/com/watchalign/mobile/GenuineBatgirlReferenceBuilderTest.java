package com.watchalign.mobile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Research-only 126710BLNR genuine-derived planar reference builder.
 *
 * The build fetches multiple photos per source watch, but this test selects at
 * most one accepted photo per physical-watch source. Rectification uses the same
 * production primary-plus-rescue path as the app. The derived reference keeps the
 * minute-track/hour-marker annulus and masks the date/cyclops sector because the
 * visible numeral and cyclops do not live on the dial plane.
 */
@RunWith(AndroidJUnit4.class)
public class GenuineBatgirlReferenceBuilderTest {
    private static final String TAG="BatgirlReference";
    private static final String MODEL="126710BLNR";
    private static final String BASE="batgirl-reference/126710BLNR";
    private static final int SIDE=1024;
    private static final double R=SIDE/2.15;
    private static final double MIN_R=0.50,MAX_R=0.965,CONSENSUS=0.60;

    private static final class Selection {
        final String sourceId,provenance,fileName;
        final double confidence;
        final Bitmap rectified;
        Selection(String sourceId,String provenance,String fileName,double confidence,Bitmap rectified){
            this.sourceId=sourceId;this.provenance=provenance;this.fileName=fileName;
            this.confidence=confidence;this.rectified=rectified;
        }
    }

    @BeforeClass public static void initOpenCv(){
        assertTrue("OpenCV failed to initialise",OpenCVLoader.initLocal());
    }

    @Test public void buildCanonicalGenuineEdgeReference()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AssetManager assets=context.getAssets();
        String[] sourceDirs=assets.list(BASE);
        assertNotNull(sourceDirs);Arrays.sort(sourceDirs);

        List<Selection> selected=new ArrayList<>();
        boolean official=false;
        for(String sourceDir:sourceDirs){
            if(sourceDir.endsWith(".tsv")||sourceDir.endsWith(".txt"))continue;
            String[] names=assets.list(BASE+"/"+sourceDir);
            if(names==null||names.length==0)continue;
            Arrays.sort(names);
            Selection best=null;int attempted=0,accepted=0;
            for(String name:names){
                String lower=name.toLowerCase(Locale.US);
                if(!(lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")))continue;
                Bitmap input;
                try(InputStream in=assets.open(BASE+"/"+sourceDir+"/"+name)){
                    input=BitmapFactory.decodeStream(in);
                }
                if(input==null||Math.min(input.getWidth(),input.getHeight())<300){
                    if(input!=null)input.recycle();continue;
                }
                attempted++;
                PerspectiveGmtOverlay.Result result=null;
                try{
                    // Use the actual app path. An accepted primary pose is untouched; only a
                    // rejected primary pose can invoke the rescue selector.
                    result=MinuteTrackRescueOverlay.build(input,MODEL,null,Color.RED);
                    if(result==null||!result.automaticAccepted||result.rectified==null)continue;
                    accepted++;
                    Bitmap canonical=Bitmap.createScaledBitmap(result.rectified,SIDE,SIDE,true);
                    if(best==null||result.confidence>best.confidence){
                        if(best!=null)best.rectified.recycle();
                        best=new Selection(sourceDir,provenance(sourceDir),name,result.confidence,canonical);
                    }else canonical.recycle();
                }finally{
                    if(result!=null){
                        if(result.rectified!=null)result.rectified.recycle();
                        if(result.nativeOverlay!=null)result.nativeOverlay.recycle();
                    }
                    input.recycle();
                }
            }
            Log.i(TAG,sourceDir+": attempted="+attempted+" accepted="+accepted+
                    (best==null?"":" best="+best.fileName+" confidence="+best.confidence));
            if(best!=null){
                selected.add(best);
                if("official".equals(best.provenance))official=true;
            }
        }

        assertTrue("Need at least five independent accepted Batgirl source watches, got "+selected.size(),selected.size()>=5);
        assertTrue("First-party Rolex source did not produce an accepted canonical rectification",official);

        selected.sort(Comparator.comparing(s->s.sourceId));
        Mat support=Mat.zeros(SIDE,SIDE,CvType.CV_32FC1);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3));
        try{
            for(Selection s:selected){
                Mat rgba=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat(),dilated=new Mat(),one=new Mat();
                try{
                    Utils.bitmapToMat(s.rectified,rgba);
                    Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                    Imgproc.GaussianBlur(gray,blur,new Size(3,3),0.8);
                    Imgproc.Canny(blur,edges,65,155);
                    mask(edges);
                    Imgproc.dilate(edges,dilated,kernel);
                    dilated.convertTo(one,CvType.CV_32FC1,1.0/255.0);
                    Core.add(support,one,support);
                }finally{
                    rgba.release();gray.release();blur.release();edges.release();dilated.release();one.release();
                }
            }
            Core.multiply(support,new Scalar(1.0/selected.size()),support);
            Mat support8=new Mat(),consensus=new Mat();
            try{
                support.convertTo(support8,CvType.CV_8UC1,255.0);
                Imgproc.threshold(support8,consensus,CONSENSUS*255.0,255.0,Imgproc.THRESH_BINARY);
                mask(consensus);

                File root=new File(context.getExternalFilesDir(null),"batgirl-reference");
                File selectedDir=new File(root,"selected");
                assertTrue(selectedDir.mkdirs()||selectedDir.isDirectory());
                writePng(support8,new File(root,"batgirl_genuine_edge_support.png"));
                writePng(consensus,new File(root,"batgirl_genuine_edge_consensus.png"));

                StringBuilder manifest=new StringBuilder("model\tsource_id\tprovenance\tselected_image\tconfidence\n");
                for(Selection s:selected){
                    manifest.append(MODEL).append('\t').append(s.sourceId).append('\t')
                            .append(s.provenance).append('\t').append(s.fileName).append('\t')
                            .append(String.format(Locale.US,"%.4f",s.confidence)).append('\n');
                    writeJpeg(s.rectified,new File(selectedDir,s.sourceId+".jpg"));
                }
                manifest.append("# consensus_fraction\t").append(String.format(Locale.US,"%.2f",CONSENSUS)).append('\n');
                manifest.append("# mask\tcanonical radius 0.50 to 0.965; date/cyclops sector excluded\n");
                writeText(manifest.toString(),new File(root,"manifest.tsv"));

                int nonZero=Core.countNonZero(consensus);
                assertTrue("Consensus unexpectedly sparse: "+nonZero,nonZero>1500);
                assertTrue("Consensus unexpectedly dense: "+nonZero,nonZero<SIDE*SIDE*0.18);
            }finally{support8.release();consensus.release();}
        }finally{
            kernel.release();support.release();
            for(Selection s:selected)s.rectified.recycle();
        }
    }

    private static String provenance(String sourceId){
        if(sourceId.startsWith("official_"))return "official";
        if(sourceId.startsWith("gen_candidate_"))return "gen_candidate";
        return "unknown";
    }

    private static void mask(Mat image){
        double cx=SIDE/2.0,cy=SIDE/2.0;
        for(int y=0;y<SIDE;y++){
            double ny=(y-cy)/R;
            for(int x=0;x<SIDE;x++){
                double nx=(x-cx)/R,rho=Math.hypot(nx,ny);
                boolean keep=rho>=MIN_R&&rho<=MAX_R;
                if(nx>0.43&&Math.abs(ny)<0.30)keep=false;
                if(!keep)image.put(y,x,0.0);
            }
        }
    }

    private static void writePng(Mat gray,File file)throws Exception{
        Mat rgba=new Mat();
        try{
            Imgproc.cvtColor(gray,rgba,Imgproc.COLOR_GRAY2RGBA);
            Bitmap b=Bitmap.createBitmap(gray.cols(),gray.rows(),Bitmap.Config.ARGB_8888);
            try{
                Utils.matToBitmap(rgba,b);
                try(FileOutputStream out=new FileOutputStream(file)){
                    assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,out));
                }
            }finally{b.recycle();}
        }finally{rgba.release();}
    }

    private static void writeJpeg(Bitmap b,File file)throws Exception{
        try(FileOutputStream out=new FileOutputStream(file)){
            assertTrue(b.compress(Bitmap.CompressFormat.JPEG,90,out));
        }
    }

    private static void writeText(String text,File file)throws Exception{
        try(OutputStreamWriter out=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){
            out.write(text);
        }
    }
}
