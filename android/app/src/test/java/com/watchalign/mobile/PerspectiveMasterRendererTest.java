package com.watchalign.mobile;

import android.graphics.PointF;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerspectiveMasterRendererTest {
    private static void assertPoint(PointF p,float x,float y){
        assertEquals(x,p.x,0.01f);
        assertEquals(y,p.y,0.01f);
    }

    @Test public void fourRealAnchorsMapExactly(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;
        p.centerX=500;p.centerY=520;
        p.anchor12X=485;p.anchor12Y=180;
        p.anchor3X=850;p.anchor3Y=500;
        p.anchor9X=130;p.anchor9Y=545;

        assertPoint(PerspectiveMasterRenderer.projectPoint(p,0,0),500,520);
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,0,-1),485,180);
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,1,0),850,500);
        assertPoint(PerspectiveMasterRenderer.projectPoint(p,-1,0),130,545);
    }

    @Test public void sixIsInferredNotMirroredFromTwelve(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;
        p.centerX=500;p.centerY=520;
        p.anchor12X=485;p.anchor12Y=180;
        p.anchor3X=850;p.anchor3Y=500;
        p.anchor9X=130;p.anchor9Y=545;

        PointF six=PerspectiveMasterRenderer.projectPoint(p,0,1);
        float mirroredX=2*p.centerX-p.anchor12X;
        float mirroredY=2*p.centerY-p.anchor12Y;
        assertTrue(Math.abs(six.x-mirroredX)>0.1f || Math.abs(six.y-mirroredY)>0.1f);
    }

    @Test public void movingThreeDoesNotMoveNine(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;
        p.centerX=500;p.centerY=500;
        p.anchor12X=500;p.anchor12Y=150;
        p.anchor3X=850;p.anchor3Y=500;
        p.anchor9X=150;p.anchor9Y=500;
        PointF nineBefore=PerspectiveMasterRenderer.projectPoint(p,-1,0);
        p.anchor3X=875;p.anchor3Y=470;
        PointF nineAfter=PerspectiveMasterRenderer.projectPoint(p,-1,0);
        assertPoint(nineAfter,nineBefore.x,nineBefore.y);
    }
}
