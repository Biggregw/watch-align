package com.watchalign.mobile;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain-Java regression suite: no Android SDK, JUnit, network or CI required. */
public final class PositionReferenceFitTest {
    private static int assertions = 0;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void near(double actual, double wanted, double tolerance, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - wanted) <= tolerance,
                message + ": actual=" + actual + " expected=" + wanted + " tol=" + tolerance);
    }

    private static PositionReferenceFit.MarkerCentre make(int hour, double dx, double dy, boolean assessable) {
        double angle = Math.toRadians((hour % 12) * 30.0);
        double x = 0.80 * Math.sin(angle), y = -0.80 * Math.cos(angle);
        PositionReferenceFit.Shape shape = hour == 12 ? PositionReferenceFit.Shape.TRIANGLE
                : hour % 3 == 0 ? PositionReferenceFit.Shape.BATON : PositionReferenceFit.Shape.ROUND;
        return new PositionReferenceFit.MarkerCentre(hour, shape, x, y, x + dx, y + dy, assessable);
    }

    private static List<PositionReferenceFit.MarkerCentre> clean() {
        List<PositionReferenceFit.MarkerCentre> xs = new ArrayList<>();
        for (int h = 1; h <= 12; h++) xs.add(make(h, 0, 0, true));
        return xs;
    }

    private static void syntheticChecks() {
        Map<Integer, PositionReferenceFit.Reading> baseline = PositionReferenceFit.measureAll(350, clean());
        check(baseline.size() == 12, "all supplied hours returned");
        for (int h = 1; h <= 11; h++) {
            near(baseline.get(h).distancePctR(), 0.0, 1e-10, "identical geometry " + h);
            check(baseline.get(h).otherContributors == 10, "leave-one-out excludes self " + h);
        }
        check(!baseline.get(12).available && baseline.get(12).reason.contains("top-edge"), "triangle not judged");

        List<PositionReferenceFit.MarkerCentre> moved = clean();
        moved.set(8, make(9, 0, -0.005, true));
        Map<Integer, PositionReferenceFit.Reading> shifted = PositionReferenceFit.measureAll(350, moved);
        near(shifted.get(9).tangentialPctR, 0.5, 1e-7, "known sideways 0.5% R measured");
        near(shifted.get(9).radialPctR, 0.0, 1e-7, "known sideways no radial spill");
        for (int h = 1; h <= 11; h++) {
            if (h != 9) check(shifted.get(h).distancePctR() < 0.15,
                    "single outlier does not create huge neighbour reading at " + h);
        }

        moved = clean();
        moved.set(5, make(6, 0, +0.005, true));
        shifted = PositionReferenceFit.measureAll(350, moved);
        // 6 is at bottom (positive Y), so positive Y is radially outward.
        near(shifted.get(6).radialPctR, 0.5, 1e-7, "known outward 0.5% R measured");
        near(shifted.get(6).tangentialPctR, 0.0, 1e-7, "known outward no sideways spill");

        // A shared ring move is removed by definition. It is never safe to replace absolute
        // placement or hour-line information with this reading.
        moved = new ArrayList<>();
        for (PositionReferenceFit.MarkerCentre m : clean()) {
            moved.add(new PositionReferenceFit.MarkerCentre(m.hour, m.shape,
                    m.fixedX, m.fixedY, m.observedX + 0.004, m.observedY - 0.003, true));
        }
        shifted = PositionReferenceFit.measureAll(350, moved);
        for (int h = 1; h <= 11; h++) {
            near(shifted.get(h).distancePctR(), 0.0, 1e-8, "known shared translation is invisible " + h);
        }

        // Whole-ring rotation and scaling also disappear from the individual residual.
        // That is a designed limitation, so never replace production's absolute check.
        moved = new ArrayList<>();
        final double scale = 0.004, turn = Math.toRadians(0.4);
        for (PositionReferenceFit.MarkerCentre m : clean()) {
            double newX = m.fixedX + scale*m.fixedX - turn*m.fixedY;
            double newY = m.fixedY + scale*m.fixedY + turn*m.fixedX;
            moved.add(new PositionReferenceFit.MarkerCentre(m.hour, m.shape,
                    m.fixedX, m.fixedY, newX, newY, true));
        }
        shifted = PositionReferenceFit.measureAll(350, moved);
        for (int h = 1; h <= 11; h++) {
            near(shifted.get(h).distancePctR(), 0.0, 1e-8,
                    "shared rotation/scale is invisible " + h);
        }

        // Contributing eligibility comes ONLY from the existing app; shadow result withheld
        // for its own unassessable markers.
        moved = clean();
        moved.set(2, make(3, 0, 0, false));
        shifted = PositionReferenceFit.measureAll(350, moved);
        check(!shifted.get(3).available, "old-gated target stays unavailable");
        for (int h = 1; h <= 11; h++) if (h != 3) {
            check(shifted.get(h).available && shifted.get(h).otherContributors == 9,
                    "original contributors reused after gate " + h);
        }

        moved = clean();
        for (int i = 3; i <= 11; i++) moved.set(i - 1, make(i, 0, 0, false));
        shifted = PositionReferenceFit.measureAll(350, moved);
        check(!shifted.get(1).available && shifted.get(1).reason.contains("insufficient"),
                "fails unavailable when ring underspecified");

        // Geometrically singular reference: unavailable is not a false pass.
        moved = new ArrayList<>();
        for (int h = 1; h <= 7; h++) {
            moved.add(new PositionReferenceFit.MarkerCentre(h, PositionReferenceFit.Shape.ROUND,
                    0.2, 0.1, 0.2, 0.1, true));
        }
        shifted = PositionReferenceFit.measureAll(350, moved);
        for (int h = 1; h <= 7; h++) {
            check(!shifted.get(h).available && shifted.get(h).reason.contains("indeterminate"),
                    "singular ring fit must be unavailable at " + h);
        }

        try {
            PositionReferenceFit.measureAll(350, Arrays.asList(make(1, 0, 0, true), make(1, 0, 0, true)));
            throw new AssertionError("duplicate hour incorrectly accepted");
        } catch (IllegalArgumentException expected) { assertions++; }
        try {
            PositionReferenceFit.measureAll(Double.NaN, clean());
            throw new AssertionError("invalid radius incorrectly accepted");
        } catch (IllegalArgumentException expected) { assertions++; }
        try {
            PositionReferenceFit.measureAll(350, null);
            throw new AssertionError("null list incorrectly accepted");
        } catch (IllegalArgumentException expected) { assertions++; }
        try {
            shifted.clear();
            throw new AssertionError("result unexpectedly mutable");
        } catch (UnsupportedOperationException expected) { assertions++; }

        List<PositionReferenceFit.MarkerCentre> backward = clean();
        Collections.reverse(backward);
        Map<Integer, PositionReferenceFit.Reading> reversed = PositionReferenceFit.measureAll(350, backward);
        for (int h = 1; h <= 11; h++) {
            near(reversed.get(h).tangentialPctR, baseline.get(h).tangentialPctR, 1e-8,
                    "iteration-order invariance " + h);
        }
    }

    private static int runVectors(String filename) throws Exception {
        int cases = 0;
        String previousCase = null;
        List<PositionReferenceFit.MarkerCentre> group = new ArrayList<>();
        Map<Integer, double[]> expected = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            check(line != null && line.startsWith("case_id,"), "vector header");
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                String[] c = line.split(",", -1);
                if (c.length != 12) throw new AssertionError("CSV column count=" + c.length + ": " + line);
                String caseId = c[0];
                double rpx = Double.parseDouble(c[1]);
                if (previousCase != null && !previousCase.equals(caseId)) {
                    verifyGroup(previousCase, group, expected);
                    cases++;
                    group = new ArrayList<>();
                    expected = new LinkedHashMap<>();
                }
                previousCase = caseId;
                int h = Integer.parseInt(c[2]);
                PositionReferenceFit.Shape shape = PositionReferenceFit.Shape.valueOf(c[3]);
                boolean assessable = Boolean.parseBoolean(c[4]);
                group.add(new PositionReferenceFit.MarkerCentre(h, shape,
                        Double.parseDouble(c[5]), Double.parseDouble(c[6]),
                        Double.parseDouble(c[7]), Double.parseDouble(c[8]), assessable));
                double side = c[9].isEmpty() ? Double.NaN : Double.parseDouble(c[9]);
                double radial = c[10].isEmpty() ? Double.NaN : Double.parseDouble(c[10]);
                expected.put(h, new double[] {side, radial, Double.parseDouble(c[11]), rpx});
            }
        }
        if (previousCase != null) { verifyGroup(previousCase, group, expected); cases++; }
        return cases;
    }

    private static void verifyGroup(String name, List<PositionReferenceFit.MarkerCentre> group,
                                    Map<Integer, double[]> expected) {
        double rpx = expected.values().iterator().next()[3];
        Map<Integer, PositionReferenceFit.Reading> actual = PositionReferenceFit.measureAll(rpx, group);
        check(actual.size() == group.size(), name + " retained all markers");
        for (Map.Entry<Integer, double[]> e : expected.entrySet()) {
            int hour = e.getKey();
            double[] q = e.getValue();
            PositionReferenceFit.Reading got = actual.get(hour);
            check(got != null, name + " returned hour " + hour);
            check(got.otherContributors == (int)q[2] || !got.available,
                    name + " contributor count " + hour);
            if (Double.isNaN(q[0])) {
                check(!got.available, name + " should be unavailable at " + hour);
            } else {
                check(got.available, name + " expected available at " + hour + " (" + got.reason + ")");
                near(got.tangentialPctR, q[0], 3e-8, name + " tangential at " + hour);
                near(got.radialPctR, q[1], 3e-8, name + " radial at " + hour);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        syntheticChecks();
        String filename = args.length > 0 ? args[0] : "fixtures/genuine-and-synthetic-vectors.csv";
        int cases = runVectors(filename);
        int parityCases = runVectors("fixtures/archived-phase-b-parity.csv");
        System.out.println("PASS PositionReferenceFitTest: " + assertions + " assertions, " + cases
                + " offline-Python vectors and " + parityCases + " archived Java parity vectors, no Android dependencies");
    }
}
