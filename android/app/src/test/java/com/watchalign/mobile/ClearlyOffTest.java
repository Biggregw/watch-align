package com.watchalign.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** The 1.6.0 clearly-off decision: the limits, the low-resolution allowance, fail-closed answers and the mapping onto
 *  the app's markers. */
public class ClearlyOffTest {
    static ClearlyOff.Marker m(ModelSpec.Shape s,double side,double inout,double tilt){
        ClearlyOff.Marker r=new ClearlyOff.Marker(3,s);r.sidePct=side;r.inoutPct=inout;r.tiltDeg=tilt;return r;
    }
    static List<ClearlyOff.Input> in(ModelSpec.Shape s){List<ClearlyOff.Input> l=new ArrayList<>();l.add(new ClearlyOff.Input(3,s,null,true,""));return l;}
    static ClearlyOff.State decide(ClearlyOff.Marker r,double rpx){ClearlyOff.decide(r,rpx,in(r.shape));return r.state;}

    @Test public void withinLimitsIsNoVisibleIssue(){
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.ROUND,0.95,-0.9,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.BATON,0.2,0.2,2.2),1e9));
    }
    @Test public void beyondAnyLimitIsClearlyOff(){
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,1.05,0,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,0,-1.05,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.BATON,0,0,-2.3),1e9));
    }
    @Test public void triangleHasItsOwnInOutLimitAndNoTiltVote(){
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.TRIANGLE,0,1.2,5.0),1e9));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.TRIANGLE,0,1.3,0),1e9));
    }
    @Test public void lowResolutionAllowance(){
        // 0.5 px on a 200 px dial radius = 0.25 % of the radius
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.ROUND,1.2,0,Double.NaN),200));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,1.3,0,Double.NaN),200));
    }
    @Test public void failsClosed(){
        ClearlyOff.Marker g=m(ModelSpec.Shape.ROUND,3.0,0,Double.NaN);g.gate.add("hand");
        assertEquals("a withheld marker is never judged",ClearlyOff.State.CANT_CHECK,decide(g,1e9));
        assertEquals(ClearlyOff.State.CANT_CHECK,decide(m(ModelSpec.Shape.ROUND,0.1,Double.NaN,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.CANT_CHECK,decide(m(ModelSpec.Shape.BATON,0.1,0.1,Double.NaN),1e9));
        // but a measurement that is clearly off stands even when another is missing
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,1.5,Double.NaN,Double.NaN),1e9));
    }
    @Test public void answersMapOntoTheAppsMarkers(){
        ClearlyOff.Result r=new ClearlyOff.Result();
        ClearlyOff.Marker off=m(ModelSpec.Shape.ROUND,1.5,0,Double.NaN);decide(off,1e9);r.markers.add(off);
        List<AlignmentDecision.Marker> ms=new ArrayList<>();
        AlignmentDecision.Marker a=new AlignmentDecision.Marker(3,ModelSpec.Shape.ROUND),b=new AlignmentDecision.Marker(4,ModelSpec.Shape.ROUND);
        b.outcome=AlignmentDecision.Outcome.NO_DEFECT;b.metalBorderline=true;ms.add(a);ms.add(b);
        AlignmentDecision.applyClearlyOff(ms,r);
        assertEquals(AlignmentDecision.Outcome.DEFECT,a.outcome);assertEquals("Clearly off",a.label());
        assertEquals("a marker the check did not return is never passed",AlignmentDecision.Outcome.CANNOT_ASSESS,b.outcome);
        assertFalse(b.worthALook());assertEquals("Can't check",b.label());
        AlignmentDecision.applyClearlyOff(ms,null);
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,a.outcome);
        // a photo-wide reason (e.g. too low a resolution) is what the owner sees for every marker
        AlignmentDecision.applyClearlyOff(ms,r,"the photo's resolution is too low");
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,a.outcome);assertEquals("the photo's resolution is too low",a.reason);
    }
}
