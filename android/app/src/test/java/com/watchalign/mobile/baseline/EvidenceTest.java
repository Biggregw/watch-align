package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.watchalign.mobile.qc.QcModuleResult;

import org.junit.Test;

public class EvidenceTest {
    private static final MetricKey KEY=new MetricKey("h12","centre_r");

    private static ReferenceComparison comparison(){
        MetricBaseline baseline=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,0.0025,0.0,
                ValidationStatus.SUPPORTED);
        return new ReferenceComparison(KEY,0.7509,baseline);
    }

    @Test public void exposesAllFields(){
        Evidence e=new Evidence(comparison(),ValidationStatus.SUPPORTED,
                QcModuleResult.Confidence.HIGH,EvidenceStrength.STRONG);
        assertEquals(ValidationStatus.SUPPORTED,e.status());
        assertEquals(QcModuleResult.Confidence.HIGH,e.detectorConfidence());
        assertEquals(EvidenceStrength.STRONG,e.strength());
    }

    @Test public void rejectsNullComparison(){
        try{
            new Evidence(null,ValidationStatus.SUPPORTED,QcModuleResult.Confidence.HIGH,EvidenceStrength.STRONG);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNullStatus(){
        try{
            new Evidence(comparison(),null,QcModuleResult.Confidence.HIGH,EvidenceStrength.STRONG);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNullDetectorConfidence(){
        try{
            new Evidence(comparison(),ValidationStatus.SUPPORTED,null,EvidenceStrength.STRONG);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void rejectsNullStrength(){
        try{
            new Evidence(comparison(),ValidationStatus.SUPPORTED,QcModuleResult.Confidence.HIGH,null);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }
}
