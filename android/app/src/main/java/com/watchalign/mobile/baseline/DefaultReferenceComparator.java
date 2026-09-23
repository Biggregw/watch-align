package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.RawMeasurement;

/**
 * Straightforward {@link ReferenceComparator}: looks up the measurement's own id-derived
 * {@link MetricKey} in the supplied baseline and builds the comparison. All the arithmetic lives
 * in {@link ReferenceComparison} itself; this class only wires stage 1 to stage 2.
 */
public final class DefaultReferenceComparator implements ReferenceComparator {
    @Override
    public ReferenceComparison compare(RawMeasurement measurement, MetricBaseline baseline) {
        if (measurement == null) {
            throw new IllegalArgumentException("measurement must not be null");
        }
        if (baseline == null) {
            throw new IllegalArgumentException("baseline must not be null");
        }
        MetricKey key = MetricKeys.parse(measurement.id());
        return new ReferenceComparison(key, measurement.value(), baseline);
    }
}
