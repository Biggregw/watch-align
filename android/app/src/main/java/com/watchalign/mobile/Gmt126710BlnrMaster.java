package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 *
 * v6 keeps the official/genuine-reference calibration from v5, then applies a
 * conservative correction from the alpha34 perspective-aligned QC validation:
 * marker centres slightly farther outward, fuller round applied-marker bodies,
 * broader 6/9 bodies, and a shorter/wider 12 triangle closer to the observed
 * applied metal outline. It remains a calibrated visual reference, not Rolex CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-reference-calibrated-v6";

    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.924;
    static final double MINUTE_TICK_INNER_R = 0.884;
    static final double MINUTE_TICK_OUTER_R = 0.928;

    // Alpha34 validation showed the v5 plot ring landing fractionally inward.
    static final double MARKER_CENTER_R = 0.762;

    // Applied round plots: v6 traces the visible metal body more closely rather
    // than sitting inside it, while retaining a separate inner lume reference.
    static final double ROUND_OUTER_R = 0.075;
    static final double ROUND_LUME_R = 0.057;

    // 6 / 9 applied batons. Slightly fuller than v5 in both radial length and width.
    static final double BATON_RADIAL_HALF = 0.124;
    static final double BATON_TANGENTIAL_HALF = 0.047;
    static final double BATON_LUME_RADIAL_HALF = 0.106;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.034;

    // 12 o'clock applied triangle. BASE OUTWARD, APEX INWARD.
    // v6 is wider at the base and less over-extended toward the pinion than v5.
    static final double TRI_CENTER_R = 0.758;
    static final double TRI_BASE_OUTWARD = 0.083;
    static final double TRI_APEX_INWARD = 0.138;
    static final double TRI_HALF_BASE = 0.094;

    static final double TRI_LUME_CENTER_R = 0.758;
    static final double TRI_LUME_BASE_OUTWARD = 0.065;
    static final double TRI_LUME_APEX_INWARD = 0.114;
    static final double TRI_LUME_HALF_BASE = 0.071;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
