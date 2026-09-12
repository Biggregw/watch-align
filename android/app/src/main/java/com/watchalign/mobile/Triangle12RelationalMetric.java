package com.watchalign.mobile;

import android.graphics.PointF;

/** Scale-independent 12-marker relationship metric. */
public final class Triangle12RelationalMetric {
    public static final class Result {
        public final float baseGapRatio, apexGapRatio, heightRatio, rotationDeg, lateralPx;
        Result(float bg,float ag,float hr,float rd,float lat){baseGapRatio=bg;apexGapRatio=ag;heightRatio=hr;rotationDeg=rd;lateralPx=lat;}
    }
    private Triangle12RelationalMetric() {}

    public static Result measure(PointF left,PointF right,PointF apex,PointF minute60Inner,PointF crownTop,PointF centre,PointF p12){
        PointF baseMid=new PointF((left.x+right.x)/2f,(left.y+right.y)/2f);
        float ux=p12.x-centre.x,uy=p12.y-centre.y,un=(float)Math.hypot(ux,uy);if(un<1e-6f)un=1;ux/=un;uy/=un;
        float tx=-uy,ty=ux;
        float bw=dist(left,right);if(bw<1e-6f)bw=1e-6f;
        float baseGap=((minute60Inner.x-baseMid.x)*ux+(minute60Inner.y-baseMid.y)*uy)/bw;
        float apexGap=((apex.x-crownTop.x)*ux+(apex.y-crownTop.y)*uy)/bw;
        float height=((baseMid.x-apex.x)*ux+(baseMid.y-apex.y)*uy)/bw;
        float baseDx=right.x-left.x,baseDy=right.y-left.y;
        float baseAlongT=baseDx*tx+baseDy*ty,baseAlongU=baseDx*ux+baseDy*uy;
        float rot=(float)Math.toDegrees(Math.atan2(baseAlongU,baseAlongT));
        float lateral=(baseMid.x-centre.x)*tx+(baseMid.y-centre.y)*ty;
        return new Result(baseGap,apexGap,height,rot,lateral);
    }
    private static float dist(PointF a,PointF b){return (float)Math.hypot(a.x-b.x,a.y-b.y);}
}
