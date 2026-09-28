package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HandIntrusionTest {
    private static final double CX = 400, CY = 400, R = 200;
    // Triangle at 12 (base 0.90R, tip 0.60R, half-base 0.123R) and 59/60/01 tick ends.
    private static final GmtTwelveLandmarkAnalyzer.Geometry G = new GmtTwelveLandmarkAnalyzer.Geometry(
            new double[]{CX - 0.123 * R, CY - 0.90 * R}, new double[]{CX + 0.123 * R, CY - 0.90 * R},
            new double[]{CX, CY - 0.60 * R},
            new double[]{CX - 0.10 * R, CY - 0.92 * R}, new double[]{CX, CY - 0.925 * R},
            new double[]{CX + 0.10 * R, CY - 0.92 * R}, true);

    private static DialEdgeEllipseFit.Intensity dial(boolean hand, boolean crown) {
        return (x, y) -> {
            if (HandIntrusion.signedDistance(G, x, y) <= 0) return 225;               // triangle
            if (crown && Math.hypot(x - CX, y - (CY - 0.52 * R)) < 0.07 * R) return 215; // crown logo
            if (hand) {                                                                 // hand shaft beside the triangle
                double hx = CX + 0.14 * R, along = (CY - y) / R;
                if (along > 0 && along < 0.95 && Math.abs(x - hx - 0.02 * R * along) < 0.02 * R) return 210;
            }
            return 22;
        };
    }

    @Test public void cleanDialHasNoHand() {
        assertFalse(HandIntrusion.measure(dial(false, false), 800, 800, CX, CY, R, G).present);
    }

    @Test public void crownLogoBelowTheTriangleIsNotAHand() {
        assertFalse(HandIntrusion.measure(dial(false, true), 800, 800, CX, CY, R, G).present);
    }

    @Test public void handBesideTheTriangleIsDetected() {
        assertTrue(HandIntrusion.measure(dial(true, true), 800, 800, CX, CY, R, G).present);
    }

    /** A thin seconds hand straight through the triangle: too little area for the wedge check. */
    private static DialEdgeEllipseFit.Intensity secondsHand(double angleDeg, double widthR) {
        double a = Math.toRadians(angleDeg), dx = Math.sin(a), dy = -Math.cos(a);
        return (x, y) -> {
            double px = x - CX, py = y - CY, along = px * dx + py * dy, across = Math.abs(px * dy - py * dx);
            if (along > -0.15 * R && along < 0.97 * R && across < widthR * R / 2) return 200;
            if (HandIntrusion.signedDistance(G, x, y) <= 0) return 225;
            return 22;
        };
    }

    @Test public void thinSecondsHandThroughTheTriangleIsDetected() {
        HandIntrusion.Result h = HandIntrusion.measure(secondsHand(1.5, 0.012), 800, 800, CX, CY, R, G);
        assertTrue("fraction " + h.brightFraction, h.present);
        assertTrue(h.thinLine);
    }

    @Test public void thinSecondsHandBesideTheTriangleIsDetected() {
        assertTrue(HandIntrusion.measure(secondsHand(10.0, 0.012), 800, 800, CX, CY, R, G).present);
    }

    /** A base touching the minute track is a gap finding, not a hand. */
    @Test public void touchingBaseIsNotAHand() {
        GmtTwelveLandmarkAnalyzer.Geometry touch = new GmtTwelveLandmarkAnalyzer.Geometry(
                new double[]{CX - 0.123 * R, CY - 0.918 * R}, new double[]{CX + 0.123 * R, CY - 0.918 * R},
                new double[]{CX, CY - 0.618 * R}, G.tick59, G.tick60, G.tick01, true);
        DialEdgeEllipseFit.Intensity img = (x, y) -> {
            if (HandIntrusion.signedDistance(touch, x, y) <= 1.2) return 225;
            return 22;
        };
        assertFalse(HandIntrusion.measure(img, 800, 800, CX, CY, R, touch).present);
    }
}
