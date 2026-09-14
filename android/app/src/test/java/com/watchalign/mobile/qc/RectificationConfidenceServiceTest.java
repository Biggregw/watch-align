package com.watchalign.mobile.qc;
import static org.junit.Assert.*;import org.junit.Test;
public class RectificationConfidenceServiceTest {
 @Test public void wellConstrainedWarpWithVerifiedInnerBoundaryIsHigh(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0,new RectificationConfidenceService.Validation(.0001,.99,Double.NaN,.98,true));assertEquals(QcModuleResult.Confidence.HIGH,a.confidence());assertTrue(a.evidence().get(1).contains("inner dial-edge"));}
 @Test public void anchorsOnlyCanNeverClaimHigh(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0);assertEquals(QcModuleResult.Confidence.MEDIUM,a.confidence());assertTrue(a.evidence().get(1).contains("capped at medium"));}
 @Test public void obliqueButIndependentlyValidatedWarpCanRemainUsable(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-72,125,4,8,89,-96,-3,new RectificationConfidenceService.Validation(.001,.64,Double.NaN,.90,true));assertNotEquals(QcModuleResult.Confidence.LOW,a.confidence());}
 @Test public void weakInnerBoundaryVerificationRejectsHighConfidence(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0,new RectificationConfidenceService.Validation(.0001,.99,Double.NaN,.35,true));assertEquals(QcModuleResult.Confidence.LOW,a.confidence());}
 @Test public void badReprojectionCapsConfidence(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0,new RectificationConfidenceService.Validation(.03,.99,Double.NaN,.96,true));assertEquals(QcModuleResult.Confidence.LOW,a.confidence());}
 @Test public void crossedAnchorsAreRejected(){assertEquals(QcModuleResult.Confidence.LOW,RectificationConfidenceService.assess(0,-100,100,0,-100,0,0,100).confidence());}
 @Test public void componentConfidenceCannotExceedRectification(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-20,120,0,0,22,-120,0);assertEquals(QcModuleResult.Confidence.LOW,a.cap(QcModuleResult.Confidence.HIGH));}
}
