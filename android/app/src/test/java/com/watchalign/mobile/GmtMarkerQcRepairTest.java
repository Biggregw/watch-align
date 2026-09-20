package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GmtMarkerQcRepairTest {
    @Test public void twelveUsesTriangleCentreWhileBatonsUseMarkerRingCentre(){
        assertEquals(Gmt126710BlnrMaster.TRI_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(12),1e-12);
        assertEquals(Gmt126710BlnrMaster.MARKER_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(6),1e-12);
        assertEquals(Gmt126710BlnrMaster.MARKER_CENTER_R,GmtMarkerQcRepair.expectedRadiusRatio(9),1e-12);
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

        assertTrue(out.contains("12 marker vs minute track: angular offset +0.04°, radial +1.35% R vs visual master, body rotation +0.62°"));
        assertTrue(out.contains("9 marker vs minute track: angular offset -0.78°, radial +0.05% R vs visual master, body rotation -1.28°"));
        assertTrue(out.contains("positive = outward/high"));
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
