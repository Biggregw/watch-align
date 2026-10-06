package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Overlay-only proof of concept for GMT perspective recovery.
 *
 * The user supplies two SAFE anchors: the dial edge at 12 and 6. Those establish
 * only an initial centre/radius/clock direction. The projective transform itself
 * is then fitted from the 48 minor minute ticks. Hour markers and the 12 triangle
 * are deliberately excluded from the pose fit and are drawn only afterwards, so
 * their visual agreement with the projected master remains an independent test.
 *
 * This class makes no QC measurements and no pass/fail judgement.
 */
final class MinuteTrackPerspectiveOverlay {
    static final class Result {
        final Bitmap overlay;
        final boolean valid;
        final String reason;
        final int detectedTicks;
        final int inlierTicks;

        Result(Bitmap overlay, int detectedTicks, int inlierTicks) {
            this.overlay = overlay;
            this.valid = overlay != null;
            this.reason = "";
            this.detectedTicks = detectedTicks;
            this.inlierTicks = inlierTicks;
        }

        Result(String reason, int detectedTicks) {
            this.overlay = null;
            this.valid = false;
            this.reason = reason == null ? "minute-track pose could not be established" : reason;
            this.detectedTicks = detectedTicks;
            this.inlierTicks = 0;
        }
    }

    private static final double TRACK_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double TICK_HALF = 0.025;
    private static final double SEARCH_A_DEG = 2.55;
    private static final double SEARCH_A_STEP_DEG = 0.10;
    private static final double SIDE_A_DEG = 0.72;
    private static final int MIN_DETECTED = 18;
    private static final int MIN_INLIERS = 14;

    private static final class Tick {
        final int minute;
        final double angleDeg;
        final double x, y;
        final double score;
        Tick(int minute, double angleDeg, double x, double y, double score) {
            this.minute = minute;
            this.angleDeg = angleDeg;
            this.x = x;
            this.y = y;
            this.score = score;
        }
    }

    private MinuteTrackPerspectiveOverlay() {}

    static Result build(Bitmap input, double cx, double cy, double apparentRadius, double rollDeg) {
        if (input == null || !(apparentRadius > 40.0)) return new Result("invalid dial alignment", 0);

        Mat rgba = new Mat();
        Mat gray = new Mat();
        Mat enhanced = new Mat();
        try {
            Utils.bitmapToMat(input, rgba);
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY);
            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(gray, enhanced);

            List<Tick> ticks = new ArrayList<>();
            for (int minute = 0; minute < 60; minute++) {
                if (minute % 5 == 0) continue; // never use hour positions to fit pose
                double expected = rollDeg + minute * 6.0;
                Tick tick = locateTick(enhanced, cx, cy, apparentRadius, minute, expected);
                if (tick != null) ticks.add(tick);
            }
            if (ticks.size() < MIN_DETECTED) {
                return new Result("not enough minor minute ticks were found", ticks.size());
            }

            Mat H = fitHomography(ticks, apparentRadius);
            if (H == null || H.empty()) return new Result("minute-track homography could not be fitted", ticks.size());
            try {
                int inliers = countInliers(H, ticks, apparentRadius, Math.max(3.0, apparentRadius * 0.012));
                if (inliers < MIN_INLIERS || !sane(H, input.getWidth(), input.getHeight(), apparentRadius)) {
                    return new Result("minute-track perspective fit was not stable", ticks.size());
                }
                Bitmap overlay = render(input, H, ticks);
                return overlay == null ? new Result("overlay rendering failed", ticks.size()) : new Result(overlay, ticks.size(), inliers);
            } finally {
                H.release();
            }
        } catch (Throwable t) {
            return new Result("perspective overlay failed: " + t.getClass().getSimpleName(), 0);
        } finally {
            enhanced.release();
            gray.release();
            rgba.release();
        }
    }

    /** Find one minor tick without using any hour-marker geometry. */
    private static Tick locateTick(Mat g, double cx, double cy, double r, int minute, double expectedDeg) {
        double bestA = Double.NaN, best = -Double.MAX_VALUE;
        for (double a = expectedDeg - SEARCH_A_DEG; a <= expectedDeg + SEARCH_A_DEG + 1e-9; a += SEARCH_A_STEP_DEG) {
            double s = tickScore(g, cx, cy, r, a);
            if (s > best) { best = s; bestA = a; }
        }
        if (!Double.isFinite(bestA) || best < 2.0) return null;

        // A real minor tick is a short radial bright feature centred close to TRACK_R.
        // Use radial contrast against two neighbouring angular rays, then take the
        // contrast-weighted centre of the physical run. That gives a consistent
        // point corresponding to the canonical TRACK_R rather than a dial-edge point.
        final double lo = 0.875, hi = 0.970, step = 0.0025;
        int n = (int)Math.floor((hi - lo) / step) + 1;
        double[] rf = new double[n], v = new double[n];
        double max = -Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double q = lo + i * step;
            rf[i] = q;
            double c = samplePolar(g, cx, cy, r * q, bestA);
            double l = samplePolar(g, cx, cy, r * q, bestA - SIDE_A_DEG);
            double rr = samplePolar(g, cx, cy, r * q, bestA + SIDE_A_DEG);
            if (!Double.isFinite(c) || !Double.isFinite(l) || !Double.isFinite(rr)) v[i] = -100;
            else v[i] = c - 0.5 * (l + rr) + Math.max(0.0, c - 145.0) * 0.06;
            if (v[i] > max) max = v[i];
        }
        if (!(max >= 5.0)) return null;

        double threshold = Math.max(3.0, max * 0.40);
        double sw = 0.0, sr = 0.0;
        for (int i = 0; i < n; i++) {
            if (v[i] < threshold) continue;
            // Keep the minute-track run, not isolated rehaut/glare pixels.
            boolean neighbour = (i > 0 && v[i - 1] >= threshold * 0.72) || (i + 1 < n && v[i + 1] >= threshold * 0.72);
            if (!neighbour) continue;
            double w = Math.max(0.1, v[i] - threshold + 0.5);
            sw += w;
            sr += w * rf[i];
        }
        if (!(sw > 0.5)) return null;
        double radialFraction = sr / sw;
        if (radialFraction < 0.885 || radialFraction > 0.958) return null;

        double a = Math.toRadians(bestA);
        double x = cx + Math.sin(a) * r * radialFraction;
        double y = cy - Math.cos(a) * r * radialFraction;
        return new Tick(minute, bestA, x, y, best);
    }

    /** Integrated radial line contrast. */
    private static double tickScore(Mat g, double cx, double cy, double r, double aDeg) {
        double[] vals = new double[32];
        int n = 0;
        for (double q = 0.875; q <= 0.970 && n < vals.length; q += 0.0065) {
            double c = samplePolar(g, cx, cy, r * q, aDeg);
            double l = samplePolar(g, cx, cy, r * q, aDeg - SIDE_A_DEG);
            double rr = samplePolar(g, cx, cy, r * q, aDeg + SIDE_A_DEG);
            if (!Double.isFinite(c) || !Double.isFinite(l) || !Double.isFinite(rr)) continue;
            vals[n++] = c - 0.5 * (l + rr) + Math.max(0.0, c - 145.0) * 0.06;
        }
        if (n < 6) return -100.0;
        Arrays.sort(vals, 0, n);
        int take = Math.max(4, n / 3);
        double s = 0.0;
        for (int i = n - take; i < n; i++) s += vals[i];
        return s / take;
    }

    private static double samplePolar(Mat g, double cx, double cy, double radius, double aDeg) {
        double a = Math.toRadians(aDeg);
        return sample(g, cx + Math.sin(a) * radius, cy - Math.cos(a) * radius);
    }

    private static double sample(Mat g, double x, double y) {
        int x0 = (int)Math.floor(x), y0 = (int)Math.floor(y);
        if (x0 < 0 || y0 < 0 || x0 + 1 >= g.cols() || y0 + 1 >= g.rows()) return Double.NaN;
        double fx = x - x0, fy = y - y0;
        double[] a = g.get(y0, x0), b = g.get(y0, x0 + 1), c = g.get(y0 + 1, x0), d = g.get(y0 + 1, x0 + 1);
        if (a == null || b == null || c == null || d == null) return Double.NaN;
        return (1 - fy) * ((1 - fx) * a[0] + fx * b[0]) + fy * ((1 - fx) * c[0] + fx * d[0]);
    }

    private static Mat fitHomography(List<Tick> ticks, double apparentRadius) {
        Point[] src = new Point[ticks.size()];
        Point[] dst = new Point[ticks.size()];
        for (int i = 0; i < ticks.size(); i++) {
            Tick t = ticks.get(i);
            double a = Math.toRadians(t.minute * 6.0 - 90.0);
            src[i] = new Point(TRACK_R * Math.cos(a), TRACK_R * Math.sin(a));
            dst[i] = new Point(t.x, t.y);
        }
        MatOfPoint2f s = new MatOfPoint2f(src), d = new MatOfPoint2f(dst);
        Mat mask = new Mat();
        try {
            double threshold = Math.max(3.0, apparentRadius * 0.012);
            Mat rough = Calib3d.findHomography(s, d, Calib3d.RANSAC, threshold, mask);
            if (rough == null || rough.empty()) return rough;

            // Refit once on the RANSAC consensus to make the displayed overlay smooth.
            List<Point> inS = new ArrayList<>(), inD = new ArrayList<>();
            for (int i = 0; i < ticks.size(); i++) {
                double[] mv = mask.get(i, 0);
                if (mv != null && mv.length > 0 && mv[0] != 0) { inS.add(src[i]); inD.add(dst[i]); }
            }
            if (inS.size() < MIN_INLIERS) return rough;
            MatOfPoint2f rs = new MatOfPoint2f(); rs.fromList(inS);
            MatOfPoint2f rd = new MatOfPoint2f(); rd.fromList(inD);
            try {
                Mat refined = Calib3d.findHomography(rs, rd, 0);
                if (refined != null && !refined.empty()) { rough.release(); return refined; }
                return rough;
            } finally { rs.release(); rd.release(); }
        } finally {
            s.release(); d.release(); mask.release();
        }
    }

    private static int countInliers(Mat H, List<Tick> ticks, double apparentRadius, double threshold) {
        int n = 0;
        for (Tick t : ticks) {
            double a = Math.toRadians(t.minute * 6.0 - 90.0);
            Point p = project(H, TRACK_R * Math.cos(a), TRACK_R * Math.sin(a));
            if (p == null) continue;
            if (Math.hypot(p.x - t.x, p.y - t.y) <= threshold) n++;
        }
        return n;
    }

    private static boolean sane(Mat H, int w, int h, double seedR) {
        Point c = project(H, 0, 0);
        if (c == null || c.x < -0.2*w || c.x > 1.2*w || c.y < -0.2*h || c.y > 1.2*h) return false;
        Point[] card = {
                project(H, 0, -1), project(H, 1, 0), project(H, 0, 1), project(H, -1, 0)
        };
        for (Point p : card) {
            if (p == null) return false;
            double q = Math.hypot(p.x - c.x, p.y - c.y);
            if (q < 0.45 * seedR || q > 1.75 * seedR) return false;
        }
        return true;
    }

    /** Transparent projected canonical master. */
    private static Bitmap render(Bitmap source, Mat H, List<Tick> ticks) {
        Bitmap out = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float scale = Math.max(1f, Math.min(out.getWidth(), out.getHeight()) / 900f);

        Paint master = paint(Color.rgb(50, 213, 242), 1.7f * scale, 245);
        Paint strong = paint(Color.rgb(50, 213, 242), 2.5f * scale, 255);
        Paint anchors = paint(Color.rgb(255, 210, 60), 1.3f * scale, 225);
        anchors.setStyle(Paint.Style.FILL);

        drawProjectedCircle(c, H, 0, 0, Gmt126710BlnrMaster.DIAL_EDGE_R, master);
        drawProjectedCircle(c, H, 0, 0, Gmt126710BlnrMaster.MINUTE_TRACK_R, master);

        // Draw the complete expected minute lattice. The 48 minor positions were pose inputs;
        // the 12 five-minute positions were not.
        for (int minute = 0; minute < 60; minute++) {
            double a = Math.toRadians(minute * 6.0 - 90.0);
            double ca = Math.cos(a), sa = Math.sin(a);
            Point p0 = project(H, (TRACK_R - TICK_HALF) * ca, (TRACK_R - TICK_HALF) * sa);
            Point p1 = project(H, (TRACK_R + TICK_HALF) * ca, (TRACK_R + TICK_HALF) * sa);
            if (p0 != null && p1 != null) c.drawLine((float)p0.x, (float)p0.y, (float)p1.x, (float)p1.y, master);
        }

        // Independent geometry: these features never contributed to the perspective fit.
        for (int hour : new int[]{1,2,4,5,7,8,10,11}) {
            double a = Gmt126710BlnrMaster.angleForHour(hour);
            double mx = Gmt126710BlnrMaster.ROUND_CENTER_R * Math.cos(a);
            double my = Gmt126710BlnrMaster.ROUND_CENTER_R * Math.sin(a);
            drawProjectedCircle(c, H, mx, my, Gmt126710BlnrMaster.ROUND_OUTER_R, strong);
        }
        drawBaton(c, H, 6, strong);
        drawBaton(c, H, 9, strong);
        drawTriangle(c, H, strong);

        // Tiny yellow dots show only the actual minor-tick points used to establish pose.
        float rr = 2.1f * scale;
        for (Tick t : ticks) c.drawCircle((float)t.x, (float)t.y, rr, anchors);
        return out;
    }

    private static void drawBaton(Canvas c, Mat H, int hour, Paint p) {
        double a = Gmt126710BlnrMaster.angleForHour(hour);
        double ux = Math.cos(a), uy = Math.sin(a), tx = -uy, ty = ux;
        double cx = Gmt126710BlnrMaster.MARKER_CENTER_R * ux;
        double cy = Gmt126710BlnrMaster.MARKER_CENTER_R * uy;
        double rh = Gmt126710BlnrMaster.BATON_RADIAL_HALF;
        double th = Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF;
        double[][] q = {
                {cx + rh*ux + th*tx, cy + rh*uy + th*ty},
                {cx + rh*ux - th*tx, cy + rh*uy - th*ty},
                {cx - rh*ux - th*tx, cy - rh*uy - th*ty},
                {cx - rh*ux + th*tx, cy - rh*uy + th*ty}
        };
        drawPolygon(c, H, q, p);
    }

    private static void drawTriangle(Canvas c, Mat H, Paint p) {
        double a = Gmt126710BlnrMaster.angleForHour(12);
        double ux = Math.cos(a), uy = Math.sin(a), tx = -uy, ty = ux;
        double baseR = Gmt126710BlnrMaster.TRI_CENTER_R + Gmt126710BlnrMaster.TRI_BASE_OUTWARD;
        double apexR = Gmt126710BlnrMaster.TRI_CENTER_R - Gmt126710BlnrMaster.TRI_APEX_INWARD;
        double half = Gmt126710BlnrMaster.TRI_HALF_BASE;
        double[][] q = {
                {baseR*ux + half*tx, baseR*uy + half*ty},
                {baseR*ux - half*tx, baseR*uy - half*ty},
                {apexR*ux, apexR*uy}
        };
        drawPolygon(c, H, q, p);
    }

    private static void drawPolygon(Canvas c, Mat H, double[][] q, Paint p) {
        Path path = new Path();
        for (int i = 0; i < q.length; i++) {
            Point z = project(H, q[i][0], q[i][1]);
            if (z == null) return;
            if (i == 0) path.moveTo((float)z.x, (float)z.y); else path.lineTo((float)z.x, (float)z.y);
        }
        path.close(); c.drawPath(path, p);
    }

    private static void drawProjectedCircle(Canvas c, Mat H, double cx, double cy, double r, Paint p) {
        Path path = new Path();
        for (int i = 0; i <= 96; i++) {
            double a = 2.0 * Math.PI * i / 96.0;
            Point z = project(H, cx + r*Math.cos(a), cy + r*Math.sin(a));
            if (z == null) return;
            if (i == 0) path.moveTo((float)z.x, (float)z.y); else path.lineTo((float)z.x, (float)z.y);
        }
        c.drawPath(path, p);
    }

    private static Paint paint(int color, float width, int alpha) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color); p.setAlpha(alpha); p.setStrokeWidth(width); p.setStyle(Paint.Style.STROKE);
        return p;
    }

    private static Point project(Mat H, double x, double y) {
        if (H == null || H.empty() || H.rows() < 3 || H.cols() < 3) return null;
        double h00 = value(H,0,0), h01 = value(H,0,1), h02 = value(H,0,2);
        double h10 = value(H,1,0), h11 = value(H,1,1), h12 = value(H,1,2);
        double h20 = value(H,2,0), h21 = value(H,2,1), h22 = value(H,2,2);
        double d = h20*x + h21*y + h22;
        if (!Double.isFinite(d) || Math.abs(d) < 1e-9) return null;
        double px = (h00*x + h01*y + h02) / d;
        double py = (h10*x + h11*y + h12) / d;
        return Double.isFinite(px) && Double.isFinite(py) ? new Point(px, py) : null;
    }

    private static double value(Mat m, int r, int c) {
        double[] v = m.get(r,c); return v == null || v.length == 0 ? Double.NaN : v[0];
    }
}
