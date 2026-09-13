package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;

/** Process-local handoff for full-screen inspection, manual perspective alignment and final QC. */
final class InspectionImageStore {
    static Bitmap bitmap;
    static Bitmap baseBitmap;
    static String title;
    static String modelRef;
    static boolean overlayMode;
    static PerspectiveMasterRenderer.Pose alignedPose;
    static String alignedModelRef;
    static PointF[] trianglePoints;
    static Triangle12RelationalMetric.Result triangleMetric;
    static Bitmap capturedBitmap;

    static void setCaptured(Bitmap bitmap){capturedBitmap=bitmap;}
    static Bitmap takeCaptured(){Bitmap b=capturedBitmap;capturedBitmap=null;return b;}

    static void set(Bitmap b, String t){
        bitmap=b;baseBitmap=null;title=t;modelRef=null;overlayMode=false;alignedPose=null;alignedModelRef=null;trianglePoints=null;triangleMetric=null;
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t){
        setOverlay(base,overlay,t,null,null);
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t,PerspectiveMasterRenderer.Pose pose,String model){
        baseBitmap=base;bitmap=overlay;title=t;modelRef=null;overlayMode=base!=null&&overlay!=null;
        alignedPose=pose==null?null:pose.copy();alignedModelRef=model;trianglePoints=null;triangleMetric=null;
    }

    static void setManual(Bitmap base,String model,String t){
        baseBitmap=base;bitmap=base;title=t;modelRef=model;overlayMode=false;alignedPose=null;alignedModelRef=null;trianglePoints=null;triangleMetric=null;
    }

    static void setTriangleResult(PointF[] points,Triangle12RelationalMetric.Result metric){
        if(points==null){trianglePoints=null;}else{trianglePoints=new PointF[points.length];for(int i=0;i<points.length;i++)trianglePoints[i]=points[i]==null?null:new PointF(points[i].x,points[i].y);}triangleMetric=metric;
    }

    static void clear(){bitmap=null;baseBitmap=null;title=null;modelRef=null;overlayMode=false;alignedPose=null;alignedModelRef=null;trianglePoints=null;triangleMetric=null;}
    private InspectionImageStore(){}
}
