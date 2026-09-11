package com.watchalign.mobile;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public class ModelCatalogTest {
    @Test public void catalogContainsBroadCommonQcCoverage() {
        assertTrue(ModelCatalog.all().size() >= 30);
        assertNotNull(ModelCatalog.byCode("126710BLNR"));
        assertNotNull(ModelCatalog.byCode("124060"));
        assertNotNull(ModelCatalog.byCode("126610LN"));
        assertNotNull(ModelCatalog.byCode("126500LN"));
        assertNotNull(ModelCatalog.byCode("126334"));
        assertNotNull(ModelCatalog.byCode("OMEGA-SMP300"));
        assertNotNull(ModelCatalog.byCode("OMEGA-AT38"));
        assertNotNull(ModelCatalog.byCode("AP-15510"));
        assertNotNull(ModelCatalog.byCode("PP-5711"));
        assertNotNull(ModelCatalog.byCode("TUDOR-BB58"));
        assertNotNull(ModelCatalog.byCode("CARTIER-SANTOS-M"));
    }

    @Test public void codesAreUniqueAndLabelsNonEmpty() {
        Set<String> codes=new HashSet<>();
        for(ModelCatalog.Profile p:ModelCatalog.all()) {
            assertTrue(codes.add(p.code));
            assertNotNull(p.label);
            assertFalse(p.label.trim().isEmpty());
            assertNotNull(p.brand);
        }
    }

    @Test public void cyclopsModelsCarryDateLocation() {
        for(ModelCatalog.Profile p:ModelCatalog.all()) {
            if(p.cyclops) assertTrue(p.hasDate());
        }
        assertEquals(3,ModelCatalog.require("126710BLNR").dateHour);
        assertEquals(9,ModelCatalog.require("126720VTNR").dateHour);
    }

    @Test public void onlyVerifiedExactModelsAdvertiseAutoReference() {
        assertTrue(ModelCatalog.require("126710BLNR").supportsAutoReference());
        assertTrue(ModelCatalog.require("124060").supportsAutoReference());
        assertFalse(ModelCatalog.require("126334").supportsAutoReference());
        assertFalse(ModelCatalog.require("OMEGA-SMP300").supportsAutoReference());
    }

    @Test public void cartierDoesNotPretendToUseCircularIndexEngine() {
        assertEquals(ModelCatalog.GeometryMode.VISUAL_ONLY,ModelCatalog.require("CARTIER-SANTOS-M").geometryMode);
        assertEquals(ModelCatalog.GeometryMode.VISUAL_ONLY,ModelCatalog.require("CARTIER-TANK").geometryMode);
    }
}
