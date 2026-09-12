package com.watchalign.mobile;

import android.graphics.PointF;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerspectiveMasterRendererTest {
    private static void assertPoint(PointF p,float x,float y){
        assertEquals(x,p.x,0.01f);
        assertEquals(y,p.y,0.01f);
    }

    private static PerspectiveMasterRenderer.Pose skewedPose(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;
        p.anchor12X=485;p.anchor12Y=180;
        p.anchor3X=850;p.anchor3Y=500;
        p.anchor6X=530;p.anchor6Y=900;
        p.anchor9X=130;p.anchor9Y=545;
        return p;
    }

    @Test public void fourEdgeAnchorsMapExactly(){
        PerspectiveMasterRenderer.Pose p=skewedPose();
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,0,-1),485,180);
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,1,0),850,500);
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,0,1),530,900);
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,-1,0),130,545);
    }

    @Test public void centerIsProjectivelyInferredNotSimpleMidpoint(){
        PerspectiveMasterRenderer.Pose p=skewedPose();
        PointF centre=PerspectiveMasterRenderer.projectPoint(p,0,0);
        float mid126X=(p.anchor12X+p.anchor6X)/2f;
        float mid126Y=(p.anchor12Y+p.anchor6Y)/2f;
        float mid39X=(p.anchor3X+p.anchor9X)/2f;
        float mid39Y=(p.anchor3Y+p.anchor9Y)/2f;
        assertTrue(Math.abs(centre.x-mid126X)>0.1f || Math.abs(centre.y-mid126Y)>0.1f);
        assertTrue(Math.abs(centre.x-mid39X)>0.1f || Math.abs(centre.y-mid39Y)>0.1f);
    }

    @Test public void movingThreeDoesNotMoveNine(){
        PerspectiveMasterRenderer.Pose p=skewedPose();
        PointF nineBefore=PerspectiveMasterRenderer.projectPoint(p,-1,0);
        p.anchor3X=875;p.anchor3Y=470;
        PointF nineAfter=PerspectiveMasterRenderer.projectPoint(p,-1,0);
        assertPoint(nineAfter,nineBefore.x,nineBefore.y);
    }
}
