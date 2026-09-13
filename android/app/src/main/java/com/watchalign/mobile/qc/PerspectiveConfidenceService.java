package com.watchalign.mobile.qc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Brand/model-agnostic confidence gate for four-anchor dial rectification.
 *
 * <p>The service does not decide whether a watch is good or bad. It only estimates whether the
 * supplied 12/3/6/9 dial-edge quadrilateral is geometrically stable enough for fine QC claims.
 * The checks are scale independent so the same service can be reused across watch families.</p>
 */
public final class PerspectiveConfidenceService {
    private static final double EPS = 1e-9;

    public static final class Assessment {
        private final QcModuleResult.Confidence confidence;
        private final double score;
        private final List<String> evidence;

        private Assessment(QcModuleResult.Confidence confidence, double score, List<String> evidence) {
            this.confidence = confidence;
            this.score = score;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        }

        public QcModuleResult.Confidence confidence() { return confidence; }
        public double score() { return score; }
        public List<String> evidence() { return evidence; }
    }

    private PerspectiveConfidenceService() {}

    public static Assessment assess(
            double x12, double y12,
            double x3, double y3,
            double x6, double y6,
            double x9, double y9) {
        double[] v = {x12,y12,x3,y3,x6,y6,x9,y9};
        for (double value : v) {
            if (!Double.isFinite(value)) return low("Perspective anchors contain non-finite coordinates");
        }

        double[][] p = {{x12,y12},{x3,y3},{x6,y6},{x9,y9}};
        double[] sides = {
                distance(p[0], p[1]), distance(p[1], p[2]),
                distance(p[2], p[3]), distance(p[3], p[0])
        };
        double minSide = min(sides), maxSide = max(sides);
        if (minSide < EPS || maxSide < EPS) return low("Perspective anchors collapse onto each other");

        double c0 = cross(p[0], p[1], p[2]);
        double c1 = cross(p[1], p[2], p[3]);
        double c2 = cross(p[2], p[3], p[0]);
        double c3 = cross(p[3], p[0], p[1]);
        boolean positive = c0 > EPS && c1 > EPS && c2 > EPS && c3 > EPS;
        boolean negative = c0 < -EPS && c1 < -EPS && c2 < -EPS && c3 < -EPS;
        if (!positive && !negative) return low("Perspective anchors are non-convex, crossed or nearly collinear");

        double d126 = distance(p[0], p[2]);
        double d39 = distance(p[1], p[3]);
        if (d126 < EPS || d39 < EPS) return low("Perspective anchor diagonals are degenerate");

        double[] intersection = segmentIntersectionParameters(p[0], p[2], p[1], p[3]);
        if (intersection == null) return low("Perspective anchor diagonals do not form a stable intersection");
        double t = intersection[0], u = intersection[1];
        if (t <= 0 || t >= 1 || u <= 0 || u >= 1) {
            return low("Perspective anchor diagonal intersection falls outside the dial quadrilateral");
        }

        double sideRatio = minSide / maxSide;
        double diagonalRatio = Math.min(d126, d39) / Math.max(d126, d39);
        double intersectionMargin = Math.min(Math.min(t, 1 - t), Math.min(u, 1 - u));
        double area = polygonArea(p);
        double areaRatio = area / (d126 * d39);

        // Soft quality terms. They intentionally tolerate normal oblique watch photos while
        // penalising the extreme foreshortening and near-degeneracy that amplify tap error.
        double sideQuality = ramp(sideRatio, 0.20, 0.65);
        double diagonalQuality = ramp(diagonalRatio, 0.30, 0.70);
        double intersectionQuality = ramp(intersectionMargin, 0.08, 0.24);
        double areaQuality = ramp(areaRatio, 0.20, 0.42);
        double score = Math.min(Math.min(sideQuality, diagonalQuality),
                Math.min(intersectionQuality, areaQuality));

        QcModuleResult.Confidence confidence = score >= 0.75
                ? QcModuleResult.Confidence.HIGH
                : score >= 0.45 ? QcModuleResult.Confidence.MEDIUM : QcModuleResult.Confidence.LOW;

        List<String> evidence = new ArrayList<>();
        evidence.add(String.format(java.util.Locale.US,
                "Perspective confidence %.2f (%s): side ratio %.2f, diagonal ratio %.2f, intersection margin %.2f, area ratio %.2f",
                score, confidence.name().toLowerCase(java.util.Locale.US), sideRatio, diagonalRatio,
                intersectionMargin, areaRatio));
        if (confidence != QcModuleResult.Confidence.HIGH) {
            evidence.add("Fine alignment claims should be treated cautiously until a straighter, well-anchored image is available");
        }
        return new Assessment(confidence, score, evidence);
    }

    private static Assessment low(String reason) {
        return new Assessment(QcModuleResult.Confidence.LOW, 0.0,
                Collections.singletonList(reason));
    }

    private static double ramp(double value, double low, double high) {
        if (value <= low) return 0.0;
        if (value >= high) return 1.0;
        return (value - low) / (high - low);
    }

    private static double distance(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static double cross(double[] a, double[] b, double[] c) {
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
    }

    private static double polygonArea(double[][] p) {
        double sum = 0;
        for (int i = 0; i < p.length; i++) {
            double[] a = p[i], b = p[(i + 1) % p.length];
            sum += a[0] * b[1] - a[1] * b[0];
        }
        return Math.abs(sum) * 0.5;
    }

    private static double[] segmentIntersectionParameters(double[] a, double[] b, double[] c, double[] d) {
        double rx = b[0] - a[0], ry = b[1] - a[1];
        double sx = d[0] - c[0], sy = d[1] - c[1];
        double den = rx * sy - ry * sx;
        if (Math.abs(den) < EPS) return null;
        double qx = c[0] - a[0], qy = c[1] - a[1];
        double t = (qx * sy - qy * sx) / den;
        double u = (qx * ry - qy * rx) / den;
        return new double[]{t, u};
    }

    private static double min(double[] values) {
        double m = Double.POSITIVE_INFINITY;
        for (double v : values) m = Math.min(m, v);
        return m;
    }

    private static double max(double[] values) {
        double m = Double.NEGATIVE_INFINITY;
        for (double v : values) m = Math.max(m, v);
        return m;
    }
}
