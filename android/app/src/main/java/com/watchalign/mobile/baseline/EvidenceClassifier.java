package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.QcModuleResult;

/**
 * Stage 2 -&gt; stage 3: classifies a comparison into {@link Evidence}, given the metric's research
 * status and the detector's own confidence in the measurement it was built from.
 *
 * <p>No implementation is provided by this branch. Deciding the {@link EvidenceStrength}
 * thresholds requires the empirical baseline's repeatability data -- see the "Waiting for
 * genuine baseline" list.</p>
 */
public interface EvidenceClassifier {
    Evidence classify(ReferenceComparison comparison, ValidationStatus status,
                       QcModuleResult.Confidence detectorConfidence);
}
