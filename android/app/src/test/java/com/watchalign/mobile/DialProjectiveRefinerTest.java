package com.watchalign.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;

import java.util.Locale;

public class DialProjectiveRefinerTest {
    private static final double[][] TRUE_H = {
            {352.4, 110.6, 420.0},
            {94.2, 274.8, 360.0},
            {0.22, 0.18, 1.0}
    };

    @Test public void recoversProjectivePoseFromIndependentAnnularEvidence() {
        double[][] h0 = ellipsePose(TRUE_H);
        ErrorSummary before = errors(h0, TRUE_H, "before");
        DialProjectiveRefiner.Result result = DialProjectiveRefiner.refine(
                new SyntheticAnnularField(TRUE_H, 900, 800), h0);
        ErrorSummary after = errors(result.homography, TRUE_H, "after");

        System.out.printf(Locale.US,
                "projective before=(%.6f, %.6f) after=(%.6f, %.6f) true=(%.6f, %.6f)%n",
                normalized(h0, 2, 0), normalized(h0, 2, 1),
                normalized(result.homography, 2, 0), normalized(result.homography, 2, 1),
                TRUE_H[2][0], TRUE_H[2][1]);
        System.out.printf(Locale.US,
                "mean_before=%.6f max_before=%.6f mean_after=%.6f max_after=%.6f fit=%.4f->%.4f holdout=%.4f->%.4f%n",
                before.mean, before.max, after.mean, after.max,
                result.fitBefore, result.fitAfter, result.holdoutBefore, result.holdoutAfter);

        assertTrue(result.accepted);
        assertEquals(49.459, before.mean, 0.001);
        assertEquals(94.149, before.max, 0.001);
        assertEquals(0.22, normalized(result.homography, 2, 0), 0.035);
        assertEquals(0.18, normalized(result.homography, 2, 1), 0.035);
        assertTrue(after.mean < 10.0);
        assertTrue(after.max < 20.0);
        assertTrue(after.mean < before.mean * 0.25);
        assertTrue(after.max < before.max * 0.25);
    }

    @Test public void affineInputDoesNotInventProjectiveSkew() {
        double[][] affine = {
                {285.0, 42.0, 430.0},
                {-18.0, 248.0, 350.0},
                {0.0, 0.0, 1.0}
        };
        DialProjectiveRefiner.Result result = DialProjectiveRefiner.refine(
                new SyntheticAnnularField(affine, 900, 800), affine);

        assertFalse(result.accepted);
        assertMatrixEquals(affine, result.homography, 0.0);
        assertEquals(0.0, normalized(result.homography, 2, 0), 0.0);
        assertEquals(0.0, normalized(result.homography, 2, 1), 0.0);
    }

    @Test public void weakNoisyEvidenceFallsBackExactlyToH0() {
        double[][] h0 = ellipsePose(TRUE_H);
        DialProjectiveRefiner.DistanceField weak = new DialProjectiveRefiner.DistanceField() {
            @Override public int width() { return 900; }
            @Override public int height() { return 800; }
            @Override public double distance(double x, double y) {
                return 7.85 + 0.08 * Math.sin(x * 0.071 + y * 0.053);
            }
        };
        DialProjectiveRefiner.Result result = DialProjectiveRefiner.refine(weak, h0);

        assertFalse(result.accepted);
        assertMatrixEquals(h0, result.homography, 0.0);
        assertEquals(result.fitBefore, result.fitAfter, 0.0);
        assertEquals(result.holdoutBefore, result.holdoutAfter, 0.0);
    }

    private static ErrorSummary errors(double[][] actual, double[][] expected, String label) {
        double sum = 0.0, max = 0.0;
        for (int hour = 1; hour <= 12; hour++) {
            double a = Math.toRadians(hour * 30.0 - 90.0);
            Point p = project(actual, Math.cos(a), Math.sin(a));
            Point q = project(expected, Math.cos(a), Math.sin(a));
            double error = Math.hypot(p.x - q.x, p.y - q.y);
            sum += error;
            max = Math.max(max, error);
            System.out.printf(Locale.US, "%s hour=%02d error_px=%.6f%n", label, hour, error);
        }
        return new ErrorSummary(sum / 12.0, max);
    }

    private static double[][] ellipsePose(double[][] trueH) {
        RotatedRect ellipse = ellipseFromProjectedUnitCircle(trueH);
        Point[] p = PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse, 0.0);
        Point center = new Point((p[0].x + p[2].x) * 0.5, (p[0].y + p[2].y) * 0.5);
        return new double[][]{
                {(p[1].x - p[3].x) * 0.5, (p[2].x - p[0].x) * 0.5, center.x},
                {(p[1].y - p[3].y) * 0.5, (p[2].y - p[0].y) * 0.5, center.y},
                {0.0, 0.0, 1.0}
        };
    }

    private static final class SyntheticAnnularField implements DialProjectiveRefiner.DistanceField {
        private final double[][] inverse;
        private final int width, height;
        SyntheticAnnularField(double[][] h, int width, int height) {
            this.inverse = invert3(h);
            this.width = width;
            this.height = height;
        }
        @Override public int width() { return width; }
        @Override public int height() { return height; }
        @Override public double distance(double x, double y) {
            Point p = project(inverse, x, y);
            double best = Math.abs(Math.hypot(p.x, p.y) - 1.0);
            for (int minute = 0; minute < 60; minute++) {
                if (minute % 5 == 0) continue;
                double a = Math.toRadians(minute * 6.0 - 90.0);
                best = Math.min(best, tickOutlineDistance(p.x, p.y, a));
            }
            // The local dial scale is about 250 px per canonical unit. Capping is
            // performed by the production robust loss after this conversion.
            return best * 250.0;
        }
    }

    private static double tickOutlineDistance(double x, double y, double angle) {
        double ca = Math.cos(angle), sa = Math.sin(angle);
        double radial = x * ca + y * sa;
        double tangent = -x * sa + y * ca;
        double center = Gmt126710BlnrMaster.MINUTE_TRACK_R;
        double radialHalf = 0.025;
        double tangentialHalf = center * Math.tan(Math.toRadians(0.34));
        double dr = Math.abs(radial - center), dt = Math.abs(tangent);
        double side = Math.hypot(Math.max(0.0, dr - radialHalf), Math.abs(dt - tangentialHalf));
        double end = Math.hypot(Math.abs(dr - radialHalf), Math.max(0.0, dt - tangentialHalf));
        return Math.min(side, end);
    }

    private static void assertMatrixEquals(double[][] expected, double[][] actual, double tolerance) {
        for (int row = 0; row < 3; row++) assertArrayEquals(expected[row], actual[row], tolerance);
    }

    private static double normalized(double[][] h, int row, int col) { return h[row][col] / h[2][2]; }

    private static Point project(double[][] h, double x, double y) {
        double w = h[2][0] * x + h[2][1] * y + h[2][2];
        return new Point((h[0][0] * x + h[0][1] * y + h[0][2]) / w,
                (h[1][0] * x + h[1][1] * y + h[1][2]) / w);
    }

    private static RotatedRect ellipseFromProjectedUnitCircle(double[][] h) {
        double[][] inv = invert3(h);
        double[][] circle = {{1, 0, 0}, {0, 1, 0}, {0, 0, -1}};
        double[][] conic = multiply(transpose(inv), multiply(circle, inv));
        double a = conic[0][0], b = 2.0 * conic[0][1], c = conic[1][1];
        double d = 2.0 * conic[0][2], e = 2.0 * conic[1][2], f = conic[2][2];
        double det = 4.0 * a * c - b * b;
        double cx = (b * e - 2.0 * c * d) / det;
        double cy = (b * d - 2.0 * a * e) / det;
        double fc = a * cx * cx + b * cx * cy + c * cy * cy + d * cx + e * cy + f;
        double trace = a + c, diff = a - c, root = Math.hypot(diff, b);
        double lambda1 = (trace + root) * 0.5, lambda2 = (trace - root) * 0.5;
        double angle = 0.5 * Math.atan2(b, diff);
        double r1 = Math.sqrt(-fc / lambda1), r2 = Math.sqrt(-fc / lambda2);
        return new RotatedRect(new Point(cx, cy), new Size(2.0 * r1, 2.0 * r2), Math.toDegrees(angle));
    }

    private static double[][] invert3(double[][] m) {
        double a=m[0][0],b=m[0][1],c=m[0][2],d=m[1][0],e=m[1][1],f=m[1][2],g=m[2][0],h=m[2][1],i=m[2][2];
        double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        return new double[][]{
                {(e*i-f*h)/det,(c*h-b*i)/det,(b*f-c*e)/det},
                {(f*g-d*i)/det,(a*i-c*g)/det,(c*d-a*f)/det},
                {(d*h-e*g)/det,(b*g-a*h)/det,(a*e-b*d)/det}
        };
    }

    private static double[][] transpose(double[][] m) {
        double[][] out=new double[m[0].length][m.length];
        for(int r=0;r<m.length;r++)for(int c=0;c<m[0].length;c++)out[c][r]=m[r][c];
        return out;
    }

    private static double[][] multiply(double[][] a,double[][] b) {
        double[][] out=new double[a.length][b[0].length];
        for(int r=0;r<a.length;r++)for(int c=0;c<b[0].length;c++)for(int k=0;k<b.length;k++)out[r][c]+=a[r][k]*b[k][c];
        return out;
    }

    private static final class ErrorSummary {
        final double mean, max;
        ErrorSummary(double mean, double max) { this.mean = mean; this.max = max; }
    }
}
