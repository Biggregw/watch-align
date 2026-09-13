package com.watchalign.mobile;

/**
 * Visual 12 o'clock marker reference calibrated from the current corrected genuine GMT pilot controls.
 * Coordinates are normalized to the same dial-edge radius used by the four-point perspective ruler.
 * This is an image-derived visual reference, not Rolex factory CAD or a manufacturing tolerance.
 */
final class Gmt126710BlnrTriangleReference {
    static final String ID = "126710GMT-corrected-pilot-v2";

    // Canonical dial coordinates: +x right, +y down, dial edge radius = 1.0.
    // Width is retained at 25.0% of dial radius. The radial placement is calibrated so the
    // projected base-to-60 relationship is the corrected genuine pilot median (0.123 BW).
    static final float LEFT_X = -0.1250f;
    static final float RIGHT_X = 0.1250f;
    static final float WIDTH_R = RIGHT_X - LEFT_X;

    static final float MINUTE_INNER_R = 0.9100f;
    static final float MINUTE_OUTER_R = 1.0000f;
    static final float CALIBRATED_BASE_TO_60_OVER_BASE = 0.123f;
    static final float CALIBRATED_HEIGHT_OVER_BASE = 1.246f;

    // With +y down and the 60-minute inner tip at y=-0.910, an inward triangle base has a
    // less-negative y. These coordinates therefore reproduce the pilot median relationship.
    static final float LEFT_Y = -(MINUTE_INNER_R - WIDTH_R * CALIBRATED_BASE_TO_60_OVER_BASE);
    static final float RIGHT_Y = LEFT_Y;
    static final float APEX_X = 0.0000f;
    static final float APEX_Y = LEFT_Y + WIDTH_R * CALIBRATED_HEIGHT_OVER_BASE;

    static final float HEIGHT_R = APEX_Y - LEFT_Y;
    static final float BASE_TO_TRACK_R = MINUTE_INNER_R + LEFT_Y;

    static float[][] vertices() {
        return new float[][] {
            {LEFT_X, LEFT_Y},
            {RIGHT_X, RIGHT_Y},
            {APEX_X, APEX_Y}
        };
    }

    private Gmt126710BlnrTriangleReference() {}
}
