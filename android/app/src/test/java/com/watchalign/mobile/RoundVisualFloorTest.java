package com.watchalign.mobile;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.security.MessageDigest;

import static org.junit.Assert.*;

/**
 * Round markers: a position component is a defect only when it is beyond the genuine envelope + this photo's uncertainty
 * AND displaced by more than AlignmentDecision.ROUND_VISUAL_FLOOR_R (1.00 % of the dial radius). The larger threshold
 * applies; the two are never added. Triangles and batons are unaffected; the genuine references are unchanged.
 */
public class RoundVisualFloorTest {
    static final double FLOOR=AlignmentDecision.ROUND_VISUAL_FLOOR_R;

    static AlignmentDecision.Marker decide(LuminousAlignment.Result f,AlignmentReference ref){return AlignmentDecisionTest.decide(f,ref);}
    static LuminousAlignment.Result round(int hour,double radialR,double tangentialR){
        return AlignmentDecisionTest.fit(hour,ModelSpec.Shape.ROUND,radialR,tangentialR,Double.NaN,0.0002);
    }
    static AlignmentReference gmt(){return AlignmentReference.load(TestModels.files(),TestModels.gmt());}

    @Test public void theFloorIsOnePercentOfTheDialRadius(){assertEquals(0.010,FLOOR,0);}

    /** The owner's Batgirl (dial radius 269.6 px): 7 read 0.89 % R low (radial), 8 read 0.93 % R (tangential).
     *  Both are beyond every genuine GMT but under 1.00 % R, so neither is a defect now. */
    @Test public void batgirlSevenAndEightPass(){
        AlignmentReference ref=gmt();
        LuminousAlignment.Result seven=round(7,0.00703025,-0.00407718);seven.rpx=269.636;seven.seRadialR=0.000241128;seven.seTangentialR=0.000244781;
        LuminousAlignment.Result eight=round(8,0.00232739,-0.00838494);eight.rpx=269.636;eight.seRadialR=0.000197287;eight.seTangentialR=0.000194229;
        for(LuminousAlignment.Result f:new LuminousAlignment.Result[]{seven,eight}){
            AlignmentDecision.Marker m=decide(f,ref);
            assertEquals(f.hour+" o'clock",AlignmentDecision.Outcome.NO_DEFECT,m.outcome);
            boolean beyondGenuine=false;
            for(AlignmentDecision.Issue i:m.within){
                if(Math.abs(i.deviation)>i.limit+i.uncertainty){beyondGenuine=true;assertTrue(Math.abs(i.deviation)<FLOOR);assertTrue(Math.abs(i.deviation)>0.0088);}
            }
            assertTrue("the genuine rule alone would still flag "+f.hour,beyondGenuine);
        }
    }

    @Test public void beyondGenuineButBelowTheFloorIsNoDefectForRoundsOnly(){
        AlignmentReference ref=AlignmentDecisionTest.ref();   // envelopes 0.5 % R
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(round(4,0.009,0),ref).outcome);
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(round(4,0,0.0095),ref).outcome);
        // the same 0.9 % R on a baton or the triangle is still a defect: the floor is for round markers only
        assertEquals(AlignmentDecision.Outcome.DEFECT,decide(AlignmentDecisionTest.fit(6,ModelSpec.Shape.BATON,0.009,0,0,0.0002),ref).outcome);
        assertEquals(AlignmentDecision.Outcome.DEFECT,decide(AlignmentDecisionTest.fit(12,ModelSpec.Shape.TRIANGLE,0,0.009,0,0.0002),ref).outcome);
    }

    @Test public void clearlyDisplacedRoundsAreStillDefectsRadialAndTangential(){
        AlignmentReference ref=AlignmentDecisionTest.ref();
        AlignmentDecision.Marker r=decide(round(4,0.015,0),ref),t=decide(round(4,0,0.015),ref);
        assertEquals(AlignmentDecision.Outcome.DEFECT,r.outcome);assertEquals(LuminousAlignment.Param.RADIAL,r.primary.param);
        assertEquals(AlignmentDecision.Outcome.DEFECT,t.outcome);assertEquals(LuminousAlignment.Param.TANGENTIAL,t.primary.param);
        assertEquals(0.015-FLOOR,r.primary.excessR,1e-12);assertEquals(r.primary.excessR,t.primary.excessR,1e-12);
        assertTrue(r.primary.genuine(),r.primary.genuine().contains("more than 1.00% of the dial radius"));
    }

    @Test public void exactVisualFloorIsNotAnExceedanceAndPhotoUncertaintyCanRaiseTheThreshold(){
        AlignmentReference ref=AlignmentDecisionTest.ref();
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(round(4,FLOOR,0),ref).outcome);
        assertEquals(AlignmentDecision.Outcome.DEFECT,decide(round(4,FLOOR+1e-6,0),ref).outcome);

        LuminousAlignment.Result f=round(4,0.011,0);
        f.seRadialR=0.003; // genuine envelope plus 2 SE, not the visual floor, is now the larger bound
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(f,ref).outcome);
        f.radialR=0.013;
        assertEquals(AlignmentDecision.Outcome.DEFECT,decide(f,ref).outcome);
    }

    @Test public void negativeDirectionsBehaveIdentically(){
        AlignmentReference ref=AlignmentDecisionTest.ref();
        for(double d:new double[]{0.009,0.0105,0.02}){
            for(boolean radial:new boolean[]{true,false}){
                AlignmentDecision.Marker p=decide(radial?round(4,d,0):round(4,0,d),ref),n=decide(radial?round(4,-d,0):round(4,0,-d),ref);
                assertEquals(p.outcome,n.outcome);
                if(p.primary!=null){assertEquals(p.primary.excessR,n.primary.excessR,1e-12);
                    assertEquals(-p.primary.screen[0],n.primary.screen[0],1e-12);assertEquals(-p.primary.screen[1],n.primary.screen[1],1e-12);}
            }
        }
    }

    @Test public void theFloorIsAMinimumNotAnAddedAllowance(){
        AlignmentReference ref=AlignmentDecisionTest.ref();ref.shapes.get("round").radialLimitR=0.012;   // genuine wider than the floor
        assertEquals(AlignmentDecision.Outcome.DEFECT,decide(round(4,0.0145,0),ref).outcome);       // beyond 0.012 + uncertainty; not 0.012 + 0.010
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(round(4,0.0115,0),ref).outcome);    // the genuine envelope still applies
    }

    @Test public void aRoundWithInsufficientEvidenceStillCannotBeAssessed(){
        AlignmentReference ref=AlignmentDecisionTest.ref();
        LuminousAlignment.Result f=new LuminousAlignment.Result(4,ModelSpec.Shape.ROUND);f.reason="hand crosses or touches this marker";
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,decide(f,ref).outcome);
        LuminousAlignment.Result g=round(4,0.003,0.003);g.radialOk=false;g.tangentialOk=false;
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,decide(g,ref).outcome);
    }

    /** The bundled genuine luminous references are byte-for-byte those installed from calibration run 38565452660. */
    @Test public void genuineReferencesAreUnchanged()throws Exception{
        assertEquals("a2c74a438778cccbb6b5bfc9c54f35aeb60ddde006fe4a2d9854e5cdd01dcc72",sha("gmt_126710"));
        assertEquals("1990537dbf33e8e0345a41b24f9a6d3284ab6620050dcb4d11ea98cfc4521ca1",sha("submariner_124060"));
        AlignmentReference g=gmt();
        assertEquals(0.0047355,g.shape(ModelSpec.Shape.TRIANGLE).radialLimitR,1e-12);
        assertTrue(g.shape(ModelSpec.Shape.ROUND).radialLimitR<FLOOR);
    }
    static String sha(String model)throws Exception{
        byte[] b=Files.readAllBytes(new File(TestModels.assets(),"models/"+model+"/alignment/luminous_alignment.json").toPath());
        StringBuilder s=new StringBuilder();for(byte x:MessageDigest.getInstance("SHA-256").digest(b))s.append(String.format("%02x",x));return s.toString();
    }
}
