package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GenTriangle12RelationalReferenceTest {
    @Test public void genuineControlRangesAreFrozen() {
        assertEquals(1.4167f, GenTriangle12RelationalReference.HEIGHT_OVER_BASE, 0.0001f);
        assertEquals(0.125f, GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE, 0.0001f);
        assertEquals(0.26f, GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE, 0.0001f);
        assertTrue(GenTriangle12RelationalReference.BASE_TO_60_MIN < GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE);
        assertTrue(GenTriangle12RelationalReference.BASE_TO_60_MAX > GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE);
        assertTrue(GenTriangle12RelationalReference.APEX_TO_CROWN_MIN < GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE);
        assertTrue(GenTriangle12RelationalReference.APEX_TO_CROWN_MAX > GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE);
    }
}
