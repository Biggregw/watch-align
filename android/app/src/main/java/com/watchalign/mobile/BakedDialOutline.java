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
 * IMPORTANT PROOF PROPERTY:
 * Every visible element below, including the radial construction rays and baton
 * rectangles, is drawn into this one canonical flat bitmap BEFORE any camera
 * perspective is applied. AutomaticDialOverlay then warps this bitmap once as a
 * whole. Nothing in this class is drawn onto the candidate after that warp.
 *
 * The complete rendered geometry is fixed: dial edge, 60 minute ticks, twelve
 * full hour construction rays, twelve bezel-only half-hour/GMT rays, eight round
 * surrounds, 6/9 baton surrounds and the 12 triangle. The candidate can only
 * supply a camera pose; it cannot alter any of these shapes or positions.
 *
 * The bezel rays are visual aids only. They are not evidence for pose fitting.
 * Because the bezel sits above the dial plane, strong oblique views may show real
 * bezel/dial parallax even when both are mechanically aligned.
 */
final class BakedDialOutline {
    // Larger transparent canvas lets the same canonical dial master extend rays
    // beyond the dial edge into the bezel without clipping. R remains 480 px, so
    // all canonical dial geometry and normalization remain unchanged.
    static final int W=1440,H=1440;
    static final double CX=720.0,CY=720.0,R=480.0;

    private static final int BRIGHT=Color.rgb(255,255,0);
    private static final float OUTLINE_STROKE=4.00f;
    private static final float CONSTRUCTION_STROKE=3.40f;
    private static final float BEZEL_INTERMEDIATE_STROKE=3.00f;
    private static final int CONSTRUCTION_ALPHA=255;

    // Visual-only canonical radii. 1.0 is the physical black-dial edge.
    private static final double BEZEL_RAY_OUTER_R=1.40;
    private static final double BEZEL_ONLY_INNER_R=1.035;

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
        final double tickOuter=Gmt126710BlnrMaster.MINUTE_TRACK_OUTER_R;
        for(int i=0;i<60;i++){
            double a=Gmt126710BlnrMaster.angleForMinute(i);
            double ca=Math.cos(a),sa=Math.sin(a);
            float x1=(float)(CX+R*tickInner*ca);
            float y1=(float)(CY+R*tickInner*sa);
            float x2=(float)(CX+R*tickOuter*ca);
            float y2=(float)(CY+R*tickOuter*sa);
            c.drawLine(x1,y1,x2,y2,p);
        }

        // Proof/construction rays are part of the canonical master itself. All are
        // created here before perspective and therefore receive the exact same one
        // projective warp as the baton sides, circles, triangle and minute ticks.
        float oldWidth=p.getStrokeWidth();
        int oldAlpha=p.getAlpha();
        int oldColor=p.getColor();
        p.setAlpha(CONSTRUCTION_ALPHA);
        p.setColor(Color.WHITE);

        // Twelve primary hour rays. These start at the canonical dial centre and now
        // continue beyond the dial into the bezel region for visual alignment checks.
        p.setStrokeWidth(CONSTRUCTION_STROKE);
        for(int hour=0;hour<12;hour++){
            int h=(hour==0)?12:hour;
            double a=Gmt126710BlnrMaster.angleForHour(h);
            drawRadialSegment(c,p,a,0.0,BEZEL_RAY_OUTER_R);
        }

        // Twelve additional bezel-only rays at the intermediate 15-degree GMT
        // positions. They deliberately begin outside the physical dial so the dial
        // QC view is not doubled to 24 spokes. Together the bezel region shows all
        // 24 canonical GMT-hour directions.
        p.setStrokeWidth(BEZEL_INTERMEDIATE_STROKE);
        for(int i=0;i<12;i++){
            double a=Math.toRadians(i*30.0-75.0); // halfway between each 30-degree hour ray
            drawRadialSegment(c,p,a,BEZEL_ONLY_INNER_R,BEZEL_RAY_OUTER_R);
        }

        p.setStrokeWidth(oldWidth);
        p.setAlpha(oldAlpha);
        p.setColor(oldColor);

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

    private static void drawRadialSegment(Canvas c,Paint p,double a,double r1,double r2){
        double ca=Math.cos(a),sa=Math.sin(a);
        float x1=(float)(CX+R*r1*ca);
        float y1=(float)(CY+R*r1*sa);
        float x2=(float)(CX+R*r2*ca);
        float y2=(float)(CY+R*r2*sa);
        c.drawLine(x1,y1,x2,y2,p);
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
