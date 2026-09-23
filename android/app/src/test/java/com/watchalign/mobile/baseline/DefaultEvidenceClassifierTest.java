package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.watchalign.mobile.qc.QcModuleResult;

import org.junit.Test;

public class DefaultEvidenceClassifierTest {
    private static final DefaultEvidenceClassifier CLASSIFIER = new DefaultEvidenceClassifier();
    private static final MetricKey KEY = new MetricKey("h12", "apex_radial");

    private static ReferenceComparison comparisonWithMadMultiples(double observed, double median, double mad,
                                                                    ValidationStatus status) {
        MetricBaseline baseline = new MetricBaseline(5, median, mad, median - 0.01, median + 0.01, null, 0.0, status);
        return new ReferenceComparison(KEY, observed, baseline);
    }

    @Test public void rejectStatusAlwaysNone() {
        ReferenceComparison comparison = comparisonWithMadMultiples(0.60, 0.55, 0.003, ValidationStatus.REJECT);
        Evidence evidence = CLASSIFIER.classify(comparison, ValidationStatus.REJECT, QcModuleResult.Confidence.HIGH);
        assertEquals(EvidenceStrength.NONE, evidence.strength());
    }

    @Test public void degenerateMadCapsWeakEvenWithLargeDeviation() {
        // mad below MIN_MAD_FOR_NORMALISATION -> madMultiples() is null -> WEAK regardless of magnitude.
        ReferenceComparison comparison = comparisonWithMadMultiples(0.60, 0.55, 1e-9, ValidationStatus.SUPPORTED);
        assertNull(comparison.madMultiples());
        Evidence evidence = CLASSIFIER.classify(comparison, ValidationStatus.SUPPORTED, QcModuleResult.Confidence.HIGH);
        assertEquals(EvidenceStrength.WEAK, evidence.strength());
    }

    @Test public void lowDetectorConfidenceCapsWeak() {
        // 5 MAD-multiples deviation would otherwise be STRONG.
        ReferenceComparison comparison = comparisonWithMadMultiples(0.60, 0.55, 0.01, ValidationStatus.SUPPORTED);
        Evidence evidence = CLASSIFIER.classify(comparison, ValidationStatus.SUPPORTED, QcModuleResult.Confidence.LOW);
        assertEquals(EvidenceStrength.WEAK, evidence.strength());
    }

    @Test public void diagnosticOnlyCapsWeak() {
        ReferenceComparison comparison = comparisonWithMadMultiples(0.60, 0.55, 0.01, ValidationStatus.DIAGNOSTIC_ONLY);
        Evidence evidence = CLASSIFIER.classify(comparison, ValidationStatus.DIAGNOSTIC_ONLY, QcModuleResult.Confidence.HIGH);
        assertEquals(EvidenceStrength.WEAK, evidence.strength());
    }

    @Test public void magnitudeBandsForSupportedWithAdequateConfidence() {
        // (0.60 - 0.55) / 0.01 = 5.0 mad-multiples -> STRONG.
        ReferenceComparison strong = comparisonWithMadMultiples(0.60, 0.55, 0.01, ValidationStatus.SUPPORTED);
        assertEquals(EvidenceStrength.STRONG,
                CLASSIFIER.classify(strong, ValidationStatus.SUPPORTED, QcModuleResult.Confidence.MEDIUM).strength());

        // (0.552 - 0.55) / 0.01 = 0.2 mad-multiples -> WEAK.
        ReferenceComparison weak = comparisonWithMadMultiples(0.552, 0.55, 0.01, ValidationStatus.SUPPORTED);
        assertEquals(EvidenceStrength.WEAK,
                CLASSIFIER.classify(weak, ValidationStatus.SUPPORTED, QcModuleResult.Confidence.MEDIUM).strength());

        // (0.5620 - 0.55) / 0.01 = 1.2 mad-multiples -> MODERATE.
        ReferenceComparison moderate = comparisonWithMadMultiples(0.5620, 0.55, 0.01, ValidationStatus.SUPPORTED);
        assertEquals(EvidenceStrength.MODERATE,
                CLASSIFIER.classify(moderate, ValidationStatus.SUPPORTED, QcModuleResult.Confidence.MEDIUM).strength());
    }

    @Test public void promisingStatusUsesSameBandingAsSupported() {
        ReferenceComparison strong = comparisonWithMadMultiples(0.60, 0.55, 0.01, ValidationStatus.PROMISING);
        assertEquals(EvidenceStrength.STRONG,
                CLASSIFIER.classify(strong, ValidationStatus.PROMISING, QcModuleResult.Confidence.HIGH).strength());
    }

    @Test public void rejectsNullArguments() {
        ReferenceComparison comparison = comparisonWithMadMultiples(0.60, 0.55, 0.01, ValidationStatus.SUPPORTED);
        try { CLASSIFIER.classify(null, ValidationStatus.SUPPORTED, QcModuleResult.Confidence.HIGH); fail("expected"); }
        catch (IllegalArgumentException expected) { }
        try { CLASSIFIER.classify(comparison, null, QcModuleResult.Confidence.HIGH); fail("expected"); }
        catch (IllegalArgumentException expected) { }
        try { CLASSIFIER.classify(comparison, ValidationStatus.SUPPORTED, null); fail("expected"); }
        catch (IllegalArgumentException expected) { }
    }
}
