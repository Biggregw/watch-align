package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class DirectionWordingTest {
    @Test public void radialPositiveIsHighNegativeIsLow() {
        MetricKey key = new MetricKey("h12", "apex_radial");
        assertEquals("high", DirectionWording.describe(key, 0.01));
        assertEquals("low", DirectionWording.describe(key, -0.01));
    }

    @Test public void radialConventionAppliesToNonH12MarkersToo() {
        MetricKey key = new MetricKey("h09", "centre_radial");
        assertEquals("high", DirectionWording.describe(key, 0.01));
        assertEquals("low", DirectionWording.describe(key, -0.01));
    }

    @Test public void h12TangentialPositiveIsRightNegativeIsLeft() {
        // Positive tangential at the 12 o'clock marker is the clockwise direction, which on
        // screen at the very top of the dial is rightward.
        MetricKey key = new MetricKey("h12", "tangential_centroid_offset");
        assertEquals("right", DirectionWording.describe(key, 0.005));
        assertEquals("left", DirectionWording.describe(key, -0.005));
    }

    @Test public void h06TangentialSignIsFlippedRelativeToH12() {
        // Positive tangential at the 6 o'clock marker is still the clockwise direction, but at
        // the bottom of the dial that is leftward on screen -- the opposite screen word from h12.
        MetricKey key = new MetricKey("h06", "centre_tangential");
        assertEquals("left", DirectionWording.describe(key, 0.005));
        assertEquals("right", DirectionWording.describe(key, -0.005));
    }

    @Test public void otherMarkerTangentialFallsBackToClockwiseCounterClockwise() {
        // At h09 the tangential direction is vertical on screen, not horizontal, so left/right
        // would be geometrically wrong; clockwise/counter-clockwise is correct at every position.
        MetricKey key = new MetricKey("h09", "centre_tangential");
        assertEquals("clockwise", DirectionWording.describe(key, 0.005));
        assertEquals("counter-clockwise", DirectionWording.describe(key, -0.005));
    }

    @Test public void axisMetricsUseClockwiseWording() {
        MetricKey key = new MetricKey("h09", "axis_residual_deg");
        assertEquals("clockwise", DirectionWording.describe(key, 1.0));
        assertEquals("counter-clockwise", DirectionWording.describe(key, -1.0));
    }

    @Test public void spanMetricsUseLargerSmaller() {
        MetricKey key = new MetricKey("h06", "radial_span");
        assertEquals("larger", DirectionWording.describe(key, 0.01));
        assertEquals("smaller", DirectionWording.describe(key, -0.01));
    }

    @Test public void zeroDeviationIsMatching() {
        MetricKey key = new MetricKey("h12", "apex_radial");
        assertEquals("matching", DirectionWording.describe(key, 0.0));
    }

    @Test public void rejectsNullKeyOrNonFiniteDeviation() {
        try { DirectionWording.describe(null, 0.1); fail("expected"); }
        catch (IllegalArgumentException expected) { }
        try { DirectionWording.describe(new MetricKey("h12", "apex_radial"), Double.NaN); fail("expected"); }
        catch (IllegalArgumentException expected) { }
    }
}
