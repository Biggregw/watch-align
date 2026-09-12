package com.watchalign.mobile;

/**
 * Visual QC reference measured from the supplied front-on genuine 126710BLNR image.
 * This is not Rolex factory CAD or a factory tolerance.
 *
 * Pixel landmarks were taken from the same genuine source so the 12 marker is
 * referenced to its neighbouring minute track and the printed Rolex crown,
 * rather than only to an abstract dial radius.
 */
public final class GenTriangle12RelationalReference {
    private GenTriangle12RelationalReference() {}

    // Source-image local measurements around 12 o'clock, expressed relative
    // to the genuine triangle outer base width so they are scale independent.
    // Outer triangle: base width ~48 px, base y ~75 px, apex y ~143 px.
    // 60-minute inner tip ~69 px, printed crown top ~147 px.
    public static final float HEIGHT_OVER_BASE = 68f / 48f;              // ~1.4167
    public static final float BASE_TO_60_INNER_OVER_BASE = 6f / 48f;     // ~0.1250
    public static final float APEX_TO_CROWN_OVER_BASE = 4f / 48f;        // ~0.0833

    // Neighbouring 59/01 minute axes are +/-6 degrees from the 60 axis.
    public static final float MINUTE_STEP_DEG = 6f;
}
