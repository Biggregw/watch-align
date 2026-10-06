package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

/**
 * Alpha97 12 research readout: frozen constants pinned to the research calibration files, parity with the validated
 * desktop prototype (tools/desktop-harness/drivers/Alpha97TwelveAngles.java) on recorded production inputs, fail-closed
 * behaviour, wording (no verdicts) and the Alpha96 summary lines staying identical.
 */
public class Alpha97TwelveReadoutTest {
    private static File cal(String rel){
        File d=new File("").getAbsoluteFile();
        while(d!=null){File f=new File(d,"tools/research/alpha96_calibration/"+rel);if(f.exists())return f;d=d.getParentFile();}
        throw new AssertionError("calibration file not found: "+rel);
    }

    private static List<Map<String,String>> csv(String rel)throws Exception{
        List<String> lines=Files.readAllLines(cal(rel).toPath(),StandardCharsets.UTF_8);
        String[] h=lines.get(0).split(",",-1);List<Map<String,String>> out=new ArrayList<>();
        for(int i=1;i<lines.size();i++){String[] f=lines.get(i).split(",",-1);Map<String,String> m=new HashMap<>();
            for(int k=0;k<h.length&&k<f.length;k++)m.put(h[k],f[k]);out.add(m);}
        return out;
    }

    private static double d(String s){return s==null||s.isEmpty()?Double.NaN:Double.parseDouble(s);}

    // ---------------------------------------------------------------- frozen constants
    @Test public void nominalMatchesCalibrationFile()throws Exception{
        Properties p=new Properties();p.load(Files.newBufferedReader(cal("m12_nominal.properties").toPath()));
        assertEquals(Integer.parseInt(p.getProperty("n_watches").trim()),Alpha97TwelveReadout.N_WATCHES);
        assertEquals(45,Alpha97TwelveReadout.N_WATCHES);   // 41 catalogue watches + 4 owner-priority official / CPO watches
        assertEquals(d(p.getProperty("radial_R").trim()),Alpha97TwelveReadout.NOMINAL_RADIAL_R,0);
        assertEquals(d(p.getProperty("tangential_R").trim()),Alpha97TwelveReadout.NOMINAL_TANGENTIAL_R,0);
        assertEquals(d(p.getProperty("left_side_deg").trim()),Alpha97TwelveReadout.NOMINAL_LEFT_SIDE_DEG,0);
        assertEquals(d(p.getProperty("right_side_deg").trim()),Alpha97TwelveReadout.NOMINAL_RIGHT_SIDE_DEG,0);
        assertEquals(d(p.getProperty("rotation_deg").trim()),Alpha97TwelveReadout.NOMINAL_ROTATION_DEG,0);
    }

    @Test public void genuineContextMatchesReferenceFileIncludesPriorityAndExcludesSwe()throws Exception{
        List<Map<String,String>> ref=csv("m12_genuine_reference.csv");
        assertEquals(Alpha97TwelveReadout.N_WATCHES,ref.size());
        int priority=0;for(Map<String,String> r:ref)if("Owner priority".equals(r.get("source")))priority++;
        assertEquals("owner-priority official / CPO watches must be in the reference",4,priority);
        for(int i=0;i<ref.size();i++){
            assertFalse("SWE must be excluded",ref.get(i).get("source").contains("SWE"));
            assertEquals(d(ref.get(i).get("lateral_R")),Alpha97TwelveReadout.GENUINE_LATERAL_R[i],0);
            assertEquals(d(ref.get(i).get("centreline_deg")),Alpha97TwelveReadout.GENUINE_CENTRELINE_DEG[i],0);
            assertEquals(d(ref.get(i).get("sides_deg")),Alpha97TwelveReadout.GENUINE_SIDES_DEG[i],0);
        }
    }

    // ---------------------------------------------------------------- prototype parity on recorded production inputs
    private static Alpha94MarkerMeasurement.Report reportFromRunner(Map<String,String> r){
        Alpha94MarkerMeasurement.Marker tri=new Alpha94MarkerMeasurement.Marker(12,"triangle");
        tri.usable="true".equals(r.get("m12_usable"));
        tri.localRadialPx=d(r.get("m12_local_radial_px"));tri.localTangentialPx=d(r.get("m12_local_tangential_px"));
        tri.rotationDeg=d(r.get("m12_rotation_deg"));tri.leftSideErrDeg=d(r.get("m12_left_side_err_deg"));
        tri.rightSideErrDeg=d(r.get("m12_right_side_err_deg"));
        return new Alpha94MarkerMeasurement.Report(new ArrayList<>(),new Alpha94MarkerMeasurement.Ring(),d(r.get("dial_radius_px")),tri);
    }

    private static int parity(String runnerCsv,String protoCsv)throws Exception{
        Map<String,Map<String,String>> run=new HashMap<>();for(Map<String,String> r:csv(runnerCsv))run.put(r.get("photo_id"),r);
        int n=0;
        for(Map<String,String> p:csv(protoCsv)){
            if(!"accepted".equals(p.get("status")))continue;
            Map<String,String> r=run.get(p.get("photo_id"));if(r==null)continue;
            Alpha97TwelveReadout.Result o=Alpha97TwelveReadout.from(reportFromRunner(r));
            String id=p.get("photo_id");
            assertEquals(id+" usable","true".equals(p.get("usable")),o.usable);
            n++;
            if(!o.usable)continue;
            assertEquals(id,d(p.get("centreline_deg")),o.centrelineDeg,2e-4);
            assertEquals(id,d(p.get("left_side_deg")),o.leftSideDeg,2e-4);
            assertEquals(id,d(p.get("right_side_deg")),o.rightSideDeg,2e-4);
            assertEquals(id,d(p.get("sides_deg")),o.sidesDeg,2e-4);
            assertEquals(id,d(p.get("lateral_px")),o.lateralPx,2e-4);
            assertEquals(id,d(p.get("lateral_R")),o.lateralR,1e-5);   // fixture is printed to 5 decimals
            assertEquals(id,Integer.parseInt(p.get("at_least_centreline")),o.atLeastCentreline);
            assertEquals(id,Integer.parseInt(p.get("at_least_sides")),o.atLeastSides);
            assertEquals(id,Integer.parseInt(p.get("at_least_lateral")),o.atLeastLateral);
        }
        return n;
    }

    @Test public void matchesDesktopPrototypeOnLocalPhotos()throws Exception{
        assertEquals(8,parity("results/runner_local.csv","results/m12_angles_lateral/proto_local.csv"));
    }

    @Test public void matchesDesktopPrototypeOnScaledVariants()throws Exception{
        assertTrue(parity("results/resolution/runner_scaled.csv","results/m12_angles_lateral/proto_scaled.csv")>=160);
    }

    @Test public void knownLocalPhotoFindings()throws Exception{
        Map<String,Alpha97TwelveReadout.Result> o=new HashMap<>();
        for(Map<String,String> r:csv("results/runner_local.csv"))o.put(r.get("photo_id"),Alpha97TwelveReadout.from(reportFromRunner(r)));
        // marketplace candidates: well inside the genuine reference on every component
        for(String id:new String[]{"GEN_CAND_HO_01","GEN_CAND_HO_02"}){
            Alpha97TwelveReadout.Result x=o.get(id);assertTrue(x.usable);
            assertTrue(id,x.atLeastCentreline>=5&&x.atLeastSides>=5&&x.atLeastLateral>=5);
        }
        // Theonewatches: angular and lateral findings
        Alpha97TwelveReadout.Result t=o.get("RL_THEONE_BLNR");
        assertTrue(t.usable);assertTrue(t.centrelineDeg>0.8);assertTrue(t.atLeastSides==0);assertEquals(0,t.atLeastLateral);assertTrue(t.lateralPx<0);
        // Local BLNR: lateral finding
        Alpha97TwelveReadout.Result l=o.get("RL_LOCAL_BLNR");assertTrue(l.usable);assertEquals(0,l.atLeastLateral);assertTrue(l.lateralPx<-2.5);
        // ARF crooked-6: lateral finding while its angles are ordinary
        Alpha97TwelveReadout.Result a=o.get("RL_ARF_BLRO_CROOKED6");assertTrue(a.usable);assertEquals(0,a.atLeastLateral);
        assertTrue(a.atLeastCentreline>=10&&a.atLeastSides>=10);
        // Batgirl (and both WEX candidates): 12 occluded -> withheld
        for(String id:new String[]{"RL_USER_BATGIRL","GEN_CAND_WEX_BLRO_01","GEN_CAND_WEX_BLRO_02"})assertFalse(id,o.get(id).usable);
    }

    // ---------------------------------------------------------------- fail-closed and wording
    @Test public void withholdsOccludedOrUnfittedTwelve(){
        Alpha94MarkerMeasurement.Marker occluded=new Alpha94MarkerMeasurement.Marker(12,"triangle");occluded.usable=false;
        Alpha97TwelveReadout.Result a=Alpha97TwelveReadout.from(new Alpha94MarkerMeasurement.Report(new ArrayList<>(),new Alpha94MarkerMeasurement.Ring(),200,occluded));
        assertFalse(a.usable);assertTrue(Alpha97TwelveReadout.line(a).contains("withheld"));
        Alpha94MarkerMeasurement.Marker noRing=new Alpha94MarkerMeasurement.Marker(12,"triangle");noRing.usable=true;noRing.rotationDeg=0.1;
        assertFalse(Alpha97TwelveReadout.from(new Alpha94MarkerMeasurement.Report(new ArrayList<>(),new Alpha94MarkerMeasurement.Ring(),200,noRing)).usable);
        assertFalse(Alpha97TwelveReadout.from(new Alpha94MarkerMeasurement.Report(new ArrayList<>(),new Alpha94MarkerMeasurement.Ring(),200,null)).usable);
        assertFalse(Alpha97TwelveReadout.from(null).usable);
    }

    @Test public void lineIsResearchLabelledWithoutVerdictsOrRadialValue(){
        Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(12,"triangle");
        m.usable=true;m.localRadialPx=-7.77;m.localTangentialPx=-0.51;m.rotationDeg=0.96;m.leftSideErrDeg=0.99;m.rightSideErrDeg=0.89;
        String s=Alpha97TwelveReadout.line(Alpha97TwelveReadout.from(new Alpha94MarkerMeasurement.Report(new ArrayList<>(),new Alpha94MarkerMeasurement.Ring(),165,m)));
        assertTrue(s.startsWith("12 (research"));
        assertTrue(s.contains("lighting-sensitive · not assessed"));
        assertTrue(s.contains("centreline")&&s.contains("left side")&&s.contains("right side"));
        for(String w:new String[]{"PASS","FAIL","BORDERLINE","pass","fail","borderline","genuine?","fake","replica"})assertFalse(w,s.contains(w));
        double radial=Alpha97TwelveReadout.from(new Alpha94MarkerMeasurement.Report(new ArrayList<>(),new Alpha94MarkerMeasurement.Ring(),165,m)).radialPx;
        assertFalse("radial value must not be displayed",s.contains("7.77")||s.contains(String.format(java.util.Locale.US,"%.2f",Math.abs(radial))));
    }

    @Test public void summaryChangesOnlyTheTwelveLine(){
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(int h:Alpha94MarkerMeasurement.HOURS){
            Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(h,(h==6||h==9)?"baton":"round");
            m.usable=true;m.dialRightPx=0.1*h;m.dialDownPx=-0.05*h;m.rotationDeg=0.01*h;m.localOffsetPx=0.02*h;ms.add(m);
        }
        Alpha94MarkerMeasurement.Ring ring=new Alpha94MarkerMeasurement.Ring();ring.usable=true;ring.n=10;ring.shiftPx=0.5;ring.scalePct=0.1;ring.rotationDeg=0.05;
        Alpha94MarkerMeasurement.Marker tri=new Alpha94MarkerMeasurement.Marker(12,"triangle");
        tri.usable=true;tri.localRadialPx=-0.8;tri.localTangentialPx=-0.2;tri.rotationDeg=0.3;tri.leftSideErrDeg=0.2;tri.rightSideErrDeg=0.4;
        Alpha94MarkerMeasurement.Report r=new Alpha94MarkerMeasurement.Report(ms,ring,200,tri);
        String[] a96=r.compactSummary().split("\n",-1),a97=Alpha97TwelveReadout.summary(r).split("\n",-1);
        assertEquals(a96.length,a97.length);
        for(int i=0;i<a96.length;i++)if(i!=1)assertEquals("line "+i+" must be identical to Alpha96",a96[i],a97[i]);
        assertTrue(a96[1].startsWith("12 (research)"));assertTrue(a97[1].startsWith(Alpha97TwelveReadout.LABEL));
    }
}
