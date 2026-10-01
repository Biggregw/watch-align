package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

/** alpha72: new families feed the mature renderer/close-up planner through a neutral adapter. */
public class CommonPresentationTest {

    static Sub124060QcAnalyzer.Result subResult(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.dialSource=Sub124060QcAnalyzer.DialSource.AUTO_EDGE_FIT;
        r.frame=GmtRoundMarkerAnalyzer.DialFrame.circle(500,500,400);
        r.dialReproducible=true;

        SubTwelveTriangle.Cand c=new SubTwelveTriangle.Cand();
        c.L=new double[]{450,210};c.R=new double[]{550,210};c.T=new double[]{500,120};c.outline="single";
        r.triangle=c;r.tick59=new double[]{470,95};r.tick60=new double[]{500,94};r.tick01=new double[]{530,95};
        r.rotationDeg=-0.18;r.gapR=0.024;r.centringW=0.011;

        for(GmtSixLandmarkAnalyzer.Position p:Sub124060Layout.BATONS){
            Sub124060QcAnalyzer.Baton b=new Sub124060QcAnalyzer.Baton(p);b.status=Sub124060QcAnalyzer.Status.FOUND;r.batons.add(b);
        }
        for(int h:Sub124060Layout.ROUND_HOURS){
            GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);m.found=true;m.x=500;m.y=500;m.radiusPx=30;m.seedX=500;m.seedY=500;m.expectedRadiusPx=30;
            Sub124060QcAnalyzer.Status s=h==8?Sub124060QcAnalyzer.Status.HAND:Sub124060QcAnalyzer.Status.FOUND;
            r.rounds.add(new Sub124060QcAnalyzer.Round(m,s,h==8?"a hand is over or next to it":""));
        }
        return r;
    }

    @Test public void subUsesMeasurementOnlySharedModel(){
        MeasuredOverlayRenderer.Drawing d=Sub124060PresentationAdapter.adapt(subResult());
        assertTrue(d.measurementOnly);
        assertTrue(d.genericBatonsOnly);
        assertTrue(d.twelveMeasured);
        assertTrue(d.gapMeasured);
        assertTrue(d.alignmentMeasured);
        assertEquals(3,d.batons.size());
        assertEquals(8,d.round.size());
        assertEquals(7,d.roundMeasuredHours.size());
        assertEquals(Sub124060PresentationAdapter.BANNER,d.bannerText);
    }

    @Test public void sharedPlannerShowsUnavailableMarkerBeforeReferenceTwelve(){
        MeasuredOverlayRenderer.Drawing d=Sub124060PresentationAdapter.adapt(subResult());
        assertEquals(Arrays.asList("r8"),MeasuredOverlayRenderer.closeUpPlan(d));
        d.roundMeasuredHours.add(8);d.roundNotJudged.remove(8);
        assertEquals(Arrays.asList("12"),MeasuredOverlayRenderer.closeUpPlan(d));
    }

    @Test public void legacyDrawingDefaultsKeepGmtMode(){
        MeasuredOverlayRenderer.Drawing d=new MeasuredOverlayRenderer.Drawing();
        assertFalse(d.measurementOnly);
        assertFalse(d.genericBatonsOnly);
    }
}
