package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.QcModuleResult;

/**
 * Stage 3 of the measurement pipeline: a {@link ReferenceComparison} classified into an
 * {@link EvidenceStrength}, alongside the metric's {@link ValidationStatus} and the detector's
 * own confidence in the underlying measurement.
 *
 * <p>Still contains no user-facing wording and no verdict -- see {@link UserFacingFinding} for
 * that, which is deliberately a separate stage/class so a display string is never assembled
 * before all three inputs (comparison, validation status, detector confidence) are known.</p>
 */
public final class Evidence {
    private final ReferenceComparison comparison;
    private final ValidationStatus status;
    private final QcModuleResult.Confidence detectorConfidence;
    private final EvidenceStrength strength;

    public Evidence(ReferenceComparison comparison, ValidationStatus status,
                     QcModuleResult.Confidence detectorConfidence, EvidenceStrength strength) {
        if (comparison == null) {
            throw new IllegalArgumentException("comparison must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (detectorConfidence == null) {
            throw new IllegalArgumentException("detectorConfidence must not be null");
        }
        if (strength == null) {
            throw new IllegalArgumentException("strength must not be null");
        }
        this.comparison = comparison;
        this.status = status;
        this.detectorConfidence = detectorConfidence;
        this.strength = strength;
    }

    public ReferenceComparison comparison() {
        return comparison;
    }

    public ValidationStatus status() {
        return status;
    }

    public QcModuleResult.Confidence detectorConfidence() {
        return detectorConfidence;
    }

    public EvidenceStrength strength() {
        return strength;
    }
}
