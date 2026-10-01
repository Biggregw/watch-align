package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** One generic GMT-Master II check; date side read from the photo (alpha61). */
public class GenericGmtTest {
    @Test public void onlyTheGenericProfileIsOffered(){
        // One GMT entry, first (the default); the 124060 checkpoint entry follows it (see SubmarinerProductionIsolationTest).
        List<ModelCatalog.Profile> m=MainActivity.offeredModels();
        int gmt=0;for(ModelCatalog.Profile p:m)if(CanonicalGmtGeometryAnalyzer.supports(p.code))gmt++;
        assertEquals(1,gmt);
        assertEquals("Rolex GMT-Master II",m.get(0).label);
        // Before alpha61's fix every 126710* profile was offered, so the selector showed three.
        int legacy=0;for(ModelCatalog.Profile p:ModelCatalog.all())if(CanonicalGmtGeometryAnalyzer.supports(p.code))legacy++;
        assertTrue(legacy>1);
    }

    @Test public void dateSideDecision(){
        assertEquals(GmtDialLayout.Layout.DATE_AT_3,GmtDialLayout.decide(0.34,0.00));   // official 126710BLNR render
        assertEquals(GmtDialLayout.Layout.DATE_AT_9,GmtDialLayout.decide(0.00,0.26));   // official 126720VTNR render
        assertEquals(GmtDialLayout.Layout.UNKNOWN,GmtDialLayout.decide(0.22,0.37));     // hand over the Sprite's 3 baton
        assertEquals(GmtDialLayout.Layout.UNKNOWN,GmtDialLayout.decide(0.36,0.09));     // hand near a standard 9
        assertEquals(GmtDialLayout.Layout.UNKNOWN,GmtDialLayout.decide(Double.NaN,0.0));
    }

    private static GmtHumanSummary.Input clearTwelve(){
        GmtHumanSummary.Input in=new GmtHumanSummary.Input();
        in.pose=GmtHumanQcMath.PoseLabel.GOOD;in.twelveValid=true;in.stableFrame=true;
        in.gap=GmtHumanQcMath.Attention.CLEAR;in.alignment=GmtHumanQcMath.Attention.CLEAR;in.observedGap=0.09;
        in.spacing59=0.14;in.spacing01=0.14;in.rotationDeg=0.2;
        return in;
    }

    @Test public void spriteLayoutReportsTheThreeBatonAndDateAtNine(){
        GmtHumanSummary.Input in=clearTwelve();
        in.layout=GmtDialLayout.Layout.DATE_AT_9;
        in.nine=new GmtHumanSummary.Baton("3",14,16);in.nine.valid=true;in.nine.stable=true;in.nine.attention=GmtHumanQcMath.Attention.CLEAR;
        in.nine.centring=0.01;in.nine.rotationDeg=0.2;
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("3 OK"));
        assertTrue(s,s.contains("9 date window (not a marker)"));
        assertTrue(s,s.contains("3 baton: centred between the 14 and 16 ticks"));
        assertFalse(s,s.contains("9 baton:"));
    }

    @Test public void standardLayoutUnchanged(){
        GmtHumanSummary.Input in=clearTwelve();
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("3 date window (not a marker)"));
        assertTrue(s,s.contains("9 baton:"));
    }

    @Test public void unknownLayoutNamesNeitherSideAsTheDate(){
        GmtHumanSummary.Input in=clearTwelve();in.layout=GmtDialLayout.Layout.UNKNOWN;
        String s=GmtHumanSummary.build(in);
        assertTrue(s,s.contains("3 not checked (date side not determined)"));
        assertFalse(s,s.contains("date window (not a marker)"));
    }

    @Test public void threeBatonOffsetIsUpOrDown(){
        GmtHumanSummary.Baton b=new GmtHumanSummary.Baton("3",14,16);
        b.valid=true;b.stable=true;b.attention=GmtHumanQcMath.Attention.CHECK;b.offCentre=true;b.centring=0.14;b.rotationDeg=0.1;
        String s=GmtHumanSummary.batonLine(b);
        assertTrue(s,s.contains("sits high (towards the 14 tick)"));
    }

    private static GmtHumanQcAnalyzerV2.BatonOutcome sideBaton(GmtSixLandmarkAnalyzer.Position pos,GmtHumanQcMath.Attention a){
        GmtHumanQcAnalyzerV2.BatonOutcome o=new GmtHumanQcAnalyzerV2.BatonOutcome(pos);
        o.result=new GmtSixLandmarkAnalyzer.Result(0.05,0.20,1.5,20,60,true,null);
        o.decision=new GmtHumanQcMath.SixDecision(a,a!=GmtHumanQcMath.Attention.CLEAR,false,false,"measured");
        return o;
    }

    @Test public void unknownLayoutGivesNoThreeOrNineVerdict(){
        // alpha62 review: UNKNOWN can't say whether the baton is at 9 (standard) or 3 (Sprite).
        for(GmtSixLandmarkAnalyzer.Position pos:new GmtSixLandmarkAnalyzer.Position[]{GmtSixLandmarkAnalyzer.Position.NINE,GmtSixLandmarkAnalyzer.Position.THREE}){
            for(GmtHumanQcMath.Attention a:new GmtHumanQcMath.Attention[]{GmtHumanQcMath.Attention.CLEAR,GmtHumanQcMath.Attention.CHECK,GmtHumanQcMath.Attention.STRONG}){
                GmtHumanQcAnalyzerV2.BatonOutcome o=GmtHumanQcAnalyzerV2.withholdIfSideUnknown(sideBaton(pos,a),GmtDialLayout.Layout.UNKNOWN);
                assertEquals(GmtHumanQcMath.Attention.UNASSESSABLE,o.decision.attention);
                GmtHumanSummary.Input in=clearTwelve();in.layout=GmtDialLayout.Layout.UNKNOWN;in.nine=o.summary();
                assertFalse(in.nine.flagged());assertFalse(in.nine.clear());
                String s=GmtHumanSummary.build(in);
                assertTrue(s,s.contains("3 not checked (date side not determined)"));
                assertTrue(s,s.contains("9 not checked (date side not determined)"));
                assertTrue(s,s.contains("3/9 baton: not checked: the date side could not be determined"));
                assertFalse(s,s.contains("9 baton position"));assertFalse(s,s.contains("3 baton position"));
                for(String h:new String[]{"3","9"})for(String v:new String[]{" OK"," worth a look"," CHECK CLOSELY"})assertFalse(s,s.contains(h+v));
                assertFalse(s,s.contains("baton is centred"));assertFalse(s,s.contains("batons are centred"));
            }
        }
        // Even a verdict that reaches the summary without the analyzer's withholding is not shown.
        GmtHumanSummary.Input in=clearTwelve();in.layout=GmtDialLayout.Layout.UNKNOWN;
        in.nine=sideBaton(GmtSixLandmarkAnalyzer.Position.NINE,GmtHumanQcMath.Attention.CHECK).summary();
        String s=GmtHumanSummary.build(in);
        assertFalse(s,s.contains("worth a look"));assertFalse(s,s.contains("9 baton position"));
        // A known layout keeps its verdict.
        GmtHumanQcAnalyzerV2.BatonOutcome k=GmtHumanQcAnalyzerV2.withholdIfSideUnknown(sideBaton(GmtSixLandmarkAnalyzer.Position.NINE,GmtHumanQcMath.Attention.CHECK),GmtDialLayout.Layout.DATE_AT_3);
        assertEquals(GmtHumanQcMath.Attention.CHECK,k.decision.attention);
    }
}
