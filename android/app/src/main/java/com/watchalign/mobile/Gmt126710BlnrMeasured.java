package com.watchalign.mobile;

/**
 * Normalized visual geometry measured from the first-party Rolex 126710BLNR
 * 2026 catalogue image by calibrate_gmt_master.py. This is image-derived QC
 * reference geometry, not Rolex factory CAD or a manufacturing tolerance.
 */
final class Gmt126710BlnrMeasured {
    static final String ID="126710BLNR-official-trace-v1";
    static final double DIAL_EDGE_R=1.0;
    static final double MARKER_CENTER_R=0.7904886;
    static final double BATON_CENTER_R=0.7977680;
    static final double TRI_CENTER_R=0.7843200;
    static final float[][] ROUND_OUTER={{-0.067988f,0.004086f},{-0.060003f,-0.040131f},{0.006452f,-0.035846f},{0.038089f,-0.060205f},{0.047677f,-0.038320f},{0.067018f,-0.031206f},{0.043555f,-0.008520f},{0.045159f,0.057582f},{0.002614f,0.057771f}};
    static final float[][] BATON_OUTER={{-0.071566f,-0.113824f},{0.071566f,-0.113824f},{0.071566f,0.113824f},{-0.071566f,0.113824f}};
    // Local 12-marker coordinates: x=tangential, y=radial; +y points outward to the minute track.
    static final float[][] TRI_OUTER={{-0.086726f,0.098697f},{0.085841f,0.098697f},{0.000885f,-0.197395f}};
    private Gmt126710BlnrMeasured(){}
}
