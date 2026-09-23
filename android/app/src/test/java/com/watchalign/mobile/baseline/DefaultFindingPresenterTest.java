package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.watchalign.mobile.qc.QcModuleResult;

import org.junit.Test;

public class DefaultFindingPresenterTest {
    private static final DefaultFindingPresenter PRESENTER = new DefaultFindingPresenter();
    private static final MetricKey KEY = new MetricKey("h12", "apex_radial");

    private static Evidence evidenceOf(ValidationStatus status, EvidenceStrength strength,
                                        QcModuleResult.Confidence confidence, double observed) {
        MetricBaseline baseline = new MetricBaseline(5, 0.5512, 0.0026, 0.5493, 0.5643, null, 0.0, status);
        ReferenceComparison comparison = new ReferenceComparison(KEY, observed, baseline);
        return new Evidence(comparison, status, confidence, strength);
    }

    @Test public void lowDetectorConfidenceAlwaysDetectorConfidenceInsufficient() {
        // Even a SUPPORTED metric with STRONG-looking evidence must not be shown as a deviation
        // if the underlying measurement itself was not trusted.
        Evidence evidence = evidenceOf(ValidationStatus.SUPPORTED, EvidenceStrength.STRONG,
                QcModuleResult.Confidence.LOW, 0.60);
        UserFacingFinding finding = PRESENTER.present(evidence);
        assertTrue(finding instanceof UserFacingFinding.DetectorConfidenceInsufficient);
    }

    @Test public void supportedWithModerateOrStrongEvidenceIsMeasurableDeviation() {
        Evidence moderate = evidenceOf(ValidationStatus.SUPPORTED, EvidenceStrength.MODERATE,
                QcModuleResult.Confidence.HIGH, 0.5620);
        assertTrue(PRESENTER.present(moderate) instanceof UserFacingFinding.MeasurableDeviation);

        Evidence strong = evidenceOf(ValidationStatus.SUPPORTED, EvidenceStrength.STRONG,
                QcModuleResult.Confidence.HIGH, 0.60);
        assertTrue(PRESENTER.present(strong) instanceof UserFacingFinding.MeasurableDeviation);
    }

    @Test public void supportedWithWeakEvidenceIsInconclusive() {
        Evidence evidence = evidenceOf(ValidationStatus.SUPPORTED, EvidenceStrength.WEAK,
                QcModuleResult.Confidence.HIGH, 0.5520);
        assertTrue(PRESENTER.present(evidence) instanceof UserFacingFinding.Inconclusive);
    }

    @Test public void promisingStatusIsInconclusiveEvenWithStrongEvidence() {
        Evidence evidence = evidenceOf(ValidationStatus.PROMISING, EvidenceStrength.STRONG,
                QcModuleResult.Confidence.HIGH, 0.60);
        UserFacingFinding finding = PRESENTER.present(evidence);
        assertTrue(finding instanceof UserFacingFinding.Inconclusive);
    }

    @Test public void diagnosticOnlyStatusIsInconclusive() {
        Evidence evidence = evidenceOf(ValidationStatus.DIAGNOSTIC_ONLY, EvidenceStrength.WEAK,
                QcModuleResult.Confidence.HIGH, 0.5520);
        assertTrue(PRESENTER.present(evidence) instanceof UserFacingFinding.Inconclusive);
    }

    @Test public void rejectStatusIsInconclusiveNotDeviation() {
        Evidence evidence = evidenceOf(ValidationStatus.REJECT, EvidenceStrength.NONE,
                QcModuleResult.Confidence.HIGH, 0.60);
        assertTrue(PRESENTER.present(evidence) instanceof UserFacingFinding.Inconclusive);
    }

    @Test public void rejectsNullEvidence() {
        try { PRESENTER.present(null); fail("expected IllegalArgumentException"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void describeMeasurableDeviationUsesDirectionWordingNoRawNumbers() {
        Evidence evidence = evidenceOf(ValidationStatus.SUPPORTED, EvidenceStrength.STRONG,
                QcModuleResult.Confidence.HIGH, 0.60);
        UserFacingFinding finding = PRESENTER.present(evidence);
        String description = PRESENTER.describe(finding);
        assertTrue(description.contains("h12 apex_radial"));
        assertTrue(description.contains("high"));
        assertTrue(description.contains("genuine reference population"));
        assertTrue(description.contains("strong evidence"));
        // Never a millimetre/pixel claim or a pass/fail word.
        assertTrue(!description.toLowerCase(java.util.Locale.ROOT).contains("mm"));
        assertTrue(!description.toLowerCase(java.util.Locale.ROOT).contains("fail"));
        assertTrue(!description.toLowerCase(java.util.Locale.ROOT).contains("pass"));
    }

    @Test public void describeInconclusiveExplainsReason() {
        Evidence evidence = evidenceOf(ValidationStatus.PROMISING, EvidenceStrength.STRONG,
                QcModuleResult.Confidence.HIGH, 0.60);
        UserFacingFinding finding = PRESENTER.present(evidence);
        String description = PRESENTER.describe(finding);
        assertTrue(description.contains("PROMISING"));
    }

    @Test public void describeDetectorConfidenceInsufficientExplainsReason() {
        Evidence evidence = evidenceOf(ValidationStatus.SUPPORTED, EvidenceStrength.STRONG,
                QcModuleResult.Confidence.LOW, 0.60);
        UserFacingFinding finding = PRESENTER.present(evidence);
        String description = PRESENTER.describe(finding);
        assertTrue(description.toLowerCase(java.util.Locale.ROOT).contains("confidence"));
    }
}
