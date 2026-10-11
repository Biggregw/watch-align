package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Locale;

/** Offline only: export profiles through the unchanged detector's spline sampler and frozen pose.
 * Usage: run.sh MarkerEdgeAudit photo.jpg outdir [hour=9]. No edge correction or QC limits.
 */
public class MarkerEdgeAudit {
    public static void main(String[] args)throws Exception{
        nu.pattern.OpenCV.loadLocally();
        Bitmap photo=Alpha96Calib.loadAlpha96(args[0]);
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        Alpha99Pipeline.Output out=Alpha99Pipeline.run(photo,model,ref);
        if(!out.ok())throw new IllegalStateException("pose unavailable");
        int hour=args.length>2?Integer.parseInt(args[2]):9;
        ModelSpec.Marker spec=model.atHour(hour);
        Alpha94MarkerMeasurement.Marker marker=spec!=null&&spec.shape==ModelSpec.Shape.TRIANGLE?out.measurement.triangle:out.measurement.atHour(hour);
        if(spec==null||marker==null||!marker.usable)throw new IllegalStateException("polygon unavailable");
        Path dir=Path.of(args[1]);Files.createDirectories(dir);
        Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(photo,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
        // Read the exact production sampler without exposing a new production API.
        Class<?> sampler=Class.forName("com.watchalign.mobile.Alpha94MarkerMeasurement$Sampler");
        Constructor<?> ctor=sampler.getDeclaredConstructor(Mat.class,double[].class);ctor.setAccessible(true);
        Object image=ctor.newInstance(gray,out.pose.homography);
        Method at=sampler.getDeclaredMethod("at",double.class,double.class);at.setAccessible(true);
        double R=out.measurement.dialRadiusPx;
        double[][] actual=MarkerMismatchResearch.actual(spec,marker,R),expected=ExpectedMarkerOutline.polygon(spec,ref,out.measurement.ring.model);
        if(actual==null)throw new IllegalStateException("polygon unavailable");
        double[][] master=ExpectedMarkerOutline.polygon(spec,null,null);
        try(PrintWriter meta=new PrintWriter(dir.resolve("geometry.txt").toFile())){
            meta.println("H="+Arrays.toString(out.pose.homography));meta.println("ring="+Arrays.toString(out.measurement.ring.model));
            meta.println("ring_shift_photo_px="+out.measurement.ring.shiftPx);
            meta.println("R="+R);meta.println("coverage="+Arrays.toString(marker.sideCov));
            meta.println("side_offsets_R="+Arrays.toString(marker.sideOffR));
            meta.println("rotation_deg="+marker.rotationDeg);
            meta.println("local_radial_px="+marker.localRadialPx);meta.println("local_tangential_px="+marker.localTangentialPx);
            MarkerMismatchResearch.Result q=out.mismatch.get(hour);meta.println("display="+q.display());
            for(Alpha99Findings.Finding f:out.summary.all)if(!f.dialWide&&f.hour==hour){
                meta.println("status="+f.status);meta.println("long_edge_angles="+f.edgeDiagnostic);
                for(Alpha99Findings.Measure m:f.measures)meta.printf(Locale.US,"measure=%s value=%.10f genuine_max=%.10f sigma=%.10f status=%s%n",m.name,m.value,m.genuineMax,m.sigma,m.status());
            }
            if(spec.shape==ModelSpec.Shape.TRIANGLE){
                // Offline geometry only: midpoint of the outer base to NOMINAL track circle. Not a detected tick gap.
                meta.printf(Locale.US,"fitted_base_to_nominal_track_R=%.10f%n",baseGap(actual,model.minuteTrackInnerR));
                meta.printf(Locale.US,"expected_base_to_nominal_track_R=%.10f%n",baseGap(expected,model.minuteTrackInnerR));
                meta.println("base_gap_is_unvalidated=true; radial QC measure above is a centroid displacement, not this gap");
            }
            for(double[][] polygon:new double[][][]{expected,actual,master}){
                meta.println("vertices="+Arrays.deepToString(polygon));
                MarkerPolygonMismatch.Score s=MarkerPolygonMismatch.compare(polygon,actual);
                meta.printf(Locale.US,"extra=%.8f missing=%.8f total=%.8f%n",s.extraPct,s.missingPct,s.mismatchPct);
            }
        }
        try(PrintWriter csv=new PrintWriter(dir.resolve("profiles.csv").toFile())){
            csv.println("side,fraction,offset_px,intensity");
            double cx=0,cy=0;for(double[] p:actual){cx+=p[0]/actual.length;cy+=p[1]/actual.length;}
            for(int side=0;side<actual.length;side+=spec.shape==ModelSpec.Shape.ROUND?16:1){
                double[] p=actual[side],q=actual[(side+1)%actual.length];double dx=q[0]-p[0],dy=q[1]-p[1],len=Math.hypot(dx,dy);
                double nx=dy/len,ny=-dx/len;
                if(nx*((p[0]+q[0])/2-cx)+ny*((p[1]+q[1])/2-cy)<0){nx=-nx;ny=-ny;}
                for(double fraction:spec.shape==ModelSpec.Shape.ROUND?new double[]{.5}:new double[]{.2,.35,.5,.65,.8})for(int j=-120;j<=120;j++){
                    double offset=j*.05;
                    double intensity=(double)at.invoke(image,p[0]+fraction*dx+offset/R*nx,p[1]+fraction*dy+offset/R*ny);
                    csv.printf(Locale.US,"%d,%.2f,%.2f,%.8f%n",side,fraction,offset,intensity);
                }
            }
        }
        double[] c=spec.masterPoint();
        Mat raw=Alpha98Closeups.render(rgba,out.pose.homography,c[0],c[1],.20);
        Bitmap crop=Bitmap.createBitmap(480,480,Bitmap.Config.ARGB_8888);Utils.matToBitmap(raw,crop);
        javax.imageio.ImageIO.write(crop.img,"png",dir.resolve("raw.png").toFile());
        for(Alpha99Findings.Finding f:out.summary.all)if(!f.dialWide&&f.hour==hour){
            javax.imageio.ImageIO.write(out.closeups.get(f.key).img,"png",dir.resolve("scored-outline.png").toFile());break;
        }
        raw.release();gray.release();rgba.release();
        Files.writeString(dir.resolve("technical.txt"),out.technical);
    }
    private static double baseGap(double[][] triangle,double trackInnerR){
        int apex=0;for(int i=1;i<3;i++)if(Math.hypot(triangle[i][0],triangle[i][1])<Math.hypot(triangle[apex][0],triangle[apex][1]))apex=i;
        double x=0,y=0;for(int i=0;i<3;i++)if(i!=apex){x+=triangle[i][0]/2;y+=triangle[i][1]/2;}
        return trackInnerR-Math.hypot(x,y);
    }
}
