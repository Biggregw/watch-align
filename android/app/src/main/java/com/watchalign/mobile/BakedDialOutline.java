package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Fixed genuine 126710BLNR dial master for the perspective proof.
 *
 * This never traces or adapts to the candidate photograph. Geometry comes only
 * from Gmt126710BlnrMaster, measured from a genuine current-generation 126710BLNR
 * reference and cross-checked against an independent real watch photo.
 *
 * The complete rendered geometry is fixed: dial edge, 60 minute ticks, eight round
 * surrounds, 6/9 baton surrounds and the 12 triangle. The candidate can only supply
 * a camera pose; it cannot alter any of these shapes or their relative positions.
 */
final class BakedDialOutline {
    static final int W=1024,H=1024;
    static final double CX=512.0,CY=512.0,R=480.0;
    private static final int BRIGHT=Color.rgb(255,255,0);
    private static final float OUTLINE_STROKE=1.30f;

    private BakedDialOutline(){}

    static Bitmap bitmap(){
        Bitmap out=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        c.drawColor(Color.TRANSPARENT);

        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(OUTLINE_STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(BRIGHT);
        p.setAlpha(255);

        c.drawCircle((float)CX,(float)CY,(float)(R*Gmt126710BlnrMaster.DIAL_EDGE_R),p);

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

        for(int hour:new int[]{1,2,4,5,7,8,10,11}){
            double a=Gmt126710BlnrMaster.angleForHour(hour);
            float x=(float)(CX+R*Gmt126710BlnrMaster.ROUND_CENTER_R*Math.cos(a));
            float y=(float)(CY+R*Gmt126710BlnrMaster.ROUND_CENTER_R*Math.sin(a));
            c.drawCircle(x,y,(float)(R*Gmt126710BlnrMaster.ROUND_OUTER_R),p);
        }

        drawBaton(c,p,6);
        drawBaton(c,p,9);
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
