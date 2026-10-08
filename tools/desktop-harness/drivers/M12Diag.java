package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
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
 * RESEARCH DIAGNOSTIC (harness only): where does each side of the 12 triangle's edge sit?
 *
 * Uses the production pose (AutomaticDialOverlay.build), the production Report (ring model), and the production
 * private edge finder Alpha94MarkerMeasurement.edgeOnNormal on the production Sampler, both reached by reflection,
 * so no edge maths is copied. For each triangle side (apex-left, apex-right, base) it probes 21 points along the
 * MASTER side line (moved by the ring model, as the production local offset is) with the WIDE span and records the
 * signed distance of the detected edge from that line along the outward normal (+ = outside the master outline).
 * Also probes the 8 round markers' outlines the same way (radius direction) for comparison, and writes a rectified
 * canonical crop of the 12 region (x -0.20..0.20, y -1.00..-0.50 R) as a PNG.
 *
 * Usage: run.sh M12Diag <manifest.csv> <root> <out.csv> <crop_dir|-> [photo_id_substring_filter]
 */
public class M12Diag {
    static Method EDGE,AT;static Constructor<?> SAMPLER;
    static final double WIDE=0.035,MIN_STRENGTH_FRAC=0.35;

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Class<?> sc=Class.forName("com.watchalign.mobile.Alpha94MarkerMeasurement$Sampler");
        SAMPLER=sc.getDeclaredConstructor(Mat.class,double[].class);SAMPLER.setAccessible(true);
        AT=sc.getDeclaredMethod("at",double.class,double.class);AT.setAccessible(true);
        EDGE=Alpha94MarkerMeasurement.class.getDeclaredMethod("edgeOnNormal",sc,double.class,double.class,double.class,double.class,double.class);
        EDGE.setAccessible(true);
        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();if(h.equals("local_path")||h.equals("path"))iPath=i;if(h.equals("photo_id"))iId=i;}
        String filt=a.length>4?a[4]:"";
        try(PrintWriter out=new PrintWriter(new FileWriter(a[2]))){
            out.println("photo_id,status,R,ring_scale_pct,m12_usable,m12_local_radial_px,side_left_R,side_right_R,side_base_R,n_left,n_right,n_base,"
                    +"left_spread_R,right_spread_R,base_spread_R,round_edge_R,round_edge_n,lume_minus_dial,surround_minus_dial");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String rel=f[iPath],id=iId>=0?f[iId]:rel;
                if(!filt.isEmpty()&&!id.contains(filt))continue;
                File img=Path.of(a[1]).resolve(rel).toFile();
                if(!img.exists()){out.println(id+",missing");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(img.getPath());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
                if(q==null||!q.valid||q.homography==null){out.println(id+",pose_rejected");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography,HarnessModel.spec());
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                Object smp=SAMPLER.newInstance(gray,q.homography);
                double R=r.dialRadiusPx;double[] x=r.ring!=null&&r.ring.usable?r.ring.model:new double[]{0,0,0,0};
                // master triangle, moved by the ring model (translation, scale, rotation) like setLocal does
                double[][] tri={{0,-Alpha92GmtMaster.TRI_APEX_R},{Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R},
                        {-Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R}};
                for(double[] p:tri){double px=p[0],py=p[1];p[0]=px+x[0]+x[2]*px-x[3]*py;p[1]=py+x[1]+x[2]*py+x[3]*px;}
                double cx=(tri[0][0]+tri[1][0]+tri[2][0])/3,cy=(tri[0][1]+tri[1][1]+tri[2][1])/3;
                // sides: 0 = apex->base right (dial right), 1 = base, 2 = base left->apex (dial left)
                double[][] res=new double[3][];
                for(int i=0;i<3;i++){
                    double[] p=tri[i],qq=tri[(i+1)%3];double dx=qq[0]-p[0],dy=qq[1]-p[1],L=Math.hypot(dx,dy);dx/=L;dy/=L;
                    double nx=dy,ny=-dx;if(nx*((p[0]+qq[0])/2-cx)+ny*((p[1]+qq[1])/2-cy)<0){nx=-nx;ny=-ny;}
                    List<Double> t=new ArrayList<>();List<Double> s=new ArrayList<>();
                    for(int j=0;j<21;j++){double u=0.12+j*(0.76/20.0);double ox=p[0]+u*L*dx,oy=p[1]+u*L*dy;
                        double[] e=(double[])EDGE.invoke(null,smp,ox,oy,nx,ny,WIDE);
                        if(e!=null){t.add((e[0]-ox)*nx+(e[1]-oy)*ny);s.add(e[2]);}}
                    res[i]=strongMedian(t,s);
                }
                // round markers: edge distance from the master circle (ring-moved), outward radial normals
                List<Double> rt=new ArrayList<>();
                for(int h:HarnessModel.spec().roundHours()){
                    double ang=Math.toRadians(h*30.0),ex=Math.sin(ang),ey=-Math.cos(ang),c0x=ex*Alpha92GmtMaster.ROUND_CENTER_R,c0y=ey*Alpha92GmtMaster.ROUND_CENTER_R;
                    double mx=c0x+x[0]+x[2]*c0x-x[3]*c0y,my=c0y+x[1]+x[2]*c0y+x[3]*c0x,rr=Alpha92GmtMaster.ROUND_OUTER_R*(1+x[2]);
                    List<Double> t=new ArrayList<>(),s=new ArrayList<>();
                    for(int k=0;k<36;k++){double th=2*Math.PI*k/36,nx=Math.cos(th),ny=Math.sin(th);
                        double ox=mx+rr*nx,oy=my+rr*ny;double[] e=(double[])EDGE.invoke(null,smp,ox,oy,nx,ny,0.02);
                        if(e!=null){t.add((e[0]-ox)*nx+(e[1]-oy)*ny);s.add(e[2]);}}
                    double[] m=strongMedian(t,s);if(m[1]>=12)rt.add(m[0]);
                }
                // brightness: lume (inside triangle), polished surround band (just inside master outline), dial (outside)
                double lume=meanAt(smp,cx,cy,0.02),dial=meanAt(smp,cx+0.20,cy,0.015),sur=bandMean(smp,tri,cx,cy,-0.006);
                Alpha94MarkerMeasurement.Marker m=r.triangle;boolean mu=m!=null&&m.usable;
                double[] rtm=rt.isEmpty()?new double[]{Double.NaN}:rt.stream().mapToDouble(Double::doubleValue).sorted().toArray();
                out.println(String.join(",",id,"accepted",n(R),n(r.ring!=null?r.ring.scalePct:Double.NaN),Boolean.toString(mu),
                        n(mu?m.localRadialPx:Double.NaN),n(res[2][0]),n(res[0][0]),n(res[1][0]),
                        Integer.toString((int)res[2][1]),Integer.toString((int)res[0][1]),Integer.toString((int)res[1][1]),
                        n(res[2][2]),n(res[0][2]),n(res[1][2]),n(rtm[rtm.length/2]),Integer.toString(rt.size()),n(lume-dial),n(sur-dial)));
                out.flush();
                if(!a[3].equals("-"))crop(smp,a[3]+"/"+id+".png");
                System.err.println(li+" "+id);
            }
        }
    }

    static double[] strongMedian(List<Double> t,List<Double> s){
        if(t.isEmpty())return new double[]{Double.NaN,0,Double.NaN};
        double[] ss=s.stream().mapToDouble(Double::doubleValue).sorted().toArray();double med=ss[ss.length/2];
        List<Double> k=new ArrayList<>();for(int i=0;i<t.size();i++)if(s.get(i)>MIN_STRENGTH_FRAC*med)k.add(t.get(i));
        double[] v=k.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double mdn=v[v.length/2];double[] dev=Arrays.stream(v).map(z->Math.abs(z-mdn)).sorted().toArray();
        return new double[]{mdn,v.length,dev[dev.length/2]};
    }
    static double at(Object smp,double x,double y)throws Exception{return (double)AT.invoke(smp,x,y);}
    static double meanAt(Object smp,double x,double y,double rad)throws Exception{
        double s=0;int c=0;for(double dx=-rad;dx<=rad;dx+=rad/4)for(double dy=-rad;dy<=rad;dy+=rad/4){double v=at(smp,x+dx,y+dy);if(Double.isFinite(v)){s+=v;c++;}}
        return c==0?Double.NaN:s/c;
    }
    /** Mean over a band parallel to the master outline, offset d along the outward normal (negative = inside). */
    static double bandMean(Object smp,double[][] tri,double cx,double cy,double d)throws Exception{
        double s=0;int c=0;
        for(int i=0;i<3;i++){double[] p=tri[i],q=tri[(i+1)%3];double dx=q[0]-p[0],dy=q[1]-p[1],L=Math.hypot(dx,dy);dx/=L;dy/=L;
            double nx=dy,ny=-dx;if(nx*((p[0]+q[0])/2-cx)+ny*((p[1]+q[1])/2-cy)<0){nx=-nx;ny=-ny;}
            for(int j=0;j<21;j++){double u=0.15+j*0.035;double v=at(smp,p[0]+u*L*dx+d*nx,p[1]+u*L*dy+d*ny);if(Double.isFinite(v)){s+=v;c++;}}}
        return c==0?Double.NaN:s/c;
    }
    static void crop(Object smp,String path)throws Exception{
        int w=160,h=200;Mat m=new Mat(h,w,CvType.CV_8UC1);byte[] px=new byte[w*h];
        for(int yy=0;yy<h;yy++)for(int xx=0;xx<w;xx++){double v=at(smp,-0.20+xx*0.0025,-1.00+yy*0.0025);px[yy*w+xx]=(byte)Math.max(0,Math.min(255,Double.isFinite(v)?Math.round(v):0));}
        m.put(0,0,px);Imgcodecs.imwrite(path,m);
    }
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.6f",v):"";}
}
