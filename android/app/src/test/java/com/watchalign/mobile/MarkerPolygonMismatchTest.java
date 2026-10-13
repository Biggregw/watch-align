package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class MarkerPolygonMismatchTest {
    private static double[][] rectangle(double x0,double y0,double x1,double y1){
        return new double[][]{{x0,y0},{x1,y0},{x1,y1},{x0,y1}};
    }
    private static double[][] rotate(double[][] src,double deg){
        double a=Math.toRadians(deg),c=Math.cos(a),s=Math.sin(a);
        double[][] out=new double[src.length][2];
        for(int i=0;i<src.length;i++){double x=src[i][0],y=src[i][1];
            out[i][0]=x*c-y*s;out[i][1]=x*s+y*c;}
        return out;
    }
    @Test public void sameShapeIsPerfectOverlap(){
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(rectangle(0,0,2,1),rectangle(0,0,2,1));
        assertNotNull(q);
        assertEquals(0,q.mismatchPct,1e-9);
        assertEquals(0,q.extraPct,1e-9);
        assertEquals(0,q.missingPct,1e-9);
    }
    @Test public void shiftCountsBothExtraAndMissing(){
        // unit squares shifted by 0.5: each non-overlap is .5 of the filled expected square.
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(rectangle(0,0,1,1),rectangle(.5,0,1.5,1));
        assertEquals(50,q.extraPct,1e-8);
        assertEquals(50,q.missingPct,1e-8);
        assertEquals(100,q.mismatchPct,1e-8);
    }
    @Test public void completeSeparationCountsBothFilledAreas(){
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(rectangle(0,0,1,1),rectangle(2,0,3,1));
        assertEquals(200,q.mismatchPct,1e-8);
    }
    @Test public void windingOrderDoesNotChangeResult(){
        double[][] a=rectangle(0,0,1,1);
        double[][] b=rectangle(.25,.1,1.25,1.1);
        double[][] rev=new double[b.length][];
        for(int i=0;i<b.length;i++)rev[i]=b[b.length-1-i];
        assertEquals(MarkerPolygonMismatch.compare(a,b).mismatchPct,
                     MarkerPolygonMismatch.compare(a,rev).mismatchPct,1e-9);
    }
    @Test public void oneDegreeGmtBatonRotationIsTwoPointFivePercent(){
        double[][] baton=rectangle(-.1525,-.0594,.1525,.0594);
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(baton,rotate(baton,1.0));
        assertEquals(2.55033,q.mismatchPct,.002);
        assertEquals(q.extraPct,q.missingPct,1e-9);
    }
    @Test public void gmtTriangleShiftProducesEightPointThreePercent(){
        double[][] tri={{0,-.5925},{.12375,-.9025},{-.12375,-.9025}};
        double[][] moved={{0,-.5993},{.12375,-.9093},{-.12375,-.9093}};
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(tri,moved);
        assertEquals(8.67796,q.mismatchPct,.003);
    }
    @Test public void sizeChangeCreatesUnequalExtraAndMissing(){
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(rectangle(0,0,2,2),rectangle(0,0,2.2,2));
        assertEquals(0,q.missingPct,1e-8);
        assertEquals(10,q.extraPct,1e-8);
        assertEquals(.4,q.extraArea,1e-8);
        assertEquals(0,q.missingArea,1e-8);
    }
    @Test public void circleDiscretizationShowsZeroMismatchForIdenticalShape(){
        double[][] c=MarkerPolygonMismatch.circle(.3,.6,.08,128);
        assertEquals(0,MarkerPolygonMismatch.compare(c,c).mismatchPct,1e-7);
    }
    @Test public void degenerateShapeIsWithheld(){
        assertNull(MarkerPolygonMismatch.compare(rectangle(0,0,1,1),new double[][]{{0,0},{1,1},{2,2}}));
    }
    @Test public void malformedVertexIsWithheld(){
        assertNull(MarkerPolygonMismatch.compare(rectangle(0,0,1,1),new double[][]{{0,0},null,{1,1}}));
    }
    @Test public void undersizingIsOnlyMissingAndDenominatorIsDirectional(){
        double[][] e=rectangle(0,0,2,2),a=rectangle(0,0,1,2);
        assertEquals(50,MarkerPolygonMismatch.compare(e,a).mismatchPct,1e-8);
        assertEquals(100,MarkerPolygonMismatch.compare(a,e).mismatchPct,1e-8);
    }
}
