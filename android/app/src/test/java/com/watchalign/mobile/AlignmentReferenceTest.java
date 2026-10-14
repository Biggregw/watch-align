package com.watchalign.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * The bundled genuine luminous alignment references (calibration run 38565452660) load for exactly the models the app
 * offers, each with its own data, covering every marker of that model; and a smoke check that a clean synthetic dial
 * judged against the REAL reference is assessed (not "Cannot assess") and shows no defect.
 */
public class AlignmentReferenceTest {
    static ModelSpec spec(String id){try{return ModelSpec.load(TestModels.files(),id);}catch(Exception e){throw new AssertionError(e);}}

    @Test public void bothSupportedModelsLoadTheirOwnReference(){
        for(String id:new String[]{"gmt_126710","submariner_124060"}){
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
        // model isolation: the two references are different data, not one shared file
        AlignmentReference g=AlignmentReference.load(TestModels.files(),spec("gmt_126710")),s=AlignmentReference.load(TestModels.files(),spec("submariner_124060"));
        assertNotEquals(g.shape(ModelSpec.Shape.TRIANGLE).insetR,s.shape(ModelSpec.Shape.TRIANGLE).insetR,1e-9);
    }

    @Test public void unvalidatedModelHasNoReferenceAndIsNotOffered(){
        // 126610 has research data only: no alignment reference is bundled, so the start screen does not offer it
        assertNull(AlignmentReference.load(TestModels.files(),spec("submariner_126610")));
    }

    @Test public void cleanDialAgainstTheRealReferenceIsAssessedWithoutDefects(){
        for(String id:new String[]{"gmt_126710","submariner_124060"}){
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
