package com.watchalign.mobile;

/**
 * Model-neutral pixel-space repeatability helpers.
 *
 * The mature GMT path established that a fine geometric reading should not be trusted merely
 * because the same marker is found again after a small resize.  The measurement itself should
 * move by no more than about one source pixel.  These helpers deliberately contain no QC or
 * model-family thresholds; they only convert normalized/angle spreads into physical pixel travel.
 */
final class MeasurementRepeatability {
    static final double MAX_SHIFT_PX = GmtTwelveLandmarkAnalyzer.MAX_RESAMPLE_SHIFT_PX;

    static boolean stable(double shiftPx){
        return Double.isFinite(shiftPx) && shiftPx<=MAX_SHIFT_PX+1e-9;
    }

    /** Spread of a width-normalized sideways measurement in pixels. */
    static double widthShiftPx(double min,double max,double widthPx){
        if(!Double.isFinite(min)||!Double.isFinite(max)||!(widthPx>0))return Double.NaN;
        return Math.abs(max-min)*widthPx;
    }

    /** Spread of a radius-normalized radial measurement in pixels. */
    static double radiusShiftPx(double min,double max,double radiusPx){
        if(!Double.isFinite(min)||!Double.isFinite(max)||!(radiusPx>0))return Double.NaN;
        return Math.abs(max-min)*radiusPx;
    }

    /** Tip/end travel caused by an angular spread, measured at the supplied lever arm. */
    static double angleShiftPx(double minDeg,double maxDeg,double leverPx){
        if(!Double.isFinite(minDeg)||!Double.isFinite(maxDeg)||!(leverPx>0))return Double.NaN;
        return Math.abs(Math.tan(Math.toRadians(maxDeg-minDeg))*leverPx);
    }

    private MeasurementRepeatability(){}
}
