package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class BatonFitDiagnosticTest {
    private static final double[][] BATON={
            {-0.15,-0.06},{0.15,-0.06},{0.15,0.06},{-0.15,0.06}
    };

    private static double[][] rotate(double[][] points,double deg){
        double a=Math.toRadians(deg),c=Math.cos(a),s=Math.sin(a);
        double[][] out=new double[points.length][];
        for(int i=0;i<points.length;i++){
            double x=points[i][0],y=points[i][1];
            out[i]=new double[]{c*x-s*y,s*x+c*y};
        }
        return out;
    }

    @Test public void exactMatchShowsZeroOnBothLongEdges(){
        BatonFitDiagnostic.Angles a=BatonFitDiagnostic.angles(BATON,BATON);
        assertNotNull(a);
        assertEquals(0,a.side0Deg,1e-10);
        assertEquals(0,a.side2Deg,1e-10);
    }

    @Test public void knownClockwiseRotationAppearsOnBothEdges(){
        BatonFitDiagnostic.Angles a=BatonFitDiagnostic.angles(BATON,rotate(BATON,1.4));
        assertNotNull(a);
        assertEquals(1.4,a.side0Deg,1e-9);
        assertEquals(1.4,a.side2Deg,1e-9);
    }

    @Test public void ringAdjustedYellowIsUsedRatherThanUnmovedMaster(){
        double[] ring={0.009,-0.007,0.005,Math.toRadians(0.6)};
        double[][] expected=BatonFitDiagnostic.moved(BATON,ring);
        BatonFitDiagnostic.Angles matched=BatonFitDiagnostic.angles(expected,expected);
        assertEquals(0,matched.side0Deg,1e-10);
        assertEquals(0,matched.side2Deg,1e-10);
        BatonFitDiagnostic.Angles offMaster=BatonFitDiagnostic.angles(BATON,expected);
        assertEquals(0.6,offMaster.side0Deg,0.01);
        assertEquals(0.6,offMaster.side2Deg,0.01);
    }

    @Test public void oppositeLongEdgesCanHaveIndependentAngles(){
        double[][] trapezoid={{-0.15,-0.06},{0.15,-0.06},
                {0.15,0.06+0.004},{-0.15,0.06}};
        BatonFitDiagnostic.Angles a=BatonFitDiagnostic.angles(BATON,trapezoid);
        assertEquals(0,a.side0Deg,1e-10);
        assertTrue(Math.abs(a.side2Deg)>0.5);
    }

    @Test public void invalidFitsAreNotPresentedAsMeasurements(){
        assertNull(BatonFitDiagnostic.angles(BATON,null));
        assertNull(BatonFitDiagnostic.angles(BATON,new double[][]{{0,0}}));
        double[][] invalid={{0,0},{0,0},{1,1},{2,2}};
        assertNull(BatonFitDiagnostic.angles(BATON,invalid));
    }
}
