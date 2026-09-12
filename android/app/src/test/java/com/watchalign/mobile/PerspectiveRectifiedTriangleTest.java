package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import android.graphics.PointF;
import org.junit.Test;

public class PerspectiveRectifiedTriangleTest {
    @Test public void perspectiveWarpDoesNotChangeLocalTriangleMetric(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;
        p.anchor12X=510;p.anchor12Y=160;
        p.anchor3X=860;p.anchor3Y=520;
        p.anchor6X=500;p.anchor6Y=900;
        p.anchor9X=175;p.anchor9Y=545;

        float half=.125f;
        float baseY=-.72f;
        float apexY=-.366f;
        float minuteY=baseY-.125f*(half*2f);
        float crownY=apexY+.26f*(half*2f);

        PointF l=PerspectiveMasterRenderer.projectPoint(p,-half,baseY);
        PointF r=PerspectiveMasterRenderer.projectPoint(p, half,baseY);
        PointF a=PerspectiveMasterRenderer.projectPoint(p,0,apexY);
        PointF m=PerspectiveMasterRenderer.projectPoint(p,0,minuteY);
        PointF c=PerspectiveMasterRenderer.projectPoint(p,0,crownY);

        Triangle12RelationalMetric.Result q=Triangle12RelationalMetric.measureRectified(p,l,r,a,m,c);
        assertNotNull(q);
        assertEquals(GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,q.baseGapRatio,0.0005f);
        assertEquals(GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,q.apexGapRatio,0.0005f);
        assertEquals(0f,q.rotationDeg,0.01f);
    }
}
