package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class Triangle12ShapeMetricTest {
    private static final float[] E={-7,-84, 7,-84, 0,-69};

    @Test public void exactTriangleIsZero(){
        Triangle12ShapeMetric.Result r=Triangle12ShapeMetric.measure(E.clone(),E,0,-1,1,0,100);
        assertEquals(0,r.radialPx,0.001);assertEquals(0,r.lateralPx,0.001);assertEquals(0,r.rotationDeg,0.001);
        assertEquals(0,r.widthPct,0.001);assertEquals(0,r.heightPct,0.001);assertEquals(0,r.apexCentrePx,0.001);
    }

    @Test public void outwardShiftReadsHigh(){
        float[] a={-7,-86, 7,-86, 0,-71};
        Triangle12ShapeMetric.Result r=Triangle12ShapeMetric.measure(a,E,0,-1,1,0,100);
        assertEquals(2,r.radialPx,0.001);assertEquals(2,r.radialPct,0.001);
    }

    @Test public void rotationAndWidthAreDetected(){
        float[] a={-8,-84, 8,-84, 2,-69};
        Triangle12ShapeMetric.Result r=Triangle12ShapeMetric.measure(a,E,0,-1,1,0,100);
        assertTrue(r.widthPct>10);assertTrue(Math.abs(r.rotationDeg)>1);assertTrue(r.apexCentrePx>1);
    }
}
