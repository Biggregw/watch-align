package com.watchalign.redditqc;

import static org.junit.Assert.*;
import org.junit.Test;

public class CollectorTestGateTest {
    @Test public void metadataOnlyThreadUnlocksAll() {
        assertTrue(CollectorTestGate.canCollect(1,false));
    }
    @Test public void failedOrDeniedMetadataNeverUnlocksAll() {
        assertFalse(CollectorTestGate.canCollect(0,false));
        assertFalse(CollectorTestGate.canCollect(0,true));
        assertFalse(CollectorTestGate.canCollect(1,true));
    }
}
