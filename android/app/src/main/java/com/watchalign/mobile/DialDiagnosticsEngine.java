package com.watchalign.mobile;

/**
 * Compatibility-preserving name for the surviving dial diagnostics implementation.
 * The implementation is generated from the former WatchAlignCoreV7 during the
 * architecture consolidation. Keeping diagnostics separate from the public core
 * avoids version-stacked core classes while preserving behaviour.
 */
final class DialDiagnosticsEngine {
    private DialDiagnosticsEngine() {}

    static double referenceScore(android.graphics.Bitmap watch, android.graphics.Bitmap reference) {
        return WatchAlignCoreV7.referenceScore(watch, reference);
    }

    static WatchAlignCoreV7.AnalysisResult analyse(android.graphics.Bitmap watch, android.graphics.Bitmap reference, String modelRef) {
        return WatchAlignCoreV7.analyse(watch, reference, modelRef);
    }
}
