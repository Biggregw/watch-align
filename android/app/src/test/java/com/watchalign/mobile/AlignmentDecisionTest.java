package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

/** The binary alignment answer: genuine envelope + this photo's uncertainty, dominant issue by visible displacement. */
public class AlignmentDecisionTest {
    static final double RPX=300;

    static LuminousAlignment.Result fit(int hour,ModelSpec.Shape shape,double radialR,double tangentialR,double rotDeg,double se){
        LuminousAlignment.Result f=new LuminousAlignment.Result(hour,shape);
        f.assessed=true;f.rpx=RPX;f.refCentre=new double[]{0,-0.8};
        f.refPolygon=shape==ModelSpec.Shape.ROUND?null:new double[][]{{0,-0.65},{0.1,-0.9},{-0.1,-0.9}};
        f.refRadius=0.075;
        f.radialOk=true;f.tangentialOk=true;f.rotationOk=shape!=ModelSpec.Shape.ROUND;
        f.radialR=radialR;f.tangentialR=tangentialR;f.rotationDeg=rotDeg;f.seRadialR=se;f.seTangentialR=se;f.seRotationDeg=0.05;
        return f;
    }

    static AlignmentDecision.Marker decide(LuminousAlignment.Result f,AlignmentReference ref){
        AlignmentDecision.Marker m=new AlignmentDecision.Marker(f.hour,f.shape);m.fit=f;AlignmentDecision.decide(m,ref);return m;
    }

    static AlignmentReference ref(){return LuminousAlignmentTest.syntheticReference();}

    @Test public void withinTheGenuineEnvelopeIsNoDefect(){
        AlignmentDecision.Marker m=decide(fit(12,ModelSpec.Shape.TRIANGLE,0.004,-0.004,0.3,0.0005),ref());
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,m.outcome);assertNull(m.primary);
        assertNull("a normal marker shows no measurements",AlignmentDecision.summary(m));
    }

    @Test public void justBeyondTheEnvelopeButWithinThePhotoUncertaintyIsNoDefect(){
        // 0.55 % R against a 0.5 % envelope, with a 2 SE uncertainty of 0.2 %: not resolvable as a defect
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(fit(12,ModelSpec.Shape.TRIANGLE,0,0.0055,0,0.001),ref()).outcome);
    }

    @Test public void clearlyBeyondIsADefectWithItsDirectionAndGenuineRange(){
        AlignmentDecision.Marker m=decide(fit(12,ModelSpec.Shape.TRIANGLE,0,0.016,0,0.0005),ref());
        assertEquals(AlignmentDecision.Outcome.DEFECT,m.outcome);
        assertEquals("Positioned too far right",m.primary.headline());
        assertTrue(m.primary.measured(),m.primary.measured().contains("1.60% of the dial radius"));
        assertTrue(m.primary.genuine(),m.primary.genuine().contains("0.50%"));
    }

    @Test public void aLargeShiftLeadsAndASmallExtraRotationIsSecondary(){
        AlignmentDecision.Marker m=decide(fit(12,ModelSpec.Shape.TRIANGLE,0.02,0,1.5,0.0005),ref());
        assertEquals(LuminousAlignment.Param.RADIAL,m.primary.param);
        assertEquals("Positioned too high",m.primary.headline());   // 12: radial outward = up, towards the minute track
        assertEquals(1,m.secondary.size());assertEquals(LuminousAlignment.Param.ROTATION,m.secondary.get(0).param);
        AlignmentDecision.Marker r=decide(fit(12,ModelSpec.Shape.TRIANGLE,0,0,-1.6,0.0005),ref());
        assertEquals("Rotated anticlockwise",r.primary.headline());
    }

    @Test public void deviationIsMeasuredFromTheGenuineNominalNotFromTheMaster(){
        AlignmentReference ref=ref();ref.hours.get(12).tangentialR=0.012;   // genuine 12s sit 1.2 % R right of the master
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,decide(fit(12,ModelSpec.Shape.TRIANGLE,0,0.014,0,0.0005),ref).outcome);
    }

    @Test public void missingGenuineEvidenceCannotBeAssessedNeverNoDefect(){
        AlignmentReference ref=ref();ref.shapes.get("round").supported=false;
        AlignmentDecision.Marker m=decide(fit(4,ModelSpec.Shape.ROUND,0,0,Double.NaN,0.0005),ref);
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,m.outcome);
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,decide(fit(4,ModelSpec.Shape.ROUND,0,0,Double.NaN,0.0005),null).outcome);
    }

    @Test public void roundsAreJudgedOnPositionOnly(){
        AlignmentDecision.Marker m=decide(fit(4,ModelSpec.Shape.ROUND,0,0,Double.NaN,0.0005),ref());
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,m.outcome);
        for(AlignmentDecision.Issue i:m.within)assertNotEquals(LuminousAlignment.Param.ROTATION,i.param);
        assertFalse(m.notAssessed.contains("rotation"));
    }

    @Test public void anUndeterminedComponentIsNamedNotPassed(){
        LuminousAlignment.Result f=fit(6,ModelSpec.Shape.BATON,0,0,0,0.0005);f.radialOk=false;f.radialR=Double.NaN;
        AlignmentDecision.Marker m=decide(f,ref());
        assertEquals(AlignmentDecision.Outcome.NO_DEFECT,m.outcome);
        assertTrue(AlignmentDecision.summary(m).contains("distance from the centre"));
    }

    @Test public void anUnmeasuredMarkerCannotBeAssessed(){
        LuminousAlignment.Result f=new LuminousAlignment.Result(9,ModelSpec.Shape.BATON);f.reason="only one luminous edge is visible";
        AlignmentDecision.Marker m=decide(f,ref());
        assertEquals(AlignmentDecision.Outcome.CANNOT_ASSESS,m.outcome);assertEquals("Cannot assess",m.label());
    }
}
