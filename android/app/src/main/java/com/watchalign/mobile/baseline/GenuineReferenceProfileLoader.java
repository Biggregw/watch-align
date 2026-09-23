package com.watchalign.mobile.baseline;

import android.content.res.AssetManager;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a versioned empirical genuine-reference profile (see
 * {@code docs/research/gmt-genuine-baseline-profile-v1.json} for the canonical schema and the
 * research provenance behind the numbers) into an immutable {@link GenuineReferenceProfile}.
 *
 * <p>Mirrors {@code com.watchalign.mobile.profile.WatchProfileLoader}'s DTO/validate/construct
 * pattern. JSON keys are snake_case (matching this project's Python research-tooling convention
 * for every other profile JSON, e.g. {@code gmt_12_triangle_profile.json}); Gson
 * {@code @SerializedName} bridges that to camelCase Java fields.</p>
 *
 * <p>This loader performs no interpretation: it does not decide which metrics are usable, does
 * not compute anything, and does not touch master/ideal geometry. It only turns a JSON file into
 * the typed objects {@link ReferenceComparator}/{@link EvidenceClassifier}/
 * {@link FindingPresenter} consume.</p>
 */
public final class GenuineReferenceProfileLoader {
    private static final Gson GSON = new Gson();

    private GenuineReferenceProfileLoader() {
    }

    public static GenuineReferenceProfile load(AssetManager assets, String assetPath) throws IOException {
        if (assets == null) throw new IllegalArgumentException("assets == null");
        if (isBlank(assetPath)) throw new IllegalArgumentException("assetPath is blank");
        try (InputStream in = assets.open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line).append('\n');
            return parse(json.toString());
        }
    }

    public static GenuineReferenceProfile parse(String json) {
        if (isBlank(json)) throw new IllegalArgumentException("profile JSON is blank");
        final ProfileDto dto;
        try {
            dto = GSON.fromJson(json, ProfileDto.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("invalid genuine-reference-profile JSON", e);
        }
        if (dto == null) throw new IllegalArgumentException("profile JSON produced no object");
        if (isBlank(dto.profileId)) throw new IllegalArgumentException("profile_id is required");
        if (dto.metrics == null) throw new IllegalArgumentException("metrics array is required");

        Map<MetricKey, MetricBaseline> metrics = new HashMap<>();
        for (MetricDto m : dto.metrics) {
            if (isBlank(m.marker) || isBlank(m.key)) {
                throw new IllegalArgumentException("metric entry missing marker/key: " + m.key);
            }
            ValidationStatus status;
            try {
                status = ValidationStatus.valueOf(m.status);
            } catch (Exception e) {
                throw new IllegalArgumentException("metric " + m.key + " has unknown status: " + m.status);
            }
            MetricKey metricKey = new MetricKey(m.marker, metricNameFrom(m.key, m.marker));
            MetricBaseline baseline = new MetricBaseline(
                    m.nWatches, m.median, m.mad, m.p10, m.p90,
                    m.repeatabilityError, m.missingRate == null ? 0.0 : m.missingRate, status);
            metrics.put(metricKey, baseline);
        }

        String source = "profile_version=" + dto.profileVersion
                + (dto.sourceReport != null ? "; " + dto.sourceReport.path + "@" + dto.sourceReport.commit : "")
                + "; status=" + dto.status;
        return new GenuineReferenceProfile(dto.profileId, source, metrics);
    }

    private static String metricNameFrom(String fullKey, String marker) {
        String prefix = marker + ".";
        return fullKey.startsWith(prefix) ? fullKey.substring(prefix.length()) : fullKey;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ProfileDto {
        @SerializedName("profile_id") String profileId;
        @SerializedName("profile_version") int profileVersion;
        String status;
        @SerializedName("source_report") SourceReportDto sourceReport;
        List<MetricDto> metrics;
    }

    private static final class SourceReportDto {
        String path;
        String commit;
    }

    private static final class MetricDto {
        String key;
        String marker;
        @SerializedName("n_watches") int nWatches;
        double median;
        double mad;
        double p10;
        double p90;
        @SerializedName("repeatability_error") Double repeatabilityError;
        @SerializedName("missing_rate") Double missingRate;
        String status;
    }
}
