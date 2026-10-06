package com.watchalign.mobile;

/**
 * Alpha92 overlay-only GMT master.
 *
 * Measured from the supplied bare genuine GMT dial after rectifying that reference
 * onto the exact 6-degree minute lattice and circular dial edge. This class is
 * intentionally separate from Gmt126710BlnrMaster so the frozen Alpha90 master and
 * its snapshot tests remain untouched.
 */
final class Alpha92GmtMaster {
    static final String ID="126710BLNR-bare-dial-master-2026-10-06";

    static final double DIAL_EDGE_R=1.000;
    static final double MINUTE_TRACK_R=0.9325;
    static final double MINUTE_TRACK_OUTER_R=0.9803;

    static final double BATON_CENTER_R=0.7551;
    static final double ROUND_CENTER_R=0.8128;
    static final double ROUND_OUTER_R=0.0915;

    static final double BATON_RADIAL_HALF=0.1525;
    static final double BATON_TANGENTIAL_HALF=0.0594;

    static final double TRI_APEX_R=0.5925;
    static final double TRI_BASE_R=0.9025;
    static final double TRI_HALF_BASE=0.12375;
    static final double TRI_AREA_CENTROID_R=0.7991666666666667;

    static double angleForHour(int hour){return Math.toRadians(hour*30.0-90.0);}
    static double angleForMinute(int minute){return Math.toRadians((minute%60)*6.0-90.0);}

    private Alpha92GmtMaster(){}
}
