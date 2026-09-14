package com.watchalign.mobile;

import android.util.Size;

/** Deterministic camera size/orientation policy, separated so it can be tested without the UI. */
final class CameraCaptureConfig {
    static final long MAX_STILL_PIXELS=12_000_000L;

    static Size chooseStill(Size[] sizes){
        if(sizes==null||sizes.length==0)return new Size(1920,1080);
        Size best=null;
        for(Size size:sizes){
            long pixels=(long)size.getWidth()*size.getHeight();
            if(pixels>MAX_STILL_PIXELS)continue;
            if(best==null||pixels>(long)best.getWidth()*best.getHeight())best=size;
        }
        if(best!=null)return best;
        best=sizes[0];for(Size size:sizes)if((long)size.getWidth()*size.getHeight()<(long)best.getWidth()*best.getHeight())best=size;return best;
    }

    static Size choosePreview(Size[] sizes,Size still,int viewWidth,int viewHeight){
        if(sizes==null||sizes.length==0)return new Size(1280,960);
        double target=still.getWidth()/(double)still.getHeight();Size best=null;double score=Double.MAX_VALUE;
        for(Size size:sizes){
            if(size.getWidth()>1920||size.getHeight()>1920)continue;
            double aspect=size.getWidth()/(double)size.getHeight();double areaPenalty=Math.abs(size.getWidth()*size.getHeight()-viewWidth*viewHeight)/(double)Math.max(1,viewWidth*viewHeight);
            double s=Math.abs(aspect-target)*20+areaPenalty;if(s<score){score=s;best=size;}
        }
        return best==null?sizes[0]:best;
    }

    static int jpegOrientation(int sensorOrientation,int displayRotation,boolean frontFacing){
        int deviceDegrees=displayRotation==android.view.Surface.ROTATION_90?90:displayRotation==android.view.Surface.ROTATION_180?180:displayRotation==android.view.Surface.ROTATION_270?270:0;
        return frontFacing?(sensorOrientation-deviceDegrees+360)%360:(sensorOrientation+deviceDegrees)%360;
    }
    private CameraCaptureConfig(){}
}
