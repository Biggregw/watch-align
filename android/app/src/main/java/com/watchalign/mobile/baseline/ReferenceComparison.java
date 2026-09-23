package com.watchalign.mobile.baseline;

/**
 * Stage 2 of the measurement pipeline: one observed value compared against its
 * {@link MetricBaseline}. Pure arithmetic -- no wording, no severity, no colour, no verdict.
 *
 * <p>{@link #standardizedDeviation()} is only populated when the baseline carries a
 * {@link MetricBaseline#repeatabilityError()} to divide by; a signed deviation is never turned
 * into a standardized one against an absent or zero noise floor.</p>
 */
public final class ReferenceComparison {
    private final MetricKey key;
    private final double observedValue;
    private final MetricBaseline baseline;
    private final double signedDeviation;
    private final Double standardizedDeviation;

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

    /** {@code observedValue - baseline.median()}, same units as the metric. */
    public double signedDeviation() {
        return signedDeviation;
    }

    /** Deviation divided by the baseline's repeatability error, or {@code null} if that error
     * is not yet known. */
    public Double standardizedDeviation() {
        return standardizedDeviation;
    }
}
