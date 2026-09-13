package com.watchalign.mobile.qc;

import java.util.Objects;

/**
 * Brand/model-agnostic numeric output from a QC measurement module.
 *
 * <p>This class deliberately contains no pass/fail or genuine/replica interpretation. A later
 * assessment layer may compare the raw value with model-specific calibration data.</p>
 */
public final class RawMeasurement {
    private final String id;
    private final double value;
    private final String unit;

    public RawMeasurement(String id, double value, String unit) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("measurement id must not be blank");
        }
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("measurement value must be finite");
        }
        this.id = id;
        this.value = value;
        this.unit = unit == null ? "" : unit;
    }

    public String id() {
        return id;
    }

    public double value() {
        return value;
    }

    public String unit() {
        return unit;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RawMeasurement)) return false;
        RawMeasurement that = (RawMeasurement) other;
        return Double.compare(that.value, value) == 0
                && id.equals(that.id)
                && unit.equals(that.unit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, value, unit);
    }

    @Override
    public String toString() {
        return id + "=" + value + (unit.isEmpty() ? "" : " " + unit);
    }
}
