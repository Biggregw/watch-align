package com.watchalign.mobile.baseline;

/**
 * Maps a {@link ReferenceComparison#signedDeviation()}'s sign to a human direction word, in
 * terms a reviewer would use ("high"/"low", "outward"/"inward", "left"/"right",
 * "clockwise"/"counter-clockwise", "larger"/"smaller") -- never a millimetre or pixel value, and
 * never a pass/fail word. This is wording only; it carries no opinion about whether the
 * deviation matters (see {@link EvidenceStrength}/{@link ValidationStatus} for that).
 *
 * <p>Sign conventions are not invented here; they are read off the two places in this codebase
 * that already define them:</p>
 * <ul>
 *   <li><b>radial</b> metrics (names containing {@code "radial"}): {@code GmtMarkerQcRepair}'s
 *       own diagnostic text already documents "positive = outward/high, negative = inward/low"
 *       for every marker, so that exact pairing is reused verbatim here rather than restated in
 *       different words.</li>
 *   <li><b>tangential</b>/<b>lateral</b> metrics: {@code marker_consensus_analysis.py}'s
 *       {@code tangential = -dxv * v + dyv * u} definition (tangential unit vector is the
 *       outward-radial unit vector rotated so that positive tangential is the clockwise
 *       direction of travel around the dial, true at every hour position -- algebraically
 *       verified here at h12, h06 and h09). At the top (h12) and bottom (h06) markers only, that
 *       clockwise direction happens to coincide with simple screen left/right (clockwise at the
 *       top moves right, clockwise at the bottom moves left), which is the more natural word for
 *       a marker sitting at the very top or bottom of the dial and matches the vocabulary this
 *       project's own control-set labels already use there (e.g. {@code 12_marker_left},
 *       {@code 12_marker_clockwise_shift} for what is the same underlying direction). At any
 *       other marker position left/right would be geometrically wrong (at h09 the tangential
 *       direction is vertical, not horizontal), so those fall back to clockwise/counter-clockwise,
 *       which is correct everywhere by construction.</li>
 * </ul>
 */
public final class DirectionWording {
    private DirectionWording() {
    }

    public static String describe(MetricKey key, double signedDeviation) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (!Double.isFinite(signedDeviation)) {
            throw new IllegalArgumentException("signedDeviation must be finite");
        }
        if (signedDeviation == 0.0) {
            return "matching";
        }
        boolean positive = signedDeviation > 0.0;
        String metric = key.metricName();
        String marker = key.markerId();

        // Checked before "radial" -- "radial_span" contains both substrings, and span (a shape
        // metric) is the more specific match.
        if (metric.contains("span")) {
            return positive ? "larger" : "smaller";
        }
        if (metric.contains("radial")) {
            return positive ? "high" : "low";
        }
        if (metric.contains("tangential") || metric.contains("lateral")) {
            if ("h12".equals(marker)) {
                return positive ? "right" : "left";
            }
            if ("h06".equals(marker)) {
                return positive ? "left" : "right";
            }
            return positive ? "clockwise" : "counter-clockwise";
        }
        if (metric.contains("axis")) {
            return positive ? "clockwise" : "counter-clockwise";
        }
        return positive ? "above" : "below";
    }
}
