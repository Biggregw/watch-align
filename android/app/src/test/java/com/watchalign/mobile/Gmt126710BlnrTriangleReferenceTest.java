package com.watchalign.mobile;

import static org.junit.Assert.*;
import org.junit.Test;

public class Gmt126710BlnrTriangleReferenceTest {
    @Test public void dimensionsMatchCorrectedPilotReference(){
        assertEquals(0.2500f,Gmt126710BlnrTriangleReference.WIDTH_R,0.0001f);
        assertEquals(0.3115f,Gmt126710BlnrTriangleReference.HEIGHT_R,0.0001f);
        assertEquals(0.03075f,Gmt126710BlnrTriangleReference.BASE_TO_TRACK_R,0.0001f);
        assertEquals(0.123f,Gmt126710BlnrTriangleReference.CALIBRATED_BASE_TO_60_OVER_BASE,0.0001f);
        assertEquals(1.246f,Gmt126710BlnrTriangleReference.CALIBRATED_HEIGHT_OVER_BASE,0.0001f);
    }

    @Test public void triangleIsCentredOnTwelveAxis(){
        float[][] v=Gmt126710BlnrTriangleReference.vertices();
        assertEquals(-v[0][0],v[1][0],0.0001f);
        assertEquals(v[0][1],v[1][1],0.0001f);
        assertEquals(0f,v[2][0],0.0001f);
        assertTrue(v[0][1] < v[2][1]);
    }

    @Test public void minuteTrackStartsOutsideMarkerBase(){
        assertTrue(Gmt126710BlnrTriangleReference.MINUTE_INNER_R > -Gmt126710BlnrTriangleReference.LEFT_Y);
        assertEquals(1f,Gmt126710BlnrTriangleReference.MINUTE_OUTER_R,0.0001f);
    }
}
