package com.watchalign.mobile;

/**
 * Fixed Watch Align master geometry for Rolex GMT-Master II 126710BLNR.
 *
 * Coordinates are normalized to the detected dial boundary radius (dial edge = 1.0),
 * with +x toward 3 o'clock and +y toward 6 o'clock. This is a Watch Align inspection
 * master calibrated once from front-on genuine 126710BLNR imagery, not Rolex factory CAD.
 * Runtime photo analysis must project this fixed master into the photo; marker detections
 * must never move or resize these shapes.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-master-v1";

    static final double DIAL_EDGE_R = 1.000;
    static final double MINUTE_TRACK_R = 0.925;
    static final double MARKER_CENTER_R = 0.755;

    // Circular applied marker outside outline, normalized to dial radius.
    static final double ROUND_R = 0.061;

    // 6/9 baton half dimensions: radial length first, tangential width second.
    static final double BATON_RADIAL_HALF = 0.092;
    static final double BATON_TANGENTIAL_HALF = 0.037;

    // 12 triangle, expressed around its radial centre.
    static final double TRI_CENTER_R = 0.770;
    static final double TRI_OUTWARD = 0.120;
    static final double TRI_INWARD = 0.090;
    static final double TRI_HALF_BASE = 0.078;

    // Date aperture only, deliberately excluding cyclops magnifier.
    static final double DATE_X_INNER = 0.535;
    static final double DATE_X_OUTER = 0.720;
    static final double DATE_Y_TOP = -0.076;
    static final double DATE_Y_BOTTOM = 0.076;

    static boolean supports(String modelRef) {
        return modelRef != null && modelRef.toUpperCase().contains("126710BLNR");
    }

    /** Canonical angle in radians: 12= -90°, 3=0°, 6=90°, 9=180°. */
    static double angleForHour(int hour) {
        return Math.toRadians(hour * 30.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
