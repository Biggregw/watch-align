package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 1.5.4 corrections from the 1.5.3 audit: a marker with an unmeasured alignment component is never an unqualified
 * "No defect"; missing numbers fail closed; a cleanly displaced round is read exactly at any resolution (no
 * linearisation error mistaken for edge noise); a shallow 20 % separator before a lit bevel is found; the close-up's
 * luminous geometry depends on the accepted luminous fit, never on an outer-metal finding.
 */
public class AuditFixesTest {
    static LuminousAlignment.Result baton(double radialR){
        LuminousAlignment.Result f=AlignmentDecisionTest.fit(6,ModelSpec.Shape.BATON,radialR,0,0,0.0005);
        f.radialOk=false;   // both ends hidden: the long sides do not fix the radial position
        return f;
    }

    @Test public void aBatonWithItsRadialPositionUnmeasuredIsNeverNoDefect(){
        for(double r:new double[]{0,0.01,0.02}){
            AlignmentDecision.Marker m=AlignmentDecisionTest.decide(baton(r),AlignmentDecisionTest.ref());
            assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,m.outcome);
            assertTrue(m.reason,m.reason.contains("distance from the centre could not be measured"));
        }
    }

    @Test public void aConfirmedDefectStandsWhenAnotherComponentIsMissing(){
        LuminousAlignment.Result f=AlignmentDecisionTest.fit(6,ModelSpec.Shape.BATON,0,0.02,0,0.0005);f.radialOk=false;
        AlignmentDecision.Marker m=AlignmentDecisionTest.decide(f,AlignmentDecisionTest.ref());
        assertEquals(AlignmentDecision.Outcome.DEFECT,m.outcome);
        assertTrue(AlignmentDecision.summary(m).contains("Not measured: distance from the centre"));
    }

    @Test public void theMissingComponentIsNotHiddenByAMetalNote(){
        LuminousAlignment.Result f=AlignmentDecisionTest.fit(6,ModelSpec.Shape.BATON,0,0.02,0,0.0005);f.radialOk=false;
        AlignmentDecision.Marker m=AlignmentDecisionTest.decide(f,AlignmentDecisionTest.ref());
        m.metalNotes.add("Worth a look (not confirmed): the outer metal reads rotated clockwise.");m.metalBorderline=true;
        assertTrue(AlignmentDecision.summary(m).contains("distance from the centre"));
    }

    @Test public void missingNumbersFailClosed(){
        AlignmentReference ref=AlignmentDecisionTest.ref();ref.hours.get(6).tangentialR=Double.NaN;
        AlignmentDecision.Marker m=AlignmentDecisionTest.decide(AlignmentDecisionTest.fit(6,ModelSpec.Shape.BATON,0,0,0,0.0005),ref);
        assertNotEquals(AlignmentDecision.Outcome.NO_DEFECT,m.outcome);
        LuminousAlignment.Result f=AlignmentDecisionTest.fit(6,ModelSpec.Shape.BATON,0,0,0,0.0005);f.seRotationDeg=Double.NaN;
        assertNotEquals(AlignmentDecision.Outcome.NO_DEFECT,AlignmentDecisionTest.decide(f,AlignmentDecisionTest.ref()).outcome);
    }

    // ------------------------------------------------------------------ round markers at any resolution

    /** A round marker on a dark dial at rpx pixels per dial radius: lume, a dark separator of the given depth (share of
     *  the lume-dial contrast below the lume), a lit metal bevel, then the dial. 1 px ramps. */
    static final class Round implements LuminousAlignment.Image {
        final double rpx,cx,cy,r;final double sepV,bevelV;static final double LUME=210,DIAL=20;
        Round(double rpx,double[] c,double radius,double sepShare,double bevelV){this.rpx=rpx;cx=c[0];cy=c[1];r=radius;sepV=LUME-sepShare*(LUME-DIAL);this.bevelV=bevelV;}
        public double at(double px,double py){
            double x=(px-1000)/rpx,y=(py-1000)/rpx;double d=(Math.hypot(x-cx,y-cy)-r)*rpx;
            double v=ramp(d,0,LUME,sepV);
            if(d>0.5)v=ramp(d,0.006*rpx,sepV,bevelV);
            if(d>0.006*rpx+0.5)v=ramp(d,0.016*rpx,bevelV,DIAL);
            return v;
        }
        static double ramp(double d,double at,double in,double out){double t=Math.max(0,Math.min(1,d-at+0.5));return in+(out-in)*t;}
    }
    static LuminousAlignment.Result measure(Round img,int hour){
        double[] H={img.rpx,0,1000,0,img.rpx,1000,0,0,1};
        return LuminousAlignment.measure(img,H,LuminousAlignmentTest.marker(hour),LuminousAlignmentTest.NO_RING,LuminousAlignmentTest.setup());
    }

    @Test public void aDisplacedRoundIsReadExactlyAtAnyResolution(){
        ModelSpec.Marker m=LuminousAlignmentTest.marker(4);double[] c=m.masterPoint();
        double[] er=LuminousAlignment.radial(4);
        for(double rpx:new double[]{300,600,900}){
            double[] moved={c[0]+0.02*er[0],c[1]+0.02*er[1]};   // 2.00 % R outward, whole outline visible
            LuminousAlignment.Result f=measure(new Round(rpx,moved,m.outerR-LuminousAlignmentTest.BEVEL,0.75,150),4);
            assertTrue(rpx+" px: "+f.reason,f.assessed);
            assertTrue(rpx+" px noise "+f.noisePx,f.noisePx<0.5);
            assertEquals(0.02,f.radialR,0.3/rpx);assertEquals(0,f.tangentialR,0.3/rpx);
            assertEquals(rpx+" px",AlignmentDecision.Outcome.DEFECT,AlignmentDecisionTest.decide(f,AlignmentDecisionTest.ref()).outcome);
        }
    }

    @Test public void aShallowSeparatorBeforeALitBevelIsTheLuminousEdge(){
        ModelSpec.Marker m=LuminousAlignmentTest.marker(4);double[] c=m.masterPoint();
        for(double share:new double[]{0.211,0.263}){
            LuminousAlignment.Result f=measure(new Round(300,c,m.outerR-LuminousAlignmentTest.BEVEL,share,210),4);
            assertTrue(share+": "+f.reason,f.assessed);
            assertEquals(0,f.radialR*300,0.5);assertEquals(0,f.tangentialR*300,0.5);
            assertTrue("growth "+f.growthR*300,Math.abs(f.growthR*300)<1.0);   // traced at the lume, not a bevel outward
        }
    }

    // ------------------------------------------------------------------ close-up evidence

    @Test public void anOuterMetalDefectNeverRestoresRejectedLuminousGeometry(){
        LuminousAlignment.Result f=new LuminousAlignment.Result(12,ModelSpec.Shape.TRIANGLE);
        f.reason="a bright bevel merges with its luminous fill on 40% of the outline";
        f.fittedPolygon=new double[][]{{0,-0.65},{0.1,-0.9},{-0.1,-0.9}};   // geometry that must not be shown
        AlignmentDecision.Marker m=AlignmentDecisionTest.decide(f,AlignmentDecisionTest.ref());
        assertFalse(m.lumeAccepted());
        AlignmentDecision.Issue metal=MetalAlignmentTest.metal(0.002);m.metal.add(metal);MetalAlignment.settle(m);
        assertEquals(AlignmentDecision.Outcome.DEFECT,m.outcome);
        assertFalse("promotion by the outer metal does not accept the luminous fit",m.lumeAccepted());
        assertTrue(AlignmentDecisionTest.decide(AlignmentDecisionTest.fit(12,ModelSpec.Shape.TRIANGLE,0,0,0,0.0005),AlignmentDecisionTest.ref()).lumeAccepted());
    }
}
