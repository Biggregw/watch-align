package com.watchalign.mobile;

/** Pure geometry helper for assisted dial-edge alignment. */
final class ManualSeedMath {
    private ManualSeedMath() {}

    /**
     * Build a dial pose seed from opposite points on the dial boundary at 12 and 6.
     * Returns {cx, cy, apparentRadiusAlong12to6, rollDeg}.
     */
    static double[] fromOppositeDialEdges(double x12, double y12, double x6, double y6) {
        double dx = x12 - x6;
        double dy = y12 - y6;
        double diameter = Math.hypot(dx, dy);
        if (!(diameter > 1.0) || !Double.isFinite(diameter)) return null;

        double cx = (x12 + x6) * 0.5;
        double cy = (y12 + y6) * 0.5;
        double radius = diameter * 0.5;

        double clockAngle = Math.toDegrees(Math.atan2(x12 - cx, -(y12 - cy)));
        while (clockAngle > 180.0) clockAngle -= 360.0;
        while (clockAngle <= -180.0) clockAngle += 360.0;

        return new double[]{cx, cy, radius, clockAngle};
    }
}
