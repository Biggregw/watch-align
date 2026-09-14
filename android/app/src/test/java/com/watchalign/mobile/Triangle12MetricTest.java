package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class Triangle12MetricTest {
    @Test public void outwardMovementReportsHigh(){
        Triangle12Metric.Result r=Triangle12Metric.measure(100,90,100,100,0,-10,10,0,200);
        assertEquals(10f,r.radialPx,0.0001f);
        assertEquals(0f,r.lateralPx,0.0001f);
        assertEquals(5f,r.radialPct,0.0001f);
    }

    @Test public void inwardMovementReportsLow(){
        Triangle12Metric.Result r=Triangle12Metric.measure(100,104,100,100,0,-10,10,0,200);
        assertEquals(-4f,r.radialPx,0.0001f);
    }

    @Test public void lateralMovementIsSeparateFromHeight(){
        Triangle12Metric.Result r=Triangle12Metric.measure(103,100,100,100,0,-10,10,0,200);
        assertEquals(0f,r.radialPx,0.0001f);
        assertEquals(3f,r.lateralPx,0.0001f);
    }
}
