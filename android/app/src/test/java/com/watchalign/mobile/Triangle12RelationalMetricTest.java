package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class Triangle12RelationalMetricTest {
    @Test public void genuineRelativeGeometryMeasuresAsReference(){
        // Screen-style coordinates: 12 o'clock is upward (smaller y).
        Triangle12RelationalMetric.Result m=Triangle12RelationalMetric.measureRaw(
                -24f,75f, 24f,75f, 0f,143f,
                0f,69f, 0f,147f, 0f,200f, 0f,100f);
        assertEquals(GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,m.baseGapRatio,0.0001f);
        assertEquals(GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,m.apexGapRatio,0.0001f);
        assertEquals(GenTriangle12RelationalReference.HEIGHT_OVER_BASE,m.heightRatio,0.0001f);
        assertEquals(0f,m.rotationDeg,0.0001f);
        assertEquals(0f,m.lateralPx,0.0001f);
    }

    @Test public void movingWholeTriangleOutwardReducesMinuteGapAndIncreasesCrownGap(){
        Triangle12RelationalMetric.Result gen=Triangle12RelationalMetric.measureRaw(
                -24f,75f,24f,75f,0f,143f,0f,69f,0f,147f,0f,200f,0f,100f);
        Triangle12RelationalMetric.Result outward=Triangle12RelationalMetric.measureRaw(
                -24f,72f,24f,72f,0f,140f,0f,69f,0f,147f,0f,200f,0f,100f);
        assertTrue(outward.baseGapRatio < gen.baseGapRatio);
        assertTrue(outward.apexGapRatio > gen.apexGapRatio);
        assertTrue(outward.heightRatio > 0f);
    }

    @Test public void heightRemainsPositiveForNormalTwelveTriangle(){
        Triangle12RelationalMetric.Result m=Triangle12RelationalMetric.measureRaw(
                -25f,80f,25f,80f,0f,150f,0f,74f,0f,154f,0f,210f,0f,100f);
        assertTrue(m.heightRatio > 0f);
    }
}
