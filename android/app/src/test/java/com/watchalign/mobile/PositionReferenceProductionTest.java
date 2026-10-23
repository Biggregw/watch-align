package com.watchalign.mobile;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

/** Dedicated production decision regressions; unchanged original detection and tolerances. */
public class PositionReferenceProductionTest {
    private static final String[] FAMILIES = {"gmt_126710", "submariner_124060", "submariner_126610"};

    private static ModelSpec model(String family) throws Exception {
        return ModelSpec.load(TestModels.files(), family);
    }

    private static void promote(ClearlyOff.Result result, ModelSpec model) {
        Map<Integer, PositionReferenceFit.Reading> shadow = PositionReferenceShadow.measure(result, model, null, null);
        PositionReferenceProduction.apply(result, shadow, null);
    }

    @Test public void originalFalseRadialDoesNotBecomeReplicaQcFailure() throws Exception {
        for(String family : FAMILIES) {
            ModelSpec spec = model(family);
            ClearlyOff.Result r = PositionReferenceShadowTest.measured(spec);
            ClearlyOff.Marker six = r.at(6);
            six.inoutPct = 2.0; // legacy ring relative radial false positive
            ClearlyOff.decide(six, r.rpx, null);
            assertTrue(six.posOff);
            promote(r, spec);
            assertTrue(six.relativePositionUsed);
            assertEquals(0.0, six.relativePositionPct, 1e-8);
            assertEquals(0.0, six.posPct, 1e-8);
            assertFalse(six.posOff);
            assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE, six.state);
            // Strict-genuine standard is still calculated with the ORIGINAL values.
            ClearlyOff.Marker strict = WatchClassification.strict(six, r.rpx);
            assertTrue(strict.posOff);
            assertEquals(2.0, strict.posPct, 1e-8);
            assertEquals(2.0, six.inoutPct, 0);
        }
    }

    @Test public void relativeDefectIsFlaggedWithoutFalselyFlaggingNeighbours() throws Exception {
        for(String family : FAMILIES) {
            ModelSpec spec = model(family);
            ClearlyOff.Result r = PositionReferenceShadowTest.measured(spec);
            ClearlyOff.Marker six = r.at(6);
            six.cObs[1] += 0.014; // position displacement inward or outward, 1.4% of dial radius
            // Existing metric artificially missed this, to verify new evidence can identify it.
            assertFalse(six.posOff);
            promote(r, spec);
            assertTrue(six.relativePositionUsed);
            assertEquals(1.4, six.relativePositionPct, 1e-7);
            assertTrue(six.posOff);
            assertEquals(ClearlyOff.State.CLEARLY_OFF, six.state);
            for(ClearlyOff.Marker m : r.markers) if(m.hour != 6 && m.shape != ModelSpec.Shape.TRIANGLE)
                assertEquals("untouched neighbour " + family + " " + m.hour,
                        ClearlyOff.State.NO_VISIBLE_ISSUE, m.state);
        }
    }

    @Test public void absoluteMinuteTrackCannotBeClearedByWholeRingFit() throws Exception {
        ModelSpec spec = model("submariner_124060");
        ClearlyOff.Result r = PositionReferenceShadowTest.measured(spec);
        for(ClearlyOff.Marker m : r.markers) {
            m.cObs[0] += .02;
            m.cObs[1] -= .01;
            m.sidePct = 2;
            ClearlyOff.decide(m, r.rpx, null);
        }
        promote(r, spec);
        for(ClearlyOff.Marker m : r.markers) if(m.shape != ModelSpec.Shape.TRIANGLE) {
            assertTrue(m.relativePositionUsed);
            assertEquals(0, m.relativePositionPct, 1e-8);
            assertEquals(2.0, m.posPct, 1e-8);
            assertTrue(m.posOff);
            assertEquals(ClearlyOff.State.CLEARLY_OFF, m.state);
        }
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,
                WatchClassification.classify(r, spec, null).verdict);
    }

    @Test public void withheldAndInsufficientContributorsUseOriginalPosition() throws Exception {
        ModelSpec spec = model("submariner_126610");
        ClearlyOff.Result r = PositionReferenceShadowTest.measured(spec);
        for(int hour : new int[]{1, 2, 4, 5}) {
            ClearlyOff.Marker m = r.at(hour);
            m.gate.add("hand");
            ClearlyOff.decide(m, r.rpx, null);
        }
        ClearlyOff.Marker six = r.at(6);
        six.inoutPct = 2;
        ClearlyOff.decide(six, r.rpx, null);
        ClearlyOff.State old = six.state;
        promote(r, spec);
        assertFalse(six.relativePositionUsed);
        assertEquals(old, six.state);
        assertEquals(2.0, six.posPct, 1e-8);
        assertEquals(ClearlyOff.State.CANT_CHECK, r.at(1).state);
    }

    @Test public void unchangedTriangleRotationAndPhotoWideFailure() throws Exception {
        ModelSpec spec = model("submariner_124060");
        ClearlyOff.Result r = PositionReferenceShadowTest.measured(spec);
        ClearlyOff.Marker triangle = r.at(12);
        triangle.tiltDeg = 2.5;
        ClearlyOff.decide(triangle, r.rpx, null);
        ClearlyOff.State original = triangle.state;
        promote(r, spec);
        assertEquals(original, triangle.state);
        assertFalse(triangle.relativePositionUsed);
        assertEquals(2.5, triangle.tiltDeg, 0.0);
        r = PositionReferenceShadowTest.measured(spec);
        ClearlyOff.Marker six = r.at(6);
        six.inoutPct = 2;
        ClearlyOff.decide(six, r.rpx, null);
        PositionReferenceProduction.apply(r, PositionReferenceShadow.measure(r, spec, null, null), "pose unavailable");
        assertFalse(six.relativePositionUsed);
        assertTrue(six.posOff);
    }
}
