package com.watchalign.mobile;

/** Pure-Java similarity transform using the same image-coordinate convention as OpenCV warpAffine. */
public final class SimilarityTransform {
    public final double a, b, tx, ty;
    public final double scale;
    public final double rotationDeg;

    private SimilarityTransform(double a, double b, double tx, double ty, double scale, double rotationDeg) {
        this.a = a; this.b = b; this.tx = tx; this.ty = ty;
        this.scale = scale; this.rotationDeg = rotationDeg;
    }

    /**
     * Build a transform that maps sourceCentre exactly onto referenceCentre.
     * Positive rotation follows OpenCV getRotationMatrix2D image coordinates:
     * x' = a*x + b*y + tx; y' = -b*x + a*y + ty.
     */
    public static SimilarityTransform between(
            double srcX, double srcY,
            double refX, double refY,
            double scale, double rotationDeg) {
        if (!Double.isFinite(srcX) || !Double.isFinite(srcY)
                || !Double.isFinite(refX) || !Double.isFinite(refY)
                || !Double.isFinite(scale) || scale <= 0
                || !Double.isFinite(rotationDeg)) {
            throw new IllegalArgumentException("Invalid similarity transform geometry.");
        }
        double rad = Math.toRadians(rotationDeg);
        double a = scale * Math.cos(rad);
        double b = scale * Math.sin(rad);
        double tx = refX - a * srcX - b * srcY;
        double ty = refY + b * srcX - a * srcY;
        return new SimilarityTransform(a, b, tx, ty, scale, rotationDeg);
    }

    public double mapX(double x, double y) { return a * x + b * y + tx; }
    public double mapY(double x, double y) { return -b * x + a * y + ty; }

    public double mappedRadius(double radius) {
        if (!Double.isFinite(radius) || radius < 0) throw new IllegalArgumentException("Invalid radius.");
        return radius * scale;
    }

    public double[][] matrix2x3() {
        return new double[][]{{a, b, tx}, {-b, a, ty}};
    }
}
