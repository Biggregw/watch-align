package com.watchalign.mobile;

import org.junit.Test;

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
}
