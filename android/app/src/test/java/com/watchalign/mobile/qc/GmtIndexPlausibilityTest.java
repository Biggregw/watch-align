package com.watchalign.mobile.qc;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmtIndexPlausibilityTest {
    @Test public void acceptsMarkerInsideExpectedHourSectorAndRing() {
        double a = Math.toRadians(150.0); // 5 o'clock in clock coordinates
        double r = 0.76;
        double x = Math.sin(a) * r;
        double y = -Math.cos(a) * r;
        assertTrue(GmtIndexPlausibility.plausibleCanonicalPosition(5, x, y));
        assertTrue(GmtIndexPlausibility.plausibleAgainstRing(5, x, y, 0.76));
    }

    @Test public void rejectsWrongHourSectorInsteadOfReportingHugeTangentialOffset() {
        double a = Math.toRadians(270.0); // 9 o'clock position mislabeled as 5
        double x = Math.sin(a) * 0.76;
        double y = -Math.cos(a) * 0.76;
        assertFalse(GmtIndexPlausibility.plausibleCanonicalPosition(5, x, y));
    }

    @Test public void rejectsImpossibleRadialPosition() {
        double a = Math.toRadians(150.0);
        double x = Math.sin(a) * 0.20;
        double y = -Math.cos(a) * 0.20;
        assertFalse(GmtIndexPlausibility.plausibleCanonicalPosition(5, x, y));
    }

    @Test public void dateAtThreeIsNeverTreatedAsAppliedMarker() {
        assertFalse(GmtIndexPlausibility.plausibleCanonicalPosition(3, 0.76, 0.0));
    }

    @Test public void bodyRotationRequiresHighConfidenceAndPlausibleMagnitude() {
        assertTrue(GmtIndexPlausibility.bodyRotationUsable(9, 2.0, QcModuleResult.Confidence.HIGH));
        assertFalse(GmtIndexPlausibility.bodyRotationUsable(9, 35.9, QcModuleResult.Confidence.HIGH));
        assertFalse(GmtIndexPlausibility.bodyRotationUsable(9, 2.0, QcModuleResult.Confidence.MEDIUM));
        assertFalse(GmtIndexPlausibility.bodyRotationUsable(12, 2.0, QcModuleResult.Confidence.HIGH));
    }
}
