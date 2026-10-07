package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.Test;

/**
 * Alpha99 evidence layer: uncertainty constants pinned to the genuine-only research file, the three evidence states,
 * the per-marker interference gate (fail closed), per-hour round findings, no new findings relative to Alpha98 on the
 * recorded runner rows, plain wording, and the interference check itself on synthetic dials (hand across a round,
 * hand passing clear, seconds hand along the 6).
 */
public class Alpha99FindingsTest {
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

    static Map<Integer,Alpha99MarkerInterference.Check> allClean(){
        Map<Integer,Alpha99MarkerInterference.Check> m=new LinkedHashMap<>();
        Alpha99MarkerInterference.Check t=new Alpha99MarkerInterference.Check(12);t.clean=true;m.put(12,t);
        for(int h:Alpha94MarkerMeasurement.HOURS){Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(h);c.clean=true;m.put(h,c);}
        return m;
    }

    // ---------------------------------------------------------------- constants
    @Test public void uncertaintyMatchesResearchFile()throws Exception{
        Properties p=new Properties();p.load(Files.newBufferedReader(cal("alpha99_uncertainty.properties").toPath()));
        assertEquals(Double.parseDouble(p.getProperty("k_sigma")),Alpha99Uncertainty.K_SIGMA,0);
        Object[][] pairs={{"six_rot.deg",Alpha99Uncertainty.SIX_ROT_DEG},{"nine_rot.deg",Alpha99Uncertainty.NINE_ROT_DEG},
                {"six_off.R",Alpha99Uncertainty.SIX_OFF_R},{"six_off.px",Alpha99Uncertainty.SIX_OFF_PX},
                {"nine_off.R",Alpha99Uncertainty.NINE_OFF_R},{"nine_off.px",Alpha99Uncertainty.NINE_OFF_PX},
                {"rounds_off.R",Alpha99Uncertainty.ROUNDS_OFF_R},{"rounds_off.px",Alpha99Uncertainty.ROUNDS_OFF_PX},
                {"ring_rot.deg",Alpha99Uncertainty.RING_ROT_DEG},{"ring_shift.R",Alpha99Uncertainty.RING_SHIFT_R},{"ring_shift.px",Alpha99Uncertainty.RING_SHIFT_PX},
                {"twelve_centreline.deg",Alpha99Uncertainty.TWELVE_CENTRELINE_DEG},{"twelve_sides.deg",Alpha99Uncertainty.TWELVE_SIDES_DEG},
                {"twelve_lateral.R",Alpha99Uncertainty.TWELVE_LATERAL_R},{"twelve_lateral.px",Alpha99Uncertainty.TWELVE_LATERAL_PX},
                {"date_tilt.deg",Alpha99Uncertainty.DATE_TILT_DEG}};
        for(Object[] x:pairs){assertEquals((String)x[0],Double.parseDouble(p.getProperty((String)x[0])),(double)x[1],0);
            assertTrue((String)x[0]+" from genuine multi-photo watches",Integer.parseInt(p.getProperty(x[0]+".watches"))>=8);}
    }

    // ---------------------------------------------------------------- evidence states
    @Test public void threeEvidenceStates(){
        double max=0.7,s=0.2,k=Alpha99Uncertainty.K_SIGMA;
        assertEquals(Alpha99Findings.Status.WITHIN,m(max,max,s).status());                 // a reference watch is never beyond itself
        assertEquals(Alpha99Findings.Status.WITHIN,m(0.5,max,s).status());
        assertEquals(Alpha99Findings.Status.WORTH,m(max+0.1,max,s).status());
        assertEquals(Alpha99Findings.Status.WORTH,m(max+k*s-1e-6,max,s).status());
        assertEquals(Alpha99Findings.Status.CLEAR,m(max+k*s+1e-3,max,s).status());
        assertEquals("no allowance -> never clear",Alpha99Findings.Status.WORTH,m(max+10,max,Double.NaN).status());
        // the genuine range is not widened by the allowance: anything beyond the furthest genuine watch is at least WORTH
        assertEquals(Alpha99Findings.Status.WORTH,m(max*1.01,max,s).status());
    }
    private static Alpha99Findings.Measure m(double v,double max,double s){return new Alpha99Findings.Measure("x",v,max,s,40,"deg","","");}

    @Test public void batonExamplesFromTheBrief(){
        // 6 rotation ~0.8 deg vs genuine max ~0.7 -> worth a look; 9 rotation ~1.6 deg vs max ~0.65 -> clear
        Alpha94MarkerMeasurement.Report r=report(6,0.8+Alpha98Reference.NOMINAL_SIX_ROT,9,-1.6+Alpha98Reference.NOMINAL_NINE_ROT);
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean());
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"six").status);
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"nine").status);
        assertEquals("9 o'clock",s.tiles().get(0).title);                                 // clear first
        assertTrue(find(s,"nine").shortLine().startsWith("1.6° CCW; genuine max 0.6°"));
    }

    // ---------------------------------------------------------------- interference gate
    @Test public void contaminatedMarkersAreNeverFindings(){
        Alpha94MarkerMeasurement.Report r=report(6,3.0,9,-3.0);
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind)){m.localOffsetPx=m.hour==2?5.0:0.1;}
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();
        c.get(6).clean=false;c.get(6).reason=Alpha99MarkerInterference.HAND;
        c.get(2).clean=false;c.get(2).reason=Alpha99MarkerInterference.HAND;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,c);
        Alpha99Findings.Finding six=find(s,"six"),two=find(s,"round2");
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,six.status);assertEquals("hand crosses marker",six.shortReason);assertTrue(six.visual);
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,two.status);assertEquals("2 o'clock",two.title);
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"nine").status);                   // the clean marker still reports
        // no interference result at all -> every marker withheld (fail closed)
        Alpha99Findings.Summary none=Alpha99Findings.build(r,null,null);
        for(Alpha99Findings.Finding f:none.all)if(f.shape!=Alpha99Findings.Shape.RING&&f.shape!=Alpha99Findings.Shape.DATE)
            assertEquals(f.key,Alpha99Findings.Status.NOT_ASSESSED,f.status);
    }

    @Test public void roundsAreJudgedAndNamedOneByOne(){
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);
        double lim=Alpha98Findings.matchedMax(Alpha98Reference.ROUNDS_OFF_FAR,Alpha98Reference.ROUNDS_OFF_R,r.dialRadiusPx);
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))m.localOffsetPx=(m.hour==8||m.hour==11?lim*1.2:lim*0.5)*r.dialRadiusPx;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean());
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"round8").status);
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"round11").status);
        assertEquals(Alpha99Findings.Status.WITHIN,find(s,"round1").status);
        assertTrue(s.withinLine().contains("1, 2, 4, 5, 7, 10"));
    }

    // ---------------------------------------------------------------- no new findings vs Alpha98 on the recorded rows
    @Test public void neverOutsideWhereAlpha98WasWithin()throws Exception{
        List<Map<String,String>> rows=new ArrayList<>(csv("results/runner_local.csv"));rows.addAll(csv("results/priority_genuine_runner.csv"));
        int n=0;
        for(Map<String,String> row:rows){
            if(!"accepted".equals(row.get("status")))continue;
            Alpha94MarkerMeasurement.Report r=Alpha98FindingsTest.report(row);
            Alpha98Findings.Summary old=Alpha98Findings.build(r,null);
            Alpha99Findings.Summary now=Alpha99Findings.build(r,null,allClean());
            for(Alpha99Findings.Finding f:now.all){
                if(f.status!=Alpha99Findings.Status.CLEAR&&f.status!=Alpha99Findings.Status.WORTH)continue;
                String key=f.key.startsWith("round")?"rounds":f.key;
                Alpha98Findings.Finding o=null;for(Alpha98Findings.Finding x:old.all)if(x.key.equals(key))o=x;
                assertNotNull(o);
                assertEquals(row.get("photo_id")+" "+f.key,Alpha98Findings.Status.OUTSIDE,o.status);
            }
            // and every Alpha98 finding is still outside (clear or worth a look) when nothing interferes
            for(Alpha98Findings.Finding o:old.outside()){
                boolean any=false;
                for(Alpha99Findings.Finding f:now.all)if((f.key.startsWith("round")?"rounds":f.key).equals(o.key)
                        &&(f.status==Alpha99Findings.Status.CLEAR||f.status==Alpha99Findings.Status.WORTH))any=true;
                assertTrue(row.get("photo_id")+" "+o.key,any);
            }
            n++;
        }
        assertTrue(n>=16);
    }

    @Test public void plainWordingWithoutVerdicts()throws Exception{
        List<Map<String,String>> rows=new ArrayList<>(csv("results/runner_local.csv"));rows.addAll(csv("results/priority_genuine_runner.csv"));
        for(Map<String,String> row:rows){
            if(!"accepted".equals(row.get("status")))continue;
            Alpha99Findings.Summary s=Alpha99Findings.build(Alpha98FindingsTest.report(row),null,allClean());
            List<String> texts=new ArrayList<>(s.headlineLines());texts.add(s.withinLine());texts.add(s.notAssessedLine());
            for(Alpha99Findings.Finding f:s.all){texts.add(f.text());texts.add(f.shortLine());texts.addAll(f.detail());}
            for(String t:texts)for(String w:new String[]{"PASS","FAIL","fake","Fake","replica","Replica","counterfeit","authentic watch"})
                assertFalse(row.get("photo_id")+": "+t,t.contains(w));
            assertEquals(13,s.all.size());                                                  // 12, 6, 9, 8 rounds, ring, date
            for(Alpha99Findings.Finding f:s.notAssessed())assertFalse(f.reason.isEmpty());
        }
        assertTrue(Alpha99Findings.DISCLAIMER.contains("not an authenticity verdict"));
        assertEquals("Dial marker ring",Alpha99Findings.ring(null,200).title);
    }

    @Test public void headlineCountsByEvidenceStrength(){
        Alpha94MarkerMeasurement.Report r=report(6,0.8+Alpha98Reference.NOMINAL_SIX_ROT,9,-1.6+Alpha98Reference.NOMINAL_NINE_ROT);
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();c.get(12).clean=false;c.get(12).reason=Alpha99MarkerInterference.HAND;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,c);
        assertEquals("1 clear alignment finding",s.headlineLines().get(0));
        assertEquals("1 other measurement is worth a look",s.headlineLines().get(1));
        assertTrue(s.headlineLines().get(2).endsWith("could not be assessed"));
    }

    // ---------------------------------------------------------------- interference check on synthetic dials
    static final double[] H={200,0,500,0,200,500,0,0,1};   // R = 200 px, centre (500, 500)

    /** Synthetic black dial: lume markers (220) in a metal ring (150) on the Alpha92 master; optional hands. */
    static Alpha99MarkerInterference.Sampler dial(double handDeg,double handWidth,int handGrey,double secondsDeg){
        return (px,py)->{
            double x=(px-500)/200,y=(py-500)/200,r=Math.hypot(x,y);
            if(handDeg>=0&&onHand(x,y,handDeg,handWidth,0.92))return handGrey;
            if(secondsDeg>=0){
                double a=Math.toRadians(secondsDeg),dx=Math.sin(a)*0.545,dy=-Math.cos(a)*0.545;
                if(Math.hypot(x-dx,y-dy)<0.04)return 220;
                if(onHand(x,y,secondsDeg,0.006,0.95))return 28;                               // dark shaft, barely visible
            }
            if(r>0.93)return 60;
            for(int h=1;h<=11;h++){if(h==3)continue;
                double a=Math.toRadians(h*30.0);
                if(h==6||h==9){double rr=x*Math.sin(a)-y*Math.cos(a),tt=x*Math.cos(a)+y*Math.sin(a);
                    double dr=Math.abs(rr-Alpha92GmtMaster.BATON_CENTER_R)-Alpha92GmtMaster.BATON_RADIAL_HALF,dt=Math.abs(tt)-Alpha92GmtMaster.BATON_TANGENTIAL_HALF;
                    if(dr<=0&&dt<=0)return Math.max(dr,dt)>-0.012?150:220;continue;}
                double d=Math.hypot(x-Math.sin(a)*Alpha92GmtMaster.ROUND_CENTER_R,y+Math.cos(a)*Alpha92GmtMaster.ROUND_CENTER_R);
                if(d<=Alpha92GmtMaster.ROUND_OUTER_R)return d>Alpha92GmtMaster.ROUND_OUTER_R-0.012?150:220;
            }
            double sd=Alpha99MarkerInterference.dist(12,-y,x);
            if(sd<=0)return sd>-0.012?150:220;
            return 20;
        };
    }
    private static boolean onHand(double x,double y,double deg,double width,double len){
        double a=Math.toRadians(deg),ex=Math.sin(a),ey=-Math.cos(a);double along=x*ex+y*ey,across=-x*ey+y*ex;
        return along>0.05&&along<len&&Math.abs(across)<=width/2;
    }

    @Test public void cleanSyntheticDialPasses(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(-1,0,0,-1),H);
        for(Alpha99MarkerInterference.Check x:c.values())assertTrue(x.hour+": "+x.reason,x.clean);
    }

    @Test public void handAcrossARoundMarkerWithholdsIt(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(62,0.03,190,-1),H);
        assertFalse(c.get(2).clean);assertEquals(Alpha99MarkerInterference.HAND,c.get(2).reason);
        assertTrue(c.get(1).clean);assertTrue(c.get(4).clean);assertTrue(c.get(12).clean);
    }

    @Test public void handPassingClearDoesNotWithhold(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(75,0.03,190,-1),H);
        assertTrue(c.get(2).clean);assertTrue(c.get(4).clean);
    }

    @Test public void secondsHandAlongTheSixWithholdsIt(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(-1,0,0,185.5),H);
        assertFalse(c.get(6).clean);assertTrue(c.get(6).secondsHand);
        assertTrue(c.get(12).clean);assertTrue(c.get(5).clean);assertTrue(c.get(7).clean);
    }

    @Test public void failsClosedWithoutAnImage(){
        for(Alpha99MarkerInterference.Check x:Alpha99MarkerInterference.analyse((Alpha99MarkerInterference.Sampler)null,H).values())assertFalse(x.clean);
        for(Alpha99MarkerInterference.Check x:Alpha99MarkerInterference.analyse(dial(-1,0,0,-1),null).values())assertFalse(x.clean);
    }

    // ---------------------------------------------------------------- helpers
    static Alpha94MarkerMeasurement.Report report(int hA,double rotA,int hB,double rotB){
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(int h:Alpha94MarkerMeasurement.HOURS){
            Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(h,(h==6||h==9)?"baton":"round");
            m.usable=true;m.rotationDeg=h==hA?rotA:h==hB?rotB:0;m.localOffsetPx=0.1;m.localRadialPx=0.1;m.localTangentialPx=0;ms.add(m);
        }
        Alpha94MarkerMeasurement.Ring g=new Alpha94MarkerMeasurement.Ring();g.usable=true;g.rotationDeg=Alpha98Reference.NOMINAL_RING_ROT;g.shiftPx=0.1;
        Alpha94MarkerMeasurement.Marker t=new Alpha94MarkerMeasurement.Marker(12,"triangle");t.usable=false;t.reason="not part of this test";
        return new Alpha94MarkerMeasurement.Report(ms,g,250,t);
    }
    static Alpha99Findings.Finding find(Alpha99Findings.Summary s,String key){for(Alpha99Findings.Finding f:s.all)if(f.key.equals(key))return f;throw new AssertionError(key);}
}
