package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

public class MetricBaselineTest {
    @Test public void exposesAllFields(){
        MetricBaseline b=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,0.0031,0.05,
                ValidationStatus.SUPPORTED);
        assertEquals(9,b.n());
        assertEquals(0.7382,b.median(),1e-12);
        assertEquals(0.0056,b.mad(),1e-12);
        assertEquals(0.7240,b.p10(),1e-12);
        assertEquals(0.7451,b.p90(),1e-12);
        assertEquals(0.0031,b.repeatabilityError(),1e-12);
        assertEquals(0.05,b.missingRate(),1e-12);
        assertEquals(ValidationStatus.SUPPORTED,b.status());
    }

    @Test public void repeatabilityErrorMayBeNull(){
        MetricBaseline b=new MetricBaseline(2,0.5,0.01,0.49,0.51,null,0.0,
                ValidationStatus.DIAGNOSTIC_ONLY);
        assertNull(b.repeatabilityError());
    }

    @Test public void rejectsNegativeN(){
        try{
            new MetricBaseline(-1,0.5,0.01,0.49,0.51,null,0.0,ValidationStatus.REJECT);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNonFiniteMedian(){
        try{
            new MetricBaseline(5,Double.NaN,0.01,0.49,0.51,null,0.0,ValidationStatus.PROMISING);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsMissingRateOutsideUnitInterval(){
        try{
            new MetricBaseline(5,0.5,0.01,0.49,0.51,null,1.5,ValidationStatus.PROMISING);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
        try{
            new MetricBaseline(5,0.5,0.01,0.49,0.51,null,-0.1,ValidationStatus.PROMISING);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNullStatus(){
        try{
            new MetricBaseline(5,0.5,0.01,0.49,0.51,null,0.0,null);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }
}
