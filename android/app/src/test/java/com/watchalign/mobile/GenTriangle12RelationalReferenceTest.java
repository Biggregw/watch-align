package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class GenTriangle12RelationalReferenceTest {
    @Test public void genuineRatiosAreFrozenFromSingleReference() {
        assertEquals(1.4166666f, GenTriangle12RelationalReference.HEIGHT_OVER_BASE, 0.0001f);
        assertEquals(0.125f, GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE, 0.0001f);
        assertEquals(0.0833333f, GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE, 0.0001f);
    }
}
