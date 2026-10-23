package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Read-only adapter. Never feeds a production measurement, decision, or rendering path. */
final class PositionReferenceShadow {
    private PositionReferenceShadow() {}

    /** Empty means no shadow evidence; absent/invalid centres are omitted, gated ones unavailable. */
    static Map<Integer, PositionReferenceFit.Reading> measure(ClearlyOff.Result measured,
            ModelSpec model, ModelReference reference, String photoReason) {
        try {
            if (measured == null || model == null || photoReason != null || measured.photoReason != null
                    || !Double.isFinite(measured.rpx) || measured.rpx <= 0) return Collections.emptyMap();
            List<PositionReferenceFit.MarkerCentre> centres = new ArrayList<>();
            for (ClearlyOff.Marker marker : measured.markers) {
                if (marker == null || !"outer metal".equals(marker.source) || !finite(marker.cObs)) continue;
                ModelSpec.Marker spec = model.atHour(marker.hour);
                if (spec == null || spec.shape != marker.shape) continue;
                // cGen/yellow may contain the earlier leave-one-out ring adjustment. Use the
                // fixed model instead, in the SAME canonical R coordinates as MetalFit.cObs.
                double[][] polygon = ExpectedMarkerOutline.polygon(spec, reference, null);
                if (polygon == null || polygon.length < 3) continue;
                double[] fixed = ClearlyOff.mean(polygon);
                if (!finite(fixed)) continue;
                centres.add(new PositionReferenceFit.MarkerCentre(marker.hour,
                        PositionReferenceFit.Shape.valueOf(marker.shape.name()),
                        fixed[0], fixed[1], marker.cObs[0], marker.cObs[1],
                        WatchClassification.credible(marker)));
            }
            return PositionReferenceFit.measureAll(measured.rpx, centres);
        } catch (Throwable failure) {
            // Optional diagnostic only: even a helper failure must leave production intact.
            return Collections.emptyMap();
        }
    }

    private static boolean finite(double[] point) {
        return point != null && point.length >= 2
                && Double.isFinite(point[0]) && Double.isFinite(point[1]);
    }
}
