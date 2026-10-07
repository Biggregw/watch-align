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
 * Alpha98 results logic: reference constants pinned to the research files, flag decisions identical to the research
 * offline check (build_alpha98_reference.py) on the recorded production inputs of the local / owner photos, the
 * resolution-matching and fail-closed rules, and plain wording without verdicts.
 */
public class Alpha98FindingsTest {
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

    // ---------------------------------------------------------------- constants
    @Test public void referenceMatchesResearchFiles()throws Exception{
        Map<String,List<double[]>> ref=new HashMap<>();
        for(Map<String,String> r:csv("alpha98_reference.csv"))ref.computeIfAbsent(r.get("feature"),k->new ArrayList<>()).add(new double[]{d(r.get("far")),d(r.get("dial_radius_px"))});
        Object[][] pairs={{"six_rot",Alpha98Reference.SIX_ROT_FAR,Alpha98Reference.SIX_ROT_R},{"six_off",Alpha98Reference.SIX_OFF_FAR,Alpha98Reference.SIX_OFF_R},
                {"nine_rot",Alpha98Reference.NINE_ROT_FAR,Alpha98Reference.NINE_ROT_R},{"nine_off",Alpha98Reference.NINE_OFF_FAR,Alpha98Reference.NINE_OFF_R},
                {"rounds_off",Alpha98Reference.ROUNDS_OFF_FAR,Alpha98Reference.ROUNDS_OFF_R},{"ring_rot",Alpha98Reference.RING_ROT_FAR,Alpha98Reference.RING_ROT_R},
                {"ring_shift",Alpha98Reference.RING_SHIFT_FAR,Alpha98Reference.RING_SHIFT_R},{"date_tilt",Alpha98Reference.DATE_TILT_FAR,Alpha98Reference.DATE_TILT_R}};
        for(Object[] p:pairs){
            List<double[]> r=ref.get((String)p[0]);double[] far=(double[])p[1],R=(double[])p[2];
            assertEquals((String)p[0],r.size(),far.length);
            for(int i=0;i<far.length;i++){assertEquals(r.get(i)[0],far[i],0);
                if(Double.isNaN(r.get(i)[1]))assertTrue(Double.isNaN(R[i]));else assertEquals(r.get(i)[1],R[i],0);}
        }
        assertEquals(52,Alpha98Reference.DATE_TILT_FAR.length);   // date window: SWE included (owner decision)
        Properties p=new Properties();p.load(Files.newBufferedReader(cal("alpha98_nominal.properties").toPath()));
        assertEquals(d(p.getProperty("six_rot")),Alpha98Reference.NOMINAL_SIX_ROT,0);
        assertEquals(d(p.getProperty("nine_rot")),Alpha98Reference.NOMINAL_NINE_ROT,0);
        assertEquals(d(p.getProperty("ring_rot")),Alpha98Reference.NOMINAL_RING_ROT,0);
        assertEquals(d(p.getProperty("date_tilt")),Alpha98Reference.NOMINAL_DATE_TILT,0);
    }

    // ---------------------------------------------------------------- flags vs the research offline check
    static Alpha94MarkerMeasurement.Report report(Map<String,String> r){
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(int h:Alpha94MarkerMeasurement.HOURS){
            Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(h,(h==6||h==9)?"baton":"round");
            m.usable="true".equals(r.get("m"+h+"_usable"));m.reason=r.getOrDefault("m"+h+"_reason","");
            m.rotationDeg=d(r.get("m"+h+"_rotation_deg"));m.localOffsetPx=d(r.get("m"+h+"_local_px"));
            m.localRadialPx=d(r.get("m"+h+"_local_radial_px"));m.localTangentialPx=d(r.get("m"+h+"_local_tangential_px"));
            ms.add(m);
        }
        Alpha94MarkerMeasurement.Ring g=new Alpha94MarkerMeasurement.Ring();
        g.usable="true".equals(r.get("ring_usable"));g.rotationDeg=d(r.get("ring_rotation_deg"));g.shiftPx=d(r.get("ring_shift_px"));
        Alpha94MarkerMeasurement.Marker t=new Alpha94MarkerMeasurement.Marker(12,"triangle");t.usable=false;t.reason="not part of this test";
        return new Alpha94MarkerMeasurement.Report(ms,g,d(r.get("dial_radius_px")),t);
    }

    @Test public void flagsMatchResearchOfflineCheck()throws Exception{
        Map<String,Map<String,String>> exp=new HashMap<>();
        for(Map<String,String> r:csv("results/alpha98/expected_flags_local.csv"))exp.computeIfAbsent(r.get("photo_id"),k->new HashMap<>()).put(r.get("feature"),r.get("status"));
        List<Map<String,String>> rows=new ArrayList<>(csv("results/runner_local.csv"));rows.addAll(csv("results/priority_genuine_runner.csv"));
        int n=0;
        for(Map<String,String> r:rows){
            Map<String,String> e=exp.get(r.get("photo_id"));if(e==null||!"accepted".equals(r.get("status")))continue;
            Alpha94MarkerMeasurement.Report rep=report(r);double R=rep.dialRadiusPx;
            String id=r.get("photo_id");
            check(id,"six",Alpha98Findings.baton(rep,6,R),e,"six_rot","six_off");
            check(id,"nine",Alpha98Findings.baton(rep,9,R),e,"nine_rot","nine_off");
            check(id,"rounds",Alpha98Findings.rounds(rep,R),e,"rounds_off");
            check(id,"ring",Alpha98Findings.ring(rep,R),e,"ring_rot","ring_shift");
            n++;
        }
        assertTrue("photos checked: "+n,n>=16);
    }

    private static void check(String id,String what,Alpha98Findings.Finding f,Map<String,String> e,String... keys){
        boolean any=false,flag=false,allNa=true;
        for(String k:keys){String s=e.get(k);if(s==null)continue;any=true;if("FLAG".equals(s))flag=true;if(!"n/a".equals(s))allNa=false;}
        if(!any){assertEquals(id+" "+what+" (research had no value -> withheld)",Alpha98Findings.Status.NOT_ASSESSED,f.status);return;}
        if(flag){assertEquals(id+" "+what,Alpha98Findings.Status.OUTSIDE,f.status);return;}
        if(allNa&&keys.length==1){assertEquals(id+" "+what,Alpha98Findings.Status.NOT_ASSESSED,f.status);return;}
        assertEquals(id+" "+what,Alpha98Findings.Status.WITHIN,f.status);
    }

    // ---------------------------------------------------------------- rules
    @Test public void roundsAreNotAssessedWhenNoComparableGenuineResolution(){
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(int h:Alpha94MarkerMeasurement.ROUND_HOURS){Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(h,"round");m.usable=true;m.localOffsetPx=5;m.localRadialPx=5;m.localTangentialPx=0;ms.add(m);}
        Alpha94MarkerMeasurement.Report r=new Alpha94MarkerMeasurement.Report(ms,new Alpha94MarkerMeasurement.Ring(),60,null);
        assertEquals(Alpha98Findings.Status.NOT_ASSESSED,Alpha98Findings.rounds(r,60).status);
    }

    @Test public void dateWindowRuleAndReasons(){
        Alpha98DateWindow.Result d=new Alpha98DateWindow.Result();d.usable=true;d.cx=0.64;d.cy=0;d.wR=0.4;d.hR=0.25;
        d.windowTiltDeg=Alpha98Reference.NOMINAL_DATE_TILT+Alpha98Findings.max(Alpha98Reference.DATE_TILT_FAR)+0.2;
        assertEquals(Alpha98Findings.Status.OUTSIDE,Alpha98Findings.date(d).status);
        d.windowTiltDeg=Alpha98Reference.NOMINAL_DATE_TILT;
        assertEquals(Alpha98Findings.Status.WITHIN,Alpha98Findings.date(d).status);
        Alpha98DateWindow.Result g=new Alpha98DateWindow.Result();g.reason="window not rectangular (fill 0.84: glare / reflection / occlusion)";
        Alpha98Findings.Finding f=Alpha98Findings.date(g);
        assertEquals(Alpha98Findings.Status.NOT_ASSESSED,f.status);assertTrue(f.reason.contains("glare"));
        assertEquals(Alpha98Findings.Status.NOT_ASSESSED,Alpha98Findings.date(null).status);
    }

    @Test public void referenceWatchNeverBeyondItself(){
        double lim=Alpha98Findings.max(Alpha98Reference.SIX_OFF_FAR);
        assertFalse(Alpha98Findings.beyond(lim,lim));assertFalse(Alpha98Findings.beyond(lim*(1+5e-5),lim));
        assertTrue(Alpha98Findings.beyond(lim*1.01,lim));
    }

    @Test public void plainWordingWithoutVerdicts()throws Exception{
        List<Map<String,String>> rows=new ArrayList<>(csv("results/runner_local.csv"));rows.addAll(csv("results/priority_genuine_runner.csv"));
        for(Map<String,String> r:rows){
            if(!"accepted".equals(r.get("status")))continue;
            Alpha98Findings.Summary s=Alpha98Findings.build(report(r),null);
            List<String> texts=new ArrayList<>();texts.add(s.headline());for(Alpha98Findings.Finding f:s.all)texts.add(f.text());
            for(String t:texts)for(String w:new String[]{"PASS","FAIL","BORDERLINE","fake","Fake","replica","Replica","counterfeit","authentic watch"})
                assertFalse(r.get("photo_id")+": "+t,t.contains(w));
            assertEquals(6,s.all.size());
            for(Alpha98Findings.Finding f:s.notAssessed())assertFalse(f.reason.isEmpty());
        }
        assertTrue(Alpha98Findings.DISCLAIMER.contains("not a verdict"));
    }

    // ---------------------------------------------------------------- numpy-equivalent helpers in the date-window port
    @Test public void numpyEquivalentHelpers(){
        assertEquals(2.5,Alpha98DateWindow.median(new double[]{4,1,3,2}),0);
        assertEquals(3.0,Alpha98DateWindow.median(new double[]{5,1,3}),0);
        assertEquals(9.1,Alpha98DateWindow.percentile(new double[]{0,1,2,3,4,5,6,7,8,9,10},91),1e-12);
        assertEquals(0.0,Alpha98DateWindow.normAngle(180),0);assertEquals(-30.0,Alpha98DateWindow.normAngle(150),0);
        assertEquals(90.0,Alpha98DateWindow.normAngle(90),0);assertEquals(90.0,Alpha98DateWindow.normAngle(-90),0);
    }
}
