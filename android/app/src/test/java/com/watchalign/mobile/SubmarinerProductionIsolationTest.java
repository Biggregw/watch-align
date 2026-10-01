package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Submariner isolation and routing (124060 checkpoint build). The GMT analyser never handles a
 * Submariner; only the 124060 is offered and routed, to its own experimental path; no other
 * Submariner is offered; the GMT path and its catalogue rows stay as they were.
 */
public class SubmarinerProductionIsolationTest {
    private static final String[] SUBMARINERS = {"124060", "126610LN", "126610LV", "116610LN"};

    @Test public void submarinersAreNotSupportedByTheGmtGeometry() {
        for (String m : SUBMARINERS) assertFalse(m, CanonicalGmtGeometryAnalyzer.supports(m));
        assertTrue(CanonicalGmtGeometryAnalyzer.supports("126710BLNR"));
    }

    @Test public void theGmtAnalyserRefusesEverySubmariner() {
        for (String m : SUBMARINERS) {
            GmtHumanQcAnalyzerV2.Result r = GmtHumanQcAnalyzerV2.analyse(null, m);
            assertEquals(m, GmtHumanQcMath.PoseLabel.UNASSESSABLE, r.poseLabel);
            assertEquals(m, GmtHumanQcMath.Attention.UNASSESSABLE, r.rotationAttention);
            assertEquals(m, GmtHumanQcMath.Attention.UNASSESSABLE, r.clearanceAttention);
            assertFalse(m, r.localFrameValid);
        }
    }

    @Test public void onlyThe124060HasTheExperimentalRoute() {
        assertTrue(Sub124060QcAnalyzer.supports("124060"));
        assertTrue(Sub124060QcAnalyzer.supports(" 124060 "));
        for (String m : new String[]{"126610LN", "126610LV", "116610LN", "126710BLNR", "126720VTNR", "", null})
            assertFalse(String.valueOf(m), Sub124060QcAnalyzer.supports(m));
    }

    @Test public void theAppOffersTheGenericGmtFirstThenThe124060() {
        List<ModelCatalog.Profile> offered = MainActivity.offeredModels();
        assertEquals(2, offered.size());
        assertEquals("126710BLNR", offered.get(0).code);
        assertEquals("126710BLNR", MainActivity.GENERIC_GMT_CODE);
        assertEquals("124060", offered.get(1).code);
        assertEquals("GMT-Master II 126710", MainActivity.selectorLabel(offered.get(0)));
        assertEquals("Submariner 124060 (experimental)", MainActivity.selectorLabel(offered.get(1)));
        // The GMT line under the title is the one the app always showed.
        assertEquals("Rolex GMT-Master II dial check · " + WatchAlignCoreV13.CORE_VERSION, MainActivity.subtitleFor(offered.get(0)));
        assertTrue(MainActivity.subtitleFor(offered.get(1)).contains("Submariner 124060"));
    }

    @Test public void submarinerCatalogueEntriesAreUnchanged() {
        ModelCatalog.Profile p = ModelCatalog.require("124060");
        assertEquals(0, p.dateHour);
        assertFalse(p.cyclops);
        ModelCatalog.Profile ln = ModelCatalog.require("126610LN"), lv = ModelCatalog.require("126610LV");
        assertEquals(3, ln.dateHour);
        assertEquals(3, lv.dateHour);
        assertEquals(0.69, ln.dateRadiusRatio, 0.0);
        assertEquals(0.69, lv.dateRadiusRatio, 0.0);
    }

    /** Routing: a 124060 never reaches the GMT or legacy analysers; it gets its own result, no GMT wording. */
    @Test public void the124060RouteIsSeparateAndFailsClosedWithoutAPhoto() {
        InspectionImageStore.clearManualSeed();
        WatchAlignCoreV13.AnalysisResult r = WatchAlignCoreV13.analyse(null, java.util.Collections.emptyList(), "124060", null);
        assertTrue(r.submariner);
        assertTrue(r.dialNeedsManual);
        assertFalse(r.twelveMeasured);
        assertTrue(r.report.startsWith("Rolex Submariner 124060 (experimental)"));
        assertTrue(r.report.contains(Sub124060Summary.EXPERIMENTAL));
        assertTrue(r.report.contains("Dial: not assessable"));
        for (String gmt : new String[]{"GMT-Master", "date window", "Sprite", "date side", "HUMAN 12-MARKER QC", "reference-distribution"})
            assertFalse(gmt, r.report.contains(gmt));
        String sum = MainActivity.summaryOf(r.report);
        assertTrue(sum != null && sum.startsWith(Sub124060Summary.EXPERIMENTAL));
    }
}
