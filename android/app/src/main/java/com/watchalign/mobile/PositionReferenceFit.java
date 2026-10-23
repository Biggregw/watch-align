package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shadow-only individual-marker positional reference against the OTHER measured markers.
 *
 * <p>Algorithm: the tested research MarkerGeometry biweight fit, without its archive-specific
 * 90% visible-outline eligibility rule. This class is deliberately independent of Android,
 * OpenCV, the QC classifier, and any production tolerance. All units are canonical dial
 * radii (R), except dialRadiusPx and the public output expressed in percent of R.
 *
 * <p>IMPORTANT: this fit removes common marker-ring translation, rotation and scale. It must
 * NEVER replace an existing absolute hour-line or whole-ring check on its own. It does not
 * and cannot issue QC verdicts. A caller must leave the original measurements, marker gates,
 * thresholds, and classifications untouched. The boolean originalAssessable must reflect
 * the existing pipeline's decisions, not a newly imposed research eligibility rule.
 *
 * <p>Phase: offline helper only. Not wired into the production Android path.
 */
public final class PositionReferenceFit {
    private PositionReferenceFit() {}

    public static final int MIN_OTHER_CONTRIBUTORS = 5;
    private static final int HUBER_ITERATIONS = 10;
    private static final int BIWEIGHT_ITERATIONS = 20;
    private static final double BIWEIGHT_C_R = 0.0030;
    private static final double SINGULAR_TOLERANCE = 1e-14;

    public enum Shape { ROUND, BATON, TRIANGLE }

    /** One already-detected outer-metal centre. No photo analysis is performed by this helper. */
    public static final class MarkerCentre {
        public final int hour;
        public final Shape shape;
        public final double fixedX, fixedY, observedX, observedY;
        /** Supplied by the OLD application gate. True only if the old measurement is credible. */
        public final boolean originalAssessable;

        public MarkerCentre(int hour, Shape shape, double fixedX, double fixedY,
                            double observedX, double observedY, boolean originalAssessable) {
            if (hour < 1 || hour > 12 || shape == null ||
                    !Double.isFinite(fixedX) || !Double.isFinite(fixedY) ||
                    !Double.isFinite(observedX) || !Double.isFinite(observedY)) {
                throw new IllegalArgumentException("invalid marker centre: hour, shape or coordinates");
            }
            this.hour = hour;
            this.shape = shape;
            this.fixedX = fixedX;
            this.fixedY = fixedY;
            this.observedX = observedX;
            this.observedY = observedY;
            this.originalAssessable = originalAssessable;
        }
    }

    /** Candidate positional result; never a defect finding. */
    public static final class Reading {
        public final int hour;
        public final boolean available;
        public final int otherContributors;
        /** Positive tangential means clockwise, positive radial means outward; both in % of R. */
        public final double tangentialPctR, radialPctR;
        public final String reason;

        private Reading(int hour, int otherContributors, double tangentialPctR,
                        double radialPctR, String reason) {
            this.hour = hour;
            this.otherContributors = otherContributors;
            this.tangentialPctR = tangentialPctR;
            this.radialPctR = radialPctR;
            this.reason = reason;
            this.available = Double.isFinite(tangentialPctR) && Double.isFinite(radialPctR)
                    && reason.isEmpty();
        }

        public double distancePctR() {
            return available ? Math.hypot(tangentialPctR, radialPctR) : Double.NaN;
        }
    }

    /**
     * Returns one shadow reading for each input hour. The original gate is respected for BOTH
     * targets and contributors. TRIANGLE is not measured (top-edge production logic remains
     * exclusive). A ring from at least five OTHER original-assessable rounds or batons is
     * required; absence gives an unavailable reading, NEVER a fail or pass.
     *
     * @throws IllegalArgumentException on malformed, duplicated, or non-finite input.
     */
    public static Map<Integer, Reading> measureAll(double dialRadiusPx,
                                                    List<MarkerCentre> markers) {
        if (!(dialRadiusPx > 0) || !Double.isFinite(dialRadiusPx) || markers == null) {
            throw new IllegalArgumentException("positive finite dialRadiusPx and marker list required");
        }
        Map<Integer, MarkerCentre> unique = new LinkedHashMap<>();
        for (MarkerCentre m : markers) {
            if (m == null || unique.put(m.hour, m) != null) {
                throw new IllegalArgumentException("null or duplicate hour in marker centres");
            }
        }
        List<MarkerCentre> pool = new ArrayList<>();
        for (MarkerCentre m : markers) {
            if (m.originalAssessable && m.shape != Shape.TRIANGLE) pool.add(m);
        }

        Map<Integer, Reading> out = new LinkedHashMap<>();
        for (MarkerCentre m : markers) {
            if (m.shape == Shape.TRIANGLE) {
                out.put(m.hour, missing(m.hour, 0, "triangle excluded: preserve top-edge method"));
                continue;
            }
            if (!m.originalAssessable) {
                out.put(m.hour, missing(m.hour, 0, "original app could not assess marker"));
                continue;
            }
            List<MarkerCentre> others = new ArrayList<>();
            for (MarkerCentre q : pool) if (q.hour != m.hour) others.add(q);
            if (others.size() < MIN_OTHER_CONTRIBUTORS) {
                out.put(m.hour, missing(m.hour, others.size(), "insufficient original-assessable neighbours"));
                continue;
            }
            double[] fit = biweight(others, dialRadiusPx);
            if (fit == null) {
                out.put(m.hour, missing(m.hour, others.size(), "ring fit is numerically indeterminate"));
                continue;
            }
            final double dx = (m.observedX - m.fixedX)
                    - (fit[0] + fit[2] * m.fixedX - fit[3] * m.fixedY);
            final double dy = (m.observedY - m.fixedY)
                    - (fit[1] + fit[2] * m.fixedY + fit[3] * m.fixedX);
            final double a = Math.toRadians(30.0 * (m.hour % 12));
            final double tangential = 100.0 * (dx * Math.cos(a) + dy * Math.sin(a));
            final double radial = 100.0 * (dx * Math.sin(a) - dy * Math.cos(a));
            if (!Double.isFinite(tangential) || !Double.isFinite(radial)) {
                out.put(m.hour, missing(m.hour, others.size(), "nonfinite ring residual"));
            } else {
                out.put(m.hour, new Reading(m.hour, others.size(), tangential, radial, ""));
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Reading missing(int hour, int n, String reason) {
        return new Reading(hour, n, Double.NaN, Double.NaN, reason);
    }

    // Existing MarkerGeometry LB mathematics; Huber pixel-weighted seed, then Tukey biweight.
    private static double[] biweight(List<MarkerCentre> rows, double dialRadiusPx) {
        final int n = rows.size();
        if (n < 3) return null;
        double[] w = new double[2 * n];
        Arrays.fill(w, 1.0);
        double[] fit = null;
        for (int j = 0; j < HUBER_ITERATIONS; j++) {
            fit = solve(rows, w);
            if (fit == null) return null;
            for (int i = 0; i < n; i++) {
                MarkerCentre m = rows.get(i);
                double dx = Math.abs((m.observedX - m.fixedX) - predictedX(fit, m)) * dialRadiusPx;
                double dy = Math.abs((m.observedY - m.fixedY) - predictedY(fit, m)) * dialRadiusPx;
                w[2 * i] = dx <= 1.0 ? 1.0 : 1.0 / Math.max(dx, 1e-9);
                w[2 * i + 1] = dy <= 1.0 ? 1.0 : 1.0 / Math.max(dy, 1e-9);
            }
        }
        for (int j = 0; j < BIWEIGHT_ITERATIONS; j++) {
            int live = 0;
            for (int i = 0; i < n; i++) {
                MarkerCentre m = rows.get(i);
                double dx = (m.observedX - m.fixedX) - predictedX(fit, m);
                double dy = (m.observedY - m.fixedY) - predictedY(fit, m);
                double u = Math.hypot(dx, dy) / BIWEIGHT_C_R;
                double weight = u < 1.0 ? Math.pow(1.0 - u * u, 2.0) : 0.0;
                if (weight > 0.0) live++;
                w[2 * i] = weight;
                w[2 * i + 1] = weight;
            }
            // Match the existing research estimator: fall back to the last valid Huber/biweight
            // estimate rather than inventing a fit from fewer than four robust inliers.
            if (live < 4) return fit;
            double[] next = solve(rows, w);
            if (next == null) return fit;
            fit = next;
        }
        return fit;
    }

    private static double predictedX(double[] f, MarkerCentre m) {
        return f[0] + f[2] * m.fixedX - f[3] * m.fixedY;
    }

    private static double predictedY(double[] f, MarkerCentre m) {
        return f[1] + f[2] * m.fixedY + f[3] * m.fixedX;
    }

    // Four parameters: translation x/y, linearised scale, linearised rotation.
    private static double[] solve(List<MarkerCentre> rows, double[] weights) {
        double[][] normal = new double[4][4];
        double[] rhs = new double[4];
        for (int i = 0; i < rows.size(); i++) {
            MarkerCentre m = rows.get(i);
            double[] ax = {1.0, 0.0, m.fixedX, -m.fixedY};
            double[] ay = {0.0, 1.0, m.fixedY, m.fixedX};
            double[] values = {m.observedX - m.fixedX, m.observedY - m.fixedY};
            double[][] design = {ax, ay};
            for (int row = 0; row < 2; row++) {
                double wr = weights[2 * i + row];
                for (int a = 0; a < 4; a++) {
                    rhs[a] += wr * design[row][a] * values[row];
                    for (int b = 0; b < 4; b++) {
                        normal[a][b] += wr * design[row][a] * design[row][b];
                    }
                }
            }
        }
        return gauss(normal, rhs);
    }

    private static double[] gauss(double[][] normal, double[] rhs) {
        final int n = 4;
        double[][] augmented = new double[n][n + 1];
        for (int row = 0; row < n; row++) {
            System.arraycopy(normal[row], 0, augmented[row], 0, n);
            augmented[row][n] = rhs[row];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int row = col + 1; row < n; row++) {
                if (Math.abs(augmented[row][col]) > Math.abs(augmented[pivot][col])) pivot = row;
            }
            if (!(Math.abs(augmented[pivot][col]) >= SINGULAR_TOLERANCE)) return null;
            double[] temp = augmented[pivot];
            augmented[pivot] = augmented[col];
            augmented[col] = temp;
            for (int row = 0; row < n; row++) {
                if (row == col) continue;
                double coeff = augmented[row][col] / augmented[col][col];
                for (int k = col; k <= n; k++) {
                    augmented[row][k] -= coeff * augmented[col][k];
                }
            }
        }
        double[] fit = new double[n];
        for (int i = 0; i < n; i++) {
            fit[i] = augmented[i][n] / augmented[i][i];
            if (!Double.isFinite(fit[i])) return null;
        }
        return fit;
    }
}
