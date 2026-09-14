package com.watchalign.mobile;

/**
 * One canonical image-derived geometry source for Rolex GMT-Master II 126710BLNR.
 *
 * IMPORTANT: every radial value in this class uses the SAME radius definition as the production
 * rectifier: radius 1.0 is the smaller / inner dial-side rehaut boundary selected by the global
 * boundary fit. Older catalogue-trace constants used a slightly different visual radius and must
 * not be mixed with these values, because that manufactured ~0.028 DR radial residuals.
 *
 * Angular relationships are mathematical. Body dimensions and radial locations are visual QC
 * references, not Rolex CAD or factory tolerances.
 */
final class Gmt126710BlnrMeasured {
    static final String ID="126710BLNR-inner-edge-reference-v4";
    static final double DIAL_EDGE_R=1.0;

    // Unified centre ring on the production inner-edge radius. Keep round plots and 6/9 on one
    // coherent radial system; individual QC reports deviations from this fixed reference.
    static final double MARKER_CENTER_R=Gmt126710BlnrMaster.MARKER_CENTER_R;
    static final double BATON_CENTER_R=Gmt126710BlnrMaster.MARKER_CENTER_R;
    static final double TRI_CENTER_R=Gmt126710BlnrMaster.TRI_CENTER_R;

    // Clean ideal round body. The overlay constructs a true circle from the master radius rather
    // than replaying a sparse photographed contour.
    static final float ROUND_RADIUS=(float)Gmt126710BlnrMaster.ROUND_OUTER_R;

    // Local marker coordinates: x=tangential, y=radial; +y points outward to the minute track.
    static final float BATON_TANGENTIAL_HALF=(float)Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF;
    static final float BATON_RADIAL_HALF=(float)Gmt126710BlnrMaster.BATON_RADIAL_HALF;
    static final float[][] BATON_OUTER={
            {-BATON_TANGENTIAL_HALF,-BATON_RADIAL_HALF},
            { BATON_TANGENTIAL_HALF,-BATON_RADIAL_HALF},
            { BATON_TANGENTIAL_HALF, BATON_RADIAL_HALF},
            {-BATON_TANGENTIAL_HALF, BATON_RADIAL_HALF}
    };

    // 12 o'clock OUTER APPLIED-METAL triangle. The old catalogue trace was much too tall and used
    // the obsolete radius convention. Base is outward, apex inward.
    static final float[][] TRI_OUTER={
            {-(float)Gmt126710BlnrMaster.TRI_HALF_BASE,(float)Gmt126710BlnrMaster.TRI_BASE_OUTWARD},
            { (float)Gmt126710BlnrMaster.TRI_HALF_BASE,(float)Gmt126710BlnrMaster.TRI_BASE_OUTWARD},
            {0f,-(float)Gmt126710BlnrMaster.TRI_APEX_INWARD}
    };

    // 3 o'clock date aperture. Its tangential midpoint is constrained exactly to the 15-minute axis.
    // Radial location and body dimensions remain image-calibrated and will be refined from the
    // genuine-control set; they are deliberately kept in this one radius convention.
    static final double DATE_CENTER_R=0.690;
    static final float DATE_TANGENTIAL_HALF=0.081f;
    static final float DATE_RADIAL_HALF=0.123f;
    static final float[][] DATE_APERTURE_OUTER={
            {-DATE_TANGENTIAL_HALF,-DATE_RADIAL_HALF},
            { DATE_TANGENTIAL_HALF,-DATE_RADIAL_HALF},
            { DATE_TANGENTIAL_HALF, DATE_RADIAL_HALF},
            {-DATE_TANGENTIAL_HALF, DATE_RADIAL_HALF}
    };

    private Gmt126710BlnrMeasured(){}
}
