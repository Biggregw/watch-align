package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * TEST-ONLY audit of global clock phase using only the printed minute-track annulus.
 *
 * Frozen Alpha90 intentionally locks canonical 12 close to image-up and then lets the
 * minute-pair detector search only a narrow angular neighbourhood. During batch review
 * that can produce a numerically accepted overlay whose global rotation still looks
 * wrong when the photographed watch itself has a few degrees of roll.
 *
 * This class does NOT alter Alpha90, its homography, master, thresholds or verdict.
 * It independently scans a small global phase range against the same kind of evidence
 * Alpha90 is allowed to use: normal minute ticks plus the outer hour-position tick/stubs.
 * Applied hour markers, hands, text, date/cyclops and bezel are never sampled.
 *
 * The output is a review aid only. A CHECK flag means "inspect global rotation before
 * interpreting local marker residuals", not that the watch or Alpha90 has failed.
 */
final class Alpha90MinutePhaseAudit {
    static final class Result {
        final boolean valid;
        final double bestPhaseDeg;
        final double zeroScore;
        final double bestScore;
        final double gainFraction;
        final int strongNormalTicks;
        final int strongHourStubs;
        final boolean review;
        final String reason;

        Result(String reason) {
            this.valid = false;
            this.bestPhaseDeg = Double.NaN;
            this.zeroScore = Double.NaN;
            this.bestScore = Double.NaN;
            this.gainFraction = Double.NaN;
            this.strongNormalTicks = 0;
            this.strongHourStubs = 0;
            this.review = false;
            this.reason = reason;
        }

        Result(double bestPhaseDeg, double zeroScore, double bestScore,
               int strongNormalTicks, int strongHourStubs) {
            this.valid = true;
            this.bestPhaseDeg = bestPhaseDeg;
            this.zeroScore = zeroScore;
            this.bestScore = bestScore;
            this.gainFraction = (bestScore - zeroScore) / Math.max(1.0, Math.abs(zeroScore));
            this.strongNormalTicks = strongNormalTicks;
            this.strongHourStubs = strongHourStubs;
            // Deliberately conservative and review-only. Frozen Alpha90's hour-stub search
            // is +/-1.45 deg, so a materially better solution outside roughly that range
            // deserves visual review before local-marker conclusions are counted.
            this.review = Math.abs(bestPhaseDeg) > 1.50
                    && this.gainFraction > 0.04
                    && strongNormalTicks >= 18
                    && strongHourStubs >= 4;
            this.reason = "";
        }
    }

    private static final double NORMAL_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double HOUR_R = Gmt126710BlnrMaster.HOUR_TICK_SAMPLE_R;
    private static final double SEARCH_R_MIN = NORMAL_R - 0.065;
    private static final double SEARCH_R_MAX = NORMAL_R + 0.035;
    private static final double SEARCH_R_STEP = 0.0030;
    private static final double EDGE_DELTA_R = 0.0065;
    private static final double BODY_DELTA_R = 0.017;
    private static final double HOUR_SIDE_DEG = 1.05;
    private static final double HOUR_RADIAL_DELTA = 0.008;
    private static final double MIN_EDGE_SCORE = 20.0;
    private static final double MIN_HOUR_SCORE = 15.0;
    private static final double PHASE_MIN_DEG = -10.0;
    private static final double PHASE_MAX_DEG = 10.0;
    private static final double PHASE_STEP_DEG = 0.25;

    private Alpha90MinutePhaseAudit() {}

    static Result analyse(Bitmap input) {
        if (input == null) return new Result("no image");
        Mat rgba = new Mat(), bgr = new Mat(), gray = new Mat(), enh = new Mat();
        try {
            Utils.bitmapToMat(input, rgba);
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR);

            StrictDialBoundarySeedAnalyzer.Result seed = StrictDialBoundarySeedAnalyzer.analyse(bgr);
            if (seed == null || !seed.valid)
                return new Result(seed == null ? "dial seed unavailable" : seed.reason);
            DialEdgeEllipseFit.Fit edge = DialEdgeFitter.fitBgr(bgr, seed.x, seed.y, seed.r);
            if (edge == null) return new Result("physical dial ellipse unavailable");

            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));
            clahe.apply(gray, enh);
            Imgproc.GaussianBlur(enh, enh, new Size(3, 3), 0.65);

            PhaseScore zero = scorePhase(enh, edge, 0.0);
            if (!zero.valid) return new Result("minute-track phase evidence unavailable");

            PhaseScore best = zero;
            for (double phase = PHASE_MIN_DEG; phase <= PHASE_MAX_DEG + 1e-9; phase += PHASE_STEP_DEG) {
                PhaseScore s = scorePhase(enh, edge, phase);
                if (s.valid && s.score > best.score) best = s;
            }
            return new Result(best.phaseDeg, zero.score, best.score,
                    best.strongNormalTicks, best.strongHourStubs);
        } catch (Throwable t) {
            return new Result("phase audit failed: " + t.getClass().getSimpleName());
        } finally {
            enh.release(); gray.release(); bgr.release(); rgba.release();
        }
    }

    private static final class PhaseScore {
        final boolean valid;
        final double phaseDeg;
        final double score;
        final int strongNormalTicks;
        final int strongHourStubs;
        PhaseScore(boolean valid, double phaseDeg, double score, int normal, int hour) {
            this.valid = valid; this.phaseDeg = phaseDeg; this.score = score;
            this.strongNormalTicks = normal; this.strongHourStubs = hour;
        }
    }

    private static PhaseScore scorePhase(Mat gray, DialEdgeEllipseFit.Fit edge, double phaseDeg) {
        double rad = Math.toRadians(phaseDeg);
        Mat h = ellipsePose(edge, Math.sin(rad), -Math.cos(rad));
        if (h == null || h.empty()) return new PhaseScore(false, phaseDeg, Double.NaN, 0, 0);
        try {
            List<Double> normal = new ArrayList<>();
            List<Double> hours = new ArrayList<>();
            int strongNormal = 0, strongHour = 0;
            for (int minute = 0; minute < 60; minute++) {
                double a = Gmt126710BlnrMaster.angleForMinute(minute);
                if (minute % 5 == 0) {
                    double s = hourStubScore(gray, h, a);
                    if (Double.isFinite(s)) {
                        double q = clip(s / MIN_HOUR_SCORE, 0.0, 2.5);
                        hours.add(q);
                        if (s >= MIN_HOUR_SCORE) strongHour++;
                    }
                } else {
                    double best = Double.NEGATIVE_INFINITY;
                    // Radial search only. Global angular phase is the quantity being audited.
                    for (double r = SEARCH_R_MIN; r <= SEARCH_R_MAX + 1e-9; r += SEARCH_R_STEP) {
                        double s = normalEdgeScore(gray, h, r, a);
                        if (Double.isFinite(s) && s > best) best = s;
                    }
                    if (Double.isFinite(best)) {
                        double q = clip(best / MIN_EDGE_SCORE, 0.0, 2.5);
                        normal.add(q);
                        if (best >= MIN_EDGE_SCORE) strongNormal++;
                    }
                }
            }
            if (normal.size() < 24 || hours.size() < 8)
                return new PhaseScore(false, phaseDeg, Double.NaN, strongNormal, strongHour);

            // Trim extremes so glare or a hand crossing one tick cannot dominate the scan.
            double normalMean = trimmedMean(normal, 0.15);
            double hourMean = trimmedMean(hours, 0.10);
            // Hour-position stubs are intentionally given extra weight because they break
            // the 6-degree periodic ambiguity of the otherwise repetitive minute track.
            double score = 0.68 * normalMean + 0.32 * hourMean;
            return new PhaseScore(true, phaseDeg, score, strongNormal, strongHour);
        } finally {
            h.release();
        }
    }

    private static double trimmedMean(List<Double> values, double trimFraction) {
        if (values.isEmpty()) return Double.NaN;
        List<Double> v = new ArrayList<>(values);
        Collections.sort(v);
        int trim = Math.min(v.size() / 3, (int)Math.floor(v.size() * trimFraction));
        int from = trim, to = v.size() - trim;
        if (from >= to) { from = 0; to = v.size(); }
        double sum = 0.0;
        for (int i = from; i < to; i++) sum += v.get(i);
        return sum / Math.max(1, to - from);
    }

    private static double normalEdgeScore(Mat gray, Mat h, double r, double a) {
        double ca = Math.cos(a), sa = Math.sin(a);
        double inner = intensity(gray, h, (r - EDGE_DELTA_R) * ca, (r - EDGE_DELTA_R) * sa);
        double outer = intensity(gray, h, (r + EDGE_DELTA_R) * ca, (r + EDGE_DELTA_R) * sa);
        double body = intensity(gray, h, (r + BODY_DELTA_R) * ca, (r + BODY_DELTA_R) * sa);
        if (!Double.isFinite(inner) || !Double.isFinite(outer) || !Double.isFinite(body)) return Double.NaN;
        return 0.65 * (outer - inner) + 0.35 * (body - inner);
    }

    private static double hourStubScore(Mat gray, Mat h, double a) {
        double side = Math.toRadians(HOUR_SIDE_DEG);
        double r = HOUR_R;
        double c0 = intensity(gray, h, r * Math.cos(a), r * Math.sin(a));
        double cm = intensity(gray, h, r * Math.cos(a - side), r * Math.sin(a - side));
        double cp = intensity(gray, h, r * Math.cos(a + side), r * Math.sin(a + side));
        double ri = intensity(gray, h, (r - HOUR_RADIAL_DELTA) * Math.cos(a), (r - HOUR_RADIAL_DELTA) * Math.sin(a));
        double ro = intensity(gray, h, (r + HOUR_RADIAL_DELTA) * Math.cos(a), (r + HOUR_RADIAL_DELTA) * Math.sin(a));
        if (!Double.isFinite(c0) || !Double.isFinite(cm) || !Double.isFinite(cp)
                || !Double.isFinite(ri) || !Double.isFinite(ro)) return Double.NaN;
        double angularRidge = c0 - 0.5 * (cm + cp);
        double radialSupport = Math.min(c0, 0.5 * (ri + ro));
        return angularRidge + 0.12 * radialSupport;
    }

    private static double intensity(Mat gray, Mat h, double x, double y) {
        Point p = project(h, x, y);
        if (p == null || p.x < 1 || p.y < 1 || p.x >= gray.cols() - 2 || p.y >= gray.rows() - 2)
            return Double.NaN;
        int x0 = (int)Math.floor(p.x), y0 = (int)Math.floor(p.y);
        double fx = p.x - x0, fy = p.y - y0;
        double d00 = gray.get(y0, x0)[0], d10 = gray.get(y0, x0 + 1)[0];
        double d01 = gray.get(y0 + 1, x0)[0], d11 = gray.get(y0 + 1, x0 + 1)[0];
        return (d00 * (1 - fx) + d10 * fx) * (1 - fy) + (d01 * (1 - fx) + d11 * fx) * fy;
    }

    private static Mat ellipsePose(DialEdgeEllipseFit.Fit e, double targetDx, double targetDy) {
        double a = Math.toRadians(e.angleDeg), ca = Math.cos(a), sa = Math.sin(a);
        double a00 = ca * e.axisA, a01 = -sa * e.axisB;
        double a10 = sa * e.axisA, a11 = ca * e.axisB;
        double det = a00 * a11 - a01 * a10;
        if (Math.abs(det) < 1e-9) return null;

        double vx = (a11 * targetDx - a01 * targetDy) / det;
        double vy = (-a10 * targetDx + a00 * targetDy) / det;
        double vn = Math.hypot(vx, vy);
        if (!(vn > 1e-9)) { vx = 0; vy = -1; vn = 1; }
        vx /= vn; vy /= vn;
        double phi = Math.atan2(vx, -vy), cp = Math.cos(phi), sp = Math.sin(phi);
        double r00 = cp, r01 = -sp, r10 = sp, r11 = cp;

        double h00 = a00 * r00 + a01 * r10, h01 = a00 * r01 + a01 * r11;
        double h10 = a10 * r00 + a11 * r10, h11 = a10 * r01 + a11 * r11;
        Mat out = Mat.eye(3, 3, CvType.CV_64F);
        out.put(0, 0, h00, h01, e.cx, h10, h11, e.cy, 0, 0, 1);
        return out;
    }

    private static Point project(Mat h, double x, double y) {
        double[] m = new double[9]; h.get(0, 0, m);
        double w = m[6] * x + m[7] * y + m[8];
        if (Math.abs(w) < 1e-9) return null;
        return new Point((m[0] * x + m[1] * y + m[2]) / w,
                (m[3] * x + m[4] * y + m[5]) / w);
    }

    private static double clip(double x, double lo, double hi) {
        return Math.max(lo, Math.min(hi, x));
    }
}
