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
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH ONLY: 12-triangle lateral-position experiment.
 *
 * Compares Alpha97's current polygon-centroid lateral position with a base-independent
 * symmetry position made from ONLY the two long triangle sides. The two production-style
 * side lines are fitted with the production private edge finder / strong-edge filter /
 * RANSAC line fitter, reached by reflection. Their common marker-ring similarity transform
 * is removed, then the midpoint between the two lines is measured at the fixed Alpha92
 * triangle area-centroid radius. Equal inward/outward movement of both side edges cancels.
 *
 * No app code, master geometry, pose, limits or verdicts are changed.
 *
 * Usage:
 *   run.sh M12AxisPosition <manifest.csv> <root> <out.csv>
 */
public final class M12AxisPosition {
    static Method EDGE, STRONG, RANSAC;
    static Constructor<?> SAMPLER;
    static final double WIDE=0.035, NARROW=0.012;

    static final class Line {
        double[] p,d;
        double coverage;
        Line(double[] p,double[] d,double coverage){this.p=p;this.d=d;this.coverage=coverage;}
    }

    public static void main(String[] a) throws Exception {
        if(a.length<3) throw new IllegalArgumentException("manifest root out.csv");
        nu.pattern.OpenCV.loadLocally();
        Class<?> sc=Class.forName("com.watchalign.mobile.Alpha94MarkerMeasurement$Sampler");
        SAMPLER=sc.getDeclaredConstructor(Mat.class,double[].class); SAMPLER.setAccessible(true);
        EDGE=Alpha94MarkerMeasurement.class.getDeclaredMethod("edgeOnNormal",sc,double.class,double.class,double.class,double.class,double.class);
        EDGE.setAccessible(true);
        STRONG=Alpha94MarkerMeasurement.class.getDeclaredMethod("strong",List.class); STRONG.setAccessible(true);
        RANSAC=Alpha94MarkerMeasurement.class.getDeclaredMethod("ransacLine",List.class,double.class); RANSAC.setAccessible(true);

        List<String> lines=Files.readAllLines(Path.of(a[0]));
        String[] hdr=lines.get(0).split(",",-1); int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){
            String h=hdr[i].trim();
            if(h.equals("local_path")||h.equals("path")) iPath=i;
            if(h.equals("photo_id")) iId=i;
        }
        if(iPath<0) throw new IllegalArgumentException("manifest needs local_path/path");

        try(PrintWriter out=new PrintWriter(new FileWriter(a[2]))){
            out.println("photo_id,status,R,current_12_usable,current_lateral_R,axis_usable,axis_lateral_R,left_x_R,right_x_R,left_coverage,right_coverage");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);
                if(f.length<=iPath) continue;
                String rel=f[iPath], id=iId>=0&&iId<f.length?f[iId]:rel;
                File img=Path.of(a[1]).resolve(rel).toFile();
                if(!img.exists()){out.println(id+",missing");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(img.getPath());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b);
                if(q==null||!q.valid||q.homography==null){out.println(id+",pose_rejected");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography);
                double R=r.dialRadiusPx;
                Alpha94MarkerMeasurement.Marker tri=r.triangle;
                boolean currentUsable=tri!=null&&tri.usable&&r.ring!=null&&r.ring.usable&&r.ring.model!=null
                        &&Double.isFinite(tri.localTangentialPx)&&R>0;
                double current=currentUsable?tri.localTangentialPx/R:Double.NaN;

                if(!currentUsable){
                    out.println(String.join(",",id,"accepted",n(R),"false",n(current),"false","","","","",""));
                    continue;
                }

                Mat rgba=new Mat(),gray=new Mat();
                Object smp=null;
                try{
                    Utils.bitmapToMat(b,rgba); Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                    smp=SAMPLER.newInstance(gray,q.homography);
                    double tol=0.5/R;
                    Line right=fitLongSide(smp,0,tol); // apex -> base right
                    Line left =fitLongSide(smp,2,tol); // base left -> apex
                    if(left==null||right==null){
                        out.println(String.join(",",id,"accepted",n(R),"true",n(current),"false","","","",
                                left==null?"":n(left.coverage),right==null?"":n(right.coverage)));
                        continue;
                    }
                    Line ln=unring(left,r.ring.model), rn=unring(right,r.ring.model);
                    double y0=-Alpha92GmtMaster.TRI_AREA_CENTROID_R;
                    double xl=xAtY(ln,y0), xr=xAtY(rn,y0);
                    if(!Double.isFinite(xl)||!Double.isFinite(xr)){
                        out.println(String.join(",",id,"accepted",n(R),"true",n(current),"false","","","",n(left.coverage),n(right.coverage)));
                        continue;
                    }
                    double axis=0.5*(xl+xr); // master symmetry axis is x=0
                    out.println(String.join(",",id,"accepted",n(R),"true",n(current),"true",n(axis),n(xl),n(xr),n(left.coverage),n(right.coverage)));
                }finally{ gray.release(); rgba.release(); }
                out.flush();
            }
        }
    }

    /** Exact two-pass production side fit, restricted to one triangle side. */
    @SuppressWarnings("unchecked")
    static Line fitLongSide(Object smp,int side,double tol)throws Exception{
        double[][] poly={{0,-Alpha92GmtMaster.TRI_APEX_R},
                {Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R},
                {-Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R}};
        double pcx=0,pcy=0; for(double[] v:poly){pcx+=v[0]/3;pcy+=v[1]/3;}
        double[] a=poly[side], q=poly[(side+1)%3];
        double L=Math.hypot(q[0]-a[0],q[1]-a[1]);
        double[] dm={(q[0]-a[0])/L,(q[1]-a[1])/L};
        double[] nm={dm[1],-dm[0]};
        if(nm[0]*((a[0]+q[0])/2-pcx)+nm[1]*((a[1]+q[1])/2-pcy)<0){nm[0]=-nm[0];nm[1]=-nm[1];}
        double[] lp=a.clone(), ld=dm.clone(); double cov=0;
        for(double span:new double[]{WIDE,NARROW}){
            double[] n={ld[1],-ld[0]};
            if(n[0]*nm[0]+n[1]*nm[1]<0){n[0]=-n[0];n[1]=-n[1];}
            List<double[]> raw=new ArrayList<>();
            for(int j=0;j<21;j++){
                double u=0.12+j*(0.76/20.0);
                double qx=a[0]+u*L*dm[0],qy=a[1]+u*L*dm[1];
                double t=(qx-lp[0])*ld[0]+(qy-lp[1])*ld[1];
                double[] e=(double[])EDGE.invoke(null,smp,lp[0]+t*ld[0],lp[1]+t*ld[1],n[0],n[1],span);
                if(e!=null) raw.add(e);
            }
            List<double[]> P=(List<double[]>)STRONG.invoke(null,raw);
            double[][] fit=P.size()>=4?(double[][])RANSAC.invoke(null,P,tol):null;
            if(fit==null||fit.length<3||fit[2][0]<5) return null;
            double[] d=fit[1].clone();
            if(d[0]*dm[0]+d[1]*dm[1]<0){d[0]=-d[0];d[1]=-d[1];}
            lp=fit[0].clone(); ld=d; cov=fit[2][0]/21.0;
        }
        if(cov<0.50) return null; // same production side-coverage gate
        return new Line(lp,ld,cov);
    }

    /** Bring a measured line back through the inverse common ring similarity transform. */
    static Line unring(Line l,double[] m){
        double tx=m[0],ty=m[1],s=m[2],rot=m[3];
        double a=1+s,b=-rot,c=rot,d=1+s,det=a*d-b*c;
        if(Math.abs(det)<1e-12) return null;
        double ia=d/det,ib=-b/det,ic=-c/det,id=a/det;
        double px=ia*(l.p[0]-tx)+ib*(l.p[1]-ty), py=ic*(l.p[0]-tx)+id*(l.p[1]-ty);
        double dx=ia*l.d[0]+ib*l.d[1],dy=ic*l.d[0]+id*l.d[1],L=Math.hypot(dx,dy);
        if(!(L>0)) return null;
        return new Line(new double[]{px,py},new double[]{dx/L,dy/L},l.coverage);
    }

    static double xAtY(Line l,double y){
        if(l==null||Math.abs(l.d[1])<1e-12) return Double.NaN;
        double u=(y-l.p[1])/l.d[1];
        return l.p[0]+u*l.d[0];
    }
    static String n(double v){return Double.isFinite(v)?String.format(Locale.US,"%.9f",v):"";}
}
