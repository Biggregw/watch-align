package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import android.graphics.PointF;
import org.junit.Test;

public class Triangle12RelationalMetricTest {
    @Test public void genuineRelativeGeometryMeasuresAsReference(){
        PointF c=new PointF(0,0),p12=new PointF(0,-100);
        float w=48f;
        PointF l=new PointF(-24,-75),r=new PointF(24,-75),ap=new PointF(0,-143);
        PointF minute=new PointF(0,-81),crown=new PointF(0,-139);
        Triangle12RelationalMetric.Result m=Triangle12RelationalMetric.measure(l,r,ap,minute,crown,c,p12);
        assertEquals(GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,m.baseGapRatio,0.0001f);
        assertEquals(GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,m.apexGapRatio,0.0001f);
        assertEquals(GenTriangle12RelationalReference.HEIGHT_OVER_BASE,m.heightRatio,0.0001f);
        assertEquals(0f,m.rotationDeg,0.0001f);
    }
}
