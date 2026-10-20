package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Research only. Pose-only independent photo-quality audit, BEFORE marker scoring.
 * No marker locations, QC outcomes or genuine limits enter the perspective decision.
 */
public final class PoseQualityDump {
    private static String num(double v) {
        return Double.isFinite(v) ? String.format(Locale.US, "%.8f", v) : "";
    }
    private static double axisRatio(double a, double b, double c, double d) {
        double trace = a*a+b*b+c*c+d*d;
        double det = a*d-b*c;
        double disc = Math.sqrt(Math.max(0.0, trace*trace-4.0*det*det));
        double big = 0.5*(trace+disc), small = 0.5*(trace-disc);
        return big>0 && small>=0 ? Math.sqrt(small/big) : Double.NaN;
    }
    private static double scale(double[] H, double x, double y) {
        double k = H[6]*x+H[7]*y+H[8];
        if(Math.abs(k)<1e-8)return Double.NaN;
        double xx=(H[0]*x+H[1]*y+H[2])/k;
        double yy=(H[3]*x+H[4]*y+H[5])/k;
        double a=(H[0]-xx*H[6])/k, b=(H[1]-xx*H[7])/k;
        double c=(H[3]-yy*H[6])/k, d=(H[4]-yy*H[7])/k;
        return Math.sqrt(Math.abs(a*d-b*c));
    }
    private static double oppositeScale(double[] H) {
        double max=1.0;
        double[] s={scale(H,-0.85,0),scale(H,0.85,0),scale(H,0,-0.85),scale(H,0,0.85)};
        for(int i=0;i<2;i++){
            double a=s[2*i],b=s[2*i+1];
            if(!Double.isFinite(a)||!Double.isFinite(b)||a<=0||b<=0)return Double.NaN;
            max=Math.max(max,Math.max(a/b,b/a));
        }
        return max;
    }
    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path manifest=Path.of(args[0]), root=Path.of(args[1]), out=Path.of(args[2]);
        ModelSpec model=HarnessModel.spec();
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(manifest));
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
            w.println("photo_id,watch,model,status,reason,ellipse_ratio,jacobian_ratio,opposite_scale_ratio,projective_norm,edge_rms_px,edge_rms_over_radius,diameter_px,minute_ticks");
            for(Map<String,String> r:rows){
                String id=r.get("photo_id"),watch=r.getOrDefault("physical_watch_id","");
                Path path=root.resolve(r.get("local_path"));
                if(!Files.isRegularFile(path)){w.println(id+","+watch+","+model.id+",MISSING,verified_photo_unavailable,,,,,,,,");continue;}
                try{
                    Bitmap b=Alpha96Calib.loadAlpha96(path.toString());
                    AutomaticDialOverlay.Result p=b==null?null:AutomaticDialOverlay.build(b,model);
                    if(p==null||!p.valid||p.homography==null){
                        String why=p==null?"decode_or_pose_failed":p.reason.replace(",",";");
                        w.println(id+","+watch+","+model.id+",NO_POSE,"+why+",,,,,,,,");
                        continue;
                    }
                    double[] h=p.homography;
                    double ar=axisRatio(h[0],h[1],h[3],h[4]);
                    double projective=Math.hypot(h[6],h[7]);
                    w.println(id+","+watch+","+model.id+",MEASURED,,"+num(p.ellipseRatio)+","+
                        num(ar)+","+num(oppositeScale(h))+","+num(projective)+","+
                        num(p.edgeRms)+","+num(p.edgeRms/p.dialRadius)+","+num(2*p.dialRadius)+","+p.detectedTicks);
                }catch(Throwable t){
                    w.println(id+","+watch+","+model.id+",ERROR,"+t.getClass().getSimpleName()+",,,,,,,,");
                }
                w.flush();
                System.out.println("POSE "+id);
            }
        }
    }
}
