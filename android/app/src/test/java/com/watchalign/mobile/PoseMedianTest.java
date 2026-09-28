package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PoseMedianTest {
    private static final GmtHumanQcMath.PoseLabel G=GmtHumanQcMath.PoseLabel.GOOD,C=GmtHumanQcMath.PoseLabel.CORRECTABLE,
            R=GmtHumanQcMath.PoseLabel.RETAKE,U=GmtHumanQcMath.PoseLabel.UNASSESSABLE;

    /** User's ARF Pepsi photo: one borderline "too angled" among "slight angle" ratings. */
    @Test public void loneRetakeIsOutvoted() { assertEquals(C, GmtHumanQcAnalyzerV2.medianPose(new GmtHumanQcMath.PoseLabel[]{R,C,C})); }
    @Test public void solidRetakeStands() { assertEquals(R, GmtHumanQcAnalyzerV2.medianPose(new GmtHumanQcMath.PoseLabel[]{C,R,R})); }
    @Test public void agreementIsKept() { assertEquals(G, GmtHumanQcAnalyzerV2.medianPose(new GmtHumanQcMath.PoseLabel[]{G,G,G})); }
    @Test public void unassessableCopiesAreIgnored() { assertEquals(R, GmtHumanQcAnalyzerV2.medianPose(new GmtHumanQcMath.PoseLabel[]{R,U,R})); }
    @Test public void twoDisagreeingGoesToTheMiddle() { assertEquals(C, GmtHumanQcAnalyzerV2.medianPose(new GmtHumanQcMath.PoseLabel[]{G,R,U})); }
}
