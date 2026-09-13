package com.watchalign.mobile.qc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generic per-index geometry measurement for applied hour markers in rectified dial coordinates.
 *
 * <p>The module is deliberately brand/model agnostic. A watch profile or calibration supplies the
 * expected marker-centre radius. This module only reports raw geometry and inherits confidence from
 * the common perspective-confidence service.</p>
 */
public final class IndexGeometryQcModule implements QcModule<IndexGeometryQcModule.Input> {
    public static final String ID = "generic.index_geometry";
    private static final double EPS = 1e-9;

    /** One applied marker observation after perspective rectification. */
    public static final class MarkerObservation {
        public final int hour;
        public final double centerX;
        public final double centerY;
        public final double axisInnerX;
        public final double axisInnerY;
        public final double axisOuterX;
        public final double axisOuterY;
        public final double expectedCenterRadiusRatio;
        public final Double widthPx;
        public final Double heightPx;

        private MarkerObservation(
                int hour,
                double centerX,
                double centerY,
                double axisInnerX,
                double axisInnerY,
                double axisOuterX,
                double axisOuterY,
                double expectedCenterRadiusRatio,
                Double widthPx,
                Double heightPx) {
            validateHour(hour);
            requireFinite(centerX, "centerX");
            requireFinite(centerY, "centerY");
            requireFinite(axisInnerX, "axisInnerX");
            requireFinite(axisInnerY, "axisInnerY");
            requireFinite(axisOuterX, "axisOuterX");
            requireFinite(axisOuterY, "axisOuterY");
            requireFinite(expectedCenterRadiusRatio, "expectedCenterRadiusRatio");
            if (expectedCenterRadiusRatio <= 0.0) {
                throw new IllegalArgumentException("expectedCenterRadiusRatio must be > 0");
            }
            if (Math.hypot(axisOuterX - axisInnerX, axisOuterY - axisInnerY) <= EPS) {
                throw new IllegalArgumentException("marker axis endpoints must not collapse");
            }
            validateOptionalSize(widthPx, "widthPx");
            validateOptionalSize(heightPx, "heightPx");
            this.hour = hour;
            this.centerX = centerX;
            this.centerY = centerY;
            this.axisInnerX = axisInnerX;
            this.axisInnerY = axisInnerY;
            this.axisOuterX = axisOuterX;
            this.axisOuterY = axisOuterY;
            this.expectedCenterRadiusRatio = expectedCenterRadiusRatio;
            this.widthPx = widthPx;
            this.heightPx = heightPx;
        }

        public static MarkerObservation of(
                int hour,
                double centerX,
                double centerY,
                double axisInnerX,
                double axisInnerY,
                double axisOuterX,
                double axisOuterY,
                double expectedCenterRadiusRatio) {
            return new MarkerObservation(
                    hour, centerX, centerY,
                    axisInnerX, axisInnerY, axisOuterX, axisOuterY,
                    expectedCenterRadiusRatio, null, null);
        }

        public static MarkerObservation ofWithSize(
                int hour,
                double centerX,
                double centerY,
                double axisInnerX,
                double axisInnerY,
                double axisOuterX,
                double axisOuterY,
                double expectedCenterRadiusRatio,
                double widthPx,
                double heightPx) {
            return new MarkerObservation(
                    hour, centerX, centerY,
                    axisInnerX, axisInnerY, axisOuterX, axisOuterY,
                    expectedCenterRadiusRatio, widthPx, heightPx);
        }
    }

    public static final class Input {
        final double dialCenterX;
        final double dialCenterY;
        final double dialRadiusPx;
        final List<MarkerObservation> markers;
        final PerspectiveConfidenceService.Assessment perspective;

        private Input(
                double dialCenterX,
                double dialCenterY,
                double dialRadiusPx,
                List<MarkerObservation> markers,
                PerspectiveConfidenceService.Assessment perspective) {
            requireFinite(dialCenterX, "dialCenterX");
            requireFinite(dialCenterY, "dialCenterY");
            requireFinite(dialRadiusPx, "dialRadiusPx");
            if (dialRadiusPx <= 0.0) throw new IllegalArgumentException("dialRadiusPx must be > 0");
            if (markers == null || markers.isEmpty()) {
                throw new IllegalArgumentException("at least one marker observation is required");
            }
            if (perspective == null) {
                throw new IllegalArgumentException("perspective assessment must not be null");
            }
            Set<Integer> hours = new HashSet<>();
            for (MarkerObservation marker : markers) {
                if (marker == null) throw new IllegalArgumentException("marker observation must not be null");
                if (!hours.add(marker.hour)) {
                    throw new IllegalArgumentException("duplicate hour marker: " + marker.hour);
                }
            }
            this.dialCenterX = dialCenterX;
            this.dialCenterY = dialCenterY;
            this.dialRadiusPx = dialRadiusPx;
            this.markers = Collections.unmodifiableList(new ArrayList<>(markers));
            this.perspective = perspective;
        }

        public static Input rectified(
                double dialCenterX,
                double dialCenterY,
                double dialRadiusPx,
                List<MarkerObservation> markers,
                PerspectiveConfidenceService.Assessment perspective) {
            return new Input(dialCenterX, dialCenterY, dialRadiusPx, markers, perspective);
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public QcModuleResult measure(Input input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");

        List<RawMeasurement> measurements = new ArrayList<>();
        for (MarkerObservation marker : input.markers) {
            addMarkerMeasurements(measurements, input, marker);
        }

        List<String> evidence = new ArrayList<>(input.perspective.evidence());
        evidence.add(String.format(Locale.US,
                "%d applied hour marker%s measured in rectified dial coordinates",
                input.markers.size(), input.markers.size() == 1 ? "" : "s"));
        evidence.add("Positive rotation means clockwise in image coordinates; raw geometry is not a pass/fail classification");

        return new QcModuleResult(
                ID,
                measurements,
                input.perspective.confidence(),
                evidence);
    }

    private static void addMarkerMeasurements(
            List<RawMeasurement> out,
            Input input,
            MarkerObservation marker) {
        double expectedAngleDeg = expectedAxisAngleDeg(marker.hour);
        double angleRad = Math.toRadians(expectedAngleDeg);
        double radialX = Math.cos(angleRad);
        double radialY = Math.sin(angleRad);
        double tangentX = -radialY;
        double tangentY = radialX;

        double relX = marker.centerX - input.dialCenterX;
        double relY = marker.centerY - input.dialCenterY;
        double radialRatio = (relX * radialX + relY * radialY) / input.dialRadiusPx;
        double tangentialRatio = (relX * tangentX + relY * tangentY) / input.dialRadiusPx;

        double axisX = marker.axisOuterX - marker.axisInnerX;
        double axisY = marker.axisOuterY - marker.axisInnerY;
        double observedAngleDeg = Math.toDegrees(Math.atan2(axisY, axisX));
        double rotationDeg = normalizeLineAngleDeg(observedAngleDeg - expectedAngleDeg);

        String prefix = String.format(Locale.US, "index_%02d_", marker.hour);
        out.add(new RawMeasurement(prefix + "center_radius_over_dial_radius", radialRatio, "DR"));
        out.add(new RawMeasurement(prefix + "radial_offset_over_dial_radius",
                radialRatio - marker.expectedCenterRadiusRatio, "DR"));
        out.add(new RawMeasurement(prefix + "tangential_offset_over_dial_radius", tangentialRatio, "DR"));
        out.add(new RawMeasurement(prefix + "rotation_deg", rotationDeg, "deg"));

        if (marker.widthPx != null) {
            out.add(new RawMeasurement(prefix + "width_over_dial_radius",
                    marker.widthPx / input.dialRadiusPx, "DR"));
        }
        if (marker.heightPx != null) {
            out.add(new RawMeasurement(prefix + "height_over_dial_radius",
                    marker.heightPx / input.dialRadiusPx, "DR"));
        }
    }

    static double expectedAxisAngleDeg(int hour) {
        validateHour(hour);
        return (hour % 12) * 30.0 - 90.0;
    }

    static double normalizeLineAngleDeg(double angleDeg) {
        double a = angleDeg;
        while (a > 90.0) a -= 180.0;
        while (a <= -90.0) a += 180.0;
        return a;
    }

    private static void validateHour(int hour) {
        if (hour < 1 || hour > 12) {
            throw new IllegalArgumentException("hour must be in 1..12");
        }
    }

    private static void validateOptionalSize(Double value, String name) {
        if (value == null) return;
        requireFinite(value, name);
        if (value <= 0.0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
