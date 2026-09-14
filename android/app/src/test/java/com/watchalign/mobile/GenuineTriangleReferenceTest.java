package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class GenuineTriangleReferenceTest {
    @Test public void canonicalTriangleGeometryMatchesUnifiedInnerEdgeCalibration(){
        assertEquals("126710BLNR-inner-edge-reference-v4",Gmt126710BlnrMeasured.ID);
        assertEquals(Gmt126710BlnrMaster.TRI_CENTER_R,Gmt126710BlnrMeasured.TRI_CENTER_R,1e-9);
        assertEquals(3,Gmt126710BlnrMeasured.TRI_OUTER.length);
        assertEquals(-(float)Gmt126710BlnrMaster.TRI_HALF_BASE,Gmt126710BlnrMeasured.TRI_OUTER[0][0],1e-6f);
        assertEquals((float)Gmt126710BlnrMaster.TRI_BASE_OUTWARD,Gmt126710BlnrMeasured.TRI_OUTER[0][1],1e-6f);
        assertEquals((float)Gmt126710BlnrMaster.TRI_HALF_BASE,Gmt126710BlnrMeasured.TRI_OUTER[1][0],1e-6f);
        assertEquals(0f,Gmt126710BlnrMeasured.TRI_OUTER[2][0],1e-6f);
        assertEquals(-(float)Gmt126710BlnrMaster.TRI_APEX_INWARD,Gmt126710BlnrMeasured.TRI_OUTER[2][1],1e-6f);
        double baseRadius=Gmt126710BlnrMeasured.TRI_CENTER_R+Gmt126710BlnrMeasured.TRI_OUTER[0][1];
        double apexRadius=Gmt126710BlnrMeasured.TRI_CENTER_R+Gmt126710BlnrMeasured.TRI_OUTER[2][1];
        assertEquals(Gmt126710BlnrMaster.TRI_CENTER_R+Gmt126710BlnrMaster.TRI_BASE_OUTWARD,baseRadius,1e-6);
        assertEquals(Gmt126710BlnrMaster.TRI_CENTER_R-Gmt126710BlnrMaster.TRI_APEX_INWARD,apexRadius,1e-6);
        assertTrue(apexRadius<baseRadius);
        assertTrue(baseRadius<Gmt126710BlnrMaster.MINUTE_TRACK_R);
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
