package com.watchalign.mobile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MinuteTrackAcquisitionRescueTest {
    @Test public void acceptedPrimaryIsNeverReplaced(){
        assertFalse(MinuteTrackAcquisitionRescue.shouldUseRescue(true,true,true,true));
    }

    @Test public void rejectedPrimaryCanBeRescuedOnlyWhenEveryGatePasses(){
        assertTrue(MinuteTrackAcquisitionRescue.shouldUseRescue(false,true,true,true));
        assertFalse(MinuteTrackAcquisitionRescue.shouldUseRescue(false,false,true,true));
        assertFalse(MinuteTrackAcquisitionRescue.shouldUseRescue(false,true,false,true));
        assertFalse(MinuteTrackAcquisitionRescue.shouldUseRescue(false,true,true,false));
    }

    @Test public void periodicEvidenceCanOnlyReduceSelectionObjective(){
        double base=MinuteTrackAcquisitionRescue.selectObjective(1.0,2.0,0.0,0.0);
        double periodic=MinuteTrackAcquisitionRescue.selectObjective(1.0,2.0,1.0,0.5);
        assertTrue(periodic<base);
    }
}
