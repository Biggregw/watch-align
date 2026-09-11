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

    static double localDateAxisOffsetDeg(double cx, double cy, double x, double y,
                                         int dateHour, double globalRotationDeg) {
        double measured = clockAngleDeg(cx, cy, x, y);
        double expected = hourAngleDeg(dateHour) + globalRotationDeg;
        return wrap180(measured - expected);
    }

    static double minuteTrackUnits(double angularOffsetDeg) {
        return angularOffsetDeg / 6.0;
    }

    static int orientationSeverity(double deg) {
        double a = Math.abs(deg);
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

    static int dateAxisSeverity(double deg) {
        double a = Math.abs(deg);
        if (a > 4.5) return 2;  // roughly three quarters of one minute-track division
        if (a > 2.0) return 1;
        return 0;
    }

    static int dateCenterSeverity(double xPct, double yPct) {
        double m = Math.max(Math.abs(xPct), Math.abs(yPct));
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
