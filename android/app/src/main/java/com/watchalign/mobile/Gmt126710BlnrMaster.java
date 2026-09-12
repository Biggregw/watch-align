package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-visual-master-v2";

    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.925;
    static final double MARKER_CENTER_R = 0.755;

    // Applied marker outer body and inner lume references.
    static final double ROUND_OUTER_R = 0.061;
    static final double ROUND_LUME_R = 0.046;

    // 6/9 baton half dimensions: radial length, tangential width.
    static final double BATON_RADIAL_HALF = 0.092;
    static final double BATON_TANGENTIAL_HALF = 0.037;
    static final double BATON_LUME_RADIAL_HALF = 0.076;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.025;

    // 12 triangle. Genuine orientation is BASE OUTWARD, APEX INWARD.
    static final double TRI_CENTER_R = 0.770;
    static final double TRI_BASE_OUTWARD = 0.103;
    static final double TRI_APEX_INWARD = 0.108;
    static final double TRI_HALF_BASE = 0.079;
    static final double TRI_LUME_CENTER_R = 0.765;
    static final double TRI_LUME_BASE_OUTWARD = 0.079;
    static final double TRI_LUME_APEX_INWARD = 0.082;
    static final double TRI_LUME_HALF_BASE = 0.057;

    // Date aperture, excluding cyclops magnifier.
    static final double DATE_X_INNER = 0.535;
    static final double DATE_X_OUTER = 0.720;
    static final double DATE_Y_TOP = -0.076;
    static final double DATE_Y_BOTTOM = 0.076;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
