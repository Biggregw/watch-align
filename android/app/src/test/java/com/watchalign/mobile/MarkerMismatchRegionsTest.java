package com.watchalign.mobile;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class MarkerMismatchRegionsTest {
    private static double[][] box(double left,double top,double right,double bottom){
        return new double[][]{{left,top},{right,top},{right,bottom},{left,bottom}};
    }
    private static double area(List<double[][]> regions){
        double result=0;for(double[][] p:regions)result+=MarkerPolygonMismatch.area(p);return result;
    }
    private static void consistent(MarkerPolygonMismatch.Score q){
        assertNotNull(q);
        assertEquals(q.extraArea,area(q.extraRegions()),1e-10);
        assertEquals(q.missingArea,area(q.missingRegions()),1e-10);
        assertEquals(q.mismatchPct,100*(area(q.extraRegions())+area(q.missingRegions()))/q.expectedArea,1e-7);
        for(double[][] extra:q.extraRegions()){
            assertEquals(MarkerPolygonMismatch.area(extra),MarkerPolygonMismatch.compare(extra,q.actualPolygon).overlapArea,1e-10);
            assertEquals(0,MarkerPolygonMismatch.compare(extra,q.expectedPolygon).overlapArea,1e-10);
        }
        for(double[][] missing:q.missingRegions()){
            assertEquals(MarkerPolygonMismatch.area(missing),MarkerPolygonMismatch.compare(missing,q.expectedPolygon).overlapArea,1e-10);
            assertEquals(0,MarkerPolygonMismatch.compare(missing,q.actualPolygon).overlapArea,1e-10);
        }
    }
    @Test public void identicalPolygonsHaveNoColouredRegions(){
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(box(0,0,2,1),box(0,0,2,1));
        consistent(q);assertTrue(q.extraRegions().isEmpty());assertTrue(q.missingRegions().isEmpty());
    }
    @Test public void translationSeparatesExtraAndMissingWithoutOverlap(){
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(box(0,0,1,1),box(.3,.2,1.3,1.2));
        consistent(q);
        for(List<double[][]> pieces:java.util.Arrays.asList(q.extraRegions(),q.missingRegions()))
            for(int i=0;i<pieces.size();i++)for(int j=i+1;j<pieces.size();j++)
                assertEquals(0,MarkerPolygonMismatch.compare(pieces.get(i),pieces.get(j)).overlapArea,1e-10);
    }
    @Test public void containmentAndSeparationPreserveEntireFilledRegions(){
        MarkerPolygonMismatch.Score small=MarkerPolygonMismatch.compare(box(0,0,2,2),box(.5,.5,1.5,1.5));
        consistent(small);assertTrue(small.extraRegions().isEmpty());assertEquals(3,area(small.missingRegions()),1e-10);
        consistent(MarkerPolygonMismatch.compare(box(.5,.5,1.5,1.5),box(0,0,2,2)));
        MarkerPolygonMismatch.Score separate=MarkerPolygonMismatch.compare(box(0,0,1,1),box(2,0,3,1));
        consistent(separate);assertEquals(1,area(separate.extraRegions()),1e-10);assertEquals(1,area(separate.missingRegions()),1e-10);
    }
    @Test public void oppositeWindingAndRoundPolygonsHaveMatchingRegionAreas(){
        double[][] e=box(0,0,1,1),a={{1.2,1.1},{1.2,.1},{.2,.1},{.2,1.1}};
        consistent(MarkerPolygonMismatch.compare(e,a));
        consistent(MarkerPolygonMismatch.compare(MarkerPolygonMismatch.circle(.3,.6,.08,128),MarkerPolygonMismatch.circle(.302,.599,.081,128)));
    }
    @Test public void smallCornerDifferencesRetainTheirUnpaddedArea(){
        double[][] expected=box(0,0,1,1),actual={{0,0},{1.00001,0},{1,1},{.00002,1}};
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(expected,actual);
        consistent(q);
        assertEquals(.000005,q.extraArea,1e-12);assertEquals(.00001,q.missingArea,1e-12);
        assertEquals(.0015,q.mismatchPct,1e-9);
    }
    @Test public void genuineFixturesAndPerturbationsMatchTheirColouredAreaSums()throws Exception{
        int count=0;
        for(String name:new String[]{"pepsi_alpha106.csv","wos_alpha106.csv"})
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/marker_mismatch/"+name),StandardCharsets.UTF_8))){
                reader.readLine();String line;
                while((line=reader.readLine())!=null){String[] row=line.split(",",-1);if(row[22].isEmpty())continue;
                    consistent(MarkerPolygonMismatch.compare(points(row[22]),points(row[23])));count++;
                }
            }
        assertEquals(12,count);
    }
    private static double[][] points(String text){
        String[] vertices=text.split(";");double[][] result=new double[vertices.length][2];
        for(int i=0;i<result.length;i++){String[] xy=vertices[i].split(":");result[i][0]=Double.parseDouble(xy[0]);result[i][1]=Double.parseDouble(xy[1]);}
        return result;
    }
    @Test public void scoringSnapshotsSurviveLaterDetectorArrayChanges(){
        double[][] expected=box(0,0,1,1),actual=box(.3,0,1.3,1);
        MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(expected,actual);
        MarkerMismatchResearch.Result result=new MarkerMismatchResearch.Result(q,"measured");
        expected[0][0]=-99;actual[0][0]=99;
        assertSame(q.expectedPolygon,result.expected);assertEquals(0,q.expectedPolygon[0][0],0);assertEquals(.3,q.actualPolygon[0][0],0);
        consistent(q);
    }
    @Test public void fullWatchProjectionUsesFrozenPerspectiveWithoutRingReadjustment(){
        double[][] polygon=box(0,0,1,1);double[] H={100,12,10,3,90,20,.1,.2,1};double[] frozen=H.clone();
        double[][] image=MarkerMismatchOverlay.project(polygon,H);
        assertArrayEquals(new double[]{10,20},image[0],1e-12);
        assertArrayEquals(new double[]{122/1.3,113/1.3},image[2],1e-12);
        assertArrayEquals(frozen,H,0);assertEquals(1,polygon[2][0],0);
        assertNull(MarkerMismatchOverlay.project(polygon,new double[9]));
    }
    @Test public void closeupProjectionMatchesTheWarpHalfPixelOrigin(){
        double cx=-.755,cy=.002,half=.22;
        double[] T=MarkerMismatchOverlay.closeupTransform(480,cx,cy,half);
        double[][] points={{cx-half,cy-half},{cx,cy},{cx+half,cy+half}};
        double[][] crop=MarkerMismatchOverlay.project(points,T);
        assertArrayEquals(new double[]{-.5,-.5},crop[0],1e-10);
        assertArrayEquals(new double[]{239.5,239.5},crop[1],1e-10);
        assertArrayEquals(new double[]{479.5,479.5},crop[2],1e-10);
        for(int i=0;i<points.length;i++)assertEquals(points[i][0],cx-half+(crop[i][0]+.5)*2*half/480,1e-12);
    }
    @Test public void missingRingCannotSilentlyProvideAFixedMasterScoringOverlay(){
        MarkerMismatchResearch.Result unavailable=new MarkerMismatchResearch.Result(null,"marker ring unavailable");
        assertNull(MarkerMismatchOverlay.fullWatch(480,480,new double[9],Collections.singletonMap(9,unavailable)));
    }
}
