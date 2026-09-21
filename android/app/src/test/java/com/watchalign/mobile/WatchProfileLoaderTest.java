package com.watchalign.mobile;

import com.watchalign.mobile.profile.WatchProfile;
import com.watchalign.mobile.profile.WatchProfileLoader;

import org.junit.Test;

import static org.junit.Assert.*;

public class WatchProfileLoaderTest {
    private static final String VALID = "{"
            + "\"schemaVersion\":1,"
            + "\"id\":\"test-model\","
            + "\"brand\":\"Test\","
            + "\"modelFamily\":\"Model\","
            + "\"reference\":\"123\","
            + "\"displayName\":\"Test Model 123\","
            + "\"dialType\":\"APPLIED_INDEX\","
            + "\"capabilities\":[\"applied-index-geometry\",\"date-centering\"],"
            + "\"enabledModules\":[\"index-geometry\"],"
            + "\"calibrationIds\":{\"index-geometry\":\"test-index-v1\"}"
            + "}";

    @Test public void parsesImmutableProfile() {
        WatchProfile p = WatchProfileLoader.parse(VALID);
        assertEquals(1, p.schemaVersion);
        assertEquals("test-model", p.id);
        assertEquals("Test", p.brand);
        assertEquals(WatchProfile.DialType.APPLIED_INDEX, p.dialType);
        assertTrue(p.supports("date-centering"));
        assertTrue(p.moduleEnabled("index-geometry"));
        assertEquals("test-index-v1", p.calibrationIds.get("index-geometry"));

        try {
            p.capabilities.add("x");
            fail("capabilities must be immutable");
        } catch (UnsupportedOperationException expected) {
            // expected
        }
    }

    @Test public void rejectsUnsupportedSchemaVersion() {
        expectFailure(VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":2"));
    }

    @Test public void rejectsMissingRequiredField() {
        expectFailure(VALID.replace("\"brand\":\"Test\",", ""));
    }

    @Test public void rejectsUnknownDialType() {
        expectFailure(VALID.replace("APPLIED_INDEX", "UNKNOWN"));
    }

    @Test public void rejectsDuplicateCapabilities() {
        expectFailure(VALID.replace(
                "\"capabilities\":[\"applied-index-geometry\",\"date-centering\"]",
                "\"capabilities\":[\"date-centering\",\"date-centering\"]"));
    }

    private static void expectFailure(String json) {
        try {
            WatchProfileLoader.parse(json);
            fail("Expected invalid profile to be rejected");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
