package com.watchalign.mobile;

import com.watchalign.mobile.qc.QcModule;
import com.watchalign.mobile.qc.QcModuleResult;
import com.watchalign.mobile.qc.RawMeasurement;

import java.util.Arrays;
import java.util.Collections;

/**
 * Reusable QC-module wrapper around the existing production GMT 12-triangle measurement.
 *
 * <p>This class deliberately delegates to {@link Triangle12RelationalMetric} and does not
 * duplicate or alter its geometry. Classification remains outside this module.</p>
 */
public final class GmtTriangle12QcModule implements QcModule<GmtTriangle12QcModule.Input> {
    public static final String ID = "gmt.triangle12.relationship";

    public static final class Input {
        final PerspectiveMasterRenderer.Pose pose;
        final double[][] points;

        private Input(PerspectiveMasterRenderer.Pose pose, double[][] points) {
            if (pose == null) throw new IllegalArgumentException("pose must not be null");
            if (points == null || points.length != 5) {
                throw new IllegalArgumentException("exactly five measurement points are required");
            }
            this.pose = pose.copy();
            this.points = deepCopy(points);
        }

        public static Input rectified(PerspectiveMasterRenderer.Pose pose, double[][] points) {
            return new Input(pose, points);
        }

        private static double[][] deepCopy(double[][] source) {
            double[][] copy = new double[source.length][];
            for (int i = 0; i < source.length; i++) {
                if (source[i] == null || source[i].length < 2) {
                    throw new IllegalArgumentException("each point must contain x and y");
                }
                copy[i] = Arrays.copyOf(source[i], source[i].length);
            }
            return copy;
        }
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public QcModuleResult measure(Input input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");

        Triangle12RelationalMetric.Result result =
                Triangle12RelationalMetric.measureRectifiedRaw(input.pose, input.points);
        if (result == null || result.rawRectified == null) {
            return new QcModuleResult(
                    ID,
                    Collections.emptyList(),
                    QcModuleResult.Confidence.LOW,
                    Collections.singletonList("Production rectified triangle measurement unavailable"));
        }

        return new QcModuleResult(
                ID,
                Arrays.asList(
                        new RawMeasurement("base_to_60_over_base", result.rawRectified.baseTo60Ratio, "BW"),
                        new RawMeasurement("apex_to_crown_over_base", result.rawRectified.apexToCrownRatio, "BW"),
                        new RawMeasurement("rotation_deg", result.rawRectified.rotationDeg, "deg"),
                        new RawMeasurement("height_over_base", result.rawRectified.heightRatio, "BW"),
                        new RawMeasurement("lateral_px", result.rawRectified.lateralPx, "px")
                ),
                QcModuleResult.Confidence.HIGH,
                Collections.singletonList(
                        "Delegated unchanged to Triangle12RelationalMetric.measureRectifiedRaw"));
    }
}
