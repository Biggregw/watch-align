package com.watchalign.mobile.qc;
import static org.junit.Assert.*;import org.junit.Test;
public class RectificationConfidenceServiceTest {
 @Test public void wellConstrainedWarpIsHigh(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0,new RectificationConfidenceService.Validation(.0001,.99,.88,.98,true));assertEquals(QcModuleResult.Confidence.HIGH,a.confidence());assertTrue(a.evidence().get(0).contains("Rectification confidence"));}
 @Test public void obliqueButValidatedWarpCanRemainUsable(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-72,125,4,8,89,-96,-3,new RectificationConfidenceService.Validation(.001,.64,.72,.97,true));assertNotEquals(QcModuleResult.Confidence.LOW,a.confidence());}
 @Test public void circularityRegressionRejectsOverCorrection(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0,new RectificationConfidenceService.Validation(.0001,.99,.98,.82,true));assertEquals(QcModuleResult.Confidence.LOW,a.confidence());}
 @Test public void badReprojectionCapsConfidence(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-100,100,0,0,100,-100,0,new RectificationConfidenceService.Validation(.03,.99,.85,.96,true));assertEquals(QcModuleResult.Confidence.LOW,a.confidence());}
 @Test public void crossedAnchorsAreRejected(){assertEquals(QcModuleResult.Confidence.LOW,RectificationConfidenceService.assess(0,-100,100,0,-100,0,0,100).confidence());}
 @Test public void componentConfidenceCannotExceedRectification(){RectificationConfidenceService.Assessment a=RectificationConfidenceService.assess(0,-20,120,0,0,22,-120,0);assertEquals(QcModuleResult.Confidence.LOW,a.cap(QcModuleResult.Confidence.HIGH));}
}
