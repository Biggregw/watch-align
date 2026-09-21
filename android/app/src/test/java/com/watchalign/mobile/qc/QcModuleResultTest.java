package com.watchalign.mobile.qc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public class QcModuleResultTest {
    @Test public void looksUpMeasurementById(){
        List<RawMeasurement> measurements=Arrays.asList(
                new RawMeasurement("a",1.0,"px"),
                new RawMeasurement("b",2.0,"deg"));
        QcModuleResult result=new QcModuleResult("test-module",measurements,
                QcModuleResult.Confidence.HIGH,Collections.singletonList("evidence"));

        assertEquals(2.0,result.measurement("b").value(),1e-12);
        assertNull(result.measurement("missing"));
        assertNull(result.measurement(null));
    }

    @Test public void defaultsToLowConfidenceAndEmptyListsWhenNull(){
        QcModuleResult result=new QcModuleResult("test-module",null,null,null);
        assertEquals(QcModuleResult.Confidence.LOW,result.confidence());
        assertTrue(result.measurements().isEmpty());
        assertTrue(result.evidence().isEmpty());
    }

    @Test public void measurementsAndEvidenceAreImmutable(){
        QcModuleResult result=new QcModuleResult("test-module",
                Arrays.asList(new RawMeasurement("a",1.0,"px")),
                QcModuleResult.Confidence.MEDIUM,Collections.singletonList("note"));
        try{result.measurements().add(new RawMeasurement("b",2.0,"px"));fail("expected immutable list");}
        catch(UnsupportedOperationException expected){}
        try{result.evidence().add("more");fail("expected immutable list");}
        catch(UnsupportedOperationException expected){}
    }

    @Test public void rejectsBlankModuleId(){
        try{new QcModuleResult(" ",null,null,null);fail("expected IllegalArgumentException");}
        catch(IllegalArgumentException expected){}
    }
}
