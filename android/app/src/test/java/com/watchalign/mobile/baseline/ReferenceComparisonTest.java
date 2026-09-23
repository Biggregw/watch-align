package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class ReferenceComparisonTest {
    private static final MetricKey KEY=new MetricKey("h12","centre_r");

    @Test public void computesSignedDeviationFromMedian(){
        MetricBaseline baseline=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,null,0.0,
                ValidationStatus.PROMISING);
        ReferenceComparison c=new ReferenceComparison(KEY,0.7509,baseline);
        assertEquals(0.7509-0.7382,c.signedDeviation(),1e-12);
    }

    @Test public void standardizedDeviationRequiresPositiveRepeatabilityError(){
        MetricBaseline withRepeatability=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,0.0025,0.0,
                ValidationStatus.SUPPORTED);
        ReferenceComparison c=new ReferenceComparison(KEY,0.7509,withRepeatability);
        assertEquals((0.7509-0.7382)/0.0025,c.standardizedDeviation(),1e-9);

        MetricBaseline withoutRepeatability=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,null,0.0,
                ValidationStatus.SUPPORTED);
        ReferenceComparison c2=new ReferenceComparison(KEY,0.7509,withoutRepeatability);
        assertNull(c2.standardizedDeviation());

        MetricBaseline zeroRepeatability=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,0.0,0.0,
                ValidationStatus.SUPPORTED);
        ReferenceComparison c3=new ReferenceComparison(KEY,0.7509,zeroRepeatability);
        assertNull(c3.standardizedDeviation());
    }

    @Test public void rejectsNonFiniteObservedValue(){
        MetricBaseline baseline=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,null,0.0,
                ValidationStatus.PROMISING);
        try{new ReferenceComparison(KEY,Double.NaN,baseline);fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNullBaseline(){
        try{new ReferenceComparison(KEY,0.5,null);fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }
}
