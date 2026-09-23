package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.QcModuleResult;

/**
 * Stage 2 -&gt; stage 3: classifies a comparison into {@link Evidence}, given the metric's research
 * status and the detector's own confidence in the measurement it was built from.
 *
 * <p>See {@link DefaultEvidenceClassifier} for the only implementation.</p>
 */
public interface EvidenceClassifier {
    Evidence classify(ReferenceComparison comparison, ValidationStatus status,
                       QcModuleResult.Confidence detectorConfidence);
}
