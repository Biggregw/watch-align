package com.watchalign.mobile.profile;

import android.content.res.AssetManager;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parses and validates versioned GMT 12-triangle calibration JSON. */
public final class Triangle12CalibrationLoader {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    private Triangle12CalibrationLoader() {}

    public static Triangle12Calibration load(AssetManager assets, String assetPath) throws IOException {
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

    public static Triangle12Calibration parse(String json) {
        if (isBlank(json)) throw new IllegalArgumentException("calibration JSON is blank");
        final Dto dto;
        try {
            dto = GSON.fromJson(json, Dto.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("invalid calibration JSON", e);
        }
        if (dto == null) throw new IllegalArgumentException("calibration JSON produced no object");
        validate(dto);

        return new Triangle12Calibration(
                dto.schemaVersion,
                dto.id.trim(),
                dto.profileId.trim(),
                dto.moduleId.trim(),
                dto.provenance.trim(),
                dto.factoryTolerance,
                dto.genuineControlCount,
                new Triangle12Calibration.BaseTo60(
                        dto.baseTo60.pilotObserved,
                        dto.baseTo60.pilotMedian,
                        dto.baseTo60.legacyClassifierMin,
                        dto.baseTo60.legacyClassifierMax),
                new Triangle12Calibration.ApexToCrown(
                        dto.apexToCrown.pilotMedian,
                        dto.apexToCrown.legacyClassifierMin,
                        dto.apexToCrown.legacyClassifierMax),
                new Triangle12Calibration.Rotation(dto.rotation.observedEnvelopeAbsMaxDeg),
                new Triangle12Calibration.Repeatability(dto.repeatability.baseTo60PointPlacementP95),
                dto.notes == null ? Collections.emptyList() : dto.notes);
    }

    private static void validate(Dto dto) {
        if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION)
            throw new IllegalArgumentException("unsupported calibration schemaVersion: " + dto.schemaVersion);
        require(dto.id, "id");
        require(dto.profileId, "profileId");
        require(dto.moduleId, "moduleId");
        require(dto.provenance, "provenance");
        if (dto.factoryTolerance)
            throw new IllegalArgumentException("image-derived calibration must not be marked as factory tolerance");
        if (dto.genuineControlCount <= 0)
            throw new IllegalArgumentException("genuineControlCount must be positive");
        if (dto.baseTo60 == null || dto.apexToCrown == null || dto.rotation == null || dto.repeatability == null)
            throw new IllegalArgumentException("all calibration sections are required");
        if (dto.baseTo60.pilotObserved == null || dto.baseTo60.pilotObserved.isEmpty())
            throw new IllegalArgumentException("baseTo60.pilotObserved is required");
        if (dto.baseTo60.pilotObserved.size() != dto.genuineControlCount)
            throw new IllegalArgumentException("pilot observation count must equal genuineControlCount");
        for (Double value : dto.baseTo60.pilotObserved) finite(value, "baseTo60.pilotObserved");
        range(dto.baseTo60.legacyClassifierMin, dto.baseTo60.legacyClassifierMax, "baseTo60 legacy classifier");
        range(dto.apexToCrown.legacyClassifierMin, dto.apexToCrown.legacyClassifierMax, "apexToCrown legacy classifier");
        finite(dto.baseTo60.pilotMedian, "baseTo60.pilotMedian");
        finite(dto.apexToCrown.pilotMedian, "apexToCrown.pilotMedian");
        if (!Double.isFinite(dto.rotation.observedEnvelopeAbsMaxDeg) || dto.rotation.observedEnvelopeAbsMaxDeg < 0)
            throw new IllegalArgumentException("rotation envelope must be finite and non-negative");
        if (!Double.isFinite(dto.repeatability.baseTo60PointPlacementP95) || dto.repeatability.baseTo60PointPlacementP95 < 0)
            throw new IllegalArgumentException("repeatability p95 must be finite and non-negative");
    }

    private static void range(double min, double max, String field) {
        finite(min, field + " min"); finite(max, field + " max");
        if (min > max) throw new IllegalArgumentException(field + " min exceeds max");
    }

    private static void finite(Double value, String field) {
        if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
    }

    private static void require(String value, String field) {
        if (isBlank(value)) throw new IllegalArgumentException(field + " is required");
    }

    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }

    private static final class Dto {
        int schemaVersion;
        String id;
        String profileId;
        String moduleId;
        String provenance;
        boolean factoryTolerance;
        int genuineControlCount;
        BaseTo60Dto baseTo60;
        ApexToCrownDto apexToCrown;
        RotationDto rotation;
        RepeatabilityDto repeatability;
        List<String> notes;
    }
    private static final class BaseTo60Dto {
        List<Double> pilotObserved = new ArrayList<>();
        double pilotMedian;
        double legacyClassifierMin;
        double legacyClassifierMax;
    }
    private static final class ApexToCrownDto {
        double pilotMedian;
        double legacyClassifierMin;
        double legacyClassifierMax;
    }
    private static final class RotationDto { double observedEnvelopeAbsMaxDeg; }
    private static final class RepeatabilityDto { double baseTo60PointPlacementP95; }
}
