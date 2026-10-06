package com.watchalign.mobile;

/**
 * Fixed Watch Align visual master for Rolex GMT-Master II 126710BLNR.
 * Coordinates are normalized to dial radius. +x=3 o'clock, +y=6 o'clock.
 * This is a Watch Align calibrated inspection master, not Rolex factory CAD.
 */
final class Gmt126710BlnrMaster {
    static final String ID = "126710BLNR-bare-dial-master-2026-10-06";

    // Alpha91 geometry measured from the supplied bare genuine GMT dial after first
    // rectifying that reference onto the exact 6-degree minute lattice and circular
    // dial edge. The research proof is recorded in alpha91-overlay-registration-2026-10-06.md.
    static final double DIAL_EDGE_R = 1.000;

    // Normal minor-minute marks. MINUTE_TRACK_R is their measured inner end.
    // The common outer annulus is also useful for detecting the hour-position
    // minute marks without looking inward at the applied hour marker itself.
    static final double MINUTE_TRACK_R = 0.9325;
    static final double MINUTE_TRACK_OUTER_R = 0.9803;
    static final double HOUR_TICK_SAMPLE_R = 0.960;

    static final double MARKER_CENTER_R = 0.7551;
    static final double ROUND_CENTER_R = 0.8128;

    static final double ROUND_OUTER_R = 0.0915;
    static final double ROUND_LUME_R = 0.066;

    static final double BATON_RADIAL_HALF = 0.1525;
    static final double BATON_TANGENTIAL_HALF = 0.0594;
    static final double BATON_LUME_RADIAL_HALF = 0.128;
    static final double BATON_LUME_TANGENTIAL_HALF = 0.038;

    static final double TRI_CENTER_R = 0.7475;
    static final double TRI_BASE_OUTWARD = 0.1550;
    static final double TRI_APEX_INWARD = 0.1550;
    static final double TRI_HALF_BASE = 0.12375;
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
