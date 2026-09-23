package com.watchalign.mobile.baseline;

/** Parsing helper for the {@code marker.metric} id convention {@link MetricKey} uses. */
public final class MetricKeys {
    private MetricKeys() {
    }

    /**
     * Parses an id of the form {@code "h12.apex_radial"} into a {@link MetricKey} with
     * {@code markerId="h12"}, {@code metricName="apex_radial"}. An id with no {@code '.'}
     * (e.g. a whole-dial metric with no marker component) is rejected rather than guessed at --
     * every metric this architecture compares against a genuine baseline is per-marker.
     */
    public static MetricKey parse(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        int dot = id.indexOf('.');
        if (dot <= 0 || dot == id.length() - 1) {
            throw new IllegalArgumentException("id must be of the form 'marker.metric': " + id);
        }
        return new MetricKey(id.substring(0, dot), id.substring(dot + 1));
    }
}
