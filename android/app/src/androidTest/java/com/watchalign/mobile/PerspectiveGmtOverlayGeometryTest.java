package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class PerspectiveGmtOverlayGeometryTest {
    private static final double EPS = 1e-6;

    @BeforeClass public static void initOpenCv() {
        assertTrue("OpenCV failed to initialise", OpenCVLoader.initLocal());
    }

    @Test public void knownAffineProjectsMasterGeometryWithoutExtraRoll() throws Exception {
        Point[] dst = new Point[]{
                affineExpected(0.0, -1.0),
                affineExpected(1.0, 0.0),
                affineExpected(0.0, 1.0),
                affineExpected(-1.0, 0.0)
        };

        Mat h = homographyFromUnitSquare(dst);
        try {
            Point expected12Centre = affineExpected(0.0, -Gmt126710BlnrMaster.MARKER_CENTER_R);
            Point actual12Centre = projectViaHomography(h, 0.0, -Gmt126710BlnrMaster.MARKER_CENTER_R);
            assertPointEquals("12 o'clock marker centre", expected12Centre, actual12Centre);

            Point expected6Centre = affineExpected(0.0, Gmt126710BlnrMaster.MARKER_CENTER_R);
            Point actual6Centre = projectViaHomography(h, 0.0, Gmt126710BlnrMaster.MARKER_CENTER_R);
            assertPointEquals("6 o'clock marker centre", expected6Centre, actual6Centre);

            double baseY = -(Gmt126710BlnrMaster.TRI_CENTER_R + Gmt126710BlnrMaster.TRI_BASE_OUTWARD);
            double apexY = -(Gmt126710BlnrMaster.TRI_CENTER_R - Gmt126710BlnrMaster.TRI_APEX_INWARD);
            double halfBase = Gmt126710BlnrMaster.TRI_HALF_BASE;

            Point expectedBaseRight = affineExpected(halfBase, baseY);
            Point actualBaseRight = projectViaHomography(h, halfBase, baseY);
            assertPointEquals("12 triangle base-right vertex", expectedBaseRight, actualBaseRight);

            Point expectedBaseLeft = affineExpected(-halfBase, baseY);
            Point actualBaseLeft = projectViaHomography(h, -halfBase, baseY);
            assertPointEquals("12 triangle base-left vertex", expectedBaseLeft, actualBaseLeft);

            Point expectedApex = affineExpected(0.0, apexY);
            Point actualApex = projectViaHomography(h, 0.0, apexY);
            assertPointEquals("12 triangle apex vertex", expectedApex, actualApex);

            Point centre = affineExpected(0.0, 0.0);
            Point encodedTwelveAxis = projectViaHomography(h, 0.0, -1.0);
            Point baseMid = midpoint(actualBaseRight, actualBaseLeft);
            double baseOutward = outwardComponent(centre, encodedTwelveAxis, baseMid);
            double apexOutward = outwardComponent(centre, encodedTwelveAxis, actualApex);
            assertTrue("Expected base to remain farther outward than apex, but base=" + baseOutward + " apex=" + apexOutward,
                    baseOutward > apexOutward);

            double encodedRollDeg = clockAngleDeg(centre, encodedTwelveAxis);
            double r = Gmt126710BlnrMaster.MARKER_CENTER_R;
            double extraRolledX = Math.sin(Math.toRadians(encodedRollDeg)) * r;
            double extraRolledY = -Math.cos(Math.toRadians(encodedRollDeg)) * r;
            Point extraRolled12Centre = projectViaHomography(h, extraRolledX, extraRolledY);
            double extraRollError = distance(extraRolled12Centre, expected12Centre);
            assertTrue("Expected extra roll correction to move the 12 centre away from its correct position, error=" + extraRollError,
                    extraRollError > 1.0);
        } finally {
            h.release();
        }
    }

    private static Mat homographyFromUnitSquare(Point[] dst) throws Exception {
        Method m = PerspectiveGmtOverlay.class.getDeclaredMethod("homographyFromUnitSquare", Point[].class);
        m.setAccessible(true);
        return (Mat) m.invoke(null, new Object[]{dst});
    }

    private static Point affineExpected(double x, double y) {
        return new Point(200.0 * x + 40.0 * y + 500.0, -60.0 * x + 180.0 * y + 400.0);
    }

    private static Point projectViaHomography(Mat h, double x, double y) {
        double[] m = new double[9];
        h.get(0, 0, m);
        double w = m[6] * x + m[7] * y + m[8];
        return new Point((m[0] * x + m[1] * y + m[2]) / w, (m[3] * x + m[4] * y + m[5]) / w);
    }

    private static void assertPointEquals(String label, Point expected, Point actual) {
        assertEquals(label + " x", expected.x, actual.x, EPS);
        assertEquals(label + " y", expected.y, actual.y, EPS);
    }

    private static Point midpoint(Point a, Point b) {
        return new Point((a.x + b.x) * 0.5, (a.y + b.y) * 0.5);
    }

    private static double outwardComponent(Point centre, Point outwardAxisPoint, Point p) {
        double ax = outwardAxisPoint.x - centre.x;
        double ay = outwardAxisPoint.y - centre.y;
        double px = p.x - centre.x;
        double py = p.y - centre.y;
        return ax * px + ay * py;
    }

    private static double clockAngleDeg(Point centre, Point p) {
        double a = Math.toDegrees(Math.atan2(p.x - centre.x, -(p.y - centre.y)));
        return a < 0.0 ? a + 360.0 : a;
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(a.x - b.x, a.y - b.y);
    }
}
