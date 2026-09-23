package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.watchalign.mobile.qc.RawMeasurement;

import org.junit.Test;

public class DefaultReferenceComparatorTest {
    private static final DefaultReferenceComparator COMPARATOR = new DefaultReferenceComparator();
    private static final MetricBaseline BASELINE = new MetricBaseline(
            5, 0.5512, 0.0026, 0.5493, 0.5643, null, 0.0, ValidationStatus.PROMISING);

    @Test public void looksUpMetricKeyFromMeasurementId() {
        RawMeasurement measurement = new RawMeasurement("h12.apex_radial", 0.5600, "ratio");
        ReferenceComparison comparison = COMPARATOR.compare(measurement, BASELINE);
        assertEquals("h12", comparison.key().markerId());
        assertEquals("apex_radial", comparison.key().metricName());
        assertEquals(0.5600, comparison.observedValue(), 1e-12);
        assertEquals(0.5600 - 0.5512, comparison.signedDeviation(), 1e-12);
    }

    @Test public void rejectsNullMeasurement() {
        try { COMPARATOR.compare(null, BASELINE); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsNullBaseline() {
        RawMeasurement measurement = new RawMeasurement("h12.apex_radial", 0.5600, "ratio");
        try { COMPARATOR.compare(measurement, null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsMeasurementIdWithNoMarkerPrefix() {
        RawMeasurement measurement = new RawMeasurement("no_marker_prefix", 0.5, "ratio");
        try { COMPARATOR.compare(measurement, BASELINE); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
