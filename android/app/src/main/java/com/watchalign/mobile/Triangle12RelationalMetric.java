package com.watchalign.mobile;

import android.graphics.PointF;

/** Scale-independent 12-marker relationship metric. */
public final class Triangle12RelationalMetric {
    public static final class Result {
        public final float baseGapRatio, apexGapRatio, heightRatio, rotationDeg, lateralRadius;
        Result(float bg,float ag,float hr,float rd,float lat){baseGapRatio=bg;apexGapRatio=ag;heightRatio=hr;rotationDeg=rd;lateralRadius=lat;}
    }
    private Triangle12RelationalMetric() {}

    public static Result measure(PointF left,PointF right,PointF apex,PointF minute60Inner,PointF crownTop,PointF centre,PointF p12){
        return measureRaw(left.x,left.y,right.x,right.y,apex.x,apex.y,minute60Inner.x,minute60Inner.y,crownTop.x,crownTop.y,centre.x,centre.y,p12.x,p12.y);
    }

    /**
     * Rectifies all five user-selected image points back onto the canonical dial plane before measurement.
     * This removes pitch/yaw/projective distortion from the local 12-marker geometry.
     */
    public static Result measureRectified(PerspectiveMasterRenderer.Pose pose,PointF left,PointF right,PointF apex,PointF minute60Inner,PointF crownTop){
        PointF l=PerspectiveRectifier.toDial(pose,left),r=PerspectiveRectifier.toDial(pose,right),a=PerspectiveRectifier.toDial(pose,apex),m=PerspectiveRectifier.toDial(pose,minute60Inner),c=PerspectiveRectifier.toDial(pose,crownTop);
        if(l==null||r==null||a==null||m==null||c==null)return null;
        return measureRaw(l.x,l.y,r.x,r.y,a.x,a.y,m.x,m.y,c.x,c.y,0f,0f,0f,-1f);
    }

    static Result measureRaw(float lx,float ly,float rx,float ry,float ax,float ay,float mx,float my,float cx,float cy,float ox,float oy,float p12x,float p12y){
        float baseX=(lx+rx)/2f,baseY=(ly+ry)/2f;
        float ux=p12x-ox,uy=p12y-oy,un=(float)Math.hypot(ux,uy);if(un<1e-6f)un=1;ux/=un;uy/=un;
        float tx=-uy,ty=ux;
        float bw=(float)Math.hypot(rx-lx,ry-ly);if(bw<1e-6f)bw=1e-6f;

        float baseGap=((mx-baseX)*ux+(my-baseY)*uy)/bw;
        float apexGap=((ax-cx)*ux+(ay-cy)*uy)/bw;
        float height=((baseX-ax)*ux+(baseY-ay)*uy)/bw;

        float baseDx=rx-lx,baseDy=ry-ly;
        float baseAlongT=baseDx*tx+baseDy*ty,baseAlongU=baseDx*ux+baseDy*uy;
        float rot=(float)Math.toDegrees(Math.atan2(baseAlongU,baseAlongT));
        float lateral=(baseX-ox)*tx+(baseY-oy)*ty;
        return new Result(baseGap,apexGap,height,rot,lateral);
    }
}
