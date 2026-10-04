package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Clean 126710BLNR dial outline for the perspective proof.
 *
 * This deliberately does NOT trace a photograph. The geometry is taken from
 * Gmt126710BlnrMaster, which was measured from the official front-on current
 * 126710BLNR image and cross-checked against an independent real photo.
 *
 * Only stable dial geometry is drawn: dial edge, 60 minute ticks, eight round
 * surrounds, 6/9 batons and the 12 triangle. No hands, text, centre stack,
 * date/cyclops, reflections or photographic texture can contaminate the overlay.
 */
final class BakedDialOutline {
    static final int W=1024,H=1024;
    static final double CX=512.0,CY=512.0,R=480.0;
    // Magenta deliberately contrasts with white lume, black dial, steel and blue bezel.
    private static final int BRIGHT=Color.rgb(255,0,180);

    private BakedDialOutline(){}

    static Bitmap bitmap(){
        Bitmap out=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        c.drawColor(Color.TRANSPARENT);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3.8f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(BRIGHT);
        p.setAlpha(255);

        // Physical black-dial edge.
        c.drawCircle((float)CX,(float)CY,(float)(R*Gmt126710BlnrMaster.DIAL_EDGE_R),p);

        // All 60 minute ticks.
        // Gmt126710BlnrMaster.MINUTE_TRACK_R is the INNER tick end, not the tick centre.
        // The previous proof incorrectly drew +/-0.025R around it, which extended every
        // tick too far inward. Keep the inner end fixed and draw only outward to the
        // visible outer minute-track end.
        final double tickInner=Gmt126710BlnrMaster.MINUTE_TRACK_R;
        final double tickOuter=0.972;
        for(int i=0;i<60;i++){
            double a=Math.toRadians(i*6.0-90.0);
            double ca=Math.cos(a),sa=Math.sin(a);
            float x1=(float)(CX+R*tickInner*ca);
            float y1=(float)(CY+R*tickInner*sa);
            float x2=(float)(CX+R*tickOuter*ca);
            float y2=(float)(CY+R*tickOuter*sa);
            c.drawLine(x1,y1,x2,y2,p);
        }

        // Eight round hour-marker surrounds.
        for(int hour:new int[]{1,2,4,5,7,8,10,11}){
            double a=Gmt126710BlnrMaster.angleForHour(hour);
            float x=(float)(CX+R*Gmt126710BlnrMaster.ROUND_CENTER_R*Math.cos(a));
            float y=(float)(CY+R*Gmt126710BlnrMaster.ROUND_CENTER_R*Math.sin(a));
            c.drawCircle(x,y,(float)(R*Gmt126710BlnrMaster.ROUND_OUTER_R),p);
        }

        // 6 and 9 baton surrounds.
        drawBaton(c,p,6);
        drawBaton(c,p,9);

        // 12 triangle surround: base outward, apex inward.
        drawTriangle(c,p);
        return out;
    }

    private static void drawBaton(Canvas c,Paint p,int hour){
        double a=Gmt126710BlnrMaster.angleForHour(hour);
        double ca=Math.cos(a),sa=Math.sin(a);
        double tx=-sa,ty=ca;
        double cr=Gmt126710BlnrMaster.MARKER_CENTER_R;
        double rh=Gmt126710BlnrMaster.BATON_RADIAL_HALF;
        double th=Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF;
        Path path=new Path();
        for(int k=0;k<4;k++){
            double rr=cr+((k==0||k==1)?rh:-rh);
            double tt=((k==0||k==3)?-th:th);
            float x=(float)(CX+R*(rr*ca+tt*tx));
            float y=(float)(CY+R*(rr*sa+tt*ty));
            if(k==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        path.close();c.drawPath(path,p);
    }

    private static void drawTriangle(Canvas c,Paint p){
        double cr=Gmt126710BlnrMaster.TRI_CENTER_R;
        double baseR=cr+Gmt126710BlnrMaster.TRI_BASE_OUTWARD;
        double apexR=cr-Gmt126710BlnrMaster.TRI_APEX_INWARD;
        double half=Gmt126710BlnrMaster.TRI_HALF_BASE;
        Path path=new Path();
        path.moveTo((float)(CX-R*half),(float)(CY-R*baseR));
        path.lineTo((float)(CX+R*half),(float)(CY-R*baseR));
        path.lineTo((float)CX,(float)(CY-R*apexR));
        path.close();c.drawPath(path,p);
    }
}
