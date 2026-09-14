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
 *
 * IMPORTANT: the yellow ideal bodies are built as CLEAN canonical primitives before projection.
 * A round applied marker is a true circle in the face-on dial plane, 6/9 are true rectangles,
 * 12 is the calibrated triangle and 3 is the date-aperture rectangle. We never draw a noisy
 * photographed contour as the ideal body. The homography is applied only after the canonical
 * primitive has been constructed, so a round marker can become a smooth perspective conic but
 * can never become the lumpy polygon produced by the old trace-based overlay.
 */
final class Gmt126710IdealOverlay {
    private static final int ROUND_SEGMENTS = 96;

    /*
     * Round-body size comes from the genuine-reference visual master. The first-party automatic
     * trace is still useful for the marker-centre ring, but its sparse outline is not an ideal shape.
     */
    private static final double ROUND_REFERENCE_R = Gmt126710BlnrMaster.ROUND_OUTER_R;

    /*
     * The catalogue baton trace clipped the long bright inner edge. Preserve the well-located outer
     * end from that trace, but use the genuine-reference master for the inner end. This produces one
     * clean canonical rectangle rather than centring a too-short box on the clipped contour.
     */
    private static final double BATON_REFERENCE_OUTER_R = Math.min(
            Gmt126710BlnrMeasured.BATON_CENTER_R + Gmt126710BlnrMeasured.BATON_RADIAL_HALF,
            Gmt126710BlnrMaster.MINUTE_TRACK_R - 0.002);
    private static final double BATON_REFERENCE_INNER_R =
            Gmt126710BlnrMaster.MARKER_CENTER_R - Gmt126710BlnrMaster.BATON_RADIAL_HALF;
    private static final double BATON_REFERENCE_CENTER_R =
            (BATON_REFERENCE_OUTER_R + BATON_REFERENCE_INNER_R) * 0.5;
    private static final double BATON_REFERENCE_RADIAL_HALF =
            (BATON_REFERENCE_OUTER_R - BATON_REFERENCE_INNER_R) * 0.5;
    private static final double BATON_REFERENCE_TANGENTIAL_HALF =
            Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF;

    private Gmt126710IdealOverlay() {}

    /** Image-calibrated round-marker centre ring. */
    static double markerCenterRadius() { return Gmt126710BlnrMeasured.MARKER_CENTER_R; }

    /** 6/9 use the geometric midpoint of the corrected full applied-metal body. */
    static double markerCenterRadius(int hour) {
        return (hour == 6 || hour == 9) ? BATON_REFERENCE_CENTER_R : Gmt126710BlnrMeasured.MARKER_CENTER_R;
    }

    static double roundReferenceRadius() { return ROUND_REFERENCE_R; }
    static double batonReferenceInnerRadius() { return BATON_REFERENCE_INNER_R; }
    static double batonReferenceOuterRadius() { return BATON_REFERENCE_OUTER_R; }

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

        // Full expected applied-marker bodies. At 3 o'clock the date aperture replaces the marker.
        for (int hour = 1; hour <= 12; hour++) {
            if (hour == 3) drawDateAperture(c, pose, reference);
            else if (hour == 12) drawTriangle(c, pose, reference);
            else if (hour == 6 || hour == 9) drawBaton(c, pose, hour, reference);
            else drawRoundMarker(c, pose, hour, reference);
        }
    }

    static PointF idealMarker(PerspectiveMasterRenderer.Pose pose, int hour, double ignoredLegacyRadius) {
        int h = ((hour % 12) + 12) % 12;
        if (h == 0) {
            PointF[] q = idealTriangle(pose);
            return new PointF((q[0].x + q[1].x + q[2].x) / 3f, (q[0].y + q[1].y + q[2].y) / 3f);
        }
        double a = Math.toRadians(h * 30.0), r = markerCenterRadius(h);
        return project(pose, r * Math.sin(a), -r * Math.cos(a));
    }

    static PointF[] idealTriangle(PerspectiveMasterRenderer.Pose pose) {
        float[][] local = Gmt126710BlnrMeasured.TRI_OUTER;
        double cr = Gmt126710BlnrMeasured.TRI_CENTER_R;
        return bodyPoints(pose, 12, cr, local);
    }

    static PointF[] idealDateAperture(PerspectiveMasterRenderer.Pose pose) {
        return bodyPoints(pose, 3, Gmt126710BlnrMeasured.DATE_CENTER_R, Gmt126710BlnrMeasured.DATE_APERTURE_OUTER);
    }

    private static void drawRoundMarker(Canvas c, PerspectiveMasterRenderer.Pose pose, int hour, Paint p) {
        PointF[] q = new PointF[ROUND_SEGMENTS];
        for (int i = 0; i < ROUND_SEGMENTS; i++) {
            double t = 2.0 * Math.PI * i / ROUND_SEGMENTS;
            double tangential = ROUND_REFERENCE_R * Math.cos(t);
            double radialOffset = ROUND_REFERENCE_R * Math.sin(t);
            q[i] = bodyPoint(pose, hour, Gmt126710BlnrMeasured.MARKER_CENTER_R, tangential, radialOffset);
        }
        drawClosed(c, q, p);
    }

    private static void drawBaton(Canvas c, PerspectiveMasterRenderer.Pose pose, int hour, Paint p) {
        float w = (float) BATON_REFERENCE_TANGENTIAL_HALF;
        float h = (float) BATON_REFERENCE_RADIAL_HALF;
        float[][] local = {
                {-w, -h},
                { w, -h},
                { w,  h},
                {-w,  h}
        };
        drawClosed(c, bodyPoints(pose, hour, BATON_REFERENCE_CENTER_R, local), p);
    }

    private static void drawTriangle(Canvas c, PerspectiveMasterRenderer.Pose pose, Paint p) {
        drawClosed(c, idealTriangle(pose), p);
    }

    private static void drawDateAperture(Canvas c, PerspectiveMasterRenderer.Pose pose, Paint p) {
        drawClosed(c, idealDateAperture(pose), p);
    }

    /**
     * local[x,y] convention: x is tangential; +y is radially outward.
     * The entire canonical body is transformed through the SAME homography as its centre.
     */
    private static PointF[] bodyPoints(PerspectiveMasterRenderer.Pose pose, int hour, double centreR, float[][] local) {
        PointF[] q = new PointF[local.length];
        for (int i = 0; i < local.length; i++) {
            q[i] = bodyPoint(pose, hour, centreR, local[i][0], local[i][1]);
        }
        return q;
    }

    private static PointF bodyPoint(PerspectiveMasterRenderer.Pose pose, int hour, double centreR, double tangential, double radialOffset) {
        double a = Math.toRadians(hour * 30.0);
        double urx = Math.sin(a), ury = -Math.cos(a);
        double utx = Math.cos(a), uty = Math.sin(a);
        double radial = centreR + radialOffset;
        return project(pose, urx * radial + utx * tangential, ury * radial + uty * tangential);
    }

    private static void drawClosed(Canvas c, PointF[] q, Paint p) {
        if (q == null || q.length < 2) return;
        Path path = new Path();
        path.moveTo(q[0].x, q[0].y);
        for (int i = 1; i < q.length; i++) path.lineTo(q[i].x, q[i].y);
        path.close();
        c.drawPath(path, p);
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
        p.setStrokeJoin(Paint.Join.ROUND);
        return p;
    }
}
