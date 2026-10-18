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

    static final double POS=100*ClearlyOff.POS_LIMIT_R;
    @Test public void withinLimitsIsNoVisibleIssue(){
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.ROUND,0.6*POS,-0.6*POS,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.BATON,0.2,0.2,2.2),1e9));
    }
    @Test public void beyondAnyLimitIsClearlyOff(){
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,POS+0.05,0,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,0,-POS-0.05,Double.NaN),1e9));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.BATON,0,0,-2.3),1e9));
    }
    @Test public void positionIsADistanceInAnyDirection(){
        // the owner's circle around where the centre should be: 0.75 POS sideways and 0.75 POS out is 1.06 POS away
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,0.75*POS,0.75*POS,Double.NaN),1e9));
        ClearlyOff.Marker r=m(ModelSpec.Shape.ROUND,0.75*POS,0.75*POS,Double.NaN);decide(r,1e9);
        assertTrue(ClearlyOff.headline(r).contains("from where it should be"));
    }
    @Test public void ownerLimitsReplaceTheDefaults(){
        ClearlyOff.Limits L=ClearlyOff.Limits.defaults();assertTrue(L.isDefault());
        L.roundPosR=0.008;assertFalse(L.isDefault());
        ClearlyOff.Marker r=m(ModelSpec.Shape.ROUND,0.9,0,Double.NaN);
        ClearlyOff.decide(r,1e9,in(r.shape),ClearlyOff.Limits.defaults());assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,r.state);
        ClearlyOff.decide(r,1e9,in(r.shape),L);assertEquals(ClearlyOff.State.CLEARLY_OFF,r.state);
        // triangle rotation is not judged by default; once set it is
        ClearlyOff.Marker t=m(ModelSpec.Shape.TRIANGLE,0,0,3.0);t.gapPct=3.0;
        ClearlyOff.decide(t,1e9,in(t.shape),ClearlyOff.Limits.defaults());assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,t.state);
        ClearlyOff.Limits T=ClearlyOff.Limits.defaults();T.triTiltDeg=2.0;
        ClearlyOff.decide(t,1e9,in(t.shape),T);assertEquals(ClearlyOff.State.CLEARLY_OFF,t.state);
        // a withheld marker stays withheld whatever the limits
        ClearlyOff.Result res=new ClearlyOff.Result();res.rpx=1e9;ClearlyOff.Marker g=m(ModelSpec.Shape.ROUND,5,0,Double.NaN);g.gate.add("hand");
        ClearlyOff.decide(g,1e9,in(g.shape));res.markers.add(g);res.markers.add(r);
        ClearlyOff.rescore(res,ClearlyOff.Limits.defaults());
        assertEquals(ClearlyOff.State.CANT_CHECK,g.state);assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,r.state);
        assertTrue(res.limits.isDefault());
    }
    static ClearlyOff.Marker tri(double inout,double tilt,double gap){ClearlyOff.Marker r=m(ModelSpec.Shape.TRIANGLE,0,inout,tilt);r.gapPct=gap;return r;}
    @Test public void triangleHasItsOwnInOutLimitAndNoTiltVote(){
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(tri(1.2,5.0,3.0),1e9));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(tri(1.3,0,3.0),1e9));
    }
    @Test public void triangleNeedsAClearGapUnderTheMinuteTrack(){
        // the owner's "clear gap": genuine 2.34 % or more; their BLNR photos 1.08 % and 1.90 % with the centre in place
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(tri(0.0,0,2.34),250));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(tri(0.6,0,1.08),278));
        ClearlyOff.Marker two=tri(0.03,0,1.90);
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(two,366));
        assertTrue(ClearlyOff.headline(two).contains("clear under the minute track"));
        // 0.5 px allowance: 2.2 % less 0.25 % on a 200 px dial radius
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(tri(0,0,1.97),200));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(tri(0,0,1.93),200));
        assertEquals("no gap reading, no pass",ClearlyOff.State.CANT_CHECK,decide(tri(0,0,Double.NaN),1e9));
    }
    @Test public void lowResolutionAllowance(){
        // 0.5 px on a 200 px dial radius = 0.25 % of the radius
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,decide(m(ModelSpec.Shape.ROUND,POS+0.2,0,Double.NaN),200));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,decide(m(ModelSpec.Shape.ROUND,POS+0.3,0,Double.NaN),200));
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
