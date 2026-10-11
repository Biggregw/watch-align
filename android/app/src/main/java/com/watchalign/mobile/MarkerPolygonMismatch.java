package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Exact area comparison of two CONVEX filled marker boundaries in the same
 * canonical, perspective-corrected dial plane. Mismatch is 100 times the
 * symmetric difference divided by the FILLED EXPECTED area, including metal.
 * It can exceed 100%; it is not an IoU score or a severity classification.
 *
 * Unlike raw raster counts, this is independent of photo resolution; it is
 * the limit of counting extra and missing pixels on a fine canonical grid.
 * No measurement tolerance, genuine calibration or QC status is assigned.
 */
final class MarkerPolygonMismatch {
    private static final double EPS=1.0e-12;
    private MarkerPolygonMismatch() {}

    static final class Score {
        /** Snapshots of the exact polygons whose areas were compared; renderers never refit them. */
        final double[][] expectedPolygon,actualPolygon;
        final double expectedArea,actualArea,overlapArea,unionArea;
        final double extraArea,missingArea,extraPct,missingPct,mismatchPct;
        Score(double e,double a,double i,double[][] expected,double[][] actual) {
            expectedPolygon=expected;actualPolygon=actual;
            expectedArea=e;actualArea=a;overlapArea=i;
            unionArea=e+a-i;
            extraArea=a-i;missingArea=e-i;
            extraPct=100.0*extraArea/e;
            missingPct=100.0*missingArea/e;
            mismatchPct=extraPct+missingPct;
        }
        /** Disjoint convex pieces of A minus E and E minus A, in the same canonical plane. */
        List<double[][]> extraRegions(){return difference(actualPolygon,expectedPolygon);}
        List<double[][]> missingRegions(){return difference(expectedPolygon,actualPolygon);}
    }

    /** null means an invalid, unmeasurable or non-convex outline. */
    static Score compare(double[][] expected,double[][] actual) {
        if(!isConvex(expected)||!isConvex(actual))return null;
        expected=copyPolygon(expected);actual=copyPolygon(actual);
        double e=area(expected),a=area(actual);
        if(e<EPS||a<EPS)return null;
        double intersection=area(clip(expected,actual));
        intersection=Math.min(Math.min(e,a),Math.max(0,intersection));
        if(e+a-intersection<EPS)return null;
        return new Score(e,a,intersection,expected,actual);
    }

    static double[][] copyPolygon(double[][] polygon){
        if(polygon==null)return null;
        double[][] copy=new double[polygon.length][];
        for(int i=0;i<copy.length;i++)copy[i]=polygon[i].clone();
        return copy;
    }

    static double area(double[][] p) {
        if(p==null||p.length<3)return 0;
        double sum=0;
        for(int i=0;i<p.length;i++){
            double[] a=p[i],b=p[(i+1)%p.length];
            sum+=a[0]*b[1]-a[1]*b[0];
        }
        return Math.abs(sum)*0.5;
    }

    /** Positive area when CCW in the mathematical coordinate plane. */
    private static double signedArea(double[][] p) {
        double sum=0;
        for(int i=0;i<p.length;i++){
            double[] a=p[i],b=p[(i+1)%p.length];
            sum+=a[0]*b[1]-a[1]*b[0];
        }
        return 0.5*sum;
    }

    private static boolean isConvex(double[][] p) {
        if(p==null||p.length<3)return false;
        for(double[] point:p)if(point==null||point.length<2||!Double.isFinite(point[0])||!Double.isFinite(point[1]))return false;
        if(!Double.isFinite(signedArea(p))||area(p)<EPS)return false;
        double sign=Math.signum(signedArea(p));
        for(int i=0;i<p.length;i++){
            if(p[i]==null||p[i].length<2||!Double.isFinite(p[i][0])||!Double.isFinite(p[i][1]))return false;
            double[] a=p[i],b=p[(i+1)%p.length],c=p[(i+2)%p.length];
            double cross=cross(b[0]-a[0],b[1]-a[1],c[0]-b[0],c[1]-b[1]);
            if(sign*cross < -EPS)return false;
        }
        return true;
    }

    private static double cross(double ax,double ay,double bx,double by){return ax*by-ay*bx;}

    private static double inside(double[] p,double[] a,double[] b,double orientation) {
        return orientation*cross(b[0]-a[0],b[1]-a[1],p[0]-a[0],p[1]-a[1]);
    }

    private static double[] intersect(double[] a,double[] b,double sa,double sb) {
        double denom=sa-sb;
        double t=Math.abs(denom)<EPS?0.5:sa/denom;
        t=Math.max(0,Math.min(1,t));
        return new double[]{a[0]+(b[0]-a[0])*t,a[1]+(b[1]-a[1])*t};
    }

    /** Sutherland-Hodgman convex clipping, either winding accepted. */
    private static double[][] clip(double[][] subject,double[][] clipping) {
        List<double[]> points=new ArrayList<>();
        for(double[] p:subject)points.add(p);
        double orientation=Math.signum(signedArea(clipping));
        for(int k=0;k<clipping.length&&!points.isEmpty();k++){
            double[] a=clipping[k],b=clipping[(k+1)%clipping.length];
            List<double[]> next=new ArrayList<>();
            double[] prev=points.get(points.size()-1);
            double sp=inside(prev,a,b,orientation);
            for(double[] cur:points){
                double sc=inside(cur,a,b,orientation);
                boolean was=sp>=-EPS,now=sc>=-EPS;
                if(was!=now)next.add(intersect(prev,cur,sp,sc));
                if(now)next.add(cur);
                prev=cur;sp=sc;
            }
            points=next;
        }
        return points.toArray(new double[0][]);
    }

    /** Partition a convex subtraction by peeling off the exterior of each clipping half-plane.
     * Each exterior piece is removed before the next cut, so coloured pieces do not overlap.
     * Uses the same inside test and intersection arithmetic as the overlap calculation above.
     */
    private static List<double[][]> difference(double[][] subject,double[][] clipping){
        List<double[][]> pieces=new ArrayList<>();
        double[][] remaining=subject;
        double orientation=Math.signum(signedArea(clipping));
        for(int k=0;k<clipping.length&&remaining.length>=3;k++){
            double[] a=clipping[k],b=clipping[(k+1)%clipping.length];
            double[][] outside=halfPlane(remaining,a,b,orientation,false);
            if(area(outside)>EPS)pieces.add(outside);
            remaining=halfPlane(remaining,a,b,orientation,true);
        }
        return pieces;
    }

    private static double[][] halfPlane(double[][] polygon,double[] a,double[] b,double orientation,boolean keepInside){
        List<double[]> result=new ArrayList<>();
        double[] prev=polygon[polygon.length-1];double sp=inside(prev,a,b,orientation);
        for(double[] cur:polygon){
            double sc=inside(cur,a,b,orientation);
            boolean was=(sp>=-EPS)==keepInside,now=(sc>=-EPS)==keepInside;
            if(was!=now)result.add(intersect(prev,cur,sp,sc));
            if(now)result.add(cur);
            prev=cur;sp=sc;
        }
        return result.toArray(new double[0][]);
    }

    static double[][] circle(double cx,double cy,double r,int segments) {
        if(!(r>0)||segments<32)throw new IllegalArgumentException("Invalid circle");
        double[][] p=new double[segments][2];
        for(int i=0;i<segments;i++){
            double theta=2*Math.PI*i/segments;
            p[i][0]=cx+r*Math.cos(theta);p[i][1]=cy+r*Math.sin(theta);
        }
        return p;
    }
}
