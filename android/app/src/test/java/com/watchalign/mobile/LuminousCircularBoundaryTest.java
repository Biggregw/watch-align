package com.watchalign.mobile;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class LuminousCircularBoundaryTest {
    static byte[] scene(int n){
        byte[] p=new byte[n*n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            double r=Math.hypot(x-n/2,y-n/2);
            p[y*n+x]=(byte)(r<12?210:r<20?185:r<22?50:r<26?230:10);
        }
        return p;
    }
    /** 124060 c14f8b3abb49c038 / 8 mechanism: on the lit side the inner bevel is as bright as the lume
     * (a weak dip, no dark separator); the next trough is the bevel groove. That later trough must
     * never be substituted as the luminous boundary. */
    @Test public void litBevelMergedWithLumeNeverSubstitutesTheGrooveTrough(){
        int n=100;byte[] p=new byte[n*n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            double r=Math.hypot(x-50,y-50);boolean lit=y<50;
            p[y*n+x]=(byte)(r<20?220:r<22?(lit?195:50):r<25?215:r<27?50:r<30?230:10);
        }
        LuminousCircularBoundary.Evidence evidence=LuminousCircularBoundary.evidence(p,n,50);
        assertTrue(evidence.primary.length>0);
        for(double[] q:evidence.primary){
            double r=Math.hypot(q[0]-50,q[1]-50);
            assertTrue("groove trough supplied a selected sample at r="+r,r<22.5);
            assertTrue("lit-side ray supplied a sample",q[1]>=49);
        }
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(evidence.primary,evidence.alternatives,12,1);
        if(f.ideal!=null)assertEquals(21,f.radius,.8);
    }
    @Test public void interiorDropCannotBorrowRecoveryFromDistantMetal(){
        double[][] p=LuminousCircularBoundary.samples(scene(100),100,50);
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(p,12,1);
        assertNotNull(f.ideal);assertArrayEquals(new double[]{50,50},f.center,.5);
        assertEquals(20,f.radius,.8);
        for(double[] q:p)assertTrue("interior brightness feature supplied an arc",Math.hypot(q[0]-50,q[1]-50)>18);
    }
    @Test public void partialIndependentArcsDoNotNeedCompleteBoundary(){
        List<double[]> arcs=new ArrayList<>();
        for(double[] p:LuminousCircularBoundary.samples(scene(100),100,50)){
            double a=Math.atan2(p[1]-50,p[0]-50);if(a<0)a+=2*Math.PI;
            if(a<Math.PI*2/3||a>Math.PI*4/3)arcs.add(p);
        }
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(arcs.toArray(new double[0][]),12,1);
        assertNotNull(f.ideal);assertArrayEquals(new double[]{50,50},f.center,.5);assertEquals(20,f.radius,.8);
    }
    @Test public void competingConcentricBoundariesWithholdBothPercentages(){
        List<double[]> arcs=new ArrayList<>();
        for(double r:new double[]{20,24})for(int k=0;k<96;k++){double a=k*2*Math.PI/96;arcs.add(new double[]{r*Math.cos(a),r*Math.sin(a)});}
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(arcs.toArray(new double[0][]),12,1);
        assertNull(f.center);assertNull(f.ideal);assertTrue(f.reason.contains("competing")||f.reason.contains("contradict"));
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(12);d.alignment=f;
        ModelSpec.Marker spec=new ModelSpec.Marker(12,ModelSpec.Shape.ROUND,"rounds",.7,.06,0,0,0,0,0,0,0);
        LuminousMeasurement.Marker m=new LuminousMeasurement.Marker(spec,d,MarkerPolygonMismatch.circle(0,0,20,96));
        assertNull(m.fittedScore);assertNull(m.alignmentScore);assertNull(m.score);
    }
    @Test public void shortTrueArcDoesNotEstablishPositionOrRadius(){
        List<double[]> arcs=new ArrayList<>();for(int k=0;k<50;k++){double a=k*.01;arcs.add(new double[]{20*Math.cos(a),20*Math.sin(a)});}
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(arcs.toArray(new double[0][]),12,1);assertNull(f.ideal);assertNull(f.center);
    }
    @Test public void oneWayDialExitCannotSupplyBoundaryEvidence(){
        byte[] p=new byte[10000];for(int y=0;y<100;y++)for(int x=0;x<100;x++)if(Math.hypot(x-50,y-50)<20)p[y*100+x]=(byte)180;
        assertEquals(0,LuminousCircularBoundary.samples(p,100,50).length);
    }
    @Test public void imageEvidenceReplacesStableButIncorrectInteriorFit(){
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(12);d.circularEvidence=LuminousCircularBoundary.samples(scene(100),100,50);
        d.alignment=LuminousPartialFit.fit(MarkerPolygonMismatch.circle(50,50,12,96),ModelSpec.Shape.ROUND,12,1);
        assertNotNull(d.alignment.ideal);assertEquals(0,d.alignment.rmsPx,1e-8);
        LuminousCircularBoundary.validate(d,12,1);assertEquals(20,d.alignment.radius,.8);
        assertTrue(d.attempts.toString().contains("CORRECTED"));
    }
    @Test public void independentlyConfirmedGeometryRemainsExactlyUnchanged(){
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(12);d.circularEvidence=LuminousCircularBoundary.samples(scene(100),100,50);
        d.alignment=LuminousPartialFit.fit(d.circularEvidence,ModelSpec.Shape.ROUND,12,1);LuminousPartialFit.Fit old=d.alignment;
        LuminousCircularBoundary.validate(d,12,1);assertSame(old,d.alignment);
    }
    @Test public void ambiguousBoundaryCannotKeepAnObservedFullFootprint(){
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(12);d.circularEvidence=new double[0][];d.polygon=MarkerPolygonMismatch.circle(50,50,12,96);d.reason="measured";
        d.alignment=LuminousPartialFit.fit(d.polygon,ModelSpec.Shape.ROUND,12,1);LuminousCircularBoundary.validate(d,12,1);
        assertNull(d.alignment.ideal);assertNull(d.polygon);assertFalse(d.usable());
    }
    @Test public void contradictoryPhotographicArcsCannotBeHiddenAsRansacOutliers(){
        List<double[]> p=new ArrayList<>(Arrays.asList(MarkerPolygonMismatch.circle(50,50,20,96)));
        for(int k=0;k<72;k++){double a=k*2*Math.PI/96,r=24+(k%5);p.add(new double[]{50+r*Math.cos(a),50+r*Math.sin(a)});}
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(p.toArray(new double[0][]),12,1);
        assertNull(f.ideal);assertTrue(f.reason.contains("contradict"));
    }
    @Test public void localUnresolvedArcDoesNotRequireCompletePhysicalBoundary(){
        List<double[]> p=new ArrayList<>(Arrays.asList(MarkerPolygonMismatch.circle(50,50,20,96)));
        for(int k=0;k<16;k++){double a=k*2*Math.PI/96,r=24+(k%5);p.add(new double[]{50+r*Math.cos(a),50+r*Math.sin(a)});}
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(p.toArray(new double[0][]),12,1);
        assertNotNull(f.ideal);assertEquals(20,f.radius,1e-8);
    }
    @Test public void interiorDarkAnnulusCannotEraseCompetingOuterLuminousBoundary(){
        int n=100;byte[] p=new byte[n*n];
        for(int y=0;y<n;y++)for(int x=0;x<n;x++){
            double r=Math.hypot(x-50,y-50);
            p[y*n+x]=(byte)(r<12?210:r<14?50:r<24?185:r<26?50:r<30?230:10);
        }
        LuminousCircularBoundary.Evidence evidence=LuminousCircularBoundary.evidence(p,n,50);
        // The seed-connected selector alone confidently follows the interior reflection.
        LuminousPartialFit.Fit interior=LuminousCircularBoundary.fit(evidence.primary,12,1);
        assertNotNull(interior.ideal);assertTrue(interior.radius<14);
        boolean outer=false;for(double[] q:evidence.alternatives)if(Math.hypot(q[0]-50,q[1]-50)>22)outer=true;
        assertTrue("true outer trough was discarded by the bright-prefix gate",outer);
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(evidence.primary,evidence.alternatives,12,1);
        assertNull(f.ideal);assertNull(f.center);
        assertTrue(f.reason.contains("contradict")||f.reason.contains("competing"));
    }
    @Test public void alternativeArcsCanContradictButCannotSupplyTheSelectedFit(){
        double[][] shortArc=new double[8][2];for(int k=0;k<8;k++){double a=k*.02;shortArc[k]=new double[]{20*Math.cos(a),20*Math.sin(a)};}
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(shortArc,MarkerPolygonMismatch.circle(0,0,24,96),12,1);
        assertNull(f.ideal);assertNull(f.center);
    }
    @Test public void localAlternativeArcDoesNotDemandCompleteBoundary(){
        double[][] arc=new double[16][2];for(int k=0;k<16;k++){double a=k*2*Math.PI/96;arc[k]=new double[]{24*Math.cos(a),24*Math.sin(a)};}
        LuminousPartialFit.Fit f=LuminousCircularBoundary.fit(MarkerPolygonMismatch.circle(0,0,20,96),arc,12,1);
        assertNotNull(f.ideal);assertEquals(20,f.radius,1e-8);
    }
    @Test public void acceptedObservedCircleCannotBeDiscardedToTrustConflictingPhotographicCircle(){
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(12);
        d.circularEvidence=LuminousCircularBoundary.samples(scene(100),100,50);
        d.polygon=MarkerPolygonMismatch.circle(50,50,16,96);d.reason="measured";
        d.alignment=LuminousPartialFit.fit(d.polygon,ModelSpec.Shape.ROUND,12,1);
        LuminousCircularBoundary.validate(d,12,1);
        assertNull(d.alignment.ideal);assertNull(d.alignment.center);assertNull(d.polygon);
        assertTrue(d.alignment.reason.contains("accepted observed boundary"));
    }
    @Test public void localContourIrregularityDoesNotEraseSupportedPartialCircle(){
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(12);
        d.circularEvidence=LuminousCircularBoundary.samples(scene(100),100,50);
        d.polygon=MarkerPolygonMismatch.circle(50,50,20,96);d.polygon[0][0]+=6;d.reason="measured";
        d.alignment=LuminousPartialFit.fit(d.circularEvidence,ModelSpec.Shape.ROUND,12,1);
        LuminousCircularBoundary.validate(d,12,1);
        assertNotNull(d.alignment.ideal);assertEquals(20,d.alignment.radius,.8);
        assertNull("unresolved observed protrusion cannot supply full-footprint area",d.polygon);
    }
    @Test public void handAlternativesAreMaskedBeforeTheyCanEraseSupportedContourSamples(){
        int hour=12;double R=100;ModelSpec.Marker m=new ModelSpec.Marker(hour,ModelSpec.Shape.ROUND,"rounds",.75,.09,0,0,0,0,0,0,0);
        double[][] contour=MarkerPolygonMismatch.circle(0,-.75,.07,96);
        LuminousMarkerDetector.Detection d=new LuminousMarkerDetector.Detection(hour);d.polygon=contour;d.reason="measured";
        d.alignment=LuminousPartialFit.fit(contour,ModelSpec.Shape.ROUND,hour,R);
        d.circularEvidence=MarkerPolygonMismatch.circle(.003,-.75,.075,96);
        d.circularAlternatives=MarkerPolygonMismatch.circle(0,-.75,.18,96);
        assertNull("unmasked hand evidence contradicts the luminous circle",LuminousCircularBoundary.fit(d.circularEvidence,d.circularAlternatives,hour,R).ideal);
        Alpha99MarkerInterference.Check check=new Alpha99MarkerInterference.Check(hour);check.clean=false;check.reason=Alpha99MarkerInterference.HAND;
        check.rows=150;check.cols=130;check.grid=new int[check.rows*check.cols];double step=.75/R,r0=m.innerR()-Alpha99MarkerInterference.CORRIDOR;
        for(int row=0;row<check.rows;row++)for(int col=0;col<check.cols;col++){
            double x=(col-(check.cols-1)/2.0)*step,y=-(r0+row*step);
            if(Math.hypot(x,y+.75)>.12)check.grid[row*check.cols+col]=5;
        }
        LuminousMarkerDetector.excludeHand(d,m,check,new double[]{R,0,200,0,R,200,0,0,1},R);
        assertEquals(0,d.circularAlternatives.length);assertEquals(96,d.circularEvidence.length);
        assertNotNull(d.alignment.ideal);assertEquals("confirmed contour geometry must remain unchanged",.07,d.alignment.radius,1e-12);
        assertEquals("provisional contour samples were replaced before hand masking",96,d.alignment.observed.length);
        assertNull(d.polygon);
    }
}
