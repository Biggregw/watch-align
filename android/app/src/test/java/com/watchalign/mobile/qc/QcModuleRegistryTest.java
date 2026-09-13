package com.watchalign.mobile.qc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.watchalign.mobile.profile.WatchProfile;
import com.watchalign.mobile.profile.WatchProfileLoader;

import org.junit.Test;

import java.util.List;

public class QcModuleRegistryTest {
    @Test public void appliedIndexProfileCanResolveGenericIndexGeometry() {
        WatchProfile profile = WatchProfileLoader.parse("{"
                + "\"schemaVersion\":1,"
                + "\"id\":\"test.applied\","
                + "\"brand\":\"Test\","
                + "\"modelFamily\":\"Applied\","
                + "\"reference\":\"1\","
                + "\"displayName\":\"Applied Test\","
                + "\"dialType\":\"APPLIED_INDEX\","
                + "\"capabilities\":[\"applied-index-geometry\"],"
                + "\"enabledModules\":[\"generic.index_geometry\"],"
                + "\"calibrationIds\":{}"
                + "}");

        List<QcModule<?>> modules = QcModuleRegistry.modulesFor(profile);
        assertEquals(1, modules.size());
        assertTrue(modules.get(0) instanceof IndexGeometryQcModule);
    }

    @Test(expected = IllegalArgumentException.class)
    public void printedDialCannotEnableAppliedIndexGeometryWithoutCapability() {
        WatchProfile profile = WatchProfileLoader.parse("{"
                + "\"schemaVersion\":1,"
                + "\"id\":\"test.printed\","
                + "\"brand\":\"Test\","
                + "\"modelFamily\":\"Printed\","
                + "\"reference\":\"1\","
                + "\"displayName\":\"Printed Test\","
                + "\"dialType\":\"PRINTED\","
                + "\"capabilities\":[],"
                + "\"enabledModules\":[\"generic.index_geometry\"],"
                + "\"calibrationIds\":{}"
                + "}");
        QcModuleRegistry.modulesFor(profile);
    }

    @Test(expected = IllegalArgumentException.class)
    public void gmtTriangleModuleRequiresTriangleCapability() {
        WatchProfile profile = WatchProfileLoader.parse("{"
                + "\"schemaVersion\":1,"
                + "\"id\":\"test.gmt\","
                + "\"brand\":\"Test\","
                + "\"modelFamily\":\"GMT\","
                + "\"reference\":\"1\","
                + "\"displayName\":\"GMT Test\","
                + "\"dialType\":\"APPLIED_INDEX\","
                + "\"capabilities\":[\"applied-index-geometry\"],"
                + "\"enabledModules\":[\"gmt.triangle12.relationship\"],"
                + "\"calibrationIds\":{}"
                + "}");
        QcModuleRegistry.modulesFor(profile);
    }
}
