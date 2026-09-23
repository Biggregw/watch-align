package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class GenuineReferenceProfileTest {
    @Test public void emptyConstantHasNoMetrics(){
        assertTrue(GenuineReferenceProfile.EMPTY.metrics().isEmpty());
        assertEquals("empty",GenuineReferenceProfile.EMPTY.profileId());
    }

    @Test public void exposesMetricsByKey(){
        MetricKey key=new MetricKey("h12","centre_r");
        MetricBaseline baseline=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,null,0.0,
                ValidationStatus.PROMISING);
        Map<MetricKey,MetricBaseline> metrics=new HashMap<>();
        metrics.put(key,baseline);
        GenuineReferenceProfile profile=new GenuineReferenceProfile(
                "126710BLNR-genuine-v0","research branch, provisional",metrics);
        assertEquals(baseline,profile.baseline(key));
    }

    @Test public void baselineIsNullForUnknownMetric(){
        assertNull(GenuineReferenceProfile.EMPTY.baseline(new MetricKey("h12","centre_r")));
    }

    @Test public void metricsMapIsUnmodifiable(){
        GenuineReferenceProfile profile=new GenuineReferenceProfile("p","",Collections.emptyMap());
        try{
            profile.metrics().put(new MetricKey("h12","centre_r"),
                    new MetricBaseline(1,0.0,0.0,0.0,0.0,null,0.0,ValidationStatus.REJECT));
            fail("expected UnsupportedOperationException");
        }catch(UnsupportedOperationException expected){}
    }

    @Test public void rejectsBlankProfileId(){
        try{new GenuineReferenceProfile(" ","",Collections.emptyMap());fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void sourceDescriptionDefaultsToEmptyStringWhenNull(){
        GenuineReferenceProfile profile=new GenuineReferenceProfile("p",null,null);
        assertEquals("",profile.sourceDescription());
        assertTrue(profile.metrics().isEmpty());
    }
}
