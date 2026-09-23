package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MetricKeysTest {
    @Test public void parsesMarkerAndMetricName() {
        MetricKey key = MetricKeys.parse("h12.apex_radial");
        assertEquals("h12", key.markerId());
        assertEquals("apex_radial", key.metricName());
    }

    @Test public void parsesMetricNameContainingFurtherDots() {
        MetricKey key = MetricKeys.parse("h12.stage3_apex_r_simple");
        assertEquals("h12", key.markerId());
        assertEquals("stage3_apex_r_simple", key.metricName());
    }

    @Test public void rejectsBlank() {
        try { MetricKeys.parse(""); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
        try { MetricKeys.parse(null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsMissingDot() {
        try { MetricKeys.parse("global_metric"); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsDotAtStartOrEnd() {
        try { MetricKeys.parse(".metric"); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
        try { MetricKeys.parse("h12."); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
