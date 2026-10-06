package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Clean Alpha92 fixed genuine GMT overlay. Geometry is entirely canonical and is
 * drawn before the single perspective warp. No candidate marker can alter it.
 */
final class Alpha92BakedDialOutline {
    static final int W=1440,H=1440;
    static final double CX=720.0,CY=720.0,R=480.0;
    private static final int BRIGHT=Color.rgb(255,255,0);
    private static final float STROKE=4.0f;

    private Alpha92BakedDialOutline(){}

    static Bitmap bitmap(){
        Bitmap out=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);c.drawColor(Color.TRANSPARENT);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(BRIGHT);p.setAlpha(255);

        c.drawCircle((float)CX,(float)CY,(float)(R*Alpha92GmtMaster.DIAL_EDGE_R),p);

        for(int i=0;i<60;i++){
            double a=Alpha92GmtMaster.angleForMinute(i),ca=Math.cos(a),sa=Math.sin(a);
            c.drawLine(
                    (float)(CX+R*Alpha92GmtMaster.MINUTE_TRACK_R*ca),
                    (float)(CY+R*Alpha92GmtMaster.MINUTE_TRACK_R*sa),
                    (float)(CX+R*Alpha92GmtMaster.MINUTE_TRACK_OUTER_R*ca),
                    (float)(CY+R*Alpha92GmtMaster.MINUTE_TRACK_OUTER_R*sa),p);
        }

        for(int hour:new int[]{1,2,4,5,7,8,10,11}){
            double a=Alpha92GmtMaster.angleForHour(hour);
            float x=(float)(CX+R*Alpha92GmtMaster.ROUND_CENTER_R*Math.cos(a));
            float y=(float)(CY+R*Alpha92GmtMaster.ROUND_CENTER_R*Math.sin(a));
            c.drawCircle(x,y,(float)(R*Alpha92GmtMaster.ROUND_OUTER_R),p);
        }
        drawBaton(c,p,6);drawBaton(c,p,9);drawTriangle(c,p);
        return out;
    }

    private static void drawBaton(Canvas c,Paint p,int hour){
        double a=Alpha92GmtMaster.angleForHour(hour),ca=Math.cos(a),sa=Math.sin(a);
        double tx=-sa,ty=ca,cr=Alpha92GmtMaster.BATON_CENTER_R;
        double rh=Alpha92GmtMaster.BATON_RADIAL_HALF,th=Alpha92GmtMaster.BATON_TANGENTIAL_HALF;
        Path path=new Path();
        for(int k=0;k<4;k++){
            double rr=cr+((k==0||k==1)?rh:-rh);
            double tt=((k==0||k==3)?-th:th);
            float x=(float)(CX+R*(rr*ca+tt*tx)),y=(float)(CY+R*(rr*sa+tt*ty));
            if(k==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        path.close();c.drawPath(path,p);
    }

    private static void drawTriangle(Canvas c,Paint p){
        Path path=new Path();
        path.moveTo((float)(CX-R*Alpha92GmtMaster.TRI_HALF_BASE),(float)(CY-R*Alpha92GmtMaster.TRI_BASE_R));
        path.lineTo((float)(CX+R*Alpha92GmtMaster.TRI_HALF_BASE),(float)(CY-R*Alpha92GmtMaster.TRI_BASE_R));
        path.lineTo((float)CX,(float)(CY-R*Alpha92GmtMaster.TRI_APEX_R));
        path.close();c.drawPath(path,p);
    }
}
