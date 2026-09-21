package com.watchalign.mobile.qc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;

public class RawMeasurementTest {
    @Test public void exposesIdValueAndUnit(){
        RawMeasurement m=new RawMeasurement("base_to_60_over_base",0.164,"ratio");
        assertEquals("base_to_60_over_base",m.id());
        assertEquals(0.164,m.value(),1e-12);
        assertEquals("ratio",m.unit());
    }

    @Test public void unitDefaultsToEmptyStringWhenNull(){
        RawMeasurement m=new RawMeasurement("rotation_deg",-0.29,null);
        assertEquals("",m.unit());
    }

    @Test public void rejectsBlankId(){
        try{new RawMeasurement("  ",1.0,"px");fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNonFiniteValue(){
        try{new RawMeasurement("x",Double.NaN,"px");fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
        try{new RawMeasurement("x",Double.POSITIVE_INFINITY,"px");fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }

    @Test public void equalityIsByIdValueAndUnit(){
        assertEquals(new RawMeasurement("a",1.0,"px"),new RawMeasurement("a",1.0,"px"));
        assertFalse(new RawMeasurement("a",1.0,"px").equals(new RawMeasurement("a",1.0,"mm")));
    }
}
