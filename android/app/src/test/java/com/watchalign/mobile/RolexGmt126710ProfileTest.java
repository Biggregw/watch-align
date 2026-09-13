package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.watchalign.mobile.profile.WatchProfile;
import com.watchalign.mobile.profile.WatchProfileLoader;
import com.watchalign.mobile.qc.IndexGeometryQcModule;
import com.watchalign.mobile.qc.QcModule;
import com.watchalign.mobile.qc.QcModuleRegistry;

import java.util.List;

import org.junit.Test;

public class RolexGmt126710ProfileTest {
    private static final String PROFILE_JSON = "{"
            + "\"schemaVersion\":1,"
            + "\"id\":\"rolex.gmt-master-ii.126710-family\","
            + "\"brand\":\"Rolex\","
            + "\"modelFamily\":\"GMT-Master II\","
            + "\"reference\":\"126710-family\","
            + "\"displayName\":\"Rolex GMT-Master II 126710 family\","
            + "\"dialType\":\"APPLIED_INDEX\","
            + "\"capabilities\":[\"applied-index-geometry\",\"triangle-12-relationship\",\"date-centering\",\"cyclops-alignment\",\"rotating-bezel-alignment\",\"rehaut-alignment\",\"sel-gap\"],"
            + "\"enabledModules\":[\"gmt.triangle12.relationship\",\"generic.index_geometry\"],"
            + "\"calibrationIds\":{\"triangle12\":\"rolex-gmt-126710-triangle12-corrected-pilot-v2\"}"
            + "}";

    @Test public void profileDeclaresExpectedCapabilitiesAndCalibration() {
        WatchProfile profile = WatchProfileLoader.parse(PROFILE_JSON);
        assertEquals("Rolex", profile.brand);
        assertEquals("GMT-Master II", profile.modelFamily);
        assertEquals("126710-family", profile.reference);
        assertEquals(WatchProfile.DialType.APPLIED_INDEX, profile.dialType);
        assertTrue(profile.supports("triangle-12-relationship"));
        assertTrue(profile.supports("applied-index-geometry"));
        assertTrue(profile.supports("date-centering"));
        assertEquals("rolex-gmt-126710-triangle12-corrected-pilot-v2",
                profile.calibrationIds.get("triangle12"));
    }

    @Test public void profileResolvesTriangleAndPerIndexModules() {
        WatchProfile profile = WatchProfileLoader.parse(PROFILE_JSON);
        List<QcModule<?>> modules = QcModuleRegistry.modulesFor(profile);
        assertEquals(2, modules.size());
        assertEquals(GmtTriangle12QcModule.ID, modules.get(0).id());
        assertEquals(IndexGeometryQcModule.ID, modules.get(1).id());
    }

    @Test(expected = IllegalArgumentException.class)
    public void registryRejectsUnknownModuleId() {
        WatchProfile profile = WatchProfileLoader.parse(PROFILE_JSON.replace(
                "gmt.triangle12.relationship", "unknown.module"));
        QcModuleRegistry.modulesFor(profile);
    }
}
