package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Clean fixed genuine overlay of a model's dial (ModelSpec): dial edge, minute track and every applied marker.
 * Geometry is entirely canonical and is drawn before the single perspective warp. No candidate marker can alter it.
 * For the GMT spec this draws exactly the Alpha92 outline.
 */
final class Alpha92BakedDialOutline {
    static final int W=1440,H=1440;
    static final double CX=720.0,CY=720.0,R=480.0;
    private static final int BRIGHT=Color.rgb(255,255,0);
    private static final float STROKE=4.0f;

    private Alpha92BakedDialOutline(){}

    static Bitmap bitmap(ModelSpec model){
        Bitmap out=Bitmap.createBitmap(W,H,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);c.drawColor(Color.TRANSPARENT);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(BRIGHT);p.setAlpha(255);

        c.drawCircle((float)CX,(float)CY,(float)(R*1.0),p);

        for(int i=0;i<60;i++){
            double a=Alpha92GmtMaster.angleForMinute(i),ca=Math.cos(a),sa=Math.sin(a);
            c.drawLine(
                    (float)(CX+R*model.minuteTrackInnerR*ca),
                    (float)(CY+R*model.minuteTrackInnerR*sa),
                    (float)(CX+R*model.minuteTrackOuterR*ca),
                    (float)(CY+R*model.minuteTrackOuterR*sa),p);
        }

        for(ModelSpec.Marker m:model.withShape(ModelSpec.Shape.ROUND)){
            double a=Alpha92GmtMaster.angleForHour(m.hour);
            float x=(float)(CX+R*m.centreR*Math.cos(a));
            float y=(float)(CY+R*m.centreR*Math.sin(a));
            c.drawCircle(x,y,(float)(R*m.outerR),p);
        }
        for(ModelSpec.Marker m:model.withShape(ModelSpec.Shape.BATON))drawBaton(c,p,m);
        for(ModelSpec.Marker m:model.withShape(ModelSpec.Shape.TRIANGLE))drawTriangle(c,p,m);
        return out;
    }

    private static void drawBaton(Canvas c,Paint p,ModelSpec.Marker m){
        double a=Alpha92GmtMaster.angleForHour(m.hour),ca=Math.cos(a),sa=Math.sin(a);
        double tx=-sa,ty=ca,cr=m.centreR;
        double rh=m.radialHalf,th=m.tangentialHalf;
        Path path=new Path();
        for(int k=0;k<4;k++){
            double rr=cr+((k==0||k==1)?rh:-rh);
            double tt=((k==0||k==3)?-th:th);
            float x=(float)(CX+R*(rr*ca+tt*tx)),y=(float)(CY+R*(rr*sa+tt*ty));
            if(k==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        path.close();c.drawPath(path,p);
    }

    private static void drawTriangle(Canvas c,Paint p,ModelSpec.Marker m){
        double[][] v=m.trianglePolygon();   // apex, base right, base left
        Path path=new Path();
        path.moveTo((float)(CX+R*v[2][0]),(float)(CY+R*v[2][1]));
        path.lineTo((float)(CX+R*v[1][0]),(float)(CY+R*v[1][1]));
        path.lineTo((float)(CX+R*v[0][0]),(float)(CY+R*v[0][1]));
        path.close();c.drawPath(path,p);
    }
}
