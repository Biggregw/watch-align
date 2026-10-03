package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sub124060Calibration.decide is the single authority for 124060 metric states, reasons and eligible
 * values. These tests pin its contract: registered code constants only, analyser codes beside every message,
 * no class/partition input, state invariants, the agreed reason precedence (raw-producing order for
 * unavailable, gate order for withheld, secondaries only from conditions already evaluated) and the
 * overlay wording that is projected from it.
 */
public class Sub124060DecisionTest {
    static final Set<String> IDENTICAL_TO_RAW=new HashSet<>(java.util.Arrays.asList(
            "twelve.rotation_deg","twelve.gap_r","twelve.centring_w","baton.3_9_line_offset_r","axis.12_6_line_offset_r"));

    // ------------------------------------------------------------------------------------ reason codes
    /** Every analyser statement that writes a withheld message or dial reason is followed by its code. */
    @Test public void everyAnalyserMessageIsWrittenWithItsCode()throws Exception{
        String src=TestFiles.read(TestFiles.repoFile("android/app/src/main/java/com/watchalign/mobile/Sub124060QcAnalyzer.java"));
        src=src.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("//[^\\n]*","").replaceAll("\"(\\\\.|[^\"\\\\])*\"","\"\"");
        String[] statements=src.split(";");
        String[] fields={"dialReason","twelveWithheld","rotationWithheld","gapWithheld","centringWithheld"};
        int checked=0;
        for(int k=0;k<statements.length;k++){
            for(String f:fields){
                Matcher m=Pattern.compile("res\\."+f+"\\s*=(?!=)").matcher(statements[k]);
                if(!m.find())continue;
                checked++;
                String here=statements[k],next=k+1<statements.length?statements[k+1]:"";
                assertTrue("no "+f+"Code beside: "+here.trim(),here.contains("res."+f+"Code=")||next.contains("res."+f+"Code="));
            }
        }
        assertTrue("message assignments found: "+checked,checked>=20);
    }

    @Test public void everyBatonStatusExceptFoundHasAStatusCode(){
        for(Sub124060QcAnalyzer.Status s:Sub124060QcAnalyzer.Status.values()){
            String code=Sub12Reasons.batonStatusCode(s);
            if(s==Sub124060QcAnalyzer.Status.FOUND)assertNull(code);
            else assertTrue(s.name(),java.util.Arrays.asList(Sub12Reasons.BATON_STATUS_CODES).contains(code));
        }
    }

    // ------------------------------------------------------------------------------------ blindness
    @Test public void decideCannotReceiveClassPartitionRoleOrSeparation(){
        Set<Class<?>> allowed=new HashSet<>(java.util.Arrays.asList(Sub124060QcAnalyzer.Result.class,MeasurementDecisions.LayoutSpec.class));
        int n=0;
        for(Method m:Sub124060Calibration.class.getDeclaredMethods()){
            if(!m.getName().equals("decide"))continue;
            n++;
            for(Class<?> p:m.getParameterTypes())assertTrue(m+" takes "+p,allowed.contains(p));
        }
        assertEquals(2,n);
        Pattern forbidden=Pattern.compile("(?i)class(label)?$|partition|role|genuine|replica|split|holdout|separation|label");
        for(Class<?> c:allowed)for(Field f:c.getDeclaredFields())
            assertFalse(c.getSimpleName()+"."+f.getName(),forbidden.matcher(f.getName()).find());
    }

    @Test public void decisionStateIsNotCarriedBetweenPhotos()throws Exception{
        for(Class<?> c:new Class<?>[]{Sub124060Calibration.class,MeasurementDecisions.class,LayoutCompatibility.class,Sub12Reasons.class,CoreReasons.class})
            for(Field f:c.getDeclaredFields())
                if(Modifier.isStatic(f.getModifiers())&&!f.isSynthetic())assertTrue(c.getSimpleName()+"."+f.getName()+" must be final",Modifier.isFinal(f.getModifiers()));
        List<Long> seeds=new ArrayList<>();for(long s=1;s<=300;s++)seeds.add(s);
        List<String> inOrder=new ArrayList<>();for(long s:seeds)inOrder.add(render(Sub124060Calibration.decide(Sub124060Fixtures.random(s))));
        List<Long> shuffled=new ArrayList<>(seeds);Collections.shuffle(shuffled,new Random(7));
        for(long s:shuffled)assertEquals(inOrder.get((int)(s-1)),render(Sub124060Calibration.decide(Sub124060Fixtures.random(s))));
    }

    // ------------------------------------------------------------------------------------ invariants
    @Test public void analyserLikeDecisionsSatisfyTheContractInvariants()throws Exception{
        Set<String> codes=constants(Sub12Reasons.class,"sub12.");codes.addAll(constants(CoreReasons.class,"core."));
        int accepted=0,withheld=0,unavailable=0;
        for(long seed=1;seed<=20000;seed++){
            Sub124060QcAnalyzer.Result r=Sub124060Fixtures.analyserLike(new Random(seed));
            MeasurementDecisions.Photo p=Sub124060Calibration.decide(r);
            List<String> ids=new ArrayList<>();for(MeasurementDecisions.Stage s:p.stages)ids.add(s.id);
            assertEquals(MeasurementDecisions.PHOTO_STAGES,ids);
            boolean failed=false;
            for(MeasurementDecisions.Stage s:p.stages){
                if(failed)assertEquals(MeasurementDecisions.Outcome.BLOCKED,s.outcome);
                else assertTrue(s.outcome!=MeasurementDecisions.Outcome.BLOCKED);
                if(s.outcome==MeasurementDecisions.Outcome.FAIL||s.outcome==MeasurementDecisions.Outcome.FLAG){
                    assertNotNull(s.reason);registered(codes,s.reason);
                }
                failed|=s.outcome==MeasurementDecisions.Outcome.FAIL;
            }
            List<String> metrics=new ArrayList<>();for(MeasurementDecisions.Metric m:p.metrics)metrics.add(m.id);
            assertEquals(java.util.Arrays.asList(Sub124060Calibration.METRICS),metrics);
            for(MeasurementDecisions.Metric m:p.metrics){
                String at="seed "+seed+" "+m.id;
                switch(m.state){
                    case ACCEPTED:
                        accepted++;
                        assertNull(at,m.reason);assertTrue(at,m.secondary.isEmpty());
                        assertTrue(at,Double.isFinite(m.raw));assertTrue(at,Double.isFinite(m.eligible));
                        assertEquals(at,MeasurementDecisions.STAGE_METRIC_RELIABILITY,m.terminalStage);
                        if(IDENTICAL_TO_RAW.contains(m.id))assertEquals(at,m.raw,m.eligible,0);
                        break;
                    case WITHHELD:
                        withheld++;
                        assertTrue(at,Double.isFinite(m.raw));assertTrue(at,Double.isNaN(m.eligible));
                        assertEquals(at,MeasurementDecisions.STAGE_METRIC_RELIABILITY,m.terminalStage);
                        registered(codes,m.reason);
                        break;
                    default:
                        unavailable++;
                        assertTrue(at,Double.isNaN(m.raw));assertTrue(at,Double.isNaN(m.eligible));
                        registered(codes,m.reason);
                        if(m.photoOrigin){
                            MeasurementDecisions.Stage f=p.failedStage();
                            assertEquals(at,f.id,m.terminalStage);assertTrue(at,f.reason.sameAs(m.reason));assertTrue(at,m.secondary.isEmpty());
                        }else assertTrue(at,p.failedStage()==null);
                }
                for(MeasurementDecisions.Reason s:m.secondary){
                    registered(codes,s);
                    assertFalse(at+" repeats its primary",s.sameAs(m.reason));
                }
                for(int a=0;a<m.secondary.size();a++)for(int b=a+1;b<m.secondary.size();b++)assertFalse(at,m.secondary.get(a).sameAs(m.secondary.get(b)));
            }
            Sub124060Calibration.Assessment a=Sub124060Calibration.project(p,true);
            assertEquals(p.metric("twelve.rotation_deg").accepted(),a.rotationMeasured);
            assertEquals(p.metric("baton.3_9_line_offset_r").accepted(),Double.isFinite(a.baton39LineOffsetR));
            if(p.layout.compatibility==LayoutCompatibility.Result.COMPATIBLE){
                MeasurementDecisions.Observation o=p.layout.observations.get(0);
                assertTrue(o.present&&o.highConfidence);assertEquals("high",p.layout.reliability);
            }else if(p.layout.compatibility!=null)assertEquals(LayoutCompatibility.Result.UNRESOLVED,p.layout.compatibility);
            assertFalse(p.layout.quarantined);
        }
        assertTrue(accepted>1000&&withheld>1000&&unavailable>1000);
    }

    static Set<String> constants(Class<?> c,String prefix)throws Exception{
        Set<String> out=new TreeSet<>();
        for(Field f:c.getDeclaredFields()){
            if(!Modifier.isStatic(f.getModifiers())||f.getType()!=String.class)continue;
            f.setAccessible(true);String v=(String)f.get(null);
            if(v!=null&&v.startsWith(prefix))out.add(v);
        }
        return out;
    }

    static final Set<String> BATON_SUBJECT_CODES=new HashSet<>(java.util.Arrays.asList(
            Sub12Reasons.BATON_NOT_FOUND,Sub12Reasons.BATON_LOW_CONFIDENCE,Sub12Reasons.BATON_HAND,Sub12Reasons.BATON_WRONG_PLACE,
            Sub12Reasons.BATON_GEOMETRY_INCOMPLETE,Sub12Reasons.BATON_RESULT_MISSING,Sub12Reasons.BATON_RESIZE_NOT_REPEATABLE));

    /** A code the Java core defines, never the uncoded fallback, with a baton subject exactly where one belongs. */
    static void registered(Set<String> codes,MeasurementDecisions.Reason r){
        assertNotNull(r);
        assertTrue("unregistered "+r.code,codes.contains(r.code));
        assertFalse("uncoded decision",Sub12Reasons.UNCODED.equals(r.code));
        if(BATON_SUBJECT_CODES.contains(r.code))assertTrue(r.code+" subject "+r.subject,r.subject!=null&&r.subject.matches("^baton:(3|6|9)$"));
        else assertNull(r.code+" takes no subject",r.subject);
    }

    // ------------------------------------------------------------------------------------ precedence
    /** A fully measured, reproducible, analyser-shaped result: every metric accepted. */
    static Sub124060QcAnalyzer.Result clean(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        double cx=500,cy=500,rad=300;
        r.edge=new DialEdgeEllipseFit.Fit(cx,cy,rad,rad,0,170,180,0.6);
        r.dialSource=Sub124060QcAnalyzer.DialSource.AUTO_EDGE_FIT;
        r.frame=new GmtRoundMarkerAnalyzer.DialFrame(cx,cy,rad,rad,0);
        r.dialReproducible=true;
        SubTwelveTriangle.Cand c=new SubTwelveTriangle.Cand();
        c.L=new double[]{490,250};c.R=new double[]{510,250};c.T=new double[]{500,270};c.cx=500.5;c.cy=260;c.outline="outer";
        c.tickAngle=0.1;c.rotationDeg=0.4;c.gapR=0.03;c.centring=0.01;c.widthR=0.07;c.heightR=0.07;
        r.triangle=c;r.tick60=new double[]{500,242};r.tick59=new double[]{483,243};r.tick01=new double[]{517,243};
        r.triangleResizeStable=true;r.rotationResizeStable=r.gapResizeStable=r.centringResizeStable=true;
        r.rotationDeg=c.rotationDeg;r.gapR=c.gapR;r.centringW=c.centring;
        for(GmtSixLandmarkAnalyzer.Position p:Sub124060Layout.BATONS){
            Sub124060QcAnalyzer.Baton b=new Sub124060QcAnalyzer.Baton(p);
            b.result=baton(r.frame,p,true);b.status=Sub124060QcAnalyzer.Status.FOUND;
            r.batons.add(b);
        }
        for(int h:Sub124060Layout.ROUND_HOURS){
            GmtRoundMarkerAnalyzer.Marker m=new GmtRoundMarkerAnalyzer.Marker(h);
            double[] q=r.frame.at(-Math.PI/2+Math.toRadians(30.0*h+0.2*(h%3-1)),0.817);
            m.found=true;m.x=q[0];m.y=q[1];m.radiusPx=25;m.stable=true;
            repeatable(m,true);
            r.rounds.add(new Sub124060QcAnalyzer.Round(m,Sub124060QcAnalyzer.Status.FOUND,""));
        }
        return r;
    }

    static GmtSixLandmarkAnalyzer.Result baton(GmtRoundMarkerAnalyzer.DialFrame f,GmtSixLandmarkAnalyzer.Position p,boolean repeatable){
        double phi=-Math.PI/2+Math.toRadians(p.angleFromTwelveDeg);
        double[] c=f.at(phi,0.80);
        double ux=Math.cos(phi),uy=Math.sin(phi),vx=-uy,vy=ux,len=36,wid=10;
        double[] ol={c[0]+ux*len/2+vx*wid/2,c[1]+uy*len/2+vy*wid/2},or={c[0]+ux*len/2-vx*wid/2,c[1]+uy*len/2-vy*wid/2};
        double[] il={c[0]-ux*len/2+vx*wid/2,c[1]-uy*len/2+vy*wid/2},ir={c[0]-ux*len/2-vx*wid/2,c[1]-uy*len/2-vy*wid/2};
        GmtSixLandmarkAnalyzer.Result b=new GmtSixLandmarkAnalyzer.Result(0.05,0.001,0.1,wid,len,true,
                new GmtSixLandmarkAnalyzer.Geometry(ol,or,il,ir,f.at(phi+0.1,0.86),f.at(phi,0.86),f.at(phi-0.1,0.86),true));
        b.stabilityRun=true;b.stabilitySameEdge=true;
        b.centringMin=0.0;b.centringMax=repeatable?0.02:0.5;b.rotMin=0.0;b.rotMax=0.5;
        assertEquals(repeatable,Sub124060QcAnalyzer.batonRepeatable(b));
        return b;
    }

    static void repeatable(GmtRoundMarkerAnalyzer.Marker m,boolean yes){
        m.stabilityRun=true;m.stabilitySameEdge=true;m.offMin=0.0;m.offMax=yes?0.01:0.1;
        assertEquals(yes,Sub124060QcAnalyzer.roundOffsetRepeatable(m));
    }

    static void withholdTwelve(Sub124060QcAnalyzer.Result r,String message,String code){
        r.twelveWithheld=r.rotationWithheld=r.gapWithheld=r.centringWithheld=message;
        r.twelveWithheldCode=r.rotationWithheldCode=r.gapWithheldCode=r.centringWithheldCode=code;
        r.rotationDeg=r.gapR=r.centringW=Double.NaN;
    }

    static MeasurementDecisions.Metric metric(Sub124060QcAnalyzer.Result r,String id){return Sub124060Calibration.decide(r).metric(id);}

    static void expect(MeasurementDecisions.Metric m,MeasurementDecisions.State state,String code,String subject,String... secondary){
        assertEquals(m.id,state,m.state);
        if(code==null){assertNull(m.reason);}
        else{assertEquals(m.id,code,m.reason.code);assertEquals(m.id,subject,m.reason.subject);}
        List<String> got=new ArrayList<>();for(MeasurementDecisions.Reason s:m.secondary)got.add(s.code+(s.subject==null?"":"@"+s.subject));
        assertEquals(m.id,java.util.Arrays.asList(secondary),got);
    }

    @Test public void aCleanResultIsAcceptedEverywhere(){
        MeasurementDecisions.Photo p=Sub124060Calibration.decide(clean());
        for(MeasurementDecisions.Metric m:p.metrics)expect(m,MeasurementDecisions.State.ACCEPTED,null,null);
        assertEquals(LayoutCompatibility.Result.COMPATIBLE,p.layout.compatibility);
        assertEquals(MeasurementDecisions.Outcome.COMPATIBLE,p.stage(MeasurementDecisions.STAGE_LAYOUT).outcome);
        assertEquals(MeasurementDecisions.Outcome.NOT_APPLICABLE,p.stage(MeasurementDecisions.STAGE_PREFLIGHT).outcome);
        assertEquals(0.4,p.metric("twelve.rotation_deg").eligible,0);
    }

    @Test public void aWithheld12KeepsItsCandidateRawValue(){
        Sub124060QcAnalyzer.Result r=clean();
        r.tooSmall=true;withholdTwelve(r,"the 12 triangle is only 31 px wide in this photo (minimum 34)",Sub12Reasons.TRIANGLE_TOO_SMALL);
        MeasurementDecisions.Metric m=metric(r,"twelve.rotation_deg");
        expect(m,MeasurementDecisions.State.WITHHELD,Sub12Reasons.TRIANGLE_TOO_SMALL,null);
        assertEquals(0.4,m.raw,0);assertTrue(Double.isNaN(m.eligible));
        expect(metric(r,"twelve.gap_r"),MeasurementDecisions.State.WITHHELD,Sub12Reasons.TRIANGLE_TOO_SMALL,null);
        // The 12-6 axis is withheld by the same 12 chain, which follows the dial gate.
        expect(metric(r,"axis.12_6_line_offset_r"),MeasurementDecisions.State.WITHHELD,Sub12Reasons.TRIANGLE_TOO_SMALL,null);
    }

    @Test public void unavailableFollowsRawOrderAndKeepsTheEvaluatedGateAsSecondary(){
        Sub124060QcAnalyzer.Result r=clean();
        r.triangle.tickAngle=Double.NaN;   // no 60-minute tick on the candidate
        r.tooSmall=true;withholdTwelve(r,"the 12 triangle is only 31 px wide in this photo (minimum 34)",Sub12Reasons.TRIANGLE_TOO_SMALL);
        expect(metric(r,"twelve.rotation_deg"),MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.MINUTE_TICK_NOT_FOUND,null,Sub12Reasons.TRIANGLE_TOO_SMALL);
        // Gap and centring still have their candidate values: withheld by the 12 chain.
        expect(metric(r,"twelve.gap_r"),MeasurementDecisions.State.WITHHELD,Sub12Reasons.TRIANGLE_TOO_SMALL,null);
    }

    @Test public void lumeOutlineIsADifferentQuantityNotAWithheldOne(){
        Sub124060QcAnalyzer.Result r=clean();
        r.triangle.outline="inner";r.lumeOutline=true;
        r.gapWithheld=r.centringWithheld="only the inner (lume) outline of the triangle was found";
        r.gapWithheldCode=r.centringWithheldCode=Sub12Reasons.LUME_OUTLINE_ONLY;r.gapR=r.centringW=Double.NaN;
        MeasurementDecisions.Metric gap=metric(r,"twelve.gap_r");
        expect(gap,MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.LUME_OUTLINE_ONLY,null);
        assertTrue(Double.isNaN(gap.raw));
        expect(metric(r,"twelve.rotation_deg"),MeasurementDecisions.State.ACCEPTED,null,null);
    }

    @Test public void noTriangleIsUnavailableWithNothingElseEvaluatedForTheTwelve(){
        Sub124060QcAnalyzer.Result r=clean();
        r.triangle=null;r.tick60=r.tick59=r.tick01=null;r.triangleReason="no triangle-shaped outline near 12";
        withholdTwelve(r,"12 triangle not found",Sub12Reasons.TRIANGLE_NOT_FOUND);
        expect(metric(r,"twelve.rotation_deg"),MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.TRIANGLE_NOT_FOUND,null);
        expect(metric(r,"axis.12_6_line_offset_r"),MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.TRIANGLE_NOT_FOUND,null);
        expect(metric(r,"round.spacing_rms_deg"),MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.TRIANGLE_NOT_FOUND,null);
        expect(metric(r,"round.ring_rho"),MeasurementDecisions.State.ACCEPTED,null,null);
    }

    @Test public void resizeInstabilityIsTheTwelveMetricsOwnGate(){
        Sub124060QcAnalyzer.Result r=clean();
        r.rotationResizeStable=false;r.rotationDeg=Double.NaN;
        r.rotationWithheld="the rotation measurement moves by 1.4 px when the photo is reduced by 6% and 12%";
        r.rotationWithheldCode=Sub12Reasons.ROTATION_RESIZE_UNSTABLE;
        MeasurementDecisions.Metric m=metric(r,"twelve.rotation_deg");
        expect(m,MeasurementDecisions.State.WITHHELD,Sub12Reasons.ROTATION_RESIZE_UNSTABLE,null);
        assertEquals(0.4,m.raw,0);
    }

    @Test public void theDialGateShortCircuitsBatonRepeatability(){
        Sub124060QcAnalyzer.Result r=clean();
        r.dialReproducible=false;
        r.batons.get(0).result=baton(r.frame,GmtSixLandmarkAnalyzer.Position.THREE,false);
        Sub124060QcAnalyzer.Baton nine=r.batons.get(2);nine.status=Sub124060QcAnalyzer.Status.NOT_FOUND;nine.note="9 baton not found";
        // Raw 3-9 needs the 9 baton: unavailable, first in raw order; the dial gate was evaluated and
        // failed; the 3 baton's repeatability was never evaluated, so it is not cited.
        expect(metric(r,"baton.3_9_line_offset_r"),MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.BATON_NOT_FOUND,"baton:9",
                Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE);
        expect(metric(r,"round.ring_rho"),MeasurementDecisions.State.WITHHELD,Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE,null);
        MeasurementDecisions.Photo p=Sub124060Calibration.decide(r);
        assertEquals(MeasurementDecisions.Outcome.FLAG,p.stage(MeasurementDecisions.STAGE_GEOMETRY).outcome);
        assertEquals(Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE,p.stage(MeasurementDecisions.STAGE_GEOMETRY).reason.code);
    }

    @Test public void bothBatonsAreJudgedSoBothRepeatabilityFailuresAreCited(){
        Sub124060QcAnalyzer.Result r=clean();
        r.batons.get(0).result=baton(r.frame,GmtSixLandmarkAnalyzer.Position.THREE,false);
        r.batons.get(2).result=baton(r.frame,GmtSixLandmarkAnalyzer.Position.NINE,false);
        MeasurementDecisions.Metric m=metric(r,"baton.3_9_line_offset_r");
        expect(m,MeasurementDecisions.State.WITHHELD,Sub12Reasons.BATON_RESIZE_NOT_REPEATABLE,"baton:3",Sub12Reasons.BATON_RESIZE_NOT_REPEATABLE+"@baton:9");
        assertEquals(Boolean.FALSE,m.diagnostics.get("baton_3_repeatable"));
        assertTrue(Double.isFinite(m.raw));
    }

    @Test public void theTwelveChainPrecedesTheSixBaton(){
        Sub124060QcAnalyzer.Result r=clean();
        r.handAtTwelve=true;withholdTwelve(r,"a hand is touching or right beside the 12 triangle, which can shift its measured outline",Sub12Reasons.HAND_AT_TWELVE);
        r.batons.get(1).result=baton(r.frame,GmtSixLandmarkAnalyzer.Position.SIX,false);
        expect(metric(r,"axis.12_6_line_offset_r"),MeasurementDecisions.State.WITHHELD,Sub12Reasons.HAND_AT_TWELVE,null,
                Sub12Reasons.BATON_RESIZE_NOT_REPEATABLE+"@baton:6");
    }

    @Test public void roundMarkersDistinguishTooFewFromNotRepeatable(){
        Sub124060QcAnalyzer.Result r=clean();
        for(int i=0;i<5;i++)repeatable(r.rounds.get(i).marker,false);
        expect(metric(r,"round.ring_rho"),MeasurementDecisions.State.WITHHELD,Sub12Reasons.ROUND_MARKERS_NOT_REPEATABLE,null);
        MeasurementDecisions.Metric ring=metric(r,"round.ring_rho");
        assertEquals(8,ring.rawSupport.get("markers"));assertEquals(3,ring.eligibleSupport.get("markers"));
        Sub124060QcAnalyzer.Result few=clean();
        for(int i=0;i<5;i++)few.rounds.get(i).status=Sub124060QcAnalyzer.Status.HAND;
        expect(metric(few,"round.ring_rho"),MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.ROUND_MARKERS_INSUFFICIENT,null);
        assertEquals(5,metric(few,"round.ring_rho").diagnostics.get("markers_hand"));
    }

    @Test public void aRegionFailureEndsEveryMetricAtTheRegionStage(){
        Sub124060QcAnalyzer.Result r=new Sub124060QcAnalyzer.Result();
        r.dialReason="the dial edge could not be fitted automatically";r.dialReasonCode=Sub12Reasons.DIAL_EDGE_FIT_FAILED;
        MeasurementDecisions.Photo p=Sub124060Calibration.decide(r);
        assertEquals(MeasurementDecisions.Outcome.FAIL,p.stage(MeasurementDecisions.STAGE_REGION).outcome);
        assertEquals(MeasurementDecisions.Outcome.BLOCKED,p.stage(MeasurementDecisions.STAGE_GEOMETRY).outcome);
        assertEquals(MeasurementDecisions.Outcome.BLOCKED,p.stage(MeasurementDecisions.STAGE_LAYOUT).outcome);
        assertNull(p.layout.compatibility);
        for(MeasurementDecisions.Metric m:p.metrics){
            expect(m,MeasurementDecisions.State.UNAVAILABLE,Sub12Reasons.DIAL_EDGE_FIT_FAILED,null);
            assertTrue(m.photoOrigin);assertEquals(MeasurementDecisions.STAGE_REGION,m.terminalStage);
        }
        MeasurementDecisions.Photo none=Sub124060Calibration.decide(null);
        assertEquals(MeasurementDecisions.Outcome.FAIL,none.stage(MeasurementDecisions.STAGE_READABLE).outcome);
        assertEquals(7,none.metrics.size());
    }

    /** An analysis that failed closed after the frame keeps today's eligibility; everything else cites the failure. */
    @Test public void aLateAnalysisFailureKeepsWhatWasAdmittedAndCitesTheFailure(){
        Sub124060QcAnalyzer.Result r=clean();
        r.dialReason="analysis failed closed: ArrayIndexOutOfBoundsException";r.dialReasonCode=CoreReasons.ANALYSIS_EXCEPTION;
        r.dialSource=Sub124060QcAnalyzer.DialSource.UNAVAILABLE;r.triangle=null;
        MeasurementDecisions.Photo p=Sub124060Calibration.decide(r);
        assertEquals(MeasurementDecisions.Outcome.PASS,p.stage(MeasurementDecisions.STAGE_REGION).outcome);
        assertEquals(CoreReasons.ANALYSIS_EXCEPTION,p.stage(MeasurementDecisions.STAGE_GEOMETRY).reason.code);
        assertEquals(LayoutCompatibility.Result.UNRESOLVED,p.layout.compatibility);
        expect(p.metric("twelve.rotation_deg"),MeasurementDecisions.State.ACCEPTED,null,null);
        expect(p.metric("axis.12_6_line_offset_r"),MeasurementDecisions.State.UNAVAILABLE,CoreReasons.ANALYSIS_EXCEPTION,null);
        assertTrue(Sub124060Calibration.assess(r).rotationMeasured);
    }

    // ------------------------------------------------------------------------------------ layout
    @Test public void absenceIsNeverLayoutEvidence(){
        MeasurementDecisions.Observation none=new MeasurementDecisions.Observation("f",false,true,"d",1,
                Collections.singletonList("a"),Collections.singletonList("b"));
        assertFalse(none.decides());
        assertEquals(LayoutCompatibility.Result.UNRESOLVED,LayoutCompatibility.resolve("a",LayoutCompatibility.hypotheses(java.util.Arrays.asList("a","b"),Collections.singletonList(none))));
        MeasurementDecisions.Observation low=new MeasurementDecisions.Observation("g",true,false,"d",1,
                Collections.singletonList("b"),Collections.singletonList("a"));
        assertEquals(LayoutCompatibility.Result.UNRESOLVED,LayoutCompatibility.resolve("a",LayoutCompatibility.hypotheses(java.util.Arrays.asList("a","b"),Collections.singletonList(low))));
        MeasurementDecisions.Observation yes=new MeasurementDecisions.Observation("h",true,true,"d",1,
                Collections.singletonList("a"),Collections.singletonList("b"));
        MeasurementDecisions.Observation other=new MeasurementDecisions.Observation("k",true,true,"d",1,
                Collections.singletonList("b"),Collections.singletonList("a"));
        List<String> layouts=java.util.Arrays.asList("a","b");
        assertEquals(LayoutCompatibility.Result.COMPATIBLE,LayoutCompatibility.resolve("a",LayoutCompatibility.hypotheses(layouts,Collections.singletonList(yes))));
        assertEquals(LayoutCompatibility.Result.INCOMPATIBLE,LayoutCompatibility.resolve("a",LayoutCompatibility.hypotheses(layouts,Collections.singletonList(other))));
        LayoutCompatibility.Result both=LayoutCompatibility.resolve("a",LayoutCompatibility.hypotheses(layouts,java.util.Arrays.asList(yes,other)));
        assertEquals(LayoutCompatibility.Result.CONFLICTING,both);
        assertTrue(LayoutCompatibility.quarantined(both));
    }

    @Test public void aThreeBatonNotFoundLeavesTheLayoutUnresolved(){
        Sub124060QcAnalyzer.Result r=clean();
        r.batons.get(0).status=Sub124060QcAnalyzer.Status.LOW_CONFIDENCE;
        MeasurementDecisions.Photo p=Sub124060Calibration.decide(r);
        assertEquals(LayoutCompatibility.Result.UNRESOLVED,p.layout.compatibility);
        assertFalse(p.layout.observations.get(0).present);
        assertEquals("low",p.layout.reliability);
    }

    // ------------------------------------------------------------------------------------ overlay wording
    static Sub124060Overlay.Baton drawn(Sub124060Overlay.Drawing d,String label){
        for(Sub124060Overlay.Baton b:d.batons)if(b.label.equals(label))return b;
        throw new AssertionError(label);
    }

    @Test public void unjudgedBatonWordsAreProjectedFromThePairDecision(){
        Sub124060QcAnalyzer.Result r=clean();
        Sub124060QcAnalyzer.Baton nine=r.batons.get(2);nine.status=Sub124060QcAnalyzer.Status.HAND;nine.note="a hand is next to it";
        Sub124060Overlay.Drawing d=Sub124060Overlay.Drawing.of(r);
        Sub124060Overlay.Baton three=drawn(d,"3");
        assertEquals("needs the 9 baton",three.note);
        assertEquals(Sub12Reasons.BATON_HAND,three.reason.code);assertEquals("baton:9",three.reason.subject);

        Sub124060QcAnalyzer.Result unsteady=clean();
        unsteady.dialReproducible=false;
        Sub124060Overlay.Drawing u=Sub124060Overlay.Drawing.of(unsteady);
        // Visible words unchanged ("reading not steady enough"); the authoritative reason is the dial edge.
        assertEquals("reading not steady enough",drawn(u,"3").note);
        assertEquals(Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE,drawn(u,"3").reason.code);
        assertEquals("reading not steady enough",drawn(u,"6").note);
        assertEquals(Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE,drawn(u,"6").reason.code);

        Sub124060Overlay.Drawing ok=Sub124060Overlay.Drawing.of(clean());
        assertNull(drawn(ok,"3").reason);assertNull(drawn(ok,"3").note);
    }

    // ------------------------------------------------------------------------------------ rendering
    /** Deterministic text of a decision, for order and runtime comparisons. */
    static String render(MeasurementDecisions.Photo p){
        StringBuilder s=new StringBuilder();
        for(MeasurementDecisions.Stage st:p.stages){
            s.append("stage ").append(st.id).append(' ').append(st.outcome.wire).append(' ').append(reason(st.reason)).append(' ').append(st.diagnostics).append('\n');
        }
        s.append("layout ").append(p.layout.expected.layoutId).append(' ').append(p.layout.compatibility==null?"-":p.layout.compatibility.wire)
                .append(' ').append(p.layout.reliability).append(' ').append(p.layout.quarantined);
        for(MeasurementDecisions.Observation o:p.layout.observations)s.append(' ').append(o.feature).append(o.present?"+":"-").append(o.highConfidence?"high":"low");
        for(MeasurementDecisions.Hypothesis h:p.layout.hypotheses)s.append(' ').append(h.layoutId).append('=').append(h.state).append(h.decidedBy);
        s.append('\n');
        for(MeasurementDecisions.Metric m:p.metrics){
            s.append(m.id).append(' ').append(m.state.wire).append(' ').append(m.terminalStage).append(m.photoOrigin?" photo":" metric")
                    .append(" raw=").append(m.raw).append(" eligible=").append(m.eligible).append(' ').append(reason(m.reason)).append(" [");
            for(MeasurementDecisions.Reason x:m.secondary)s.append(reason(x)).append(';');
            s.append("] ").append(m.rawSupport).append(m.eligibleSupport).append(m.diagnostics).append('\n');
        }
        return s.toString();
    }
    static String reason(MeasurementDecisions.Reason r){return r==null?"-":r.code+(r.subject==null?"":"@"+r.subject)+(r.detail==null?"":"("+r.detail+")");}
}
