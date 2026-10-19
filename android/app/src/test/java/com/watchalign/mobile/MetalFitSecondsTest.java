package com.watchalign.mobile;

import org.junit.Test;
import java.util.Collections;
import static com.watchalign.mobile.MetalFitTest.*;
import static org.junit.Assert.*;

/** Independently known outer-metal geometry with thin shafts extending towards the dial centre. */
public class MetalFitSecondsTest {
    static final double[][] BATON=rect(0,0.75,0.025,0.08);
    static final double ADJACENT=Math.toDegrees(Math.atan2(0.039,-0.75));

    static Pic shaft(Pic p,double degrees){
        double a=Math.toRadians(degrees),ux=Math.sin(a),uy=-Math.cos(a),vx=Math.cos(a),vy=Math.sin(a),half=0.003;
        return p.add(new double[][]{{.05*ux-half*vx,.05*uy-half*vy},{.98*ux-half*vx,.98*uy-half*vy},
                {.98*ux+half*vx,.98*uy+half*vy},{.05*ux+half*vx,.05*uy+half*vy}},200);
    }
    static MetalFit.Fit fit(Pic p,double[][] ref,ModelSpec.Shape shape,double degrees){
        ClearlyOff.Input input=new ClearlyOff.Input(6,shape,ref,false,Alpha99MarkerInterference.HAND,degrees);
        return MetalFit.fitAll(p.img(),Collections.singletonList(input),RPX,Collections.emptyList())[0];
    }
    static void aligned(MetalFit.Fit f,double tx,double ty,double degrees){
        assertTrue(f.note,f.ok());
        assertEquals(tx,f.cObs[0],0.001);
        assertEquals(.75+ty,f.cObs[1],0.001);
        assertEquals(degrees,f.tiltDeg,0.2);
    }

    @Test public void adjacentSecondsShaftCannotBecomeABatonEdge(){
        Pic photo=shaft(dial(BATON),ADJACENT);
        MetalFit.Fit baseline=fit(photo,BATON,ModelSpec.Shape.BATON,Double.NaN);
        MetalFit.Fit corrected=fit(photo,BATON,ModelSpec.Shape.BATON,ADJACENT);
        assertTrue(baseline.note,baseline.ok());
        assertTrue("fixture must expose the original contaminated fit",Math.abs(baseline.tiltDeg)>1.0);
        aligned(corrected,0,0,0);
    }

    @Test public void knownRotatedBatonRemainsFlaggedBesideSecondsHand(){
        MetalFit.Fit f=fit(shaft(dial(moved(BATON,0,0,Math.toRadians(3))),ADJACENT),BATON,ModelSpec.Shape.BATON,ADJACENT);
        aligned(f,0,0,3);
        ClearlyOff.Marker m=new ClearlyOff.Marker(6,ModelSpec.Shape.BATON);
        m.sidePct=m.inoutPct=0;m.tiltDeg=f.tiltDeg;m.uncertain=f.uncertain;
        ClearlyOff.decide(m,RPX,null);
        assertTrue(m.state.toString(),m.state==ClearlyOff.State.CLEARLY_OFF||m.state==ClearlyOff.State.POSSIBLE);
        assertEquals(1.75,m.tiltLimitDeg,0);
    }

    @Test public void knownPositionIsRecoveredFromSurvivingEdges(){
        MetalFit.Fit f=fit(shaft(dial(moved(BATON,.006,-.003,0)),ADJACENT),BATON,ModelSpec.Shape.BATON,ADJACENT);
        aligned(f,.006,-.003,0);
    }

    @Test public void overlappingSecondsHandLeavesEnoughBatonEndsToMeasure(){
        MetalFit.Fit f=fit(shaft(dial(BATON),180),BATON,ModelSpec.Shape.BATON,180);
        aligned(f,0,0,0);
        assertTrue("some covered observations must be excluded",f.cover<.99);
        assertTrue("clean long sides and end fragments stay usable",f.cover>.7);
    }

    @Test public void centreLineDoesNotEraseAllShortEdgesAsBroadReferenceMaskDid(){
        // The exact PR #62 regression: confirmed direction on an otherwise clean marker.
        MetalFit.Fit f=fit(dial(BATON),BATON,ModelSpec.Shape.BATON,180);
        aligned(f,0,0,0);
        assertTrue(f.cover>.7&&f.cover<.99);
    }

    @Test public void nearbyLineOutsideTheObservedEdgePreservesEverySample(){
        MetalFit.Fit normal=fit(dial(BATON),BATON,ModelSpec.Shape.BATON,Double.NaN);
        MetalFit.Fit nearby=fit(dial(BATON),BATON,ModelSpec.Shape.BATON,170);
        assertEquals(normal.cover,nearby.cover,0);
        assertEquals(normal.tiltDeg,nearby.tiltDeg,0);
        assertArrayEquals(normal.cObs,nearby.cObs,0);
    }

    @Test public void aSingleSurvivingStraightEdgeCannotFixPosition(){
        Pic fragment=new Pic().add(rect(-.023,.75,.002,.07),180);
        MetalFit.Fit f=fit(fragment,BATON,ModelSpec.Shape.BATON,180);
        assertFalse("one line cannot establish both position coordinates",f.ok());
        assertFalse(f.note.isEmpty());
    }

    @Test public void rotatedTriangleStillMeasuresThroughCentralSecondsHand(){
        double[][] triangle={{-.10,-.88},{.10,-.88},{0,-.62}};
        MetalFit.Fit f=fit(shaft(dial(moved(triangle,0,0,Math.toRadians(2))),0),triangle,ModelSpec.Shape.TRIANGLE,0);
        assertTrue(f.note,f.ok());assertEquals(2,f.tiltDeg,.2);
    }

    @Test public void roundPositionSurvivesPartialSecondsOcclusion(){
        double[][] round=circle(0,.75,.05);
        MetalFit.Fit f=fit(shaft(dial(moved(round,.005,0,0)),180),round,ModelSpec.Shape.ROUND,180);
        assertTrue(f.note,f.ok());assertEquals(.005,f.cObs[0],.001);assertEquals(.75,f.cObs[1],.001);
        assertTrue(Double.isNaN(f.tiltDeg));
    }

    @Test public void radialMarkerEdgesAndOppositeRayAreNotHandEvidence(){
        assertFalse(MetalFit.onSecondsHand(0,.75,Double.NaN,RPX));
        assertFalse(MetalFit.onSecondsHand(0,-.75,180,RPX));
        assertTrue(MetalFit.onSecondsHand(0,.75,180,RPX));
    }
}
