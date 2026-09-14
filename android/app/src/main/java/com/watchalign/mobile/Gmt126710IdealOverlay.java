package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;

/**
 * Mathematical ideal-layout overlay for the Rolex GMT-Master II 126710 family.
 *
 * This deliberately contains only geometry that is true by construction after rectification:
 *  - a circular dial coordinate system;
 *  - 12 hour axes exactly 30 degrees apart;
 *  - 60 minute axes exactly 6 degrees apart;
 *  - 12/6 and 3/9 are exact perpendicular diameters;
 *  - mirror/opposite relationships are exact.
 *
 * The marker-ring radius is supplied by the caller because its absolute design radius is a
 * model dimension, not something derivable from circle geometry alone. This lets the app test
 * alignment independently from genuine-population tolerances.
 */
final class Gmt126710IdealOverlay {
    private Gmt126710IdealOverlay() {}

    static void draw(Bitmap out, PerspectiveMasterRenderer.Pose pose, double markerRingRadius) {
        if (out == null || pose == null) return;
        Canvas c = new Canvas(out);
        float u = Math.max(1f, Math.min(out.getWidth(), out.getHeight()) / 900f);

        Paint axis = stroke(Color.argb(190, 0, 220, 255), 1.4f * u);
        Paint cardinal = stroke(Color.argb(225, 0, 255, 255), 2.4f * u);
        Paint marker = stroke(Color.argb(230, 255, 215, 0), 2.0f * u);
        Paint tick = stroke(Color.argb(150, 255, 255, 255), 1.0f * u);

        // Exact minute axes. Draw only the outer segment to avoid obscuring the dial.
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6.0);
            PointF p1 = project(pose, 0.91 * Math.sin(a), -0.91 * Math.cos(a));
            PointF p2 = project(pose, 0.95 * Math.sin(a), -0.95 * Math.cos(a));
            c.drawLine(p1.x, p1.y, p2.x, p2.y, tick);
        }

        // Exact 12-hour axes. Cardinal axes are emphasized because they define rectification.
        for (int h = 0; h < 12; h++) {
            double a = Math.toRadians(h * 30.0);
            PointF inner = project(pose, 0.18 * Math.sin(a), -0.18 * Math.cos(a));
            PointF outer = project(pose, 0.88 * Math.sin(a), -0.88 * Math.cos(a));
            c.drawLine(inner.x, inner.y, outer.x, outer.y, h % 3 == 0 ? cardinal : axis);
        }

        // Ideal marker centres on a common ring. The supplied ring radius is a nuisance/design
        // parameter; angular placement is exact and independent of it.
        double r = Double.isFinite(markerRingRadius) && markerRingRadius > 0.45 && markerRingRadius < 0.90
                ? markerRingRadius : 0.72;
        for (int h = 0; h < 12; h++) {
            double a = Math.toRadians(h * 30.0);
            PointF p = project(pose, r * Math.sin(a), -r * Math.cos(a));
            c.drawCircle(p.x, p.y, 5.5f * u, marker);
        }
    }

    static PointF idealMarker(PerspectiveMasterRenderer.Pose pose, int hour, double radius) {
        int h = ((hour % 12) + 12) % 12;
        double a = Math.toRadians(h * 30.0);
        return project(pose, radius * Math.sin(a), -radius * Math.cos(a));
    }

    private static PointF project(PerspectiveMasterRenderer.Pose pose, double x, double y) {
        return PerspectiveMasterRenderer.projectPoint(pose, x, y);
    }

    private static Paint stroke(int color, float width) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        return p;
    }
}
