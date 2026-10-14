package com.watchalign.mobile;

import android.graphics.Bitmap;

/** Process-local handoff for full-screen inspection bitmaps. */
final class InspectionImageStore {
    static Bitmap bitmap;
    static Bitmap baseBitmap;
    static Bitmap scoringOverlay;
    static String title;
    static String diagnosticsText;
    static boolean overlayMode;

    static double manualCx, manualCy, manualR, manualRoll;
    static boolean hasManualSeed;

    static void set(Bitmap b, String t){
        bitmap=b;baseBitmap=null;scoringOverlay=null;title=t;diagnosticsText=null;overlayMode=false;
    }

    static void setDiagnostics(Bitmap b, String t, String text){
        bitmap=b;baseBitmap=null;scoringOverlay=null;title=t;diagnosticsText=text;overlayMode=false;
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t){
        setOverlay(base,overlay,t,null);
    }

    static void setOverlay(Bitmap base, Bitmap overlay, String t, String diagnostics){
        baseBitmap=base;bitmap=overlay;scoringOverlay=null;title=t;diagnosticsText=diagnostics;overlayMode=base!=null&&overlay!=null;
    }

    static void setMarkerOverlays(Bitmap base,Bitmap fixedMaster,Bitmap scoring){
        setOverlay(base,fixedMaster,"Marker reference overlays",null);scoringOverlay=scoring;
    }

    static void clearManualSeed(){
        hasManualSeed=false;manualCx=0;manualCy=0;manualR=0;manualRoll=0;
    }

    static void clear(){bitmap=null;baseBitmap=null;scoringOverlay=null;title=null;diagnosticsText=null;overlayMode=false;clearManualSeed();}
    private InspectionImageStore(){}
}
