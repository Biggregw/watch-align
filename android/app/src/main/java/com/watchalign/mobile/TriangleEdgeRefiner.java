package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Re-measures the 12 triangle's three sides on the outer edge of the applied
 * surround and returns the corners where the fitted side lines meet.
 *
 * Why this exists: the triangle is found as a thresholded contour whose convex hull
 * is reduced to three vertices. Where the white-gold surround is dimmer, or a dark
 * groove separates it from the lume, the contour drops inside onto the lume boundary
 * for part of a side. The hull vertex then lands several pixels inside the real
 * corner. One bad corner tilts the base (fake top-edge angle), swings the marker
 * axis and makes one 59/01 side gap look larger; together those read as a STRONG
 * rotation on a genuine watch (field report, alpha43).
 *
 * Each side is sampled along its middle section. On each sample's outward normal the
 * edge is the OUTERMOST half-level crossing between the local marker brightness and
 * the dark dial just outside, the same edge a global threshold finds on a clean
 * marker, so the clearance definition the calibration used does not shift. Taking the
 * outermost crossing means an inner groove cannot pull the edge in. The base search
 * stops short of the 59/01 minute ticks. Straight lines are fitted with outlier
 * trimming and intersected.
 *
 * Pure Java, so it can be unit tested without the native OpenCV library.
 */
final class TriangleEdgeRefiner {
    static final int SAMPLES = 30;
    private static final double MIN_SPAN = 40.0;   // marker-to-dial contrast, grey levels

    private TriangleEdgeRefiner(){}

    /**
     * @param left,right,tip rough corners {x,y}; left/right are the base corners
     * @param tickA,tickB 59 and 01 minute-tick inner points bounding the base search, or null
     * @param dialR dial radius in px
     * @return refined {left, right, tip}, or null to keep the rough corners
     */
    static double[][] refine(DialEdgeEllipseFit.Intensity img,int w,int h,
                             double[] left,double[] right,double[] tip,
                             double[] tickA,double[] tickB,double dialR){
        if(img==null||!(dialR>20))return null;
        double gx=(left[0]+right[0]+tip[0])/3.0, gy=(left[1]+right[1]+tip[1])/3.0;
        double[] base=fitSide(img,w,h,left,right,gx,gy,dialR,0.12,0.88,0.045,tickA,tickB);
        double[] rs=fitSide(img,w,h,right,tip,gx,gy,dialR,0.10,0.75,0.035,null,null);
        double[] ls=fitSide(img,w,h,tip,left,gx,gy,dialR,0.25,0.90,0.035,null,null);
        if(base==null||rs==null||ls==null)return null;
        double[] l=intersect(ls,base), r=intersect(base,rs), t=intersect(rs,ls);
        if(l==null||r==null||t==null)return null;
        double lim=0.06*dialR;
        if(Math.hypot(l[0]-left[0],l[1]-left[1])>lim)return null;
        if(Math.hypot(r[0]-right[0],r[1]-right[1])>lim)return null;
        if(Math.hypot(t[0]-tip[0],t[1]-tip[1])>lim)return null;
        return new double[][]{l,r,t};
    }

    /** Line {px, py, dx, dy} through the outer edge of side a->b, or null. */
    static double[] fitSide(DialEdgeEllipseFit.Intensity img,int w,int h,double[] a,double[] b,
                            double gx,double gy,double dialR,double t0,double t1,double outFrac,
                            double[] stopA,double[] stopB){
        double ux=b[0]-a[0], uy=b[1]-a[1], len=Math.hypot(ux,uy);
        if(len<4)return null;
        ux/=len;uy/=len;
        double nx=-uy, ny=ux;
        double mx=(a[0]+b[0])/2, my=(a[1]+b[1])/2;
        if((mx-gx)*nx+(my-gy)*ny<0){nx=-nx;ny=-ny;}          // outward, away from the centroid
        double step=0.25, din=0.05*dialR;
        List<double[]> pts=new ArrayList<>();
        for(int k=0;k<SAMPLES;k++){
            double t=t0+(t1-t0)*k/(SAMPLES-1.0);
            double px=a[0]+(b[0]-a[0])*t, py=a[1]+(b[1]-a[1])*t;
            double dout=outFrac*dialR;
            if(stopA!=null&&stopB!=null){
                // Distance along the normal to the 59-01 tick line, less a pixel of margin.
                double lx=stopB[0]-stopA[0],ly=stopB[1]-stopA[1];
                double den=nx*(-ly)+ny*lx;
                if(Math.abs(den)>1e-9){
                    double s=((stopA[0]-px)*(-ly)+(stopA[1]-py)*lx)/den;
                    if(s>0)dout=Math.min(dout,s-1.0);
                }
            }
            if(dout<1.5)continue;
            int n=(int)Math.floor((din+dout)/step)+1;
            double[] v=new double[n];
            boolean ok=true;
            for(int i=0;i<n;i++){
                double s=-din+i*step, x=px+nx*s, y=py+ny*s;
                if(x<1||y<1||x>w-2||y>h-2){ok=false;break;}
                v[i]=img.at(x,y);
            }
            if(!ok)continue;
            double peak=Double.NEGATIVE_INFINITY;
            for(int i=0;i<n;i++)peak=Math.max(peak,v[i]);
            // Dark dial level: just outside, where the scan ends.
            int tail=Math.max(3,(int)Math.round(1.0/step));
            double bg=Double.POSITIVE_INFINITY;
            for(int i=n-tail;i<n;i++)bg=Math.min(bg,v[i]);
            if(!(peak-bg>=MIN_SPAN))continue;
            double level=0.5*(peak+bg);
            int cross=-1;
            for(int i=0;i<n-1;i++)if(v[i]>=level&&v[i+1]<level)cross=i;   // outermost crossing
            if(cross<0)continue;
            double frac=(v[cross]-level)/Math.max(1e-9,v[cross]-v[cross+1]);
            double s=-din+(cross+frac)*step;
            pts.add(new double[]{px+nx*s,py+ny*s});
        }
        if(pts.size()<0.6*SAMPLES)return null;
        double[] line=robustLine(pts,dialR);
        if(line==null)return null;
        // A fitted side must stay close to the rough side's direction.
        if(Math.abs(line[2]*ux+line[3]*uy)<Math.cos(Math.toRadians(12)))return null;
        return line;
    }

    /** PCA line with MAD trimming. Returns {px, py, dx, dy} or null. */
    static double[] robustLine(List<double[]> pts,double dialR){
        if(pts.size()<5)return null;
        boolean[] keep=new boolean[pts.size()];Arrays.fill(keep,true);
        double[] line=null;
        for(int iter=0;iter<3;iter++){
            line=pcaLine(pts,keep);
            if(line==null)return null;
            double[] res=new double[pts.size()];
            for(int i=0;i<res.length;i++)res[i]=Math.abs(dist(line,pts.get(i)));
            double[] sorted=res.clone();Arrays.sort(sorted);
            double lim=Math.max(0.75,3.0*1.4826*sorted[sorted.length/2]);
            int used=0;
            for(int i=0;i<res.length;i++){keep[i]=res[i]<=lim;if(keep[i])used++;}
            if(used<5||used<0.6*pts.size())return null;
        }
        line=pcaLine(pts,keep);
        if(line==null)return null;
        double ss=0;int used=0;
        for(int i=0;i<pts.size();i++)if(keep[i]){double d=dist(line,pts.get(i));ss+=d*d;used++;}
        if(!(Math.sqrt(ss/used)<=Math.max(1.0,0.006*dialR)))return null;
        return line;
    }

    private static double[] pcaLine(List<double[]> pts,boolean[] keep){
        double sx=0,sy=0;int n=0;
        for(int i=0;i<pts.size();i++)if(keep[i]){sx+=pts.get(i)[0];sy+=pts.get(i)[1];n++;}
        if(n<3)return null;
        double mx=sx/n,my=sy/n,cxx=0,cxy=0,cyy=0;
        for(int i=0;i<pts.size();i++)if(keep[i]){
            double dx=pts.get(i)[0]-mx,dy=pts.get(i)[1]-my;cxx+=dx*dx;cxy+=dx*dy;cyy+=dy*dy;
        }
        double th=0.5*Math.atan2(2*cxy,cxx-cyy);
        return new double[]{mx,my,Math.cos(th),Math.sin(th)};
    }

    private static double dist(double[] l,double[] p){
        return (p[0]-l[0])*(-l[3])+(p[1]-l[1])*l[2];
    }

    static double[] intersect(double[] a,double[] b){
        double det=a[2]*(-b[3])-a[3]*(-b[2]);
        if(Math.abs(det)<1e-9)return null;
        double rx=b[0]-a[0],ry=b[1]-a[1];
        double s=(rx*(-b[3])-ry*(-b[2]))/det;
        return new double[]{a[0]+a[2]*s,a[1]+a[3]*s};
    }
}
