package com.watchalign.mobile;

import android.graphics.Bitmap;

/**
 * Alpha8 release facade. The image pipeline remains the geometry-first alpha7
 * implementation, with the corrected OpenCV rotation convention supplied by
 * GeometryRegistration and additional regression tests in this release.
 */
public final class WatchAlignCoreV8 {
    public static final String CORE_VERSION = "1.3.0-alpha8";

    public static final class AnalysisResult {
        public final Bitmap annotated;
        public final Bitmap reference;
        public final Bitmap aligned;
        public final String report;
        public final double registrationConfidence;
        private final WatchAlignCoreV7.AnalysisResult delegate;

        AnalysisResult(WatchAlignCoreV7.AnalysisResult delegate) {
            this.delegate = delegate;
            this.annotated = delegate.annotated;
            this.reference = delegate.reference;
            this.aligned = delegate.aligned;
            this.registrationConfidence = delegate.registrationConfidence;
            this.report = delegate.report.replace("1.3.0-alpha7", CORE_VERSION);
        }

        public Bitmap overlay(float alpha) { return delegate.overlay(alpha); }
    }

    public static double referenceScore(Bitmap watch, Bitmap reference) {
        return WatchAlignCoreV7.referenceScore(watch, reference);
    }

    public static AnalysisResult analyse(Bitmap watch, Bitmap reference, String modelRef) {
        return new AnalysisResult(WatchAlignCoreV7.analyse(watch, reference, modelRef));
    }

    private WatchAlignCoreV8() {}
}
