package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-visual-master-v3";

    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.925;
    static final double MARKER_CENTER_R = 0.755;

    // Applied marker outer body and inner lume references.
    // Alpha28 enlarges the outer body to trace the applied white-gold surround rather than the lume only.
    static final double ROUND_OUTER_R = 0.070;
    static final double ROUND_LUME_R = 0.049;

    // 6/9 baton half dimensions: radial length, tangential width.
    static final double BATON_RADIAL_HALF = 0.106;
    static final double BATON_TANGENTIAL_HALF = 0.044;
    static final double BATON_LUME_RADIAL_HALF = 0.077;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.026;

    // 12 triangle. Genuine orientation is BASE OUTWARD, APEX INWARD.
    // Alpha28 shifts the body slightly inward and extends the apex so the outline traces the full applied marker.
    static final double TRI_CENTER_R = 0.748;
    static final double TRI_BASE_OUTWARD = 0.096;
    static final double TRI_APEX_INWARD = 0.154;
    static final double TRI_HALF_BASE = 0.083;
    static final double TRI_LUME_CENTER_R = 0.746;
    static final double TRI_LUME_BASE_OUTWARD = 0.071;
    static final double TRI_LUME_APEX_INWARD = 0.118;
    static final double TRI_LUME_HALF_BASE = 0.058;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
