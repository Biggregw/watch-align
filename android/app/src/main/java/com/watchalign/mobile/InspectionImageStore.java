package com.watchalign.mobile;

import android.graphics.Bitmap;

/** Process-local handoff for full-screen inspection and manual perspective alignment. */
final class InspectionImageStore {
    static Bitmap bitmap;
    static Bitmap baseBitmap;
    static String title;
    static String modelRef;
    static boolean overlayMode;

    static void set(Bitmap b, String t){
        bitmap=b;baseBitmap=null;title=t;modelRef=null;overlayMode=false;
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t){
        baseBitmap=base;bitmap=overlay;title=t;modelRef=null;overlayMode=base!=null&&overlay!=null;
    }

    static void setManual(Bitmap base,String model,String t){
        baseBitmap=base;bitmap=base;title=t;modelRef=model;overlayMode=false;
    }

    static void clear(){bitmap=null;baseBitmap=null;title=null;modelRef=null;overlayMode=false;}
    private InspectionImageStore(){}
}
