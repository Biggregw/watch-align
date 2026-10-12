package com.watchalign.mobile;
import android.graphics.*;

/** All coloured regions come from the selected comparison's saved polygons. Partial lines never supply area. */
final class LuminousAlignmentOverlay {
    static void draw(Bitmap image,double cx,double cy,double half,LuminousMeasurement.Marker marker,int mode){
        MarkerMismatchOverlay.closeup(image,cx,cy,half,marker.displayPolygon(mode));
        if(marker.comparison(mode)!=null)return;
        LuminousPartialFit.Fit fit=marker.alignment;if(fit==null||!fit.usable())return;
        Canvas c=new Canvas(image);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(MarkerMismatchOverlay.CYAN);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);
        double[] T=MarkerMismatchOverlay.closeupTransform(image.getWidth(),cx,cy,half);
        for(double[][] segment:fit.segments){double[][] p=MarkerMismatchOverlay.project(new double[][]{segment[0],segment[1],segment[0]},T);if(p!=null)c.drawLine((float)p[0][0],(float)p[0][1],(float)p[1][0],(float)p[1][1],paint);}
        if(marker.spec.shape==ModelSpec.Shape.ROUND&&fit.ideal!=null){double[][] p=MarkerMismatchOverlay.project(fit.ideal,T);for(int i=0;i<p.length;i+=2){int next=(i+1)%p.length;c.drawLine((float)p[i][0],(float)p[i][1],(float)p[next][0],(float)p[next][1],paint);}}
    }
}
