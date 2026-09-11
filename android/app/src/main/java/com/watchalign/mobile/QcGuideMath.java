package com.watchalign.mobile;

final class QcGuideMath {
    static final class P { final double x,y; P(double x,double y){this.x=x;this.y=y;} }

    static double idealAngleDeg(int hour,double globalRotation){
        double h = hour==12 ? 0.0 : hour*30.0;
        return h + globalRotation;
    }

    static P polar(double cx,double cy,double radius,double clockwiseDeg){
        double a=Math.toRadians(clockwiseDeg);
        return new P(cx + Math.sin(a)*radius, cy - Math.cos(a)*radius);
    }

    static int severity(double angularDeg,double radialPct){
        double a=Math.abs(angularDeg), r=Math.abs(radialPct);
        if(a>2.0 || r>5.0) return 2;
        if(a>1.0 || r>3.0) return 1;
        return 0;
    }

    static boolean alignmentGuideReliable(double perspectiveEquivalentDeg){
        return !Double.isFinite(perspectiveEquivalentDeg) || perspectiveEquivalentDeg < 14.0;
    }

    private QcGuideMath() {}
}
