package com.watchalign.mobile;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Reporting regressions from the owner's 11 October evidence pack. No detector/limit changes. */
public class InspectionReportingTest {
    private static Alpha99Findings.Measure rotation(double value){
        Alpha99Findings.Measure m=new Alpha99Findings.Measure("rotation",value,.64543,.205383,3,46,"deg","rotated anticlockwise","It is rotated anticlockwise");
        m.visibleBar=Alpha99Findings.VISIBLE_DEG;return m;
    }
    @Test public void outsideButSubtleIsNeverCalledWithinGenuine(){
        Alpha99Findings.Finding f=new Alpha99Findings.Finding("six","6 o'clock","6");
        f.measures.add(rotation(.97));Alpha99Findings.settle(f);
        Alpha99Findings.Summary s=new Alpha99Findings.Summary();s.all.add(f);
        assertEquals(Alpha99Findings.Status.MINOR,f.status);
        assertEquals("",s.withinLine());assertTrue(s.belowVisibilityLine().contains("Outside measured genuine range"));
        assertTrue(f.statusLabel().contains("VISIBILITY"));assertFalse(f.statusLabel().contains("WITHIN"));
        assertTrue(String.join(" ",f.detail()).contains("outside the measured genuine range"));
        assertFalse(String.join(" ",f.detail()).contains("less than the eye"));
        assertTrue(s.tiles().isEmpty()); // existing non-highlighted decision preserved
    }
    @Test public void withinAndSubtleFeaturesAreListedSeparately(){
        Alpha99Findings.Summary s=new Alpha99Findings.Summary();
        Alpha99Findings.Finding within=new Alpha99Findings.Finding("nine","9 o'clock","9");within.measures.add(rotation(.19));Alpha99Findings.settle(within);
        Alpha99Findings.Finding subtle=new Alpha99Findings.Finding("six","6 o'clock","6");subtle.measures.add(rotation(.97));subtle.partNotAssessed="position";Alpha99Findings.settle(subtle);
        s.all.add(within);s.all.add(subtle);
        assertEquals("Within measured genuine range: 9",s.withinLine());
        assertTrue(s.belowVisibilityLine().endsWith("6 rotation"));
    }
    @Test public void screenshotRotationRemainsClearEvenWhenPositionIsWithheld(){
        // Screenshot measurement, not an independent rerun: 1.37 CCW at R~225.
        Alpha99Findings.Finding f=new Alpha99Findings.Finding("nine","9 o'clock","9");
        f.measures.add(rotation(1.37));f.partNotAssessed="position";Alpha99Findings.settle(f);
        assertEquals(Alpha99Findings.Status.CLEAR,f.status);assertEquals("CLEAR FINDING",f.statusLabel());
        assertTrue(Alpha99Findings.DISCLAIMER.contains("not a decision"));
    }
    @Test public void suppliedOriginalRetainsRotationAndWithholdsBoundaryDependentMetrics(){
        // Exact frozen fit of 37828.jpg (c50e0845...), R differs from the screenshot's input.
        double R=269.63581425909763;
        Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(9,"baton");m.usable=true;
        m.rotationDeg=-1.1166353217373581;
        m.sideOffR=new double[]{-.013460772136802489,.0037042164561694735,.00616771821845833,.006815202605856441};
        m.localRadialPx=-.08234370581424366;m.localTangentialPx=1.2195449474853401;m.localOffsetPx=Math.hypot(m.localRadialPx,m.localTangentialPx);
        m.fitVertices=new double[][]{{-.5943891649720965,.0426897488205135},{-.9094749321792048,.04904984580981688},{-.9136502481079922,-.06258953469006152},{-.5980061977744682,-.06852288279117093}};
        Alpha94MarkerMeasurement.Ring ring=new Alpha94MarkerMeasurement.Ring();ring.usable=true;
        ring.model=new double[]{.0010117808294243978,-.00408303176262586,.0002662049168247982,.0016570220441682615};
        Alpha94MarkerMeasurement.Report r=new Alpha94MarkerMeasurement.Report(Arrays.asList(m),ring,R,null);
        Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(9);c.clean=true;
        Alpha99Findings.Finding f=Alpha99Findings.baton(r,TestModels.gmt().atHour(9),R,c,TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.WORTH,f.status);
        assertEquals("position",f.partNotAssessed);assertEquals(1,f.measures.size());
        assertEquals(1.1746053217373581,f.measures.get(0).value,1e-12);
        assertTrue(f.edgeDiagnostic.contains("upper 1.17° anticlockwise; lower 1.25° anticlockwise"));
        assertFalse(f.edgeDiagnostic.contains("Cyan")); // outline is withheld, so do not claim it is drawn
        String details=String.join(" ",f.detail());assertTrue(details.contains("does not establish which edge"));
        assertTrue(details.contains("Rotation is assessed separately"));
        MarkerMismatchResearch.Result q=MarkerMismatchResearch.score(r,TestModels.gmt().atHour(9),TestModels.gmtRef(),f,c);
        assertFalse(q.trusted());assertTrue(q.display().contains("withheld"));
        assertEquals(11.43539277,q.score.mismatchPct,1e-8); // offline only; not a displayed defect score
    }
    @Test public void spacingDescriptionDoesNotClaimADirectMetalToTickMeasurement(){
        Alpha94MarkerMeasurement.Report r=Alpha99FindingsTest.twelveAt(.0068);
        Alpha99Findings.Finding f=Alpha99Findings.twelve(r,Alpha99FindingsTest.allClean().get(12),r.dialRadiusPx,TestModels.gmt().atHour(12),TestModels.gmtRef(),TestModels.gmt().minuteTrackInnerR);
        Alpha99Findings.Measure track=null;for(Alpha99Findings.Measure m:f.measures)if(m.name.equals("track"))track=m;
        assertNotNull(track);assertEquals(Alpha99Findings.Status.WORTH,track.status());
        assertTrue(track.sentence.contains("position-based spacing estimate"));assertTrue(track.sentence.contains("not a direct measurement"));
        assertTrue(track.capAtWorth);assertTrue(track.capReason.contains("polished surround"));
    }
    @Test public void technicalDetailsDoNotRebrandAnOutsideReadingAsWithin(){
        Alpha99Pipeline.Output o=new Alpha99Pipeline.Output();o.model=TestModels.gmt();o.reference=TestModels.gmtRef();
        o.pose=new AutomaticDialOverlay.Result("synthetic reporting fixture");
        o.measurement=new Alpha94MarkerMeasurement.Report(java.util.Collections.emptyList(),null,225,null);
        o.summary=new Alpha99Findings.Summary();
        Alpha99Findings.Finding f=new Alpha99Findings.Finding("six","6 o'clock","6");f.measures.add(rotation(.97));Alpha99Findings.settle(f);o.summary.all.add(f);
        String text=Alpha99Pipeline.technical(o);
        assertTrue(text.contains("OUTSIDE GENUINE RANGE (below visibility guideline"));
        assertFalse(text.contains("WITHIN (too small"));
        assertTrue(text.contains("scoring reference and a separate fixed master"));
        assertTrue(text.contains("Continuous geometric area"));
    }
}
