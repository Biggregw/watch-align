package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class QcSummaryFormatterTest {
    @Test public void noRankedFindingsSaysNoMajorDefectsAndCanSurfaceTinyTwelveBias() {
        String report="Watch Align Core 1.3.0-alpha17\n12 o'clock angular +0.04° · radial +0.31%\n\nExtended QC checks\n";
        String out=QcSummaryFormatter.prependSummary(report);
        assertTrue(out.startsWith("QC SUMMARY\nNo major defects detected."));
        assertTrue(out.contains("12 triangle is fractionally high/outward (+0.31% radial)"));
        assertTrue(out.contains("Full QC detail"));
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
