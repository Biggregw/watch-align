package com.watchalign.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 124060 path: layout, frozen triangle rules, fail-closed gates and calibrated GMT-style presentation. */
public class Sub124060Test {

    // ---------------------------------------------------------------------------------------- layout
    @Test public void layoutIsTheExplicit124060Layout(){
        assertEquals("triangle",Sub124060Layout.kindAt(12));
        for(int h:new int[]{3,6,9})assertEquals("baton",Sub124060Layout.kindAt(h));
        for(int h:new int[]{1,2,4,5,7,8,10,11})assertEquals("round",Sub124060Layout.kindAt(h));
        assertFalse(Sub124060Layout.HAS_DATE);
        assertArrayEquals(GmtRoundMarkerAnalyzer.HOURS,Sub124060Layout.ROUND_HOURS);
        assertEquals(3,Sub124060Layout.BATONS.length);
        assertSame(GmtSixLandmarkAnalyzer.Position.THREE,Sub124060Layout.BATONS[0]);
    }

    // ---------------------------------------------------------------------------------------- frozen triangle v2
    /** A clean development-like candidate: rho 0.79, width 0.25 R, apex 44, gap 0.04 R, complete refit. */
    static SubTwelveTriangle.Cand clean(){
        SubTwelveTriangle.Cand c=new SubTwelveTriangle.Cand();
        c.rho=0.79;c.widthR=0.25;c.heightR=0.31;c.apex=44.0;c.square=0.5;c.sym=0.01;c.axisDeg=0.4;c.dthetaDeg=0.2;
        c.gapR=0.04;c.trackR=380;c.completeness=1.0;c.residualR=0.001;c.base=new SubTwelveTriangle.Side();
        c.outline="single";c.masks=3;c.outsideTrack=false;
        return c;
    }
    static SubTwelveTriangle.Cand judged(SubTwelveTriangle.Cand c){SubTwelveTriangle.score(c);SubTwelveTriangle.classify(c);return c;}

    @Test public void frozenConstantsAreUnchanged(){
        assertEquals("sub-triangle-v2",SubTwelveTriangle.VERSION);
        assertEquals(0.68,SubTwelveTriangle.PLAUS_RHO_LO,0);assertEquals(0.92,SubTwelveTriangle.PLAUS_RHO_HI,0);
        assertEquals(0.12,SubTwelveTriangle.PLAUS_W_LO,0);assertEquals(0.35,SubTwelveTriangle.PLAUS_W_HI,0);
        assertEquals(-0.01,SubTwelveTriangle.PLAUS_GAP_LO,0);assertEquals(0.10,SubTwelveTriangle.PLAUS_GAP_HI,0);
        assertEquals(30.0,SubTwelveTriangle.PLAUS_APEX_LO,0);assertEquals(58.0,SubTwelveTriangle.PLAUS_APEX_HI,0);
        assertEquals(0.9,SubTwelveTriangle.PLAUS_HW_LO,0);assertEquals(1.9,SubTwelveTriangle.PLAUS_HW_HI,0);
        assertEquals(6.5,SubTwelveTriangle.SCORE_MAX,0);
    }

    @Test public void aCleanCandidateIsPlausible(){
        SubTwelveTriangle.Cand c=judged(clean());
        assertTrue(c.plausible);
        assertTrue(c.score<=SubTwelveTriangle.SCORE_MAX);
    }

    @Test public void noGmtApexGate(){
        for(double apex:new double[]{38.0,50.0}){
            SubTwelveTriangle.Cand c=clean();c.apex=apex;judged(c);
            assertTrue("apex "+apex,c.plausible);
        }
        SubTwelveTriangle.Cand c=clean();c.apex=62.0;judged(c);
        assertFalse(c.plausible);assertEquals("shape",c.implausible);
    }

    @Test public void implausibleCandidatesAreRejectedWithAReason(){
        SubTwelveTriangle.Cand a=clean();a.rho=0.50;judged(a);assertEquals("rho",a.implausible);
        SubTwelveTriangle.Cand b=clean();b.widthR=0.40;judged(b);assertEquals("width",b.implausible);
        SubTwelveTriangle.Cand g=clean();g.gapR=0.15;judged(g);assertEquals("track_gap",g.implausible);
        SubTwelveTriangle.Cand s=clean();s.sym=0.30;s.square=9;s.completeness=0.2;judged(s);assertEquals("score",s.implausible);
    }

    @Test public void selectionIsTheLowestScoringPlausibleCandidate(){
        SubTwelveTriangle.Cand worse=clean();worse.dthetaDeg=6.0;judged(worse);
        SubTwelveTriangle.Cand best=judged(clean());
        SubTwelveTriangle.Cand bad=clean();bad.rho=0.5;judged(bad);bad.score=-5;
        List<SubTwelveTriangle.Cand> l=new ArrayList<>();l.add(bad);l.add(worse);l.add(best);
        SubTwelveTriangle.rank(l);
        SubTwelveTriangle.Result r=new SubTwelveTriangle.Result();r.cands=l;
        assertSame(best,r.best());
        assertEquals(1,best.rank);assertEquals(3,bad.rank);
    }

    @Test public void noSelectionWhenNothingIsPlausible(){
        SubTwelveTriangle.Cand bad=clean();bad.rho=0.5;judged(bad);
        List<SubTwelveTriangle.Cand> l=new ArrayList<>();l.add(bad);SubTwelveTriangle.rank(l);
        SubTwelveTriangle.Result r=new SubTwelveTriangle.Result();r.cands=l;
        assertNull(r.best());
        assertNull(new SubTwelveTriangle.Result().best());
    }

    // ---------------------------------------------------------------------------------------- fail-closed gates
    @Test public void dialReproducibilityUsesTheResearchRule(){
        assertTrue(Sub124060QcAnalyzer.sameDial(500,500,400,503,502,406));
        assertFalse(Sub124060QcAnalyzer.sameDial(500,500,400,505,500,400));
        assertFalse(Sub124060QcAnalyzer.sameDial(500,500,400,500,500,410));
        assertFalse(Sub124060QcAnalyzer.sameDial(500,500,0,500,500,0));
    }

    @Test public void sameOutlineUsesTheResearchGrouping(){
        assertTrue(Sub124060QcAnalyzer.sameOutline(100,100,0.25,105,100,0.27,400));
        assertFalse(Sub124060QcAnalyzer.sameOutline(100,100,0.25,110,100,0.25,400));
        assertFalse(Sub124060QcAnalyzer.sameOutline(100,100,0.25,100,100,0.19,400));
    }

    // ---------------------------------------------------------------------------------------- calibrated summary
    static void assertNoGmtContamination(String s){
        for(String gmt:new String[]{"date window","Sprite","date side","GMT-Master"})assertFalse(gmt,s.contains(gmt));
    }

    static Sub124060QcAnalyzer.Result measured(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.dialSource=Sub124060QcAnalyzer.DialSource.AUTO_EDGE_FIT;r.dialReproducible=true;
        r.triangle=clean();r.rotationDeg=0.4;r.gapR=0.041;r.centringW=0.006;
        r.rotationResizeStable=true;r.gapResizeStable=true;r.centringResizeStable=true;
        for(GmtSixLandmarkAnalyzer.Position p:Sub124060Layout.BATONS){Sub124060QcAnalyzer.Baton b=new Sub124060QcAnalyzer.Baton(p);b.status=Sub124060QcAnalyzer.Status.FOUND;r.batons.add(b);}
        for(int h:Sub124060Layout.ROUND_HOURS){GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);m.found=true;
            r.rounds.add(new Sub124060QcAnalyzer.Round(m,h==8?Sub124060QcAnalyzer.Status.HAND:Sub124060QcAnalyzer.Status.FOUND,""));}
        return r;
    }

    @Test public void measuredTwelveIsJudgedByProvisionalCalibration(){
        String s=Sub124060Summary.build(measured());
        assertTrue(s.startsWith("SUMMARY\n"+Sub124060Summary.EXPERIMENTAL));
        assertTrue(s.contains("Dial: automatic edge fit"));
        assertTrue(s.contains("12 rotation: +0.40°"));
        assertTrue(s.contains("12 centring: +0.006"));
        assertTrue(s.contains("12 overall alignment: CLEAR"));
        assertFalse(s.contains("CHECK CLOSELY"));
        assertTrue(s.contains("12 gap to minute track: 0.041"));
        assertTrue(s.contains("0.041 of the dial radius between the triangle's top edge and the minute-track inner edge · "+Sub124060Summary.MEASURED));   // gap measured-only
        assertTrue(s.contains("Batons 3/6/9: 3/3 detected, 3/3 measurable"));
        assertTrue(s.contains("Round markers: 8/8 detected, 7/8 measurable (8: detected, not measured (hand in the way))"));
        assertTrue(s.contains("GMT-style whole-dial markup"));
        assertTrue(s.contains("Tick = nothing flagged"));
        assertTrue(s.contains(Sub124060Summary.NOT_CHECKED));
        assertNoGmtContamination(s);
    }

    @Test public void detectedAndMeasurableAreNotConflated(){
        Sub124060QcAnalyzer.Result r=measured();
        r.batons.get(1).status=Sub124060QcAnalyzer.Status.HAND;
        String s=Sub124060Summary.build(r);
        assertTrue(s.contains("Batons 3/6/9: 3/3 detected, 2/3 measurable"));
        assertTrue(s.contains("6: detected, not measured (hand in the way)"));
        assertNoGmtContamination(s);
    }

    @Test public void withheldValuesSayWhyAndDoNotReceiveVerdicts(){
        Sub124060QcAnalyzer.Result r=measured();
        r.rotationDeg=r.gapR=r.centringW=Double.NaN;
        r.twelveWithheld=r.rotationWithheld=r.gapWithheld=r.centringWithheld="a hand is at the 12 triangle";
        String s=Sub124060Summary.build(r);
        assertEquals(3,count(s,"not measured (a hand is at the 12 triangle)"));
        assertTrue(s.contains("12 overall alignment: NOT JUDGED"));
        assertNoGmtContamination(s);
    }

    @Test public void unstableTwelveMeasurementsStayUnjudged(){
        Sub124060QcAnalyzer.Result r=measured();
        r.rotationResizeStable=false;r.centringResizeStable=null;
        Sub124060Calibration.Assessment a=Sub124060Calibration.assess(r);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.rotation);
        assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,a.centring);
        String s=Sub124060Summary.build(r);
        assertTrue(s.contains("12 rotation: +0.40°"));
        assertTrue(s.contains("· NOT JUDGED"));
    }

    @Test public void dialSourcesAreDistinguished(){
        Sub124060QcAnalyzer.Result r=measured();
        r.dialSource=Sub124060QcAnalyzer.DialSource.MANUAL_EDGE_FIT;
        assertTrue(Sub124060Summary.build(r).contains("Dial: hand-aligned, then the dial edge was re-fitted"));
        r.dialSource=Sub124060QcAnalyzer.DialSource.MANUAL_CIRCLE;
        assertTrue(Sub124060Summary.build(r).contains("Dial: hand-aligned circle only"));
        Sub124060QcAnalyzer.Result u=new Sub124060QcAnalyzer.Result();u.dialReason="the dial edge could not be fitted automatically";
        String s=Sub124060Summary.build(u);
        assertTrue(s.contains("Dial: not assessable: the dial edge could not be fitted automatically"));
        assertTrue(s.contains("Align dial edge by hand"));
        assertTrue(u.needsManual());
        assertNoGmtContamination(s);
    }

    @Test public void overlayDrawingIsEmptyWithoutDialGeometry(){
        Sub124060Overlay.Drawing d=Sub124060Overlay.Drawing.of(new Sub124060QcAnalyzer.Result());
        assertFalse(d.hasAnything());
    }

    @Test public void overlayUsesGmtStyleVerdictContract(){
        assertTrue(Sub124060Overlay.BANNER.contains("provisional family calibration"));
        GmtHumanQcMath.Attention u=GmtHumanQcMath.Attention.UNASSESSABLE;
        assertFalse(Sub124060Overlay.neutral(u,true));
        assertEquals(Sub124060Overlay.WITHHELD,Sub124060Overlay.ink(u,true));
        assertEquals(Sub124060Overlay.CLEAR,Sub124060Overlay.colour(GmtHumanQcMath.Attention.CLEAR));
        assertEquals(Sub124060Overlay.CHECK,Sub124060Overlay.colour(GmtHumanQcMath.Attention.CHECK));
        assertEquals(Sub124060Overlay.STRONG,Sub124060Overlay.colour(GmtHumanQcMath.Attention.STRONG));
        assertEquals(Sub124060Overlay.WITHHELD,Sub124060Overlay.colour(GmtHumanQcMath.Attention.UNASSESSABLE));
    }

    private static int count(String s,String w){int n=0,i=0;while((i=s.indexOf(w,i))>=0){n++;i+=w.length();}return n;}
}
