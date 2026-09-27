package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OffCentreTest {
    private static final GmtHumanQcMath.PoseLabel GOOD = GmtHumanQcMath.PoseLabel.GOOD;

    @Test public void genuineSpreadIsClear() {
        assertEquals(GmtHumanQcMath.Attention.CLEAR, GmtHumanQcMath.assessOffCentre(0.040, 0.035, 0.042, 49.6, GOOD, true));
    }

    /** User photo: 0.084 vs 0.156 on a ~68 px triangle. */
    @Test public void userPhotoIsChecked() {
        assertEquals(GmtHumanQcMath.Attention.CHECK, GmtHumanQcMath.assessOffCentre(0.072, 0.068, 0.075, 68, GOOD, true));
    }

    @Test public void largeOffsetIsStrong() {
        assertEquals(GmtHumanQcMath.Attention.STRONG, GmtHumanQcMath.assessOffCentre(-0.21, -0.22, -0.19, 40.6, GOOD, true));
    }

    @Test public void inconsistentUnderResizeIsNotFlagged() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.07, 0.02, 0.08, 68, GOOD, true));
    }

    @Test public void subTwoPixelOffsetIsNotFlagged() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.065, 0.062, 0.066, 30, GOOD, true));
    }

    @Test public void lowConfidenceOrAngledPhotoIsNotJudged() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.09, 0.09, 0.09, 68, GOOD, false));
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.09, 0.09, 0.09, 68, GmtHumanQcMath.PoseLabel.RETAKE, true));
    }
}
