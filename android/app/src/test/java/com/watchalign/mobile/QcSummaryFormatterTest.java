package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class QcSummaryFormatterTest {
    @Test public void noRankedFindingsSaysNoMajorDefectsWithoutGuessingFromOldRadialMetric() {
        String report="Watch Align Core 1.3.0-alpha21\n12 o'clock angular +0.04° · radial +0.31%\n\nExtended QC checks\n";
        String out=QcSummaryFormatter.prependSummary(report);
        assertTrue(out.startsWith("QC SUMMARY\nNo major defects detected."));
        assertFalse(out.contains("12 triangle is fractionally"));
        assertTrue(out.contains("Full QC detail"));
    }

    @Test public void canonicalGmtCheckIsSurfacedInSummary() {
        String report="Watch Align Core 1.3.0-alpha21\n\nCANONICAL GMT GEOMETRY\n"+
                "12 o'clock: angle +0.10° from canonical; radial Δ +1.05% of dial radius  [CHECK; radial n=5]\n"+
                " 6 o'clock: angle +0.05° from canonical; radial Δ +0.12% of dial radius  [normal; radial n=5]\n"+
                "\nInterpretation: canonical geometry test\n";
        String out=QcSummaryFormatter.prependSummary(report);
        assertTrue(out.contains("No major defects detected. Minor observations"));
        assertTrue(out.contains("12 o'clock: angle +0.10° from canonical; radial Δ +1.05%"));
        assertFalse(out.contains("6 o'clock: angle +0.05°"));
    }

    @Test public void severeForumStyleFindingsAreRankedAndSimplified() {
        String report="Top QC findings\n1. 12 marker body rotation +4.20° (strong detected deviation)\n2. SEL gap area merits visual inspection (left 0.31, right 0.08; lighting-sensitive)\n\nExtended QC checks\n";
        String out=QcSummaryFormatter.prependSummary(report);
        assertTrue(out.contains("Biggest detected issues"));
        assertTrue(out.contains("1. 12 triangle rotated +4.20°"));
        assertTrue(out.contains("2. SEL gap"));
    }

    @Test public void mildRankedFindingDoesNotBecomeMajorDefect() {
        String report="Top QC findings\n1. 12 marker local position +0.90° vs minute track\n\nExtended QC checks\n";
        String out=QcSummaryFormatter.prependSummary(report);
        assertTrue(out.contains("No major defects detected. Minor observations"));
    }
}
