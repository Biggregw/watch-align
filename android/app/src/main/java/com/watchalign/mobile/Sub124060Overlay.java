package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * Compatibility adapter for older Sub harness/tests. There is no Sub-specific renderer here any more:
 * all visible drawing and close-up planning are delegated to {@link MeasuredOverlayRenderer}.
 */
final class Sub124060Overlay {
    static final class Drawing {
        final MeasuredOverlayRenderer.Drawing common;
        // Kept only for the existing desktop SubCheck harness, which records the selected 12 centre.
        double[] triL,triR,triT;

        Drawing(){this(new MeasuredOverlayRenderer.Drawing());}
        private Drawing(MeasuredOverlayRenderer.Drawing d){common=d;sync();}

        static Drawing of(Sub124060QcAnalyzer.Result r){return new Drawing(Sub124060PresentationAdapter.adapt(r));}
        boolean hasTriangle(){return common.twelve!=null;}
        boolean hasAnything(){return common.hasAnything();}
        void mapTo(GmtDialCrop.Crop c){common.mapTo(c);sync();}

        private void sync(){
            if(common.twelve==null){triL=triR=triT=null;return;}
            triL=common.twelve.triLeft;triR=common.twelve.triRight;triT=common.twelve.triTip;
        }
    }

    private Sub124060Overlay(){}

    static Bitmap render(Bitmap watch,Drawing d){return d==null?null:MeasuredOverlayRenderer.render(watch,d.common);}
    static Bitmap closeUp(Bitmap watch,Bitmap overlay,Drawing d,int size){return d==null?null:MeasuredOverlayRenderer.closeUp(watch,overlay,d.common,size);}
}
