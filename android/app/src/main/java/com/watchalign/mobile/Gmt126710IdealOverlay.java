package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

/**
 * Perspective-projected ideal-layout overlay for the Rolex GMT-Master II 126710 family.
 *
 * Angular relationships are mathematical: 12 hour axes at 30 degrees, 60 minute axes at
 * 6 degrees, exact opposite/mirror relationships. Marker body dimensions use the current
 * calibrated 126710 design model and are deliberately kept separate from tolerance decisions.
 */
final class Gmt126710IdealOverlay {
    private Gmt126710IdealOverlay() {}

    static double markerCenterRadius() { return Gmt126710BlnrMaster.MARKER_CENTER_R; }

    static void draw(Bitmap out, PerspectiveMasterRenderer.Pose pose, double ignoredLegacyRadius) {
        if (out == null || pose == null) return;
        Canvas c = new Canvas(out);
        float u = Math.max(1f, Math.min(out.getWidth(), out.getHeight()) / 900f);

        Paint axis = stroke(Color.argb(165, 0, 220, 255), 1.25f * u);
        Paint cardinal = stroke(Color.argb(220, 0, 255, 255), 2.2f * u);
        Paint ideal = stroke(Color.argb(245, 255, 215, 0), 2.0f * u);
        Paint tick = stroke(Color.argb(130, 255, 255, 255), 0.9f * u);

        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6.0);
            PointF p1 = project(pose, Gmt126710BlnrMaster.MINUTE_TICK_INNER_R * Math.sin(a), -Gmt126710BlnrMaster.MINUTE_TICK_INNER_R * Math.cos(a));
            PointF p2 = project(pose, Gmt126710BlnrMaster.MINUTE_TICK_OUTER_R * Math.sin(a), -Gmt126710BlnrMaster.MINUTE_TICK_OUTER_R * Math.cos(a));
            c.drawLine(p1.x, p1.y, p2.x, p2.y, tick);
        }

        for (int h = 0; h < 12; h++) {
            double a = Math.toRadians(h * 30.0);
            PointF inner = project(pose, 0.18 * Math.sin(a), -0.18 * Math.cos(a));
            PointF outer = project(pose, 0.88 * Math.sin(a), -0.88 * Math.cos(a));
            c.drawLine(inner.x, inner.y, outer.x, outer.y, h % 3 == 0 ? cardinal : axis);
        }

        // Full expected applied-marker bodies. 3 o'clock is intentionally left to the date module.
        for (int hour = 1; hour <= 12; hour++) {
            if (hour == 3) continue;
            if (hour == 12) drawTriangle(c, pose, ideal);
            else if (hour == 6 || hour == 9) drawBaton(c, pose, hour, ideal);
            else drawRound(c, pose, hour, ideal);
        }
    }

    static PointF idealMarker(PerspectiveMasterRenderer.Pose pose, int hour, double ignoredLegacyRadius) {
        int h = ((hour % 12) + 12) % 12;
        double a = Math.toRadians(h * 30.0);
        double r = Gmt126710BlnrMaster.MARKER_CENTER_R;
        if (h == 0) r = Gmt126710BlnrMaster.TRI_CENTER_R;
        return project(pose, r * Math.sin(a), -r * Math.cos(a));
    }

    static PointF[] idealTriangle(PerspectiveMasterRenderer.Pose pose) {
        double baseR = Gmt126710BlnrMaster.TRI_CENTER_R + Gmt126710BlnrMaster.TRI_BASE_OUTWARD;
        double apexR = Gmt126710BlnrMaster.TRI_CENTER_R - Gmt126710BlnrMaster.TRI_APEX_INWARD;
        return new PointF[] {
                project(pose, -Gmt126710BlnrMaster.TRI_HALF_BASE, -baseR),
                project(pose,  Gmt126710BlnrMaster.TRI_HALF_BASE, -baseR),
                project(pose, 0, -apexR)
        };
    }

    private static void drawTriangle(Canvas c, PerspectiveMasterRenderer.Pose pose, Paint p) {
        PointF[] q = idealTriangle(pose);
        Path path = new Path();path.moveTo(q[0].x,q[0].y);path.lineTo(q[1].x,q[1].y);path.lineTo(q[2].x,q[2].y);path.close();c.drawPath(path,p);
    }

    private static void drawRound(Canvas c, PerspectiveMasterRenderer.Pose pose, int hour, Paint p) {
        double a = Math.toRadians(hour * 30.0), urx = Math.sin(a), ury = -Math.cos(a), utx = Math.cos(a), uty = Math.sin(a);
        double cr = Gmt126710BlnrMaster.MARKER_CENTER_R, rr = Gmt126710BlnrMaster.ROUND_OUTER_R;
        Path path = new Path();
        for (int i=0;i<=48;i++) {
            double t=2*Math.PI*i/48.0,radial=cr+rr*Math.cos(t),tan=rr*Math.sin(t);
            PointF q=project(pose,urx*radial+utx*tan,ury*radial+uty*tan);
            if(i==0)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);
        }
        path.close();c.drawPath(path,p);
    }

    private static void drawBaton(Canvas c, PerspectiveMasterRenderer.Pose pose, int hour, Paint p) {
        double a=Math.toRadians(hour*30.0),urx=Math.sin(a),ury=-Math.cos(a),utx=Math.cos(a),uty=Math.sin(a),r=Gmt126710BlnrMaster.MARKER_CENTER_R;
        double rh=Gmt126710BlnrMaster.BATON_RADIAL_HALF,th=Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF;
        PointF[] q=new PointF[]{
                project(pose,urx*(r-rh)-utx*th,ury*(r-rh)-uty*th),
                project(pose,urx*(r-rh)+utx*th,ury*(r-rh)+uty*th),
                project(pose,urx*(r+rh)+utx*th,ury*(r+rh)+uty*th),
                project(pose,urx*(r+rh)-utx*th,ury*(r+rh)-uty*th)};
        Path path=new Path();path.moveTo(q[0].x,q[0].y);for(int i=1;i<4;i++)path.lineTo(q[i].x,q[i].y);path.close();c.drawPath(path,p);
    }

    private static PointF project(PerspectiveMasterRenderer.Pose pose, double x, double y) {return PerspectiveMasterRenderer.projectPoint(pose, x, y);}
    private static Paint stroke(int color, float width) {Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);return p;}
}
