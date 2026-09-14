package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

/**
 * Perspective-projected GMT 126710 reference overlay.
 *
 * The angular scaffold is mathematical: 12 hour axes at 30 degrees, 60 minute axes at 6 degrees,
 * exact opposite/mirror relationships. Applied-marker positions/body dimensions are image-derived
 * 126710 references and are deliberately kept separate from factory-tolerance claims.
 */
final class Gmt126710IdealOverlay {
    private Gmt126710IdealOverlay() {}

    /** Image-calibrated round-marker centre radius from the first-party 126710BLNR reference. */
    static double markerCenterRadius() { return Gmt126710BlnrMeasured.MARKER_CENTER_R; }
    static double markerCenterRadius(int hour) {return (hour==6||hour==9)?Gmt126710BlnrMeasured.BATON_CENTER_R:Gmt126710BlnrMeasured.MARKER_CENTER_R;}

    static void draw(Bitmap out, PerspectiveMasterRenderer.Pose pose, double ignoredLegacyRadius) {
        if (out == null || pose == null) return;
        Canvas c = new Canvas(out);
        float u = Math.max(1f, Math.min(out.getWidth(), out.getHeight()) / 900f);

        Paint axis = stroke(Color.argb(165, 0, 220, 255), 1.25f * u);
        Paint cardinal = stroke(Color.argb(220, 0, 255, 255), 2.2f * u);
        Paint reference = stroke(Color.argb(245, 255, 215, 0), 2.0f * u);
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
            PointF outer = project(pose, 0.90 * Math.sin(a), -0.90 * Math.cos(a));
            c.drawLine(inner.x, inner.y, outer.x, outer.y, h % 3 == 0 ? cardinal : axis);
        }

        // Full expected applied-marker bodies. 3 o'clock is intentionally left to the date module.
        // Body shapes/radii are calibrated references; only their angular axes are mathematically exact.
        for (int hour = 1; hour <= 12; hour++) {
            if (hour == 3) continue;
            if (hour == 12) drawTriangle(c, pose, reference);
            else if (hour == 6 || hour == 9) drawBaton(c, pose, hour, reference);
            else drawRound(c, pose, hour, reference);
        }
    }

    static PointF idealMarker(PerspectiveMasterRenderer.Pose pose, int hour, double ignoredLegacyRadius) {
        int h = ((hour % 12) + 12) % 12;
        if(h==0){PointF[] q=idealTriangle(pose);return new PointF((q[0].x+q[1].x+q[2].x)/3f,(q[0].y+q[1].y+q[2].y)/3f);}
        double a = Math.toRadians(h * 30.0),r=markerCenterRadius(h);
        return project(pose, r * Math.sin(a), -r * Math.cos(a));
    }

    static PointF[] idealTriangle(PerspectiveMasterRenderer.Pose pose) {
        float[][] local=Gmt126710BlnrMeasured.TRI_OUTER;double cr=Gmt126710BlnrMeasured.TRI_CENTER_R;
        return new PointF[] {
                project(pose, local[0][0], -(cr+local[0][1])),
                project(pose, local[1][0], -(cr+local[1][1])),
                project(pose, local[2][0], -(cr+local[2][1]))
        };
    }

    private static void drawTriangle(Canvas c, PerspectiveMasterRenderer.Pose pose, Paint p) {
        PointF[] q = idealTriangle(pose);
        Path path = new Path();path.moveTo(q[0].x,q[0].y);path.lineTo(q[1].x,q[1].y);path.lineTo(q[2].x,q[2].y);path.close();c.drawPath(path,p);
    }

    private static void drawRound(Canvas c, PerspectiveMasterRenderer.Pose pose, int hour, Paint p) {
        double a = Math.toRadians(hour * 30.0), urx = Math.sin(a), ury = -Math.cos(a), utx = Math.cos(a), uty = Math.sin(a);
        double cr = Gmt126710BlnrMeasured.MARKER_CENTER_R, rr = Gmt126710BlnrMaster.ROUND_OUTER_R;
        Path path = new Path();
        for (int i=0;i<=48;i++) {
            double t=2*Math.PI*i/48.0,radial=cr+rr*Math.cos(t),tan=rr*Math.sin(t);
            PointF q=project(pose,urx*radial+utx*tan,ury*radial+uty*tan);
            if(i==0)path.moveTo(q.x,q.y);else path.lineTo(q.x,q.y);
        }
        path.close();c.drawPath(path,p);
    }

    private static void drawBaton(Canvas c, PerspectiveMasterRenderer.Pose pose, int hour, Paint p) {
        double a=Math.toRadians(hour*30.0),urx=Math.sin(a),ury=-Math.cos(a),utx=Math.cos(a),uty=Math.sin(a),r=Gmt126710BlnrMeasured.BATON_CENTER_R;
        double rh=.113824,th=.071566;
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
