package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class PerspectiveMasterRendererTest {
    private static PerspectiveMasterRenderer.Pose skewedPose(){
        PerspectiveMasterRenderer.Pose p=new PerspectiveMasterRenderer.Pose();
        p.anchorMode=true;p.perspectiveMode=true;
        p.anchor12X=485;p.anchor12Y=180;
        p.anchor3X=850;p.anchor3Y=500;
        p.anchor6X=530;p.anchor6Y=900;
        p.anchor9X=130;p.anchor9Y=545;
        return p;
    }

    private static double[] homography(PerspectiveMasterRenderer.Pose p){return PerspectiveMasterRenderer.buildH(p);}

    private static double[] project(double[] h,double x,double y){
        double d=h[6]*x+h[7]*y+1.0;
        return new double[]{(h[0]*x+h[1]*y+h[2])/d,(h[3]*x+h[4]*y+h[5])/d};
    }

    private static void assertPoint(double[] p,double x,double y){assertEquals(x,p[0],0.01);assertEquals(y,p[1],0.01);}

    @Test public void fourEdgeAnchorsMapExactly()throws Exception{
        PerspectiveMasterRenderer.Pose p=skewedPose();double[] h=homography(p);assertNotNull(h);
        assertPoint(project(h,0,-1),485,180);
        assertPoint(project(h,1,0),850,500);
        assertPoint(project(h,0,1),530,900);
        assertPoint(project(h,-1,0),130,545);
    }

    @Test public void centerIsProjectivelyInferredNotSimpleMidpoint()throws Exception{
        PerspectiveMasterRenderer.Pose p=skewedPose();double[] centre=project(homography(p),0,0);
        double mid126X=(p.anchor12X+p.anchor6X)/2.0,mid126Y=(p.anchor12Y+p.anchor6Y)/2.0;
        double mid39X=(p.anchor3X+p.anchor9X)/2.0,mid39Y=(p.anchor3Y+p.anchor9Y)/2.0;
        assertTrue(Math.abs(centre[0]-mid126X)>0.1 || Math.abs(centre[1]-mid126Y)>0.1);
        assertTrue(Math.abs(centre[0]-mid39X)>0.1 || Math.abs(centre[1]-mid39Y)>0.1);
    }

    @Test public void movingThreeDoesNotMoveNine()throws Exception{
        PerspectiveMasterRenderer.Pose p=skewedPose();double[] before=project(homography(p),-1,0);
        p.anchor3X=875;p.anchor3Y=470;double[] after=project(homography(p),-1,0);
        assertPoint(after,before[0],before[1]);
    }
}
