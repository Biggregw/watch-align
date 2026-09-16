package com.watchalign.mobile;

import android.graphics.Bitmap;

/** Process-local handoff for full-screen inspection bitmaps. */
final class InspectionImageStore {
    static Bitmap bitmap;
    static Bitmap baseBitmap;
    static String title;
    static boolean overlayMode;

    static double manualCx, manualCy, manualR, manualRoll;
    static boolean hasManualSeed;

    static void set(Bitmap b, String t){
        bitmap=b;baseBitmap=null;title=t;overlayMode=false;
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t){
        baseBitmap=base;bitmap=overlay;title=t;overlayMode=base!=null&&overlay!=null;
    }

    static void clearManualSeed(){
        hasManualSeed=false;manualCx=0;manualCy=0;manualR=0;manualRoll=0;
    }

    static void clear(){bitmap=null;baseBitmap=null;title=null;overlayMode=false;clearManualSeed();}
    private InspectionImageStore(){}
}
