package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.watchalign.mobile.qc.QcModuleResult;
import com.watchalign.mobile.qc.RawMeasurement;

import org.junit.Test;

public class GmtTriangle12QcModuleTest {
    private static PerspectiveMasterRenderer.Pose canonicalPose() {
        PerspectiveMasterRenderer.Pose p = new PerspectiveMasterRenderer.Pose();
        p.anchorMode = true;
        p.perspectiveMode = true;
        p.anchor12X = 0f;
        p.anchor12Y = -100f;
        p.anchor3X = 100f;
        p.anchor3Y = 0f;
        p.anchor6X = 0f;
        p.anchor6Y = 100f;
        p.anchor9X = -100f;
        p.anchor9Y = 0f;
        return p;
    }

    private static double[][] points() {
        return new double[][]{
                {-24, 75}, {24, 75}, {0, 143}, {0, 69}, {0, 155.48}
        };
    }

    @Test public void wrapperPreservesProductionRawMeasurementsExactly() {
        PerspectiveMasterRenderer.Pose pose = canonicalPose();
        double[][] points = points();

        Triangle12RelationalMetric.Result legacy =
                Triangle12RelationalMetric.measureRectifiedRaw(pose, points);
        assertNotNull(legacy);
        assertNotNull(legacy.rawRectified);

        QcModuleResult wrapped = new GmtTriangle12QcModule().measure(
                GmtTriangle12QcModule.Input.rectified(pose, points));

        assertEquals(GmtTriangle12QcModule.ID, wrapped.moduleId());
        // This legacy wrapper has only the four registration anchors. It deliberately cannot claim
        // HIGH after the confidence hardening because it has no independent inner-rehaut evidence.
        assertEquals(QcModuleResult.Confidence.MEDIUM, wrapped.confidence());
        assertEquals(5, wrapped.measurements().size());

        assertSameValue(legacy.rawRectified.baseTo60Ratio,
                wrapped.measurement("base_to_60_over_base"));
        assertSameValue(legacy.rawRectified.apexToCrownRatio,
                wrapped.measurement("apex_to_crown_over_base"));
        assertSameValue(legacy.rawRectified.rotationDeg,
                wrapped.measurement("rotation_deg"));
        assertSameValue(legacy.rawRectified.heightRatio,
                wrapped.measurement("height_over_base"));
        assertSameValue(legacy.rawRectified.lateralPx,
                wrapped.measurement("lateral_px"));
        assertTrue(wrapped.evidence().size() >= 2);
    }

    @Test public void wrapperCopiesInputSoCallerMutationCannotChangeMeasurement() {
        PerspectiveMasterRenderer.Pose pose = canonicalPose();
        double[][] points = points();
        GmtTriangle12QcModule.Input input = GmtTriangle12QcModule.Input.rectified(pose, points);

        points[0][0] = 9999;
        pose.anchor12Y = -9999;

        QcModuleResult wrapped = new GmtTriangle12QcModule().measure(input);
        RawMeasurement base = wrapped.measurement("base_to_60_over_base");
        assertNotNull(base);
        assertEquals(0.125d, base.value(), 0.000001d);
    }

    @Test public void extremePerspectiveLowersConfidenceWithoutChangingRawGeometryPath() {
        PerspectiveMasterRenderer.Pose pose = canonicalPose();
        pose.anchor12Y = -20f;
        pose.anchor6Y = 22f;
        pose.anchor3X = 120f;
        pose.anchor9X = -120f;

        QcModuleResult wrapped = new GmtTriangle12QcModule().measure(
                GmtTriangle12QcModule.Input.rectified(pose, points()));

        assertEquals(QcModuleResult.Confidence.LOW, wrapped.confidence());
        assertEquals(5, wrapped.measurements().size());
        assertNotNull(wrapped.measurement("base_to_60_over_base"));
    }

    @Test public void missingPerspectiveAnchorsCannotClaimHighConfidence() {
        PerspectiveMasterRenderer.Pose pose = canonicalPose();
        pose.perspectiveMode = false;

        QcModuleResult wrapped = new GmtTriangle12QcModule().measure(
                GmtTriangle12QcModule.Input.rectified(pose, points()));

        assertEquals(QcModuleResult.Confidence.LOW, wrapped.confidence());
    }

    private static void assertSameValue(float expected, RawMeasurement actual) {
        assertNotNull(actual);
        assertEquals((double) expected, actual.value(), 0.0d);
    }
}