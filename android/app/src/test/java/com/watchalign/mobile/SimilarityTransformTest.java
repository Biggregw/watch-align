package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

public class SimilarityTransformTest {
    @Test public void mapsSourceCentreExactlyToReferenceCentre() {
        SimilarityTransform t=SimilarityTransform.between(312.5,487.25,151.0,205.5,1.37,-8.2);
        assertEquals(151.0,t.mapX(312.5,487.25),1e-9);
        assertEquals(205.5,t.mapY(312.5,487.25),1e-9);
    }

    @Test public void scalesRadiusExactly() {
        SimilarityTransform t=SimilarityTransform.between(100,200,300,400,1.5,13.0);
        assertEquals(150.0,t.mappedRadius(100.0),1e-9);
    }

    @Test public void preservesDistanceTimesScaleAroundCentre() {
        SimilarityTransform t=SimilarityTransform.between(100,100,250,300,0.75,21.0);
        double x=t.mapX(140,70), y=t.mapY(140,70);
        double d=Math.hypot(x-250,y-300);
        assertEquals(Math.hypot(40,-30)*0.75,d,1e-9);
    }

    @Test public void zeroRotationHasExpectedTranslationAndScale() {
        SimilarityTransform t=SimilarityTransform.between(10,20,110,220,2.0,0.0);
        assertEquals(2.0,t.a,1e-12); assertEquals(0.0,t.b,1e-12);
        assertEquals(90.0,t.tx,1e-12); assertEquals(180.0,t.ty,1e-12);
        assertEquals(130.0,t.mapX(20,20),1e-12);
        assertEquals(220.0,t.mapY(20,20),1e-12);
    }

    @Test public void positiveOpenCvRotationMovesTwelveTowardEleven() {
        SimilarityTransform t=SimilarityTransform.between(100,100,100,100,1.0,30.0);
        // A point at 12 o'clock (100,50) rotates visually counter-clockwise to 11 o'clock.
        assertEquals(75.0,t.mapX(100,50),1e-9);
        assertEquals(100.0-50.0*Math.cos(Math.toRadians(30)),t.mapY(100,50),1e-9);
    }

    @Test public void negativeOpenCvRotationMovesTwelveTowardOne() {
        SimilarityTransform t=SimilarityTransform.between(100,100,100,100,1.0,-30.0);
        assertEquals(125.0,t.mapX(100,50),1e-9);
        assertEquals(100.0-50.0*Math.cos(Math.toRadians(30)),t.mapY(100,50),1e-9);
    }

    @Test public void scaleRotationAndTranslationTogetherRemainExact() {
        SimilarityTransform t=SimilarityTransform.between(321.25,477.75,188.5,222.25,1.43,7.6);
        assertEquals(188.5,t.mapX(321.25,477.75),1e-9);
        assertEquals(222.25,t.mapY(321.25,477.75),1e-9);
        double px=321.25+86.0, py=477.75-33.0;
        double mappedDistance=Math.hypot(t.mapX(px,py)-188.5,t.mapY(px,py)-222.25);
        assertEquals(Math.hypot(86.0,-33.0)*1.43,mappedDistance,1e-9);
    }

    @Test public void matrixMatchesPointMethods() {
        SimilarityTransform t=SimilarityTransform.between(93,144,287,355,1.22,-4.7);
        double[][] m=t.matrix2x3(); double x=155,y=244;
        assertEquals(t.mapX(x,y),m[0][0]*x+m[0][1]*y+m[0][2],1e-10);
        assertEquals(t.mapY(x,y),m[1][0]*x+m[1][1]*y+m[1][2],1e-10);
    }

    @Test public void rejectsInvalidScale() {
        assertThrows(IllegalArgumentException.class,()->SimilarityTransform.between(0,0,1,1,0,0));
        assertThrows(IllegalArgumentException.class,()->SimilarityTransform.between(0,0,1,1,Double.NaN,0));
        assertThrows(IllegalArgumentException.class,()->SimilarityTransform.between(0,0,1,1,-1,0));
    }

    @Test public void rejectsNonFiniteCoordinatesAndRotation() {
        assertThrows(IllegalArgumentException.class,()->SimilarityTransform.between(Double.NaN,0,1,1,1,0));
        assertThrows(IllegalArgumentException.class,()->SimilarityTransform.between(0,0,Double.POSITIVE_INFINITY,1,1,0));
        assertThrows(IllegalArgumentException.class,()->SimilarityTransform.between(0,0,1,1,1,Double.NaN));
    }
}
