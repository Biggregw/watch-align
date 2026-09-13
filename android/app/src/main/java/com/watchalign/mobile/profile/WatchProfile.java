package com.watchalign.mobile.profile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Immutable, declarative description of one supported watch/reference family. */
public final class WatchProfile {
    public enum DialType { APPLIED_INDEX, PRINTED, MIXED }

    public final int schemaVersion;
    public final String id;
    public final String brand;
    public final String modelFamily;
    public final String reference;
    public final String displayName;
    public final DialType dialType;
    public final Set<String> capabilities;
    public final Set<String> enabledModules;
    public final Map<String, String> calibrationIds;

    WatchProfile(
            int schemaVersion,
            String id,
            String brand,
            String modelFamily,
            String reference,
            String displayName,
            DialType dialType,
            Set<String> capabilities,
            Set<String> enabledModules,
            Map<String, String> calibrationIds) {
        this.schemaVersion = schemaVersion;
        this.id = id;
        this.brand = brand;
        this.modelFamily = modelFamily;
        this.reference = reference;
        this.displayName = displayName;
        this.dialType = dialType;
        this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
        this.enabledModules = Collections.unmodifiableSet(new LinkedHashSet<>(enabledModules));
        this.calibrationIds = Collections.unmodifiableMap(new LinkedHashMap<>(calibrationIds));
    }

    public boolean supports(String capability) {
        return capability != null && capabilities.contains(capability);
    }

    public boolean moduleEnabled(String moduleId) {
        return moduleId != null && enabledModules.contains(moduleId);
    }
}
