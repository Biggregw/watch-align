package com.watchalign.mobile.qc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PerspectiveConfidenceServiceTest {
    @Test public void symmetricDialAnchorsAreHighConfidence() {
        PerspectiveConfidenceService.Assessment a = PerspectiveConfidenceService.assess(
                0,-100, 100,0, 0,100, -100,0);
        assertEquals(QcModuleResult.Confidence.HIGH, a.confidence());
        assertTrue(a.score() >= 0.75);
    }

    @Test public void moderatePerspectiveRemainsUsable() {
        PerspectiveConfidenceService.Assessment a = PerspectiveConfidenceService.assess(
                0,-92, 118,5, 8,105, -90,-4);
        assertTrue(a.confidence() == QcModuleResult.Confidence.HIGH
                || a.confidence() == QcModuleResult.Confidence.MEDIUM);
        assertTrue(a.score() >= 0.45);
    }

    @Test public void extremeForeshorteningIsLowConfidence() {
        PerspectiveConfidenceService.Assessment a = PerspectiveConfidenceService.assess(
                0,-20, 120,0, 0,22, -120,0);
        assertEquals(QcModuleResult.Confidence.LOW, a.confidence());
    }

    @Test public void crossedAnchorsAreRejected() {
        PerspectiveConfidenceService.Assessment a = PerspectiveConfidenceService.assess(
                0,-100, 100,0, -100,0, 0,100);
        assertEquals(QcModuleResult.Confidence.LOW, a.confidence());
        assertEquals(0.0, a.score(), 0.0);
    }

    @Test public void nonFiniteAnchorsAreRejected() {
        PerspectiveConfidenceService.Assessment a = PerspectiveConfidenceService.assess(
                Double.NaN,-100, 100,0, 0,100, -100,0);
        assertEquals(QcModuleResult.Confidence.LOW, a.confidence());
    }
}
