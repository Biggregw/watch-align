package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-visual-master-v4";

    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.925;
    static final double MARKER_CENTER_R = 0.755;

    // Round applied markers were already fitting well in alpha28, so alpha29 leaves them unchanged.
    static final double ROUND_OUTER_R = 0.070;
    static final double ROUND_LUME_R = 0.049;

    // Alpha29 lengthens the 6/9 applied bodies substantially in the radial direction.
    // Width remains unchanged because the alpha28 screenshot showed the tangential width was already close.
    static final double BATON_RADIAL_HALF = 0.128;
    static final double BATON_TANGENTIAL_HALF = 0.044;
    static final double BATON_LUME_RADIAL_HALF = 0.083;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.026;

    // 12 triangle. Genuine orientation is BASE OUTWARD, APEX INWARD.
    // Alpha29 widens the base and extends the apex further inward while retaining the established pose.
    static final double TRI_CENTER_R = 0.738;
    static final double TRI_BASE_OUTWARD = 0.106;
    static final double TRI_APEX_INWARD = 0.190;
    static final double TRI_HALF_BASE = 0.096;
    static final double TRI_LUME_CENTER_R = 0.738;
    static final double TRI_LUME_BASE_OUTWARD = 0.079;
    static final double TRI_LUME_APEX_INWARD = 0.140;
    static final double TRI_LUME_HALF_BASE = 0.064;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
