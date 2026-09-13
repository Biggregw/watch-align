package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.watchalign.mobile.profile.Triangle12Calibration;
import com.watchalign.mobile.profile.Triangle12CalibrationLoader;

import org.junit.Test;

public class Triangle12CalibrationLoaderTest {
    private static final String JSON = "{"
            + "\"schemaVersion\":1,"
            + "\"id\":\"rolex-gmt-126710-triangle12-corrected-pilot-v2\","
            + "\"profileId\":\"rolex.gmt-master-ii.126710-family\","
            + "\"moduleId\":\"gmt.triangle12.relationship\","
            + "\"provenance\":\"image-derived-controls\","
            + "\"factoryTolerance\":false,"
            + "\"genuineControlCount\":4,"
            + "\"baseTo60\":{\"pilotObserved\":[0.114754,0.120690,0.125,0.164],\"pilotMedian\":0.123,\"legacyClassifierMin\":0.10,\"legacyClassifierMax\":0.20},"
            + "\"apexToCrown\":{\"pilotMedian\":0.237,\"legacyClassifierMin\":0.18,\"legacyClassifierMax\":0.34},"
            + "\"rotation\":{\"observedEnvelopeAbsMaxDeg\":0.50},"
            + "\"repeatability\":{\"baseTo60PointPlacementP95\":0.009607},"
            + "\"notes\":[\"image-derived only\"]} ";

    @Test public void parsesCurrentCalibrationWithoutChangingValues() {
        Triangle12Calibration c = Triangle12CalibrationLoader.parse(JSON);
        assertEquals("rolex-gmt-126710-triangle12-corrected-pilot-v2", c.id);
        assertEquals(4, c.genuineControlCount);
        assertFalse(c.factoryTolerance);
        assertEquals(0.114754, c.baseTo60.pilotMin(), 0.0000001);
        assertEquals(0.164000, c.baseTo60.pilotMax(), 0.0000001);
        assertEquals(0.123000, c.baseTo60.pilotMedian, 0.0000001);
        assertEquals(0.100000, c.baseTo60.legacyClassifierMin, 0.0000001);
        assertEquals(0.200000, c.baseTo60.legacyClassifierMax, 0.0000001);
        assertEquals(0.237000, c.apexToCrown.pilotMedian, 0.0000001);
        assertEquals(0.500000, c.rotation.observedEnvelopeAbsMaxDeg, 0.0000001);
        assertEquals(0.009607, c.repeatability.baseTo60PointPlacementP95, 0.0000001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsFactoryToleranceClaim() {
        Triangle12CalibrationLoader.parse(JSON.replace("\"factoryTolerance\":false", "\"factoryTolerance\":true"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsObservationCountMismatch() {
        Triangle12CalibrationLoader.parse(JSON.replace("\"genuineControlCount\":4", "\"genuineControlCount\":5"));
    }
}
