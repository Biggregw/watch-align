package com.watchalign.mobile;

final class QcExtendedMath {
    static double wrap90(double deg) {
        double x = deg % 180.0;
        if (x <= -90.0) x += 180.0;
        if (x > 90.0) x -= 180.0;
        return x;
    }

    static double smallestAxisError(double measuredAxisDeg, double expectedAxisDeg) {
        return wrap90(measuredAxisDeg - expectedAxisDeg);
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

    static int dateCenterSeverity(double xPct, double yPct) {
        double m = Math.max(Math.abs(xPct), Math.abs(yPct));
        if (m > 12.0) return 2;
        if (m > 6.0) return 1;
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
