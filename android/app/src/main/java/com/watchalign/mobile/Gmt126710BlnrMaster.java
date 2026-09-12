package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 *
 * v5 was rebuilt against the official Rolex 126710BLNR front view and
 * cross-checked against multiple genuine front-on dealer photographs.
 * It is a calibrated visual reference, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-reference-calibrated-v5";

    // Canonical dial geometry. The overlay is fitted to these stable rings first.
    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.924;
    static final double MINUTE_TICK_INNER_R = 0.884;
    static final double MINUTE_TICK_OUTER_R = 0.928;

    // Genuine front-on references place the applied-marker centres at ~75% dial radius.
    static final double MARKER_CENTER_R = 0.748;

    // Circular applied markers. The previous master made the lume disc too small.
    // v5 uses a thinner metal surround matching the genuine reference proportions.
    static final double ROUND_OUTER_R = 0.068;
    static final double ROUND_LUME_R = 0.053;

    // 6 / 9 applied batons. Independent body/lume dimensions preserve the genuine
    // thin metal border instead of the undersized lume rectangle used previously.
    static final double BATON_RADIAL_HALF = 0.118;
    static final double BATON_TANGENTIAL_HALF = 0.042;
    static final double BATON_LUME_RADIAL_HALF = 0.101;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.031;

    // 12 o'clock applied triangle. BASE OUTWARD, APEX INWARD.
    // The alpha29/32 triangle was intentionally over-extended during manual tuning;
    // v5 returns to proportions measured from genuine front-on reference imagery.
    static final double TRI_CENTER_R = 0.748;
    static final double TRI_BASE_OUTWARD = 0.090;
    static final double TRI_APEX_INWARD = 0.158;
    static final double TRI_HALF_BASE = 0.083;

    static final double TRI_LUME_CENTER_R = 0.748;
    static final double TRI_LUME_BASE_OUTWARD = 0.071;
    static final double TRI_LUME_APEX_INWARD = 0.132;
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
