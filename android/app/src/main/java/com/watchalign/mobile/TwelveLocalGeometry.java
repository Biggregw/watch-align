package com.watchalign.mobile;

/**
 * Model-neutral 12-marker geometry measured only from the marker outline and its local
 * 59/60/01 minute-track frame. The maths is the same local reference used by the mature GMT
 * path: the 59-01 chord defines the tangent, its inward normal defines marker rotation, and the
 * detected 60 tick defines lateral centring. No marker dimensions or QC tolerances live here.
 *
 * All points must be in one common Cartesian image/dial frame. Supplying points after an affine
 * dial rectification is valid; the result is normalized by marker width where appropriate.
 */
final class TwelveLocalGeometry {
    static final class Result {
        final boolean valid;
        final String reason;
        final double gapOverWidth;
        final double centringOverWidth;
        final double rotationDeg;
        final double baseEdgeDeg;
        final double leftClearanceOverWidth;
        final double rightClearanceOverWidth;
        final double sideAsymmetry;
        final double markerWidth;
        final double axisReferenceDisagreementDeg;

        Result(String reason) {
            valid=false;this.reason=reason;
            gapOverWidth=centringOverWidth=rotationDeg=baseEdgeDeg=Double.NaN;
            leftClearanceOverWidth=rightClearanceOverWidth=sideAsymmetry=Double.NaN;
            markerWidth=axisReferenceDisagreementDeg=Double.NaN;
        }

        Result(double gap,double centring,double rotation,double base,double left,double right,
               double width,double axisDisagreement) {
            valid=true;reason="";
            gapOverWidth=gap;centringOverWidth=centring;rotationDeg=rotation;baseEdgeDeg=base;
            leftClearanceOverWidth=left;rightClearanceOverWidth=right;sideAsymmetry=right-left;
            markerWidth=width;axisReferenceDisagreementDeg=axisDisagreement;
        }
    }

    private TwelveLocalGeometry() {}

    /**
     * @param left/right marker base corners
     * @param tip marker inward tip
     * @param tick59/tick60/tick01 local minute tick inner ends
     * @param dialCx/dialCy dial centre in the same frame; NaN skips only the centre-vs-chord diagnostic
     */
    static Result measure(double[] left,double[] right,double[] tip,
                          double[] tick59,double[] tick60,double[] tick01,
                          double dialCx,double dialCy) {
        if(!point(left)||!point(right)||!point(tip)||!point(tick59)||!point(tick60)||!point(tick01))
            return new Result("local triangle or 59/60/01 points missing");

        double width=dist(left,right);
        if(!(width>1e-9))return new Result("triangle base is degenerate");

        double kx=tick01[0]-tick59[0],ky=tick01[1]-tick59[1],kn=Math.hypot(kx,ky);
        if(!(kn>1e-9))return new Result("59-01 tick chord is degenerate");

        // Normal to the local minute-track chord. Orient it inward. When a dial centre is
        // available this is the mature GMT convention; otherwise the triangle tip supplies the
        // same physical direction without letting its angle define the reference.
        double ux=-ky/kn,uy=kx/kn;
        if(Double.isFinite(dialCx)&&Double.isFinite(dialCy)) {
            if((dialCx-tick60[0])*ux+(dialCy-tick60[1])*uy<0){ux=-ux;uy=-uy;}
        } else {
            double[] bm=mid(left,right);
            if((tip[0]-bm[0])*ux+(tip[1]-bm[1])*uy<0){ux=-ux;uy=-uy;}
        }
        double vx=-uy,vy=ux;

        double[] baseMid=mid(left,right);
        double dx=baseMid[0]-tick60[0],dy=baseMid[1]-tick60[1];
        double centring=(dx*vx+dy*vy)/width;
        double gap=Math.abs(pointLineDistance(baseMid,tick59,tick01))/width;

        double triAx=Math.atan2(tip[1]-baseMid[1],tip[0]-baseMid[0]);
        double refAx=Math.atan2(uy,ux);
        double rotation=wrap90(Math.toDegrees(triAx-refAx));
        double base=parallelAngleDifferenceDeg(left,right,tick59,tick01);

        double l=dist(left,tick59)/width,r=dist(right,tick01)/width;
        double disagree=Double.NaN;
        if(Double.isFinite(dialCx)&&Double.isFinite(dialCy)) {
            double rx=dialCx-tick60[0],ry=dialCy-tick60[1],rn=Math.hypot(rx,ry);
            if(rn>1e-9) {
                double q=(rx*ux+ry*uy)/rn;
                disagree=Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,q))));
            }
        }
        return new Result(gap,centring,rotation,base,l,r,width,disagree);
    }

    private static boolean point(double[] p){return p!=null&&p.length>=2&&Double.isFinite(p[0])&&Double.isFinite(p[1]);}
    private static double[] mid(double[] a,double[] b){return new double[]{(a[0]+b[0])/2.0,(a[1]+b[1])/2.0};}
    private static double dist(double[] a,double[] b){return Math.hypot(a[0]-b[0],a[1]-b[1]);}
    private static double pointLineDistance(double[] p,double[] a,double[] b){
        double x=b[0]-a[0],y=b[1]-a[1],len=Math.hypot(x,y);
        return len<=1e-9?Double.NaN:(x*(p[1]-a[1])-y*(p[0]-a[0]))/len;
    }
    private static double parallelAngleDifferenceDeg(double[] a,double[] b,double[] c,double[] d){
        double x=Math.atan2(b[1]-a[1],b[0]-a[0]);
        double y=Math.atan2(d[1]-c[1],d[0]-c[0]);
        return wrap90(Math.toDegrees(x-y));
    }
    private static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
}
