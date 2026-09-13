package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.watchalign.mobile.qc.PerspectiveConfidenceService;

import org.junit.Test;

public class GmtExtendedQcReportGuardTest {
    @Test public void lowConfidenceSuppressesContaminatedDateAndBodyRotationClaims() {
        PerspectiveConfidenceService.Assessment low = PerspectiveConfidenceService.assess(
                0,0, 100,0, 1,1, 0,100);
        String input = "Top QC observations — advisory only\n"
                + "1. Date aperture -4.58 marker divisions from local 15-minute reference\n\n"
                + "Extended QC checks\n"
                + "12 marker vs minute track: position +0.38°, body rotation -87.21°\n"
                + "Date aperture vs local 15-minute marker: -27.45° (-4.58 marker divisions)\n"
                + "Fine QC is advisory because perspective could not be verified from this photo.\n";
        String output = GmtExtendedQcReportGuard.sanitize(input, low);
        assertFalse(output.contains("-87.21°"));
        assertFalse(output.contains("-27.45°"));
        assertFalse(output.contains("1. Date aperture -4.58"));
        assertTrue(output.contains("body rotation withheld"));
        assertTrue(output.contains("Date aperture axis vs local minute track: withheld"));
        assertTrue(output.contains("corrected 12/3/6/9 anchors"));
    }

    @Test public void highConfidenceStillRejectsPhysicallyImplausibleBodyFit() {
        PerspectiveConfidenceService.Assessment high = PerspectiveConfidenceService.assess(
                0,-100, 100,0, 0,100, -100,0);
        String output = GmtExtendedQcReportGuard.sanitize(
                "Extended QC checks\n9 marker vs minute track: position +0.53°, body rotation +44.11°", high);
        assertFalse(output.contains("+44.11°"));
        assertTrue(output.contains("body rotation withheld"));
    }
}
