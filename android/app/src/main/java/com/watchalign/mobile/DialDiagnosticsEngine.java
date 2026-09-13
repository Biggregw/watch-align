package com.watchalign.mobile;

/**
 * Compatibility-preserving name for the surviving dial diagnostics implementation.
 * The implementation is generated from the former WatchAlignCoreV7 during the
 * architecture consolidation. Keeping diagnostics separate from the public core
 * avoids version-stacked core classes while preserving behaviour.
 */
final class DialDiagnosticsEngine {
    static final class Result {
        final android.graphics.Bitmap annotated;
        final String report;
        Result(android.graphics.Bitmap annotated,String report){this.annotated=annotated;this.report=report;}
    }
    private DialDiagnosticsEngine() {}

    static double referenceScore(android.graphics.Bitmap watch, android.graphics.Bitmap reference) {
        return DialAnalysisEngine.referenceScore(watch, reference);
    }

    static Result analyse(android.graphics.Bitmap watch, android.graphics.Bitmap reference, String modelRef) {
        DialAnalysisEngine.AnalysisResult result=DialAnalysisEngine.analyse(watch, reference, modelRef);
        return new Result(result.annotated,result.report);
    }
}
