package com.watchalign.mobile;

import org.junit.Test;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class PositionReferenceShadowTest {
    static ModelSpec model(String id) throws Exception { return ModelSpec.load(TestModels.files(), id); }

    static ClearlyOff.Result measured(ModelSpec model) {
        ClearlyOff.Result r = new ClearlyOff.Result();
        r.rpx = 400;
        for (ModelSpec.Marker spec : model.markers) {
            ClearlyOff.Marker m = new ClearlyOff.Marker(spec.hour, spec.shape);
            m.yellow = ExpectedMarkerOutline.polygon(spec, null, null);
            m.cObs = ClearlyOff.mean(m.yellow);
            // Deliberately adjusted: using cGen instead of fixed model must fail our zero test.
            m.cGen = new double[]{m.cObs[0] + .02, m.cObs[1] - .01};
            m.source = "outer metal";
            m.sidePct = m.inoutPct = m.tiltDeg = 0;
            m.gapPct = 3;
            m.fitPoly = m.yellow;
            m.fitPts = m.yellow;
            m.fitStatus = new byte[m.yellow.length];
            m.cover = 1;
            r.lines.put(spec.hour % 12, ClearlyOff.hourAngle(spec.hour));
            ClearlyOff.decide(m, r.rpx, null);
            r.markers.add(m);
        }
        return r;
    }

    /** Deep snapshot of ALL instance fields, including arrays, gates, top edge and overlay inputs. */
    static String snapshot(Object value) throws Exception {
        if (value == null) return "null";
        if (value instanceof Double) return Long.toHexString(Double.doubleToRawLongBits((Double)value));
        if (value instanceof Number || value instanceof Boolean || value instanceof String || value instanceof Enum)
            return value.getClass().getName() + ":" + value;
        StringBuilder s = new StringBuilder();
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) s.append(snapshot(Array.get(value, i))).append(';');
        } else if (value instanceof Iterable) {
            for (Object item : (Iterable<?>)value) s.append(snapshot(item)).append(';');
        } else if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>)value).entrySet())
                s.append(snapshot(entry.getKey())).append('=').append(snapshot(entry.getValue())).append(';');
        } else {
            for (Field f : value.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                f.setAccessible(true);
                s.append(f.getName()).append('=').append(snapshot(f.get(value))).append(';');
            }
        }
        return s.toString();
    }

    static Map<Integer, PositionReferenceFit.Reading> unchanged(ClearlyOff.Result r, ModelSpec model) throws Exception {
        List<AlignmentDecision.Marker> decisions = new ArrayList<>();
        for (ModelSpec.Marker m : model.markers) decisions.add(new AlignmentDecision.Marker(m.hour, m.shape));
        AlignmentDecision.applyClearlyOff(decisions, r, null);
        String before = snapshot(r), alignment = snapshot(decisions);
        String classification = snapshot(WatchClassification.classify(r, model, null));
        String technical = ClearlyOff.technical(r);
        Map<Integer, PositionReferenceFit.Reading> shadow = PositionReferenceShadow.measure(r, model, null, null);
        assertEquals(before, snapshot(r));
        assertEquals(alignment, snapshot(decisions));
        assertEquals(classification, snapshot(WatchClassification.classify(r, model, null)));
        assertEquals(technical, ClearlyOff.technical(r));
        return shadow;
    }

    @Test public void fixedGeometryAndKnownMovesForAllProductionModels() throws Exception {
        for (String id : new String[]{"gmt_126710", "submariner_124060", "submariner_126610"}) {
            ModelSpec model = model(id);
            ClearlyOff.Result r = measured(model);
            Map<Integer, PositionReferenceFit.Reading> zero = unchanged(r, model);
            for (PositionReferenceFit.Reading reading : zero.values()) {
                if (reading.hour == 12) assertFalse(reading.available);
                else {
                    assertTrue(reading.available);
                    assertEquals(0, reading.distancePctR(), 1e-9);
                    assertEquals(model.markers.size() - 2, reading.otherContributors);
                }
            }
            for (boolean radial : new boolean[]{true, false}) {
                r = measured(model);
                ClearlyOff.Marker moved = r.at(6);
                moved.cObs[radial ? 1 : 0] += radial ? .005 : -.005;
                moved.inoutPct = radial ? .5 : 0;
                moved.sidePct = radial ? 0 : .5;
                moved.tiltDeg = 2.3; // Existing rotation defect must survive the position-only helper.
                ClearlyOff.decide(moved, r.rpx, null);
                Map<Integer, PositionReferenceFit.Reading> out = unchanged(r, model);
                assertEquals(.5, radial ? out.get(6).radialPctR : out.get(6).tangentialPctR, 1e-8);
                for (PositionReferenceFit.Reading reading : out.values())
                    if (reading.available && reading.hour != 6) assertEquals(0, reading.distancePctR(), 1e-7);
            }
        }
    }

    @Test public void existingGatesAndSoftWarningsAreReused() throws Exception {
        ModelSpec model = model("submariner_124060");
        ClearlyOff.Result r = measured(model);
        ClearlyOff.Marker soft = r.at(6);
        soft.cover = .55; soft.uncertain = "its visible edge is rough"; soft.handConfirmed = true;
        soft.sidePct = 2; ClearlyOff.decide(soft, r.rpx, null);
        r.at(1).gate.add("hand"); ClearlyOff.decide(r.at(1), r.rpx, null);
        r.at(2).gate.add("minute ticks near it hidden"); ClearlyOff.decide(r.at(2), r.rpx, null);
        r.at(4).cObs = null;
        r.at(5).cObs[0] = Double.NaN;
        Map<Integer, PositionReferenceFit.Reading> out = unchanged(r, model);
        assertTrue(WatchClassification.credible(soft));
        assertTrue(out.get(6).available); // No research 90% coverage/hand/uncertainty gate.
        assertFalse(out.get(1).available); assertFalse(out.get(2).available);
        assertFalse(out.containsKey(4)); assertFalse(out.containsKey(5));
        assertEquals(6, out.get(6).otherContributors);
        for (int h : new int[]{7, 8, 9}) { r.at(h).gate.add("obstructed"); ClearlyOff.decide(r.at(h), r.rpx, null); }
        out = unchanged(r, model);
        assertFalse(out.get(6).available);
        assertTrue(out.get(6).reason.contains("insufficient"));
        assertEquals(ClearlyOff.State.POSSIBLE, soft.state);
    }

    @Test public void missingInputsAndHelperFailureAreIsolated() throws Exception {
        ModelSpec model = model("submariner_124060");
        ClearlyOff.Result r = measured(model);
        assertTrue(PositionReferenceShadow.measure(null, model, null, null).isEmpty());
        assertTrue(PositionReferenceShadow.measure(r, null, null, null).isEmpty());
        assertTrue(PositionReferenceShadow.measure(r, model, null, "pose failed").isEmpty());
        r.photoReason = "photo failed"; assertTrue(unchanged(r, model).isEmpty()); r.photoReason = null;
        r.rpx = Double.NaN; assertTrue(unchanged(r, model).isEmpty()); r.rpx = 400;
        r.markers.add(r.at(1)); // Duplicate hour makes the supplied helper throw.
        assertTrue(unchanged(r, model).isEmpty());
        r = measured(model); r.at(1).source = "lume";
        assertFalse(unchanged(r, model).containsKey(1));
    }

    @Test public void wholeRingMotionNeverReplacesAbsoluteEvidence() throws Exception {
        ModelSpec model = model("submariner_124060");
        ClearlyOff.Result r = measured(model);
        for (ClearlyOff.Marker m : r.markers) {
            m.cObs[0] += .02; m.cObs[1] -= .01;
            m.sidePct = 2; ClearlyOff.decide(m, r.rpx, null);
        }
        for (PositionReferenceFit.Reading reading : unchanged(r, model).values())
            if (reading.available) assertEquals(0, reading.distancePctR(), 1e-8);
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,
                WatchClassification.classify(r, model, null).verdict);
    }

    @Test public void approvedTriangleTopEdgeIsUntouched() throws Exception {
        ModelSpec model = model("submariner_124060");
        ClearlyOff.Result r = measured(model);
        r.at(12).topEdge = TriangleTopEdgeTest.measure(TriangleTopEdgeTest.dial(.005, 0, 0));
        ClearlyOff.useCalibratedTopEdge(r.at(12), r.rpx);
        ClearlyOff.decide(r.at(12), r.rpx, null);
        assertTrue(r.at(12).radialFromTopEdge);
        assertFalse(unchanged(r, model).get(12).available);
    }
}
