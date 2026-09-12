package com.watchalign.mobile;

/** Resolution-independent offset math for the manual 12-triangle check. */
final class Triangle12Metric {
    static final class Result {
        final float radialPx,lateralPx,radialPct;
        Result(float radialPx,float lateralPx,float radialPct){this.radialPx=radialPx;this.lateralPx=lateralPx;this.radialPct=radialPct;}
    }

    static Result measure(float actualX,float actualY,float expectedX,float expectedY,
                          float outwardX,float outwardY,float rightX,float rightY,float dialRadiusPx){
        float od=(float)Math.hypot(outwardX,outwardY);if(od<1e-6f)od=1f;
        float rd=(float)Math.hypot(rightX,rightY);if(rd<1e-6f)rd=1f;
        float ox=outwardX/od,oy=outwardY/od,rx=rightX/rd,ry=rightY/rd;
        float dx=actualX-expectedX,dy=actualY-expectedY;
        float radial=dx*ox+dy*oy;
        float lateral=dx*rx+dy*ry;
        float pct=dialRadiusPx>1e-6f?100f*radial/dialRadiusPx:0f;
        return new Result(radial,lateral,pct);
    }

    private Triangle12Metric(){}
}
