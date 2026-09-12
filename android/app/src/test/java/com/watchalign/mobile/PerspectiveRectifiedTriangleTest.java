package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Test;

public class PerspectiveRectifiedTriangleTest {
    @Test public void forwardAndInverseHomographyRoundTrip(){
        PerspectiveMasterRenderer.Pose p=pose();
        double[] image=PerspectiveRectifier.projectRaw(p,-.125,-.72);
        assertNotNull(image);
        double[] dial=PerspectiveRectifier.toDialRaw(p,image[0],image[1]);
        assertNotNull(dial);
        assertEquals(-.125,dial[0],1e-7);
        assertEquals(-.72,dial[1],1e-7);
    }

    @Test public void perspectiveWarpDoesNotChangeLocalTriangleMetric(){
        PerspectiveMasterRenderer.Pose p=pose();
        float half=.125f,baseY=-.72f,apexY=-.366f;
        float minuteY=baseY-.125f*(half*2f),crownY=apexY+.26f*(half*2f);
        double[] li=PerspectiveRectifier.projectRaw(p,-half,baseY),ri=PerspectiveRectifier.projectRaw(p,half,baseY),ai=PerspectiveRectifier.projectRaw(p,0,apexY),mi=PerspectiveRectifier.projectRaw(p,0,minuteY),ci=PerspectiveRectifier.projectRaw(p,0,crownY);
        assertNotNull(li);assertNotNull(ri);assertNotNull(ai);assertNotNull(mi);assertNotNull(ci);
        double[] l=PerspectiveRectifier.toDialRaw(p,li[0],li[1]),r=PerspectiveRectifier.toDialRaw(p,ri[0],ri[1]),a=PerspectiveRectifier.toDialRaw(p,ai[0],ai[1]),m=PerspectiveRectifier.toDialRaw(p,mi[0],mi[1]),c=PerspectiveRectifier.toDialRaw(p,ci[0],ci[1]);
        assertNotNull(l);assertNotNull(r);assertNotNull(a);assertNotNull(m);assertNotNull(c);
        Triangle12RelationalMetric.Result q=Triangle12RelationalMetric.measureRaw((float)l[0],(float)l[1],(float)r[0],(float)r[1],(float)a[0],(float)a[1],(float)m[0],(float)m[1],(float)c[0],(float)c[1],0,0,0,-1);
        assertEquals(GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE,q.baseGapRatio,.0005f);
        assertEquals(GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE,q.apexGapRatio,.0005f);
        assertEquals(0f,q.rotationDeg,.01f);
    }

    private PerspectiveMasterRenderer.Pose pose(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();p.anchorMode=true;p.perspectiveMode=true;
        p.anchor12X=510;p.anchor12Y=160;p.anchor3X=860;p.anchor3Y=520;p.anchor6X=500;p.anchor6Y=900;p.anchor9X=175;p.anchor9Y=545;return p;
    }
}
