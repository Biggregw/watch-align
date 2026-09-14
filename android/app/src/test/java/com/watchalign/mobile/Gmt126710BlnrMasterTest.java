package com.watchalign.mobile;

import org.junit.Test;
import static org.junit.Assert.*;

public class Gmt126710BlnrMasterTest {
    @Test public void markerGeometryStaysInsideDial(){
        assertTrue(Gmt126710BlnrMaster.MARKER_CENTER_R + Gmt126710BlnrMaster.ROUND_OUTER_R < Gmt126710BlnrMaster.MINUTE_TRACK_R);
        assertTrue(Gmt126710BlnrMaster.MARKER_CENTER_R + Gmt126710BlnrMaster.BATON_RADIAL_HALF < Gmt126710BlnrMaster.MINUTE_TRACK_R);
        assertTrue(Gmt126710BlnrMaster.TRI_CENTER_R + Gmt126710BlnrMaster.TRI_BASE_OUTWARD < Gmt126710BlnrMaster.MINUTE_TRACK_R);
    }

    @Test public void metalSurroundsRemainThinnerThanLumeBodies(){
        assertTrue(Gmt126710BlnrMaster.ROUND_LUME_R < Gmt126710BlnrMaster.ROUND_OUTER_R);
        assertTrue(Gmt126710BlnrMaster.BATON_LUME_RADIAL_HALF < Gmt126710BlnrMaster.BATON_RADIAL_HALF);
        assertTrue(Gmt126710BlnrMaster.BATON_LUME_TANGENTIAL_HALF < Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF);
        assertTrue(Gmt126710BlnrMaster.TRI_LUME_HALF_BASE < Gmt126710BlnrMaster.TRI_HALF_BASE);
    }

    @Test public void idealOverlayUsesCleanCanonicalRoundAndBatonBodies(){
        // The ideal round body is a mathematical circle, not the old sparse photographed polygon.
        assertEquals(Gmt126710BlnrMaster.ROUND_OUTER_R, Gmt126710IdealOverlay.roundReferenceRadius(), 1e-9);

        // 6/9 use one full canonical rectangle with a stable outer and inner radial edge.
        double inner=Gmt126710IdealOverlay.batonReferenceInnerRadius();
        double outer=Gmt126710IdealOverlay.batonReferenceOuterRadius();
        assertTrue(inner < outer);
        assertTrue(outer < Gmt126710BlnrMaster.MINUTE_TRACK_R);
        assertEquals((inner+outer)*0.5, Gmt126710IdealOverlay.markerCenterRadius(6), 1e-9);
        assertEquals(Gmt126710IdealOverlay.markerCenterRadius(6), Gmt126710IdealOverlay.markerCenterRadius(9), 1e-9);
    }

    @Test public void masterIdMarksReferenceCalibration(){
        assertTrue(Gmt126710BlnrMaster.ID.contains("reference-calibrated"));
    }
}
