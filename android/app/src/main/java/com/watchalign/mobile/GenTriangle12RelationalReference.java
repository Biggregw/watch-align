package com.watchalign.mobile;

/**
 * Visual QC reference range measured from multiple genuine 126710BLNR images.
 * These are image-derived genuine controls, not Rolex factory CAD or tolerances.
 */
public final class GenTriangle12RelationalReference {
    private GenTriangle12RelationalReference() {}

    // Keep the original nominal target for drawing/round-trip regression, but judge
    // pass/fail against the wider perspective-rectified genuine-control range.
    // Three genuine runs observed roughly 0.163, 0.169 and 0.190 BW.
    public static final float BASE_TO_60_INNER_OVER_BASE = 0.125f;
    public static final float BASE_TO_60_MIN = 0.10f;
    public static final float BASE_TO_60_MAX = 0.20f;
    public static final float BASE_TO_60_MEDIAN = 0.169f;

    // Apex-to-crown varies materially across genuine photography/rendering.
    public static final float APEX_TO_CROWN_OVER_BASE = 0.26f;
    public static final float APEX_TO_CROWN_MIN = 0.18f;
    public static final float APEX_TO_CROWN_MAX = 0.34f;
    public static final float APEX_TO_CROWN_MEDIAN = 0.26f;

    // Genuine controls have shown up to about 0.42 degrees after careful manual taps.
    // Treat <=0.50 degrees as observed genuine angular variation, not a defect by itself.
    public static final float ROTATION_OBSERVED_GEN_MAX_DEG = 0.50f;

    // Retained for shape diagnostics only, not pass/fail.
    public static final float HEIGHT_OVER_BASE = 1.4167f;
    public static final float MINUTE_STEP_DEG = 6f;

    static boolean baseGapInRange(float v){return v>=BASE_TO_60_MIN&&v<=BASE_TO_60_MAX;}
    static boolean crownGapInRange(float v){return v>=APEX_TO_CROWN_MIN&&v<=APEX_TO_CROWN_MAX;}
    static boolean rotationInObservedGenRange(float degrees){return Math.abs(degrees)<=ROTATION_OBSERVED_GEN_MAX_DEG;}
}
