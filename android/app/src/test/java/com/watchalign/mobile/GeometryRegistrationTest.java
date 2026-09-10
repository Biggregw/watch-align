package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

public class GeometryRegistrationTest {
    @Test public void medianIsRobustToOutlier() {
        assertEquals(1.0, GeometryRegistration.median(new double[]{1, 1.1, 0.9, 9, 1.0}), 1e-9);
    }

    @Test public void solvesKnownRotationAndScale() {
        GeometryRegistration.Solution s = GeometryRegistration.solve(
                200, 300,
                164, 246,
                3.2, -1.3,
                0.92, 0.95,
                11, 0.45,
                2.0);
        assertTrue(s.usable);
        assertEquals(-4.5, s.rotationDeg, 0.05);
        assertEquals(1.5, s.scale, 0.02);
        assertTrue(s.confidence > 0.75);
    }

    @Test public void rejectsScaleDisagreement() {
        GeometryRegistration.Solution s = GeometryRegistration.solve(
                200, 300,
                160, 290,
                0, 0,
                0.95, 0.95,
                12, 0.2,
                1.0);
        assertFalse(s.usable);
        assertTrue(s.reason.contains("scales disagree"));
    }

    @Test public void rejectsTooFewMarkers() {
        GeometryRegistration.Solution s = GeometryRegistration.solve(
                200, 200,
                164, 164,
                0, 0,
                0.9, 0.9,
                5, 0.2,
                1.0);
        assertFalse(s.usable);
    }

    @Test public void perspectiveMismatchLowersConfidenceWithoutFakingFailure() {
        GeometryRegistration.Solution good = GeometryRegistration.solve(
                200, 200, 164, 164, 0, 0,
                0.9, 0.9, 12, 0.4, 1.0);
        GeometryRegistration.Solution poor = GeometryRegistration.solve(
                200, 200, 164, 164, 0, 0,
                0.9, 0.9, 12, 0.4, 16.0);
        assertTrue(good.confidence > poor.confidence);
    }
}
