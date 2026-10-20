package com.watchalign.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The bundled genuine luminous alignment references (calibration run 38565452660) load for exactly the models the app
 * bundles (whether a model is offered is ModelRegistry's decision), the Date family's luminous edge-fit reference explicitly
 * inherited from the 124060, covering every marker; and a smoke check that a clean synthetic dial
 * judged against the REAL reference is assessed (not "Cannot assess") and shows no defect.
 */
public class AlignmentReferenceTest {
    static ModelSpec spec(String id){try{return ModelSpec.load(TestModels.files(),id);}catch(Exception e){throw new AssertionError(e);}}

    @Test public void allBundledModelsHaveExplicitAlignmentReference(){
        for(String id:new String[]{"gmt_126710","submariner_124060","submariner_126610"}){
            ModelSpec m=spec(id);AlignmentReference r=AlignmentReference.load(TestModels.files(),m);
            assertNotNull(id+" reference missing",r);
            assertEquals(id,r.model);
            assertTrue(r.provenance,r.provenance.contains("38565452660"));
            for(ModelSpec.Marker mk:m.markers){
                AlignmentReference.ShapeRef s=r.shape(mk.shape);
                assertNotNull(id+" "+mk.hour,s);assertTrue(id+" "+mk.shape,s.supported);
                assertTrue(Double.isFinite(s.insetR)&&s.insetR>0);
                assertTrue(Double.isFinite(s.radialLimitR)&&Double.isFinite(s.tangentialLimitR));
                if(mk.shape!=ModelSpec.Shape.ROUND)assertTrue(Double.isFinite(s.rotationLimitDeg));
                assertNotNull(id+" hour "+mk.hour+" has no genuine nominal",r.hours.get(mk.hour));
                assertNotNull(r.setup(mk.shape));
            }
        }
        // model isolation: each family loads its own file, named for it (the GMT's holds the Submariner Date values by
        // the owner's choice, 20 Oct 2026: ThreeOptionsTest), never another model's file
        AlignmentReference g=AlignmentReference.load(TestModels.files(),spec("gmt_126710")),d=AlignmentReference.load(TestModels.files(),spec("submariner_126610"));
        assertEquals("gmt_126710",g.model);assertEquals("submariner_126610",d.model);
        assertEquals(d.shape(ModelSpec.Shape.TRIANGLE).insetR,g.shape(ModelSpec.Shape.TRIANGLE).insetR,0);
    }

    @Test public void dateFamilyReusesDocumentedLuminousGeometryButKeepsDistinctGenuineMetalReference(){
        ModelSpec date=spec("submariner_126610");
        assertEquals(11,date.markers.size());
        assertEquals(3,date.date.hour);
        assertNull(date.atHour(3));
        AlignmentReference borrowed=AlignmentReference.load(TestModels.files(),date);
        assertNotNull(borrowed);
        assertEquals("submariner_126610",borrowed.model);
        assertTrue(borrowed.provenance,borrowed.provenance.contains("Shared 124060"));
        assertFalse("date family has no 3 baton",borrowed.hours.containsKey(3));
        ModelReference independent=ModelReference.load(TestModels.files(),date);
        assertTrue("date-family genuine watch reference missing",independent.far("six_rot").length>0);
    }

    @Test public void cleanDialAgainstTheRealReferenceIsAssessedWithoutDefects(){
        for(String id:new String[]{"gmt_126710","submariner_124060","submariner_126610"}){
            ModelSpec model=spec(id);AlignmentReference ref=AlignmentReference.load(TestModels.files(),model);
            LuminousAlignmentTest.Scene sc=new LuminousAlignmentTest.Scene();
            for(ModelSpec.Marker mk:model.markers){
                double inset=ref.shape(mk.shape).insetR;
                if(mk.shape==ModelSpec.Shape.ROUND){double[] c=mk.masterPoint();sc.otherCircles.add(new double[]{c[0],c[1],mk.outerR-inset});}
                else sc.others.add(LuminousAlignment.inset(LuminousAlignment.masterPolygon(mk,new double[]{0,0,0,0}),inset));
            }
            Map<Integer,Alpha99MarkerInterference.Check> checks=new HashMap<>();
            for(ModelSpec.Marker mk:model.markers){Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(mk.hour);c.clean=true;checks.put(mk.hour,c);}
            List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
            for(ModelSpec.Marker sm:model.ringMarkers()){Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(sm.hour,sm.kind());m.spec=sm;m.usable=true;m.canonDx=0;m.canonDy=0;ms.add(m);}
            Alpha94MarkerMeasurement.Report rep=new Alpha94MarkerMeasurement.Report(ms,new Alpha94MarkerMeasurement.Ring(),LuminousAlignmentTest.RPX);
            AlignmentAnalysis.Output o=AlignmentAnalysis.run((mk,ring)->sc,LuminousAlignmentTest.H,rep,checks,model,null,ref,ref::setup);
            assertEquals(model.markers.size(),o.markers.size());
            int assessed=0;
            for(AlignmentDecision.Marker m:o.markers){
                assertNotEquals(id+" "+m.hour+" "+m.reason,AlignmentDecision.Outcome.DEFECT,m.outcome);
                if(m.outcome==AlignmentDecision.Outcome.NO_DEFECT)assessed++;
            }
            assertTrue(id+": only "+assessed+" markers assessed",assessed>=model.markers.size()-1);
        }
    }
}
