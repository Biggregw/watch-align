package com.watchalign.mobile;

import org.junit.After;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/** 1.6.6 whole-watch classification: two standards on the same measurements, four outcomes, nothing averaged. */
public class WatchClassificationTest {
    static final double RPX=400;                 // 0.5 px allowance = 0.125 % R
    static final double ALLOW=100*ClearlyOff.LOWRES_ALLOWANCE_PX/RPX;

    @After public void clearUserLimits(){ClearlyOff.USER.clear();}

    static ModelSpec noDate(){return ModelRegistryTest.spec("submariner_124060");}
    static ModelSpec date(){return ModelRegistryTest.spec("submariner_126610");}
    static ModelSpec gmt(){return ModelRegistryTest.spec("gmt_126710");}

    /** a measured marker as ClearlyOff leaves it: reliable (no uncertainty), judged at the given replica-QC limits */
    static ClearlyOff.Marker mark(ModelSpec.Marker mk,ClearlyOff.Limits L){
        ClearlyOff.Marker m=new ClearlyOff.Marker(mk.hour,mk.shape);m.source="outer metal";m.uncertain="";
        if(mk.shape==ModelSpec.Shape.TRIANGLE){m.sidePct=0.10;m.inoutPct=0.10;m.tiltDeg=0.30;m.gapPct=3.50;}
        else{m.sidePct=0.20;m.inoutPct=0.10;if(mk.shape==ModelSpec.Shape.BATON)m.tiltDeg=0.40;}
        return m;
    }
    /** a good watch of the model; edit the markers, then judge() */
    static ClearlyOff.Result watch(ModelSpec model){
        ClearlyOff.Result r=new ClearlyOff.Result();r.rpx=RPX;
        for(ModelSpec.Marker mk:model.markers)r.markers.add(mark(mk,null));
        return r;
    }
    static ClearlyOff.Result judge(ClearlyOff.Result r){return judge(r,ClearlyOff.Limits.defaults());}
    static ClearlyOff.Result judge(ClearlyOff.Result r,ClearlyOff.Limits L){for(ClearlyOff.Marker m:r.markers)ClearlyOff.decide(m,r.rpx,null,L);return r;}
    static WatchClassification.Verdict verdict(ClearlyOff.Result r,ModelSpec model){return WatchClassification.classify(r,model,null).verdict;}

    @Test public void everyMarkerWithinTheStrictStandardIsGenuine(){
        for(ModelSpec model:new ModelSpec[]{noDate(),date(),gmt()})
            assertEquals(model.id,WatchClassification.Verdict.GENUINE,verdict(judge(watch(model)),model));
    }

    @Test public void oneMarkerOutsideGenuineButWithinReplicaQcIsReplica(){
        ModelSpec model=noDate();ClearlyOff.Result r=watch(model);
        r.at(4).sidePct=0.85;r.at(4).inoutPct=0;           // 0.85 > 0.671+0.125, < 1.00+0.125
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,r.at(4).state);   // the marker's own (replica-QC) answer is unchanged
        assertTrue(c.at(4).genuineFail());assertTrue(c.at(4).qcPass());
        assertEquals("[position]",WatchClassification.failed(c.at(4).strict).toString());
        // a baton turned 1.3 deg: inside 1.75, outside 1.096 (the labelled crooked GMT 6 read 1.41)
        r=watch(model);r.at(6).tiltDeg=1.30;
        assertEquals(WatchClassification.Verdict.REPLICA,verdict(judge(r),model));
    }

    @Test public void oneReliableMarkerOutsideReplicaQcIsReplicaQcIssues(){
        ModelSpec model=noDate();ClearlyOff.Result r=watch(model);
        r.at(8).sidePct=1.40;r.at(8).inoutPct=0;
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,c.verdict);
        assertEquals(ClearlyOff.State.CLEARLY_OFF,r.at(8).state);
        assertFalse(c.partial);
        // owner, 20 Oct 2026: a confirmed failure takes precedence over an unrelated unreadable marker (partial coverage)
        r=watch(model);r.at(8).sidePct=1.40;r.at(8).inoutPct=0;r.at(2).gate.add("hand (app check)");
        c=WatchClassification.classify(judge(r),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,c.verdict);
        assertTrue(c.partial);assertTrue(c.at(8).qcFail());
        r=watch(model);r.at(8).sidePct=1.40;r.at(8).inoutPct=0;r.at(5).uncertain="its visible edge is rough (0.9 px)";
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,verdict(judge(r),model));
    }

    @Test public void anUncertainOrUnreadableMarkerNeverGivesGenuine(){
        ModelSpec model=noDate();
        ClearlyOff.Result r=watch(model);r.at(10).uncertain="its visible edge is rough (0.9 px)";   // within every limit, uncertain
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,r.at(10).state);
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);
        assertTrue(c.at(10).genuineUndecided());
        // a reliable strict failure still gives Replica with an uncertain marker elsewhere, flagged as partial coverage
        r=watch(model);r.at(10).uncertain="its visible edge is rough (0.9 px)";r.at(6).tiltDeg=1.30;
        c=WatchClassification.classify(judge(r),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);assertTrue(c.partial);
        // an uncertain edge alone never decides: far over every limit but uncertain, the rest clean -> Can't Determine
        r=watch(model);r.at(10).uncertain="its visible edge is rough (0.9 px)";r.at(10).sidePct=3.0;
        c=WatchClassification.classify(judge(r),model,null);
        assertEquals(ClearlyOff.State.POSSIBLE,r.at(10).state);
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);assertFalse(c.at(10).qcFail());assertFalse(c.at(10).genuineFail());
        r=watch(model);r.at(10).gate.add("a hand covers most of its outline");
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(judge(r),model));
        r=watch(model);r.at(10).sidePct=Double.NaN;r.at(10).inoutPct=Double.NaN;   // not measured
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(judge(r),model));
        r=watch(model);r.at(12).gapFromFit=true;                                     // gap placed from the rest of the outline
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(judge(r),model));
    }

    /** The Sub Date triangle outliers (genuine; edge fit 1.2-2.2 px RMS): an uncertain fit with an extreme rotation or
     *  position is Possible at most, never a confident Replica or Replica - QC Issues. */
    @Test public void aPoorlyObservedTriangleCannotMakeAReplica(){
        ModelSpec model=date();
        ClearlyOff.Result r=watch(model);ClearlyOff.Marker t=r.at(12);
        t.uncertain="its visible edge is rough (2.2 px); 81% of its visible edge is inconsistent (shadowed or reflecting metal)";
        t.tiltDeg=-1.5331;t.sidePct=0.5963;t.inoutPct=-0.2897;t.gapPct=3.7830;t.fitRmsPx=2.19;t.cover=0.65;  // c11761654b519222
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        assertEquals(ClearlyOff.State.POSSIBLE,t.state);
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);
        assertFalse(c.at(12).genuineFail());
        t.tiltDeg=5.0;t.sidePct=3.0;judge(r);                                           // even absurd numbers
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(r,model));
    }

    @Test public void aFailedAnalysisIsCantDetermineNeverReplica(){
        WatchClassification.Result c=WatchClassification.classify(null,noDate(),"OutOfMemoryError");
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);
        assertEquals("OutOfMemoryError",c.failure);
        assertEquals(12,c.markers.size());for(WatchClassification.MarkerCheck m:c.markers){assertNull(m.reading);assertFalse(m.reliable);}
        // every marker Can't check (a photo-wide reason)
        ClearlyOff.Result r=judge(watch(noDate()));for(ClearlyOff.Marker m:r.markers){m.state=ClearlyOff.State.CANT_CHECK;m.reason="the photo's resolution is too low";}
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(r,noDate()));
    }

    @Test public void theRequiredMarkersFollowTheDial(){
        WatchClassification.Result nd=WatchClassification.classify(judge(watch(noDate())),noDate(),null);
        assertEquals(12,nd.required);assertNotNull(nd.at(3));
        for(ModelSpec model:new ModelSpec[]{date(),gmt()}){
            WatchClassification.Result c=WatchClassification.classify(judge(watch(model)),model,null);
            assertEquals(model.id,11,c.required);assertNull("the date window is not a marker",c.at(3));
            assertEquals(WatchClassification.Verdict.GENUINE,c.verdict);
        }
        // a missing marker: never Genuine
        ClearlyOff.Result r=judge(watch(noDate()));r.markers.remove(r.at(7));
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(r,noDate()));
        // a Date watch measured with the No-Date dial lacks no marker, but a No-Date watch checked as Date skips 3 (the dial decides)
        assertEquals(WatchClassification.Verdict.GENUINE,verdict(judge(watch(noDate())),date()));
    }

    @Test public void triangleInOutAndGapUseTheStrictThresholds(){
        ModelSpec model=noDate();
        ClearlyOff.Result r=watch(model);r.at(12).inoutPct=0.60;   // > 0.373+0.125, < 1.25+0.125
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);
        assertEquals("[in/out]",WatchClassification.failed(c.at(12).strict).toString());
        assertEquals(0.373+ALLOW,c.at(12).strict.inoutLimitPct,1e-12);
        assertEquals(1.25+ALLOW,r.at(12).inoutLimitPct,1e-12);     // the replica-QC in/out limit is unchanged
        r=watch(model);r.at(12).gapPct=2.60;                       // < 2.81-0.125, > 2.20-0.125
        c=WatchClassification.classify(judge(r),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);
        assertEquals("[gap]",WatchClassification.failed(c.at(12).strict).toString());
        assertEquals(2.81-ALLOW,c.at(12).strict.gapLimitPct,1e-12);
        r=watch(model);r.at(12).gapPct=2.70;                       // the minimum with its allowance: a pass
        assertEquals(WatchClassification.Verdict.GENUINE,verdict(judge(r),model));
        r=watch(model);r.at(12).sidePct=0.60;r.at(12).tiltDeg=0.2; // sideways 0.437+0.125
        assertEquals("[sideways]",WatchClassification.failed(WatchClassification.classify(judge(r),model,null).at(12).strict).toString());
        r=watch(model);r.at(12).tiltDeg=1.18;                      // rotation: 1.174 strict, 1.2 replica
        assertEquals("[rotation]",WatchClassification.failed(WatchClassification.classify(judge(r),model,null).at(12).strict).toString());
    }

    @Test public void theGenuineStandardIsFixedAndIndependentOfTheOwnersLimits(){
        ClearlyOff.Limits g=ClearlyOff.Limits.genuine();
        assertEquals(0.00671,g.roundPosR,0);assertEquals(0.00752,g.batonPosR,0);assertEquals(1.096,g.batonTiltDeg,0);
        assertEquals(0.00437,g.triSideR,0);assertEquals(0.00373,g.triInoutR,0);assertEquals(1.174,g.triTiltDeg,0);assertEquals(0.02810,g.triGapR,0);
        // the owner's saved replica-QC limits: used for the QC answer, never for the genuine standard
        ClearlyOff.Limits mine=ClearlyOff.Limits.defaults();mine.roundPosR=0.02;ClearlyOff.USER.put("submariner_124060",mine);
        ModelSpec model=noDate();ClearlyOff.Result r=watch(model);r.at(4).sidePct=1.40;r.at(4).inoutPct=0;
        WatchClassification.Result c=WatchClassification.classify(judge(r,ClearlyOff.forModel(model.id)),model,null);
        assertEquals(ClearlyOff.State.NO_VISIBLE_ISSUE,r.at(4).state);     // within the owner's 2 %
        assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);          // still outside the fixed genuine 0.671 %
        g.roundPosR=0.5;assertEquals(0.00671,ClearlyOff.Limits.genuine().roundPosR,0);   // a changed copy changes nothing
        assertEquals(0.02,ClearlyOff.forModel(model.id).roundPosR,0);                     // and the owner's stay theirs
        // saved limits never carry or set the in/out limit (it is not one of the owner's)
        String json=Calibration.limitsToJson(new java.util.LinkedHashMap<>(ClearlyOff.USER));
        Map<String,ClearlyOff.Limits> back=Calibration.limitsFromJson(json);
        assertEquals(ClearlyOff.INOUT_TRIANGLE_LIMIT_R,back.get("submariner_124060").triInoutR,0);
        assertTrue(back.get("submariner_124060").same(mine));
    }

    @Test public void theAllowanceIsAppliedOnceInTheRightDirection(){
        ModelSpec model=noDate();
        ClearlyOff.Result r=watch(model);r.at(1).sidePct=0.671+ALLOW-1e-6;r.at(1).inoutPct=0;
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        assertEquals(0.671+ALLOW,c.at(1).strict.posLimitPct,1e-12);
        assertTrue(c.at(1).genuinePass());
        r.at(1).sidePct=0.671+ALLOW+1e-6;
        assertTrue(WatchClassification.classify(judge(r),model,null).at(1).genuineFail());
        // the allowance shrinks as the photo grows (0.5 px of a larger dial)
        r=watch(model);r.rpx=800;r.at(1).sidePct=0.75;r.at(1).inoutPct=0;
        assertTrue(WatchClassification.classify(judge(r),model,null).at(1).genuineFail());   // 0.75 > 0.671+0.0625
    }

    @Test public void classifyingChangesNoMeasurementOrAnswer(){
        ModelSpec model=noDate();ClearlyOff.Result r=watch(model);r.at(4).sidePct=0.85;r.at(9).uncertain="its visible edge is rough (1.0 px)";
        judge(r);
        StringBuilder before=new StringBuilder();for(ClearlyOff.Marker m:r.markers)before.append(m.state).append(m.sidePct).append(m.posLimitPct).append(m.inoutLimitPct).append(m.reason).append(';');
        WatchClassification.classify(r,model,null);WatchClassification.classify(r,model,null);
        StringBuilder after=new StringBuilder();for(ClearlyOff.Marker m:r.markers)after.append(m.state).append(m.sidePct).append(m.posLimitPct).append(m.inoutLimitPct).append(m.reason).append(';');
        assertEquals(before.toString(),after.toString());
        // the replica-QC defaults are those of 1.6.5
        ClearlyOff.Limits d=ClearlyOff.Limits.defaults();
        assertEquals(0.010,d.roundPosR,0);assertEquals(0.010,d.batonPosR,0);assertEquals(1.75,d.batonTiltDeg,0);
        assertEquals(0.010,d.triSideR,0);assertEquals(0.0125,d.triInoutR,0);assertEquals(0.022,d.triGapR,0);assertEquals(1.2,d.triTiltDeg,0);
    }

    @Test public void eachMarkerShowsBothStandards(){
        ModelSpec model=noDate();ClearlyOff.Result r=watch(model);r.at(4).sidePct=0.85;r.at(4).inoutPct=0;
        WatchClassification.Result c=WatchClassification.classify(judge(r),model,null);
        String line=WatchClassification.line(c.at(4));
        assertTrue(line,line.contains("Outside the genuine standard (position)")&&line.contains("within replica QC"));
        String m=String.join("\n",WatchClassification.measures(c.at(4)));
        assertTrue(m,m.startsWith("Position 0.85% R · genuine max 0.80% R ✗ · replica QC max 1.13% R ✓"));
        assertTrue(String.join("\n",WatchClassification.measures(c.at(12))).contains("Gap under the minute track 3.50% R · genuine min 2.69% R ✓"));
    }

    /** a photo-wide pose or analysis failure is always Can't Determine, whatever the markers read */
    @Test public void aPhotoWideFailureIsAlwaysCantDetermine(){
        ModelSpec model=noDate();
        ClearlyOff.Result r=watch(model);r.at(8).sidePct=1.40;r.at(8).inoutPct=0;judge(r);
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,WatchClassification.classify(r,model,null).verdict);
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,WatchClassification.classify(r,model,null,"the dial could not be located in this photo").verdict);
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,WatchClassification.classify(r,model,"out of memory").verdict);
        r.photoReason="the photo is taken at too steep an angle";
        WatchClassification.Result c=WatchClassification.classify(r,model,null);
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);assertFalse(c.partial);
    }

    // ---- 1.6.6: the refit of an uncertain baton beside a confirmed hand (owner's genuine Submariner Date, 6 baton) ----

    static final double BATON_LIMIT=100*ClearlyOff.Limits.genuine().batonPosR+ALLOW,TILT_LIMIT=ClearlyOff.Limits.genuine().batonTiltDeg;
    static double uPos(){return 100*MetalFit.ALT_U_POS_R;}
    static double uRot(){return MetalFit.ALT_U_ROT_DEG;}
    /** until the one-side uncertainty is measured on the genuine photos the refit is never used (and these tests skip) */
    static void validated(){org.junit.Assume.assumeTrue(Double.isFinite(uPos())&&Double.isFinite(uRot())&&Double.isFinite(MetalFit.ALT_LUME_AGREE_R));}

    /** the owner's 6 baton: its own reading pulled by the seconds hand (0.60 % R, rough 1.4 px edge), within replica QC;
     *  the refit without the hand's side at the given numbers, agreeing with the lume */
    static ClearlyOff.Result ownersSix(ModelSpec model,double side,double inout,double tilt){
        ClearlyOff.Result r=watch(model);ClearlyOff.Marker m=r.at(6);
        m.sidePct=-0.60;m.inoutPct=-0.03;m.tiltDeg=-0.06;
        m.uncertain="its visible edge is rough (1.4 px); 66% of its visible edge is inconsistent (shadowed or reflecting metal)";
        m.altSide=0;m.altSidePct=side;m.altInoutPct=inout;m.altTiltDeg=tilt;m.altLumeDiffPct=0.05;
        return judge(r);
    }

    @Test public void theValidatedRefitLetsTheOwnersSixBatonCount(){
        validated();
        ModelSpec model=date();ClearlyOff.Result r=ownersSix(model,-0.04,-0.03,-0.24);
        assertEquals("its own replica-QC answer is unchanged (the green tick)",ClearlyOff.State.NO_VISIBLE_ISSUE,r.at(6).state);
        WatchClassification.Result c=WatchClassification.classify(r,model,null);
        assertEquals(WatchClassification.Verdict.GENUINE,c.verdict);
        assertEquals(WatchClassification.Alt.PASS,c.at(6).alt);assertFalse(c.at(6).reliable);
        assertTrue(WatchClassification.line(c.at(6)).contains("without the edge beside the hand"));
        assertEquals("the reading itself is not changed",-0.60,r.at(6).sidePct,0);
        // without the refit the same marker leaves the watch undetermined (1.6.6 test build)
        r=ownersSix(model,-0.04,-0.03,-0.24);r.at(6).altSide=-1;
        assertEquals(WatchClassification.Verdict.CANT_DETERMINE,verdict(r,model));
    }

    @Test public void aRefitWhoseUncertaintyCouldCrossALimitIsUndecided(){
        validated();ModelSpec model=date();
        WatchClassification.Result c=WatchClassification.classify(ownersSix(model,BATON_LIMIT-uPos()/2,0,0.10),model,null);
        assertEquals(WatchClassification.Alt.UNDECIDED,c.at(6).alt);assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);
        c=WatchClassification.classify(ownersSix(model,0.05,0,TILT_LIMIT-uRot()/2),model,null);
        assertEquals("rotation too",WatchClassification.Alt.UNDECIDED,c.at(6).alt);
        c=WatchClassification.classify(ownersSix(model,BATON_LIMIT-uPos()-0.01,0,TILT_LIMIT-uRot()-0.01),model,null);
        assertEquals("inside both by more than the uncertainty",WatchClassification.Alt.PASS,c.at(6).alt);
    }

    @Test public void aRefitOutsideByMoreThanItsUncertaintyIsReplica(){
        validated();ModelSpec model=date();
        WatchClassification.Result c=WatchClassification.classify(ownersSix(model,BATON_LIMIT+uPos()+0.02,0,0.10),model,null);
        assertEquals(WatchClassification.Alt.FAIL,c.at(6).alt);assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);
        c=WatchClassification.classify(ownersSix(model,0.05,0,TILT_LIMIT+uRot()+0.05),model,null);
        assertEquals(WatchClassification.Verdict.REPLICA,c.verdict);
    }

    @Test public void theRefitIsRefusedWithoutTheLumeOrOverAQcLimitOrForAReliableReading(){
        ModelSpec model=date();
        ClearlyOff.Result r=ownersSix(model,-0.04,-0.03,-0.24);r.at(6).altLumeDiffPct=100*MetalFit.ALT_LUME_AGREE_R+0.01;
        assertEquals("the lume disagrees",WatchClassification.Verdict.CANT_DETERMINE,verdict(r,model));
        r=ownersSix(model,-0.04,-0.03,-0.24);r.at(6).altLumeDiffPct=Double.NaN;
        assertEquals("the lume was not measured",WatchClassification.Verdict.CANT_DETERMINE,verdict(r,model));
        // the uncertain reading over the replica-QC limit: Possible, inspect; the refit does not overrule it
        r=watch(model);ClearlyOff.Marker m=r.at(6);m.sidePct=-1.40;m.uncertain="its visible edge is rough (1.4 px)";
        m.altSide=0;m.altSidePct=-0.04;m.altInoutPct=-0.03;m.altTiltDeg=-0.24;m.altLumeDiffPct=0.05;judge(r);
        assertEquals(ClearlyOff.State.POSSIBLE,m.state);
        WatchClassification.Result c=WatchClassification.classify(r,model,null);
        assertEquals(WatchClassification.Alt.NONE,c.at(6).alt);assertEquals(WatchClassification.Verdict.CANT_DETERMINE,c.verdict);
        // a reliable reading is judged on itself; refit numbers are ignored
        r=watch(model);m=r.at(6);m.altSide=0;m.altSidePct=5;m.altInoutPct=5;m.altTiltDeg=5;m.altLumeDiffPct=0.05;judge(r);
        c=WatchClassification.classify(r,model,null);
        assertEquals(WatchClassification.Alt.NONE,c.at(6).alt);assertEquals(WatchClassification.Verdict.GENUINE,c.verdict);
        // only batons
        r=watch(model);m=r.at(4);m.uncertain="its visible edge is rough (0.9 px)";m.altSide=0;m.altSidePct=0;m.altInoutPct=0;m.altTiltDeg=0;m.altLumeDiffPct=0;judge(r);
        assertEquals(WatchClassification.Alt.NONE,WatchClassification.classify(r,model,null).at(4).alt);
    }

    @Test public void theRefitDoesNotTouchReplicaQc(){
        ModelSpec model=date();ClearlyOff.Result r=ownersSix(model,-0.04,-0.03,-0.24);
        r.at(8).sidePct=1.40;r.at(8).inoutPct=0;judge(r);
        assertEquals(WatchClassification.Verdict.REPLICA_QC_ISSUES,verdict(r,model));
        ClearlyOff.Marker k=r.at(6).copy();
        assertEquals(0,k.altSide);assertEquals(-0.24,k.altTiltDeg,0);assertEquals(0.05,k.altLumeDiffPct,0);
    }
}
