package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmtMarkerQcRepairTest {
    @Test public void markerDatumsAreShapeSpecificAndTriangleSeparatesVisualFromDetectionCentre(){
        assertEquals(Gmt126710BlnrMaster.TRI_DETECTION_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(12),1e-12);
        assertEquals(Gmt126710BlnrMaster.BATON_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(6),1e-12);
        assertEquals(Gmt126710BlnrMaster.BATON_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(9),1e-12);
        assertEquals(Gmt126710BlnrMaster.TRI_CENTER_R,GmtMarkerQcRepair.visualRadiusRatio(12),1e-12);
        assertEquals(Gmt126710BlnrMaster.BATON_CENTER_R,GmtMarkerQcRepair.visualRadiusRatio(6),1e-12);
        assertTrue(Math.abs(Gmt126710BlnrMaster.TRI_DETECTION_CENTER_R-Gmt126710BlnrMaster.TRI_CENTER_R)>0.015);
        assertTrue(Math.abs(Gmt126710BlnrMaster.ROUND_CENTER_R-Gmt126710BlnrMaster.BATON_CENTER_R)>0.03);
    }

    @Test public void v5MarkerCentresMatchTheOfficialCatalogueCalibration(){
        // Regression guard for the v5 recalibration, sourced from OfficialMarkerCalibrationTest
        // measuring the first-party Rolex catalogue image (media.rolex.com) -- deliberately never
        // a user-submitted photo, after an earlier attempt using one turned out to be a replica
        // and had to be reverted. Round markers 1,4,5,7,8,10,11 clustered at +1.888 to +3.493%R
        // (mean +2.865%R); marker 2's -0.201%R reading was excluded as a clear outlier (a lone
        // near-zero value ~2 points from its nearest neighbour, most likely a reflection/highlight
        // artifact on this single photo, not a real manufacturing asymmetry). 6/9 batons measured
        // +0.877/+1.208%R (mean +1.043%R). The triangle's centroid-to-visual-anchor gap (0.020) is
        // preserved as a fixed property of its shape, not re-derived per photo.
        assertEquals(0.751,Gmt126710BlnrMaster.ROUND_CENTER_R,1e-9);
        assertEquals(0.687,Gmt126710BlnrMaster.BATON_CENTER_R,1e-9);
        assertEquals(0.719,Gmt126710BlnrMaster.TRI_CENTER_R,1e-9);
        assertEquals(0.020,Gmt126710BlnrMaster.TRI_DETECTION_CENTER_R-Gmt126710BlnrMaster.TRI_CENTER_R,1e-9);
        assertEquals(0.002,Gmt126710BlnrMaster.TRI_CENTER_R-Gmt126710BlnrMaster.TRI_LUME_CENTER_R,1e-9);
    }

    @Test public void roundHourMarkersUseTheSameDatumForDetectionAndVisual(){
        // Unlike the triangle, round markers have no centroid/visual-anchor distinction, so the
        // detection datum used for QC offsets and the visual datum used to draw the overlay must
        // be identical -- any recalibration of ROUND_CENTER_R applies to both directly.
        for(int hour:new int[]{1,2,4,5,7,8,10,11}){
            assertEquals(Gmt126710BlnrMaster.ROUND_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(hour),1e-12);
            assertEquals(Gmt126710BlnrMaster.ROUND_CENTER_R,GmtMarkerQcRepair.visualRadiusRatio(hour),1e-12);
        }
    }

    @Test public void reportIncludesRoundHourMarkersAlongside12And6And9(){
        String report="Extended QC checks\n2 marker vs minute track: position +99.0°, body rotation +99.0°\n";
        GmtMarkerQcRepair.MarkerDiagnostic[] d=new GmtMarkerQcRepair.MarkerDiagnostic[13];
        d[2]=new GmtMarkerQcRepair.MarkerDiagnostic(2,0.10,1.50,Double.NaN,true);
        d[12]=new GmtMarkerQcRepair.MarkerDiagnostic(12,0.04,1.35,0.62,true);

        String out=GmtMarkerQcRepair.rewriteReport(report,d);

        assertTrue(out.contains("2 marker vs minute track: angular offset +0.10°, radial +1.50% R vs calibrated marker datum"));
        assertTrue(out.contains("12 marker vs minute track: angular offset +0.04°, radial +1.35% R vs calibrated marker datum"));
        assertFalse(out.contains("99.0"));
    }

    @Test public void radialSignIsPositiveForOutwardHighMarker(){
        double expected=GmtMarkerQcRepair.expectedRadiusRatio(12);
        assertEquals(1.25,GmtMarkerQcRepair.radialOffsetPctR(expected+0.0125,12),1e-9);
        assertEquals(-1.25,GmtMarkerQcRepair.radialOffsetPctR(expected-0.0125,12),1e-9);
    }

    @Test public void reportSeparatesAngularRadialAndBodyRotationAndRemovesLegacyTopFinding(){
        String report="Top QC findings\n"+
                "1. 9 marker local position -0.75° vs minute track\n\n"+
                "Extended QC checks\n"+
                "12 marker vs minute track: position +0.04°, body rotation -48.53°\n"+
                "6 marker vs minute track: position -0.22°, body rotation +0.87°\n"+
                "9 marker vs minute track: position -0.75°, body rotation -1.31°\n"+
                "Dial-print zone sharpness: 100.0\n";
        GmtMarkerQcRepair.MarkerDiagnostic[] d=new GmtMarkerQcRepair.MarkerDiagnostic[13];
        d[12]=new GmtMarkerQcRepair.MarkerDiagnostic(12,0.04,1.35,0.62,true);
        d[6]=new GmtMarkerQcRepair.MarkerDiagnostic(6,-0.22,-0.15,0.84,true);
        d[9]=new GmtMarkerQcRepair.MarkerDiagnostic(9,-0.78,0.05,-1.28,true);

        String out=GmtMarkerQcRepair.rewriteReport(report,d);

        assertTrue(out.contains("12 marker vs minute track: angular offset +0.04°, radial +1.35% R vs calibrated marker datum, body rotation +0.62°"));
        assertTrue(out.contains("9 marker vs minute track: angular offset -0.78°, radial +0.05% R vs calibrated marker datum, body rotation -1.28°"));
        assertTrue(out.contains("positive = outward/high"));
        assertTrue(out.contains("genuine-derived marker centroid datum"));
        assertFalse(out.contains("local position"));
        assertFalse(out.contains("-48.53°"));
        assertFalse(out.contains("1. 9 marker"));
    }

    @Test public void unmeasuredMarkerDoesNotLeakAnyLegacyValue(){
        String report="Top QC findings\n"+
                "1. 12 marker body rotation -48.53° (strong detected deviation)\n\n"+
                "Extended QC checks\n"+
                "12 marker vs minute track: position +44.45°, body rotation +72.39°\n";
        GmtMarkerQcRepair.MarkerDiagnostic[] d=new GmtMarkerQcRepair.MarkerDiagnostic[13];
        d[12]=new GmtMarkerQcRepair.MarkerDiagnostic(12,Double.NaN,Double.NaN,Double.NaN,true,false);

        String out=GmtMarkerQcRepair.rewriteReport(report,d);

        assertTrue(out.contains("12 marker: not confidently isolated inside projected master ROI"));
        assertFalse(out.contains("44.45"));
        assertFalse(out.contains("72.39"));
        assertFalse(out.contains("48.53"));
    }

    @Test public void lowConfidenceOrientationCanRemainUnavailableWithoutDroppingPosition(){
        String report="Top QC findings: no material geometric deviation detected.\n\n"+
                "Extended QC checks\n"+
                "12 marker vs minute track: position +0.04°, body rotation -48.53°\n";
        GmtMarkerQcRepair.MarkerDiagnostic[] d=new GmtMarkerQcRepair.MarkerDiagnostic[13];
        d[12]=new GmtMarkerQcRepair.MarkerDiagnostic(12,0.04,0.80,Double.NaN,true);

        String out=GmtMarkerQcRepair.rewriteReport(report,d);

        assertTrue(out.contains("body rotation unavailable (component isolation confidence low)"));
        assertFalse(out.contains("-48.53°"));
    }
}
