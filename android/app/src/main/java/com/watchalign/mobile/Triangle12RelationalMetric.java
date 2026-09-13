package com.watchalign.mobile;

import android.graphics.PointF;

/** Scale-independent 12-marker relationship metric. */
public final class Triangle12RelationalMetric {
    public static final class RawMeasurement {
        public final float baseTo60Ratio,apexToCrownRatio,rotationDeg,heightRatio,lateralPx;
        RawMeasurement(float base,float apex,float rotation,float height,float lateral){baseTo60Ratio=base;apexToCrownRatio=apex;rotationDeg=rotation;heightRatio=height;lateralPx=lateral;}
    }
    public enum RangePosition { BELOW,WITHIN,ABOVE }
    public static final class Classification {
        public final RangePosition baseTo60,apexToCrown,rotationMagnitude;
        Classification(float base,float apex,float rotation){baseTo60=position(base,GenTriangle12RelationalReference.BASE_TO_60_MIN,GenTriangle12RelationalReference.BASE_TO_60_MAX);apexToCrown=position(apex,GenTriangle12RelationalReference.APEX_TO_CROWN_MIN,GenTriangle12RelationalReference.APEX_TO_CROWN_MAX);rotationMagnitude=Math.abs(rotation)<=GenTriangle12RelationalReference.ROTATION_OBSERVED_GEN_MAX_DEG?RangePosition.WITHIN:RangePosition.ABOVE;}
        private static RangePosition position(float value,float min,float max){return value<min?RangePosition.BELOW:value>max?RangePosition.ABOVE:RangePosition.WITHIN;}
    }
    public static final class Result {
        public final float baseGapRatio, apexGapRatio, heightRatio, rotationDeg, lateralPx;
        /** Present only when the five manual points were perspective-rectified first. */
        public final RawMeasurement rawRectified;
        /** Classification uses the frozen genuine-control ranges and never changes the raw values. */
        public final Classification classification;
        Result(float bg,float ag,float hr,float rd,float lat){this(bg,ag,hr,rd,lat,false);}
        private Result(float bg,float ag,float hr,float rd,float lat,boolean rectified){baseGapRatio=bg;apexGapRatio=ag;heightRatio=hr;rotationDeg=rd;lateralPx=lat;rawRectified=rectified?new RawMeasurement(bg,ag,rd,hr,lat):null;classification=new Classification(bg,ag,rd);}
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

        float meanR=(dist(pose.anchor12X,pose.anchor12Y,pose.anchor6X,pose.anchor6Y)+dist(pose.anchor3X,pose.anchor3Y,pose.anchor9X,pose.anchor9Y))/4f;
        float lateralPx=raw.lateralPx*meanR;
        return new Result(raw.baseGapRatio,raw.apexGapRatio,raw.heightRatio,raw.rotationDeg,lateralPx,true);
    }

    /** Explicit-pose entry point for validation runners; it does not read or mutate activity state. */
    static Result measureRectifiedRaw(PerspectiveMasterRenderer.Pose pose,double[][] points){
        if(points==null||points.length!=5)return null;double[][] q=new double[5][];for(int i=0;i<5;i++){if(points[i]==null||points[i].length<2)return null;q[i]=PerspectiveRectifier.toDialRaw(pose,points[i][0],points[i][1]);if(q[i]==null)return null;}
        Result raw=measureRaw((float)q[0][0],(float)q[0][1],(float)q[1][0],(float)q[1][1],(float)q[2][0],(float)q[2][1],(float)q[3][0],(float)q[3][1],(float)q[4][0],(float)q[4][1],0,0,0,-1);
        float meanR=(dist(pose.anchor12X,pose.anchor12Y,pose.anchor6X,pose.anchor6Y)+dist(pose.anchor3X,pose.anchor3Y,pose.anchor9X,pose.anchor9Y))/4f;
        return new Result(raw.baseGapRatio,raw.apexGapRatio,raw.heightRatio,raw.rotationDeg,raw.lateralPx*meanR,true);
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
