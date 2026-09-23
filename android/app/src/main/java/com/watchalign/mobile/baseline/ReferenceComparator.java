package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.RawMeasurement;

/**
 * Stage 1 -&gt; stage 2: compares one raw measurement against its empirical baseline.
 *
 * <p>See {@link DefaultReferenceComparator} for the only implementation.</p>
 */
public interface ReferenceComparator {
    ReferenceComparison compare(RawMeasurement measurement, MetricBaseline baseline);
}
