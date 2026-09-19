package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MinuteTrackDialFinderTest {
    @Test public void convertsMinuteTrackRadiusToDialRadiusUsingMasterRatio(){
        double track=92.5;
        assertEquals(100.0,MinuteTrackDialFinder.expectedDialRadiusFromTrackRadius(track),1e-9);
    }
}
