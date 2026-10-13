package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** REVIEW ONLY. Every baton and 12-triangle edge's independently fitted angle (Alpha94 sideAngDeg, deg from the
 * master side) with its coverage, the marker's rotation and the app's finding, through the unchanged pipeline.
 * Usage: run.sh EdgeAngleBatch manifest.csv image_root out.csv */
public class EdgeAngleBatch {
    public static void main(String[] a)throws Exception{
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] c=lines.get(0).split(",",-1);
        int ip=-1,iid=-1,iw=-1;for(int i=0;i<c.length;i++){if(c[i].equals("local_path"))ip=i;if(c[i].equals("photo_id"))iid=i;if(c[i].equals("physical_watch_id"))iw=i;}
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        try(PrintWriter out=new PrintWriter(Files.newBufferedWriter(Path.of(a[2])))){
            out.println("photo_id,physical_watch_id,model,hour,kind,usable,reason,interference_clean,status,dial_R_px,rotation_deg,a0,a1,a2,a3,cov0,cov1,cov2,cov3,tri_left,tri_right,tri_base");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);if(f.length<=Math.max(ip,iid))continue;
                Path file=Path.of(a[1]).resolve(f[ip]);if(!Files.exists(file))continue;String id=f[iid],w=iw>=0&&f.length>iw?f[iw]:id;
                try{
                    Bitmap b=Alpha96Calib.loadAlpha96(file.toString());Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,model,ref);
                    if(o==null||!o.ok()){out.println(id+","+w+","+model.id+",,,false,pose failed,,,,,,,,,,,,,,,");continue;}
                    for(ModelSpec.Marker sp:model.markers){
                        if(sp.shape==ModelSpec.Shape.ROUND)continue;
                        Alpha94MarkerMeasurement.Marker m=sp.shape==ModelSpec.Shape.TRIANGLE?o.measurement.triangle:o.measurement.atHour(sp.hour);
                        Alpha99Findings.Finding g=null;for(Alpha99Findings.Finding x:o.summary.all)if(x.hour==sp.hour&&!x.dialWide){g=x;break;}
                        Alpha99MarkerInterference.Check ch=o.checks==null?null:o.checks.get(sp.hour);
                        StringBuilder s=new StringBuilder();
                        s.append(String.format(Locale.US,"%s,%s,%s,%d,%s,%s,%s,%s,%s,%.1f,%s",id,w,model.id,sp.hour,sp.kind(),m!=null&&m.usable,
                                m==null?"no marker":(m.reason==null?"":m.reason.replace(',',';')),ch!=null&&ch.clean,g==null?"":g.status,o.measurement.dialRadiusPx,
                                m!=null&&m.usable?String.format(Locale.US,"%.4f",m.rotationDeg):""));
                        for(int i=0;i<4;i++)s.append(",").append(m!=null&&m.usable&&m.sideAngDeg!=null&&i<m.sideAngDeg.length?String.format(Locale.US,"%.4f",m.sideAngDeg[i]):"");
                        for(int i=0;i<4;i++)s.append(",").append(m!=null&&m.usable&&m.sideCov!=null&&i<m.sideCov.length?String.format(Locale.US,"%.2f",m.sideCov[i]):"");
                        boolean tri=m!=null&&m.usable&&sp.shape==ModelSpec.Shape.TRIANGLE;
                        s.append(tri?String.format(Locale.US,",%.4f,%.4f,%.4f",m.leftSideErrDeg,m.rightSideErrDeg,m.baseTiltDeg):",,,");
                        out.println(s);
                    }
                    out.flush();
                }catch(Throwable t){System.err.println("error "+id+": "+t);}
            }
        }
    }
}
