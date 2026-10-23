package com.watchalign.mobile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import static org.junit.Assert.*;

/** Desktop-only checks using the existing harness shims; no Android SDK or photographs. */
public final class ShadowDesktopCheck {
    private static int vectors(Path path) throws Exception {
        String previous = null;
        List<String[]> group = new ArrayList<>();
        int cases = 0;
        List<String> lines = Files.readAllLines(path);
        for (String line : lines.subList(1, lines.size())) {
            String[] row = line.split(",", -1);
            if (previous != null && !previous.equals(row[0])) { verify(group); cases++; group.clear(); }
            previous = row[0]; group.add(row);
        }
        if (!group.isEmpty()) { verify(group); cases++; }
        return cases;
    }

    private static void verify(List<String[]> rows) throws Exception {
        String id = rows.get(0)[0].split("_(genuine|synthetic)_")[0];
        ModelSpec model = PositionReferenceShadowTest.model(id);
        ClearlyOff.Result r = PositionReferenceShadowTest.measured(model);
        r.markers.clear();
        r.rpx = Double.parseDouble(rows.get(0)[1]);
        for (String[] row : rows) {
            int hour = Integer.parseInt(row[2]);
            ClearlyOff.Marker m = new ClearlyOff.Marker(hour, ModelSpec.Shape.valueOf(row[3]));
            m.source = "outer metal";
            m.cObs = new double[]{Double.parseDouble(row[7]), Double.parseDouble(row[8])};
            double[] fixed = ClearlyOff.mean(ExpectedMarkerOutline.polygon(model.atHour(hour), null, null));
            // Triangle nominal is irrelevant: it never contributes or gets a shadow reading.
            if (hour != 12) {
                assertEquals(row[0], Double.parseDouble(row[5]), fixed[0], 1e-12);
                assertEquals(row[0], Double.parseDouble(row[6]), fixed[1], 1e-12);
            }
            m.sidePct = m.inoutPct = m.tiltDeg = 0;
            m.gapPct = 3;
            if (!Boolean.parseBoolean(row[4])) m.gate.add("obstructed");
            ClearlyOff.decide(m, r.rpx, null);
            r.markers.add(m);
        }
        Map<Integer, PositionReferenceFit.Reading> out = PositionReferenceShadowTest.unchanged(r, model);
        for (String[] row : rows) {
            PositionReferenceFit.Reading got = out.get(Integer.parseInt(row[2]));
            assertNotNull(got);
            if (row[9].isEmpty()) assertFalse(got.available);
            else {
                assertTrue(got.reason, got.available);
                assertEquals(Double.parseDouble(row[9]), got.tangentialPctR, 3e-8);
                assertEquals(Double.parseDouble(row[10]), got.radialPctR, 3e-8);
                assertEquals(Integer.parseInt(row[11]), got.otherContributors);
            }
        }
    }

    private static byte[] evidence(Mat image, ModelSpec model, ClearlyOff.Result r,
                                   AlignmentDecision.Marker marker) {
        Mat rendered = ClearlyOffEvidence.renderMat(image, MetalFitTest.H, model, r, marker);
        try {
            byte[] pixels = new byte[(int)(rendered.total() * rendered.channels())];
            rendered.get(0, 0, pixels);
            return pixels;
        } finally { rendered.release(); }
    }

    public static void main(String[] args) throws Exception {
        int cases = vectors(Path.of("fixtures/genuine-and-synthetic-vectors.csv"))
                + vectors(Path.of("fixtures/archived-phase-b-parity.csv"));
        nu.pattern.OpenCV.loadLocally();
        ModelSpec model = PositionReferenceShadowTest.model("submariner_124060");
        ClearlyOff.Result r = PositionReferenceShadowTest.measured(model);
        r.at(12).topEdge = TriangleTopEdgeTest.measure(TriangleTopEdgeTest.dial(.005, 0, 0));
        ClearlyOff.useCalibratedTopEdge(r.at(12), r.rpx);
        ClearlyOff.decide(r.at(12), r.rpx, null);
        List<AlignmentDecision.Marker> decisions = new ArrayList<>();
        for (ModelSpec.Marker m : model.markers) decisions.add(new AlignmentDecision.Marker(m.hour, m.shape));
        AlignmentDecision.applyClearlyOff(decisions, r, null);
        Mat image = new Mat(900, 900, CvType.CV_8UC4, new Scalar(15, 15, 15, 255));
        try {
            List<byte[]> before = new ArrayList<>();
            for (AlignmentDecision.Marker m : decisions) before.add(evidence(image, model, r, m));
            PositionReferenceShadowTest.unchanged(r, model);
            for (int i = 0; i < decisions.size(); i++)
                assertArrayEquals(before.get(i), evidence(image, model, r, decisions.get(i)));
        } finally { image.release(); }
        System.out.println("PASS adapter fixture replay: " + cases + " cases; 12 evidence images byte-identical");
    }
}
