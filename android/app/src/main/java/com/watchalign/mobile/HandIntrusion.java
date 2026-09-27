package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detects a hand lying next to the 12 marker, where it corrupts the triangle outline
 * and the 59/60/01 tick detection.
 *
 * Looks at a wedge around local 12 (±14° about the centre→60-tick direction, radius
 * 0.66R–0.92R: from just below the triangle tip to the minute track, clear of the crown
 * logo underneath, which is bright metal) excluding the triangle itself.
 * On a clean dial that area is dark. A hand (lume, polished steel, a GMT arrowhead)
 * is roughly as bright as the marker. If more than MAX_BRIGHT_FRACTION of the wedge is
 * brighter than half-way between the dial and the triangle, a hand is present.
 *
 * Field set (alpha51): hands beside the triangle produced 01-side "spacing" of
 * 0.41–0.61 and false STRONG alignment on three replica photos.
 */
final class HandIntrusion {
    static final double MAX_BRIGHT_FRACTION = 0.03;

    static final class Result {
        final boolean present; final double brightFraction; final boolean thinLine;
        Result(boolean p,double f){this(p,f,false);}
        Result(boolean p,double f,boolean line){present=p;brightFraction=f;thinLine=line;}
    }

    private HandIntrusion(){}

    static Result measure(DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r,
                          GmtTwelveLandmarkAnalyzer.Geometry g){
        if(g==null)return new Result(false,Double.NaN);
        return measure(img,w,h,cx,cy,r,new double[][]{g.triLeft,g.triRight,g.triTip},g.tick59,g.tick60,g.tick01);
    }

    /**
     * Any convex marker outline (the 12 triangle, the 6 baton) and the inner ends of the
     * three minute ticks at that marker (either order for the outer two).
     */
    static Result measure(DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r,
                          double[][] marker,double[] tickA,double[] tickCentre,double[] tickB){
        if(img==null||marker==null||tickCentre==null||!(r>20))return new Result(false,Double.NaN);
        double ux=tickCentre[0]-cx,uy=tickCentre[1]-cy,un=Math.hypot(ux,uy);
        if(un<1e-9)return new Result(false,Double.NaN);
        ux/=un;uy/=un;
        double grow=0.025*r;
        List<Double> ring=new ArrayList<>(),tri=new ArrayList<>();
        int x0=(int)Math.max(1,Math.floor(cx-r)),x1=(int)Math.min(w-2,Math.ceil(cx+r));
        int y0=(int)Math.max(1,Math.floor(cy-r)),y1=(int)Math.min(h-2,Math.ceil(cy+r));
        double cosLim=Math.cos(Math.toRadians(14));
        for(int y=y0;y<=y1;y++)for(int x=x0;x<=x1;x++){
            double dx=x-cx,dy=y-cy,rr=Math.hypot(dx,dy);
            if(rr<0.66*r||rr>0.92*r)continue;
            if((dx*ux+dy*uy)/rr<cosLim)continue;
            double v=img.at(x,y);
            double d=signedDistance(marker,x,y);
            if(d<=-1.0)tri.add(v);                 // inside the triangle, off its edge
            else if(d>grow)ring.add(v);            // clearly outside it
        }
        if(ring.size()<50||tri.size()<10)return new Result(false,Double.NaN);
        double dial=percentile(ring,0.30),mark=percentile(tri,0.50);
        if(!(mark-dial>30))return new Result(false,Double.NaN);
        double level=dial+0.5*(mark-dial);
        int bright=0;for(double v:ring)if(v>level)bright++;
        double f=bright/(double)ring.size();
        boolean line=thinRadialLine(img,w,h,cx,cy,r,marker,tickA,tickCentre,tickB,ux,uy,level);
        return new Result(f>MAX_BRIGHT_FRACTION||line,f,line);
    }

    /**
     * A thin hand (seconds hand, or a hand seen edge-on) covers too little of the wedge
     * to pass MAX_BRIGHT_FRACTION, but it is a narrow bright radial line with dark dial
     * either side. It matters most where it crosses the triangle: between the base and
     * the minute track it looks like an extra tick and corrupts the 59/60/01 frame.
     *
     * The wedge (outside the triangle, inside the minute-track circle) is split into 0.5 deg
     * bins around local 12. A hand is a run of at most MAX_LINE_BINS bins that are mostly
     * bright, with mostly dark bins on both sides. A base touching the minute track makes
     * every bin across the triangle bright, which is far wider than a hand, so it is not
     * mistaken for one.
     */
    static boolean thinRadialLine(DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r,
                                  double[][] marker,double[] tickA,double[] tickCentre,double[] tickB,
                                  double ux,double uy,double level){
        final double binDeg=0.5,half=14.0;int nb=(int)Math.round(2*half/binDeg);
        int[] n=new int[nb],br=new int[nb];
        // Stop a pixel short of the minute-track circle (the inner tick ends), so no tick
        // (58, 59, 01, 02...) ever enters a bin: each would look like a hand.
        double track=Math.hypot(tickCentre[0]-cx,tickCentre[1]-cy);
        for(double[] t:new double[][]{tickA,tickB})if(t!=null)track=Math.min(track,Math.hypot(t[0]-cx,t[1]-cy));
        double rMax=Math.min(0.95*r,track-1.0);
        // Sub-pixel sampling so that bins in the narrow gap strip get enough samples.
        double step=0.5;
        for(double y=Math.max(1,cy-r);y<=Math.min(h-2,cy+r);y+=step)for(double x=Math.max(1,cx-r);x<=Math.min(w-2,cx+r);x+=step){
            double dx=x-cx,dy=y-cy,rr=Math.hypot(dx,dy);
            if(rr<0.66*r||rr>rMax)continue;
            double along=dx*ux+dy*uy,across=dx*(-uy)+dy*ux;
            double phi=Math.toDegrees(Math.atan2(across,along));
            if(phi<-half||phi>=half)continue;
            if(signedDistance(marker,x,y)<=1.0)continue;
            int b=(int)((phi+half)/binDeg);if(b<0||b>=nb)continue;
            n[b]++;if(img.at(x,y)>level)br[b]++;
        }
        double[] f=new double[nb];
        for(int b=0;b<nb;b++)f[b]=n[b]>=MIN_BIN_SAMPLES?br[b]/(double)n[b]:Double.NaN;
        for(int b=0;b<nb;){
            if(!(f[b]>=LINE_BRIGHT)){b++;continue;}
            int e=b;while(e+1<nb&&(f[e+1]>=LINE_BRIGHT||Double.isNaN(f[e+1])))e++;
            while(e>b&&Double.isNaN(f[e]))e--;
            int width=e-b+1;
            double left=Double.NaN,right=Double.NaN;
            for(int k=b-1;k>=0;k--)if(!Double.isNaN(f[k])){left=f[k];break;}
            for(int k=e+1;k<nb;k++)if(!Double.isNaN(f[k])){right=f[k];break;}
            if(width<=MAX_LINE_BINS&&left<LINE_DARK&&right<LINE_DARK)return true;
            b=e+1;
        }
        return false;
    }

    static final int MAX_LINE_BINS = 8;          // 4 deg: much wider than any hand at the 12 marker
    static final double LINE_BRIGHT = 0.5, LINE_DARK = 0.2;
    private static final int MIN_BIN_SAMPLES = 6;

    /** Distance outside the triangle (negative inside). */
    static double signedDistance(GmtTwelveLandmarkAnalyzer.Geometry g,double x,double y){
        return signedDistance(new double[][]{g.triLeft,g.triRight,g.triTip},x,y);
    }

    /** Distance outside a convex polygon (negative inside). */
    static double signedDistance(double[][] p,double x,double y){
        double cx=0,cy=0;for(double[] q:p){cx+=q[0];cy+=q[1];}cx/=p.length;cy/=p.length;
        double best=Double.NEGATIVE_INFINITY;
        for(int i=0;i<p.length;i++){
            double[] a=p[i],b=p[(i+1)%p.length];
            double ex=b[0]-a[0],ey=b[1]-a[1],len=Math.hypot(ex,ey);
            if(len<1e-9)continue;
            double nx=ey/len,ny=-ex/len;
            if((cx-a[0])*nx+(cy-a[1])*ny>0){nx=-nx;ny=-ny;}  // outward
            best=Math.max(best,(x-a[0])*nx+(y-a[1])*ny);
        }
        return best;
    }

    private static double percentile(List<Double> l,double q){
        double[] a=new double[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);
        Arrays.sort(a);return a[(int)Math.round(q*(a.length-1))];
    }
}
