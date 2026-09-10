package com.watchalign.mobile;

import java.util.Arrays;

/** Pure-Java geometry registration used by the Android image pipeline. */
public final class GeometryRegistration {
    public static final class Solution {
        public final double rotationDeg;
        public final double scale;
        public final double confidence;
        public final double scaleAgreement;
        public final double angularAgreement;
        public final double perspectiveAgreement;
        public final boolean usable;
        public final String reason;

        Solution(double rotationDeg, double scale, double confidence,
                 double scaleAgreement, double angularAgreement,
                 double perspectiveAgreement, boolean usable, String reason) {
            this.rotationDeg = rotationDeg;
            this.scale = scale;
            this.confidence = confidence;
            this.scaleAgreement = scaleAgreement;
            this.angularAgreement = angularAgreement;
            this.perspectiveAgreement = perspectiveAgreement;
            this.usable = usable;
            this.reason = reason;
        }
    }

    public static double median(double[] values) {
        if (values == null || values.length == 0) return Double.NaN;
        double[] copy = Arrays.copyOf(values, values.length);
        Arrays.sort(copy);
        int n = copy.length;
        return (n & 1) == 1 ? copy[n / 2] : (copy[n / 2 - 1] + copy[n / 2]) * 0.5;
    }

    public static double wrap180(double deg) {
        double x = deg % 360.0;
        if (x <= -180.0) x += 360.0;
        if (x > 180.0) x -= 360.0;
        return x;
    }

    /**
     * Solve a similarity transform from model geometry, not raw pixels.
     * Marker radii are absolute image radii. Marker angles are the global
     * photo-roll estimates produced before per-marker QC normalization.
     */
    public static Solution solve(
            double srcDialRadius, double refDialRadius,
            double srcMarkerRadius, double refMarkerRadius,
            double srcGlobalAngle, double refGlobalAngle,
            double srcDialQuality, double refDialQuality,
            int commonMarkerCount, double markerAngularRmsDeg,
            double perspectiveMismatchDeg) {

        if (!(srcDialRadius > 0) || !(refDialRadius > 0)) {
            return bad("Missing dial radius.");
        }
        if (commonMarkerCount < 7) {
            return bad("Too few common hour markers for reliable registration.");
        }

        double dialScale = refDialRadius / srcDialRadius;
        double markerScale = (srcMarkerRadius > 0 && refMarkerRadius > 0)
                ? refMarkerRadius / srcMarkerRadius : dialScale;
        if (!Double.isFinite(dialScale) || !Double.isFinite(markerScale)
                || dialScale < 0.30 || dialScale > 3.50
                || markerScale < 0.30 || markerScale > 3.50) {
            return bad("Implausible model scale.");
        }

        double relativeScaleMismatch = Math.abs(markerScale / dialScale - 1.0);
        if (relativeScaleMismatch > 0.18) {
            return bad("Dial and marker-ring scales disagree too much.");
        }

        // Marker-ring scale is less sensitive to exactly which dial/rehaut edge was detected.
        double scale = markerScale * 0.75 + dialScale * 0.25;
        double rotation = wrap180(refGlobalAngle - srcGlobalAngle);
        if (Math.abs(rotation) > 25.0) {
            return bad("Reference roll differs too much from the watch photo.");
        }

        double scaleAgreement = 1.0 - Math.min(1.0, relativeScaleMismatch / 0.18);
        double angularAgreement = Math.exp(-Math.max(0.0, markerAngularRmsDeg) / 2.5);
        double perspectiveAgreement = Double.isFinite(perspectiveMismatchDeg)
                ? 1.0 - Math.min(1.0, Math.abs(perspectiveMismatchDeg) / 18.0)
                : 0.70;
        double quality = Math.max(0.0, Math.min(1.0, Math.min(srcDialQuality, refDialQuality)));
        double coverage = Math.max(0.0, Math.min(1.0, commonMarkerCount / 12.0));

        double confidence = 0.30 * quality
                + 0.25 * coverage
                + 0.20 * angularAgreement
                + 0.15 * scaleAgreement
                + 0.10 * perspectiveAgreement;
        boolean usable = confidence >= 0.58;
        return new Solution(rotation, scale, confidence, scaleAgreement,
                angularAgreement, perspectiveAgreement, usable,
                usable ? null : "Geometry registration confidence is too low.");
    }

    private static Solution bad(String reason) {
        return new Solution(0, 1, 0, 0, 0, 0, false, reason);
    }

    private GeometryRegistration() {}
}
