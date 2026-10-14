package com.watchalign.mobile;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** Outer-metal alignment: its allowance file loads per model, and it never takes the lead from a luminous defect. */
public class MetalAlignmentTest {
    @Test public void twelveRadialAllowanceLoadsForBothModels(){
        for(String id:new String[]{"gmt_126710","submariner_124060"}){
            ModelSpec m;try{m=ModelSpec.load(TestModels.files(),id);}catch(Exception e){throw new AssertionError(e);}
            Map<String,Double> x=MetalAlignment.load(TestModels.files(),m);
            assertNotNull(id,x);assertTrue(x.get("twelve_radial.R")>0);assertTrue(x.get("twelve_radial.px")>0);
            AlignmentReference r=AlignmentReference.load(TestModels.files(),m);
            assertNotNull(r.metalExtra);
        }
    }

    @Test public void allowanceIsNeverBelowThePhotosPixelNoise(){
        Map<String,Double> x=new java.util.HashMap<>();x.put("twelve_radial.R",0.002);x.put("twelve_radial.px",0.6);
        assertEquals(0.002,MetalAlignment.sigmaRadial(x,600),1e-12);      // sharp photo: the R term
        assertEquals(0.6/200,MetalAlignment.sigmaRadial(x,200),1e-12);    // small photo: the pixel term
        assertTrue(Double.isNaN(MetalAlignment.sigmaRadial(null,300)));
    }

    static AlignmentDecision.Issue metal(double excess){
        AlignmentDecision.Issue i=new AlignmentDecision.Issue();i.metal=true;i.param=LuminousAlignment.Param.RADIAL;
        i.deviation=0.01;i.screen=new double[]{0,-1};i.excessR=excess;return i;
    }

    @Test public void clearMetalMakesADefectAndNamesTheMetal(){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(12,ModelSpec.Shape.TRIANGLE);m.outcome=AlignmentDecision.Outcome.NO_DEFECT;
        m.metal.add(metal(0.002));MetalAlignment.settle(m);
        assertEquals(AlignmentDecision.Outcome.DEFECT,m.outcome);
        assertEquals("Outer metal positioned too high",m.primary.headline());
        assertTrue(m.primary.measured().contains("outer-metal"));
    }

    @Test public void aLuminousDefectKeepsTheLeadAndMetalFollows(){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(12,ModelSpec.Shape.TRIANGLE);m.outcome=AlignmentDecision.Outcome.DEFECT;
        AlignmentDecision.Issue lume=new AlignmentDecision.Issue();lume.param=LuminousAlignment.Param.TANGENTIAL;lume.deviation=0.01;lume.screen=new double[]{1,0};lume.excessR=0.001;
        m.primary=lume;m.metal.add(metal(0.005));MetalAlignment.settle(m);
        assertSame(lume,m.primary);assertEquals("Positioned too far right",m.primary.headline());
        assertTrue(m.secondary.get(0).metal);
    }

    @Test public void noMetalFindingLeavesTheMarkerAlone(){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(6,ModelSpec.Shape.BATON);m.outcome=AlignmentDecision.Outcome.NO_DEFECT;
        MetalAlignment.settle(m);assertEquals(AlignmentDecision.Outcome.NO_DEFECT,m.outcome);assertNull(m.primary);
    }
    @Test public void unmeasurableMetalCannotLeaveAPassingMarkerAsNoDefect(){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(6,ModelSpec.Shape.BATON);m.outcome=AlignmentDecision.Outcome.NO_DEFECT;
        MetalAlignment.notAssessed(m,"edge hidden by glare");
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,m.outcome);
        assertTrue(m.reason,m.reason.contains("edge hidden by glare")&&m.reason.contains("luminous fill is within the genuine range"));
        assertTrue(m.metalNotes.get(0).startsWith("Outer metal not assessed"));
    }

    @Test public void aConfirmedLuminousDefectStandsWhenMetalCannotBeAssessed(){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(6,ModelSpec.Shape.BATON);m.outcome=AlignmentDecision.Outcome.DEFECT;
        AlignmentDecision.Issue lume=new AlignmentDecision.Issue();lume.param=LuminousAlignment.Param.RADIAL;m.primary=lume;
        MetalAlignment.notAssessed(m,"edge hidden by glare");
        assertEquals(AlignmentDecision.Outcome.DEFECT,m.outcome);assertSame(lume,m.primary);
    }

    @Test public void aLuminousWithholdingKeepsItsOwnReason(){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(9,ModelSpec.Shape.BATON);m.outcome=AlignmentDecision.Outcome.CANNOT_ASSESS;m.reason="hand crosses or touches this marker";
        MetalAlignment.notAssessed(m,"a hand crosses or touches this marker");
        assertEquals("hand crosses or touches this marker",m.reason);
    }

    @Test public void aMetalCheckThatCannotRunWithholdsOnlyTrianglesAndBatons(){
        AlignmentAnalysis.Output o=new AlignmentAnalysis.Output();
        for(ModelSpec.Marker k:TestModels.gmt().markers){AlignmentDecision.Marker m=new AlignmentDecision.Marker(k.hour,k.shape);m.outcome=AlignmentDecision.Outcome.NO_DEFECT;o.markers.add(m);}
        MetalAlignment.apply(o,null,null,TestModels.gmt(),null,null);
        for(AlignmentDecision.Marker m:o.markers)
            assertEquals(m.hour+" o'clock",m.shape==ModelSpec.Shape.ROUND?AlignmentDecision.Outcome.NO_DEFECT:AlignmentDecision.Outcome.CANNOT_ASSESS,m.outcome);
    }
}
