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
}
