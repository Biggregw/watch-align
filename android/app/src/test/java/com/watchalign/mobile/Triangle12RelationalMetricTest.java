package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class Triangle12RelationalMetricTest {
    @Test public void genuineRelativeGeometryMeasuresAsReference(){
        Triangle12RelationalMetric.Result m=Triangle12RelationalMetric.measureRaw(
                -24f,-75f, 24f,-75f, 0f,-143f,
                0f,-81f, 0f,-139f, 0f,0f, 0f,-100f);
        assertEquals(GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,m.baseGapRatio,0.0001f);
        assertEquals(GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,m.apexGapRatio,0.0001f);
        assertEquals(GenTriangle12RelationalReference.HEIGHT_OVER_BASE,m.heightRatio,0.0001f);
        assertEquals(0f,m.rotationDeg,0.0001f);
        assertEquals(0f,m.lateralPx,0.0001f);
    }
}
