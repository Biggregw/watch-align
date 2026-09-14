package com.watchalign.mobile;

/**
 * Visual 12 o'clock marker reference measured from a clean, front-on genuine 126710BLNR image.
 * Coordinates are normalized to the same dial-edge radius used by the four-point perspective ruler.
 * This is a photographic visual reference, not Rolex factory CAD or a manufacturing tolerance.
 */
final class Gmt126710BlnrTriangleReference {
    static final String ID = "126710BLNR-gen-front-on-v1";

    // Canonical dial coordinates: +x right, +y down, dial edge radius = 1.0.
    // Outer metal marker vertices measured against the local 59/60/01 minute track.
    static final float LEFT_X = -0.1250f;
    static final float LEFT_Y = -0.8490f;
    static final float RIGHT_X = 0.1250f;
    static final float RIGHT_Y = -0.8490f;
    static final float APEX_X = 0.0000f;
    static final float APEX_Y = -0.5450f;

    // Minute track visible extent used only for local relationship guides.
    static final float MINUTE_INNER_R = 0.9100f;
    static final float MINUTE_OUTER_R = 1.0000f;

    static final float WIDTH_R = RIGHT_X - LEFT_X;          // 25.0% of dial radius
    static final float HEIGHT_R = APEX_Y - LEFT_Y;          // 30.4% of dial radius
    static final float BASE_TO_TRACK_R = MINUTE_INNER_R - 0.8490f; // 6.1% of dial radius

    static float[][] vertices() {
        return new float[][] {
            {LEFT_X, LEFT_Y},
            {RIGHT_X, RIGHT_Y},
            {APEX_X, APEX_Y}
        };
    }

    private Gmt126710BlnrTriangleReference() {}
}
