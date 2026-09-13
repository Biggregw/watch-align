package com.watchalign.mobile.qc;

/**
 * Pure-Java plausibility guard for canonical modern GMT hour-marker observations.
 *
 * <p>This is intentionally not a Rolex tolerance model. It only rejects geometry that is
 * incompatible with the selected hour sector or marker ring and therefore indicates a bad
 * localisation / rectification result.</p>
 */
public final class GmtIndexPlausibility {
    private static final double MIN_RADIUS = 0.56;
    private static final double MAX_RADIUS = 0.96;
    private static final double MAX_HOUR_SECTOR_ERROR_DEG = 7.0;
    private static final double MAX_RING_DEVIATION = 0.13;
    private static final double MAX_TANGENTIAL_ABS = 0.12;
    private static final double MAX_BODY_ROTATION_DEG = 12.0;

    private GmtIndexPlausibility() {}

    public static boolean plausibleCanonicalPosition(int hour, double x, double y) {
        if (hour < 1 || hour > 12 || hour == 3 || !Double.isFinite(x) || !Double.isFinite(y)) return false;
        double radius = Math.hypot(x, y);
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) return false;
        double actual = clockAngleDeg(x, y);
        double expected = hour == 12 ? 0.0 : hour * 30.0;
        return Math.abs(wrap180(actual - expected)) <= MAX_HOUR_SECTOR_ERROR_DEG;
    }

    public static boolean plausibleAgainstRing(int hour, double x, double y, double expectedRadius) {
        if (!plausibleCanonicalPosition(hour, x, y) || !Double.isFinite(expectedRadius) || expectedRadius <= 0.0) return false;
        double expectedAngle = Math.toRadians(hour == 12 ? 0.0 : hour * 30.0);
        double radialX = Math.sin(expectedAngle);
        double radialY = -Math.cos(expectedAngle);
        double tangentX = Math.cos(expectedAngle);
        double tangentY = Math.sin(expectedAngle);
        double radial = x * radialX + y * radialY;
        double tangential = x * tangentX + y * tangentY;
        return Math.abs(radial - expectedRadius) <= MAX_RING_DEVIATION
                && Math.abs(tangential) <= MAX_TANGENTIAL_ABS;
    }

    public static boolean bodyRotationUsable(int hour, double rotationDeg, QcModuleResult.Confidence confidence) {
        return (hour == 6 || hour == 9)
                && confidence == QcModuleResult.Confidence.HIGH
                && Double.isFinite(rotationDeg)
                && Math.abs(rotationDeg) <= MAX_BODY_ROTATION_DEG;
    }

    static double clockAngleDeg(double x, double y) {
        double a = Math.toDegrees(Math.atan2(x, -y));
        return a < 0.0 ? a + 360.0 : a;
    }

    static double wrap180(double deg) {
        double x = deg % 360.0;
        if (x <= -180.0) x += 360.0;
        if (x > 180.0) x -= 360.0;
        return x;
    }
}
