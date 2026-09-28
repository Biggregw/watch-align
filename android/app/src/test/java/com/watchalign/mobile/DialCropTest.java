package com.watchalign.mobile;

import static org.junit.Assert.*;

import org.junit.Test;

/** Full-resolution dial crop (alpha61): mapping measurements back onto the preview. */
public class DialCropTest {
    private static GmtDialCrop.Crop crop(){
        GmtDialCrop.Crop c=new GmtDialCrop.Crop();
        c.originX=100;c.originY=50;c.scale=1.5;c.k=2.0;c.previewRadius=200;c.cropRadius=560;
        return c;
    }

    @Test public void cropPointsMapToThePreview(){
        GmtDialCrop.Crop c=crop();
        // Crop pixel 300 -> original 100 + 300/1.5 = 300 -> preview 150.
        assertEquals(150,c.toPreviewX(300),1e-9);
        assertEquals((50+60/1.5)/2,c.toPreviewY(60),1e-9);
        assertEquals(10.0/3.0,c.toPreviewLength(10),1e-9);
    }

    @Test public void drawingIsMappedOnceEvenWhenArraysAreShared(){
        GmtDialCrop.Crop c=crop();
        MeasuredOverlayRenderer.Drawing d=new MeasuredOverlayRenderer.Drawing();
        d.dialCx=300;d.dialCy=60;d.dialA=30;d.dialB=30;
        double[] shared={300,60};
        d.twelve=new GmtTwelveLandmarkAnalyzer.Geometry(new double[]{300,60},new double[]{300,60},new double[]{300,60},shared,shared,new double[]{300,60},true);
        GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(4);
        m.found=true;m.x=300;m.y=60;m.radiusPx=30;m.seedX=300;m.seedY=60;m.expectedRadiusPx=30;
        m.tickBefore=new double[]{300,60};m.tickCentre=shared;m.tickAfter=new double[]{300,60};
        d.round.add(m);
        d.mapTo(c);
        double px=c.toPreviewX(300),py=c.toPreviewY(60);
        assertEquals(px,d.dialCx,1e-9);assertEquals(10,d.dialA,1e-9);
        assertEquals(px,d.twelve.tick59[0],1e-9);assertEquals(py,d.twelve.tick60[1],1e-9);
        assertEquals(px,m.tickCentre[0],1e-9);       // shared array moved once, not twice
        assertEquals(px,m.x,1e-9);assertEquals(10,m.radiusPx,1e-9);assertEquals(px,m.tickAfter[0],1e-9);
    }
}
