package com.watchalign.mobile.qc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class IndexGeometryQcModuleTest {
    private static RectificationConfidenceService.Assessment highPerspective() {
        return RectificationConfidenceService.assess(
                0,-100, 100,0, 0,100, -100,0);
    }

    @Test public void perfectCardinalMarkersProduceZeroOffsetsAndRotation() {
        IndexGeometryQcModule.MarkerObservation m12 =
                IndexGeometryQcModule.MarkerObservation.of(
                        12, 0,-80, 0,-70, 0,-90, 0.80);
        IndexGeometryQcModule.MarkerObservation m3 =
                IndexGeometryQcModule.MarkerObservation.of(
                        3, 80,0, 70,0, 90,0, 0.80);

        QcModuleResult result = new IndexGeometryQcModule().measure(
                IndexGeometryQcModule.Input.rectified(
                        0,0,100, Arrays.asList(m12,m3), highPerspective()));

        assertEquals(IndexGeometryQcModule.ID, result.moduleId());
        assertEquals(QcModuleResult.Confidence.HIGH, result.confidence());
        assertValue(result, "index_12_center_radius_over_dial_radius", 0.80);
        assertValue(result, "index_12_radial_offset_over_dial_radius", 0.0);
        assertValue(result, "index_12_tangential_offset_over_dial_radius", 0.0);
        assertValue(result, "index_12_rotation_deg", 0.0);
        assertValue(result, "index_03_center_radius_over_dial_radius", 0.80);
        assertValue(result, "index_03_radial_offset_over_dial_radius", 0.0);
        assertValue(result, "index_03_tangential_offset_over_dial_radius", 0.0);
        assertValue(result, "index_03_rotation_deg", 0.0);
    }

    @Test public void detectsClockwiseCantAndTangentialShiftIndependently() {
        double expectedAngle = Math.toRadians(60.0); // 5 o'clock
        double radialX = Math.cos(expectedAngle);
        double radialY = Math.sin(expectedAngle);
        double tangentX = -radialY;
        double tangentY = radialX;

        // Expected centre radius 80 px, shifted 3 px clockwise/tangentially.
        double cx = radialX * 80.0 + tangentX * 3.0;
        double cy = radialY * 80.0 + tangentY * 3.0;

        double observedAngle = Math.toRadians(61.2); // +1.2 degrees clockwise on screen
        double axisX = Math.cos(observedAngle) * 10.0;
        double axisY = Math.sin(observedAngle) * 10.0;

        IndexGeometryQcModule.MarkerObservation m5 =
                IndexGeometryQcModule.MarkerObservation.of(
                        5,
                        cx, cy,
                        cx - axisX, cy - axisY,
                        cx + axisX, cy + axisY,
                        0.80);

        QcModuleResult result = new IndexGeometryQcModule().measure(
                IndexGeometryQcModule.Input.rectified(
                        0,0,100, Collections.singletonList(m5), highPerspective()));

        assertValue(result, "index_05_radial_offset_over_dial_radius", 0.0, 1e-9);
        assertValue(result, "index_05_tangential_offset_over_dial_radius", 0.03, 1e-9);
        assertValue(result, "index_05_rotation_deg", 1.2, 1e-9);
    }

    @Test public void axisDirectionDoesNotChangeTiltResult() {
        IndexGeometryQcModule.MarkerObservation reversed =
                IndexGeometryQcModule.MarkerObservation.of(
                        12, 0,-80, 0,-90, 0,-70, 0.80);
        QcModuleResult result = new IndexGeometryQcModule().measure(
                IndexGeometryQcModule.Input.rectified(
                        0,0,100, Collections.singletonList(reversed), highPerspective()));
        assertValue(result, "index_12_rotation_deg", 0.0);
    }

    @Test public void optionalSizeIsNormalizedByDialRadius() {
        IndexGeometryQcModule.MarkerObservation m6 =
                IndexGeometryQcModule.MarkerObservation.ofWithSize(
                        6, 0,80, 0,70, 0,90, 0.80, 8,20);
        QcModuleResult result = new IndexGeometryQcModule().measure(
                IndexGeometryQcModule.Input.rectified(
                        0,0,100, Collections.singletonList(m6), highPerspective()));
        assertValue(result, "index_06_width_over_dial_radius", 0.08);
        assertValue(result, "index_06_height_over_dial_radius", 0.20);
    }

    @Test public void perspectiveConfidencePropagatesWithoutChangingRawGeometry() {
        RectificationConfidenceService.Assessment low = RectificationConfidenceService.assess(
                0,-20, 120,0, 0,22, -120,0);
        assertEquals(QcModuleResult.Confidence.LOW, low.confidence());

        IndexGeometryQcModule.MarkerObservation m12 =
                IndexGeometryQcModule.MarkerObservation.of(
                        12, 0,-80, 0,-70, 0,-90, 0.80);
        QcModuleResult result = new IndexGeometryQcModule().measure(
                IndexGeometryQcModule.Input.rectified(
                        0,0,100, Collections.singletonList(m12), low));

        assertEquals(QcModuleResult.Confidence.LOW, result.confidence());
        assertValue(result, "index_12_rotation_deg", 0.0);
        assertValue(result, "index_12_radial_offset_over_dial_radius", 0.0);
        assertTrue(result.evidence().size() >= 2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void duplicateHoursAreRejected() {
        IndexGeometryQcModule.MarkerObservation a =
                IndexGeometryQcModule.MarkerObservation.of(
                        1, 40,-69.282, 35,-60, 45,-78, 0.80);
        IndexGeometryQcModule.MarkerObservation b =
                IndexGeometryQcModule.MarkerObservation.of(
                        1, 41,-69, 36,-60, 46,-78, 0.80);
        IndexGeometryQcModule.Input.rectified(
                0,0,100, Arrays.asList(a,b), highPerspective());
    }

    @Test public void hourAxisAnglesFollowClockFaceGeometry() {
        assertEquals(-90.0, IndexGeometryQcModule.expectedAxisAngleDeg(12), 0.0);
        assertEquals(0.0, IndexGeometryQcModule.expectedAxisAngleDeg(3), 0.0);
        assertEquals(90.0, IndexGeometryQcModule.expectedAxisAngleDeg(6), 0.0);
        assertEquals(180.0, IndexGeometryQcModule.expectedAxisAngleDeg(9), 0.0);
    }

    private static void assertValue(QcModuleResult result, String id, double expected) {
        assertValue(result, id, expected, 1e-12);
    }

    private static void assertValue(QcModuleResult result, String id, double expected, double tolerance) {
        RawMeasurement m = result.measurement(id);
        assertNotNull(id, m);
        assertEquals(expected, m.value(), tolerance);
    }
}
