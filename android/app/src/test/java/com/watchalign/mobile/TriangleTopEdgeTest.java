package com.watchalign.mobile;

import org.junit.Test;

import static org.junit.Assert.*;

/** EXPERIMENTAL TriangleTopEdge (research only) on a synthetic dial: a fixed-size 12 triangle (outer metal 180 around lume
 *  230, dial 15, 400 px radius). Known moves and turns are recovered; a sideways move does not change the radial reading;
 *  bevel highlights, a dark rim, a hand and a partly hidden edge do not move it; a second parallel edge is reported. */
public class TriangleTopEdgeTest {
    static final double RPX=MetalFitTest.RPX;
    /** the genuine triangle: top edge at 0.9025 R, half base 0.12375 R, apex 0.5925 R (12 at the top, y down) */
    static double[][] tri(){return new double[][]{{-0.12375,-0.9025},{0.12375,-0.9025},{0,-0.5925}};}
    static final double TRACK=0.9325;

    static MetalFitTest.Pic dial(double inout,double side,double turnDeg){
        double[][] g=MetalFitTest.moved(tri(),side,-inout,Math.toRadians(turnDeg));
        return MetalFitTest.dial(g);
    }
    static TriangleTopEdge.Result measure(MetalFitTest.Pic p){return TriangleTopEdge.measure(p.img(),tri(),0,TRACK,1.0,8,false,RPX);}

    @Test public void theGenuineTriangleReadsZero(){
        TriangleTopEdge.Result r=measure(dial(0,0,0));
        assertTrue(r.note,r.ok());assertFalse(r.ambiguous());
        assertEquals(0,r.radialPct,0.05);assertEquals(0,r.angleDeg,0.1);
        assertEquals(0.9025,r.genuineR,1e-9);assertTrue(r.inliers>=0.9*r.usable);
    }

    @Test public void knownRadialMovesAreRecoveredBothWays(){
        for(double d:new double[]{-1.5,-1.0,-0.5,0.5,1.0,1.5}){
            TriangleTopEdge.Result r=measure(dial(d/100,0,0));
            assertTrue(d+": "+r.note,r.ok());
            assertEquals("moved "+d+" % R",d,r.radialPct,0.05);
        }
    }

    @Test public void aSidewaysMoveDoesNotChangeTheRadialReading(){
        for(double s:new double[]{-0.8,0.8}){
            TriangleTopEdge.Result r=measure(dial(0,s/100,0));
            assertTrue(r.note,r.ok());assertEquals(0,r.radialPct,0.05);
        }
    }

    @Test public void aTurnIsMeasuredAndDoesNotMoveItRadially(){
        for(double t:new double[]{-1.5,1.5}){
            TriangleTopEdge.Result r=measure(dial(0,0,t));
            assertTrue(r.note,r.ok());
            // the triangle turns about its centre: its top edge's distance from the dial centre changes by cos only
            assertEquals(0,r.radialPct,0.05);
            assertEquals(t,r.angleDeg,0.15);   // the sign of the existing triangle rotation (checked on photos: TopEdge synth turns)
        }
    }

    @Test public void aBevelHighlightInsideTheEdgeDoesNotMoveIt(){
        // a bright polished line just inside the outer edge, across the whole top
        MetalFitTest.Pic p=dial(0,0,0).add(new double[][]{{-0.12,-0.9010},{0.12,-0.9010},{0.12,-0.8995},{-0.12,-0.8995}},255);
        TriangleTopEdge.Result r=measure(p);
        // a brighter rim moves the half-level crossing of a blurred edge by about 0.2 px (0.05 % R here)
        assertTrue(r.note,r.ok());assertEquals(0,r.radialPct,0.1);
    }

    @Test public void aShadowedRimIsNotMistakenForTheLumeBoundary(){
        // a shadowed outer rim (120) around bright lume (230): the edge is the rim's outer edge, not the lume boundary inside it
        double[][] g=tri();
        MetalFitTest.Pic p=new MetalFitTest.Pic().add(g,120).add(MetalFitTest.shrink(g,0.008),230);
        TriangleTopEdge.Result r=measure(p);
        assertTrue(r.note,r.ok());
        assertEquals("not 0.8 % inside, at the lume",0,r.radialPct,0.12);
    }

    /** KNOWN LIMITATION (shared with MetalFit's edge rule): on a blurred edge the lume, a dim rim and the dial make one
     *  ramp, the rim's level is over-read from the lume beside it, and the half-level crossing moves in. Measured here
     *  (rim 0.008 R wide, lume 230, dial 15, 400 px radius): rim 180 -> 0.01 % R in, 120 -> 0.10, 90 -> 0.23 (0.9 px);
     *  a rim under a quarter of the lume-to-dial contrast (60) is not seen as metal and the lume boundary is read. Kept
     *  as tests so a change to it is noticed. */
    @Test public void aDimRimReadsTheEdgeTooFarIn(){
        double[][] g=tri();
        TriangleTopEdge.Result r90=measure(new MetalFitTest.Pic().add(g,90).add(MetalFitTest.shrink(g,0.008),230));
        assertTrue(r90.ok());assertEquals(-0.23,r90.radialPct,0.05);
        TriangleTopEdge.Result r60=measure(new MetalFitTest.Pic().add(g,60).add(MetalFitTest.shrink(g,0.008),230));
        assertTrue(r60.ok());assertTrue("reads inside: "+r60.radialPct,r60.radialPct<-0.3);
    }

    @Test public void aHandAcrossPartOfTheEdgeIsLeftOut(){
        // a bright hand crossing the top edge (a quarter of its width), running out past the edge to the track
        MetalFitTest.Pic p=dial(0,0,0).add(new double[][]{{0.02,-0.80},{0.07,-0.80},{0.07,-0.935},{0.02,-0.935}},210);
        TriangleTopEdge.Result r=measure(p);
        assertTrue(r.note,r.ok());assertEquals(0,r.radialPct,0.05);
        assertTrue("the hand's profiles are left out",r.inliers<r.usable||r.usable<r.profiles);
        // and a true move is still recovered with the hand there
        MetalFitTest.Pic q=dial(0.01,0,0).add(new double[][]{{0.02,-0.80},{0.07,-0.80},{0.07,-0.935},{0.02,-0.935}},210);
        assertEquals(1.0,measure(q).radialPct,0.05);
    }

    @Test public void onlyPartOfTheTopEdgeVisibleIsEnoughButNotTooLittle(){
        // dark cover over 60 % of the top edge's searched width: 40 % left -> measured
        MetalFitTest.Pic p=dial(0.005,0,0).add(new double[][]{{-0.13,-0.95},{0.04,-0.95},{0.04,-0.86},{-0.13,-0.86}},15);
        TriangleTopEdge.Result r=measure(p);
        assertTrue(r.note,r.ok());assertEquals(0.5,r.radialPct,0.05);assertTrue(r.span<0.6);
        // 85 % covered -> not measured, and said why
        MetalFitTest.Pic q=dial(0,0,0).add(new double[][]{{-0.13,-0.95},{0.09,-0.95},{0.09,-0.86},{-0.13,-0.86}},15);
        TriangleTopEdge.Result s=measure(q);
        assertFalse(s.ok());assertFalse(s.note.isEmpty());
    }

    @Test public void aSecondParallelEdgeIsReportedNotHidden(){
        // a bright reflection strip 4 px outside the edge over 45 % of its width (dark gap between): two candidate edges
        double o=-0.9025-4.0/RPX;
        MetalFitTest.Pic p=dial(0,0,0).add(new double[][]{{-0.10,o},{0.0,o},{0.0,o-0.004},{-0.10,o-0.004}},200);
        TriangleTopEdge.Result r=measure(p);
        assertTrue("two edges: "+r.secondFrac,r.ambiguous());
        assertEquals("to the strip's outer side",5.6,Math.abs(r.secondOffsetPx),0.6);
    }

    @Test public void theSearchNeverUsesTheLimits(){
        // the same picture gives the same answer whatever limits are in force (the method takes none)
        TriangleTopEdge.Result a=measure(dial(0.012,0,0));
        ClearlyOff.USER.put("submariner_126610",ClearlyOff.Limits.genuine());
        try{assertEquals(a.radialPct,measure(dial(0.012,0,0)).radialPct,0);}finally{ClearlyOff.USER.clear();}
        assertEquals(1.2,a.radialPct,0.05);
    }

    @Test public void theRingScaleComesFromTheOtherRoundsAndPrefersReliableOnes(){
        java.util.List<double[]> rel=new java.util.ArrayList<>(),all=new java.util.ArrayList<>();
        for(double k:new double[]{1.001,0.999,1.002,0.998}){rel.add(new double[]{0.8136*k,0.8136});all.add(new double[]{0.8136*k,0.8136});}
        all.add(new double[]{0.8136*1.05,0.8136});   // an uncertain outlier is not used while enough reliable rounds exist
        double[] s=TriangleTopEdge.ringScale(rel,all,3);
        assertEquals(1.0,s[0],0.0015);assertEquals(4,(int)s[1]);assertEquals(0,(int)s[2]);
        s=TriangleTopEdge.ringScale(rel.subList(0,2),all,3);
        assertEquals(5,(int)s[1]);assertEquals(1,(int)s[2]);   // too few reliable: all, said so
        assertTrue(Double.isNaN(TriangleTopEdge.ringScale(rel.subList(0,1),all.subList(0,2),3)[0]));
    }

    /** Production integration of the owner-approved population calibration, without changing the legacy strict limit. */
    static ClearlyOff.Marker markerWithEdge(double rawPct){
        ClearlyOff.Marker m=new ClearlyOff.Marker(12,ModelSpec.Shape.TRIANGLE);
        m.inoutPct=-0.617; m.inoutPx=-2.468; // old erroneous whole-outline radial reading
        m.sidePct=0.10; m.tiltDeg=0.30; m.gapPct=3.4;
        TriangleTopEdge.Result t=new TriangleTopEdge.Result();
        t.radialPct=rawPct;
        m.topEdge=t;
        return m;
    }

    @Test public void calibratedReadingReplacesNotOrsTheOldRadialFailure(){
        ClearlyOff.Marker m=markerWithEdge(0.470);
        ClearlyOff.useCalibratedTopEdge(m,400);
        assertTrue(m.radialFromTopEdge);
        assertEquals(0.02985,m.inoutPct,1e-8);
        assertEquals(0.1194,m.inoutPx,1e-6);
        assertEquals(0.10,m.sidePct,0); assertEquals(0.30,m.tiltDeg,0);
        ClearlyOff.Limits genuine=ClearlyOff.Limits.genuine();
        ClearlyOff.decide(m,400,null,genuine);
        assertEquals(0.525,m.inoutLimitPct,1e-9); // 0.40% + existing 0.5px allowance
        assertFalse("the old -0.617 reading must not still trigger Replica",m.inoutOff);
        ClearlyOff.Marker copy=m.copy();
        assertTrue("classifier's strict copy must preserve source",copy.radialFromTopEdge);
        ClearlyOff.decide(copy,400,null,genuine);
        assertFalse(copy.inoutOff);
        assertEquals(m.inoutPct,copy.inoutPct,0);
        ClearlyOff.decide(m,400,null,ClearlyOff.Limits.defaults());
        assertEquals(1.375,m.inoutLimitPct,1e-9); // frozen replica-QC limit 1.25 + allowance
    }

    @Test public void realRadialDisplacementStillFailsFrozenStandards(){
        ClearlyOff.Marker m=markerWithEdge(1.44015); // actual top-edge is 1% R outward
        ClearlyOff.useCalibratedTopEdge(m,400);
        assertEquals(1.0,m.inoutPct,1e-8);
        ClearlyOff.decide(m,400,null,ClearlyOff.Limits.genuine());
        assertTrue("genuine threshold must detect a +1% shift",m.inoutOff);
        ClearlyOff.decide(m,400,null,ClearlyOff.Limits.defaults());
        assertFalse("replica QC still has its original 1.25% + pixel allowance",m.inoutOff);
        ClearlyOff.Marker qc=markerWithEdge(1.94015); // +1.5% shift
        ClearlyOff.useCalibratedTopEdge(qc,400);
        ClearlyOff.decide(qc,400,null,ClearlyOff.Limits.defaults());
        assertTrue("large radial defects still fail replica QC",qc.inoutOff);
    }

    @Test public void ambiguousMissingAndLowResolutionEdgesKeepLegacyFallback(){
        ClearlyOff.Marker bad=markerWithEdge(0.470);
        bad.topEdge.secondFrac=0.75;
        ClearlyOff.useCalibratedTopEdge(bad,400);
        assertFalse(bad.radialFromTopEdge);
        assertEquals(-0.617,bad.inoutPct,0);
        ClearlyOff.Marker low=markerWithEdge(0.470);
        ClearlyOff.useCalibratedTopEdge(low,170);
        assertFalse(low.radialFromTopEdge);
        assertEquals(-0.617,low.inoutPct,0);
        ClearlyOff.Marker missing=markerWithEdge(Double.NaN);
        ClearlyOff.useCalibratedTopEdge(missing,400);
        assertFalse(missing.radialFromTopEdge);
        assertEquals(-0.617,missing.inoutPct,0);
    }

    @Test public void strictGenuineAndReplicaLimitsRemainDistinct(){
        ClearlyOff.Limits genuine=ClearlyOff.Limits.genuine();
        assertTrue(genuine.copy().strictGenuine);
        assertEquals(0.00373,genuine.triInoutR,1e-12); // frozen legacy centre threshold unchanged
        assertEquals(0.0040,TriangleTopEdge.GENUINE_RADIAL_LIMIT_PCT/100,1e-12);
        assertEquals(0.44015,TriangleTopEdge.CALIBRATION_OFFSET_PCT,1e-12);
        assertFalse(ClearlyOff.Limits.defaults().strictGenuine);
    }
}
