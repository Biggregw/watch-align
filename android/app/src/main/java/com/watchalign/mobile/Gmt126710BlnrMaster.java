package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-measured-master-2026-09-26";

    // Geometry measured from the official front-on m126710blnr-0002 image and
    // cross-checked against an independent genuine-watch photo.
    static final double DIAL_EDGE_R = 1.000;

    // Normal minor-minute marks. MINUTE_TRACK_R is their measured inner end.
    // The common outer annulus is also useful for detecting the hour-position
    // minute marks without looking inward at the applied hour marker itself.
    static final double MINUTE_TRACK_R = 0.925;
    static final double MINUTE_TRACK_OUTER_R = 0.972;
    static final double HOUR_TICK_SAMPLE_R = 0.960;

    static final double MARKER_CENTER_R = 0.758;
    static final double ROUND_CENTER_R = 0.816;

    static final double ROUND_OUTER_R = 0.088;
    static final double ROUND_LUME_R = 0.066;

    static final double BATON_RADIAL_HALF = 0.150;
    static final double BATON_TANGENTIAL_HALF = 0.060;
    static final double BATON_LUME_RADIAL_HALF = 0.128;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.038;

    static final double TRI_CENTER_R = 0.750;
    static final double TRI_BASE_OUTWARD = 0.152;
    static final double TRI_APEX_INWARD = 0.150;
    static final double TRI_HALF_BASE = 0.123;
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

    /** Canonical minute position: exact 6 degree spacing around the genuine dial. */
    static double angleForMinute(int minute) {
        return Math.toRadians((minute % 60) * 6.0 - 90.0);
    }

    private Gmt126710BlnrMaster() {}
}
