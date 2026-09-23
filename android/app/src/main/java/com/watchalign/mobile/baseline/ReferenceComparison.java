package com.watchalign.mobile.baseline;

/**
 * Stage 2 of the measurement pipeline: one observed value compared against its
 * {@link MetricBaseline}. Pure arithmetic -- no wording, no severity, no colour, no verdict.
 *
 * <p>Two normalised forms are exposed, deliberately kept separate because they answer different
 * questions:</p>
 * <ul>
 *   <li>{@link #standardizedDeviation()} -- deviation relative to same-watch photographic
 *       repeatability noise. Only populated when the baseline carries a
 *       {@link MetricBaseline#repeatabilityError()}; most metrics in the current profile do not
 *       have one yet (repeated photographs of the same genuine watch have not been measured for
 *       every metric), so this is usually {@code null}.</li>
 *   <li>{@link #madMultiples()} -- deviation relative to the empirical genuine population's own
 *       spread (median absolute deviation), i.e. "how many typical genuine-to-genuine
 *       differences is this observation away from the genuine median". Guarded against an
 *       unstable or degenerate (near-zero) MAD -- see {@link #MIN_MAD_FOR_NORMALISATION}.</li>
 * </ul>
 */
public final class ReferenceComparison {
    /** Below this MAD (in the metric's own units), a MAD-multiple would be numerically unstable
     * (a tiny denominator turning an ordinary-sized deviation into an enormous, meaningless
     * multiple) rather than informative, so {@link #madMultiples()} returns {@code null} instead. */
    public static final double MIN_MAD_FOR_NORMALISATION = 1e-6;

    private final MetricKey key;
    private final double observedValue;
    private final MetricBaseline baseline;
    private final double signedDeviation;
    private final Double standardizedDeviation;
    private final Double madMultiples;

    public ReferenceComparison(MetricKey key, double observedValue, MetricBaseline baseline) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (!Double.isFinite(observedValue)) {
            throw new IllegalArgumentException("observedValue must be finite");
        }
        if (baseline == null) {
            throw new IllegalArgumentException("baseline must not be null");
        }
        this.key = key;
        this.observedValue = observedValue;
        this.baseline = baseline;
        this.signedDeviation = observedValue - baseline.median();
        Double repeatability = baseline.repeatabilityError();
        this.standardizedDeviation = (repeatability != null && repeatability > 0.0)
                ? Double.valueOf(this.signedDeviation / repeatability)
                : null;
        this.madMultiples = (baseline.mad() > MIN_MAD_FOR_NORMALISATION)
                ? Double.valueOf(this.signedDeviation / baseline.mad())
                : null;
    }

    public MetricKey key() {
        return key;
    }

    public double observedValue() {
        return observedValue;
    }

    public MetricBaseline baseline() {
        return baseline;
    }

    /** {@code observedValue - baseline.median()}, same units as the metric. Positive means the
     * observation sits above (numerically greater than) the genuine median. */
    public double signedDeviation() {
        return signedDeviation;
    }

    /** {@code |signedDeviation()|}. */
    public double absoluteDeviation() {
        return Math.abs(signedDeviation);
    }

    /** Deviation divided by the baseline's repeatability error, or {@code null} if that error
     * is not yet known. */
    public Double standardizedDeviation() {
        return standardizedDeviation;
    }

    /** Deviation expressed as a multiple of the genuine population's own MAD, or {@code null}
     * if the baseline's MAD is at or below {@link #MIN_MAD_FOR_NORMALISATION}. */
    public Double madMultiples() {
        return madMultiples;
    }
}
