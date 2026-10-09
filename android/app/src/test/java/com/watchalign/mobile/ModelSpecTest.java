package com.watchalign.mobile;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Model specs: the GMT spec reproduces the frozen Alpha92 master / pose / date-window constants exactly (so moving them
 * to data changed nothing), the app's reference copies are byte-identical to the research outputs, the JSON reader is
 * exact and strict, and a second (test-only) model with a different layout runs through the evidence layer without any
 * code change, reporting every feature as not assessed until it has a genuine reference.
 */
public class ModelSpecTest {
    private static final double[] LEGACY_HOURS={1,2,4,5,6,7,8,9,10,11};

    @Test public void gmtSpecReproducesTheFrozenConstants(){
        ModelSpec m=TestModels.gmt();
        assertEquals("gmt_126710",m.id);
        assertEquals(Alpha92GmtMaster.MINUTE_TRACK_R,m.minuteTrackInnerR,0);
        assertEquals(Alpha92GmtMaster.MINUTE_TRACK_OUTER_R,m.minuteTrackOuterR,0);
        assertArrayEquals(new int[]{11,12,13,14,15,16,17,18,19,29,30,31},m.excludedMinutes);
        int[] ring=m.ringHours();assertEquals(LEGACY_HOURS.length,ring.length);
        for(int i=0;i<ring.length;i++)assertEquals(LEGACY_HOURS[i],ring[i],0);
        assertArrayEquals(new int[]{1,2,4,5,7,8,10,11},m.roundHours());
        for(ModelSpec.Marker k:m.markers){
            double a=Math.toRadians(k.hour*30.0);double[] p=k.masterPoint();
            switch(k.shape){
                case ROUND:
                    assertEquals(Alpha92GmtMaster.ROUND_CENTER_R,k.centreR,0);assertEquals(Alpha92GmtMaster.ROUND_OUTER_R,k.outerR,0);
                    assertEquals(Math.sin(a)*Alpha92GmtMaster.ROUND_CENTER_R,p[0],0);assertEquals(-Math.cos(a)*Alpha92GmtMaster.ROUND_CENTER_R,p[1],0);
                    break;
                case BATON:
                    assertEquals(Alpha92GmtMaster.BATON_CENTER_R,k.centreR,0);assertEquals(Alpha92GmtMaster.BATON_RADIAL_HALF,k.radialHalf,0);
                    assertEquals(Alpha92GmtMaster.BATON_TANGENTIAL_HALF,k.tangentialHalf,0);
                    assertEquals(Math.sin(a)*Alpha92GmtMaster.BATON_CENTER_R,p[0],0);assertEquals(-Math.cos(a)*Alpha92GmtMaster.BATON_CENTER_R,p[1],0);
                    break;
                default:
                    assertEquals(12,k.hour);assertEquals("twelve",k.key);
                    assertArrayEquals(new double[]{0,-Alpha92GmtMaster.TRI_AREA_CENTROID_R},p,0);
                    double[][] t=k.trianglePolygon();
                    assertArrayEquals(new double[]{0,-Alpha92GmtMaster.TRI_APEX_R},t[0],0);
                    assertArrayEquals(new double[]{Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R},t[1],0);
                    assertArrayEquals(new double[]{-Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R},t[2],0);
                    assertEquals(0.50,k.corridorStartR,0);
            }
        }
        assertEquals("six",m.atHour(6).key);assertEquals("nine",m.atHour(9).key);assertEquals("rounds",m.atHour(2).key);
        ModelSpec.DateWindow d=m.date;
        assertEquals(3,d.hour);
        assertArrayEquals(new double[]{0.22,1.02,-0.38,0.38,0.0025,0.64,0.0,0.40,0.20},new double[]{d.x0,d.x1,d.y0,d.y1,d.step,d.expX,d.expY,d.exclXMin,d.exclHalfY},0);
        assertEquals(0.50,m.secondsDotRMin,0);assertEquals(0.60,m.secondsDotRMax,0);
    }

    /** export_model_reference.py copies the research outputs byte for byte; the app must never drift from them. */
    @Test public void referenceAssetsAreTheResearchFiles()throws Exception{
        String[][] map={{"alpha98_reference.csv",ModelReference.GENUINE},{"alpha98_nominal.properties",ModelReference.NOMINAL},
                {"m12_nominal.properties",ModelReference.TRI_NOMINAL},{"m12_genuine_reference.csv",ModelReference.TRI_REFERENCE},
                {"alpha99_uncertainty.properties",ModelReference.UNCERTAINTY}};
        File research=researchDir(),ref=new File(TestModels.assets(),"models/gmt_126710/reference");
        for(String[] p:map)assertArrayEquals(p[0]+" -> "+p[1]+" (re-run export_model_reference.py --model gmt_126710)",
                Files.readAllBytes(new File(research,p[0]).toPath()),Files.readAllBytes(new File(ref,p[1]).toPath()));
        ModelReference r=TestModels.gmtRef();
        assertEquals(45,r.triangle.nWatches);assertEquals(3.0,r.kSigma,0);
        for(String f:new String[]{"six_rot","six_off","nine_rot","nine_off","rounds_off","ring_rot","ring_shift","date_tilt"})assertTrue(f,r.has(f));
    }

    @Test public void submariner124060ReferenceIsTheResearchFilesAndRoundSizeIsDowngraded()throws Exception{
        String[][] map={{"alpha98_reference.csv",ModelReference.GENUINE},{"alpha98_nominal.properties",ModelReference.NOMINAL},
                {"m12_nominal.properties",ModelReference.TRI_NOMINAL},{"m12_genuine_reference.csv",ModelReference.TRI_REFERENCE},
                {"alpha99_uncertainty.properties",ModelReference.UNCERTAINTY}};
        File research=new File(researchDir(),"../sub124060/reference_src"),ref=new File(TestModels.assets(),"models/submariner_124060/reference");
        for(String[] p:map)assertArrayEquals(p[0]+" -> "+p[1]+" (re-run export_model_reference.py --model submariner_124060 --source-dir tools/research/sub124060/reference_src)",
                Files.readAllBytes(new File(research,p[0]).toPath()),Files.readAllBytes(new File(ref,p[1]).toPath()));
        ModelSpec m=ModelSpec.load(ModelSpec.directory(TestModels.assets()),"submariner_124060");
        assertNull(m.date);
        assertEquals(12,m.markers.size());assertEquals("three",m.atHour(3).key);assertEquals(0.7591,m.atHour(6).centreR,0);
        ModelReference r=ModelReference.load(ModelSpec.directory(TestModels.assets()),m);
        assertEquals(95,r.triangle.nWatches);   // 24 catalogue + 31 EWC + 37 Bob's packs + 6 from the harvester run (one EWC listing trio merged, one 12 edge-filtered)
        assertEquals(3.0,r.kSigma,0);
        for(String f:new String[]{"three_rot","three_off","six_rot","six_off","nine_rot","nine_off","rounds_off","ring_rot","ring_shift","rounds_size","round_size_rel"})assertTrue(f,r.has(f));
        assertFalse(r.has("date_tilt"));
        // QC guardrails 11: held-out genuine conflict on round-plot size -> no allowance, so at most worth a look
        assertTrue(Double.isNaN(r.sigma("rounds_size","R")));assertTrue(Double.isNaN(r.sigma("round_size_rel","R")));
        assertTrue(r.sigma("six_rot","deg")>0);
    }

    @Test public void submarinerDate126610IsThe124060DialWithADateWindowAndItsOwnGenuineReference()throws Exception{
        String[][] map={{"alpha98_reference.csv",ModelReference.GENUINE},{"alpha98_nominal.properties",ModelReference.NOMINAL},
                {"m12_nominal.properties",ModelReference.TRI_NOMINAL},{"m12_genuine_reference.csv",ModelReference.TRI_REFERENCE},
                {"alpha99_uncertainty.properties",ModelReference.UNCERTAINTY}};
        File research=new File(researchDir(),"../sub126610/reference_src"),ref=new File(TestModels.assets(),"models/submariner_126610/reference");
        for(String[] p:map)assertArrayEquals(p[0]+" -> "+p[1]+" (re-run export_model_reference.py --model submariner_126610 --source-dir tools/research/sub126610/reference_src)",
                Files.readAllBytes(new File(research,p[0]).toPath()),Files.readAllBytes(new File(ref,p[1]).toPath()));
        ModelSpec m=ModelSpec.load(ModelSpec.directory(TestModels.assets()),"submariner_126610");
        ModelSpec sub=ModelSpec.load(ModelSpec.directory(TestModels.assets()),"submariner_124060");
        assertNotNull(m.date);assertNull(m.atHour(3));assertEquals(11,m.markers.size());
        for(ModelSpec.Marker x:m.markers){ModelSpec.Marker y=sub.atHour(x.hour);       // the 124060 dial master, unchanged
            assertEquals(x.shape,y.shape);assertEquals(y.centreR,x.centreR,0);assertEquals(y.outerR,x.outerR,0);}
        ModelReference r=ModelReference.load(ModelSpec.directory(TestModels.assets()),m);
        assertTrue(r.triangle.nWatches>=100);
        for(String f:new String[]{"six_rot","six_off","nine_rot","nine_off","rounds_off","ring_rot","ring_shift"})assertTrue(f,r.has(f));
        assertFalse(r.has("three_rot"));
        assertFalse(r.has("date_tilt"));                                         // date window: no genuine reference yet -> not assessed
        assertTrue(Double.isNaN(r.sigma("rounds_size","R")));assertTrue(Double.isNaN(r.sigma("round_size_rel","R")));
        // no repeat photos in the 126610 catalogue: the 124060 photo-to-photo allowances are used
        ModelReference rs=ModelReference.load(ModelSpec.directory(TestModels.assets()),sub);
        assertEquals(rs.sigma("six_rot","deg"),r.sigma("six_rot","deg"),0);
    }

    @Test public void submarinerBatonPositionIsResolutionMatched()throws Exception{
        ModelSpec m=ModelSpec.load(ModelSpec.directory(TestModels.assets()),"submariner_124060");
        ModelReference ref=ModelReference.load(ModelSpec.directory(TestModels.assets()),m);
        assertTrue(m.resolutionMatched.contains("three_off"));assertTrue(TestModels.gmt().resolutionMatched.isEmpty());
        for(double R:new double[]{131,400}){
            Alpha94MarkerMeasurement.Marker b=new Alpha94MarkerMeasurement.Marker(3,"baton");b.spec=m.atHour(3);b.usable=true;
            b.rotationDeg=ref.nominal("three_rot");b.localOffsetPx=0.44;b.localRadialPx=0.35;b.localTangentialPx=0.27;
            List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();ms.add(b);
            Alpha94MarkerMeasurement.Report r=new Alpha94MarkerMeasurement.Report(ms,null,R,null);
            Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(3);c.clean=true;
            Alpha99Findings.Finding f=Alpha99Findings.baton(r,m.atHour(3),R,c,ref,true);
            if(R<150){   // the render case: below the genuine photos' resolution the position is not assessed, rotation is
                assertEquals("position",f.partNotAssessed);assertEquals(1,f.measures.size());
                assertEquals(Alpha99Findings.Status.WITHIN,f.status);
                assertTrue(String.join(" ",f.detail()).contains("position is not assessed"));
            }else{assertNull(f.partNotAssessed);assertEquals(2,f.measures.size());assertTrue(ref.matchedWatches("three_off",R)>=8);}
        }
        // GMT: low-resolution rows (Alpha102) count once per watch and never touch a photo the full-resolution
        // reference covers: there the limit is exactly the full-resolution rows' limit
        ModelReference g=TestModels.gmtRef();
        List<String> lines=Files.readAllLines(new File(TestModels.assets(),"models/gmt_126710/reference/genuine_reference.csv").toPath());
        List<String> h=java.util.Arrays.asList(lines.get(0).trim().split(",",-1));
        java.util.Set<String> full=new java.util.HashSet<>();double fullMax=0;int low=0;
        for(String l:lines.subList(1,lines.size())){String[] f=l.trim().split(",",-1);if(!f[0].equals("rounds_off"))continue;
            if(f.length>h.indexOf("max_photo_r")&&!f[h.indexOf("max_photo_r")].isEmpty()){low++;continue;}
            full.add(f[1]);if(Double.parseDouble(f[4])<=1.3*400)fullMax=Math.max(fullMax,Double.parseDouble(f[3]));}
        assertTrue("low-resolution rows: "+low,low>0);
        assertEquals(full.size(),g.matchedWatches("rounds_off",1e9));
        assertEquals(fullMax,g.matchedMax("rounds_off",400),0);
        assertTrue(g.matchedWatches("rounds_off",140)>=8);                       // small photos now assessable
    }

    @Test public void jsonReaderIsExactAndStrict(){
        @SuppressWarnings("unchecked") Map<String,Object> o=(Map<String,Object>)MiniJson.parse("{\"a\":[0.7991666666666667,-0.38,1e-3],\"b\":\"x\\\"y\",\"c\":null,\"d\":true}");
        @SuppressWarnings("unchecked") List<Object> a=(List<Object>)o.get("a");
        assertEquals(0.7991666666666667,(Double)a.get(0),0);assertEquals(-0.38,(Double)a.get(1),0);assertEquals(1e-3,(Double)a.get(2),0);
        assertEquals("x\"y",o.get("b"));assertNull(o.get("c"));assertEquals(Boolean.TRUE,o.get("d"));
        for(String bad:new String[]{"{\"a\":1,}x","{\"a\" 1}","[1,2","{\"markers\":[]} trailing"})
            try{MiniJson.parse(bad);fail(bad);}catch(IllegalArgumentException expected){}
        try{ModelSpec.parse("{\"id\":\"x\",\"pose\":{\"minute_track_inner_r\":0.9,\"minute_track_outer_r\":0.98},\"markers\":[{\"hour\":2,\"shape\":\"round\"},{\"hour\":2,\"shape\":\"round\"}],\"seconds_hand\":{\"dot_r_min\":0.5,\"dot_r_max\":0.6}}","models/x");
            fail("duplicate hour accepted");}catch(IllegalArgumentException expected){}
    }

    /** A different layout (batons at 3, 6 and 9, no date) needs only a spec file; with no genuine reference yet,
     *  nothing it measures can become a finding. */
    @Test public void aSecondModelNeedsNoCodeAndNoReferenceMeansNoFindings()throws Exception{
        ModelSpec m=ModelSpec.load(ModelSpec.directory(testResources()),"test_three_batons");
        ModelReference ref=ModelReference.load(ModelSpec.directory(testResources()),m);
        assertNull(m.date);assertNull(ref.triangle);assertFalse(ref.has("six_rot"));
        assertEquals(3,m.withShape(ModelSpec.Shape.BATON).size());assertEquals(11,m.ringMarkers().size());
        // a measurement in which every marker is far from its genuine position
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(ModelSpec.Marker k:m.ringMarkers()){Alpha94MarkerMeasurement.Marker x=new Alpha94MarkerMeasurement.Marker(k.hour,k.kind());x.spec=k;
            x.usable=true;x.rotationDeg=5;x.localOffsetPx=10;x.localRadialPx=10;x.localTangentialPx=0;ms.add(x);}
        Alpha94MarkerMeasurement.Ring g=new Alpha94MarkerMeasurement.Ring();g.usable=true;g.rotationDeg=3;g.shiftPx=10;
        Alpha94MarkerMeasurement.Report r=new Alpha94MarkerMeasurement.Report(ms,g,300,null);
        Map<Integer,Alpha99MarkerInterference.Check> clean=new java.util.LinkedHashMap<>();
        for(ModelSpec.Marker k:m.markers){Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(k.hour);c.clean=true;clean.put(k.hour,c);}
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,clean,m,ref);
        assertEquals(1+3+8+1+1,s.all.size());                                // triangle, 3 batons, 8 rounds, round plot size, ring; no date
        assertTrue(s.clear().isEmpty());assertTrue(s.worth().isEmpty());
        for(Alpha99Findings.Finding f:s.all){assertEquals(f.key,Alpha99Findings.Status.NOT_ASSESSED,f.status);assertEquals("no genuine reference yet",f.shortReason);}
        assertEquals("3 o'clock",s.all.get(1).title);
        // the interference check runs on the new layout as well (synthetic black dial with this model's markers)
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(Alpha99FindingsTest.dial(-1,0,0,-1),Alpha99FindingsTest.H,m);
        assertEquals(12,c.size());assertTrue(c.containsKey(3));
    }

    static File researchDir(){
        File d=new File("").getAbsoluteFile();
        while(d!=null){File f=new File(d,"tools/research/alpha96_calibration");if(f.isDirectory())return f;d=d.getParentFile();}
        throw new AssertionError("research folder not found");
    }
    static File testResources(){
        File d=new File("").getAbsoluteFile();
        while(d!=null){
            for(String rel:new String[]{"src/test/resources","android/app/src/test/resources"}){File a=new File(d,rel);if(new File(a,"models").isDirectory())return a;}
            d=d.getParentFile();
        }
        throw new AssertionError("test resources not found");
    }
}
