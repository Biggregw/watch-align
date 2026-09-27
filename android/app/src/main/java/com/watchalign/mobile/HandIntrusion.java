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
        final boolean present; final double brightFraction;
        Result(boolean p,double f){present=p;brightFraction=f;}
    }

    private HandIntrusion(){}

    static Result measure(DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r,
                          GmtTwelveLandmarkAnalyzer.Geometry g){
        if(img==null||g==null||!(r>20))return new Result(false,Double.NaN);
        double ux=g.tick60[0]-cx,uy=g.tick60[1]-cy,un=Math.hypot(ux,uy);
        if(un<1e-9)return new Result(false,Double.NaN);
        ux/=un;uy/=un;
        double bx=(g.triLeft[0]+g.triRight[0]+g.triTip[0])/3,by=(g.triLeft[1]+g.triRight[1]+g.triTip[1])/3;
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
            double d=signedDistance(g,x,y);
            if(d<=-1.0)tri.add(v);                 // inside the triangle, off its edge
            else if(d>grow)ring.add(v);            // clearly outside it
        }
        if(ring.size()<50||tri.size()<10)return new Result(false,Double.NaN);
        double dial=percentile(ring,0.30),mark=percentile(tri,0.50);
        if(!(mark-dial>30))return new Result(false,Double.NaN);
        double level=dial+0.5*(mark-dial);
        int bright=0;for(double v:ring)if(v>level)bright++;
        double f=bright/(double)ring.size();
        return new Result(f>MAX_BRIGHT_FRACTION,f);
    }

    /** Distance outside the triangle (negative inside). */
    static double signedDistance(GmtTwelveLandmarkAnalyzer.Geometry g,double x,double y){
        double[][] p={g.triLeft,g.triRight,g.triTip};
        double cx=(p[0][0]+p[1][0]+p[2][0])/3,cy=(p[0][1]+p[1][1]+p[2][1])/3;
        double best=Double.NEGATIVE_INFINITY;
        for(int i=0;i<3;i++){
            double[] a=p[i],b=p[(i+1)%3];
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
