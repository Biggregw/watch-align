package com.watchalign.mobile.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Versioned, model-specific calibration data for the GMT 12-triangle relationship module. */
public final class Triangle12Calibration {
    public final int schemaVersion;
    public final String id;
    public final String profileId;
    public final String moduleId;
    public final String provenance;
    public final boolean factoryTolerance;
    public final int genuineControlCount;
    public final BaseTo60 baseTo60;
    public final ApexToCrown apexToCrown;
    public final Rotation rotation;
    public final Repeatability repeatability;
    public final List<String> notes;

    Triangle12Calibration(int schemaVersion, String id, String profileId, String moduleId,
            String provenance, boolean factoryTolerance, int genuineControlCount,
            BaseTo60 baseTo60, ApexToCrown apexToCrown, Rotation rotation,
            Repeatability repeatability, List<String> notes) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.profileId = profileId;
        this.moduleId = moduleId;
        this.provenance = provenance;
        this.factoryTolerance = factoryTolerance;
        this.genuineControlCount = genuineControlCount;
        this.baseTo60 = baseTo60;
        this.apexToCrown = apexToCrown;
        this.rotation = rotation;
        this.repeatability = repeatability;
        this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
    }

    public static final class BaseTo60 {
        public final List<Double> pilotObserved;
        public final double pilotMedian;
        public final double legacyClassifierMin;
        public final double legacyClassifierMax;
        BaseTo60(List<Double> observed, double median, double min, double max) {
            this.pilotObserved = Collections.unmodifiableList(new ArrayList<>(observed));
            this.pilotMedian = median;
            this.legacyClassifierMin = min;
            this.legacyClassifierMax = max;
        }
        public double pilotMin() { return Collections.min(pilotObserved); }
        public double pilotMax() { return Collections.max(pilotObserved); }
    }

    public static final class ApexToCrown {
        public final double pilotMedian;
        public final double legacyClassifierMin;
        public final double legacyClassifierMax;
        ApexToCrown(double median, double min, double max) {
            this.pilotMedian = median;
            this.legacyClassifierMin = min;
            this.legacyClassifierMax = max;
        }
    }

    public static final class Rotation {
        public final double observedEnvelopeAbsMaxDeg;
        Rotation(double max) { this.observedEnvelopeAbsMaxDeg = max; }
    }

    public static final class Repeatability {
        public final double baseTo60PointPlacementP95;
        Repeatability(double p95) { this.baseTo60PointPlacementP95 = p95; }
    }
}
