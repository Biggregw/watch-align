package com.watchalign.mobile.baseline;

/**
 * Stage 4 of the measurement pipeline: what, if anything, is shown to a user about one metric.
 *
 * <p>There is deliberately no pass/fail, genuine/replica, or authenticity-scored subclass here.
 * Every metric resolves to exactly one of the three cases below -- see
 * {@code docs/research/gmt-genuine-baseline-android-integration-design.md} section 6:</p>
 *
 * <ul>
 *   <li>{@link MeasurableDeviation} -- the metric is {@link ValidationStatus#SUPPORTED} and the
 *       evidence is at least {@link EvidenceStrength#MODERATE}.</li>
 *   <li>{@link Inconclusive} -- the metric is {@link ValidationStatus#PROMISING} or
 *       {@link ValidationStatus#DIAGNOSTIC_ONLY}, or the evidence is weaker than
 *       {@code MODERATE} even on a supported metric.</li>
 *   <li>{@link DetectorConfidenceInsufficient} -- the underlying measurement itself could not be
 *       trusted or could not be made at all.</li>
 * </ul>
 */
public abstract class UserFacingFinding {
    private UserFacingFinding() {
    }

    public abstract MetricKey key();

    /** A measurable deviation from the genuine reference, supported by evidence. */
    public static final class MeasurableDeviation extends UserFacingFinding {
        private final Evidence evidence;

        public MeasurableDeviation(Evidence evidence) {
            if (evidence == null) {
                throw new IllegalArgumentException("evidence must not be null");
            }
            if (evidence.status() != ValidationStatus.SUPPORTED) {
                throw new IllegalArgumentException(
                        "MeasurableDeviation requires a SUPPORTED metric, was " + evidence.status());
            }
            this.evidence = evidence;
        }

        public Evidence evidence() {
            return evidence;
        }

        @Override
        public MetricKey key() {
            return evidence.comparison().key();
        }
    }

    /** The measurement exists but the evidence does not yet support a deviation claim. */
    public static final class Inconclusive extends UserFacingFinding {
        private final MetricKey key;
        private final String reason;

        public Inconclusive(MetricKey key, String reason) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            this.key = key;
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }

        @Override
        public MetricKey key() {
            return key;
        }
    }

    /** The detector could not measure this metric confidently, or at all, on this photo. */
    public static final class DetectorConfidenceInsufficient extends UserFacingFinding {
        private final MetricKey key;
        private final String reason;

        public DetectorConfidenceInsufficient(MetricKey key, String reason) {
            if (key == null) {
                throw new IllegalArgumentException("key must not be null");
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("reason must not be blank");
            }
            this.key = key;
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }

        @Override
        public MetricKey key() {
            return key;
        }
    }
}
