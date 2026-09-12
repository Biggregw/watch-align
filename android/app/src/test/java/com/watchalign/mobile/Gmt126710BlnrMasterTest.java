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

    @Test public void masterIdMarksReferenceCalibration(){
        assertTrue(Gmt126710BlnrMaster.ID.contains("reference-calibrated"));
    }
}
