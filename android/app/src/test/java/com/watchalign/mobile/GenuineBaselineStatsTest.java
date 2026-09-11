package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class GenuineBaselineStatsTest {
    @Test public void medianAndMadIgnoreOutlier(){
        double[] v={-2.0,-1.9,-2.1,-2.0,4.8};
        GenuineBaselineStats.Summary s=GenuineBaselineStats.summarize(v,v.length);
        assertEquals(-2.0,s.median,1e-9);
        assertTrue(s.mad<=0.1+1e-9);
    }

    @Test public void radialDeltaLikeObservedTwelveIsFlagged(){
        double[] gens={-2.0,-1.8,-2.1,-1.9,-2.0,-2.2};
        GenuineBaselineStats.Summary s=GenuineBaselineStats.summarize(gens,gens.length);
        assertEquals(2,GenuineBaselineStats.severity(0.31,s,0.90));
    }

    @Test public void normalSixMarkerStaysNormal(){
        double[] gens={0.1,0.2,0.0,-0.1,0.15,0.05};
        GenuineBaselineStats.Summary s=GenuineBaselineStats.summarize(gens,gens.length);
        assertEquals(0,GenuineBaselineStats.severity(0.35,s,0.90));
    }

    @Test public void smallSampleUsesToleranceFloor(){
        double[] one={0.0};
        GenuineBaselineStats.Summary s=GenuineBaselineStats.summarize(one,1);
        assertEquals(0,GenuineBaselineStats.severity(0.7,s,0.90));
        assertEquals(1,GenuineBaselineStats.severity(1.0,s,0.90));
    }
}
