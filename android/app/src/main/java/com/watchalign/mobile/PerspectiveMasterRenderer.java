package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

/** Deterministic perspective renderer for the fixed model master. */
final class PerspectiveMasterRenderer {
    static final class Pose {
        float centerX, centerY;
        float scalePx;
        float pitchDeg, yawDeg, rollDeg;
        float alpha = 1f;
        Pose copy(){Pose p=new Pose();p.centerX=centerX;p.centerY=centerY;p.scalePx=scalePx;p.pitchDeg=pitchDeg;p.yawDeg=yawDeg;p.rollDeg=rollDeg;p.alpha=alpha;return p;}
    }

    static Bitmap render(Bitmap base,String modelRef,Pose pose){
        Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);
        if(!Gmt126710BlnrMaster.supports(modelRef))return out;
        drawMaster(new Canvas(out),out.getWidth(),out.getHeight(),modelRef,pose,true);
        return out;
    }

    static Bitmap renderOverlay(int width,int height,String modelRef,Pose pose){
        Bitmap out=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        if(!Gmt126710BlnrMaster.supports(modelRef))return out;
        drawMaster(new Canvas(out),width,height,modelRef,pose,true);
        return out;
    }

    static Bitmap renderGuides(Bitmap base,String modelRef,Pose pose){
        Bitmap out=base.copy(Bitmap.Config.ARGB_8888,true);
        if(!Gmt126710BlnrMaster.supports(modelRef))return out;
        drawMaster(new Canvas(out),out.getWidth(),out.getHeight(),modelRef,pose,false);
        return out;
    }

    private static void drawMaster(Canvas c,int width,int height,String modelRef,Pose pose,boolean markers){
        float unit=Math.max(1f,Math.min(width,height)/900f);
        int a=Math.max(0,Math.min(255,Math.round(255*pose.alpha)));
        int ai=Math.max(0,Math.min(255,Math.round(205*pose.alpha)));
        int ag=Math.max(0,Math.min(255,Math.round(225*pose.alpha)));
        Paint outer=paint(Color.rgb(255,28,28),1.8f*unit,a);
        Paint inner=paint(Color.WHITE,1.05f*unit,ai);
        Paint guide=paint(Color.rgb(255,55,55),1.45f*unit,ag);
        Paint tick=paint(Color.rgb(255,105,105),0.85f*unit,Math.max(0,Math.min(255,Math.round(175*pose.alpha))));
        Paint centre=paint(Color.rgb(255,225,0),2.2f*unit,255);

        drawCircle(c,pose,Gmt126710BlnrMaster.DIAL_EDGE_R,guide);
        drawCircle(c,pose,Gmt126710BlnrMaster.MINUTE_TRACK_R,guide);
        drawMinuteTicks(c,pose,tick);
        drawCrosshair(c,pose,centre,unit);
        if(!markers)return;

        for(int h:new int[]{1,2,4,5,7,8,10,11}){
            double ang=Gmt126710BlnrMaster.angleForHour(h);
            drawRound(c,pose,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.ROUND_OUTER_R,ang,outer);
            drawRound(c,pose,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.ROUND_LUME_R,ang,inner);
        }
        for(int h:new int[]{6,9}){
            double ang=Gmt126710BlnrMaster.angleForHour(h);
            drawRect(c,pose,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF,Gmt126710BlnrMaster.BATON_RADIAL_HALF,ang,outer);
            drawRect(c,pose,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.BATON_LUME_TANGENTIAL_HALF,Gmt126710BlnrMaster.BATON_LUME_RADIAL_HALF,ang,inner);
        }
        drawTriangle(c,pose,outer,false);
        drawTriangle(c,pose,inner,true);
    }

    private static void drawMinuteTicks(Canvas c,Pose p,Paint paint){
        for(int i=0;i<60;i++){
            double a=Math.toRadians(i*6.0-90.0);
            double in=Gmt126710BlnrMaster.MINUTE_TICK_INNER_R;
            double out=Gmt126710BlnrMaster.MINUTE_TICK_OUTER_R;
            if(i%5==0)in-=0.012;
            PointF p1=project(p,in*Math.cos(a),in*Math.sin(a));
            PointF p2=project(p,out*Math.cos(a),out*Math.sin(a));
            c.drawLine(p1.x,p1.y,p2.x,p2.y,paint);
        }
    }

    private static void drawCrosshair(Canvas c,Pose p,Paint paint,float unit){
        float r=10f*unit;
        c.drawLine(p.centerX-r,p.centerY,p.centerX+r,p.centerY,paint);
        c.drawLine(p.centerX,p.centerY-r,p.centerX,p.centerY+r,paint);
        c.drawCircle(p.centerX,p.centerY,3.3f*unit,paint);
    }

    private static PointF project(Pose p,double x,double y){
        double pitch=Math.toRadians(p.pitchDeg),yaw=Math.toRadians(p.yawDeg),roll=Math.toRadians(p.rollDeg);
        double cp=Math.cos(pitch),sp=Math.sin(pitch),cy=Math.cos(yaw),sy=Math.sin(yaw),cr=Math.cos(roll),sr=Math.sin(roll);
        double y1=y*cp,z1=y*sp;
        double x2=x*cy+z1*sy;
        double z2=-x*sy+z1*cy;
        double y2=y1;
        double xr=x2*cr-y2*sr;
        double yr=x2*sr+y2*cr;
        double camera=3.20;
        double denom=Math.max(1.25,camera-z2);
        double perspective=camera/denom;
        return new PointF(p.centerX+(float)(p.scalePx*xr*perspective),p.centerY+(float)(p.scalePx*yr*perspective));
    }
    private static void drawCircle(Canvas c,Pose p,double r,Paint paint){Path path=new Path();for(int i=0;i<=180;i++){double a=2*Math.PI*i/180.0;PointF q=project(p,r*Math.cos(a),r*Math.sin(a));if(i==0)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);}c.drawPath(path,paint);}
    private static void drawRound(Canvas c,Pose p,double rr,double size,double a,Paint paint){double cx=rr*Math.cos(a),cy=rr*Math.sin(a);Path path=new Path();for(int i=0;i<=64;i++){double q=2*Math.PI*i/64.0;PointF z=project(p,cx+size*Math.cos(q),cy+size*Math.sin(q));if(i==0)path.moveTo(z.x,z.y);else path.lineTo(z.x,z.y);}path.close();c.drawPath(path,paint);}
    private static void drawRect(Canvas c,Pose p,double rr,double tang,double radial,double a,Paint paint){double cx=rr*Math.cos(a),cy=rr*Math.sin(a),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;double[][] pts={{cx-ux*radial-vx*tang,cy-uy*radial-vy*tang},{cx-ux*radial+vx*tang,cy-uy*radial+vy*tang},{cx+ux*radial+vx*tang,cy+uy*radial+vy*tang},{cx+ux*radial-vx*tang,cy+uy*radial-vy*tang}};drawPoly(c,p,pts,paint);}
    private static void drawTriangle(Canvas c,Pose p,Paint paint,boolean lume){double a=Gmt126710BlnrMaster.angleForHour(12),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;double center=lume?Gmt126710BlnrMaster.TRI_LUME_CENTER_R:Gmt126710BlnrMaster.TRI_CENTER_R;double baseOut=lume?Gmt126710BlnrMaster.TRI_LUME_BASE_OUTWARD:Gmt126710BlnrMaster.TRI_BASE_OUTWARD;double apexIn=lume?Gmt126710BlnrMaster.TRI_LUME_APEX_INWARD:Gmt126710BlnrMaster.TRI_APEX_INWARD;double half=lume?Gmt126710BlnrMaster.TRI_LUME_HALF_BASE:Gmt126710BlnrMaster.TRI_HALF_BASE;double cx=center*ux,cy=center*uy;double[][] pts={{cx+ux*baseOut+vx*half,cy+uy*baseOut+vy*half},{cx+ux*baseOut-vx*half,cy+uy*baseOut-vy*half},{cx-ux*apexIn,cy-uy*apexIn}};drawPoly(c,p,pts,paint);}
    private static void drawPoly(Canvas c,Pose p,double[][] pts,Paint paint){Path path=new Path();for(int i=0;i<pts.length;i++){PointF q=project(p,pts[i][0],pts[i][1]);if(i==0)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);}path.close();c.drawPath(path,paint);}
    private static Paint paint(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);p.setAlpha(alpha);return p;}
    private PerspectiveMasterRenderer(){}
}
