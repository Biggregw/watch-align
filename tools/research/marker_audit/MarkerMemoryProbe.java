package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.nio.file.Files;
import java.nio.file.Path;

/** Research-only constrained-heap probe; expose errors that the pipeline correctly withholds. */
public final class MarkerMemoryProbe {
    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        ModelSpec model=HarnessModel.spec();
        Path root=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment");
        AlignmentReference ref=AlignmentReference.parse(Files.readString(root.resolve("luminous_alignment.json")));
        ref.metalExtra=MetalAlignment.parse(Files.readString(root.resolve("metal_alignment.properties")));
        Bitmap photo=Alpha96Calib.loadAlpha96(args[0]);
        Alpha99Pipeline.Output result=Alpha99Pipeline.run(photo,model,HarnessModel.ref(),ref,ref::setup);
        System.out.println("heap_limit="+Runtime.getRuntime().maxMemory()+" pose="+result.pose.valid+" reason="+result.pose.reason+
                " clearlyOff="+(result.clearlyOff!=null));
        if(result.clearlyOff==null&&result.alignment!=null){
            Mat rgba=new Mat(),gray=new Mat();
            try{
                Utils.bitmapToMat(photo,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                // Do not swallow the underlying exception in this diagnostic.
                ClearlyOff.analyse(gray,result.pose.homography,result.measurement.dialRadiusPx,
                        Alpha99Pipeline.clearlyOffInputs(result.alignment,result.checks),Alpha99Pipeline.insets(ref),
                        model.minuteTrackInnerR,ClearlyOff.forModel(model.id));
            } finally {rgba.release();gray.release();}
            throw new IllegalStateException("Initial pipeline failed even though isolated measurement completed");
        }
        if(result.clearlyOff==null)throw new IllegalStateException("Pipeline failed");
        for(ClearlyOff.Marker m:result.clearlyOff.markers)
            System.out.println(m.hour+" "+m.state+" "+m.sidePct+" "+m.inoutPct+" "+m.tiltDeg);
    }
}
