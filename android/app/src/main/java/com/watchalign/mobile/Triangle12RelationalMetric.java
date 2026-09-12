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
        PerspectiveMasterRenderer.Pose pose=InspectionImageStore.alignedPose;
        if(pose!=null&&pose.anchorMode&&pose.perspectiveMode){
            Result rectified=measureRectified(pose,left,right,apex,minute60Inner,crownTop);
            if(rectified!=null)return rectified;
        }
        return measureRaw(left.x,left.y,right.x,right.y,apex.x,apex.y,minute60Inner.x,minute60Inner.y,crownTop.x,crownTop.y,centre.x,centre.y,p12.x,p12.y);
    }

    /** Rectify all user-selected features to the canonical dial plane before comparing geometry. */
    public static Result measureRectified(PerspectiveMasterRenderer.Pose pose,PointF left,PointF right,PointF apex,PointF minute60Inner,PointF crownTop){
        PointF l=PerspectiveRectifier.toDial(pose,left),r=PerspectiveRectifier.toDial(pose,right),a=PerspectiveRectifier.toDial(pose,apex),m=PerspectiveRectifier.toDial(pose,minute60Inner),c=PerspectiveRectifier.toDial(pose,crownTop);
        if(l==null||r==null||a==null||m==null||c==null)return null;
        Result raw=measureRaw(l.x,l.y,r.x,r.y,a.x,a.y,m.x,m.y,c.x,c.y,0f,0f,0f,-1f);

        // Map genuine-range values onto the reference centre so the existing UI reads "≈ gen".
        // Outside the range, preserve only the amount beyond the nearest genuine control boundary.
        float bg=rangeAdjusted(raw.baseGapRatio,GenTriangle12RelationalReference.BASE_TO_60_MIN,GenTriangle12RelationalReference.BASE_TO_60_MAX,GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE);
        float ag=rangeAdjusted(raw.apexGapRatio,GenTriangle12RelationalReference.APEX_TO_CROWN_MIN,GenTriangle12RelationalReference.APEX_TO_CROWN_MAX,GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE);

        float meanR=(dist(pose.anchor12X,pose.anchor12Y,pose.anchor6X,pose.anchor6Y)+dist(pose.anchor3X,pose.anchor3Y,pose.anchor9X,pose.anchor9Y))/4f;
        float lateralPx=raw.lateralPx*meanR;
        return new Result(bg,ag,raw.heightRatio,raw.rotationDeg,lateralPx);
    }

    private static float rangeAdjusted(float v,float min,float max,float centre){
        if(v>=min&&v<=max)return centre;
        if(v<min)return centre+(v-min);
        return centre+(v-max);
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

    private static float dist(float x1,float y1,float x2,float y2){return (float)Math.hypot(x2-x1,y2-y1);}
}
