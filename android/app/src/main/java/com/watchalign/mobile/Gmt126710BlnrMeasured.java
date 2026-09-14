package com.watchalign.mobile;

/**
 * Normalized visual geometry for the Rolex GMT-Master II 126710BLNR.
 *
 * Marker centres and the round/triangle traces come from the first-party 2026 catalogue calibration.
 * The original automatic trace under-captured the long 6/9 applied-baton edges, so the baton body is
 * represented by the separately validated visual-master dimensions. This is image-derived QC
 * reference geometry, not Rolex factory CAD or a manufacturing tolerance.
 */
final class Gmt126710BlnrMeasured {
    static final String ID="126710BLNR-reference-geometry-v2";
    static final double DIAL_EDGE_R=1.0;
    static final double MARKER_CENTER_R=0.7904886;
    static final double BATON_CENTER_R=0.7977680;
    static final double TRI_CENTER_R=0.7843200;

    static final float[][] ROUND_OUTER={{-0.067988f,0.004086f},{-0.060003f,-0.040131f},{0.006452f,-0.035846f},{0.038089f,-0.060205f},{0.047677f,-0.038320f},{0.067018f,-0.031206f},{0.043555f,-0.008520f},{0.045159f,0.057582f},{0.002614f,0.057771f}};

    // Local marker coordinates: x=tangential, y=radial; +y points outward to the minute track.
    // The catalogue contour trace clipped the bright long edges of the 6/9 markers and produced a
    // visibly squat box. Use the validated applied-metal proportions instead: long radial body,
    // narrower tangential width. The same body is used for both 6 and 9 before perspective projection.
    static final float BATON_TANGENTIAL_HALF=0.047f;
    static final float BATON_RADIAL_HALF=0.124f;
    static final float[][] BATON_OUTER={
            {-BATON_TANGENTIAL_HALF,-BATON_RADIAL_HALF},
            { BATON_TANGENTIAL_HALF,-BATON_RADIAL_HALF},
            { BATON_TANGENTIAL_HALF, BATON_RADIAL_HALF},
            {-BATON_TANGENTIAL_HALF, BATON_RADIAL_HALF}
    };

    static final float[][] TRI_OUTER={{-0.086726f,0.098697f},{0.085841f,0.098697f},{0.000885f,-0.197395f}};
    private Gmt126710BlnrMeasured(){}
}
