package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Issue #34 research work must not enable Submariner production QC. The phase-1 Submariner models stay
 * unavailable to the GMT analyser and out of the offered model list; the GMT path stays as it is.
 */
public class SubmarinerProductionIsolationTest {
    private static final String[] SUBMARINERS = {"124060", "126610LN", "126610LV", "116610LN"};

    @Test public void submarinersAreNotSupportedByTheGmtGeometry() {
        for (String m : SUBMARINERS) assertFalse(m, CanonicalGmtGeometryAnalyzer.supports(m));
        assertTrue(CanonicalGmtGeometryAnalyzer.supports("126710BLNR"));
    }

    @Test public void productionAnalysisOfASubmarinerIsUnassessable() {
        for (String m : SUBMARINERS) {
            GmtHumanQcAnalyzerV2.Result r = GmtHumanQcAnalyzerV2.analyse(null, m);
            assertEquals(m, GmtHumanQcMath.PoseLabel.UNASSESSABLE, r.poseLabel);
            assertEquals(m, GmtHumanQcMath.Attention.UNASSESSABLE, r.rotationAttention);
            assertEquals(m, GmtHumanQcMath.Attention.UNASSESSABLE, r.clearanceAttention);
            assertFalse(m, r.localFrameValid);
        }
    }

    @Test public void theAppOffersOnlyTheGenericGmt() {
        List<ModelCatalog.Profile> offered = MainActivity.offeredModels();
        assertEquals(1, offered.size());
        assertEquals("126710BLNR", offered.get(0).code);
        assertEquals("126710BLNR", MainActivity.GENERIC_GMT_CODE);
    }

    @Test public void submarinerCatalogueEntriesAreUnchangedAndDormant() {
        // Snapshot of the existing (dormant) catalogue rows; research must not edit them.
        ModelCatalog.Profile p = ModelCatalog.require("124060");
        assertEquals(0, p.dateHour);
        assertFalse(p.cyclops);
        ModelCatalog.Profile ln = ModelCatalog.require("126610LN"), lv = ModelCatalog.require("126610LV");
        assertEquals(3, ln.dateHour);
        assertEquals(3, lv.dateHour);
        assertEquals(0.69, ln.dateRadiusRatio, 0.0);
        assertEquals(0.69, lv.dateRadiusRatio, 0.0);
    }
}
