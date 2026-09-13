package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import com.watchalign.mobile.qc.GmtIndexPlausibility;
import com.watchalign.mobile.qc.IndexGeometryQcModule;
import com.watchalign.mobile.qc.PerspectiveConfidenceService;
import com.watchalign.mobile.qc.QcModuleResult;
import com.watchalign.mobile.qc.RawMeasurement;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Production bridge from automatic GMT marker localisation into the generic per-index geometry
 * module. Marker centres are rectified through the user's corrected 12/3/6/9 pose before
 * measurement.
 *
 * <p>The expected marker radius is the median radius of plausible rectified markers in the same
 * watch image. It is a within-watch consensus baseline, not a Rolex tolerance or a genuine-control
 * calibration. Any localisation that lands outside the selected hour sector or far from the common
 * marker ring is rejected before it can reach the measurement module.</p>
 */
final class GmtIndexAutoAnalyzer {
    static final class Result {
        final QcModuleResult geometry;
        final Bitmap annotated;
        final String summary;
        final int localizedCount;

        Result(QcModuleResult geometry, Bitmap annotated, String summary, int localizedCount) {
            this.geometry = geometry;
            this.annotated = annotated;
            this.summary = summary;
            this.localizedCount = localizedCount;
        }
    }

    private static final class Candidate {
        int hour;
        double imageX, imageY;
        double dialX, dialY, radius;
        double[] axis;
    }

    private GmtIndexAutoAnalyzer() {}

    static Result analyse(Bitmap watch, PerspectiveMasterRenderer.Pose pose) {
        if (watch == null || pose == null || !pose.anchorMode || !pose.perspectiveMode) {
            return unavailable(watch, "Index geometry unavailable: corrected 12/3/6/9 perspective anchors are required.");
        }

        PerspectiveConfidenceService.Assessment perspective = PerspectiveConfidenceService.assess(
                pose.anchor12X, pose.anchor12Y,
                pose.anchor3X, pose.anchor3Y,
                pose.anchor6X, pose.anchor6Y,
                pose.anchor9X, pose.anchor9Y);

        Mat src = new Mat();
        try {
            Utils.bitmapToMat(watch, src);
            Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle dial = DialAnalysisEngine.detectDial(src);
            if (dial == null) return unavailable(watch, "Index geometry unavailable: dial could not be localized reliably.");

            DialAnalysisEngine.MarkerSet set = DialAnalysisEngine.measureMarkerSet(src, dial);
            if (set == null || set.markers == null || set.markers.isEmpty()) {
                return unavailable(watch, "Index geometry unavailable: no hour-marker centres were localized reliably.");
            }
            double global = Double.isFinite(set.globalRotation) ? set.globalRotation : 0.0;
            List<Candidate> firstPass = new ArrayList<>();
            List<Double> radii = new ArrayList<>();

            for (DialAnalysisEngine.Marker marker : set.markers) {
                if (marker.hour == 3) continue; // GMT date aperture, not an applied index.
                double clockDeg = (marker.hour == 12 ? 0.0 : marker.hour * 30.0) + global + marker.angular;
                double theta = Math.toRadians(clockDeg);
                double ix = dial.x + Math.sin(theta) * marker.radius;
                double iy = dial.y - Math.cos(theta) * marker.radius;
                double[] q = PerspectiveRectifier.toDialRaw(pose, ix, iy);
                if (q == null || !GmtIndexPlausibility.plausibleCanonicalPosition(marker.hour, q[0], q[1])) continue;

                Candidate c = new Candidate();
                c.hour = marker.hour;
                c.imageX = ix;
                c.imageY = iy;
                c.dialX = q[0];
                c.dialY = q[1];
                c.radius = Math.hypot(q[0], q[1]);
                if (marker.hour == 6 || marker.hour == 9) {
                    c.axis = elongatedAxis(src, ix, iy, Math.max(10.0, dial.r * 0.10));
                }
                firstPass.add(c);
                radii.add(c.radius);
            }

            if (firstPass.size() < 7) {
                return unavailable(watch, "Index geometry unavailable: fewer than seven hour markers passed canonical sector checks.");
            }

            double expectedRadius = median(radii);
            List<Candidate> candidates = new ArrayList<>();
            for (Candidate c : firstPass) {
                if (GmtIndexPlausibility.plausibleAgainstRing(c.hour, c.dialX, c.dialY, expectedRadius)) {
                    candidates.add(c);
                }
            }
            if (candidates.size() < 7) {
                return unavailable(watch, "Index geometry unavailable: marker-ring geometry was inconsistent after rectification.");
            }

            List<IndexGeometryQcModule.MarkerObservation> observations = new ArrayList<>();
            for (Candidate c : candidates) {
                double[] innerOuter = rectifiedAxis(pose, c, expectedRadius);
                if (innerOuter == null) continue;
                observations.add(IndexGeometryQcModule.MarkerObservation.of(
                        c.hour, c.dialX, c.dialY,
                        innerOuter[0], innerOuter[1], innerOuter[2], innerOuter[3], expectedRadius));
            }
            if (observations.size() < 7) {
                return unavailable(watch, "Index geometry unavailable: marker axes could not be rectified reliably.");
            }

            QcModuleResult measured = new IndexGeometryQcModule().measure(
                    IndexGeometryQcModule.Input.rectified(0.0, 0.0, 1.0, observations, perspective));
            Bitmap annotated = annotate(watch, candidates);
            return new Result(measured, annotated, summarize(measured, observations.size()), observations.size());
        } catch (Throwable t) {
            return unavailable(watch, "Index geometry unavailable: " + t.getClass().getSimpleName() + ".");
        } finally {
            src.release();
        }
    }

    /**
     * Returns canonical inner/outer axis endpoints. For elongated 6/9 markers the image-derived
     * PCA axis is used. Round plots use the radial line only as a structural placeholder so their
     * rotation output is never shown as a body-rotation measurement.
     */
    private static double[] rectifiedAxis(PerspectiveMasterRenderer.Pose pose, Candidate c, double expectedRadius) {
        if (c.axis != null) {
            double[] a = PerspectiveRectifier.toDialRaw(pose, c.axis[0], c.axis[1]);
            double[] b = PerspectiveRectifier.toDialRaw(pose, c.axis[2], c.axis[3]);
            if (a != null && b != null) return new double[]{a[0], a[1], b[0], b[1]};
        }
        double r = Math.max(0.02, expectedRadius * 0.05);
        double n = Math.hypot(c.dialX, c.dialY);
        if (n <= 1e-9) return null;
        double ux = c.dialX / n, uy = c.dialY / n;
        return new double[]{c.dialX - ux * r, c.dialY - uy * r, c.dialX + ux * r, c.dialY + uy * r};
    }

    /** Weighted local PCA of bright marker material. Returns image-space line endpoints. */
    private static double[] elongatedAxis(Mat bgr, double cx, double cy, double half) {
        Mat gray = new Mat();
        try {
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
            int x0 = (int)Math.max(0, cx - half), x1 = (int)Math.min(gray.cols() - 1, cx + half);
            int y0 = (int)Math.max(0, cy - half), y1 = (int)Math.min(gray.rows() - 1, cy + half);
            double sw = 0, mx = 0, my = 0;
            for (int y = y0; y <= y1; y += 2) for (int x = x0; x <= x1; x += 2) {
                double[] g = gray.get(y, x); if (g == null) continue;
                double v = g[0]; if (v < 150) continue;
                double w = v - 140; sw += w; mx += w * x; my += w * y;
            }
            if (sw < 200) return null;
            mx /= sw; my /= sw;
            double cxx = 0, cyy = 0, cxy = 0;
            for (int y = y0; y <= y1; y += 2) for (int x = x0; x <= x1; x += 2) {
                double[] g = gray.get(y, x); if (g == null) continue;
                double v = g[0]; if (v < 150) continue;
                double w = v - 140, dx = x - mx, dy = y - my;
                cxx += w * dx * dx; cyy += w * dy * dy; cxy += w * dx * dy;
            }
            double angle = 0.5 * Math.atan2(2.0 * cxy, cxx - cyy);
            double ux = Math.cos(angle), uy = Math.sin(angle), len = Math.max(5.0, half * 0.65);
            return new double[]{mx - ux * len, my - uy * len, mx + ux * len, my + uy * len};
        } finally {
            gray.release();
        }
    }

    static String summarize(QcModuleResult result, int localizedCount) {
        if (result == null || result.measurements().isEmpty()) return "INDEX GEOMETRY\nNo reliable per-index measurements.";
        List<Ranked> tangential = new ArrayList<>();
        List<Ranked> radial = new ArrayList<>();
        for (int hour = 1; hour <= 12; hour++) {
            if (hour == 3) continue;
            RawMeasurement t = result.measurement(String.format(Locale.US, "index_%02d_tangential_offset_over_dial_radius", hour));
            RawMeasurement r = result.measurement(String.format(Locale.US, "index_%02d_radial_offset_over_dial_radius", hour));
            if (t != null) tangential.add(new Ranked(hour, t.value()));
            if (r != null) radial.add(new Ranked(hour, r.value()));
        }
        Comparator<Ranked> byAbs = (a,b) -> Double.compare(Math.abs(b.value), Math.abs(a.value));
        Collections.sort(tangential, byAbs); Collections.sort(radial, byAbs);
        String worstT = tangential.isEmpty() ? "n/a" : String.format(Locale.US, "%d %+5.3f DR", tangential.get(0).hour, tangential.get(0).value);
        String worstR = radial.isEmpty() ? "n/a" : String.format(Locale.US, "%d %+5.3f DR", radial.get(0).hour, radial.get(0).value);

        List<String> usableBody = new ArrayList<>();
        for (int hour : new int[]{6,9}) {
            RawMeasurement rotation = result.measurement(String.format(Locale.US, "index_%02d_rotation_deg", hour));
            if (rotation != null && GmtIndexPlausibility.bodyRotationUsable(hour, rotation.value(), result.confidence())) {
                usableBody.add(String.format(Locale.US, "%d %+.2f°", hour, rotation.value()));
            }
        }
        String body = usableBody.isEmpty() ? "\n6/9 body axis: withheld unless high-confidence component isolation is plausible"
                : "\n6/9 body axis: " + String.join(" · ", usableBody) + " (diagnostic)";

        return "INDEX GEOMETRY · " + localizedCount + " markers · " + result.confidence().name().toLowerCase(Locale.US) + " perspective"
                + "\nLargest tangential: " + worstT + " · radial: " + worstR + body
                + "\nPosition baseline = this watch's marker-ring median; impossible sector/ring localisations are rejected; not a Rolex tolerance.";
    }

    private static Bitmap annotate(Bitmap watch, List<Candidate> candidates) {
        Bitmap out = watch.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(out);
        float u = Math.max(1f, Math.min(out.getWidth(), out.getHeight()) / 900f);
        Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG); ring.setStyle(Paint.Style.STROKE); ring.setStrokeWidth(2.4f * u); ring.setColor(Color.CYAN);
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG); text.setStyle(Paint.Style.FILL); text.setTextSize(14f * u); text.setColor(Color.WHITE); text.setFakeBoldText(true);
        for (Candidate c : candidates) {
            canvas.drawCircle((float)c.imageX, (float)c.imageY, 6f * u, ring);
            canvas.drawText(String.valueOf(c.hour), (float)c.imageX + 7f * u, (float)c.imageY - 7f * u, text);
            if (c.axis != null) canvas.drawLine((float)c.axis[0], (float)c.axis[1], (float)c.axis[2], (float)c.axis[3], ring);
        }
        return out;
    }

    private static Result unavailable(Bitmap watch, String message) {
        Bitmap out = watch == null ? null : watch.copy(Bitmap.Config.ARGB_8888, false);
        return new Result(null, out, "INDEX GEOMETRY\n" + message, 0);
    }

    private static double median(List<Double> values) {
        List<Double> copy = new ArrayList<>(values); Collections.sort(copy);
        int n = copy.size(); return n % 2 == 1 ? copy.get(n/2) : (copy.get(n/2-1) + copy.get(n/2)) / 2.0;
    }

    private static final class Ranked {
        final int hour; final double value;
        Ranked(int hour, double value) { this.hour = hour; this.value = value; }
    }
}
