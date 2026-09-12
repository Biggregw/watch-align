package com.watchalign.mobile;

import android.graphics.PointF;
import org.junit.Test;
import static org.junit.Assert.*;

public class PerspectiveMasterRendererTest {
    private static void close(PointF p,float x,float y){assertEquals(x,p.x,0.02f);assertEquals(y,p.y,0.02f);}

    @Test public void twoPointMapsCenterAndTwelveExactly(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.centerX=400;p.centerY=500;p.anchor12X=420;p.anchor12Y=200;p.scalePx=300;
        close(PerspectiveMasterRenderer.projectPoint(p,0,0),400,500);
        close(PerspectiveMasterRenderer.projectPoint(p,0,-1),420,200);
    }

    @Test public void fourHandlePerspectiveMapsCardinalsAndCenter(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;p.centerX=400;p.centerY=500;
        p.anchor12X=420;p.anchor12Y=200;
        p.anchor3X=690;p.anchor3Y=520;
        p.anchor9X=120;p.anchor9Y=480;
        close(PerspectiveMasterRenderer.projectPoint(p,0,-1),420,200);
        close(PerspectiveMasterRenderer.projectPoint(p,1,0),690,520);
        close(PerspectiveMasterRenderer.projectPoint(p,-1,0),120,480);
        PointF centre=PerspectiveMasterRenderer.projectPoint(p,0,0);
        assertEquals(400f,centre.x,0.5f);assertEquals(500f,centre.y,0.5f);
    }
}
