package com.watchalign.mobile.baseline;

/**
 * Empirical genuine-population summary for one metric, computed with the physical watch (not the
 * photograph) as the independent statistical unit.
 *
 * <p>Deliberately holds no interpretation: no pass/fail boundary, no defect threshold, no
 * authenticity signal. It is the numeric shape described by
 * {@code docs/research/gmt-genuine-baseline-validation-plan.md} sections 4-5, nothing more.</p>
 *
 * <p>Every field is required and finite-validated at construction time. There is no default or
 * placeholder numeric value anywhere in this class -- an instance can only be built by supplying
 * real numbers, which this branch never does (see {@link GenuineReferenceProfile#EMPTY}).</p>
 */
public final class MetricBaseline {
    private final int n;
    private final double median;
    private final double mad;
    private final double p10;
    private final double p90;
    private final Double repeatabilityError;
    private final double missingRate;
    private final ValidationStatus status;

    /**
     * @param n independent genuine-watch count backing this baseline (physical watches, not
     *          photographs -- repeated images of one watch must already be collapsed before this
     *          value is computed).
     * @param median robust central value across per-watch medians.
     * @param mad median absolute deviation across per-watch medians.
     * @param p10 10th percentile across per-watch medians.
     * @param p90 90th percentile across per-watch medians.
     * @param repeatabilityError median within-watch absolute deviation across repeated
     *                           photographs of the same watch, or {@code null} if no watch in
     *                           this baseline has more than one usable image yet.
     * @param missingRate fraction, in {@code [0,1]}, of attempted images where this metric could
     *                    not be measured (detector/pose failure), kept visible rather than
     *                    silently excluded.
     * @param status this metric's current research classification.
     */
    public MetricBaseline(int n, double median, double mad, double p10, double p90,
                           Double repeatabilityError, double missingRate, ValidationStatus status) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative");
        }
        requireFinite("median", median);
        requireFinite("mad", mad);
        requireFinite("p10", p10);
        requireFinite("p90", p90);
        if (repeatabilityError != null) {
            requireFinite("repeatabilityError", repeatabilityError);
        }
        if (missingRate < 0.0 || missingRate > 1.0) {
            throw new IllegalArgumentException("missingRate must be within [0,1]");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.n = n;
        this.median = median;
        this.mad = mad;
        this.p10 = p10;
        this.p90 = p90;
        this.repeatabilityError = repeatabilityError;
        this.missingRate = missingRate;
        this.status = status;
    }

    public int n() {
        return n;
    }

    public double median() {
        return median;
    }

    public double mad() {
        return mad;
    }

    public double p10() {
        return p10;
    }

    public double p90() {
        return p90;
    }

    /** Median within-watch absolute deviation, or {@code null} if not yet estimable. */
    public Double repeatabilityError() {
        return repeatabilityError;
    }

    public double missingRate() {
        return missingRate;
    }

    public ValidationStatus status() {
        return status;
    }

    private static void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
