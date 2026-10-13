package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Display-only full-photo outline of the precise detected polygon for each usable
 * baton (3/6/9). Yellow model geometry is already on the transparent base bitmap;
 * cyan is projected with the exact SAME frozen H the detector sampled through.
 * No threshold, pose, edge fit or genuine reference is modified.
 */
final class BatonFitDiagnosticOverlay {
    private BatonFitDiagnosticOverlay() {}

    static Bitmap render(Bitmap yellow,Alpha94MarkerMeasurement.Report measurement,double[] H) {
        if(yellow==null||measurement==null||H==null||H.length!=9)return yellow;
        Bitmap overlay=yellow.copy(Bitmap.Config.ARGB_8888,true);
        Canvas canvas=new Canvas(overlay);
        Paint stroke=new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setColor(Color.rgb(25,221,246));
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(2.0f,(float)(0.0035*measurement.dialRadiusPx)));
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeCap(Paint.Cap.ROUND);

        for(Alpha94MarkerMeasurement.Marker m:measurement.markers) {
            if(!"baton".equals(m.kind)||!m.usable||m.fitVertices==null||m.fitVertices.length!=4)continue;
            Path p=new Path();boolean ok=true;
            for(int i=0;i<4;i++){
                double[] xy=project(H,m.fitVertices[i]);
                if(xy==null){ok=false;break;}
                if(i==0)p.moveTo((float)xy[0],(float)xy[1]);
                else p.lineTo((float)xy[0],(float)xy[1]);
            }
            if(ok){p.close();canvas.drawPath(p,stroke);}
        }
        return overlay;
    }

    private static double[] project(double[] H,double[] p) {
        if(p==null||p.length<2)return null;
        double z=H[6]*p[0]+H[7]*p[1]+H[8];
        if(!Double.isFinite(z)||Math.abs(z)<1e-10)return null;
        double x=(H[0]*p[0]+H[1]*p[1]+H[2])/z,y=(H[3]*p[0]+H[4]*p[1]+H[5])/z;
        return Double.isFinite(x)&&Double.isFinite(y)?new double[]{x,y}:null;
    }
}
