package com.watchalign.mobile.qc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Common result contract returned by reusable QC measurement modules.
 *
 * <p>Contains raw measurements, confidence and evidence only. It intentionally does not contain
 * model-specific pass/fail logic. Classification belongs in the assessment layer.</p>
 */
public final class QcModuleResult {
    public enum Confidence {
        LOW,
        MEDIUM,
        HIGH
    }

    private final String moduleId;
    private final List<RawMeasurement> measurements;
    private final Confidence confidence;
    private final List<String> evidence;

    public QcModuleResult(
            String moduleId,
            List<RawMeasurement> measurements,
            Confidence confidence,
            List<String> evidence) {
        if (moduleId == null || moduleId.trim().isEmpty()) {
            throw new IllegalArgumentException("module id must not be blank");
        }
        this.moduleId = moduleId;
        this.measurements = Collections.unmodifiableList(new ArrayList<>(
                measurements == null ? Collections.emptyList() : measurements));
        this.confidence = confidence == null ? Confidence.LOW : confidence;
        this.evidence = Collections.unmodifiableList(new ArrayList<>(
                evidence == null ? Collections.emptyList() : evidence));
    }

    public String moduleId() {
        return moduleId;
    }

    public List<RawMeasurement> measurements() {
        return measurements;
    }

    public Confidence confidence() {
        return confidence;
    }

    public List<String> evidence() {
        return evidence;
    }

    public RawMeasurement measurement(String id) {
        if (id == null) return null;
        for (RawMeasurement measurement : measurements) {
            if (id.equals(measurement.id())) return measurement;
        }
        return null;
    }
}
