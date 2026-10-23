package com.watchalign.mobile;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The research comparison (off by default) judges the new readings at the two candidates and never decides a result. */
public class ResearchComparisonTest {

    static MarkerGeometry.Result result(double tang6,double tilt6){
        MarkerGeometry.Result g=new MarkerGeometry.Result();
        for(int h=1;h<=12;h++){
            ModelSpec.Shape s=h==12?ModelSpec.Shape.TRIANGLE:h%3==0?ModelSpec.Shape.BATON:ModelSpec.Shape.ROUND;
            MarkerGeometry.Marker m=new MarkerGeometry.Marker(h,s,h!=2);
            if(h!=2){m.tangPct=h==6?tang6:0.05;m.radPct=0.02;m.tiltDeg=s==ModelSpec.Shape.ROUND?Double.NaN:h==6?tilt6:0.1;}
            g.markers.add(m);
        }
        g.ring.ok=true;g.ring.n=9;g.ring.placeXPct=-0.010;g.ring.placeYPct=0.007;g.ring.rotDeg=0.006;g.ring.scalePct=-0.016;
        return g;
    }

    static ResearchComparison.Row row(List<ResearchComparison.Row> rows,String what){
        for(ResearchComparison.Row r:rows)if(r.what.equals(what))return r;
        throw new AssertionError(what);
    }

    @Test public void judgesEachReadingAtTheTwoCandidates(){
        double sig=ResearchComparison.SIG_POS;
        List<ResearchComparison.Row> within=ResearchComparison.rows(null,result(0.10,0.1));
        assertEquals(ResearchComparison.Level.WITHIN,row(within,"6 baton").level);
        // 4.2 sigma: past the 5 % candidate (k 3.83), inside the stricter one (k 4.56)
        List<ResearchComparison.Row> mid=ResearchComparison.rows(null,result(4.2*sig,0.1));
        assertEquals(ResearchComparison.Level.OUTSIDE_5,row(mid,"6 baton").level);
        List<ResearchComparison.Row> far=ResearchComparison.rows(null,result(0.6,0.1));
        assertEquals(ResearchComparison.Level.OUTSIDE_STRICT,row(far,"6 baton").level);
        assertEquals("new only",row(far,"6 baton").agreement());   // no existing check passed in: nothing flagged there
        // a turned baton is judged on its own scale
        assertEquals(ResearchComparison.Level.OUTSIDE_STRICT,row(ResearchComparison.rows(null,result(0.05,2.0)),"6 baton").level);
    }

    @Test public void ineligibleMarkersAreNotAssessedAndTheRingIsAFindingOfItsOwn(){
        List<ResearchComparison.Row> rows=ResearchComparison.rows(null,result(0.05,0.1));
        assertEquals(ResearchComparison.Level.NOT_ASSESSED,row(rows,"2 round").level);
        ResearchComparison.Row ring=row(rows,"whole ring");
        assertEquals(ResearchComparison.Level.WITHIN,ring.level);
        MarkerGeometry.Result moved=result(0.05,0.1);moved.ring.placeXPct+=0.5;
        assertTrue(row(ResearchComparison.rows(null,moved),"whole ring").newFlag());
        // a moved ring does not flag any marker by itself
        for(ResearchComparison.Row r:ResearchComparison.rows(null,moved))if(!r.what.equals("whole ring"))assertFalse(r.what,r.newFlag());
    }

    @Test public void theTriangleInOutIsNotJudged(){
        MarkerGeometry.Result g=result(0.05,0.1);g.at(12).radPct=2.0;   // far out radially: the top edge decides that, not this
        assertEquals(ResearchComparison.Level.WITHIN,row(ResearchComparison.rows(null,g),"12 triangle").level);
    }

    @Test public void offByDefaultAndNothingWithoutReadings(){
        assertFalse(MarkerGeometry.ENABLED);
        assertTrue(ResearchComparison.rows(null,null).isEmpty());
        assertTrue(ResearchComparison.text(null,result(0.05,0.1)).contains("whole ring"));
    }
}
