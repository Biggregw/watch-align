package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;

public class PerspectiveGmtOverlayTest {
    private static final double EPS = 1e-9;

    @Test public void circleMapsCanonicalDirectionsIndependentOfEllipseAxisAngle() {
        RotatedRect ellipse=new RotatedRect(new Point(100,200),new Size(80,80),37);
        Point[] p=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,0);

        assertPoint(p[0],100,160);
        assertPoint(p[1],140,200);
        assertPoint(p[2],100,240);
        assertPoint(p[3],60,200);
        assertOppositePairs(p,ellipse.center);
    }

    @Test public void axisAlignedEllipseMapsCanonicalDirectionsToOwnAxes() {
        RotatedRect ellipse=new RotatedRect(new Point(50,70),new Size(100,60),0);
        Point[] p=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,0);

        assertPoint(p[0],50,40);
        assertPoint(p[1],100,70);
        assertPoint(p[2],50,100);
        assertPoint(p[3],0,70);
        assertOppositePairs(p,ellipse.center);
    }

    @Test public void rotatedEllipseMapsDirectionsThroughRotatedAxisTransform() {
        RotatedRect ellipse=new RotatedRect(new Point(200,300),new Size(120,60),30);
        Point[] p=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,0);

        double c=Math.cos(Math.toRadians(30)),s=Math.sin(Math.toRadians(30));
        double a=60,b=30;
        assertPoint(p[0],200-(a-b)*s*c,300-(a*s*s+b*c*c));
        assertPoint(p[1],200+a*c*c+b*s*s,300+(a-b)*s*c);
        assertPoint(p[2],200+(a-b)*s*c,300+(a*s*s+b*c*c));
        assertPoint(p[3],200-(a*c*c+b*s*s),300-(a-b)*s*c);
        assertOppositePairs(p,ellipse.center);
    }

    private static void assertOppositePairs(Point[] p,Point center) {
        assertEquals(center.x*2.0,p[0].x+p[2].x,EPS);
        assertEquals(center.y*2.0,p[0].y+p[2].y,EPS);
        assertEquals(center.x*2.0,p[1].x+p[3].x,EPS);
        assertEquals(center.y*2.0,p[1].y+p[3].y,EPS);
    }

    private static void assertPoint(Point actual,double x,double y) {
        assertEquals(x,actual.x,EPS);
        assertEquals(y,actual.y,EPS);
    }
}
