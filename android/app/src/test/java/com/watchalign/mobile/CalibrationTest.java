package com.watchalign.mobile;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** 1.6.2 calibration: saved photos re-judged under other limits, the genuine count, verdict agreement, the suggestion,
 *  and the files (JSON) and export round trips. */
public class CalibrationTest {
    static Calibration.Mark mark(int h,ModelSpec.Shape s,double side,double inout,double tilt,double gap,Calibration.Verdict v){
        Calibration.Mark k=new Calibration.Mark();k.hour=h;k.shape=s;k.side=side;k.inout=inout;k.tilt=tilt;k.gap=gap;k.verdict=v;return k;
    }
    static Calibration.Record rec(String id,Calibration.Mark... ks){
        Calibration.Record r=new Calibration.Record();r.id=id;r.model="gmt_126710";r.label="photo "+id;r.time=Long.parseLong(id);r.rpx=1e9;
        for(Calibration.Mark k:ks)r.marks.add(k);return r;
    }

    @Test public void savedPhotosAreJudgedAgainUnderOtherLimits(){
        Calibration.Record r=rec("1",mark(1,ModelSpec.Shape.ROUND,0.9,0,Double.NaN,Double.NaN,null),
                mark(12,ModelSpec.Shape.TRIANGLE,0,0,0,1.9,null));
        ClearlyOff.Limits d=ClearlyOff.Limits.defaults(),tight=d.copy();tight.roundPosR=0.008;
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,Calibration.state(r,r.at(1),d));
        assertEquals(ClearlyOff.State.CLEARLY_OFF,Calibration.state(r,r.at(1),tight));
        assertEquals("the gap rule",ClearlyOff.State.CLEARLY_OFF,Calibration.state(r,r.at(12),d));
        List<String> ch=Calibration.changes(java.util.Arrays.asList(r),"gmt_126710",d,tight);
        assertEquals(1,ch.size());assertTrue(ch.get(0).contains("1 o'clock: No visible issue → Flagged for review"));
        // a withheld marker or a photo-wide reason never becomes an answer
        Calibration.Mark g=mark(2,ModelSpec.Shape.ROUND,5,0,Double.NaN,Double.NaN,null);g.gate="hand";g.reason="a hand reaches it";r.marks.add(g);
        assertEquals(ClearlyOff.State.CANT_CHECK,Calibration.state(r,g,tight));
        assertEquals("a hand reaches it",Calibration.judge(r,g,tight).reason);
        r.photoReason="the photo's resolution is too low";
        assertEquals(ClearlyOff.State.CANT_CHECK,Calibration.state(r,r.at(1),tight));
    }

    @Test public void genuineCountAndVerdicts(){
        Calibration.Genuine gen=Calibration.Genuine.parse("shape,rpx,side,inout,tilt,gap,relative_pct,relative_used,position_method\nround,400,0.50,0.40,,,0.64,true,position-reference-fit-v1\nround,400,0.85,0.10,,,0.86,true,position-reference-fit-v1\ntriangle,400,0.1,0.2,0.5,2.6,,false,legacy-position-v1\n");
        assertEquals(2,gen.total(ModelSpec.Shape.ROUND));assertEquals(1,gen.total(ModelSpec.Shape.TRIANGLE));
        assertEquals(2,gen.totalComparable(ModelSpec.Shape.ROUND));
        ClearlyOff.Limits d=ClearlyOff.Limits.defaults();
        assertEquals(0,gen.flagged(ModelSpec.Shape.ROUND,d));
        ClearlyOff.Limits t=d.copy();t.roundPosR=0.007;assertEquals(1,gen.flagged(ModelSpec.Shape.ROUND,t));

        List<Calibration.Record> recs=new ArrayList<>();
        recs.add(rec("1",mark(1,ModelSpec.Shape.ROUND,0.95,0,Double.NaN,Double.NaN,Calibration.Verdict.FAIL),
                mark(2,ModelSpec.Shape.ROUND,0.30,0,Double.NaN,Double.NaN,Calibration.Verdict.PASS)));
        Calibration.Agreement a=Calibration.agreement(recs,"gmt_126710",ModelSpec.Shape.ROUND,d);
        assertEquals(2,a.total);assertEquals(1,a.agree);assertEquals(1,a.disagree.size());
        // the suggestion catches the Fail (0.95, under the 1.0 % default) without failing either genuine marker (largest 0.86)
        ClearlyOff.Limits s=Calibration.suggest(recs,"gmt_126710",ModelSpec.Shape.ROUND,d,gen);
        assertNotNull(s);
        assertEquals(2,Calibration.agreement(recs,"gmt_126710",ModelSpec.Shape.ROUND,s).agree);
        assertEquals(0,gen.flagged(ModelSpec.Shape.ROUND,s));
        assertTrue(s.roundPosR*100<0.95&&s.roundPosR*100>=0.86);
        assertNull("no verdicts, no suggestion",Calibration.suggest(recs,"gmt_126710",ModelSpec.Shape.BATON,d,gen));
        Calibration.Genuine legacy=Calibration.Genuine.parse("shape,rpx,side,inout,tilt,gap\nround,400,0.85,0.10,,\n");
        assertEquals(0,legacy.totalComparable(ModelSpec.Shape.ROUND));
        assertNull("legacy genuine catalogue must not constrain a new position suggestion",
                Calibration.suggest(recs,"gmt_126710",ModelSpec.Shape.ROUND,d,legacy));
    }

    @Test public void productionReplayUsesExactlyProductionPositionScore(){
        ClearlyOff.Result measured=new ClearlyOff.Result();measured.rpx=500;
        ClearlyOff.Marker production=new ClearlyOff.Marker(6,ModelSpec.Shape.BATON);
        production.source="outer metal";production.sidePct=0.25;production.inoutPct=1.9;production.tiltDeg=0;
        production.relativePositionPct=0.7;production.relativePositionUsed=true;
        ClearlyOff.decide(production,measured.rpx,null,ClearlyOff.Limits.defaults());
        measured.markers.add(production);
        Calibration.Record saved=Calibration.of("51",51L,"submariner_124060","case",measured,null);
        assertEquals(Calibration.CURRENT_MEASUREMENT_VERSION,saved.measurementVersion);
        assertEquals(Calibration.POSITION_REFERENCE_V1,saved.at(6).positionMethod);
        assertTrue(saved.at(6).relativePositionUsed);
        Calibration.Record loaded=Calibration.recordFromJson(Calibration.toJson(saved));
        ClearlyOff.Marker replay=Calibration.judge(loaded,loaded.at(6),ClearlyOff.Limits.defaults());
        assertEquals(production.posPct,replay.posPct,0);
        assertEquals(production.state,replay.state);
        ClearlyOff.Limits tighter=ClearlyOff.Limits.defaults();tighter.batonPosR=0.006;
        assertEquals(ClearlyOff.State.CLEARLY_OFF,Calibration.state(loaded,loaded.at(6),tighter));
        ClearlyOff.Marker genuine=Calibration.judge(loaded,loaded.at(6),ClearlyOff.Limits.genuine());
        assertEquals("strict genuine retains legacy calibration",Math.hypot(.25,1.9),genuine.posPct,1e-12);
        // The strict-genuine copy deliberately does not carry a replica-QC position override.
        assertFalse(WatchClassification.strict(production,measured.rpx).relativePositionUsed);
    }

    @Test public void oldAndFallbackRecordsRemainLegacy(){
        Calibration.Record old=Calibration.recordFromJson(
                "{\"id\":\"62\",\"model\":\"gmt_126710\",\"label\":\"old\",\"time\":62,\"rpx\":500,"
                +"\"marks\":[{\"hour\":1,\"shape\":\"round\",\"gate\":\"\",\"reason\":\"\","
                +"\"side\":0.5,\"inout\":1.2,\"tilt\":null,\"gap\":null}]}");
        assertEquals(Calibration.LEGACY_POSITION_V1,old.measurementVersion);
        assertFalse(old.at(1).relativePositionUsed);
        assertEquals(Math.hypot(.5,1.2),Calibration.judge(old,old.at(1),ClearlyOff.Limits.defaults()).posPct,1e-12);
        assertFalse(Calibration.recordFromJson(Calibration.toJson(old)).at(1).relativePositionUsed);
        ClearlyOff.Result measured=new ClearlyOff.Result();measured.rpx=500;
        ClearlyOff.Marker fallback=new ClearlyOff.Marker(1,ModelSpec.Shape.ROUND);
        fallback.sidePct=.4;fallback.inoutPct=.8;
        measured.markers.add(fallback);
        Calibration.Record rec=Calibration.of("63",63L,"gmt_126710","fallback",measured,null);
        assertFalse(rec.at(1).relativePositionUsed);
        assertEquals(Calibration.LEGACY_POSITION_V1,rec.at(1).positionMethod);
        assertEquals(Math.hypot(.4,.8),Calibration.judge(rec,rec.at(1),ClearlyOff.Limits.defaults()).posPct,1e-12);
    }

    @Test public void savedDiagnosticDoesNotForceProductionUsage(){
        ClearlyOff.Result measured=new ClearlyOff.Result();measured.rpx=500;
        ClearlyOff.Marker m=new ClearlyOff.Marker(1,ModelSpec.Shape.ROUND);
        m.sidePct=.2;m.inoutPct=.7;m.relativePositionPct=.35;m.relativePositionUsed=false;
        measured.markers.add(m);
        Calibration.Record rec=Calibration.of("66",66,"gmt_126710","diagnostic",measured,null);
        Calibration.Mark k=Calibration.recordFromJson(Calibration.toJson(rec)).at(1);
        assertEquals(Calibration.POSITION_REFERENCE_V1,k.positionMethod);
        assertFalse(k.relativePositionUsed);
        assertEquals(Math.hypot(.2,.7),Calibration.judge(rec,k,ClearlyOff.Limits.defaults()).posPct,1e-12);
    }

    @Test public void filesRoundTrip(){
        Calibration.Record r=rec("1700000000000",mark(12,ModelSpec.Shape.TRIANGLE,0.2,0.6,Double.NaN,1.08,Calibration.Verdict.FAIL),
                mark(4,ModelSpec.Shape.ROUND,Double.NaN,Double.NaN,Double.NaN,Double.NaN,null));
        r.at(4).gate="hand (app check)";r.at(4).reason="a hand reaches it";r.label="18 Oct 16:05 · \"GMT\"";
        Calibration.Record b=Calibration.recordFromJson(Calibration.toJson(r));
        assertEquals(r.id,b.id);assertEquals(r.label,b.label);assertEquals(r.time,b.time);
        assertEquals(Calibration.Verdict.FAIL,b.at(12).verdict);assertEquals(1.08,b.at(12).gap,1e-12);assertTrue(Double.isNaN(b.at(12).tilt));
        assertEquals("hand (app check)",b.at(4).gate);assertNull(b.at(4).verdict);

        Map<String,ClearlyOff.Limits> m=new LinkedHashMap<>();ClearlyOff.Limits L=ClearlyOff.Limits.defaults();L.roundPosR=0.009;L.triTiltDeg=2.5;m.put("gmt_126710",L);
        ClearlyOff.Limits back=Calibration.limitsFromJson(Calibration.limitsToJson(m)).get("gmt_126710");
        assertTrue(back.same(L));
        m.put("gmt_126710",ClearlyOff.Limits.defaults());
        assertTrue("tilt off survives",Calibration.limitsFromJson(Calibration.limitsToJson(m)).get("gmt_126710").isDefault());

        String ex=Calibration.export(m,java.util.Arrays.asList(r),"1.6.2");
        assertTrue(ex.contains("model,round_position"));assertTrue(ex.contains(",12,triangle,"));assertTrue(ex.contains(",fail,Flagged for review"));
    }
}
