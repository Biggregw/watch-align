package com.watchalign.mobile;

/**
 * Visual QC reference range measured from multiple genuine 126710BLNR images.
 * These are image-derived genuine controls, not Rolex factory CAD or tolerances.
 */
public final class GenTriangle12RelationalReference {
    private GenTriangle12RelationalReference() {}

    // Genuine controls measured after local perspective rectification.
    // Base-to-60 is stable around ~12.5% of triangle base width.
    public static final float BASE_TO_60_INNER_OVER_BASE = 0.125f;
    public static final float BASE_TO_60_MIN = 0.10f;
    public static final float BASE_TO_60_MAX = 0.15f;

    // Apex-to-crown varies materially across genuine photography/rendering.
    public static final float APEX_TO_CROWN_OVER_BASE = 0.26f;
    public static final float APEX_TO_CROWN_MIN = 0.18f;
    public static final float APEX_TO_CROWN_MAX = 0.34f;

    // Retained for shape diagnostics only, not pass/fail.
    public static final float HEIGHT_OVER_BASE = 1.4167f;

    public static final float MINUTE_STEP_DEG = 6f;

    static boolean baseGapInRange(float v){return v>=BASE_TO_60_MIN&&v<=BASE_TO_60_MAX;}
    static boolean crownGapInRange(float v){return v>=APEX_TO_CROWN_MIN&&v<=APEX_TO_CROWN_MAX;}
}
