package com.watchalign.mobile.profile;

import android.content.res.AssetManager;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses and validates versioned watch-profile JSON into immutable WatchProfile objects. */
public final class WatchProfileLoader {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    private WatchProfileLoader() {}

    public static WatchProfile load(AssetManager assets, String assetPath) throws IOException {
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

    public static WatchProfile parse(String json) {
        if (isBlank(json)) throw new IllegalArgumentException("profile JSON is blank");
        final ProfileDto dto;
        try {
            dto = GSON.fromJson(json, ProfileDto.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("invalid profile JSON", e);
        }
        if (dto == null) throw new IllegalArgumentException("profile JSON produced no object");
        validate(dto);

        Set<String> capabilities = dto.capabilities == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(dto.capabilities);
        Set<String> modules = dto.enabledModules == null
                ? Collections.emptySet()
                : new LinkedHashSet<>(dto.enabledModules);
        Map<String, String> calibrationIds = dto.calibrationIds == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(dto.calibrationIds);

        return new WatchProfile(
                dto.schemaVersion,
                dto.id.trim(),
                dto.brand.trim(),
                dto.modelFamily.trim(),
                dto.reference.trim(),
                dto.displayName.trim(),
                dto.dialType,
                capabilities,
                modules,
                calibrationIds);
    }

    private static void validate(ProfileDto dto) {
        if (dto.schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported profile schemaVersion: " + dto.schemaVersion);
        }
        require(dto.id, "id");
        require(dto.brand, "brand");
        require(dto.modelFamily, "modelFamily");
        require(dto.reference, "reference");
        require(dto.displayName, "displayName");
        if (dto.dialType == null) throw new IllegalArgumentException("dialType is required or unsupported");
        validateStrings(dto.capabilities, "capabilities");
        validateStrings(dto.enabledModules, "enabledModules");
        if (dto.calibrationIds != null) {
            for (Map.Entry<String, String> e : dto.calibrationIds.entrySet()) {
                require(e.getKey(), "calibrationIds key");
                require(e.getValue(), "calibrationIds[" + e.getKey() + "]");
            }
        }
    }

    private static void validateStrings(List<String> values, String field) {
        if (values == null) return;
        Set<String> seen = new LinkedHashSet<>();
        for (String value : values) {
            require(value, field + " entry");
            if (!seen.add(value)) throw new IllegalArgumentException(field + " contains duplicate: " + value);
        }
    }

    private static void require(String value, String field) {
        if (isBlank(value)) throw new IllegalArgumentException(field + " is required");
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class ProfileDto {
        int schemaVersion;
        String id;
        String brand;
        String modelFamily;
        String reference;
        String displayName;
        WatchProfile.DialType dialType;
        List<String> capabilities;
        List<String> enabledModules;
        Map<String, String> calibrationIds;
    }
}
