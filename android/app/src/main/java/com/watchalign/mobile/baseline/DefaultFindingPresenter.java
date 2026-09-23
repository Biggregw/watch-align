package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.QcModuleResult;

/**
 * Default {@link FindingPresenter}.
 *
 * <p>Only three outcomes exist, matching {@link UserFacingFinding} exactly -- there is no
 * pass/fail case to fall into by omission:</p>
 * <ol>
 *   <li>{@link QcModuleResult.Confidence#LOW} detector confidence always resolves to
 *       {@link UserFacingFinding.DetectorConfidenceInsufficient}: an untrusted measurement is
 *       never dressed up as a deviation claim, however large it looks numerically.</li>
 *   <li>Otherwise, a {@link ValidationStatus#SUPPORTED} metric with at least
 *       {@link EvidenceStrength#MODERATE} evidence resolves to
 *       {@link UserFacingFinding.MeasurableDeviation}.</li>
 *   <li>Everything else (status is {@code PROMISING}, {@code DIAGNOSTIC_ONLY} or {@code REJECT},
 *       or a {@code SUPPORTED} metric whose evidence is only {@code WEAK} or {@code NONE})
 *       resolves to {@link UserFacingFinding.Inconclusive}.</li>
 * </ol>
 *
 * <p>{@link #describe(UserFacingFinding)} turns a finding into the one sentence a reviewer or
 * overlay may show, using {@link DirectionWording} for the directional word and always phrasing
 * a deviation relative to the current genuine reference population -- e.g. "12 apex_radial
 * appears high relative to the current genuine reference population (moderate evidence)" -- and
 * never as a millimetre/pixel quantity or a pass/fail verdict.</p>
 */
public final class DefaultFindingPresenter implements FindingPresenter {
    private static final double MODERATE_STRENGTH_ORDINAL = EvidenceStrength.MODERATE.ordinal();

    @Override
    public UserFacingFinding present(Evidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("evidence must not be null");
        }
        MetricKey key = evidence.comparison().key();

        if (evidence.detectorConfidence() == QcModuleResult.Confidence.LOW) {
            return new UserFacingFinding.DetectorConfidenceInsufficient(key,
                    "detector confidence is LOW for this measurement, so it is not trusted enough "
                            + "to compare against the genuine reference population");
        }

        if (evidence.status() == ValidationStatus.SUPPORTED
                && evidence.strength().ordinal() >= MODERATE_STRENGTH_ORDINAL) {
            return new UserFacingFinding.MeasurableDeviation(evidence);
        }

        return new UserFacingFinding.Inconclusive(key, inconclusiveReason(evidence));
    }

    private static String inconclusiveReason(Evidence evidence) {
        switch (evidence.status()) {
            case PROMISING:
                return "this metric is still under blind-defect validation (PROMISING); its "
                        + "evidence is not yet confirmed strong enough to state a deviation";
            case DIAGNOSTIC_ONLY:
                return "this metric is diagnostic-only; it is useful for debugging detection but "
                        + "has not been validated as a QC signal";
            case REJECT:
                return "this metric did not separate known defects from genuine variation and is "
                        + "not used for QC";
            case SUPPORTED:
                return "the observed value is within the range of ordinary genuine-to-genuine "
                        + "variation for this metric";
            default:
                throw new IllegalStateException("unhandled ValidationStatus: " + evidence.status());
        }
    }

    /**
     * The single sentence a reviewer or overlay may show for one finding. Never contains a
     * millimetre/pixel quantity, a percentage confidence, or a pass/fail word.
     */
    public String describe(UserFacingFinding finding) {
        if (finding == null) {
            throw new IllegalArgumentException("finding must not be null");
        }
        MetricKey key = finding.key();
        String label = key.markerId() + " " + key.metricName();

        if (finding instanceof UserFacingFinding.MeasurableDeviation) {
            Evidence evidence = ((UserFacingFinding.MeasurableDeviation) finding).evidence();
            String direction = DirectionWording.describe(key, evidence.comparison().signedDeviation());
            String strength = evidence.strength().name().toLowerCase(java.util.Locale.ROOT);
            return label + " appears " + direction
                    + " relative to the current genuine reference population (" + strength + " evidence)";
        }
        if (finding instanceof UserFacingFinding.Inconclusive) {
            return label + ": " + ((UserFacingFinding.Inconclusive) finding).reason();
        }
        if (finding instanceof UserFacingFinding.DetectorConfidenceInsufficient) {
            return label + ": " + ((UserFacingFinding.DetectorConfidenceInsufficient) finding).reason();
        }
        throw new IllegalStateException("unhandled UserFacingFinding subclass: " + finding.getClass());
    }
}
