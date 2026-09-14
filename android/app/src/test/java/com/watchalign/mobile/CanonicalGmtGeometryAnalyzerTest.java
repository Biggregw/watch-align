package com.watchalign.mobile;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class CanonicalGmtGeometryAnalyzerTest {
    @Test public void mirrorPairsFollowSymmetricGmtLayout(){
        assertEquals(11,CanonicalGmtGeometryAnalyzer.mirrorPair(1));
        assertEquals(1,CanonicalGmtGeometryAnalyzer.mirrorPair(11));
        assertEquals(10,CanonicalGmtGeometryAnalyzer.mirrorPair(2));
        assertEquals(8,CanonicalGmtGeometryAnalyzer.mirrorPair(4));
        assertEquals(6,CanonicalGmtGeometryAnalyzer.mirrorPair(6));
        assertEquals(12,CanonicalGmtGeometryAnalyzer.mirrorPair(12));
    }

    @Test public void exactAngularGridFlagsMeaningfulResiduals(){
        GenuineBaselineStats.Summary tight=GenuineBaselineStats.summarize(new double[]{-0.08,0.00,0.09,0.04,-0.03},5);
        assertEquals(0,CanonicalGmtGeometryAnalyzer.canonicalAngularSeverity(0.20,tight));
        assertEquals(1,CanonicalGmtGeometryAnalyzer.canonicalAngularSeverity(0.70,tight));
        assertEquals(2,CanonicalGmtGeometryAnalyzer.canonicalAngularSeverity(1.55,tight));
    }

    @Test public void radialGeometryUsesIndependentDialNormalisationTolerance(){
        GenuineBaselineStats.Summary refs=GenuineBaselineStats.summarize(new double[]{82.05,82.12,82.18,82.10,82.14},5);
        assertEquals(0,CanonicalGmtGeometryAnalyzer.canonicalRadialSeverity(82.35,refs));
        assertEquals(1,CanonicalGmtGeometryAnalyzer.canonicalRadialSeverity(82.85,refs));
        assertEquals(2,CanonicalGmtGeometryAnalyzer.canonicalRadialSeverity(83.70,refs));
    }

    @Test public void radialVerdictRequiresThreeGenuineSamples(){
        GenuineBaselineStats.Summary refs=GenuineBaselineStats.summarize(new double[]{82.0,82.1},2);
        assertEquals(0,CanonicalGmtGeometryAnalyzer.canonicalRadialSeverity(84.0,refs));
    }

    @Test public void perspectiveRectificationKeepsCorrectSixMarkerNormal(){
        double[] h={1.0,0.0,0.0,0.0,0.80,0.0,0.0,0.0,1.0};
        double[] image=CanonicalGmtGeometryAnalyzer.project(h,0.0,0.755);
        double[] corrected=CanonicalGmtGeometryAnalyzer.project(CanonicalGmtGeometryAnalyzer.invert3x3(h),image[0],image[1]);
        assertNotNull(corrected);
        GenuineBaselineStats.Summary refs=GenuineBaselineStats.summarize(new double[]{75.4,75.5,75.6,75.5,75.4},5);
        assertEquals(75.5,100.0*Math.hypot(corrected[0],corrected[1]),1e-6);
        assertEquals(0,CanonicalGmtGeometryAnalyzer.canonicalRadialSeverity(100.0*Math.hypot(corrected[0],corrected[1]),refs));
    }

    @Test public void visiblyHighTwelveMarkerIsFlaggedAgainstGenuineBaseline(){
        GenuineBaselineStats.Summary refs=GenuineBaselineStats.summarize(new double[]{74.6,74.7,74.8,74.7,74.9},5);
        assertEquals(2,CanonicalGmtGeometryAnalyzer.canonicalRadialSeverity(82.9,refs));
    }

    @Test public void repeatedPhotosAreCollapsedBeforeIndependentStatistics(){
        CanonicalGmtGeometryAnalyzer.Measure a=measure(0.02,0.10,74.8);
        CanonicalGmtGeometryAnalyzer.Measure b=measure(0.03,0.12,74.9);
        CanonicalGmtGeometryAnalyzer.Measure c=measure(0.01,0.08,74.7);
        CanonicalGmtGeometryAnalyzer.Measure d=measure(0.55,0.55,75.6);
        CanonicalGmtGeometryAnalyzer.Measure e=measure(-0.42,-0.48,73.9);
        List<CanonicalGmtGeometryAnalyzer.Measure> collapsed=CanonicalGmtGeometryAnalyzer.collapseDuplicateReferences(Arrays.asList(a,b,c,d,e));
        assertEquals(3,collapsed.size());
        assertTrue(CanonicalGmtGeometryAnalyzer.samePhysicalWatchGeometry(a,b));
        assertFalse(CanonicalGmtGeometryAnalyzer.samePhysicalWatchGeometry(a,d));
    }

    private static CanonicalGmtGeometryAnalyzer.Measure measure(double angularBias,double radialBias,double radial12){
        CanonicalGmtGeometryAnalyzer.Measure m=new CanonicalGmtGeometryAnalyzer.Measure(0.90,4.0);
        for(int h:new int[]{1,2,4,5,6,7,8,9,10,11,12}){
            m.present[h]=true;
            m.angularResidualDeg[h]=angularBias+0.03*h;
            m.radialPctDial[h]=h==12?radial12:75.2+radialBias+0.11*h;
        }
        return m;
    }
}
