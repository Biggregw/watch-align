package com.watchalign.mobile.baseline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.watchalign.mobile.qc.QcModuleResult;

import org.junit.Test;

public class UserFacingFindingTest {
    private static final MetricKey KEY=new MetricKey("h12","centre_r");

    private static Evidence evidenceWithStatus(ValidationStatus status){
        MetricBaseline baseline=new MetricBaseline(9,0.7382,0.0056,0.7240,0.7451,0.0025,0.0,status);
        ReferenceComparison comparison=new ReferenceComparison(KEY,0.7509,baseline);
        return new Evidence(comparison,status,QcModuleResult.Confidence.HIGH,EvidenceStrength.STRONG);
    }

    @Test public void measurableDeviationExposesEvidenceAndKey(){
        Evidence evidence=evidenceWithStatus(ValidationStatus.SUPPORTED);
        UserFacingFinding.MeasurableDeviation finding=new UserFacingFinding.MeasurableDeviation(evidence);
        assertEquals(evidence,finding.evidence());
        assertEquals(KEY,finding.key());
    }

    @Test public void measurableDeviationRejectsNonSupportedStatus(){
        Evidence evidence=evidenceWithStatus(ValidationStatus.PROMISING);
        try{
            new UserFacingFinding.MeasurableDeviation(evidence);
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void inconclusiveExposesReasonAndKey(){
        UserFacingFinding.Inconclusive finding=
                new UserFacingFinding.Inconclusive(KEY,"metric is PROMISING, not yet SUPPORTED");
        assertEquals(KEY,finding.key());
        assertEquals("metric is PROMISING, not yet SUPPORTED",finding.reason());
    }

    @Test public void inconclusiveRejectsBlankReason(){
        try{
            new UserFacingFinding.Inconclusive(KEY," ");
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }

    @Test public void detectorConfidenceInsufficientExposesReasonAndKey(){
        UserFacingFinding.DetectorConfidenceInsufficient finding=
                new UserFacingFinding.DetectorConfidenceInsufficient(KEY,"marker not confidently isolated");
        assertEquals(KEY,finding.key());
        assertEquals("marker not confidently isolated",finding.reason());
    }

    @Test public void detectorConfidenceInsufficientRejectsBlankReason(){
        try{
            new UserFacingFinding.DetectorConfidenceInsufficient(KEY,"");
            fail("expected IllegalArgumentException");
        }catch(IllegalArgumentException expected){}
    }
}
