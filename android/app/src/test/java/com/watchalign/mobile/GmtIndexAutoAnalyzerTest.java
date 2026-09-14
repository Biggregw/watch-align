package com.watchalign.mobile;

import static org.junit.Assert.assertTrue;

import com.watchalign.mobile.qc.QcModuleResult;
import com.watchalign.mobile.qc.RawMeasurement;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class GmtIndexAutoAnalyzerTest {
    @Test public void summarySeparatesExactAnglesFromCalibratedDimensions() {
        QcModuleResult result = new QcModuleResult(
                "generic.index_geometry",
                Arrays.asList(
                        new RawMeasurement("index_05_tangential_offset_over_dial_radius", 0.031, "DR"),
                        new RawMeasurement("index_05_radial_offset_over_dial_radius", -0.012, "DR"),
                        new RawMeasurement("index_06_tangential_offset_over_dial_radius", 0.006, "DR"),
                        new RawMeasurement("index_06_radial_offset_over_dial_radius", 0.018, "DR"),
                        new RawMeasurement("index_06_rotation_deg", 1.2, "deg"),
                        new RawMeasurement("index_09_rotation_deg", -0.4, "deg")
                ),
                QcModuleResult.Confidence.HIGH,
                Collections.emptyList());

        String summary = GmtIndexAutoAnalyzer.summarize(result, 10);
        assertTrue(summary.contains("10 QC markers passed strict contour checks"));
        assertTrue(summary.contains("Exact angular model"));
        assertTrue(summary.contains("Largest residual: tangential 5 +0.031 DR"));
        assertTrue(summary.contains("reference radial delta 6 +0.018 DR"));
        assertTrue(summary.contains("6/9 body axis: 6 +1.20° · 9 -0.40°"));
        assertTrue(summary.contains("Image-calibrated centres: round"));
        assertTrue(summary.contains("6/9 baton"));
        assertTrue(summary.contains("Cyan axes are mathematical"));
        assertTrue(summary.contains("12 outer-metal triangle is measured automatically"));
    }
}
