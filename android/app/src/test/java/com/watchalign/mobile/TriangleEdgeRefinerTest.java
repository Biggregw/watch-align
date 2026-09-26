package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Random;

import org.junit.Test;

public class TriangleEdgeRefinerTest {
    private static final int W = 800, H = 800;
    private static final double R = 207.0;          // dial radius, as in the field report

    // True outer surround corners of a genuine, unrotated 12 triangle (base up).
    private static final double[] L = {327.5, 567.0}, RT = {381.5, 567.0}, T = {354.5, 633.0};

    /** Signed distance to the triangle boundary (negative inside). */
    private static double inside(double x, double y) {
        double d1 = side(L, RT, x, y), d2 = side(RT, T, x, y), d3 = side(T, L, x, y);
        return Math.max(d1, Math.max(d2, d3));
    }
    /** Distance of (x,y) outside the directed edge a->b (clockwise triangle in image coords). */
    private static double side(double[] a, double[] b, double x, double y) {
        double ux = b[0] - a[0], uy = b[1] - a[1], len = Math.hypot(ux, uy);
        return ((x - a[0]) * uy - (y - a[1]) * ux) / len;
    }

    /**
     * Field-photo case: bright lume, grey surround ~3 px wide, and near the right base
     * corner a dimmer surround with a dark groove between it and the lume. Minute ticks
     * sit 5 px above the base. The contour hull put the right corner on the lume.
     */
    private static DialEdgeEllipseFit.Intensity photo(long seed) {
        Random rnd = new Random(seed);
        return (x, y) -> {
            double d = inside(x, y);
            double v;
            if (d > 0) v = 18;                                         // black dial
            else if (d > -3.0) {                                       // surround
                boolean dimCorner = Math.hypot(x - RT[0], y - RT[1]) < 22;
                v = dimCorner ? 130 : 175;
            } else if (d > -4.2 && Math.hypot(x - RT[0], y - RT[1]) < 26) v = 70;   // groove
            else v = 225;                                              // lume
            // 59, 60, 01 minute ticks above the base
            for (double tx : new double[]{334.6, 354.7, 374.7})
                if (Math.abs(x - tx) < 1.2 && y > 548 && y < 562) v = 160;
            return v + rnd.nextGaussian() * 3.0;
        };
    }

    @Test public void recoversOuterCornersWhenOneCornerWasClippedToTheLume() {
        // Rough corners as the contour hull gave them on the field photo.
        double[] roughL = {331.0, 569.0}, roughR = {376.0, 573.0}, roughT = {355.0, 632.0};
        double[][] q = TriangleEdgeRefiner.refine(photo(3), W, H, roughL, roughR, roughT,
                new double[]{334.6, 562.6}, new double[]{374.7, 563.6}, R);
        assertNotNull(q);
        assertEquals(L[0], q[0][0], 0.8);  assertEquals(L[1], q[0][1], 0.8);
        assertEquals(RT[0], q[1][0], 0.8); assertEquals(RT[1], q[1][1], 0.8);
        assertEquals(T[0], q[2][0], 1.5);  assertEquals(T[1], q[2][1], 1.5);
        // Base is level: the fake +4.8 deg top edge is gone.
        double baseDeg = Math.toDegrees(Math.atan2(q[1][1] - q[0][1], q[1][0] - q[0][0]));
        assertEquals(0.0, baseDeg, 0.6);
    }

    @Test public void baseSearchStopsShortOfTheMinuteTicks() {
        // Start the base slightly low so a greedy outward search would reach the ticks.
        double[][] q = TriangleEdgeRefiner.refine(photo(5), W, H,
                new double[]{329.0, 570.0}, new double[]{379.0, 570.0}, new double[]{355.0, 632.0},
                new double[]{334.6, 562.6}, new double[]{374.7, 563.6}, R);
        assertNotNull(q);
        assertEquals(567.0, q[0][1], 0.8);
        assertEquals(567.0, q[1][1], 0.8);
    }

    @Test public void keepsRoughCornersWhenThereIsNoMarkerEdge() {
        DialEdgeEllipseFit.Intensity flat = (x, y) -> 40.0;
        assertNull(TriangleEdgeRefiner.refine(flat, W, H, L, RT, T, null, null, R));
    }
}
