package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Offline validation using the EXACT production pose, fitted shapes and genuine model assets.
 * Usage: run.sh MarkerMismatchBatch manifest.csv image_root out.csv
 * Source photos are never uploaded as output. Only measured values are exported.
 */
public class MarkerMismatchBatch {
    public static void main(String[] args)throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(args[0]));
        String[] cols=lines.get(0).split(",",-1);
        int ip=index(cols,"local_path"),iid=index(cols,"photo_id"),iw=index(cols,"physical_watch_id");
        if(ip<0)ip=index(cols,"path");
        if(ip<0)throw new IllegalArgumentException("manifest must have local_path");
        ModelSpec model=HarnessModel.spec();
        ModelReference ref=HarnessModel.ref();
        try(PrintWriter csv=new PrintWriter(Files.newBufferedWriter(Path.of(args[2])))){
            csv.println("photo_id,physical_watch_id,model,model_shape,hour,pose_status,reason,dial_R_px,photo_width,photo_height,polygon_usable,confidence,extra_pct,missing_pct,mismatch_pct,expected_area,actual_area,overlap_area,rotation_deg,position_R,radial_R,side_agreement_px,expected_vertices,actual_vertices");
            int n=0;
            for(int li=1;li<lines.size();li++){
                String[] row=lines.get(li).split(",",-1);
                if(row.length<=ip)continue;
                String id=iid>=0?row[iid]:"photo_"+li,watch=iw>=0?row[iw]:id;
                Path file=Path.of(args[1]).resolve(row[ip]);
                if(!Files.exists(file))continue;
                try{
                    Bitmap bitmap=Alpha96Calib.loadAlpha96(file.toString());
                    if(bitmap==null)continue;
                    Alpha99Pipeline.Output out=Alpha99Pipeline.run(bitmap,model,ref);
                    n++;
                    if(n%10==0)System.err.printf(Locale.US,"processed %d: %s%n",n,id);
                    if(out==null||!out.ok()){
                        csv.println(safe(id)+","+safe(watch)+","+model.id+",,,pose_failed,"+
                                safe(out==null||out.pose==null?"no pose":out.pose.reason)+
                                ",,,,,,,,,,,,,,,,,");
                        continue;
                    }
                    Alpha94MarkerMeasurement.Report mr=out.measurement;
                    for(ModelSpec.Marker sp:model.markers){
                        Alpha94MarkerMeasurement.Marker m=sp.shape==ModelSpec.Shape.TRIANGLE?mr.triangle:mr.atHour(sp.hour);
                        Alpha99Findings.Finding f=null;
                        for(Alpha99Findings.Finding x:out.summary.all)if(x.hour==sp.hour && !x.dialWide){f=x;break;}
                        Alpha99MarkerInterference.Check interference=out.checks==null?null:out.checks.get(sp.hour);
                        MarkerMismatchResearch.Result q=MarkerMismatchResearch.score(mr,sp,ref,f,interference);
                        MarkerPolygonMismatch.Score s=q.score;
                        double[] ring=mr.ring==null?null:mr.ring.model;
                        double[][] expected=expected(sp,ring,ref),actual=actual(sp,m,mr.dialRadiusPx);
                        double angle=m!=null&&m.usable?m.rotationDeg:Double.NaN;
                        double pos=m!=null&&m.usable?m.localOffsetPx/mr.dialRadiusPx:Double.NaN;
                        double rad=m!=null&&m.usable?m.localRadialPx/mr.dialRadiusPx:Double.NaN;
                        double agreement=m!=null&&m.sideOffR!=null&&m.sideOffR.length>2?
                                (m.sideOffR[0]+m.sideOffR[2])*mr.dialRadiusPx:Double.NaN;
                        csv.printf(Locale.US,"%s,%s,%s,%s,%d,accepted,%s,%.3f,%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                safe(id),safe(watch),model.id,sp.kind(),sp.hour,
                                safe(m==null?"no marker":m.reason),mr.dialRadiusPx,bitmap.getWidth(),bitmap.getHeight(),
                                m!=null&&m.usable,safe(q.reason),
                                num(s==null?Double.NaN:s.extraPct),num(s==null?Double.NaN:s.missingPct),
                                num(s==null?Double.NaN:s.mismatchPct),
                                num(s==null?Double.NaN:s.expectedArea),num(s==null?Double.NaN:s.actualArea),
                                num(s==null?Double.NaN:s.overlapArea),
                                num(angle),num(pos),num(rad),num(agreement),
                                safe(vertices(expected)),safe(vertices(actual)));
                    }
                    csv.flush();
                }catch(Throwable t){
                    System.err.println("error "+id+": "+t.getClass().getSimpleName()+" "+t.getMessage());
                }
            }
        }
    }

    static double[][] expected(ModelSpec.Marker sp,double[] ring,ModelReference ref){
        if(ring==null||ring.length<4)return null;
        if(sp.shape==ModelSpec.Shape.TRIANGLE)
            return Alpha98Closeups.moved(Alpha98Closeups.nominalTriangle(sp,ref),ring);
        if(sp.shape==ModelSpec.Shape.BATON)
            return Alpha98Closeups.moved(sp.batonPolygon(),ring);
        double[] master=sp.masterPoint();
        double x=master[0]+ring[0]+ring[2]*master[0]-ring[3]*master[1];
        double y=master[1]+ring[1]+ring[2]*master[1]+ring[3]*master[0];
        return MarkerPolygonMismatch.circle(x,y,sp.outerR*(1+ring[2]),128);
    }

    static double[][] actual(ModelSpec.Marker sp,Alpha94MarkerMeasurement.Marker m,double R){
        if(m==null||!m.usable)return null;
        if(sp.shape!=ModelSpec.Shape.ROUND)return m.fitVertices;
        if(!Double.isFinite(m.canonDx)||!Double.isFinite(m.canonDy)
           ||!Double.isFinite(m.radiusErrPx)||!(R>0))return null;
        double r=sp.outerR+m.radiusErrPx/R;
        if(!(r>0))return null;
        double[] master=sp.masterPoint();
        return MarkerPolygonMismatch.circle(master[0]+m.canonDx,master[1]+m.canonDy,r,128);
    }

    static String vertices(double[][] v){
        if(v==null)return "";
        if(v.length>6)return ""; // rounds have center/radius, no unwieldy 128-point row
        StringBuilder s=new StringBuilder();
        for(double[] p:v){if(s.length()>0)s.append(';');
            s.append(String.format(Locale.US,"%.8f:%.8f",p[0],p[1]));}
        return s.toString();
    }
    static int index(String[] a,String value){return Arrays.asList(a).indexOf(value);}
    static String num(double n){return Double.isFinite(n)?String.format(Locale.US,"%.8f",n):"";}
    static String safe(String s){return s==null?"":s.replace(',',';').replace('\r',' ').replace('\n',' ');}
}
