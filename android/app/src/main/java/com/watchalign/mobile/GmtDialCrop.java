package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * Full-resolution dial crop (alpha61). When the dial is small in the frame (preview radius
 * under MAX_PREVIEW_RADIUS_PX, so round markers under about 40 px and batons under 30 px) and
 * the original has more pixels, the dial is found on the preview, re-read from the original at
 * up to TARGET_RADIUS_PX and analysed there. Results are mapped back onto the preview.
 */
final class GmtDialCrop {
    /**
     * Only dials smaller than this in the preview are re-read (px). Above it the markers are
     * already 40+ px across. On larger dials the extra detail moved measurements rather than
     * sharpening them: at 470 px, three genuine WOS CPO photos read their 12 at -1.1 to -1.7
     * degrees (STRONG) against -0.9 to -1.1 in the preview, and fitted the round markers on the
     * lume instead of the surround.
     */
    static final double MAX_PREVIEW_RADIUS_PX = 230;
    /** Dial radius the crop is scaled to at most (px): within the range the checks were tuned on. */
    static final double TARGET_RADIUS_PX = 380;
    /** Only worth doing when it gives at least this many times the preview's pixels. */
    static final double MIN_GAIN = 1.25;
    /** Half-width of the crop in dial radii (includes the bezel, for the dial edge fit). */
    static final double MARGIN_R = 1.35;

    static final class Crop {
        Bitmap bitmap;
        /** Crop pixel = (original pixel - origin) * scale; original pixel = preview pixel * k. */
        double originX,originY,scale,k,previewRadius,cropRadius;
        double toPreviewX(double x){return (x/scale+originX)/k;}
        double toPreviewY(double y){return (y/scale+originY)/k;}
        double toPreviewLength(double d){return d/(scale*k);}
        String describe(){
            return String.format(java.util.Locale.US,"Measured on a full-resolution crop of the dial: radius %.0f px, %.1fx the %.0f px of the preview.",
                    cropRadius,cropRadius/previewRadius,previewRadius);
        }
    }

    private GmtDialCrop(){}

    /** Null when the dial can't be found on the preview or the original adds too little. */
    static Crop make(Bitmap preview,FullResSource src){
        if(preview==null||src==null||src.width()<=0)return null;
        double[] d=GmtHumanQcAnalyzerV2.locateDial(preview);
        if(d==null)return null;
        if(d[2]>=MAX_PREVIEW_RADIUS_PX)return null;
        double k=src.width()/(double)preview.getWidth();
        double rOrig=d[2]*k,target=Math.min(rOrig,TARGET_RADIUS_PX);
        if(target<d[2]*MIN_GAIN)return null;
        double cx=d[0]*k,cy=d[1]*k,half=MARGIN_R*rOrig;
        int x0=(int)Math.max(0,Math.floor(cx-half)),y0=(int)Math.max(0,Math.floor(cy-half));
        int x1=(int)Math.min(src.width(),Math.ceil(cx+half)),y1=(int)Math.min(src.height(),Math.ceil(cy+half));
        if(x1-x0<50||y1-y0<50)return null;
        int sample=1;while(rOrig/(sample*2)>=target)sample*=2;
        Bitmap region=src.region(x0,y0,x1,y1,sample);
        if(region==null)return null;
        double want=target/rOrig;
        int w=(int)Math.max(1,Math.round((x1-x0)*want)),h=(int)Math.max(1,Math.round((y1-y0)*want));
        Bitmap scaled=region.getWidth()==w&&region.getHeight()==h?region:Bitmap.createScaledBitmap(region,w,h,true);
        Crop c=new Crop();
        c.bitmap=scaled.copy(Bitmap.Config.ARGB_8888,false);
        c.originX=x0;c.originY=y0;c.scale=w/(double)(x1-x0);c.k=k;c.previewRadius=d[2];c.cropRadius=rOrig*c.scale;
        return c;
    }
}
