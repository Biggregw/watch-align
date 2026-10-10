package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Research-only Alpha105g 9-marker measurements on externally validated photo inputs.
 * No production calibration changes. A raw angle is not treated as usable unless
 * the exact in-app 9-rotation assessment passed its reliability gates.
 * Usage: run.sh GmtNineRedditQc <manifest.csv> <root> <results.csv>
 */
public final class GmtNineRedditQc {
    static String csv(String s) {return "\""+(s==null?"":s).replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+"\"";}
    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        ModelSpec spec=HarnessModel.spec();
        ModelReference ref=HarnessModel.ref();
        List<String> lines=Files.readAllLines(Path.of(args[0]));
        String[] h=lines.get(0).split(",",-1);
        int ci=-1,pi=-1;
        for(int i=0;i<h.length;i++){
            if("photo_id".equals(h[i]))ci=i;
            if("local_path".equals(h[i]))pi=i;
        }
        if(ci<0||pi<0)throw new IllegalArgumentException("Need photo_id and local_path columns");
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(Path.of(args[2])))){
            out.println("photo_id,post_id,dial_radius_px,rotation_signed_deg,rotation_assessed,marker_status,position_assessed,reason,side_width_delta_px");
            for(int k=1;k<lines.size();k++){
                String[] f=lines.get(k).split(",",-1);
                if(f.length<=Math.max(ci,pi))continue;
                String id=f[ci],post=id.split("_")[0];
                String radius="",rotation="",rotAssessment="false",status="NO_POSE",posAssessment="false",reason="",sideWidth="";
                try{
                    Bitmap bmp=Alpha96Calib.loadAlpha96(Path.of(args[1]).resolve(f[pi]).toString());
                    Alpha99Pipeline.Output o=bmp==null?null:Alpha99Pipeline.run(bmp,spec,ref);
                    if(o!=null&&o.ok()){
                        Alpha94MarkerMeasurement.Report r=o.measurement;
                        Alpha94MarkerMeasurement.Marker m=r==null?null:r.atHour(9);
                        radius=String.format(Locale.US,"%.1f",r.dialRadiusPx);
                        Alpha99Findings.Finding nine=null;
                        for(Alpha99Findings.Finding x:o.summary.all)if("nine".equals(x.key)){nine=x;break;}
                        if(m!=null&&m.usable&&Double.isFinite(m.rotationDeg)){
                            rotation=String.format(Locale.US,"%.4f",m.rotationDeg-ref.nominal("nine_rot"));
                            if(m.sideOffR!=null&&m.sideOffR.length>2)
                                sideWidth=String.format(Locale.US,"%.3f",(m.sideOffR[0]+m.sideOffR[2])*r.dialRadiusPx);
                        }
                        if(nine!=null){
                            status=nine.status.name();
                            for(Alpha99Findings.Measure measure:nine.measures){
                                if("rotation".equals(measure.name)&&Double.isFinite(measure.value))rotAssessment="true";
                                if("position".equals(measure.name)&&Double.isFinite(measure.value))posAssessment="true";
                            }
                            reason= nine.partNotAssessed==null?nine.shortLine():nine.partNotAssessed+": "+nine.partReason;
                            if(nine.status==Alpha99Findings.Status.NOT_ASSESSED)reason=nine.shortReason;
                        }
                    }
                }catch(Throwable exc){
                    status="ERROR";reason=exc.getClass().getSimpleName()+": "+exc.getMessage();
                }
                out.println(String.join(",",csv(id),csv(post),radius,rotation,rotAssessment,csv(status),posAssessment,csv(reason),sideWidth));
                System.out.println(id+" "+status+" rotation "+rotation+" assessed "+rotAssessment+" "+reason);
            }
        }
    }
}