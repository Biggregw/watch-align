package com.watchalign.mobile;

import android.graphics.Bitmap;

/** Process-local handoff for full-screen inspection and manual perspective alignment. */
final class InspectionImageStore {
    static Bitmap bitmap;
    static Bitmap baseBitmap;
    static String title;
    static String modelRef;
    static boolean overlayMode;
    static PerspectiveMasterRenderer.Pose alignedPose;
    static String alignedModelRef;

    static void set(Bitmap b, String t){
        bitmap=b;baseBitmap=null;title=t;modelRef=null;overlayMode=false;alignedPose=null;alignedModelRef=null;
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t){
        setOverlay(base,overlay,t,null,null);
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t,PerspectiveMasterRenderer.Pose pose,String model){
        baseBitmap=base;bitmap=overlay;title=t;modelRef=null;overlayMode=base!=null&&overlay!=null;
        alignedPose=pose==null?null:pose.copy();alignedModelRef=model;
    }

    static void setManual(Bitmap base,String model,String t){
        baseBitmap=base;bitmap=base;title=t;modelRef=model;overlayMode=false;alignedPose=null;alignedModelRef=null;
    }

    static void clear(){bitmap=null;baseBitmap=null;title=null;modelRef=null;overlayMode=false;alignedPose=null;alignedModelRef=null;}
    private InspectionImageStore(){}
}
