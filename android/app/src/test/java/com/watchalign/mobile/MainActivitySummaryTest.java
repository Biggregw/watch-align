package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MainActivitySummaryTest {
    @Test public void extractsTheBlockBetweenSummaryAndDetails() {
        String report = "126710BLNR · Watch Align Core x\n\nSUMMARY\nPhoto: good.\n12 gap: normal.\n\nBottom line: nothing flagged.\n\n\nDETAILS\nlots of numbers";
        assertEquals("Photo: good.\n12 gap: normal.\n\nBottom line: nothing flagged.", MainActivity.summaryOf(report));
    }
    @Test public void noSummaryGivesNull() {
        assertNull(MainActivity.summaryOf("VISUAL QC MASTER\nUnavailable"));
        assertNull(MainActivity.summaryOf(null));
    }
}
