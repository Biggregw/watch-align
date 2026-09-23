package com.watchalign.mobile.baseline;

/**
 * An ordered, qualitative bucket for how much weight one comparison carries as evidence.
 *
 * <p>Deliberately not a probability, score, or percentage -- see
 * {@code docs/research/gmt-genuine-baseline-android-integration-design.md} section 6. A
 * {@code STRONG} value does not by itself select a {@link UserFacingFinding} category; that also
 * depends on the metric's {@link ValidationStatus} (a metric that is only {@code PROMISING} still
 * resolves to {@link UserFacingFinding.Inconclusive} even with strong-looking evidence).</p>
 */
public enum EvidenceStrength {
    NONE,
    WEAK,
    MODERATE,
    STRONG
}
