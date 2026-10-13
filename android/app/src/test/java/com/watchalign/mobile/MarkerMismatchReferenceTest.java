package com.watchalign.mobile;

import org.junit.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Frozen numeric evidence from run 38155286452, not a new manufacturing tolerance. */
public class MarkerMismatchReferenceTest {
    private static final double[] PEPSI_RING={-.0058830752281987565,-.0018965800423068565,-.0015888133196588744,.0006516182281748061};
    private static List<String[]> rows()throws Exception{
        return rows("pepsi_alpha106.csv");
    }
    private static List<String[]> rows(String name)throws Exception{
        List<String[]> result=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(MarkerMismatchReferenceTest.class.getResourceAsStream("/marker_mismatch/"+name),StandardCharsets.UTF_8))){
            r.readLine();String line;while((line=r.readLine())!=null)result.add(line.split(",",-1));
        }
        return result;
    }
    private static double[][] vertices(String text){
        String[] pairs=text.split(";");double[][] p=new double[pairs.length][2];
        for(int i=0;i<pairs.length;i++){String[] xy=pairs[i].split(":");p[i][0]=Double.parseDouble(xy[0]);p[i][1]=Double.parseDouble(xy[1]);}
        return p;
    }
    private static Alpha94MarkerMeasurement.Marker pepsiNine()throws Exception{
        Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(9,"baton");m.usable=true;
        for(String[] row:rows())if(row[0].endsWith("__var__original")&&row[4].equals("9"))m.fitVertices=vertices(row[23]);
        assertNotNull(m.fitVertices);return m;
    }
    private static Alpha94MarkerMeasurement.Report report(Alpha94MarkerMeasurement.Marker m){
        Alpha94MarkerMeasurement.Ring ring=new Alpha94MarkerMeasurement.Ring();ring.usable=true;ring.model=PEPSI_RING.clone();
        return new Alpha94MarkerMeasurement.Report(Arrays.asList(m),ring,379.22856552754223,"triangle".equals(m.kind)?m:null);
    }
    private static Alpha99Findings.Finding finding(int hour){
        Alpha99Findings.Finding f=new Alpha99Findings.Finding("marker"+hour,"marker","marker");f.hour=hour;return f;
    }
    private static Alpha99MarkerInterference.Check clean(int hour){
        Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(hour);c.clean=true;return c;
    }
    /** Inferred corners are never presented as observed: only each fitted side's sampled middle is an observed span. */
    @Test public void sampledEdgeSpansStayOnTheirOwnFittedSidesAndExcludeCorners()throws Exception{
        ModelSpec.Marker spec=TestModels.gmt().atHour(9);Alpha94MarkerMeasurement.Marker m=pepsiNine();
        double[][] V=m.fitVertices;double[][][] spans=MarkerMismatchResearch.observedSpans(spec,V);
        assertEquals(4,spans.length);
        for(int i=0;i<4;i++){
            double[] a=V[i],b=V[(i+1)%4];double L=Math.hypot(b[0]-a[0],b[1]-a[1]);
            for(double[] p:spans[i]){
                double cross=((b[0]-a[0])*(p[1]-a[1])-(b[1]-a[1])*(p[0]-a[0]))/L;
                assertEquals("span leaves its own fitted side",0,cross,1e-12);
                double u=((p[0]-a[0])*(b[0]-a[0])+(p[1]-a[1])*(b[1]-a[1]))/(L*L);
                assertTrue("span reaches an inferred corner: u="+u,u>0.05&&u<0.95);
            }
        }
        assertNull(MarkerMismatchResearch.observedSpans(TestModels.gmt().atHour(4),MarkerPolygonMismatch.circle(0,0,.1,128)));
    }
    @Test public void pepsiNineUsesTheDisplayedRingAdjustedNominal()throws Exception{
        ModelSpec.Marker spec=TestModels.gmt().atHour(9);
        Alpha94MarkerMeasurement.Marker m=pepsiNine();
        double[][] expected=ExpectedMarkerOutline.polygon(spec,TestModels.gmtRef(),PEPSI_RING);
        for(String[] row:rows())if(row[0].endsWith("__var__original")&&row[4].equals("9")){
            double[][] frozen=vertices(row[22]);for(int i=0;i<4;i++)assertArrayEquals(frozen[i],expected[i],1e-8);
        }
        Alpha99Findings.Finding f=finding(9);
        MarkerMismatchResearch.Result q=MarkerMismatchResearch.score(report(m),spec,TestModels.gmtRef(),f,clean(9));
        assertTrue(q.trusted());assertEquals(.54416046,q.score.extraPct,.00002);
        assertEquals(1.87711801,q.score.missingPct,.00002);assertEquals(2.42127847,q.score.mismatchPct,.00002);
        assertEquals(8.62716457,MarkerPolygonMismatch.compare(spec.batonPolygon(),m.fitVertices).mismatchPct,.00002);
        assertEquals(Alpha99Findings.Status.WITHIN,f.status);
    }
    @Test public void samePhotoPerturbationsReproduceTheResearchSpread()throws Exception{
        List<Double> scores=new ArrayList<>();
        for(String[] row:rows())if(row[4].equals("9")&&row[11].equals("measured")){
            MarkerPolygonMismatch.Score s=MarkerPolygonMismatch.compare(vertices(row[22]),vertices(row[23]));
            double e=Double.parseDouble(row[15]),a=Double.parseDouble(row[16]),i=Double.parseDouble(row[17]);
            assertEquals(100*(e+a-2*i)/e,s.mismatchPct,.00005);scores.add(s.mismatchPct);
        }
        assertEquals(7,scores.size());
        assertEquals(2.240,java.util.Collections.min(scores),.001);
        assertEquals(3.036,java.util.Collections.max(scores),.001);
    }
    @Test public void missingChecksInterferenceAndPartialEdgesWithholdTheNumber()throws Exception{
        Alpha94MarkerMeasurement.Marker m=pepsiNine();Alpha94MarkerMeasurement.Report r=report(m);
        ModelSpec.Marker spec=TestModels.gmt().atHour(9);Alpha99Findings.Finding f=finding(9);
        assertFalse(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,null).trusted());
        assertFalse(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),null,clean(9)).trusted());
        Alpha99MarkerInterference.Check bad=clean(9);bad.clean=false;
        assertFalse(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,bad).trusted());
        f.partNotAssessed="position";
        MarkerMismatchResearch.Result q=MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,clean(9));
        assertFalse(q.trusted());assertFalse(q.display().contains("%"));
        f.partNotAssessed=null;f.status=Alpha99Findings.Status.NOT_ASSESSED;
        assertFalse(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,clean(9)).trusted());
        f.status=Alpha99Findings.Status.WITHIN;
        Alpha99Findings.Measure measure=new Alpha99Findings.Measure("position",0,1,1,3,10,"R","","");measure.cap("glare");f.measures.add(measure);
        assertFalse(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,clean(9)).trusted());
        m.usable=false;assertNull(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,clean(9)).score);
        m.usable=true;r.ring.usable=false;assertNull(MarkerMismatchResearch.score(r,spec,TestModels.gmtRef(),f,clean(9)).score);
    }
    @Test public void genuineTriangleKeepsItsNominalAndItsAreaNumberIsWithheld()throws Exception{
        ModelSpec.Marker spec=TestModels.gmt().atHour(12);Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(12,"triangle");m.usable=true;
        for(String[] row:rows())if(row[4].equals("12"))m.fitVertices=vertices(row[23]);
        MarkerMismatchResearch.Result q=MarkerMismatchResearch.score(report(m),spec,TestModels.gmtRef(),finding(12),clean(12));
        assertEquals(14.90874048,q.score.mismatchPct,.00003);assertFalse(q.trusted());assertFalse(q.display().contains("%"));
    }
    @Test public void independentWosGenuinePolygonsRetainTheirRecordedOverlap()throws Exception{
        for(String[] row:rows("wos_alpha106.csv"))if(!row[22].isEmpty()){
            MarkerPolygonMismatch.Score q=MarkerPolygonMismatch.compare(vertices(row[22]),vertices(row[23]));
            assertEquals(Double.parseDouble(row[15]),q.expectedArea,1e-8);
            assertEquals(Double.parseDouble(row[16]),q.actualArea,1e-8);
            assertEquals(Double.parseDouble(row[17]),q.overlapArea,1e-8);
            double expected=row[4].equals("6")?2.36667317:row[4].equals("9")?2.26532726:10.95796733;
            assertEquals(expected,q.mismatchPct,.00004);
        }
    }
    @Test public void roundPolygonUsesTheNominalMetalRadiusAndSharedActualFit(){
        ModelSpec.Marker spec=TestModels.gmt().atHour(4);double R=379.22856552754223;
        Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(4,"round");m.usable=true;
        m.canonDx=PEPSI_RING[0]+PEPSI_RING[2]*spec.masterPoint()[0]-PEPSI_RING[3]*spec.masterPoint()[1];
        m.canonDy=PEPSI_RING[1]+PEPSI_RING[2]*spec.masterPoint()[1]+PEPSI_RING[3]*spec.masterPoint()[0];
        m.radiusErrPx=spec.outerR*PEPSI_RING[2]*R;
        MarkerMismatchResearch.Result q=MarkerMismatchResearch.score(report(m),spec,TestModels.gmtRef(),finding(4),clean(4));
        assertTrue(q.trusted());assertEquals(0,q.score.mismatchPct,1e-7);
        m.radiusErrPx=Double.NaN;assertNull(MarkerMismatchResearch.actual(spec,m,R));
    }
}
