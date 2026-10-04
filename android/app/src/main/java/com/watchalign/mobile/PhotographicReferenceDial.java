package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

/**
 * Front-on photographic GMT dial supplied by the user for the perspective-overlay proof.
 * Cropped to the dial edge and resized to 512x512. This is reference imagery only,
 * never a QC measurement source.
 */
final class PhotographicReferenceDial {
    static final int SIZE = 512;
    static final double CX = 256.0;
    static final double CY = 256.0;
    static final double R = 256.0;

    private static final String JPEG_B64 =
        "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";

    static Bitmap bitmap() {
        byte[] bytes = Base64.decode(JPEG_B64, Base64.DEFAULT);
        Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (b == null) throw new IllegalStateException("Reference dial image could not be decoded");
        return b.copy(Bitmap.Config.ARGB_8888, false);
    }

    private PhotographicReferenceDial() {}
}
