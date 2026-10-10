package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
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
 * Alpha99 evidence layer: the GMT model's uncertainty (app assets) pinned to the genuine-only research file, the three evidence states,
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
        for(int h:TestModels.gmt().ringHours()){Alpha99MarkerInterference.Check c=new Alpha99MarkerInterference.Check(h);c.clean=true;m.put(h,c);}
        return m;
    }

    // ---------------------------------------------------------------- constants
    @Test public void uncertaintyMatchesResearchFile()throws Exception{
        Properties p=new Properties();p.load(Files.newBufferedReader(cal("alpha99_uncertainty.properties").toPath()));
        ModelReference ref=TestModels.gmtRef();
        assertEquals(Double.parseDouble(p.getProperty("k_sigma")),ref.kSigma,0);
        int n=0;
        for(String key:p.stringPropertyNames()){
            if(key.equals("k_sigma")||key.endsWith(".watches"))continue;
            int dot=key.lastIndexOf('.');String fam=key.substring(0,dot),unit=key.substring(dot+1);
            assertEquals(key,Double.parseDouble(p.getProperty(key)),ref.sigma(fam,unit),0);
            assertTrue(key+" from genuine multi-photo watches",ref.sigmaWatches(fam,unit)>=8);n++;
        }
        assertTrue("families with an allowance: "+n,n>=22);
    }

    // ---------------------------------------------------------------- evidence states
    @Test public void threeEvidenceStates(){
        double max=0.7,s=0.2,k=TestModels.gmtRef().kSigma;
        assertEquals(Alpha99Findings.Status.WITHIN,m(max,max,s).status());                 // a reference watch is never beyond itself
        assertEquals(Alpha99Findings.Status.WITHIN,m(0.5,max,s).status());
        assertEquals(Alpha99Findings.Status.WORTH,m(max+0.1,max,s).status());
        assertEquals(Alpha99Findings.Status.WORTH,m(max+k*s-1e-6,max,s).status());
        assertEquals(Alpha99Findings.Status.CLEAR,m(max+k*s+1e-3,max,s).status());
        assertEquals("no allowance -> never clear",Alpha99Findings.Status.WORTH,m(max+10,max,Double.NaN).status());
        // the genuine range is not widened by the allowance: anything beyond the furthest genuine watch is at least WORTH
        assertEquals(Alpha99Findings.Status.WORTH,m(max*1.01,max,s).status());
    }
    private static Alpha99Findings.Measure m(double v,double max,double s){return new Alpha99Findings.Measure("x",v,max,s,TestModels.gmtRef().kSigma,40,"deg","","");}

    @Test public void angleAllowanceGrowsOnSmallPhotos(){
        // genuine catalogue case: a 126711CHNR photographed at dial radius 103 px read its 12 left side 3.2 deg off while
        // the same watch's 7 other photos read ~0.9 deg; the angle allowance must scale with 1 / R like positions do
        ModelReference ref=TestModels.gmtRef();
        double big=Alpha99Findings.sigmaAng(ref,"twelve_sides",400);
        double small=Alpha99Findings.sigmaAng(ref,"twelve_sides",103);
        assertEquals(ref.sigma("twelve_sides","deg"),big,0);
        assertEquals(ref.sigma("twelve_sides","degR")/103,small,1e-12);
        double max=Alpha98Findings.max(ref.triangle.sidesDeg);
        assertEquals(Alpha99Findings.Status.WORTH,m(3.25,max,small).status());
    }

    @Test public void dateWindowNeedsTheMarkerRingToConfirmOrientation(){
        // genuine catalogue case: a mis-registered pose (markers withheld, ring not measurable) read a level date window
        // as tilted 6 deg; without the ring the orientation is unconfirmed, so the date is not assessed
        Alpha98DateWindow.Result d=new Alpha98DateWindow.Result();d.usable=true;d.cx=0.64;d.cy=0;d.wR=0.4;d.hR=0.25;
        d.windowTiltDeg=TestModels.gmtRef().nominal("date_tilt")+6;
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,Alpha99Findings.date(d,250,false,TestModels.gmtRef(),TestModels.gmt().date).status);
        assertEquals(Alpha99Findings.Status.CLEAR,Alpha99Findings.date(d,250,true,TestModels.gmtRef(),TestModels.gmt().date).status);
        d.windowTiltDeg=TestModels.gmtRef().nominal("date_tilt");
        assertEquals(Alpha99Findings.Status.WITHIN,Alpha99Findings.date(d,250,true,TestModels.gmtRef(),TestModels.gmt().date).status);
    }

    @Test public void twelveSideAngleOnItsOwnIsAtMostWorthALook(){
        // owner decision 2026-10-08: a genuine SWE studio photo read its 12 right side 4.1 deg off with direction and
        // position in range; a side angle alone is worth a look, never a clear finding
        ModelReference.Triangle t=TestModels.gmtRef().triangle;double R=250;
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);
        Alpha94MarkerMeasurement.Marker tri=new Alpha94MarkerMeasurement.Marker(12,"triangle");tri.spec=TestModels.gmt().atHour(12);
        tri.usable=true;tri.rotationDeg=t.nominalRotationDeg;tri.leftSideErrDeg=t.nominalLeftSideDeg;tri.rightSideErrDeg=t.nominalRightSideDeg-4.1;
        tri.localRadialPx=t.nominalRadialR*R;tri.localTangentialPx=t.nominalTangentialR*R;
        Alpha94MarkerMeasurement.Report alone=new Alpha94MarkerMeasurement.Report(r.markers,r.ring,R,tri);
        Alpha99Findings.Finding f=find(Alpha99Findings.build(alone,null,allClean(),TestModels.gmt(),TestModels.gmtRef()),"twelve");
        assertEquals(Alpha99Findings.Status.WORTH,f.status);
        assertTrue(f.shortLine(),f.shortLine().startsWith("right side angled 4.1° anticlockwise"));
        assertTrue(String.join(" ",f.detail()).contains("not a clear finding"));
        // a turned triangle (both sides moved together) that is also clearly off-centre is corroborated: it can be clear
        tri.leftSideErrDeg=t.nominalLeftSideDeg-4.1;
        tri.localTangentialPx=(t.nominalTangentialR+0.012)*R;
        Alpha99Findings.Finding g=find(Alpha99Findings.build(new Alpha94MarkerMeasurement.Report(r.markers,r.ring,R,tri),null,allClean(),TestModels.gmt(),TestModels.gmtRef()),"twelve");
        assertEquals(Alpha99Findings.Status.CLEAR,g.status);
        for(Alpha99Findings.Measure m:g.measures)if(m.name.equals("sides"))assertEquals(Alpha99Findings.Status.CLEAR,m.status());
    }

    @Test public void twelveWithDisagreeingSidesIsEdgeAffected(){
        // Alpha101: the recorded values of the genuine Swiss Watch Expo studio photo ce997f77df67b365 (per_photo.csv):
        // right side 4.1 deg off, left side 0.4 deg, centreline 2.5 deg - one lit edge, not a turned marker
        ModelReference ref=TestModels.gmtRef();
        assertEquals(0.96,ref.limit("twelve_sides_agreement"),0.005);   // Alpha102: the more cautious of edge-filtered (0.963) and unfiltered (0.992)
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);
        Alpha94MarkerMeasurement.Marker tri=new Alpha94MarkerMeasurement.Marker(12,"triangle");tri.spec=TestModels.gmt().atHour(12);
        tri.usable=true;tri.rotationDeg=-2.58445;tri.leftSideErrDeg=-0.59326;tri.rightSideErrDeg=-3.97507;
        tri.localRadialPx=-1.62185;tri.localTangentialPx=-0.54516;
        double R=171.51663;
        Alpha99Findings.Finding f=find(Alpha99Findings.build(new Alpha94MarkerMeasurement.Report(r.markers,r.ring,R,tri),null,allClean(),
                TestModels.gmt(),ref),"twelve");
        assertEquals(Alpha99Findings.Status.WORTH,f.status);
        for(Alpha99Findings.Measure m:f.measures)if(!m.name.equals("lateral"))assertTrue(m.name,m.capAtWorth);
        assertTrue(String.join(" ",f.detail()).contains("two sides disagree"));
        // the same two sides moved together (a turned marker) are not edge-affected
        tri.leftSideErrDeg=tri.rightSideErrDeg;
        Alpha99Findings.Finding g=find(Alpha99Findings.build(new Alpha94MarkerMeasurement.Report(r.markers,r.ring,R,tri),null,allClean(),
                TestModels.gmt(),ref),"twelve");
        for(Alpha99Findings.Measure m:g.measures)if(m.name.equals("centreline"))assertFalse(m.capAtWorth);
        assertEquals(Alpha99Findings.Status.CLEAR,g.status);
    }

    @Test public void directionsInQcStyle(){
        Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(7,"round");
        m.localRadialPx=-1;m.localTangentialPx=0.1;assertEquals("towards the centre",Alpha99Findings.towards(m));
        m.localRadialPx=0.1;m.localTangentialPx=1;assertEquals("towards the 8",Alpha99Findings.towards(m));
        m.localTangentialPx=-1;assertEquals("towards the 6",Alpha99Findings.towards(m));
        m.localRadialPx=1;m.localTangentialPx=-0.8;assertEquals("towards its minute mark and the 6",Alpha99Findings.towards(m));
        Alpha94MarkerMeasurement.Marker t=new Alpha94MarkerMeasurement.Marker(12,"triangle");t.localRadialPx=0;t.localTangentialPx=1;
        assertEquals("towards the 1",Alpha99Findings.towards(t));t.localTangentialPx=-1;assertEquals("towards the 11",Alpha99Findings.towards(t));
        Alpha94MarkerMeasurement.Marker one=new Alpha94MarkerMeasurement.Marker(1,"round");one.localRadialPx=0;one.localTangentialPx=-1;
        assertEquals("towards the 12",Alpha99Findings.towards(one));
    }

    @Test public void roundLumePlotSize(){
        // Alpha101: one plot much larger than the others on the same dial, and all plots larger than genuine
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);double R=r.dialRadiusPx;
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))m.radiusErrPx=0.001*R;
        r.atHour(4).radiusErrPx=0.007*R;                                           // 0.6% larger than its neighbours
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        Alpha99Findings.Finding four=find(s,"round4");
        assertTrue(four.status==Alpha99Findings.Status.CLEAR||four.status==Alpha99Findings.Status.WORTH);
        assertTrue(four.shortLine(),four.shortLine().startsWith("marker larger than the others by 0.60% of the dial"));
        assertTrue(Alpha99Findings.outsideOnlyByNewMeasures(four));
        assertEquals(Alpha99Findings.Status.WITHIN,find(s,"round5").status);
        assertEquals(Alpha99Findings.Status.WITHIN,find(s,"rounds_size").status);  // dial median unchanged
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))m.radiusErrPx=0.005*R;
        // Alpha103: outside the genuine range but under the visibility bar (5% of the marker's radius): too small to see
        assertEquals(Alpha99Findings.Status.MINOR,find(Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef()),"rounds_size").status);
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))m.radiusErrPx=0.008*R;
        Alpha99Findings.Summary big=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        Alpha99Findings.Finding all=find(big,"rounds_size");
        assertTrue(all.status==Alpha99Findings.Status.CLEAR||all.status==Alpha99Findings.Status.WORTH);
        assertTrue(all.shortLine().startsWith("all round markers larger by"));
        assertFalse(Alpha99Overview.hasBadge(all));                                  // dial-wide: tile, no badge
        // a hand over a round removes it from the size comparison as well
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();c.get(4).clean=false;c.get(4).reason=Alpha99MarkerInterference.HAND;
        r.atHour(4).radiusErrPx=0.02*R;
        Alpha99Findings.Summary hand=Alpha99Findings.build(r,null,c,TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,find(hand,"round4").status);
    }

    @Test public void handNearRoundIsMeasuredFromThePartAwayFromTheHandAndNeverClear(){
        // Alpha102: a round marker withheld for a nearby hand is judged from a partial-outline fit, at most worth a look
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);double R=r.dialRadiusPx;
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();c.get(4).clean=false;c.get(4).reason=Alpha99MarkerInterference.HAND;
        Map<Integer,Alpha94MarkerMeasurement.Marker> part=new HashMap<>();
        Alpha94MarkerMeasurement.Marker far=new Alpha94MarkerMeasurement.Marker(4,"round");far.usable=true;
        far.localOffsetPx=0.05*R;far.localRadialPx=0.05*R;far.localTangentialPx=0;far.radiusErrPx=Double.NaN;part.put(4,far);
        Alpha99Findings.Finding four=find(Alpha99Findings.build(r,null,c,part,TestModels.gmt(),TestModels.gmtRef()),"round4");
        assertEquals(Alpha99Findings.Status.WORTH,four.status);                      // 5% of the dial off: still not clear
        assertTrue(String.join(" ",four.detail()),String.join(" ",four.detail()).contains("away from the hand"));
        far.localOffsetPx=0;far.localRadialPx=0;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,c,part,TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.WITHIN,find(s,"round4").status);
        assertTrue(s.withinLine(),s.withinLine().contains("4 position"));
        assertTrue(s.notAssessedLine(),s.notAssessedLine().contains("4 size (hand nearby)"));
        // glare is never re-measured; no partial fit -> still withheld
        c.get(4).reason=Alpha99MarkerInterference.GLARE;
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,find(Alpha99Findings.build(r,null,c,part,TestModels.gmt(),TestModels.gmtRef()),"round4").status);
        c.get(4).reason=Alpha99MarkerInterference.HAND;
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,find(Alpha99Findings.build(r,null,c,null,TestModels.gmt(),TestModels.gmtRef()),"round4").status);
    }

    @Test public void batonExamplesFromTheBrief(){
        // 6 rotation ~1.1 deg vs genuine max ~0.7 -> worth a look (Alpha105: 0.8 deg is now under the 1.0 deg visibility bar); 9 rotation ~1.6 deg vs max ~0.65 -> clear
        Alpha94MarkerMeasurement.Report r=report(6,1.1+TestModels.gmtRef().nominal("six_rot"),9,-1.6+TestModels.gmtRef().nominal("nine_rot"));
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"six").status);
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"nine").status);
        assertEquals("9 o'clock",s.tiles().get(0).title);                                 // clear first
        assertTrue(find(s,"nine").shortLine(),find(s,"nine").shortLine().startsWith("rotated 1.6° anticlockwise; genuine up to 0.6°"));
    }

    // ---------------------------------------------------------------- interference gate
    @Test public void contaminatedMarkersAreNeverFindings(){
        Alpha94MarkerMeasurement.Report r=report(6,3.0,9,-3.0);
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind)){m.localOffsetPx=m.hour==2?5.0:0.1;}
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();
        c.get(6).clean=false;c.get(6).reason=Alpha99MarkerInterference.HAND;
        c.get(2).clean=false;c.get(2).reason=Alpha99MarkerInterference.HAND;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,c,TestModels.gmt(),TestModels.gmtRef());
        Alpha99Findings.Finding six=find(s,"six"),two=find(s,"round2");
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,six.status);assertEquals("hand crosses marker",six.shortReason);assertTrue(six.visual);
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,two.status);assertEquals("2 o'clock",two.title);
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"nine").status);                   // the clean marker still reports
        // no interference result at all -> every marker withheld (fail closed)
        Alpha99Findings.Summary none=Alpha99Findings.build(r,null,null,TestModels.gmt(),TestModels.gmtRef());
        for(Alpha99Findings.Finding f:none.all)if(f.shape!=Alpha99Findings.Shape.RING&&f.shape!=Alpha99Findings.Shape.DATE)
            assertEquals(f.key,Alpha99Findings.Status.NOT_ASSESSED,f.status);
    }

    @Test public void roundsAreJudgedAndNamedOneByOne(){
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);
        double lim=Alpha98Findings.matchedMax(TestModels.gmtRef().far("rounds_off"),TestModels.gmtRef().radius("rounds_off"),r.dialRadiusPx);
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))m.localOffsetPx=(m.hour==8||m.hour==11?lim*1.2:lim*0.5)*r.dialRadiusPx;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        // Alpha103: just past the genuine maximum is far below what the eye can see (5% of the marker's width)
        assertEquals(Alpha99Findings.Status.MINOR,find(s,"round8").status);
        assertEquals(Alpha99Findings.Status.MINOR,find(s,"round11").status);
        assertTrue(s.headline(),s.headline().startsWith("No visible deviation"));
        // Alpha105: shown as within (green, no tile), named in the within line
        assertFalse(s.headline(),s.headline().contains("Too small"));
        assertTrue(s.withinLine(),s.withinLine().contains("8")&&s.withinLine().contains("11"));
        for(Alpha99Findings.Finding f:s.tiles())assertNotEquals(Alpha99Findings.Status.MINOR,f.status);
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if(m.hour==8||m.hour==11)m.localOffsetPx=0.012*r.dialRadiusPx;
        s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertNotEquals(Alpha99Findings.Status.MINOR,find(s,"round8").status);
        assertNotEquals(Alpha99Findings.Status.WITHIN,find(s,"round8").status);
        assertEquals(Alpha99Findings.Status.WITHIN,find(s,"round1").status);
        assertTrue(s.withinLine().contains("1, 2, 4, 5, 7, 10"));
    }

    // ---------------------------------------------------------------- Alpha103 photo trust
    /** Sets a marker's local offset to a picture-direction vector (x right, y down) of the given length in px. */
    static void picture(Alpha94MarkerMeasurement.Marker m,double x,double y){
        double a=Math.toRadians(m.hour*30.0);
        m.localRadialPx=x*Math.sin(a)-y*Math.cos(a);m.localTangentialPx=x*Math.cos(a)+y*Math.sin(a);m.localOffsetPx=Math.hypot(x,y);
    }

    @Test public void oppositeMarkersDisplacedTheSameWayInThePictureAreNeverClear(){
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);double px=0.012*r.dialRadiusPx;     // well past the visibility bar
        picture(r.atHour(1),px,0);picture(r.atHour(7),px*0.9,px*0.2);                       // both to the right in the picture
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"round1").status);
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"round7").status);
        assertTrue(String.join(" ",find(s,"round1").detail()).contains("opposite 7 o'clock marker is displaced the same way"));
        // one marker alone out of place (its opposite one in place) is still a clear finding
        picture(r.atHour(7),0.1,0);
        s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"round1").status);
        // opposite displacements (one each way) are two separate deviations, not a shared photo shift
        picture(r.atHour(7),-px,0);
        s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"round7").status);
    }

    @Test public void aRoundWhoseSizeMovesWithItsPositionIsGlareNotClear(){
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);double R=r.dialRadiusPx;
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))m.radiusErrPx=0.001*R;
        picture(r.atHour(4),0.012*R,0);
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.CLEAR,find(s,"round4").status);
        r.atHour(4).radiusErrPx=0.008*R;                                               // 0.7% larger as well
        s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"round4").status);
        assertTrue(String.join(" ",find(s,"round4").detail()).contains("Glare or a bright edge"));
    }

    @Test public void aPhotoBelowFullResolutionCoverageGetsNoClearRoundFinding(){
        Alpha94MarkerMeasurement.Report big=report(6,0,9,0);
        Alpha94MarkerMeasurement.Report r=new Alpha94MarkerMeasurement.Report(big.markers,big.ring,160,big.triangle);
        assertTrue(TestModels.gmtRef().usesShrunkRows("rounds_off",160));
        assertFalse(TestModels.gmtRef().usesShrunkRows("rounds_off",250));
        picture(r.atHour(4),0.012*160,0);
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(Alpha99Findings.Status.WORTH,find(s,"round4").status);
        assertTrue(String.join(" ",find(s,"round4").detail()).contains("This photo is small"));
    }

    @Test public void resolutionLimitedRoundsGetNoOverviewBadge(){
        Alpha94MarkerMeasurement.Report r=report(6,0,9,0);
        Alpha94MarkerMeasurement.Report small=new Alpha94MarkerMeasurement.Report(r.markers,r.ring,100,r.triangle);   // too few comparable genuine watches
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();c.get(6).clean=false;c.get(6).reason=Alpha99MarkerInterference.HAND;
        Alpha99Findings.Summary s=Alpha99Findings.build(small,null,c,TestModels.gmt(),TestModels.gmtRef());
        Alpha99Findings.Finding round=find(s,"round2");
        assertEquals(Alpha99Findings.Status.NOT_ASSESSED,round.status);assertEquals("resolution too low",round.shortReason);
        assertFalse(Alpha99Overview.hasBadge(round));
        assertTrue(Alpha99Overview.hasBadge(find(s,"six")));                               // hand: marker-specific, keeps its dash
        assertTrue(Alpha99Overview.hasBadge(find(s,"nine")));
        assertTrue(s.notAssessedLine().contains("round markers (resolution too low)"));
        assertEquals(4,s.notAssessedCount());                                               // 6, the round-marker group (once), 12 and date (not in this test)
    }

    // ---------------------------------------------------------------- no new findings vs Alpha98 on the recorded rows
    @Test public void neverOutsideWhereAlpha98WasWithin()throws Exception{
        List<Map<String,String>> rows=new ArrayList<>(csv("results/runner_local.csv"));rows.addAll(csv("results/priority_genuine_runner.csv"));
        int n=0;
        for(Map<String,String> row:rows){
            if(!"accepted".equals(row.get("status")))continue;
            Alpha94MarkerMeasurement.Report r=Alpha98FindingsTest.report(row);
            Alpha98Findings.Summary old=Alpha98Findings.build(r,null,TestModels.gmt(),TestModels.gmtRef());
            Alpha99Findings.Summary now=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
            for(Alpha99Findings.Finding f:now.all){
                if(f.status!=Alpha99Findings.Status.CLEAR&&f.status!=Alpha99Findings.Status.WORTH)continue;
                if(Alpha99Findings.outsideOnlyByNewMeasures(f))continue;                // Alpha101 checks have no Alpha98 counterpart
                String key=f.key.startsWith("round")?"rounds":f.key;
                Alpha98Findings.Finding o=null;for(Alpha98Findings.Finding x:old.all)if(x.key.equals(key))o=x;
                assertNotNull(o);
                assertEquals(row.get("photo_id")+" "+f.key,Alpha98Findings.Status.OUTSIDE,o.status);
            }
            // and every Alpha98 finding is still outside (clear or worth a look) when nothing interferes
            for(Alpha98Findings.Finding o:old.outside()){
                boolean any=false;
                // Alpha103: still listed - as clear, worth a look, or too small to see
                for(Alpha99Findings.Finding f:now.all)if((f.key.startsWith("round")?"rounds":f.key).equals(o.key)
                        &&(f.status==Alpha99Findings.Status.CLEAR||f.status==Alpha99Findings.Status.WORTH||f.status==Alpha99Findings.Status.MINOR))any=true;
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
            Alpha99Findings.Summary s=Alpha99Findings.build(Alpha98FindingsTest.report(row),null,allClean(),TestModels.gmt(),TestModels.gmtRef());
            List<String> texts=new ArrayList<>(s.headlineLines());texts.add(s.withinLine());texts.add(s.notAssessedLine());
            for(Alpha99Findings.Finding f:s.all){texts.add(f.text());texts.add(f.shortLine());texts.addAll(f.detail());}
            for(String t:texts)for(String w:new String[]{"PASS","FAIL","fake","Fake","replica","Replica","counterfeit","authentic watch"})
                assertFalse(row.get("photo_id")+": "+t,t.contains(w));
            assertEquals(14,s.all.size());                                                  // 12, 6, 9, 8 rounds, round plot size, ring, date
            for(Alpha99Findings.Finding f:s.notAssessed())assertFalse(f.reason.isEmpty());
        }
        assertTrue(Alpha99Findings.DISCLAIMER.contains("not an authenticity verdict"));
        assertEquals("Dial marker ring",Alpha99Findings.ring(null,200,TestModels.gmtRef()).title);
    }

    @Test public void headlineCountsByEvidenceStrength(){
        Alpha94MarkerMeasurement.Report r=report(6,1.1+TestModels.gmtRef().nominal("six_rot"),9,-1.6+TestModels.gmtRef().nominal("nine_rot"));
        Map<Integer,Alpha99MarkerInterference.Check> c=allClean();c.get(12).clean=false;c.get(12).reason=Alpha99MarkerInterference.HAND;
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,c,TestModels.gmt(),TestModels.gmtRef());
        assertEquals("1 clear alignment finding: 9",s.headlineLines().get(0));
        assertEquals("1 other measurement is worth a look: 6",s.headlineLines().get(1));
        assertTrue(s.headlineLines().get(2).endsWith("could not be assessed"));
    }

    @Test public void borderlineAloneSuggestsAnotherPhoto(){
        // QC guardrails 4: a worth-a-look reading on its own is not escalated; the headline asks for a repeat instead
        Alpha94MarkerMeasurement.Report r=report(6,1.1+TestModels.gmtRef().nominal("six_rot"),9,TestModels.gmtRef().nominal("nine_rot"));
        Alpha99Findings.Summary s=Alpha99Findings.build(r,null,allClean(),TestModels.gmt(),TestModels.gmtRef());
        assertEquals(0,s.clear().size());
        assertEquals("1 measurement is worth a look: 6",s.headlineLines().get(0));
        assertEquals("Another clean, straight-on photo would show whether it repeats",s.headlineLines().get(1));
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
            double sd=Alpha99MarkerInterference.dist(TestModels.gmt().atHour(12),-y,x);
            if(sd<=0)return sd>-0.012?150:220;
            return 20;
        };
    }
    private static boolean onHand(double x,double y,double deg,double width,double len){
        double a=Math.toRadians(deg),ex=Math.sin(a),ey=-Math.cos(a);double along=x*ex+y*ey,across=-x*ey+y*ex;
        return along>0.05&&along<len&&Math.abs(across)<=width/2;
    }

    @Test public void cleanSyntheticDialPasses(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(-1,0,0,-1),H,TestModels.gmt());
        for(Alpha99MarkerInterference.Check x:c.values())assertTrue(x.hour+": "+x.reason,x.clean);
    }

    @Test public void handAcrossARoundMarkerWithholdsIt(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(62,0.03,190,-1),H,TestModels.gmt());
        assertFalse(c.get(2).clean);assertEquals(Alpha99MarkerInterference.HAND,c.get(2).reason);
        assertTrue(c.get(1).clean);assertTrue(c.get(4).clean);assertTrue(c.get(12).clean);
    }

    @Test public void handPassingClearDoesNotWithhold(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(75,0.03,190,-1),H,TestModels.gmt());
        assertTrue(c.get(2).clean);assertTrue(c.get(4).clean);
    }

    @Test public void secondsHandAlongTheSixWithholdsIt(){
        Map<Integer,Alpha99MarkerInterference.Check> c=Alpha99MarkerInterference.analyse(dial(-1,0,0,185.5),H,TestModels.gmt());
        assertFalse(c.get(6).clean);assertTrue(c.get(6).secondsHand);
        assertTrue(c.get(12).clean);assertTrue(c.get(5).clean);assertTrue(c.get(7).clean);
    }

    @Test public void failsClosedWithoutAnImage(){
        for(Alpha99MarkerInterference.Check x:Alpha99MarkerInterference.analyse((Alpha99MarkerInterference.Sampler)null,H,TestModels.gmt()).values())assertFalse(x.clean);
        for(Alpha99MarkerInterference.Check x:Alpha99MarkerInterference.analyse(dial(-1,0,0,-1),null,TestModels.gmt()).values())assertFalse(x.clean);
    }

    // ---------------------------------------------------------------- helpers
    static Alpha94MarkerMeasurement.Report report(int hA,double rotA,int hB,double rotB){
        List<Alpha94MarkerMeasurement.Marker> ms=new ArrayList<>();
        for(int h:TestModels.gmt().ringHours()){
            Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(h,(h==6||h==9)?"baton":"round");m.spec=TestModels.gmt().atHour(h);
            m.usable=true;m.rotationDeg=h==hA?rotA:h==hB?rotB:0;m.localOffsetPx=0.1;m.localRadialPx=0.1;m.localTangentialPx=0;ms.add(m);
        }
        Alpha94MarkerMeasurement.Ring g=new Alpha94MarkerMeasurement.Ring();g.usable=true;g.rotationDeg=TestModels.gmtRef().nominal("ring_rot");g.shiftPx=0.1;
        Alpha94MarkerMeasurement.Marker t=new Alpha94MarkerMeasurement.Marker(12,"triangle");t.usable=false;t.reason="not part of this test";
        return new Alpha94MarkerMeasurement.Report(ms,g,250,t);
    }
    static Alpha99Findings.Finding find(Alpha99Findings.Summary s,String key){for(Alpha99Findings.Finding f:s.all)if(f.key.equals(key))return f;throw new AssertionError(key);}

    @Test public void onlyACleanOutlineWithOneModestEdgelessArcGetsTheLightingGapFit(){
        Alpha94MarkerMeasurement.Marker m=new Alpha94MarkerMeasurement.Marker(10,"round");
        m.usable=false;m.reason="outline coverage 0.61, 6/8 octants";m.fitScorePx=0.0;m.edgeGapDeg=120;m.edgeGapDirDeg=140;
        assertTrue(Alpha99Pipeline.edgeGapOnly(m));
        m.edgeGapDeg=140;assertFalse(Alpha99Pipeline.edgeGapOnly(m));                      // too much outline missing
        m.edgeGapDeg=120;m.fitScorePx=0.2;assertFalse(Alpha99Pipeline.edgeGapOnly(m));      // outline not clean: hand / glare
        m.fitScorePx=0.0;m.reason="hand/occluder crosses outline (clean 0.40)";assertFalse(Alpha99Pipeline.edgeGapOnly(m));
        m.reason="outline coverage 0.61, 6/8 octants";m.usable=true;assertFalse(Alpha99Pipeline.edgeGapOnly(m));
    }

    /** Alpha105: the 12 at the genuine nominal, moved outward (towards the minute track) by dr R. */
    static Alpha94MarkerMeasurement.Report twelveAt(double dr){
        Alpha94MarkerMeasurement.Report r=report(6,TestModels.gmtRef().nominal("six_rot"),9,TestModels.gmtRef().nominal("nine_rot"));
        ModelReference.Triangle tr=TestModels.gmtRef().triangle;double R=r.dialRadiusPx;
        r.triangle.usable=true;r.triangle.reason="";r.triangle.rotationDeg=tr.nominalRotationDeg;
        r.triangle.leftSideErrDeg=tr.nominalLeftSideDeg;r.triangle.rightSideErrDeg=tr.nominalRightSideDeg;
        r.triangle.localTangentialPx=tr.nominalTangentialR*R;r.triangle.localRadialPx=(tr.nominalRadialR+dr)*R;
        return r;
    }

    @Test public void twelveCloseToTheMinuteTrackIsWorthALookNeverClear(){
        Map<Integer,Alpha99MarkerInterference.Check> clean=allClean();
        assertNotNull(TestModels.gmtRef().triangle.radialSignedR);
        double closest=TestModels.gmtRef().triangle.radialMax(true);
        assertEquals(0.0032,closest,0.0001);                                   // genuine GMT: at most 0.32% closer
        // owner's GMT photo 2026-10-10: 0.71% of the dial closer to the track
        Alpha99Findings.Finding f=find(Alpha99Findings.build(twelveAt(0.0071),null,clean,TestModels.gmt(),TestModels.gmtRef()),"twelve");
        assertEquals(Alpha99Findings.Status.WORTH,f.status);
        assertTrue(f.shortLine(),f.shortLine().startsWith("closer to the minute track by 0.71% of the dial; genuine up to 0.32%"));
        // further out than any genuine watch by a lot: still never clear (lighting moves this edge)
        assertEquals(Alpha99Findings.Status.WORTH,find(Alpha99Findings.build(twelveAt(0.02),null,clean,TestModels.gmt(),TestModels.gmtRef()),"twelve").status);
        // past the genuine range but under 20% of the gap (0.006 R): too small to see (a genuine GMT photo read 0.46%)
        assertEquals(Alpha99Findings.Status.MINOR,find(Alpha99Findings.build(twelveAt(0.0046),null,clean,TestModels.gmt(),TestModels.gmtRef()),"twelve").status);
        // further from the track is never judged (lighting: genuine photos read up to 1.22% further), only noted
        Alpha99Findings.Finding in=find(Alpha99Findings.build(twelveAt(-0.02),null,clean,TestModels.gmt(),TestModels.gmtRef()),"twelve");
        assertEquals(Alpha99Findings.Status.WITHIN,in.status);
        assertTrue(String.join(" ",in.detail()),String.join(" ",in.detail()).contains("further from the minute track"));
        assertEquals(Alpha99Findings.Status.WITHIN,find(Alpha99Findings.build(twelveAt(0.001),null,clean,TestModels.gmt(),TestModels.gmtRef()),"twelve").status);
    }
}
