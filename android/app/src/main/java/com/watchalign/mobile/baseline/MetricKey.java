package com.watchalign.mobile.baseline;

import java.util.Objects;

/**
 * Identity of one empirical-baseline metric: a marker/component id (e.g. {@code "h12"} for the
 * 12 o'clock marker, or {@code "global"} for a whole-dial metric) plus a metric name (e.g.
 * {@code "centre_r"}).
 *
 * <p>Deliberately mirrors the {@code h{hour}.{metric}} naming convention used by the research
 * branch's {@code gmt-genuine-baseline-results/baseline_watch_level.csv} ({@code marker,
 * feature, ...} columns) so a future loader can parse that file into this type directly, without
 * a separate translation table.</p>
 */
public final class MetricKey {
    private final String markerId;
    private final String metricName;

    public MetricKey(String markerId, String metricName) {
        if (markerId == null || markerId.trim().isEmpty()) {
            throw new IllegalArgumentException("markerId must not be blank");
        }
        if (metricName == null || metricName.trim().isEmpty()) {
            throw new IllegalArgumentException("metricName must not be blank");
        }
        this.markerId = markerId;
        this.metricName = metricName;
    }

    public String markerId() {
        return markerId;
    }

    public String metricName() {
        return metricName;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MetricKey)) return false;
        MetricKey that = (MetricKey) other;
        return markerId.equals(that.markerId) && metricName.equals(that.metricName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(markerId, metricName);
    }

    @Override
    public String toString() {
        return markerId + "." + metricName;
    }
}
