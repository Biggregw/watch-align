package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH PROTOTYPE (harness only): 12-triangle sides from the INNER lume edge vs the OUTERMOST edge.
 *
 * Production pose and Report (ring model) are reused; image values come from the production Sampler (reflection).
 * Along the outward normal of each ring-moved master side (21 points, span +/-0.035 R, step 0.0005 R) the profile
 * gradient g = -dI/dt is taken; significant peaks are those >= 0.4 max (the production rule). OUTER = last peak
 * (mirrors the production 'outermost' choice), INNER = first peak (lume -> surround / dial transition). Per side: median
 * offset from the master line (+ = outside) and slope (angle error, deg, + = side rotated clockwise about its midpoint),
 * plus the inner-outer gap (surround width as seen) and how many of 21 points had two distinct peaks.
 *
 * Usage: run.sh M12Inner <manifest.csv> <root> <out.csv>
 */
public class M12Inner {
    static Method AT;static Constructor<?> SAMPLER;
    static final double SPAN=0.035,STEP=0.0005,FRAC=0.4;

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Class<?> sc=Class.forName("com.watchalign.mobile.Alpha94MarkerMeasurement$Sampler");
        SAMPLER=sc.getDeclaredConstructor(Mat.class,double[].class);SAMPLER.setAccessible(true);
        AT=sc.getDeclaredMethod("at",double.class,double.class);AT.setAccessible(true);
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();if(h.equals("local_path")||h.equals("path"))iPath=i;if(h.equals("photo_id"))iId=i;}
        try(PrintWriter out=new PrintWriter(new FileWriter(a[2]))){
            StringBuilder h=new StringBuilder("photo_id,status,R,m12_usable,m12_local_radial_px,m12_local_tangential_px");
            for(String s:new String[]{"right","base","left"})for(String k:new String[]{"outer_R","inner_R","outer_ang","inner_ang","gap_R","two_peaks"})h.append(",").append(s).append("_").append(k);
            out.println(h);
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String rel=f[iPath],id=iId>=0?f[iId]:rel;
                File img=Path.of(a[1]).resolve(rel).toFile();
                if(!img.exists()){out.println(id+",missing");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(img.getPath());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
                if(q==null||!q.valid||q.homography==null){out.println(id+",pose_rejected");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography,HarnessModel.spec());
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                Object smp=SAMPLER.newInstance(gray,q.homography);
                double[] x=r.ring!=null&&r.ring.usable?r.ring.model:new double[]{0,0,0,0};
                double[][] tri={{0,-Alpha92GmtMaster.TRI_APEX_R},{Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R},
                        {-Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R}};
                for(double[] p:tri){double px=p[0],py=p[1];p[0]=px+x[0]+x[2]*px-x[3]*py;p[1]=py+x[1]+x[2]*py+x[3]*px;}
                double cx=(tri[0][0]+tri[1][0]+tri[2][0])/3,cy=(tri[0][1]+tri[1][1]+tri[2][1])/3;
                Alpha94MarkerMeasurement.Marker m=r.triangle;boolean mu=m!=null&&m.usable;
                StringBuilder row=new StringBuilder(String.join(",",id,"accepted",n(r.dialRadiusPx),Boolean.toString(mu),
                        n(mu?m.localRadialPx:Double.NaN),n(mu?m.localTangentialPx:Double.NaN)));
                for(int i=0;i<3;i++){   // side 0 = apex->base right, 1 = base, 2 = base left->apex
                    double[] p=tri[i],qq=tri[(i+1)%3];double dx=qq[0]-p[0],dy=qq[1]-p[1],L=Math.hypot(dx,dy);dx/=L;dy/=L;
                    double nx=dy,ny=-dx;if(nx*((p[0]+qq[0])/2-cx)+ny*((p[1]+qq[1])/2-cy)<0){nx=-nx;ny=-ny;}
                    List<double[]> so=new ArrayList<>(),si=new ArrayList<>();List<Double> gaps=new ArrayList<>();int two=0;
                    for(int j=0;j<21;j++){double u=0.12+j*(0.76/20.0);double ox=p[0]+u*L*dx,oy=p[1]+u*L*dy;
                        double[] e=peaks(smp,ox,oy,nx,ny);if(e==null)continue;
                        double s=(u-0.5)*L;so.add(new double[]{s,e[1]});si.add(new double[]{s,e[0]});
                        if(e[1]-e[0]>2*STEP){two++;gaps.add(e[1]-e[0]);}}
                    double[] fo=fit(so),fi=fit(si);
                    row.append(",").append(n(fo[0])).append(",").append(n(fi[0])).append(",").append(n(fo[1])).append(",").append(n(fi[1]))
                       .append(",").append(n(gaps.isEmpty()?Double.NaN:med(gaps))).append(",").append(two);
                }
                out.println(row);out.flush();System.err.println(li+" "+id);
            }
        }
    }

    /** {inner t, outer t} of significant bright-to-dark peaks along +n, or null. */
    static double[] peaks(Object smp,double px,double py,double nx,double ny)throws Exception{
        int k=(int)Math.floor(2*SPAN/STEP+1e-9)+1;double[] v=new double[k];
        for(int i=0;i<k;i++){double t=-SPAN+i*STEP;v[i]=(double)AT.invoke(smp,px+t*nx,py+t*ny);if(!Double.isFinite(v[i]))return null;}
        double[] g=new double[k];g[0]=-(v[1]-v[0]);g[k-1]=-(v[k-1]-v[k-2]);for(int i=1;i<k-1;i++)g[i]=-(v[i+1]-v[i-1])*0.5;
        double gm=Double.NEGATIVE_INFINITY;for(double z:g)gm=Math.max(gm,z);if(!(gm>0))return null;
        int first=-1,last=-1;
        for(int i=1;i<k-1;i++)if(g[i]>=g[i-1]&&g[i]>=g[i+1]&&g[i]>=FRAC*gm){if(first<0)first=i;last=i;}
        if(first<0)return null;
        return new double[]{sub(g,first),sub(g,last)};
    }
    static double sub(double[] g,int b){double den=g[b-1]-2*g[b]+g[b+1];double o=den!=0?0.5*(g[b-1]-g[b+1])/den:0;return -SPAN+(b+o)*STEP;}
    /** Robust line fit offset = c + slope*s: median offset and Theil-Sen slope -> {median offset, angle deg}. */
    static double[] fit(List<double[]> pts){
        if(pts.size()<8)return new double[]{Double.NaN,Double.NaN};
        List<Double> off=new ArrayList<>(),sl=new ArrayList<>();for(double[] p:pts)off.add(p[1]);
        for(int i=0;i<pts.size();i++)for(int j=i+1;j<pts.size();j++){double ds=pts.get(j)[0]-pts.get(i)[0];if(Math.abs(ds)>1e-9)sl.add((pts.get(j)[1]-pts.get(i)[1])/ds);}
        return new double[]{med(off),Math.toDegrees(Math.atan(med(sl)))};
    }
    static double med(List<Double> v){double[] a=v.stream().mapToDouble(Double::doubleValue).sorted().toArray();return a.length==0?Double.NaN:a[a.length/2];}
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.6f",v):"";}
}
