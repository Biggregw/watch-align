package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-visual-master-v5";

    static final double DIAL_EDGE_R = 1.000;

    // Re-measured from a known-genuine front-on 126710BLNR control using the centreline of
    // the minor minute ticks. The previous 0.925 value sat near the outward part of the tick
    // band and made minute-track-first fitting shrink the entire master by about 3-5%.
    // A separate replica image independently reproduced the same ~0.891 tick-centre ratio,
    // but was not used to set the calibration value.
    static final double MINUTE_TRACK_R = 0.891;

    // Marker centre radii, v5. Re-measured from GmtMarkerQcRepair's own accurate projected-ROI
    // detector run against the first-party Rolex catalogue image (media.rolex.com, the same
    // fixture GenuineOfficialImageValidationTest uses), via OfficialMarkerCalibrationTest --
    // deliberately never a user-submitted or market-sourced photo, since an earlier attempt to
    // calibrate from a user photo turned out to be sourced from a replica and had to be reverted.
    // Round markers (1,2,4,5,7,8,10,11) measured +3.493, -0.201, +2.773, +1.888, +2.554, +2.880,
    // +3.249, +3.221 %R. Marker 2's reading is a clear outlier against the tight cluster of the
    // other seven (an isolated near-zero value ~2 points from its nearest neighbour, most likely
    // a reflection/highlight artifact on this single catalogue photo rather than a real
    // manufacturing asymmetry) and is excluded; the mean of the remaining seven is +2.865%R.
    // 6/9 batons measured +0.877/+1.208%R (mean +1.043%R).
    static final double ROUND_CENTER_R = 0.751;
    static final double BATON_CENTER_R = 0.687;

    // Applied marker outer body and inner lume references. Unchanged in v5: the evidence above is
    // for marker CENTRE position only, not marker size or shape.
    static final double ROUND_OUTER_R = 0.070;
    static final double ROUND_LUME_R = 0.049;

    // 6/9 baton half dimensions: radial length, tangential width.
    static final double BATON_RADIAL_HALF = 0.106;
    static final double BATON_TANGENTIAL_HALF = 0.044;
    static final double BATON_LUME_RADIAL_HALF = 0.077;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.026;

    // 12 triangle, v5. The v4 anchor (0.709) was solved from two independently measured endpoints
    // on the genuine control (base 0.805-0.096, apex 0.555+0.154). The same official catalogue
    // image measured the triangle's detection-frame centroid at +0.983%R vs the v4 detection
    // datum (0.729). Because the centroid-to-anchor gap is a fixed property of the triangle's
    // shape (not something to re-derive per photo), the same +0.00983R shift is applied to the
    // visual anchor: 0.709+0.00983=0.71883, rounded to 0.719.
    static final double TRI_CENTER_R = 0.719;
    static final double TRI_BASE_OUTWARD = 0.096;
    static final double TRI_APEX_INWARD = 0.154;
    static final double TRI_HALF_BASE = 0.083;

    // Keep the lume triangle translated with the outer body rather than changing its shape:
    // preserves the v4 body-to-lume gap (0.709-0.707=0.002) applied to the new v5 anchor.
    static final double TRI_LUME_CENTER_R = 0.717;
    static final double TRI_LUME_BASE_OUTWARD = 0.071;
    static final double TRI_LUME_APEX_INWARD = 0.118;
    static final double TRI_LUME_HALF_BASE = 0.058;

    // Detection centroid is not the same point as the triangle's visual geometric anchor.
    // A separate genuine-derived centroid datum prevents QC from forcing the search ROI onto
    // the visual anchor and then measuring the wrong bright structure. v5: preserves the v4
    // centroid-to-anchor gap (0.020) applied to the new v5 anchor (0.719+0.020=0.739).
    static final double TRI_DETECTION_CENTER_R = 0.739;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
