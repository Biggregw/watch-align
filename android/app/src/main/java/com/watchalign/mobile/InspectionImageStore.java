package com.watchalign.mobile;

import android.graphics.Bitmap;

/** Process-local handoff for full-screen inspection bitmaps. Avoids serialising large images through Intents. */
final class InspectionImageStore {
    static Bitmap bitmap;
    static String title;
    static void set(Bitmap b, String t){ bitmap=b; title=t; }
    static void clear(){ bitmap=null; title=null; }
    private InspectionImageStore(){}
}
