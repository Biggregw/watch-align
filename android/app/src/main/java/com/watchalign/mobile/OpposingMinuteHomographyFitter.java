package com.watchalign.mobile;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.List;

/**
 * Perspective proof based on explicit minor-minute correspondences.
 *
 * The ellipse-derived H0 is only a search seed. For each of the 48 non-hour
 * minute positions we look for the actual INNER end of that printed tick in a
 * small neighbourhood around the H0 prediction. Only complete opposing pairs
 * i <-> i+30 are admitted. A robust homography is then fitted from the known
 * canonical minute positions to those measured image points.
 *
 * Applied hour markers, 12 triangle, date/cyclops, text and hands are never
 * used by this fitter, so they remain independent visual checks of the result.
 */
final class OpposingMinuteHomographyFitter {
    static final class Result {
        final Mat homography;
        final boolean accepted;
        final int detectedTicks;
        final int completePairs;
        final int inliers;
        final double seedRmsPx;
        final double fittedRmsPx;
        final String reason;

        Result(Mat h, boolean accepted, int detectedTicks, int completePairs,
               int inliers, double seedRmsPx, double fittedRmsPx, String reason) {
            this.homography = h;
            this.accepted = accepted;
            this.detectedTicks = detectedTicks;
            this.completePairs = completePairs;
            this.inliers = inliers;
            this.seedRmsPx = seedRmsPx;
            this.fittedRmsPx = fittedRmsPx;
            this.reason = reason;
        }
    }

    private static final double TRUE_INNER_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double SEARCH_R_MIN = TRUE_INNER_R - 0.070;
    private static final double SEARCH_R_MAX = TRUE_INNER_R + 0.040;
    private static final double SEARCH_R_STEP = 0.0025;
    private static final double SEARCH_A_DEG = 1.8;
    private static final double SEARCH_A_STEP_DEG = 0.20;
    private static final double EDGE_DELTA_R = 0.0075;
    private static final double BODY_DELTA_R = 0.018;
    private static final double MIN_EDGE_SCORE = 20.0;
    private static final int MIN_COMPLETE_PAIRS = 7;
    private static final int MIN_INLIERS = 12;

    private static final class Tick {
        final int minute;
        final Point canonical;
        final Point image;
        final double score;
        Tick(int minute, Point canonical, Point image, double score) {
            this.minute = minute; this.canonical = canonical; this.image = image; this.score = score;
        }
    }

    private OpposingMinuteHomographyFitter() {}

    static Result fit(Mat gray, Mat h0) {
        if (gray == null || gray.empty() || h0 == null || h0.empty())
            return new Result(h0 == null ? null : h0.clone(), false, 0, 0, 0,
                    Double.NaN, Double.NaN, "missing image or seed pose");

        Tick[] ticks = new Tick[60];
        int detected = 0;
        for (int minute = 0; minute < 60; minute++) {
            if (minute % 5 == 0) continue;
            Tick t = detectTickInnerEnd(gray, h0, minute);
            if (t != null) { ticks[minute] = t; detected++; }
        }

        List<Point> src = new ArrayList<>();
        List<Point> dst = new ArrayList<>();
        int pairs = 0;
        for (int minute = 0; minute < 30; minute++) {
            if (minute % 5 == 0) continue;
            Tick a = ticks[minute], b = ticks[minute + 30];
            if (a == null || b == null) continue;
            // Complete opposite pairs only. This prevents one isolated false edge on
            // one side of the dial from gaining leverage over the perspective fit.
            src.add(a.canonical); dst.add(a.image);
            src.add(b.canonical); dst.add(b.image);
            pairs++;
        }

        if (pairs < MIN_COMPLETE_PAIRS) {
            return new Result(h0.clone(), false, detected, pairs, 0,
                    Double.NaN, Double.NaN, "not enough opposing minor-minute pairs");
        }

        MatOfPoint2f srcPts = new MatOfPoint2f();
        MatOfPoint2f dstPts = new MatOfPoint2f();
        Mat mask = new Mat();
        Mat fitted = null;
        try {
            srcPts.fromList(src);
            dstPts.fromList(dst);
            double seedRms = rms(h0, src, dst, null);
            fitted = Calib3d.findHomography(srcPts, dstPts, Calib3d.RANSAC, 3.0, mask);
            if (fitted == null || fitted.empty()) {
                if (fitted != null) fitted.release();
                return new Result(h0.clone(), false, detected, pairs, 0,
                        seedRms, Double.NaN, "robust minute homography failed");
            }

            byte[] mb = new byte[(int)mask.total()];
            if (mb.length > 0) mask.get(0,0,mb);
            int inliers = 0;
            boolean[] keep = new boolean[src.size()];
            for (int i=0;i<keep.length;i++) {
                keep[i] = i < mb.length && (mb[i] & 0xff) != 0;
                if (keep[i]) inliers++;
            }
            double fitRms = rms(fitted, src, dst, keep);

            boolean enough = inliers >= MIN_INLIERS;
            boolean improves = Double.isFinite(seedRms) && Double.isFinite(fitRms)
                    && fitRms < seedRms * 0.72 && fitRms + 0.75 < seedRms;
            if (!enough || !improves) {
                fitted.release();
                return new Result(h0.clone(), false, detected, pairs, inliers,
                        seedRms, fitRms, !enough ? "too few robust minute inliers" : "minute fit did not materially improve seed pose");
            }

            Mat out = fitted.clone();
            fitted.release();
            return new Result(out, true, detected, pairs, inliers, seedRms, fitRms, "");
        } finally {
            if (fitted != null && !fitted.empty()) fitted.release();
            mask.release(); srcPts.release(); dstPts.release();
        }
    }

    private static Tick detectTickInnerEnd(Mat gray, Mat h0, int minute) {
        double baseA = Math.toRadians(minute * 6.0 - 90.0);
        double best = -Double.MAX_VALUE;
        double bestR = Double.NaN, bestA = Double.NaN;

        for (double daDeg = -SEARCH_A_DEG; daDeg <= SEARCH_A_DEG + 1e-9; daDeg += SEARCH_A_STEP_DEG) {
            double a = baseA + Math.toRadians(daDeg);
            for (double r = SEARCH_R_MIN; r <= SEARCH_R_MAX + 1e-9; r += SEARCH_R_STEP) {
                double inner = intensity(gray, h0, (r - EDGE_DELTA_R) * Math.cos(a), (r - EDGE_DELTA_R) * Math.sin(a));
                double outer = intensity(gray, h0, (r + EDGE_DELTA_R) * Math.cos(a), (r + EDGE_DELTA_R) * Math.sin(a));
                double body  = intensity(gray, h0, (r + BODY_DELTA_R) * Math.cos(a), (r + BODY_DELTA_R) * Math.sin(a));
                if (!Double.isFinite(inner) || !Double.isFinite(outer) || !Double.isFinite(body)) continue;
                // Printed minor ticks are bright radial strokes on a dark dial. At the
                // inner end we expect a strong dark->bright step which stays bright
                // further outward into the tick body.
                double score = 0.65 * (outer - inner) + 0.35 * (body - inner);
                if (score > best) { best = score; bestR = r; bestA = a; }
            }
        }
        if (!(best >= MIN_EDGE_SCORE) || !Double.isFinite(bestR)) return null;

        Point actual = project(h0, bestR * Math.cos(bestA), bestR * Math.sin(bestA));
        Point canonical = new Point(TRUE_INNER_R * Math.cos(baseA), TRUE_INNER_R * Math.sin(baseA));
        if (actual == null) return null;
        return new Tick(minute, canonical, actual, best);
    }

    private static double intensity(Mat gray, Mat h, double x, double y) {
        Point p = project(h, x, y);
        if (p == null || p.x < 1 || p.y < 1 || p.x >= gray.cols()-2 || p.y >= gray.rows()-2) return Double.NaN;
        int x0=(int)Math.floor(p.x), y0=(int)Math.floor(p.y);
        double fx=p.x-x0, fy=p.y-y0;
        double d00=gray.get(y0,x0)[0], d10=gray.get(y0,x0+1)[0];
        double d01=gray.get(y0+1,x0)[0], d11=gray.get(y0+1,x0+1)[0];
        return (d00*(1-fx)+d10*fx)*(1-fy)+(d01*(1-fx)+d11*fx)*fy;
    }

    private static Point project(Mat h, double x, double y) {
        double[] m = new double[9]; h.get(0,0,m);
        double w=m[6]*x+m[7]*y+m[8];
        if (Math.abs(w)<1e-9) return null;
        return new Point((m[0]*x+m[1]*y+m[2])/w, (m[3]*x+m[4]*y+m[5])/w);
    }

    private static double rms(Mat h, List<Point> src, List<Point> dst, boolean[] keep) {
        double ss=0; int n=0;
        for(int i=0;i<src.size();i++) {
            if(keep!=null && !keep[i]) continue;
            Point p=project(h,src.get(i).x,src.get(i).y); if(p==null) continue;
            double dx=p.x-dst.get(i).x,dy=p.y-dst.get(i).y;
            ss+=dx*dx+dy*dy;n++;
        }
        return n==0?Double.NaN:Math.sqrt(ss/n);
    }
}
