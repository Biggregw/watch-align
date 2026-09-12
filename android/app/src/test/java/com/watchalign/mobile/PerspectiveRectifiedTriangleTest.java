package com.watchalign.mobile;

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

        float half=.125f,baseY=-.72f,apexY=-.366f;
        float minuteY=baseY-.125f*(half*2f),crownY=apexY+.26f*(half*2f);
        PointF l=PerspectiveMasterRenderer.projectPoint(p,-half,baseY),r=PerspectiveMasterRenderer.projectPoint(p,half,baseY),a=PerspectiveMasterRenderer.projectPoint(p,0,apexY),m=PerspectiveMasterRenderer.projectPoint(p,0,minuteY),c=PerspectiveMasterRenderer.projectPoint(p,0,crownY);

        PointF lr=PerspectiveRectifier.toDial(p,l),rr=PerspectiveRectifier.toDial(p,r),ar=PerspectiveRectifier.toDial(p,a),mr=PerspectiveRectifier.toDial(p,m),cr=PerspectiveRectifier.toDial(p,c);
        assertNotNull(lr);assertNotNull(rr);assertNotNull(ar);assertNotNull(mr);assertNotNull(cr);
        if(Math.abs(lr.x+half)>.001f||Math.abs(lr.y-baseY)>.001f||Math.abs(rr.x-half)>.001f||Math.abs(rr.y-baseY)>.001f)
            throw new AssertionError("roundtrip base failed L="+lr.x+","+lr.y+" R="+rr.x+","+rr.y);

        Triangle12RelationalMetric.Result q=Triangle12RelationalMetric.measureRectified(p,l,r,a,m,c);
        assertNotNull(q);
        if(Math.abs(q.baseGapRatio-GenTriangle12RelationalReference.BASE_TO_60_INNER_OVER_BASE)>.0005f || Math.abs(q.apexGapRatio-GenTriangle12RelationalReference.APEX_TO_CROWN_OVER_BASE)>.0005f || Math.abs(q.rotationDeg)>.01f)
            throw new AssertionError("metric failed base="+q.baseGapRatio+" crown="+q.apexGapRatio+" rot="+q.rotationDeg+" rect minute="+mr.x+","+mr.y+" crown="+cr.x+","+cr.y);
    }
}
