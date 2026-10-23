package com.watchalign.mobile;

import java.util.Map;

/**
 * Replica-QC-only promotion of validated shadow position readings.
 * Retains printed-minute-track sideways and legacy strict-genuine measurements.
 * Never changes original eligibility, rotation, the triangle, or calibration limits.
 */
final class PositionReferenceProduction {
    /** A conservative availability rule: four to six neighbours can produce unstable
     * reference shifts in the restricted-fixture perturbation checks; otherwise fall back. */
    static final int MIN_PRODUCTION_NEIGHBOURS = 7;
    private PositionReferenceProduction() {}

    static void apply(ClearlyOff.Result result, Map<Integer, PositionReferenceFit.Reading> readings,
                      String photoReason) {
        if(result == null || result.limits == null || result.limits.strictGenuine
                || photoReason != null || result.photoReason != null || readings == null
                || !Double.isFinite(result.rpx) || result.rpx <= 0) return;
        for(ClearlyOff.Marker marker : result.markers) {
            if(marker == null || marker.shape == ModelSpec.Shape.TRIANGLE
                    || !"outer metal".equals(marker.source)
                    || !WatchClassification.credible(marker)
                    || !Double.isFinite(marker.sidePct) || !Double.isFinite(marker.inoutPct)) continue;
            PositionReferenceFit.Reading reading = readings.get(marker.hour);
            if(reading == null || !reading.available
                    || reading.otherContributors < MIN_PRODUCTION_NEIGHBOURS) continue;
            double relative = reading.distancePctR();
            if(!Double.isFinite(relative) || relative < 0) continue;
            // Decide uses the unchanged owner's replica-QC limit and the original
            // minute-track sideways reading; a missing fit retains the old judgement.
            marker.relativePositionPct = relative;
            marker.relativePositionUsed = true;
            try {
                ClearlyOff.decide(marker, result.rpx, null, result.limits);
            } catch(Throwable ignored) {
                marker.relativePositionUsed = false;
                marker.relativePositionPct = Double.NaN;
                // Restore the original verdict from the unchanged components.
                ClearlyOff.decide(marker, result.rpx, null, result.limits);
            }
        }
    }
}
