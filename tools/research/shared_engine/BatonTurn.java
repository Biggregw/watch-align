package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.nio.file.*;
import java.util.*;

/** Known-angle ground truth: each baton (and the 12 triangle) of an unchanged genuine photo turned about its centre by a
 *  known amount (MetalSynth.move), then measured by the unchanged app check. Research only. BatonTurn image id */
public class BatonTurn {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=AlignmentReference.parse(Files.readString(refFile));
        Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));
        Bitmap b=Alpha96Calib.loadAlpha96(a[0]);
        Alpha99Pipeline.Output o=Alpha99Pipeline.run(b,model,metal,ref,ref::setup);
        double[] H=o.pose.homography;double rpx=o.measurement.dialRadiusPx;
        List<ClearlyOff.Input> in=Alpha99Pipeline.clearlyOffInputs(o.alignment,o.checks);
        Map<ModelSpec.Shape,Double> insets=Alpha99Pipeline.insets(ref);
        Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
        double[] turns={0,-2.5,-1.75,-1.4,-1.2,-0.9,-0.5,0.5,0.9,1.2,1.4,1.75,2.5};
        for(ClearlyOff.Input m:in){
            if(m.yellow==null||(m.shape!=ModelSpec.Shape.BATON&&m.shape!=ModelSpec.Shape.TRIANGLE))continue;
            ClearlyOff.Marker bm=o.clearlyOff.at(m.hour);
            if(bm==null||bm.state==ClearlyOff.State.CANT_CHECK)continue;
            for(double t:turns){
                Mat g2=t==0?gray.clone():MetalSynth.move(gray,H,m.yellow,0,0,Math.toRadians(t));
                ClearlyOff.Result res=ClearlyOff.analyse(g2,H,rpx,in,insets,model.minuteTrackInnerR,ClearlyOff.Limits.defaults());g2.release();
                ClearlyOff.Marker tm=res.at(m.hour);
                System.out.println(String.format(Locale.US,"TURN,%s,%s,%.1f,%d,%s,%.2f,%.4f,%.4f,%s,%s,%.3f,%.3f",a[1],model.id,rpx,m.hour,AlignmentReference.key(m.shape),t,
                    bm.tiltDeg,tm==null?Double.NaN:tm.tiltDeg,tm==null?"ERR":tm.state.name(),tm==null?"":tm.uncertain.isEmpty()?"clean":"uncertain",
                    tm==null?Double.NaN:tm.fitRmsPx,tm==null?Double.NaN:tm.badFrac));
            }
        }
    }
}
