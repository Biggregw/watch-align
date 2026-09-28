package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * Pixels from the original, full-resolution photo (alpha61). The app decodes a preview capped
 * at 1600 px; when the dial is small in the frame, the analysis re-reads just the dial from the
 * original through this, so the markers get two to three times the pixels.
 */
interface FullResSource {
    int width();
    int height();
    /** Region [x0,x1) x [y0,y1) of the original, decoded with power-of-two subsampling. */
    Bitmap region(int x0,int y0,int x1,int y1,int sampleSize);
}
