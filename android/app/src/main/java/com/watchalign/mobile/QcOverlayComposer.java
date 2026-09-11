package com.watchalign.mobile;

import android.graphics.Bitmap;

/** Keeps the established QC guide and adds only pixels changed by the extended analyzer. */
final class QcOverlayComposer {
    static Bitmap compose(Bitmap raw, Bitmap guide, Bitmap extended) {
        int w=Math.min(raw.getWidth(),Math.min(guide.getWidth(),extended.getWidth()));
        int h=Math.min(raw.getHeight(),Math.min(guide.getHeight(),extended.getHeight()));
        Bitmap out=guide.copy(Bitmap.Config.ARGB_8888,true);
        int[] a=new int[w], g=new int[w], e=new int[w];
        for(int y=0;y<h;y++) {
            raw.getPixels(a,0,w,0,y,w,1);
            guide.getPixels(g,0,w,0,y,w,1);
            extended.getPixels(e,0,w,0,y,w,1);
            for(int x=0;x<w;x++) if(changed(a[x],e[x])) g[x]=e[x];
            out.setPixels(g,0,w,0,y,w,1);
        }
        return out;
    }

    static boolean changed(int raw,int ext) {
        int dr=Math.abs(((raw>>16)&255)-((ext>>16)&255));
        int dg=Math.abs(((raw>>8)&255)-((ext>>8)&255));
        int db=Math.abs((raw&255)-(ext&255));
        return dr+dg+db>=36;
    }

    private QcOverlayComposer(){}
}
