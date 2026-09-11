package com.watchalign.mobile;

final class QcExtendedMath {
    static double wrap90(double deg) {
        double x = deg % 180.0;
        if (x <= -90.0) x += 180.0;
        if (x > 90.0) x -= 180.0;
        return x;
    }

    static double wrap180(double deg) {
        double x = deg % 360.0;
        if (x <= -180.0) x += 360.0;
        if (x > 180.0) x -= 360.0;
        return x;
    }

    static double smallestAxisError(double measuredAxisDeg, double expectedAxisDeg) {
        return wrap90(measuredAxisDeg - expectedAxisDeg);
    }

    static double clockAngleDeg(double cx, double cy, double x, double y) {
        double a = Math.toDegrees(Math.atan2(x - cx, -(y - cy)));
        if (a < 0) a += 360.0;
        return a;
    }

    static double hourAngleDeg(int hour) {
        if (hour == 12) return 0.0;
        return (hour % 12) * 30.0;
    }

    static int minuteIndexForHour(int hour) {
        return (hour % 12) * 5;
    }

    static double localDateAxisOffsetDeg(double cx, double cy, double x, double y,
                                         int dateHour, double globalRotationDeg) {
        double measured = clockAngleDeg(cx, cy, x, y);
        double expected = hourAngleDeg(dateHour) + globalRotationDeg;
        return wrap180(measured - expected);
    }

    static double localDateAxisOffsetFromAnchorDeg(double cx, double cy, double x, double y,
                                                   double localTrackAnchorDeg) {
        return wrap180(clockAngleDeg(cx, cy, x, y) - localTrackAnchorDeg);
    }

    static double minuteTrackUnits(double angularOffsetDeg) {
        return angularOffsetDeg / 6.0;
    }

    static double apparentMagnificationPercent(double watchNumeralHeightOverDial,
                                               double genuineNumeralHeightOverDial) {
        if (!Double.isFinite(watchNumeralHeightOverDial) || !Double.isFinite(genuineNumeralHeightOverDial)
                || watchNumeralHeightOverDial <= 0.0 || genuineNumeralHeightOverDial <= 0.0) return Double.NaN;
        return 100.0 * watchNumeralHeightOverDial / genuineNumeralHeightOverDial;
    }

    static int magnificationMatchSeverity(double percentOfGen) {
        if (!Double.isFinite(percentOfGen)) return 0;
        double d = Math.abs(percentOfGen - 100.0);
        if (d > 12.0) return 2;
        if (d > 7.0) return 1;
        return 0;
    }

    /**
     * Marker/lens orientation is derived from a local PCA/contour axis. For triangular markers,
     * cyclops reflections and hands crossing the sample window, a large angle is more commonly
     * a segmentation failure than a real component rotation. Do not turn those outliers into a
     * defect verdict. Small, stable deviations remain gradable.
     */
    static int orientationSeverity(double deg) {
        if (!Double.isFinite(deg)) return 0;
        double a = Math.abs(deg);
        if (a > 3.5) return 0;
        if (a > 2.0) return 2;
        if (a > 0.9) return 1;
        return 0;
    }

    static int localTrackSeverity(double deg) {
        double a = Math.abs(deg);
        if (a > 1.5) return 2;
        if (a > 0.7) return 1;
        return 0;
    }

    /**
     * The date aperture is compared with a locally detected minute-track anchor. Single-photo
     * perspective and track-marker isolation can easily move this by a few degrees, so only
     * larger, repeatable offsets are ranked.
     */
    static int dateAxisSeverity(double deg) {
        double a = Math.abs(deg);
        if (a > 7.5) return 2;
        if (a > 4.5) return 1;
        return 0;
    }

    /**
     * Horizontal centring is the useful QC signal. The vertical ink centroid changes materially
     * with the displayed numeral (1, 2, 8, etc.), so it remains diagnostic text only and must not
     * create an off-centre verdict by itself.
     */
    static int dateCenterSeverity(double xPct, double yPct) {
        double m = Math.abs(xPct);
        if (m > 12.0) return 2;
        if (m > 6.0) return 1;
        return 0;
    }

    static int cyclopsApertureOffsetSeverity(double dialRadiusFraction) {
        double a = Math.abs(dialRadiusFraction);
        if (a > 0.08) return 2;
        if (a > 0.04) return 1;
        return 0;
    }

    static boolean perspectiveAllowsFineQc(double equivalentDeg) {
        return Double.isFinite(equivalentDeg) && equivalentDeg < 14.0;
    }

    static String fineQcReason(double equivalentDeg) {
        if (!Double.isFinite(equivalentDeg)) return "perspective could not be verified from this photo";
        if (equivalentDeg >= 14.0) return "perspective distortion is too high for fine grading";
        return "";
    }

    static double findingPriority(int severity, double magnitude) {
        return severity * 100.0 + Math.abs(magnitude);
    }

    private QcExtendedMath() {}
}
