package com.watchalign.mobile;

/**
 * Visual QC reference data measured from genuine modern 126710-family GMT images.
 * These are image-derived genuine controls, not Rolex factory CAD or manufacturing tolerances.
 */
public final class GenTriangle12RelationalReference {
    private GenTriangle12RelationalReference() {}

    /** Number of independent genuine watches in the corrected interim control set. */
    public static final int CORRECTED_CONTROL_COUNT = 4;

    // Keep the nominal target used to draw the projected reference geometry.
    public static final float BASE_TO_60_INNER_OVER_BASE = 0.125f;

    // Legacy/frozen classification band. Do not narrow classification from the small pilot population.
    public static final float BASE_TO_60_MIN = 0.10f;
    public static final float BASE_TO_60_MAX = 0.20f;

    // Corrected genuine pilot observations from four independent modern GMT controls:
    // 0.114754, 0.120690, 0.125000 and 0.164000 BW.
    // These values drive explanatory UI only and do not change frozen classification behaviour.
    public static final float BASE_TO_60_PILOT_MIN = 0.114754f;
    public static final float BASE_TO_60_PILOT_MAX = 0.164000f;
    public static final float BASE_TO_60_MEDIAN = 0.123f;

    // Apex-to-crown frozen classification band. Do not present this as the four-watch pilot range.
    public static final float APEX_TO_CROWN_OVER_BASE = 0.26f;
    public static final float APEX_TO_CROWN_MIN = 0.18f;
    public static final float APEX_TO_CROWN_MAX = 0.34f;
    public static final float APEX_TO_CROWN_MEDIAN = 0.237f;

    // Genuine controls have shown up to about 0.42 degrees after careful manual taps.
    // Treat <=0.50 degrees as the current observed angular envelope, not a factory tolerance.
    public static final float ROTATION_OBSERVED_GEN_MAX_DEG = 0.50f;

    // Retained for shape diagnostics only, not pass/fail.
    public static final float HEIGHT_OVER_BASE = 1.4167f;
    public static final float MINUTE_STEP_DEG = 6f;

    static boolean baseGapInRange(float v){return v>=BASE_TO_60_MIN&&v<=BASE_TO_60_MAX;}
    static boolean baseGapInPilotObserved(float v){return v>=BASE_TO_60_PILOT_MIN&&v<=BASE_TO_60_PILOT_MAX;}
    static boolean crownGapInRange(float v){return v>=APEX_TO_CROWN_MIN&&v<=APEX_TO_CROWN_MAX;}
    static boolean rotationInObservedGenRange(float degrees){return Math.abs(degrees)<=ROTATION_OBSERVED_GEN_MAX_DEG;}
}
