package com.watchalign.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Snapshot of the GMT geometry master and QC thresholds. The Submariner research path must not change
 * any of them; a deliberate GMT change has to update this test in the same commit.
 */
public class GmtConstantsSnapshotTest {
    private static final double E = 0.0;

    @Test public void measuredBlnrMasterIsUnchanged() {
        assertEquals("126710BLNR-measured-master-2026-09-26", Gmt126710BlnrMaster.ID);
        assertEquals(1.000, Gmt126710BlnrMaster.DIAL_EDGE_R, E);
        assertEquals(0.925, Gmt126710BlnrMaster.MINUTE_TRACK_R, E);
        assertEquals(0.758, Gmt126710BlnrMaster.MARKER_CENTER_R, E);
        assertEquals(0.816, Gmt126710BlnrMaster.ROUND_CENTER_R, E);
        assertEquals(0.088, Gmt126710BlnrMaster.ROUND_OUTER_R, E);
        assertEquals(0.066, Gmt126710BlnrMaster.ROUND_LUME_R, E);
        assertEquals(0.150, Gmt126710BlnrMaster.BATON_RADIAL_HALF, E);
        assertEquals(0.060, Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF, E);
        assertEquals(0.750, Gmt126710BlnrMaster.TRI_CENTER_R, E);
        assertEquals(0.152, Gmt126710BlnrMaster.TRI_BASE_OUTWARD, E);
        assertEquals(0.150, Gmt126710BlnrMaster.TRI_APEX_INWARD, E);
        assertEquals(0.123, Gmt126710BlnrMaster.TRI_HALF_BASE, E);
    }

    @Test public void gmtVerdictThresholdsAreUnchanged() {
        assertEquals(0.070, GmtHumanQcMath.LOW_CLEARANCE_ATTENTION, E);
        assertEquals(8.0, GmtHumanQcMath.MAX_PLAUSIBLE_ROTATION_DEG, E);
        assertEquals(2.0, GmtHumanQcMath.SKEW_ONLY_MIN_DEG, E);
        assertEquals(0.10, GmtHumanQcMath.OFF_CENTRE_CHECK, E);
        assertEquals(0.15, GmtHumanQcMath.OFF_CENTRE_STRONG, E);
        assertEquals(0.10, GmtHumanQcMath.SIX_CENTRING_CHECK, E);
        assertEquals(0.20, GmtHumanQcMath.SIX_CENTRING_STRONG, E);
        assertEquals(2.0, GmtHumanQcMath.SIX_ROTATION_CHECK_DEG, E);
        assertEquals(3.5, GmtHumanQcMath.SIX_ROTATION_STRONG_DEG, E);
        assertEquals(0.15, GmtHumanQcMath.ROUND_OFFSET_CHECK, E);
        assertEquals(0.25, GmtHumanQcMath.ROUND_OFFSET_STRONG, E);
        assertEquals(0.12, GmtHumanQcMath.ROUND_SIZE_CHECK, E);
        assertEquals(24.0, GmtHumanQcMath.MIN_ROUND_PX, E);
        assertEquals(20.0, GmtHumanQcMath.MIN_BATON_PX, E);
        assertEquals(0.75, GmtHumanQcAnalyzerV2.GAP_PX_UNCERTAINTY, E);
        assertEquals(40.0, GmtHumanQcAnalyzerV2.MIN_TRIANGLE_PX, E);
    }

    @Test public void gmtDetectorPriorsAreUnchanged() {
        assertEquals(44.3, TriangleEdgeRefiner.EXPECTED_APEX_DEG, E);
        assertEquals(2.0, TriangleEdgeRefiner.APEX_TOLERANCE_DEG, E);
        assertEquals(2.5, TriangleEdgeRefiner.SQUARENESS_TOLERANCE_DEG, E);
        assertEquals(1.0, GmtTwelveLandmarkAnalyzer.ROTATION_VISIBLE_DEG, E);
        assertEquals(1.0, GmtTwelveLandmarkAnalyzer.MAX_RESAMPLE_SHIFT_PX, E);
        assertArrayEquals(new double[]{0.94, 0.88}, GmtTwelveLandmarkAnalyzer.STABILITY_SCALES, E);
        assertArrayEquals(new int[]{1, 2, 4, 5, 7, 8, 10, 11}, GmtRoundMarkerAnalyzer.HOURS);
        assertEquals(80.0, GmtRoundMarkerAnalyzer.MIN_EDGE_CONTRAST, E);
        assertEquals(45.0, GmtRoundMarkerAnalyzer.MIN_TICK_SCORE, E);
        assertEquals(0.40, GmtRoundMarkerAnalyzer.MIN_INSET, E);
        assertEquals(0.75, GmtRoundMarkerAnalyzer.MAX_INSET, E);
        assertEquals(0.074, GmtRoundMarkerAnalyzer.MIN_OUTER_R, E);
        assertEquals(8.0, GmtRoundMarkerAnalyzer.MAX_ANGLE_FROM_EXPECTED_DEG, E);
        assertEquals(0.20, GmtSixLandmarkAnalyzer.MIN_LEN_R, E);
        assertEquals(0.40, GmtSixLandmarkAnalyzer.MAX_LEN_R, E);
        assertEquals(0.07, GmtSixLandmarkAnalyzer.MIN_WID_R, E);
        assertEquals(0.18, GmtSixLandmarkAnalyzer.MAX_WID_R, E);
        assertEquals(6, GmtMarkerPose.MIN_MARKERS);
        assertEquals(0.006, GmtMarkerPose.MAX_RESIDUAL, E);
        assertEquals(5.0, GmtMarkerPose.NEAR_FRONTAL_MAX_DEG, E);
        assertEquals(0.12, GmtDialLayout.DATE_MIN_DARK, E);
        assertEquals(0.05, GmtDialLayout.BATON_MAX_DARK, E);
        assertEquals(230.0, GmtDialCrop.MAX_PREVIEW_RADIUS_PX, E);
        assertEquals(380.0, GmtDialCrop.TARGET_RADIUS_PX, E);
        assertEquals(180, DialEdgeEllipseFit.RAYS);
    }
}
