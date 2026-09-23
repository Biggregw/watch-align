package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MetricKeyTest {
    @Test public void exposesMarkerIdAndMetricName(){
        MetricKey k=new MetricKey("h12","centre_r");
        assertEquals("h12",k.markerId());
        assertEquals("centre_r",k.metricName());
    }

    @Test public void toStringMatchesResearchDotConvention(){
        assertEquals("h12.centre_r",new MetricKey("h12","centre_r").toString());
    }

    @Test public void rejectsBlankMarkerId(){
        try{new MetricKey(" ","centre_r");fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsBlankMetricName(){
        try{new MetricKey("h12"," ");fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void equalityIsByMarkerIdAndMetricName(){
        assertEquals(new MetricKey("h12","centre_r"),new MetricKey("h12","centre_r"));
        assertFalse(new MetricKey("h12","centre_r").equals(new MetricKey("h06","centre_r")));
        assertFalse(new MetricKey("h12","centre_r").equals(new MetricKey("h12","centre_t")));
    }
}
