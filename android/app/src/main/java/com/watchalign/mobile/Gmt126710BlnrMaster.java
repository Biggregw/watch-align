package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-visual-master-v5";

    // v5 geometry is measured, not hand-tuned: black-dial edge fitted with DialEdgeEllipseFit,
    // then each applied marker's white-gold surround segmented on (a) the official front-on
    // m126710blnr-0002 image and (b) an independent real photo. The two agree to ~0.005R.
    // All three marker types share one outer circle at ~0.905R (triangle 0.902, dots 0.904,
    // batons 0.908). v3/v4 drew every surround 25-35% undersized.
    // White lume references are the surround inset by its measured width, ~0.022R.

    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.925;

    // 6/9 batons (MARKER_CENTER_R is their centre radius). Measured 0.754-0.762.
    static final double MARKER_CENTER_R = 0.758;
    // Round markers: measured centres 0.814-0.819 (official), 0.808-0.818 (photo).
    static final double ROUND_CENTER_R = 0.816;

    // Round marker surround and lume radii. Measured surround 0.084-0.093.
    static final double ROUND_OUTER_R = 0.088;
    static final double ROUND_LUME_R = 0.066;

    // 6/9 baton half dimensions: radial length, tangential width. Measured 0.152-0.157 / 0.060-0.063.
    static final double BATON_RADIAL_HALF = 0.150;
    static final double BATON_TANGENTIAL_HALF = 0.060;
    static final double BATON_LUME_RADIAL_HALF = 0.128;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.038;

    // 12 triangle. Genuine orientation is BASE OUTWARD, APEX INWARD.
    // Measured surround: base 0.902 (both images), apex 0.599-0.603, half-base 0.120-0.126.
    static final double TRI_CENTER_R = 0.750;
    static final double TRI_BASE_OUTWARD = 0.152;
    static final double TRI_APEX_INWARD = 0.150;
    static final double TRI_HALF_BASE = 0.123;
    // Lume: surround inset by 0.022R about the triangle's incentre.
    static final double TRI_LUME_CENTER_R = 0.750;
    static final double TRI_LUME_BASE_OUTWARD = 0.130;
    static final double TRI_LUME_APEX_INWARD = 0.092;
    static final double TRI_LUME_HALF_BASE = 0.090;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
