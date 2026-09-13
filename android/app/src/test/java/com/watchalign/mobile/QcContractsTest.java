package com.watchalign.mobile;

import com.watchalign.mobile.qc.QcModuleResult;
import com.watchalign.mobile.qc.RawMeasurement;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class QcContractsTest {
    @Test
    public void resultKeepsRawMeasurementSeparateFromAssessment() {
        RawMeasurement raw = new RawMeasurement("triangle.base_to_60", 0.123, "BW");
        QcModuleResult result = new QcModuleResult(
                "triangle12",
                Collections.singletonList(raw),
                QcModuleResult.Confidence.HIGH,
                Arrays.asList("perspective-rectified", "manual points verified"));

        assertEquals("triangle12", result.moduleId());
        assertEquals(QcModuleResult.Confidence.HIGH, result.confidence());
        assertEquals(0.123, result.measurement("triangle.base_to_60").value(), 0.000001);
        assertEquals("BW", result.measurement("triangle.base_to_60").unit());
        assertNull(result.measurement("missing"));
        assertEquals(2, result.evidence().size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rawMeasurementRejectsNonFiniteValues() {
        new RawMeasurement("bad", Double.NaN, "ratio");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void resultMeasurementsAreImmutable() {
        QcModuleResult result = new QcModuleResult(
                "test",
                Collections.singletonList(new RawMeasurement("x", 1.0, "ratio")),
                QcModuleResult.Confidence.MEDIUM,
                Collections.emptyList());
        result.measurements().add(new RawMeasurement("y", 2.0, "ratio"));
    }
}
