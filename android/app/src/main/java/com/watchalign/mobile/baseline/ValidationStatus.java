package com.watchalign.mobile.baseline;

/**
 * Per-metric research classification, exactly the four categories defined by
 * {@code docs/research/gmt-genuine-baseline-validation-plan.md} section 7.
 *
 * <p>This is a research-quality label about whether a metric is trustworthy enough to report at
 * all, not a per-watch verdict. It says nothing about any individual observation; see
 * {@link UserFacingFinding} for that.</p>
 */
public enum ValidationStatus {
    /** Repeatable on genuine images and separates at least one independently labelled QC defect
     * beyond photographic noise. */
    SUPPORTED,
    /** Repeatable enough, but labelled-defect evidence is still too small to confirm separation. */
    PROMISING,
    /** Useful for debugging pose/detection but not defensible as a QC judgment. */
    DIAGNOSTIC_ONLY,
    /** Unstable, confounded by pose/optics, or shows no useful separation. */
    REJECT
}
