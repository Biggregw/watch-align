package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class TwelveLocalGeometryTest {
    @Test public void perfectCentredTriangleIsZeroRotationAndCentring(){
        double[] l={-10,-20},r={10,-20},tip={0,0};
        double[] t59={-12,-30},t60={0,-30},t01={12,-30};
        TwelveLocalGeometry.Result q=TwelveLocalGeometry.measure(l,r,tip,t59,t60,t01,0,40);
        assertTrue(q.valid);
        assertEquals(0.5,q.gapOverWidth,1e-12);
        assertEquals(0.0,q.centringOverWidth,1e-12);
        assertEquals(0.0,q.rotationDeg,1e-12);
        assertEquals(0.0,q.baseEdgeDeg,1e-12);
        assertEquals(0.0,q.sideAsymmetry,1e-12);
        assertEquals(0.0,q.axisReferenceDisagreementDeg,1e-12);
    }

    @Test public void translationDoesNotChangeLocalGeometry(){
        double[] l={90,180},r={110,180},tip={100,200};
        double[] t59={88,170},t60={100,170},t01={112,170};
        TwelveLocalGeometry.Result q=TwelveLocalGeometry.measure(l,r,tip,t59,t60,t01,100,240);
        assertTrue(q.valid);
        assertEquals(0.5,q.gapOverWidth,1e-12);
        assertEquals(0.0,q.centringOverWidth,1e-12);
        assertEquals(0.0,q.rotationDeg,1e-12);
    }

    @Test public void centringUsesDetected60NotChordMidpoint(){
        double[] l={-10,-20},r={10,-20},tip={0,0};
        double[] t59={-12,-30},t60={2,-30},t01={12,-30};
        TwelveLocalGeometry.Result q=TwelveLocalGeometry.measure(l,r,tip,t59,t60,t01,0,40);
        assertTrue(q.valid);
        assertEquals(-0.1,q.centringOverWidth,1e-12);
    }
}
