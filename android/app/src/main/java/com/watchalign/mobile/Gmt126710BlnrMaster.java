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
    // detector run against a real, hand-held, upright 126710BLNR photo with a well-fit pose
    // (centre displacement 0.58% of dial radius, 95% confidence, held-out minute-track median
    // 0.68px at 100% inliers), not eyeballed from the rendered overlay. Round markers showed a
    // consistent outward radial offset across all 8 measured positions (1,2,4,5,7,8,10,11:
    // +3.23, +3.03, +2.73, +2.34, +3.21, +2.71, +2.12, +2.17 %R; mean +2.69%R, i.e. the v4 value
    // was undershooting by essentially the "~104-105%" gap noted from earlier visual testing).
    // 6/9 batons showed a much smaller +1.05/+1.16 %R offset (mean +1.11%R). This is a single-
    // photo measurement: consistent and coherent across 8 independent markers, but not yet
    // cross-validated against a second genuine photo the way MINUTE_TRACK_R was.
    static final double ROUND_CENTER_R = 0.749;
    static final double BATON_CENTER_R = 0.688;

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
    // on the genuine control (base 0.805-0.096, apex 0.555+0.154). The same real photo used above
    // measured the triangle's detection-frame centroid at 0.7455R (+1.65%R vs the v4 detection
    // datum of 0.729R). Because the centroid-to-anchor gap is a fixed property of the triangle's
    // shape (not something that should be re-derived per photo), the same +0.0165R shift is
    // applied to the visual anchor: 0.709+0.0165=0.7255, rounded to 0.726.
    static final double TRI_CENTER_R = 0.726;
    static final double TRI_BASE_OUTWARD = 0.096;
    static final double TRI_APEX_INWARD = 0.154;
    static final double TRI_HALF_BASE = 0.083;

    // Keep the lume triangle translated with the outer body rather than changing its shape:
    // preserves the v4 body-to-lume gap (0.709-0.707=0.002) applied to the new v5 anchor.
    static final double TRI_LUME_CENTER_R = 0.724;
    static final double TRI_LUME_BASE_OUTWARD = 0.071;
    static final double TRI_LUME_APEX_INWARD = 0.118;
    static final double TRI_LUME_HALF_BASE = 0.058;

    // Detection centroid is not the same point as the triangle's visual geometric anchor.
    // A separate genuine-derived centroid datum prevents QC from forcing the search ROI onto
    // the visual anchor and then measuring the wrong bright structure. v5: preserves the v4
    // centroid-to-anchor gap (0.020) applied to the new v5 anchor (0.726+0.020=0.746).
    static final double TRI_DETECTION_CENTER_R = 0.746;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle: 12=-90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
