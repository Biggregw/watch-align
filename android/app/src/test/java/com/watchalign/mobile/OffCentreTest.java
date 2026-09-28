package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OffCentreTest {
    private static final GmtHumanQcMath.PoseLabel GOOD = GmtHumanQcMath.PoseLabel.GOOD;

    @Test public void genuineSpreadIsClear() {
        assertEquals(GmtHumanQcMath.Attention.CLEAR, GmtHumanQcMath.assessOffCentre(0.040, 0.035, 0.042, 49.6, GOOD, true));
    }

    /**
     * User photo (ONE seller, date 4): 0.072 on a ~68 px triangle, 2.7 px from the 60 tick. A
     * genuine Phillips 126710BLNR reads 0.080 (4 px), so since alpha61 this is within the
     * genuine spread and is not flagged.
     */
    @Test public void userPhotoIsWithinGenuineSpread() {
        assertEquals(GmtHumanQcMath.Attention.CLEAR, GmtHumanQcMath.assessOffCentre(0.072, 0.068, 0.075, 68, GOOD, true));
        assertEquals(GmtHumanQcMath.Attention.CLEAR, GmtHumanQcMath.assessOffCentre(-0.080, -0.079, -0.081, 82, GOOD, true));
    }

    @Test public void pastTheGenuineSpreadIsChecked() {
        assertEquals(GmtHumanQcMath.Attention.CHECK, GmtHumanQcMath.assessOffCentre(0.11, 0.105, 0.115, 68, GOOD, true));
    }

    @Test public void largeOffsetIsStrong() {
        assertEquals(GmtHumanQcMath.Attention.STRONG, GmtHumanQcMath.assessOffCentre(-0.21, -0.22, -0.19, 40.6, GOOD, true));
    }

    @Test public void inconsistentUnderResizeIsNotFlagged() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.11, 0.02, 0.12, 68, GOOD, true));
    }

    @Test public void subTwoPixelOffsetIsNotFlagged() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.105, 0.102, 0.106, 18, GOOD, true));
    }

    @Test public void lowConfidenceOrAngledPhotoIsNotJudged() {
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.09, 0.09, 0.09, 68, GOOD, false));
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE, GmtHumanQcMath.assessOffCentre(0.09, 0.09, 0.09, 68, GmtHumanQcMath.PoseLabel.RETAKE, true));
    }
}
