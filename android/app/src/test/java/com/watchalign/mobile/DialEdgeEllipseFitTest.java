package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.Test;

public class DialEdgeEllipseFitTest {
    private static final int W = 1080, H = 1400;

    /** Synthetic GMT-like dial: black dial ellipse, white minute ticks just inside the edge,
     *  a bright hand crossing the edge region, bright rehaut, black bezel beyond. */
    private static final class Dial implements DialEdgeEllipseFit.Intensity {
        final double cx, cy, a, b, angRad;
        final Random noise = new Random(7);
        Dial(double cx, double cy, double a, double b, double angDeg) {
            this.cx = cx; this.cy = cy; this.a = a; this.b = b; this.angRad = Math.toRadians(angDeg);
        }
        /** normalised elliptical radius and dial-frame clock angle (degrees) */
        double[] local(double x, double y) {
            double dx = x - cx, dy = y - cy, c = Math.cos(angRad), s = Math.sin(angRad);
            double lx = (c * dx + s * dy) / a, ly = (-s * dx + c * dy) / b;
            double ang = Math.toDegrees(Math.atan2(lx, -ly));
            return new double[]{Math.hypot(lx, ly), ang < 0 ? ang + 360 : ang};
        }
        @Override public double at(double x, double y) {
            double[] l = local(x, y);
            double rn = l[0], clock = l[1];
            double v;
            if (rn < 1.0) {
                v = 20;
                double tickPhase = Math.abs(((clock + 3.0) % 6.0) - 3.0);   // 60 ticks
                if (rn > 0.90 && rn < 0.97 && tickPhase < 0.6) v = 235;     // minute tick
                if (Math.abs(clock - 130) < 1.2 && rn < 0.965) v = 225;       // hand reaching the track
            } else if (rn < 1.12) v = 205;   // rehaut
            else if (rn < 1.40) v = 25;      // bezel insert
            else v = 210;                    // case
            return v + noise.nextGaussian() * 4.0;
        }
    }

    @Test public void recoversTrueCentreWhenHoughSeedIsDisplacedByParallax() {
        Dial d = new Dial(540.0, 700.0, 262.0, 255.0, 23.0);
        // Seed as the app gets it: centre 12 px off (bezel circle), radius slightly wrong.
        DialEdgeEllipseFit.Fit f = DialEdgeEllipseFit.fit(d, W, H, 529.0, 705.0, 252.0);
        assertNotNull(f);
        assertEquals(540.0, f.cx, 0.35);
        assertEquals(700.0, f.cy, 0.35);
        assertEquals(262.0, Math.max(f.axisA, f.axisB), 0.6);
        assertEquals(255.0, Math.min(f.axisA, f.axisB), 0.6);
        assertTrue("rms " + f.rmsPx, f.rmsPx < 1.0);
    }

    @Test public void fittedEllipseFeedsCardinalPointsInOpenCvRotatedRectConvention() {
        Dial d = new Dial(500.0, 650.0, 250.0, 236.0, 35.0);
        DialEdgeEllipseFit.Fit f = DialEdgeEllipseFit.fit(d, W, H, 508.0, 641.0, 244.0);
        assertNotNull(f);
        org.opencv.core.RotatedRect fitted = new org.opencv.core.RotatedRect(
                new org.opencv.core.Point(f.cx, f.cy),
                new org.opencv.core.Size(2 * f.axisA, 2 * f.axisB), f.angleDeg);
        org.opencv.core.RotatedRect truth = new org.opencv.core.RotatedRect(
                new org.opencv.core.Point(500.0, 650.0),
                new org.opencv.core.Size(500.0, 472.0), 35.0);
        org.opencv.core.Point[] p = PerspectiveGmtOverlay.ellipseCardinalPoints(fitted, 0);
        org.opencv.core.Point[] q = PerspectiveGmtOverlay.ellipseCardinalPoints(truth, 0);
        for (int i = 0; i < 4; i++) {
            assertEquals("x" + i, q[i].x, p[i].x, 0.8);
            assertEquals("y" + i, q[i].y, p[i].y, 0.8);
        }
    }

    /** Photo-like case from alpha41 field test: the rehaut is dark grey (a weak dial edge),
     *  and its outer rim is a bright line about 0.12R further out whose centre is shifted by
     *  parallax. Picking the outermost edge per ray mixed the two edges and the fit was
     *  rejected as noisy; it must lock onto the dark-dial edge the master is scaled to. */
    @Test public void locksOntoDialEdgeNotBrightRehautRimWhenRehautIsDark() {
        final double cx = 540.0, cy = 700.0, r = 230.0;
        final Random rnd = new Random(11);
        DialEdgeEllipseFit.Intensity img = (x, y) -> {
            double rn = Math.hypot(x - cx, y - cy) / r;
            double rim = Math.hypot(x - cx, y - (cy + 7.0)) / r;   // parallax-shifted rim
            double clock = Math.toDegrees(Math.atan2(x - cx, -(y - cy)));
            if (clock < 0) clock += 360;
            double v;
            if (rn < 1.0) {
                v = 18;
                double tickPhase = Math.abs(((clock + 3.0) % 6.0) - 3.0);
                if (rn > 0.90 && rn < 0.97 && tickPhase < 0.6) v = 230;
            } else if (rim < 1.105) v = 62 + 10 * Math.sin(Math.toRadians(clock * 9));  // engraved dark rehaut
            else if (rim < 1.135) v = 225;                                              // bright rim
            else v = 70;                                                               // bezel insert
            return v + rnd.nextGaussian() * 4.0;
        };
        DialEdgeEllipseFit.Fit f = DialEdgeEllipseFit.fit(img, W, H, cx - 6.0, cy + 8.0, r);
        assertNotNull(f);
        assertEquals(cx, f.cx, 0.6);
        assertEquals(cy, f.cy, 0.6);
        assertEquals(r, f.meanRadius(), 1.0);
    }

    @Test public void rejectsImageWithoutDialBoundary() {
        DialEdgeEllipseFit.Intensity flat = (x, y) -> 120.0;
        assertNull(DialEdgeEllipseFit.fit(flat, W, H, 540, 700, 250));
    }

    @Test public void conicFitIsExactOnCleanEllipsePoints() {
        List<double[]> pts = new ArrayList<>();
        double cx = 300, cy = 420, a = 180, b = 150, t = Math.toRadians(-20);
        for (int k = 0; k < 90; k++) {
            double u = 2 * Math.PI * k / 90, ex = a * Math.cos(u), ey = b * Math.sin(u);
            pts.add(new double[]{cx + ex * Math.cos(t) - ey * Math.sin(t), cy + ex * Math.sin(t) + ey * Math.cos(t)});
        }
        double[] e = DialEdgeEllipseFit.fitConic(pts, null, 310, 410, 170);
        assertNotNull(e);
        assertEquals(cx, e[0], 1e-6);
        assertEquals(cy, e[1], 1e-6);
        assertEquals(a, e[2], 1e-6);
        assertEquals(b, e[3], 1e-6);
        assertEquals(-20.0, e[4], 1e-6);
        for (double[] p : pts) assertEquals(0.0, DialEdgeEllipseFit.radialResidual(e, p[0], p[1]), 1e-6);
    }
}
