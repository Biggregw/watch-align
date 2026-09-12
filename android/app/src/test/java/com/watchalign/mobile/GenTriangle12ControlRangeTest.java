package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GenTriangle12ControlRangeTest {
    @Test public void threeObservedBaseGapControlsAreAccepted(){
        // Raw perspective-rectified ratios inferred from the three genuine runs:
        // previous range max 0.15 plus displayed excesses 0.013, 0.019 and 0.040.
        assertTrue(GenTriangle12RelationalReference.baseGapInRange(0.163f));
        assertTrue(GenTriangle12RelationalReference.baseGapInRange(0.169f));
        assertTrue(GenTriangle12RelationalReference.baseGapInRange(0.190f));
    }

    @Test public void clearlyOutsideBaseGapStillFails(){
        assertFalse(GenTriangle12RelationalReference.baseGapInRange(0.08f));
        assertFalse(GenTriangle12RelationalReference.baseGapInRange(0.22f));
    }

    @Test public void observedGenRotationIsAcceptedButLargeRotationIsNot(){
        assertTrue(GenTriangle12RelationalReference.rotationInObservedGenRange(0.42f));
        assertFalse(GenTriangle12RelationalReference.rotationInObservedGenRange(0.80f));
    }
}
