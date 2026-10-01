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

/** Experimental 124060 path: layout, frozen triangle selection rules, fail-closed gates, no-verdict summary. */
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
        // 38 and 50 deg are far outside the GMT 44.3 +-2 deg gate but inside the broad 124060 shape window.
        for(double apex:new double[]{38.0,50.0}){
            SubTwelveTriangle.Cand c=clean();c.apex=apex;judged(c);
            assertTrue("apex "+apex,c.plausible);
        }
        SubTwelveTriangle.Cand c=clean();c.apex=62.0;judged(c);   // an inscribed shape in a round marker
        assertFalse(c.plausible);assertEquals("shape",c.implausible);
    }

    @Test public void implausibleCandidatesAreRejectedWithAReason(){
        SubTwelveTriangle.Cand a=clean();a.rho=0.50;judged(a);assertEquals("rho",a.implausible);       // e.g. the crown logo
        SubTwelveTriangle.Cand b=clean();b.widthR=0.40;judged(b);assertEquals("width",b.implausible);
        SubTwelveTriangle.Cand g=clean();g.gapR=0.15;judged(g);assertEquals("track_gap",g.implausible);  // track found on the bezel
        SubTwelveTriangle.Cand s=clean();s.sym=0.30;s.square=9;s.completeness=0.2;judged(s);assertEquals("score",s.implausible);
    }

    @Test public void selectionIsTheLowestScoringPlausibleCandidate(){
        SubTwelveTriangle.Cand worse=clean();worse.dthetaDeg=6.0;judged(worse);
        SubTwelveTriangle.Cand best=judged(clean());
        SubTwelveTriangle.Cand bad=clean();bad.rho=0.5;judged(bad);bad.score=-5;   // implausible ranks last whatever its score
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
        assertTrue(Sub124060QcAnalyzer.sameDial(500,500,400,503,502,406));      // 3.6 px < 4 px, +1.5%
        assertFalse(Sub124060QcAnalyzer.sameDial(500,500,400,505,500,400));     // 5 px > 0.01 R
        assertFalse(Sub124060QcAnalyzer.sameDial(500,500,400,500,500,410));     // +2.5%
        assertFalse(Sub124060QcAnalyzer.sameDial(500,500,0,500,500,0));
    }

    @Test public void sameOutlineUsesTheResearchGrouping(){
        assertTrue(Sub124060QcAnalyzer.sameOutline(100,100,0.25,105,100,0.27,400));   // 5 px < 8 px, +8%
        assertFalse(Sub124060QcAnalyzer.sameOutline(100,100,0.25,110,100,0.25,400));  // 10 px > 0.02 R
        assertFalse(Sub124060QcAnalyzer.sameOutline(100,100,0.25,100,100,0.19,400));  // lume vs surround width
    }

    // ---------------------------------------------------------------------------------------- summary: no verdicts
    static final String[] VERDICT_WORDS={"OK","worth a look","CHECK CLOSELY","Check closely","!!","STRONG","CLEAR","PASS","FAIL","nothing flagged"};

    static void assertNoVerdict(String s){
        for(String w:VERDICT_WORDS)assertFalse("verdict word '"+w+"' in:\n"+s,s.contains(w));
        for(String gmt:new String[]{"date window","Sprite","date side","GMT-Master"})assertFalse(gmt,s.contains(gmt));
    }

    static Sub124060QcAnalyzer.Result measured(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.dialSource=Sub124060QcAnalyzer.DialSource.AUTO_EDGE_FIT;r.dialReproducible=true;
        r.triangle=clean();r.rotationDeg=0.4;r.gapR=0.041;r.centringW=0.006;
        for(GmtSixLandmarkAnalyzer.Position p:Sub124060Layout.BATONS){Sub124060QcAnalyzer.Baton b=new Sub124060QcAnalyzer.Baton(p);b.status=Sub124060QcAnalyzer.Status.FOUND;r.batons.add(b);}
        for(int h:Sub124060Layout.ROUND_HOURS){GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);m.found=true;
            r.rounds.add(new Sub124060QcAnalyzer.Round(m,h==8?Sub124060QcAnalyzer.Status.HAND:Sub124060QcAnalyzer.Status.FOUND,""));}
        return r;
    }

    @Test public void measuredValuesAreLabelledMeasuredNotYetJudged(){
        String s=Sub124060Summary.build(measured());
        assertTrue(s.startsWith("SUMMARY\n"+Sub124060Summary.EXPERIMENTAL));
        assertEquals(3,count(s,Sub124060Summary.MEASURED));
        assertTrue(s.contains("Dial: automatic edge fit"));
        assertTrue(s.contains("+0.40°"));assertTrue(s.contains("0.041"));assertTrue(s.contains("+0.006"));
        assertTrue(s.contains("Batons 3/6/9: 3 of 3 found"));
        assertTrue(s.contains("Round markers: 8 of 8 found (8: hand in the way)"));
        assertTrue(s.contains(Sub124060Summary.NOT_CHECKED));
        assertNoVerdict(s);
    }

    @Test public void withheldValuesSayWhyAndCarryNoNumber(){
        Sub124060QcAnalyzer.Result r=measured();
        r.rotationDeg=r.gapR=r.centringW=Double.NaN;
        r.twelveWithheld=r.rotationWithheld=r.gapWithheld=r.centringWithheld="a hand is at the 12 triangle";
        String s=Sub124060Summary.build(r);
        assertEquals(0,count(s,Sub124060Summary.MEASURED));
        assertEquals(3,count(s,"not measured (a hand is at the 12 triangle)"));
        assertNoVerdict(s);
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
        assertNoVerdict(s);
    }

    @Test public void overlayDrawingIsEmptyWithoutDialGeometry(){
        Sub124060Overlay.Drawing d=Sub124060Overlay.Drawing.of(new Sub124060QcAnalyzer.Result());
        assertFalse(d.hasAnything());
    }

    private static int count(String s,String w){int n=0,i=0;while((i=s.indexOf(w,i))>=0){n++;i+=w.length();}return n;}
}
