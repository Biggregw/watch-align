package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.RawMeasurement;

/**
 * Stage 1 -&gt; stage 2: compares one raw measurement against its empirical baseline.
 *
 * <p>No implementation is provided by this branch. Implementing this requires a populated
 * {@link GenuineReferenceProfile}, which does not exist yet -- see the "Waiting for genuine
 * baseline" list.</p>
 */
public interface ReferenceComparator {
    ReferenceComparison compare(RawMeasurement measurement, MetricBaseline baseline);
}
