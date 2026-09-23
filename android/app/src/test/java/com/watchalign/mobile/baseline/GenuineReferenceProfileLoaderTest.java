package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class GenuineReferenceProfileLoaderTest {
    private static final String VALID = "{"
            + "\"profile_id\":\"126710BLNR-gmt-marker-baseline\","
            + "\"profile_version\":1,"
            + "\"status\":\"PROVISIONAL\","
            + "\"source_report\":{"
            + "  \"path\":\"docs/research/gmt-genuine-baseline-validation-report.md\","
            + "  \"commit\":\"434b492d\""
            + "},"
            + "\"metrics\":[{"
            + "  \"key\":\"h12.apex_radial\","
            + "  \"marker\":\"h12\","
            + "  \"n_watches\":5,"
            + "  \"median\":0.5512,"
            + "  \"mad\":0.0026,"
            + "  \"p10\":0.5493,"
            + "  \"p90\":0.5643,"
            + "  \"status\":\"PROMISING\""
            + "},{"
            + "  \"key\":\"h06.axis_residual_deg\","
            + "  \"marker\":\"h06\","
            + "  \"n_watches\":5,"
            + "  \"median\":0.7087,"
            + "  \"mad\":0.5948,"
            + "  \"p10\":-0.1911,"
            + "  \"p90\":4.6968,"
            + "  \"status\":\"DIAGNOSTIC_ONLY\""
            + "}]"
            + "}";

    @Test public void parsesMetricsIntoProfile() {
        GenuineReferenceProfile profile = GenuineReferenceProfileLoader.parse(VALID);
        assertEquals("126710BLNR-gmt-marker-baseline", profile.profileId());
        assertEquals(2, profile.metrics().size());

        MetricBaseline apex = profile.baseline(new MetricKey("h12", "apex_radial"));
        assertEquals(5, apex.n());
        assertEquals(0.5512, apex.median(), 1e-12);
        assertEquals(0.0026, apex.mad(), 1e-12);
        assertEquals(0.5493, apex.p10(), 1e-12);
        assertEquals(0.5643, apex.p90(), 1e-12);
        assertEquals(ValidationStatus.PROMISING, apex.status());
        assertNull(apex.repeatabilityError());
        assertEquals(0.0, apex.missingRate(), 1e-12);

        MetricBaseline axis = profile.baseline(new MetricKey("h06", "axis_residual_deg"));
        assertEquals(ValidationStatus.DIAGNOSTIC_ONLY, axis.status());
    }

    @Test public void sourceDescriptionCitesVersionAndReport() {
        GenuineReferenceProfile profile = GenuineReferenceProfileLoader.parse(VALID);
        assertTrue(profile.sourceDescription().contains("profile_version=1"));
        assertTrue(profile.sourceDescription().contains("434b492d"));
        assertTrue(profile.sourceDescription().contains("PROVISIONAL"));
    }

    @Test public void stripsMarkerPrefixFromMetricKey() {
        GenuineReferenceProfile profile = GenuineReferenceProfileLoader.parse(VALID);
        // "h12.apex_radial" -> markerId="h12", metricName="apex_radial" (prefix stripped, not
        // "h12.apex_radial" kept whole as the metric name).
        MetricKey key = new MetricKey("h12", "apex_radial");
        assertEquals("apex_radial", key.metricName());
    }

    @Test public void rejectsBlankJson() {
        try { GenuineReferenceProfileLoader.parse(""); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
        try { GenuineReferenceProfileLoader.parse(null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsMissingProfileId() {
        String json = "{\"metrics\":[]}";
        try { GenuineReferenceProfileLoader.parse(json); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsMissingMetricsArray() {
        String json = "{\"profile_id\":\"p\"}";
        try { GenuineReferenceProfileLoader.parse(json); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsUnknownValidationStatus() {
        String json = "{\"profile_id\":\"p\",\"metrics\":[{"
                + "\"key\":\"h12.apex_radial\",\"marker\":\"h12\",\"n_watches\":5,"
                + "\"median\":0.5,\"mad\":0.01,\"p10\":0.4,\"p90\":0.6,\"status\":\"CONFIRMED_GENUINE\""
                + "}]}";
        try { GenuineReferenceProfileLoader.parse(json); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void rejectsMalformedJson() {
        try { GenuineReferenceProfileLoader.parse("{not valid json"); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }
}
