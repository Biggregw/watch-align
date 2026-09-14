package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class GenuineTriangleReferenceTest {
    @Test public void officialTriangleGeometryMatchesCalibration(){
        assertEquals("126710BLNR-reference-geometry-v3",Gmt126710BlnrMeasured.ID);
        assertEquals(0.7843200,Gmt126710BlnrMeasured.TRI_CENTER_R,1e-7);
        assertEquals(3,Gmt126710BlnrMeasured.TRI_OUTER.length);
        assertEquals(-0.086726f,Gmt126710BlnrMeasured.TRI_OUTER[0][0],1e-6f);
        assertEquals(0.098697f,Gmt126710BlnrMeasured.TRI_OUTER[0][1],1e-6f);
        assertEquals(0.085841f,Gmt126710BlnrMeasured.TRI_OUTER[1][0],1e-6f);
        assertEquals(0.000885f,Gmt126710BlnrMeasured.TRI_OUTER[2][0],1e-6f);
        assertEquals(-0.197395f,Gmt126710BlnrMeasured.TRI_OUTER[2][1],1e-6f);
        double baseRadius=Gmt126710BlnrMeasured.TRI_CENTER_R+Gmt126710BlnrMeasured.TRI_OUTER[0][1];
        double apexRadius=Gmt126710BlnrMeasured.TRI_CENTER_R+Gmt126710BlnrMeasured.TRI_OUTER[2][1];
        assertEquals(0.883017,baseRadius,1e-6);
        assertEquals(0.586925,apexRadius,1e-6);
    }

    @Test public void dateApertureIsExactlyCentredOnThreeOClockAxisBeforePerspective(){
        assertEquals(4,Gmt126710BlnrMeasured.DATE_APERTURE_OUTER.length);
        double tangentialMean=0.0;
        for(float[] p:Gmt126710BlnrMeasured.DATE_APERTURE_OUTER)tangentialMean+=p[0];
        tangentialMean/=Gmt126710BlnrMeasured.DATE_APERTURE_OUTER.length;
        assertEquals(0.0,tangentialMean,1e-9);
        assertEquals(-Gmt126710BlnrMeasured.DATE_TANGENTIAL_HALF,Gmt126710BlnrMeasured.DATE_APERTURE_OUTER[0][0],1e-7f);
        assertEquals( Gmt126710BlnrMeasured.DATE_TANGENTIAL_HALF,Gmt126710BlnrMeasured.DATE_APERTURE_OUTER[1][0],1e-7f);
        assertEquals( Gmt126710BlnrMeasured.DATE_TANGENTIAL_HALF,Gmt126710BlnrMeasured.DATE_APERTURE_OUTER[2][0],1e-7f);
        assertEquals(-Gmt126710BlnrMeasured.DATE_TANGENTIAL_HALF,Gmt126710BlnrMeasured.DATE_APERTURE_OUTER[3][0],1e-7f);
    }
}
