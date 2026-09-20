package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-visual-master-v4";

    static final double DIAL_EDGE_R = 1.000;

    // Re-measured from a known-genuine front-on 126710BLNR control using the centreline of
    // the minor minute ticks. The previous 0.925 value sat near the outward part of the tick
    // band and made minute-track-first fitting shrink the entire master by about 3-5%.
    // A separate replica image independently reproduced the same ~0.891 tick-centre ratio,
    // but was not used to set the calibration value.
    static final double MINUTE_TRACK_R = 0.891;

    // Marker centres are deliberately shape-specific. A single 0.755R marker ring was found
    // to place the genuine round markers and especially the 6/9 batons too far outward.
    // These visual centres were measured against the corrected minute-track/dial-radius datum
    // on a known-genuine front-on 126710BLNR control, then checked against the real-photo overlays.
    static final double ROUND_CENTER_R = 0.722;
    static final double BATON_CENTER_R = 0.677;

    // Applied marker outer body and inner lume references.
    static final double ROUND_OUTER_R = 0.070;
    static final double ROUND_LUME_R = 0.049;

    // 6/9 baton half dimensions: radial length, tangential width.
    static final double BATON_RADIAL_HALF = 0.106;
    static final double BATON_TANGENTIAL_HALF = 0.044;
    static final double BATON_LUME_RADIAL_HALF = 0.077;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.026;

    // 12 triangle. Genuine orientation is BASE OUTWARD, APEX INWARD.
    // The visual anchor is solved from both independently measured endpoints on the genuine
    // control: outer base 0.805R - 0.096R = 0.709R, and inner apex 0.555R + 0.154R = 0.709R.
    static final double TRI_CENTER_R = 0.709;
    static final double TRI_BASE_OUTWARD = 0.096;
    static final double TRI_APEX_INWARD = 0.154;
    static final double TRI_HALF_BASE = 0.083;

    // Keep the lume triangle translated with the outer body rather than changing its shape.
    static final double TRI_LUME_CENTER_R = 0.707;
    static final double TRI_LUME_BASE_OUTWARD = 0.071;
    static final double TRI_LUME_APEX_INWARD = 0.118;
    static final double TRI_LUME_HALF_BASE = 0.058;

    // Detection centroid is not the same point as the triangle's visual geometric anchor.
    // A separate genuine-derived centroid datum prevents QC from forcing the search ROI onto
    // the visual anchor and then measuring the wrong bright structure.
    static final double TRI_DETECTION_CENTER_R = 0.729;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
