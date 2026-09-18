package com.watchalign.mobile;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded refinement of an ellipse-derived dial pose using geometry that is
 * independent of the applied hour markers.
 */
final class DialProjectiveRefiner {
    private static final double OUTER_R = 1.0;
    private static final double TRACK_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double TICK_RADIAL_HALF = 0.025;
    private static final double TICK_ANGULAR_HALF = Math.toRadians(0.34);
    private static final double LOSS_CAP_PX = 8.0;

    // Transform relative to H0: four linear nuisance terms, translation, h31 and h32.
    private static final double[] LIMIT = {0.08, 0.08, 0.08, 0.08, 0.26, 0.26, 0.32, 0.32};
    private static final double[] INITIAL_STEP = {0.025, 0.025, 0.025, 0.025, 0.055, 0.055, 0.055, 0.055};

    interface DistanceField {
        int width();
        int height();
        double distance(double x, double y);
    }

    static final class Result {
        final double[][] homography;
        final boolean accepted;
        final double fitBefore;
        final double fitAfter;
        final double holdoutBefore;
        final double holdoutAfter;

        Result(double[][] h, boolean accepted, double fitBefore, double fitAfter,
               double holdoutBefore, double holdoutAfter) {
            this.homography = h;
            this.accepted = accepted;
            this.fitBefore = fitBefore;
            this.fitAfter = fitAfter;
            this.holdoutBefore = holdoutBefore;
            this.holdoutAfter = holdoutAfter;
        }
    }

    static Mat refine(Mat edges, Mat h0) {
        Mat inverted = new Mat();
        Mat distance = new Mat();
        try {
            Imgproc.threshold(edges, inverted, 0.0, 255.0, Imgproc.THRESH_BINARY_INV);
            Imgproc.distanceTransform(inverted, distance, Imgproc.DIST_L2, Imgproc.DIST_MASK_PRECISE);
            Result result = refine(new MatDistanceField(distance), matrix(h0));
            if (!result.accepted) return h0.clone();
            Mat refined = new Mat(3, 3, CvType.CV_64F);
            refined.put(0, 0, flatten(result.homography));
            return refined;
        } finally {
            inverted.release();
            distance.release();
        }
    }

    static Result refine(DistanceField field, double[][] h0) {
        double[] zero = new double[8];
        double fitBefore = evidenceScore(field, h0, false, true);
        double holdoutBefore = evidenceScore(field, h0, true, false);
        double[] best = zero.clone();
        double bestObjective = objective(field, h0, best);
        double[] step = INITIAL_STEP.clone();

        // Projective displacement can put H0 outside the basin of a pixel-scale
        // edge loss. Seed the local search with a small bounded h31/h32 grid.
        for (double p = -LIMIT[6]; p <= LIMIT[6] + 1e-9; p += 0.08) {
            for (double q = -LIMIT[7]; q <= LIMIT[7] + 1e-9; q += 0.08) {
                double[] candidate = conicPreservingSeed(p, q);
                double value = objective(field, h0, candidate);
                if (value < bestObjective) {
                    bestObjective = value;
                    best = candidate;
                }
            }
        }

        // Deterministic bounded coordinate search. Several shifted sweeps reduce
        // coordinate-order bias without introducing unstable random behaviour.
        for (int level = 0; level < 8; level++) {
            boolean changed;
            int sweeps = 0;
            do {
                changed = false;
                for (int i = 0; i < best.length; i++) {
                    double original = best[i];
                    double selected = original;
                    for (int direction : new int[]{-1, 1}) {
                        double candidate = clamp(original + direction * step[i], -LIMIT[i], LIMIT[i]);
                        if (candidate == original) continue;
                        best[i] = candidate;
                        double value = objective(field, h0, best);
                        if (value + 1e-9 < bestObjective) {
                            bestObjective = value;
                            selected = candidate;
                            changed = true;
                        }
                    }
                    best[i] = selected;
                }
            } while (changed && ++sweeps < 5);
            for (int i = 0; i < step.length; i++) step[i] *= 0.5;
        }

        double[][] candidate = compose(h0, best);
        double fitAfter = evidenceScore(field, candidate, false, true);
        double holdoutAfter = evidenceScore(field, candidate, true, false);
        boolean hasEvidence = fitBefore < LOSS_CAP_PX * 0.92 && holdoutBefore < LOSS_CAP_PX * 0.92;
        boolean improvesFit = fitAfter < fitBefore - 0.08 && fitAfter <= fitBefore * 0.985;
        boolean improvesHoldout = holdoutAfter < holdoutBefore - 0.08 && holdoutAfter <= holdoutBefore * 0.985;
        boolean accepted = hasEvidence && improvesFit && improvesHoldout && finite(candidate);
        return new Result(accepted ? candidate : copy(h0), accepted,
                fitBefore, accepted ? fitAfter : fitBefore,
                holdoutBefore, accepted ? holdoutAfter : holdoutBefore);
    }

    /**
     * A normalized Lorentz boost maps the unit circle onto itself while adding
     * the requested projective denominator. H0 composed with this transform
     * therefore stays on the fitted ellipse and provides a physically useful
     * basin for the bounded nuisance search.
     */
    private static double[] conicPreservingSeed(double p, double q) {
        double magnitude2 = p * p + q * q;
        double gamma = 1.0 / Math.sqrt(Math.max(1e-6, 1.0 - magnitude2));
        double inverseGamma = 1.0 / gamma;
        double blend = magnitude2 > 1e-12 ? (1.0 - inverseGamma) / magnitude2 : 0.0;
        return new double[]{
                inverseGamma + blend * p * p - 1.0,
                blend * p * q,
                blend * p * q,
                inverseGamma + blend * q * q - 1.0,
                p, q, p, q
        };
    }

    private static double objective(DistanceField field, double[][] h0, double[] p) {
        double[][] h = compose(h0, p);
        if (!finite(h)) return Double.POSITIVE_INFINITY;
        double evidence = evidenceScore(field, h, false, true);
        double regularization = 0.0;
        for (int i = 0; i < p.length; i++) {
            double normalized = p[i] / LIMIT[i];
            double weight = i < 6 ? 0.055 : 0.012;
            regularization += weight * normalized * normalized;
        }
        return evidence + regularization;
    }

    /** fit ticks plus the outer boundary, or the disjoint holdout ticks. */
    private static double evidenceScore(DistanceField field, double[][] h,
                                        boolean holdout, boolean includeOuter) {
        List<Double> groups = new ArrayList<>();
        if (includeOuter) {
            for (int block = 0; block < 24; block++) {
                double sum = 0.0;
                for (int j = 0; j < 4; j++) {
                    double angle = 2.0 * Math.PI * (block * 4 + j) / 96.0;
                    sum += sample(field, h, OUTER_R * Math.cos(angle), OUTER_R * Math.sin(angle));
                }
                groups.add(sum / 4.0);
            }
        }
        for (int minute = 0; minute < 60; minute++) {
            if (minute % 5 == 0) continue; // all twelve five-minute/hour-marker sectors
            boolean thisHoldout = (minute & 1) == 0;
            if (thisHoldout != holdout) continue;
            double angle = Math.toRadians(minute * 6.0 - 90.0);
            groups.add(tickScore(field, h, angle));
        }
        return cappedMean(groups);
    }

    private static double tickScore(DistanceField field, double[][] h, double angle) {
        double sum = 0.0;
        int count = 0;
        // Score the two long edges and both ends of a narrow printed minor tick.
        for (double side : new double[]{-TICK_ANGULAR_HALF, TICK_ANGULAR_HALF}) {
            for (int i = 0; i < 4; i++) {
                double r = TRACK_R - TICK_RADIAL_HALF + 2.0 * TICK_RADIAL_HALF * i / 3.0;
                sum += sample(field, h, r * Math.cos(angle + side), r * Math.sin(angle + side));
                count++;
            }
        }
        for (double r : new double[]{TRACK_R - TICK_RADIAL_HALF, TRACK_R + TICK_RADIAL_HALF}) {
            for (int i = -1; i <= 1; i++) {
                double a = angle + i * TICK_ANGULAR_HALF;
                sum += sample(field, h, r * Math.cos(a), r * Math.sin(a));
                count++;
            }
        }
        return sum / count;
    }

    private static double sample(DistanceField field, double[][] h, double x, double y) {
        double w = h[2][0] * x + h[2][1] * y + h[2][2];
        if (Math.abs(w) < 1e-8) return LOSS_CAP_PX;
        double px = (h[0][0] * x + h[0][1] * y + h[0][2]) / w;
        double py = (h[1][0] * x + h[1][1] * y + h[1][2]) / w;
        if (px < 1 || py < 1 || px >= field.width() - 1 || py >= field.height() - 1) return LOSS_CAP_PX;
        return Math.min(LOSS_CAP_PX, Math.max(0.0, field.distance(px, py)));
    }

    private static double cappedMean(List<Double> values) {
        if (values.isEmpty()) return LOSS_CAP_PX;
        double sum = 0.0;
        for (double value : values) sum += Math.min(LOSS_CAP_PX, value);
        return sum / values.size();
    }

    private static double[][] compose(double[][] h0, double[] p) {
        double[][] delta = {
                {1.0 + p[0], p[1], p[4]},
                {p[2], 1.0 + p[3], p[5]},
                {p[6], p[7], 1.0}
        };
        double[][] out = multiply(h0, delta);
        double scale = out[2][2];
        if (Math.abs(scale) < 1e-9) return out;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) out[r][c] /= scale;
        return out;
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        double[][] out = new double[3][3];
        for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++)
            for (int k = 0; k < 3; k++) out[r][c] += a[r][k] * b[k][c];
        return out;
    }

    private static double[][] matrix(Mat mat) {
        double[] values = new double[9];
        mat.get(0, 0, values);
        return new double[][]{
                {values[0], values[1], values[2]},
                {values[3], values[4], values[5]},
                {values[6], values[7], values[8]}
        };
    }

    private static double[] flatten(double[][] h) {
        return new double[]{h[0][0], h[0][1], h[0][2], h[1][0], h[1][1], h[1][2], h[2][0], h[2][1], h[2][2]};
    }

    private static boolean finite(double[][] h) {
        for (double[] row : h) for (double value : row) if (!Double.isFinite(value)) return false;
        return true;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static double[][] copy(double[][] source) {
        return new double[][]{source[0].clone(), source[1].clone(), source[2].clone()};
    }

    private static final class MatDistanceField implements DistanceField {
        private final Mat distance;
        MatDistanceField(Mat distance) { this.distance = distance; }
        @Override public int width() { return distance.cols(); }
        @Override public int height() { return distance.rows(); }
        @Override public double distance(double x, double y) {
            int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
            double fx = x - x0, fy = y - y0;
            double d00 = distance.get(y0, x0)[0], d10 = distance.get(y0, x0 + 1)[0];
            double d01 = distance.get(y0 + 1, x0)[0], d11 = distance.get(y0 + 1, x0 + 1)[0];
            return (d00 * (1.0 - fx) + d10 * fx) * (1.0 - fy)
                    + (d01 * (1.0 - fx) + d11 * fx) * fy;
        }
    }

    private DialProjectiveRefiner() {}
}
