package com.watchalign.mobile.qc;

import com.watchalign.mobile.GmtTriangle12QcModule;
import com.watchalign.mobile.profile.WatchProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Resolves the reusable QC modules enabled by a watch profile. */
public final class QcModuleRegistry {
    private QcModuleRegistry() {}

    public static List<QcModule<?>> modulesFor(WatchProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");

        List<QcModule<?>> modules = new ArrayList<>();
        for (String moduleId : profile.enabledModules) {
            QcModule<?> module = create(moduleId);
            if (module == null) {
                throw new IllegalArgumentException("profile enables unknown QC module: " + moduleId);
            }
            modules.add(module);
        }
        return Collections.unmodifiableList(modules);
    }

    private static QcModule<?> create(String moduleId) {
        if (GmtTriangle12QcModule.ID.equals(moduleId)) return new GmtTriangle12QcModule();
        if (IndexGeometryQcModule.ID.equals(moduleId)) return new IndexGeometryQcModule();
        return null;
    }
}
