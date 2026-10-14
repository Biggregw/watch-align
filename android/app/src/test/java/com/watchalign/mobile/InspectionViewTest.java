package com.watchalign.mobile;

import org.junit.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

/** The simplified screens never call a withheld measurement within range and never show a withheld percentage. */
public class InspectionViewTest {
    private static MarkerPolygonMismatch.Score score(){
        return MarkerPolygonMismatch.compare(new double[][]{{0,0},{1,0},{1,1},{0,1}},new double[][]{{.1,0},{1.1,0},{1.1,1},{.1,1}});
    }
    private static Alpha99Findings.Finding finding(Alpha99Findings.Status st){
        Alpha99Findings.Finding f=new Alpha99Findings.Finding("baton9","9 o'clock","9");f.hour=9;f.status=st;f.shape=Alpha99Findings.Shape.BATON;return f;
    }
    @Test public void withinOnlyWhenNothingWasWithheld(){
        MarkerMismatchResearch.Result trusted=new MarkerMismatchResearch.Result(score(),"measured");
        assertEquals(InspectionView.State.WITHIN,InspectionView.state(finding(Alpha99Findings.Status.WITHIN),trusted));
        assertEquals("No finding",InspectionView.label(InspectionView.State.WITHIN));
        assertTrue(InspectionView.explain(finding(Alpha99Findings.Status.WITHIN),trusted).contains("area is measured, not judged"));
        MarkerMismatchResearch.Result withheld=new MarkerMismatchResearch.Result(score(),"hand/glare or interference not cleared");
        assertEquals(InspectionView.State.PARTLY,InspectionView.state(finding(Alpha99Findings.Status.WITHIN),withheld));
        assertEquals(InspectionView.State.PARTLY,InspectionView.state(finding(Alpha99Findings.Status.WITHIN),null));
        Alpha99Findings.Finding part=finding(Alpha99Findings.Status.WITHIN);part.partNotAssessed="position";part.partReason="opposing edges inconsistent";
        assertEquals(InspectionView.State.PARTLY,InspectionView.state(part,trusted));
        assertTrue(InspectionView.explain(part,withheld).contains("position and area not assessed"));
        assertFalse(InspectionView.label(InspectionView.State.PARTLY).toLowerCase().contains("within"));
        assertFalse(InspectionView.label(InspectionView.State.MINOR).toLowerCase().contains("within"));
        assertFalse(InspectionView.label(InspectionView.State.NOT_ASSESSED).toLowerCase().contains("within"));
    }
    @Test public void verdictsAreTheExistingOnes(){
        MarkerMismatchResearch.Result trusted=new MarkerMismatchResearch.Result(score(),"measured");
        assertEquals(InspectionView.State.CLEAR,InspectionView.state(finding(Alpha99Findings.Status.CLEAR),trusted));
        assertEquals(InspectionView.State.WORTH,InspectionView.state(finding(Alpha99Findings.Status.WORTH),null));
        assertEquals(InspectionView.State.MINOR,InspectionView.state(finding(Alpha99Findings.Status.MINOR),trusted));
        assertEquals(InspectionView.State.NOT_ASSESSED,InspectionView.state(finding(Alpha99Findings.Status.NOT_ASSESSED),trusted));
        Alpha99Findings.Finding ring=finding(Alpha99Findings.Status.WITHIN);ring.dialWide=true;ring.shape=Alpha99Findings.Shape.RING;
        assertEquals(InspectionView.State.WITHIN,InspectionView.state(ring,null));
        assertNull(InspectionView.area(ring,null));
    }
    @Test public void dialFeaturesNeverBorrowAMarkersAreaResult(){
        Map<Integer,MarkerMismatchResearch.Result> mm=new HashMap<>();mm.put(12,new MarkerMismatchResearch.Result(score(),"provisional"));
        for(Alpha99Findings.Shape sh:new Alpha99Findings.Shape[]{Alpha99Findings.Shape.RING,Alpha99Findings.Shape.DATE}){
            Alpha99Findings.Finding f=finding(Alpha99Findings.Status.WITHIN);f.shape=sh;f.hour=12;
            assertNull(InspectionView.result(f,mm));
            assertEquals(InspectionView.State.WITHIN,InspectionView.state(f,InspectionView.result(f,mm)));
            assertNull(InspectionView.area(f,InspectionView.result(f,mm)));
        }
    }
    @Test public void longMeasureSentencesKeepOnlyTheirFirstSentence(){
        Alpha99Findings.Finding f=finding(Alpha99Findings.Status.WORTH);
        f.measures.add(new Alpha99Findings.Measure("track",0.01,0.005,0.001,3,40,"R","x","Its fitted radial position reads 2.2 px closer to the minute track. This is a position-based estimate"));
        assertEquals("Its fitted radial position reads 2.2 px closer to the minute track (clear deviation)",InspectionView.findings(f).get(0));
    }
    @Test public void percentageOnlyForAClearedComparison(){
        MarkerPolygonMismatch.Score s=score();
        String shown=InspectionView.area(finding(Alpha99Findings.Status.WITHIN),new MarkerMismatchResearch.Result(s,"measured"));
        assertEquals(String.format(java.util.Locale.US,"Area mismatch %.2f%%",s.mismatchPct),shown);
        for(String reason:new String[]{"partial measurement withheld: position","hand/glare or interference not cleared","provisional: radial edges of 12 vary with polished-surround lighting"}){
            MarkerMismatchResearch.Result q=new MarkerMismatchResearch.Result(s,reason);
            assertEquals("Area: not assessed",InspectionView.area(finding(Alpha99Findings.Status.WITHIN),q));
            assertNull(InspectionView.areaParts(q));
            assertNotNull(InspectionView.areaReason(finding(Alpha99Findings.Status.WITHIN),q));
        }
        Alpha99Findings.Finding tri=finding(Alpha99Findings.Status.WORTH);tri.shape=Alpha99Findings.Shape.TRIANGLE;
        assertTrue(InspectionView.areaReason(tri,new MarkerMismatchResearch.Result(s,"provisional")).contains("inferred"));
    }
    @Test public void headlineCountsOnlyFlaggedMarkersIndividually(){
        Alpha99Findings.Summary sum=new Alpha99Findings.Summary();Map<Integer,MarkerMismatchResearch.Result> mm=new HashMap<>();
        Alpha99Findings.Finding a=finding(Alpha99Findings.Status.WORTH),b=finding(Alpha99Findings.Status.WITHIN),c=finding(Alpha99Findings.Status.CLEAR);
        a.hour=6;b.hour=9;c.hour=3;sum.all.add(a);sum.all.add(b);sum.all.add(c);
        assertEquals("2 markers flagged",InspectionView.headline(sum,mm));
        assertEquals("1 clear deviation  ·  1 worth a look  ·  1 partly assessed",InspectionView.counts(sum,mm));
        mm.put(9,new MarkerMismatchResearch.Result(score(),"measured"));
        assertEquals("1 clear deviation  ·  1 worth a look  ·  1 no finding",InspectionView.counts(sum,mm));
    }
    @Test public void findingLinesComeFromTheExistingMeasures(){
        Alpha99Findings.Finding f=finding(Alpha99Findings.Status.WORTH);
        f.measures.add(new Alpha99Findings.Measure("rotation",1.24,1.0,0.3,3,40,"deg","1.2° CW","It is rotated 1.2° clockwise (0.4° beyond genuine)"));
        f.measures.add(new Alpha99Findings.Measure("offset",0.1,1.0,0.3,3,40,"px","0.1 px","It sits 0.1 px to the left"));
        assertEquals(java.util.Collections.singletonList("Rotated 1.2° clockwise (worth a look)"),InspectionView.findings(f));
    }
}
