package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * RESEARCH ONLY (lume-first bevel experiment). Runs the UNCHANGED Alpha107 pipeline on one photo and exports, for the
 * 12 o'clock triangle, everything the offline comparison needs in ONE coordinate system:
 *   - the frozen dial pose H (canonical dial units -> source pixels; dial radius 1, 12 up), from the dial/minute track;
 *   - the ring similarity model (fitted on the non-triangle markers only: ModelSpec.ringMarkers excludes the 12);
 *   - the exact expected (yellow) polygon ExpectedMarkerOutline.polygon(spec, ref, ring), as used by the app;
 *   - a high-resolution perspective-rectified crop of the triangle region (same transform as Alpha98Closeups.render,
 *     only larger), so a reconstructed boundary found in the crop maps back to canonical units exactly;
 *   - the app's own existing 12 o'clock readings (for the record; never used by the reconstruction).
 * Nothing here feeds back into the pipeline. Usage: run.sh TriangleLumeExport image.jpg out_dir id
 */
public class TriangleLumeExport {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path out=Path.of(a[1]);Files.createDirectories(out);String id=a[2];
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        Bitmap b=Alpha96Calib.loadAlpha96(a[0]);
        Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,model,ref);
        if(o==null||!o.ok()){System.out.println("EXPORT "+id+": no pose");return;}
        double[] H=o.pose.homography;double R=o.measurement.dialRadiusPx;
        double[] ring=o.measurement.ring!=null&&o.measurement.ring.usable?o.measurement.ring.model:null;
        ModelSpec.Marker spec=model.triangle();
        double[][] expected=ExpectedMarkerOutline.polygon(spec,ref,ring);
        double cx=0,cy=0;for(double[] p:expected){cx+=p[0]/expected.length;cy+=p[1]/expected.length;}
        double half=0.21;int size=(int)Math.min(1600,Math.max(400,Math.round(2*half*R*4)));
        double s=2*half/size;
        Mat rgba=new Mat();Utils.bitmapToMat(b,rgba);
        Mat T=new Mat(3,3,CvType.CV_64F);T.put(0,0,s,0,cx-half+0.5*s,0,s,cy-half+0.5*s,0,0,1);
        Mat Hm=new Mat(3,3,CvType.CV_64F);Hm.put(0,0,H);Mat M=new Mat();Core.gemm(Hm,T,1,new Mat(),0,M);
        Mat crop=new Mat();Imgproc.warpPerspective(rgba,crop,M,new Size(size,size),Imgproc.INTER_CUBIC|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0,0,0,255));
        Mat bgr=new Mat();Imgproc.cvtColor(crop,bgr,Imgproc.COLOR_RGBA2BGR);
        org.opencv.imgcodecs.Imgcodecs.imwrite(out.resolve(id+"_rect.png").toString(),bgr);
        // the app's own close-up (yellow-only for the withheld triangle), for the side-by-side comparison
        Alpha99Findings.Finding twelve=null;for(Alpha99Findings.Finding f:o.summary.all)if(f.shape==Alpha99Findings.Shape.TRIANGLE)twelve=f;
        Bitmap app=o.closeups.get(twelve==null?"":twelve.key);
        if(app!=null)javax.imageio.ImageIO.write(app.img,"png",out.resolve(id+"_app_closeup.png").toFile());
        Alpha94MarkerMeasurement.Marker tm=o.measurement.triangle;
        MarkerMismatchResearch.Result q=o.mismatch.get(spec.hour);
        StringBuilder j=new StringBuilder("{\n");
        j.append(String.format(Locale.US,"\"id\":\"%s\",\"image_w\":%d,\"image_h\":%d,\"dial_R_px\":%.4f,\n",id,b.getWidth(),b.getHeight(),R));
        j.append("\"H\":").append(arr(H)).append(",\n\"ring\":").append(ring==null?"null":arr(ring)).append(",\n");
        j.append("\"expected\":").append(poly(expected)).append(",\n");
        j.append("\"master\":").append(poly(spec.trianglePolygon())).append(",\n");
        j.append(String.format(Locale.US,"\"crop\":{\"cx\":%.9f,\"cy\":%.9f,\"half\":%.6f,\"size\":%d},\n",cx,cy,half,size));
        j.append(String.format(Locale.US,"\"triangle_nominal\":{\"radial_R\":%.6f,\"tangential_R\":%.6f},\n",ref.triangle==null?Double.NaN:ref.triangle.nominalRadialR,ref.triangle==null?Double.NaN:ref.triangle.nominalTangentialR));
        if(tm!=null)j.append(String.format(Locale.US,"\"alpha94\":{\"usable\":%s,\"reason\":\"%s\",\"localTangentialPx\":%.4f,\"localRadialPx\":%.4f,\"rotationDeg\":%.4f,\"leftSideErrDeg\":%.4f,\"rightSideErrDeg\":%.4f,\"baseTiltDeg\":%.4f,\"fitVertices\":%s},\n",
                tm.usable,String.valueOf(tm.reason).replace("\"","'"),tm.localTangentialPx,tm.localRadialPx,tm.rotationDeg,tm.leftSideErrDeg,tm.rightSideErrDeg,tm.baseTiltDeg,tm.fitVertices==null?"null":poly(tm.fitVertices)));
        j.append("\"finding\":{\"status\":\"").append(twelve==null?"":twelve.status).append("\",\"measures\":[");
        if(twelve!=null)for(int i=0;i<twelve.measures.size();i++){Alpha99Findings.Measure m=twelve.measures.get(i);
            j.append(i==0?"":",").append(String.format(Locale.US,"{\"name\":\"%s\",\"value\":%.6f,\"genuineMax\":%.6f,\"status\":\"%s\",\"text\":\"%s\"}",m.name,m.value,m.genuineMax,m.status(),m.sentence.replace("\"","'")));}
        j.append("]},\n\"area\":\"").append(q==null?"none":String.valueOf(q.reason).replace("\"","'")).append("\"\n}\n");
        Files.writeString(out.resolve(id+"_export.json"),j.toString());
        System.out.println("EXPORT "+id+" R="+String.format(Locale.US,"%.1f",R)+" crop="+size+" status="+(twelve==null?"-":twelve.status));
    }
    static String arr(double[] v){StringBuilder s=new StringBuilder("[");for(int i=0;i<v.length;i++)s.append(i==0?"":",").append(String.format(Locale.US,"%.12g",v[i]));return s.append("]").toString();}
    static String poly(double[][] p){StringBuilder s=new StringBuilder("[");for(int i=0;i<p.length;i++)s.append(i==0?"":",").append(arr(p[i]));return s.append("]").toString();}
}
