package com.watchalign.mobile.baseline;

import com.watchalign.mobile.qc.QcModuleResult;

/**
 * Default {@link EvidenceClassifier}.
 *
 * <p><strong>This produces an evidence-strength label, not a QC verdict.</strong> No pass/fail
 * boundary is defined anywhere in this class, and none of the numeric bands below are a
 * manufacturing tolerance or a "this watch is wrong" threshold -- they only describe how much
 * weight one comparison carries as supporting evidence, for a human reviewer or a later,
 * separately-justified promotion decision (see {@link UserFacingFinding}).</p>
 *
 * <p>Classification order, each step capping (never raising) what magnitude alone would say:</p>
 * <ol>
 *   <li>A metric marked {@link ValidationStatus#REJECT} carries no evidence: always
 *       {@link EvidenceStrength#NONE}.</li>
 *   <li>No usable magnitude ({@link ReferenceComparison#madMultiples()} is {@code null}, e.g. the
 *       baseline's MAD is degenerate/too small to normalise against) caps at
 *       {@link EvidenceStrength#WEAK}: a signed/absolute deviation can still be shown, but no
 *       claim about how unusual it is can be made.</li>
 *   <li>Low detector confidence ({@link QcModuleResult.Confidence#LOW}) caps at
 *       {@link EvidenceStrength#WEAK}: an uncertain measurement should not present as strong
 *       evidence regardless of how large its deviation looks.</li>
 *   <li>A metric that is only {@link ValidationStatus#DIAGNOSTIC_ONLY} caps at
 *       {@link EvidenceStrength#WEAK}: research has not yet shown this metric reliably separates
 *       real defects from genuine/photographic noise, so a large deviation on it is not
 *       presented as strong evidence even though it may be numerically striking.</li>
 *   <li>Otherwise (the metric is {@link ValidationStatus#SUPPORTED} or
 *       {@link ValidationStatus#PROMISING} and detector confidence is at least
 *       {@link QcModuleResult.Confidence#MEDIUM}), the MAD-multiple magnitude decides the band,
 *       using the conventional descriptive cut points for "how many typical genuine-population
 *       differences away is this" (1 and 3 MAD-multiples) -- the same order-of-magnitude
 *       convention already used elsewhere in this codebase for robust-statistic severity
 *       (see {@code GenuineBaselineStats}'s {@code 3.0*sigma}/{@code 1.75x} tiers), reused here
 *       for an evidence-strength label rather than introduced fresh for this task:
 *       <ul>
 *         <li>{@code |madMultiples| < 1} -- {@link EvidenceStrength#WEAK}: within the size of
 *             ordinary genuine-to-genuine variation.</li>
 *         <li>{@code 1 <= |madMultiples| < 3} -- {@link EvidenceStrength#MODERATE}.</li>
 *         <li>{@code |madMultiples| >= 3} -- {@link EvidenceStrength#STRONG}.</li>
 *       </ul>
 *   </li>
 * </ol>
 */
public final class DefaultEvidenceClassifier implements EvidenceClassifier {
    private static final double MODERATE_MAD_MULTIPLES = 1.0;
    private static final double STRONG_MAD_MULTIPLES = 3.0;

    @Override
    public Evidence classify(ReferenceComparison comparison, ValidationStatus status,
                              QcModuleResult.Confidence detectorConfidence) {
        if (comparison == null) {
            throw new IllegalArgumentException("comparison must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        if (detectorConfidence == null) {
            throw new IllegalArgumentException("detectorConfidence must not be null");
        }
        EvidenceStrength strength = strengthFor(comparison, status, detectorConfidence);
        return new Evidence(comparison, status, detectorConfidence, strength);
    }

    private static EvidenceStrength strengthFor(ReferenceComparison comparison, ValidationStatus status,
                                                 QcModuleResult.Confidence detectorConfidence) {
        if (status == ValidationStatus.REJECT) {
            return EvidenceStrength.NONE;
        }
        Double madMultiples = comparison.madMultiples();
        if (madMultiples == null) {
            return EvidenceStrength.WEAK;
        }
        if (detectorConfidence == QcModuleResult.Confidence.LOW) {
            return EvidenceStrength.WEAK;
        }
        if (status == ValidationStatus.DIAGNOSTIC_ONLY) {
            return EvidenceStrength.WEAK;
        }
        double magnitude = Math.abs(madMultiples);
        if (magnitude >= STRONG_MAD_MULTIPLES) {
            return EvidenceStrength.STRONG;
        }
        if (magnitude >= MODERATE_MAD_MULTIPLES) {
            return EvidenceStrength.MODERATE;
        }
        return EvidenceStrength.WEAK;
    }
}
