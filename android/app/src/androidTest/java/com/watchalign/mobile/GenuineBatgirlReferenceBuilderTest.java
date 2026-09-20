package com.watchalign.mobile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

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
import org.opencv.core.Point;
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
 * Research-only builder for a 126710BLNR genuine-derived planar reference.
 *
 * One automatically accepted image is selected per physical-watch source so a
 * source with many photos cannot dominate the reference. Each selected image is
 * rectified by the production minute-track pipeline. We retain only the annular
 * dial region that contains the minute track and applied hour markers, mask the
 * date/cyclops sector, and combine edge occupancy across independent watches.
 *
 * This deliberately does not treat Watchexchange-labelled watches as independently
 * authenticated. The generated manifest preserves provenance class so later
 * calibration can keep first-party Rolex controls separate from market-labelled
 * genuine candidates.
 */
@RunWith(AndroidJUnit4.class)
public class GenuineBatgirlReferenceBuilderTest {
    private static final String MODEL = "126710BLNR";
    private static final String BASE = "batgirl-reference/126710BLNR";
    private static final int SIDE = 1024;
    private static final double DIAL_RADIUS_PX = SIDE / 2.15;
    private static final double MIN_R = 0.50;
    private static final double MAX_R = 0.965;
    private static final double CONSENSUS = 0.60;

    private static final class Selection {
        final String sourceId;
        final String provenance;
        final String fileName;
        final double confidence;
        final Bitmap rectified;

        Selection(String sourceId,String provenance,String fileName,double confidence,Bitmap rectified){
            this.sourceId=sourceId;
            this.provenance=provenance;
            this.fileName=fileName;
            this.confidence=confidence;
            this.rectified=rectified;
        }
    }

    @BeforeClass public static void initOpenCv(){
        assertTrue("OpenCV failed to initialise", OpenCVLoader.initLocal());
    }

    @Test public void buildCanonicalGenuineEdgeReference() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        AssetManager assets=context.getAssets();
        String[] sourceDirs=assets.list(BASE);
        assertNotNull(sourceDirs);
        Arrays.sort(sourceDirs);

        List<Selection> selected=new ArrayList<>();
        boolean officialSelected=false;
        for(String sourceDir:sourceDirs){
            if(sourceDir.endsWith(".tsv")||sourceDir.endsWith(".txt"))continue;
            String sourceBase=BASE+"/"+sourceDir;
            String[] names=assets.list(sourceBase);
            if(names==null||names.length==0)continue;
            Arrays.sort(names);

            Selection best=null;
            for(String name:names){
                String lower=name.toLowerCase(Locale.US);
                if(!(lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")))continue;
                Bitmap input;
                try(InputStream in=assets.open(sourceBase+"/"+name)){
                    input=BitmapFactory.decodeStream(in);
                }
                if(input==null||Math.min(input.getWidth(),input.getHeight())<300){
                    if(input!=null)input.recycle();
                    continue;
                }
                try{
                    PerspectiveGmtOverlay.Result result=MinuteTrackFirstOverlay.build(input,MODEL,null,Color.RED);
                    if(result==null||!result.automaticAccepted||result.rectified==null)continue;
                    Bitmap canonical=Bitmap.createScaledBitmap(result.rectified,SIDE,SIDE,true);
                    if(best==null||result.confidence>best.confidence){
                        if(best!=null)best.rectified.recycle();
                        best=new Selection(sourceDir,provenance(sourceDir),name,result.confidence,canonical);
                    }else canonical.recycle();
                    result.rectified.recycle();
                    if(result.nativeOverlay!=null)result.nativeOverlay.recycle();
                }finally{
                    input.recycle();
                }
            }
            if(best!=null){
                selected.add(best);
                if("official".equals(best.provenance))officialSelected=true;
            }
        }

        assertTrue("Need at least five independent accepted Batgirl source watches, got "+selected.size(),selected.size()>=5);
        assertTrue("First-party Rolex source did not produce an accepted canonical rectification",officialSelected);

        selected.sort(Comparator.comparing(s->s.sourceId));
        Mat support=Mat.zeros(SIDE,SIDE,CvType.CV_32FC1);
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3));
        try{
            for(Selection s:selected){
                Mat rgba=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat(),dilated=new Mat(),binary32=new Mat();
                try{
                    Utils.bitmapToMat(s.rectified,rgba);
                    Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                    Imgproc.GaussianBlur(gray,blur,new Size(3,3),0.8);
                    Imgproc.Canny(blur,edges,65,155);
                    applyPlanarInspectionMask(edges);
                    Imgproc.dilate(edges,dilated,kernel);
                    dilated.convertTo(binary32,CvType.CV_32FC1,1.0/255.0);
                    Core.add(support,binary32,support);
                }finally{
                    rgba.release();gray.release();blur.release();edges.release();dilated.release();binary32.release();
                }
            }

            Core.multiply(support,new Scalar(1.0/selected.size()),support);
            Mat support8=new Mat(),consensus=new Mat();
            try{
                support.convertTo(support8,CvType.CV_8UC1,255.0);
                Imgproc.threshold(support8,consensus,CONSENSUS*255.0,255.0,Imgproc.THRESH_BINARY);
                applyPlanarInspectionMask(consensus);

                File root=new File(context.getExternalFilesDir(null),"batgirl-reference");
                File selectedDir=new File(root,"selected");
                assertTrue("Could not create reference output directory",selectedDir.mkdirs()||selectedDir.isDirectory());

                writePng(support8,new File(root,"batgirl_genuine_edge_support.png"));
                writePng(consensus,new File(root,"batgirl_genuine_edge_consensus.png"));

                StringBuilder manifest=new StringBuilder();
                manifest.append("model\tsource_id\tprovenance\tselected_image\tconfidence\n");
                for(Selection s:selected){
                    manifest.append(MODEL).append('\t')
                            .append(s.sourceId).append('\t')
                            .append(s.provenance).append('\t')
                            .append(s.fileName).append('\t')
                            .append(String.format(Locale.US,"%.4f",s.confidence)).append('\n');
                    writeJpeg(s.rectified,new File(selectedDir,s.sourceId+".jpg"));
                }
                manifest.append("# consensus_fraction\t").append(String.format(Locale.US,"%.2f",CONSENSUS)).append('\n');
                manifest.append("# mask\tcanonical radius ").append(String.format(Locale.US,"%.2f",MIN_R))
                        .append(" to ").append(String.format(Locale.US,"%.3f",MAX_R))
                        .append("; 3-o'clock date/cyclops sector excluded\n");
                writeText(manifest.toString(),new File(root,"manifest.tsv"));

                int nonZero=Core.countNonZero(consensus);
                assertTrue("Consensus reference unexpectedly sparse: "+nonZero,nonZero>1500);
                assertTrue("Consensus reference unexpectedly dense: "+nonZero,nonZero<SIDE*SIDE*0.18);
            }finally{
                support8.release();consensus.release();
            }
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

    private static void applyPlanarInspectionMask(Mat image){
        double cx=SIDE/2.0,cy=SIDE/2.0,r=DIAL_RADIUS_PX;
        for(int y=0;y<SIDE;y++){
            double ny=(y-cy)/r;
            for(int x=0;x<SIDE;x++){
                double nx=(x-cx)/r;
                double rho=Math.hypot(nx,ny);
                boolean keep=rho>=MIN_R&&rho<=MAX_R;
                // Exclude the complete date/cyclops zone at 3 o'clock. The date numeral lies
                // below the dial and the cyclops lies above it, so neither belongs in a single
                // planar homography reference.
                if(nx>0.43&&Math.abs(ny)<0.30)keep=false;
                if(!keep)image.put(y,x,0.0);
            }
        }
    }

    private static void writePng(Mat gray,File file)throws Exception{
        Mat rgba=new Mat();
        try{
            Imgproc.cvtColor(gray,rgba,Imgproc.COLOR_GRAY2RGBA);
            Bitmap bitmap=Bitmap.createBitmap(gray.cols(),gray.rows(),Bitmap.Config.ARGB_8888);
            try{
                Utils.matToBitmap(rgba,bitmap);
                try(FileOutputStream out=new FileOutputStream(file)){
                    assertTrue("PNG compression failed for "+file,bitmap.compress(Bitmap.CompressFormat.PNG,100,out));
                }
            }finally{bitmap.recycle();}
        }finally{rgba.release();}
    }

    private static void writeJpeg(Bitmap bitmap,File file)throws Exception{
        try(FileOutputStream out=new FileOutputStream(file)){
            assertTrue("JPEG compression failed for "+file,bitmap.compress(Bitmap.CompressFormat.JPEG,90,out));
        }
    }

    private static void writeText(String text,File file)throws Exception{
        try(OutputStreamWriter out=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8)){
            out.write(text);
        }
    }
}
