package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Research (new-model master): runs the app's frozen pose and marker measurement for the harness model on every photo
 * and writes, model-generically, one row per marker (long format) plus one row per photo for the minute track:
 *
 *   <out>_markers.csv  photo_id, status, dial_radius_px, tick_rms_px, ticks_used, tick_sectors, hour, kind, usable, reason,
 *                      x, y (measured centre, canonical: dial radius 1, 12 at the top), r, angle_deg (clockwise from 12),
 *                      rotation_deg, radius_err_R (rounds: fitted outer radius minus the spec's), edge_support, outline_integrity
 *   <out>_track.csv    photo_id, status, dial_radius_px, tick_rms_px, tick_inner_r, tick_outer_r, tick_contrast:
 *                      median grey profile on the 60 minute-tick angles minus the profile half-way between ticks, along r;
 *                      inner / outer r = where that difference crosses half its peak (the printed ticks' radial extent).
 *
 * Nothing here feeds back into pose or measurement; it only reads what the app computes for the spec it is given.
 * Usage: JAVA_TOOL_OPTIONS="-Dwatchalign.model=<id> -Dwatchalign.models_root=<dir>" run.sh SubMaster <manifest.csv> <root> <out_prefix>
 */
public class SubMaster {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        ModelSpec spec=HarnessModel.spec();
        try(PrintWriter mk=new PrintWriter(new FileWriter(a[2]+"_markers.csv"));PrintWriter tr=new PrintWriter(new FileWriter(a[2]+"_track.csv"))){
            mk.println("photo_id,status,dial_radius_px,tick_rms_px,ticks_used,tick_sectors,hour,kind,usable,reason,x,y,r,angle_deg,rotation_deg,radius_err_R,edge_support,outline_integrity");
            tr.println("photo_id,status,dial_radius_px,tick_rms_px,ticks_used,tick_sectors,tick_inner_r,tick_outer_r,tick_contrast");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                Path p=Path.of(a[1]).resolve(f[iPath]);
                if(!Files.exists(p)){tr.println(id+",missing,,,,,,,");continue;}
                try{
                    Bitmap b=Alpha96Calib.loadAlpha96(p.toString());
                    AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,spec);
                    if(q==null||!q.valid||q.homography==null){tr.println(id+",pose_rejected,,,,,,,\""+(q==null?"":q.reason.replace('"','\''))+"\"");continue;}
                    double[] H=q.homography;double R=Alpha99MarkerInterference.pxPerR(H);
                    String pose=String.format(Locale.US,"%s,accepted,%.1f,%.3f,%d,%d",id,R,q.fitAfter,q.detectedTicks,q.completePairs);
                    Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                    double[] t=track(gray,H);
                    tr.println(pose+String.format(Locale.US,",%.4f,%.4f,%.1f",t[0],t[1],t[2]));
                    Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,H,spec);
                    java.util.List<Alpha94MarkerMeasurement.Marker> all=new java.util.ArrayList<>(r.markers);if(r.triangle!=null)all.add(0,r.triangle);
                    for(Alpha94MarkerMeasurement.Marker m:all){
                        double[] mp=m.spec==null?null:m.spec.masterPoint();
                        double x=Double.NaN,y=Double.NaN,rr=Double.NaN,ang=Double.NaN;
                        if(m.usable&&mp!=null&&Double.isFinite(m.canonDx)){x=mp[0]+m.canonDx;y=mp[1]+m.canonDy;rr=Math.hypot(x,y);
                            ang=Math.toDegrees(Math.atan2(x,-y));if(ang<0)ang+=360;}
                        mk.println(pose+String.format(Locale.US,",%d,%s,%b,\"%s\",%s,%s,%s,%s,%s,%s,%s,%s",m.hour,m.kind,m.usable,m.reason==null?"":m.reason.replace('"','\''),
                                n(x),n(y),n(rr),n(ang),n(m.usable?m.rotationDeg:Double.NaN),n(m.usable?m.radiusErrPx/R:Double.NaN),n(m.fitSupport),
                                Double.isFinite(m.fitScorePx)?n(1-m.fitScorePx):""));
                    }
                    rgba.release();gray.release();
                }catch(Throwable e){tr.println(id+",error,,,,,,,\""+e.getClass().getSimpleName()+"\"");}
                mk.flush();tr.flush();
                System.err.println(li+"/"+(lines.size()-1)+" "+id);
            }
        }
    }

    /** Printed minute ticks' radial extent: {inner r, outer r, peak contrast in grey levels}. */
    static double[] track(Mat gray,double[] H){
        double r0=0.82,r1=1.03,dr=0.002;int nR=(int)Math.round((r1-r0)/dr)+1;
        double[] on=new double[nR],off=new double[nR];
        double[] buf=new double[60],bufOff=new double[60];
        for(int i=0;i<nR;i++){double r=r0+i*dr;int n=0,m=0;
            for(int k=0;k<60;k++){
                if(k%5==0)continue;                              // hour positions: markers / printing nearby
                double a1=Math.toRadians(k*6.0),a2=Math.toRadians(k*6.0+3.0);
                double v1=sample(gray,H,r*Math.sin(a1),-r*Math.cos(a1)),v2=sample(gray,H,r*Math.sin(a2),-r*Math.cos(a2));
                if(Double.isFinite(v1))buf[n++]=v1;if(Double.isFinite(v2))bufOff[m++]=v2;}
            on[i]=n>10?median(Arrays.copyOf(buf,n)):Double.NaN;off[i]=m>10?median(Arrays.copyOf(bufOff,m)):Double.NaN;}
        double[] d=new double[nR];double peak=0;int ip=-1;
        for(int i=0;i<nR;i++){d[i]=Math.abs(on[i]-off[i]);if(Double.isFinite(d[i])&&d[i]>peak){peak=d[i];ip=i;}}
        if(ip<0)return new double[]{Double.NaN,Double.NaN,Double.NaN};
        int lo=ip,hi=ip;while(lo>0&&d[lo-1]>=0.5*peak)lo--;while(hi<nR-1&&d[hi+1]>=0.5*peak)hi++;
        return new double[]{r0+(lo-0.5)*dr,r0+(hi+0.5)*dr,peak};
    }

    static double sample(Mat gray,double[] H,double x,double y){
        double q=H[6]*x+H[7]*y+H[8];if(Math.abs(q)<1e-12)return Double.NaN;
        double px=(H[0]*x+H[1]*y+H[2])/q,py=(H[3]*x+H[4]*y+H[5])/q;
        int x0=(int)Math.floor(px),y0=(int)Math.floor(py);
        if(x0<0||y0<0||x0+1>=gray.cols()||y0+1>=gray.rows())return Double.NaN;
        double fx=px-x0,fy=py-y0;
        double v00=gray.get(y0,x0)[0],v10=gray.get(y0,x0+1)[0],v01=gray.get(y0+1,x0)[0],v11=gray.get(y0+1,x0+1)[0];
        return (1-fy)*((1-fx)*v00+fx*v10)+fy*((1-fx)*v01+fx*v11);
    }
    static double median(double[] v){Arrays.sort(v);int n=v.length;return n%2==1?v[n/2]:0.5*(v[n/2-1]+v[n/2]);}
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.5f",v):"";}
}
