package com.watchalign.mobile.baseline;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A loadable collection of {@link MetricBaseline}s for one watch model, keyed by
 * {@link MetricKey}.
 *
 * <p>This class defines the shape of the artifact. {@link GenuineReferenceProfileLoader} populates
 * one from the versioned JSON asset in {@code assets/genuine-profiles/}; nothing in this package
 * is referenced from any production QC report path yet -- see
 * {@code docs/research/gmt-genuine-baseline-android-integration-design.md}.</p>
 *
 * <p>{@code sourceDescription} is free text recording provenance (e.g. a branch/commit and
 * population size) and must never be phrased as a manufacturing-tolerance or authenticity
 * claim -- callers should treat it as a citation, not a warranty.</p>
 */
public final class GenuineReferenceProfile {
    /** A profile with no metrics. Useful as a safe default before any baseline is loaded. */
    public static final GenuineReferenceProfile EMPTY =
            new GenuineReferenceProfile("empty", "no genuine baseline loaded", Collections.emptyMap());

    private final String profileId;
    private final String sourceDescription;
    private final Map<MetricKey, MetricBaseline> metrics;

    public GenuineReferenceProfile(String profileId, String sourceDescription,
                                    Map<MetricKey, MetricBaseline> metrics) {
        if (profileId == null || profileId.trim().isEmpty()) {
            throw new IllegalArgumentException("profileId must not be blank");
        }
        this.profileId = profileId;
        this.sourceDescription = sourceDescription == null ? "" : sourceDescription;
        this.metrics = Collections.unmodifiableMap(new HashMap<>(
                metrics == null ? Collections.emptyMap() : metrics));
    }

    public String profileId() {
        return profileId;
    }

    public String sourceDescription() {
        return sourceDescription;
    }

    public Map<MetricKey, MetricBaseline> metrics() {
        return metrics;
    }

    /** The baseline for one metric, or {@code null} if this profile has none for it. */
    public MetricBaseline baseline(MetricKey key) {
        return metrics.get(key);
    }
}
