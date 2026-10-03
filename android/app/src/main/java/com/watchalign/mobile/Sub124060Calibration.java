package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Conservative family-specific alignment calibration for the Rolex Submariner 124060.
 *
 * Source: Watch-family Calibrator run 37054338303 (2026-10-02). The run acquired 40 genuine
 * 124060 watches from six sources and fitted the production envelope from every reliable genuine
 * photo measurement across development, validation and holdout. No cross-watch statistical
 * trimming is used. Only an obvious photo-level failure within the same physical watch may be
 * removed; this run rejected zero such measurements for all seven metrics.
 *
 * Product rule: a reliable value already observed on a genuine watch is accepted genuine variation
 * for replica QC, including a small genuine imperfection. CHECK begins only beyond the retained
 * genuine envelope plus the repeatability guard. Replica evidence never moves a genuine-derived
 * boundary. The six current replica stress watches did not separate on these seven metrics, so the
 * bands are deliberately conservative rather than claims of authenticity or Rolex tolerances.
 */
final class Sub124060Calibration {
    /** Single switch: no CLEAR/CHECK/CHECK CLOSELY verdict reaches the user while this is false. */
    static final boolean VERDICTS_ENABLED=true;
    static final String MEASURED_ONLY="MEASURED / NOT YET JUDGED";
    static final class Band {
        final String key;
        final double clearLow,clearHigh,checkLow,checkHigh;
        Band(String key,double clearLow,double clearHigh,double checkLow,double checkHigh){
            this.key=key;this.clearLow=clearLow;this.clearHigh=clearHigh;this.checkLow=checkLow;this.checkHigh=checkHigh;
        }
        GmtHumanQcMath.Attention judge(double v){
            if(!Double.isFinite(v))return GmtHumanQcMath.Attention.UNASSESSABLE;
            if(v>=clearLow&&v<=clearHigh)return GmtHumanQcMath.Attention.CLEAR;
            if(v>=checkLow&&v<=checkHigh)return GmtHumanQcMath.Attention.CHECK;
            return GmtHumanQcMath.Attention.STRONG;
        }
    }

    static final class Assessment {
        GmtHumanQcMath.Attention rotation=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention centring=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention roundRing=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention roundSpacing=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention baton39=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention axis126=GmtHumanQcMath.Attention.UNASSESSABLE;
        double roundRingRho=Double.NaN,roundSpacingRmsDeg=Double.NaN;
        double baton39LineOffsetR=Double.NaN,axis126LineOffsetR=Double.NaN;
        /** A reliable value exists (passed the production gates), whether or not it was judged. */
        boolean rotationMeasured,gapMeasured,centringMeasured,roundRingMeasured,roundSpacingMeasured,baton39Measured,axis126Measured;

        boolean twelveMeasured(){return rotationMeasured||gapMeasured||centringMeasured||axis126Measured;}
        boolean roundsMeasured(){return roundRingMeasured||roundSpacingMeasured;}
        boolean batonMeasured(String label){
            if("3".equals(label)||"9".equals(label))return baton39Measured;
            if("6".equals(label))return axis126Measured;
            return false;
        }

        GmtHumanQcMath.Attention twelve(){return worst(rotation,gap,centring,axis126);}
        GmtHumanQcMath.Attention rounds(){return worst(roundRing,roundSpacing);}
        GmtHumanQcMath.Attention baton(String label){
            if("3".equals(label)||"9".equals(label))return baton39;
            if("6".equals(label))return axis126;
            return GmtHumanQcMath.Attention.UNASSESSABLE;
        }
    }

    // Run 37054338303, all-reliable-genuine-photo envelope. CLEAR includes every retained genuine
    // observation plus one repeatability guard. CHECK is the next guard-width outside CLEAR.
    static final Band TWELVE_ROTATION=new Band("twelve.rotation_deg",
            -1.438497,3.555616,-1.638497,3.755616);
    static final boolean GAP_JUDGED=true;
    static final boolean RING_JUDGED=true;
    static final Band TWELVE_GAP=new Band("twelve.gap_r",
            0.0155867,0.0463993,0.0131864,0.0487996);
    static final Band TWELVE_CENTRING=new Band("twelve.centring_w",
            -0.080044,0.050926,-0.090044,0.060926);
    static final Band ROUND_RING_RHO=new Band("round.ring_rho",
            0.784912,0.827718,0.782912,0.829718);
    /** One-sided: an RMS cannot be negative, so only an upper warning boundary exists. */
    static final Band ROUND_SPACING_RMS=new Band("round.spacing_rms_deg",
            Double.NEGATIVE_INFINITY,4.033474,Double.NEGATIVE_INFINITY,4.133474);
    /** One-sided: an absolute offset cannot be negative. */
    static final Band BATON_3_9_LINE_OFFSET=new Band("baton.3_9_line_offset_r",
            Double.NEGATIVE_INFINITY,0.023287,Double.NEGATIVE_INFINITY,0.025287);
    /** One-sided: an absolute offset cannot be negative. */
    static final Band AXIS_12_6_LINE_OFFSET=new Band("axis.12_6_line_offset_r",
            Double.NEGATIVE_INFINITY,0.014611,Double.NEGATIVE_INFINITY,0.016611);

    private Sub124060Calibration(){}

    static String words(GmtHumanQcMath.Attention a){
        switch(a){
            case CLEAR:return "CLEAR";
            case CHECK:return "CHECK";
            case STRONG:return "CHECK CLOSELY";
            default:return "NOT JUDGED";
        }
    }

    /** User-facing word for a metric: the verdict if judged, otherwise whether a reliable value exists. */
    static String label(boolean measured,GmtHumanQcMath.Attention a){
        if(a!=null&&a!=GmtHumanQcMath.Attention.UNASSESSABLE)return words(a);
        return measured?MEASURED_ONLY:"NOT JUDGED";
    }

    static GmtHumanQcMath.Attention worst(GmtHumanQcMath.Attention... values){
        boolean any=false;
        GmtHumanQcMath.Attention out=GmtHumanQcMath.Attention.UNASSESSABLE;
        for(GmtHumanQcMath.Attention v:values){
            if(v==null||v==GmtHumanQcMath.Attention.UNASSESSABLE)continue;
            any=true;
            if(v==GmtHumanQcMath.Attention.STRONG)return v;
            if(v==GmtHumanQcMath.Attention.CHECK)out=v;
            else if(out==GmtHumanQcMath.Attention.UNASSESSABLE)out=GmtHumanQcMath.Attention.CLEAR;
        }
        return any?out:GmtHumanQcMath.Attention.UNASSESSABLE;
    }

    // ------------------------------------------------------------------------------------------ decisions
    /** The seven calibrated 124060 metrics, in production-route order. */
    static final String[] METRICS={TWELVE_ROTATION.key,TWELVE_GAP.key,TWELVE_CENTRING.key,
            ROUND_RING_RHO.key,ROUND_SPACING_RMS.key,BATON_3_9_LINE_OFFSET.key,AXIS_12_6_LINE_OFFSET.key};
    /** 12-series candidate layouts: no date (124060) and a date at 3 (126610LN/LV). */
    static final String LAYOUT_NO_DATE="sub12.no_date_v1",LAYOUT_DATE="sub12.date_v1";
    static final String OBS_BATON_AT_3="sub12.baton_at_3";
    static final String BATON_DETECTOR_ID="sub12.baton_status";
    static final int BATON_DETECTOR_VERSION=1;

    /** Evaluate only measurements that survive the existing production reliability gates. */
    static Assessment assess(Sub124060QcAnalyzer.Result r){return assess(r,VERDICTS_ENABLED);}

    /** judge=false computes the same gated values but leaves every verdict UNASSESSABLE. */
    static Assessment assess(Sub124060QcAnalyzer.Result r,boolean judge){return project(decide(r),judge);}

    /**
     * Mechanical projection of the authoritative decisions onto the app's assessment: a metric is
     * measured exactly when its decision is accepted, its value is the eligible value, and a verdict
     * is the frozen band applied to that value. No reliability condition lives here.
     */
    static Assessment project(MeasurementDecisions.Photo d,boolean judge){
        Assessment a=new Assessment();
        MeasurementDecisions.Metric m=d.metric(TWELVE_ROTATION.key);
        a.rotationMeasured=m.accepted();a.rotation=verdict(m,TWELVE_ROTATION,judge);
        m=d.metric(TWELVE_GAP.key);
        a.gapMeasured=m.accepted();a.gap=verdict(m,TWELVE_GAP,judge&&GAP_JUDGED);
        m=d.metric(TWELVE_CENTRING.key);
        a.centringMeasured=m.accepted();a.centring=verdict(m,TWELVE_CENTRING,judge);
        m=d.metric(ROUND_RING_RHO.key);
        a.roundRingMeasured=m.accepted();a.roundRingRho=value(m);a.roundRing=verdict(m,ROUND_RING_RHO,judge&&RING_JUDGED);
        m=d.metric(ROUND_SPACING_RMS.key);
        a.roundSpacingMeasured=m.accepted();a.roundSpacingRmsDeg=value(m);a.roundSpacing=verdict(m,ROUND_SPACING_RMS,judge);
        m=d.metric(BATON_3_9_LINE_OFFSET.key);
        a.baton39Measured=m.accepted();a.baton39LineOffsetR=value(m);a.baton39=verdict(m,BATON_3_9_LINE_OFFSET,judge);
        m=d.metric(AXIS_12_6_LINE_OFFSET.key);
        a.axis126Measured=m.accepted();a.axis126LineOffsetR=value(m);a.axis126=verdict(m,AXIS_12_6_LINE_OFFSET,judge);
        return a;
    }

    private static double value(MeasurementDecisions.Metric m){return m.accepted()?m.eligible:Double.NaN;}
    private static GmtHumanQcMath.Attention verdict(MeasurementDecisions.Metric m,Band band,boolean judge){
        return judge&&band!=null&&m.accepted()?band.judge(m.eligible):GmtHumanQcMath.Attention.UNASSESSABLE;
    }

    /** The metric whose decision explains a measured baton's verdict: 3 and 9 as a pair, 6 with the 12. */
    static String batonMetric(String label){
        if("3".equals(label)||"9".equals(label))return BATON_3_9_LINE_OFFSET.key;
        if("6".equals(label))return AXIS_12_6_LINE_OFFSET.key;
        return null;
    }

    static MeasurementDecisions.Photo decide(Sub124060QcAnalyzer.Result r){return decide(r,Sub124060Layout.EXPECTED_LAYOUT);}

    /**
     * The authoritative per-metric decision for one analysed photo: the only implementation of the
     * 124060 raw metrics, reliability conditions, their order, the state, the reason and the eligible
     * value. Android (through project), the desktop harness and calibration all consume it.
     *
     * It depends only on the analyser result and the claimed layout. It reads conditions the analyser
     * already recorded and runs no detector or resize check; it evaluates the same pure predicates
     * (batonRepeatable, roundOffsetRepeatable) at the same points as the assessment always has.
     * Acceptance and every eligible value are exactly the production gates':
     *  - 12 rotation, gap, centring: the gated analyser value, its withheld message clear, resize stable;
     *  - round ring and spacing: a reproducible edge-fitted dial and at least four markers whose
     *    centre offset repeats under resize (the eligible value is recomputed on those markers);
     *  - 3-9 and 12-6 axes: a reproducible edge-fitted dial, FOUND batons whose resize repeats, and for
     *    12-6 a 12 triangle with no withheld reason.
     * The primary reason of an unavailable metric is the first failure in raw-producing order; of a
     * withheld metric, the first failure in gate order. Secondary reasons are other failures those two
     * paths evaluated anyway; no check runs to collect them.
     */
    static MeasurementDecisions.Photo decide(Sub124060QcAnalyzer.Result r,MeasurementDecisions.LayoutSpec expected){
        if(r==null)return MeasurementDecisions.inputFailure(CoreReasons.ANALYSIS_EXCEPTION,"no analysis result",expected,METRICS);
        MeasurementDecisions.Photo p=new MeasurementDecisions.Photo();
        // Photo-level failures exist only where the analyser withdrew the dial (it pairs these codes with
        // DialSource.UNAVAILABLE). An analysis that failed closed after the dial frame existed keeps what
        // it measured before the failure: the region was found, so the failure is a geometry flag that
        // every gate cites.
        boolean noDialSource=r.dialSource==Sub124060QcAnalyzer.DialSource.UNAVAILABLE;
        boolean lateFailure=noDialSource&&CoreReasons.ANALYSIS_EXCEPTION.equals(r.dialReasonCode)&&r.frame!=null;
        MeasurementDecisions.Reason late=lateFailure?reason(CoreReasons.ANALYSIS_EXCEPTION,null,r.dialReason):null;

        boolean unreadable=noDialSource&&CoreReasons.IMAGE_UNREADABLE.equals(r.dialReasonCode);
        addStage(p,MeasurementDecisions.STAGE_READABLE,unreadable?MeasurementDecisions.Outcome.FAIL:MeasurementDecisions.Outcome.PASS,
                unreadable?reason(CoreReasons.IMAGE_UNREADABLE,null,r.dialReason):null);
        addStage(p,MeasurementDecisions.STAGE_PREFLIGHT,MeasurementDecisions.Outcome.NOT_APPLICABLE,reason(CoreReasons.STAGE_NOT_APPLICABLE,null,null));
        boolean noDial=noDialSource&&!lateFailure;
        MeasurementDecisions.Stage region=addStage(p,MeasurementDecisions.STAGE_REGION,noDial?MeasurementDecisions.Outcome.FAIL:MeasurementDecisions.Outcome.PASS,
                noDial?reason(r.dialReasonCode,null,r.dialReason):null);
        region.diagnostics.put("dial_source",region.outcome==MeasurementDecisions.Outcome.BLOCKED?null:r.dialSource.name());
        MeasurementDecisions.Reason geometryFlag=lateFailure?late
                :r.dialSource==Sub124060QcAnalyzer.DialSource.MANUAL_CIRCLE?reason(Sub12Reasons.DIAL_MANUAL_CIRCLE,null,null)
                :r.edge!=null&&!Boolean.TRUE.equals(r.dialReproducible)?reason(Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE,null,r.dialReproNote)
                :null;
        MeasurementDecisions.Stage geometry=addStage(p,MeasurementDecisions.STAGE_GEOMETRY,
                geometryFlag!=null?MeasurementDecisions.Outcome.FLAG:MeasurementDecisions.Outcome.PASS,geometryFlag);
        if(geometry.outcome!=MeasurementDecisions.Outcome.BLOCKED){
            geometry.diagnostics.put("dial_reproducible",r.dialReproducible);
            geometry.diagnostics.put("pose_tilt_deg",r.markerPose!=null&&r.markerPose.valid?finiteOrNull(r.markerPose.tiltDeg):null);
        }
        boolean blocked=p.failedStage()!=null;
        p.layout=layoutEvidence(r,expected,blocked);
        addStage(p,MeasurementDecisions.STAGE_LAYOUT,blocked?MeasurementDecisions.Outcome.BLOCKED:layoutOutcome(p.layout.compatibility),null);

        MeasurementDecisions.Stage failed=p.failedStage();
        // Raw 12 values come from the selected triangle candidate through the analyser's own definitions.
        SubTwelveTriangle.Cand c=r.triangle;
        String rotationFailure=Sub124060QcAnalyzer.rotationFailure(c),trackFailure=Sub124060QcAnalyzer.minuteTrackFailure(c,r.lumeOutline);
        p.metrics.add(twelve(TWELVE_ROTATION.key,r,r.rotationDeg,r.rotationWithheld,r.rotationWithheldCode,r.rotationResizeStable,
                rotationFailure,rotationFailure==null?c.rotationDeg:Double.NaN,Sub12Reasons.ROTATION_RESIZE_UNSTABLE,r.rotationShiftPx,failed,late));
        p.metrics.add(twelve(TWELVE_GAP.key,r,r.gapR,r.gapWithheld,r.gapWithheldCode,r.gapResizeStable,
                trackFailure,trackFailure==null?c.gapR:Double.NaN,Sub12Reasons.GAP_RESIZE_UNSTABLE,r.gapShiftPx,failed,late));
        p.metrics.add(twelve(TWELVE_CENTRING.key,r,r.centringW,r.centringWithheld,r.centringWithheldCode,r.centringResizeStable,
                trackFailure,trackFailure==null?c.centring:Double.NaN,Sub12Reasons.CENTRING_RESIZE_UNSTABLE,r.centringShiftPx,failed,late));

        // Relational metrics, evaluated in the order the assessment always used: dial gate, round
        // markers, the 3-9 pair, then the 12-6 axis.
        MeasurementDecisions.Reason dial=dialGate(r);
        rounds(p,r,dial,failed,late);
        baton39(p,r,dial,failed,late);
        axis126(p,r,dial,failed,late);
        return p;
    }

    /** A metric the 12 triangle measures: raw is the gated value, or the candidate value before any gate. */
    private static MeasurementDecisions.Metric twelve(String id,Sub124060QcAnalyzer.Result r,double gated,String withheld,String withheldCode,
                                                     Boolean resizeStable,String candidateFailure,double candidate,String resizeCode,double shiftPx,
                                                     MeasurementDecisions.Stage failed,MeasurementDecisions.Reason late){
        MeasurementDecisions.Metric m=new MeasurementDecisions.Metric(id);
        m.raw=Double.isFinite(gated)?gated:candidate;
        m.diagnostics.put("resize_shift_px",finiteOrNull(shiftPx));
        m.diagnostics.put("resize_stable",resizeStable);
        Failures raw=new Failures(),gate=new Failures();
        if(!Double.isFinite(m.raw))raw.add(candidateFailure,null,
                Sub12Reasons.TRIANGLE_NOT_FOUND.equals(candidateFailure)?triangleDetail(r):candidateFailure!=null&&candidateFailure.equals(withheldCode)?withheld:null);
        boolean accepted=false;
        if(withheld!=null)gate.add(withheldCode,null,withheld);
        else if(!Double.isFinite(gated))gate.add(Sub12Reasons.UNCODED,null,null);
        else if(!Boolean.TRUE.equals(resizeStable))gate.add(resizeCode,null,null);
        else accepted=true;
        return finish(m,accepted,gated,raw,gate,failed,late);
    }

    private static void rounds(MeasurementDecisions.Photo p,Sub124060QcAnalyzer.Result r,MeasurementDecisions.Reason dial,
                               MeasurementDecisions.Stage failed,MeasurementDecisions.Reason late){
        MeasurementDecisions.Metric ring=new MeasurementDecisions.Metric(ROUND_RING_RHO.key),spacing=new MeasurementDecisions.Metric(ROUND_SPACING_RMS.key);
        Failures ringRaw=new Failures(),ringGate=new Failures(),spacingRaw=new Failures(),spacingGate=new Failures();
        for(MeasurementDecisions.Metric m:new MeasurementDecisions.Metric[]{ring,spacing}){
            m.rawSupport.put("markers",null);m.eligibleSupport.put("markers",null);markerDiagnostics(m,r);
        }

        // Raw: every FOUND marker, before the resize-repeatability filter.
        if(r.frame==null){ringRaw.add(Sub12Reasons.UNCODED,null,"no dial frame");spacingRaw.add(Sub12Reasons.UNCODED,null,"no dial frame");}
        else{
            List<Double> rho=new ArrayList<>();
            for(Sub124060QcAnalyzer.Round q:r.rounds){
                GmtRoundMarkerAnalyzer.Marker m=q.marker;
                if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found)continue;
                double[] pt=rectNorm(r.frame,m.x,m.y);
                if(pt!=null)rho.add(Math.hypot(pt[0],pt[1]));
            }
            ring.rawSupport.put("markers",rho.size());
            if(rho.size()>=4)ring.raw=median(rho);else ringRaw.add(Sub12Reasons.ROUND_MARKERS_INSUFFICIENT,null,null);

            double ref=rectClock(r.frame,r.tick60);
            if(!Double.isFinite(ref))spacingRaw.list.add(twelveReference(r));
            else{
                List<Double> errors=new ArrayList<>();
                for(Sub124060QcAnalyzer.Round q:r.rounds){
                    GmtRoundMarkerAnalyzer.Marker m=q.marker;
                    if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found)continue;
                    errors.add(wrap180(rectClock(r.frame,m.x,m.y)-ref-m.hour*30.0));
                }
                spacing.rawSupport.put("markers",errors.size());
                double v=errors.size()>=4?spacingRms(errors):Double.NaN;
                if(Double.isFinite(v))spacing.raw=v;else spacingRaw.add(Sub12Reasons.ROUND_MARKERS_INSUFFICIENT,null,null);
            }
        }

        // Reliability: a reproducible edge-fitted dial, then markers whose centre offset repeats under resize.
        double ringEligible=Double.NaN,spacingEligible=Double.NaN;
        boolean ringAccepted=false,spacingAccepted=false;
        if(dial!=null){ringGate.list.add(dial);spacingGate.list.add(dial);}
        else{
            double ref12=rectClock(r.frame,r.tick60);
            List<Double> rho=new ArrayList<>(),angleErrors=new ArrayList<>();
            for(Sub124060QcAnalyzer.Round q:r.rounds){
                GmtRoundMarkerAnalyzer.Marker m=q.marker;
                if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found||!Sub124060QcAnalyzer.roundOffsetRepeatable(m))continue;
                double[] pt=rectNorm(r.frame,m.x,m.y);
                if(pt==null)continue;
                rho.add(Math.hypot(pt[0],pt[1]));
                if(Double.isFinite(ref12))angleErrors.add(wrap180(rectClock(r.frame,m.x,m.y)-ref12-m.hour*30.0));
            }
            ring.eligibleSupport.put("markers",rho.size());
            spacing.eligibleSupport.put("markers",angleErrors.size());
            if(rho.size()>=4){ringEligible=median(rho);ringAccepted=true;}
            else ringGate.add(Double.isFinite(ring.raw)?Sub12Reasons.ROUND_MARKERS_NOT_REPEATABLE:Sub12Reasons.ROUND_MARKERS_INSUFFICIENT,null,null);
            if(angleErrors.size()>=4)spacingEligible=spacingRms(angleErrors);
            if(Double.isFinite(spacingEligible))spacingAccepted=true;
            else if(!Double.isFinite(ref12))spacingGate.list.add(twelveReference(r));
            else spacingGate.add(Double.isFinite(spacing.raw)?Sub12Reasons.ROUND_MARKERS_NOT_REPEATABLE:Sub12Reasons.ROUND_MARKERS_INSUFFICIENT,null,null);
        }
        p.metrics.add(finish(ring,ringAccepted,ringEligible,ringRaw,ringGate,failed,late));
        p.metrics.add(finish(spacing,spacingAccepted,spacingEligible,spacingRaw,spacingGate,failed,late));
    }

    private static void baton39(MeasurementDecisions.Photo p,Sub124060QcAnalyzer.Result r,MeasurementDecisions.Reason dial,
                                MeasurementDecisions.Stage failed,MeasurementDecisions.Reason late){
        MeasurementDecisions.Metric m=new MeasurementDecisions.Metric(BATON_3_9_LINE_OFFSET.key);
        Sub124060QcAnalyzer.Baton b3=baton(r,GmtSixLandmarkAnalyzer.Position.THREE),b9=baton(r,GmtSixLandmarkAnalyzer.Position.NINE);
        m.diagnostics.put("baton_3_status",b3==null?null:String.valueOf(b3.status));
        m.diagnostics.put("baton_9_status",b9==null?null:String.valueOf(b9.status));
        m.diagnostics.put("baton_3_repeatable",null);
        m.diagnostics.put("baton_9_repeatable",null);
        Failures raw=new Failures(),gate=new Failures();

        // Raw: the 3 and 9 baton geometry with no repeatability filter.
        double[] r3=rawBatonPoint(r,b3,"3",raw),r9=rawBatonPoint(r,b9,"9",raw);
        if(r3!=null&&r9!=null){
            m.raw=lineOffset(r3,r9);
            if(!Double.isFinite(m.raw))raw.add(Sub12Reasons.RELATION_DEGENERATE,null,null);
        }

        double eligible=Double.NaN;boolean accepted=false;
        if(dial!=null)gate.list.add(dial);
        else{
            double[] p3=gatedBatonPoint(r.frame,b3,"3",gate,m),p9=gatedBatonPoint(r.frame,b9,"9",gate,m);
            if(p3!=null&&p9!=null){
                eligible=lineOffset(p3,p9);
                if(Double.isFinite(eligible))accepted=true;else gate.add(Sub12Reasons.RELATION_DEGENERATE,null,null);
            }
        }
        p.metrics.add(finish(m,accepted,eligible,raw,gate,failed,late));
    }

    private static void axis126(MeasurementDecisions.Photo p,Sub124060QcAnalyzer.Result r,MeasurementDecisions.Reason dial,
                                MeasurementDecisions.Stage failed,MeasurementDecisions.Reason late){
        MeasurementDecisions.Metric m=new MeasurementDecisions.Metric(AXIS_12_6_LINE_OFFSET.key);
        Sub124060QcAnalyzer.Baton b6=baton(r,GmtSixLandmarkAnalyzer.Position.SIX);
        m.diagnostics.put("baton_6_status",b6==null?null:String.valueOf(b6.status));
        m.diagnostics.put("baton_6_repeatable",null);
        m.diagnostics.put("twelve_withheld",r.twelveWithheld!=null);
        Failures raw=new Failures(),gate=new Failures();

        // Raw: the triangle centre (whatever the 12 chain decided) and the 6 baton geometry.
        if(r.frame==null)raw.add(Sub12Reasons.UNCODED,null,"no dial frame");
        else if(r.triangle==null)raw.add(Sub12Reasons.TRIANGLE_NOT_FOUND,null,triangleDetail(r));
        else{
            double[] p12=rectNorm(r.frame,r.triangle.cx,r.triangle.cy);
            if(p12==null)raw.add(Sub12Reasons.RELATION_DEGENERATE,null,null);
            double[] r6=rawBatonPoint(r,b6,"6",raw);
            if(p12!=null&&r6!=null){
                m.raw=lineOffset(p12,r6);
                if(!Double.isFinite(m.raw))raw.add(Sub12Reasons.RELATION_DEGENERATE,null,null);
            }
        }

        double eligible=Double.NaN;boolean accepted=false;
        if(dial!=null)gate.list.add(dial);
        else{
            double[] p12=null;
            if(r.triangle==null)gate.add(Sub12Reasons.TRIANGLE_NOT_FOUND,null,triangleDetail(r));
            else if(r.twelveWithheld!=null)gate.add(r.twelveWithheldCode,null,r.twelveWithheld);
            else{
                p12=rectNorm(r.frame,r.triangle.cx,r.triangle.cy);
                if(p12==null)gate.add(Sub12Reasons.RELATION_DEGENERATE,null,null);
            }
            double[] p6=gatedBatonPoint(r.frame,b6,"6",gate,m);
            if(p12!=null&&p6!=null){
                eligible=lineOffset(p12,p6);
                if(Double.isFinite(eligible))accepted=true;else gate.add(Sub12Reasons.RELATION_DEGENERATE,null,null);
            }
        }
        p.metrics.add(finish(m,accepted,eligible,raw,gate,failed,late));
    }

    /** Raw baton centre: a FOUND, valid baton with a complete outer-edge outline; no repeatability filter. */
    private static double[] rawBatonPoint(Sub124060QcAnalyzer.Result r,Sub124060QcAnalyzer.Baton b,String label,Failures out){
        String subject=Sub12Reasons.batonSubject(label);
        if(r.frame==null){out.add(Sub12Reasons.UNCODED,subject,"no dial frame");return null;}
        if(b==null){out.add(Sub12Reasons.BATON_RESULT_MISSING,subject,null);return null;}
        if(b.status!=Sub124060QcAnalyzer.Status.FOUND){out.add(Sub12Reasons.batonStatusCode(b.status),subject,emptyToNull(b.note));return null;}
        if(b.result==null||!b.result.valid){out.add(Sub12Reasons.BATON_GEOMETRY_INCOMPLETE,subject,null);return null;}
        double[] pt=batonCentre(r.frame,b.result.geometry);
        if(pt==null)out.add(Sub12Reasons.BATON_GEOMETRY_INCOMPLETE,subject,null);
        return pt;
    }

    /** Gated baton centre: the raw conditions plus resize repeatability, in the assessment's order. */
    private static double[] gatedBatonPoint(GmtRoundMarkerAnalyzer.DialFrame f,Sub124060QcAnalyzer.Baton b,String label,Failures out,
                                            MeasurementDecisions.Metric diagnostics){
        String subject=Sub12Reasons.batonSubject(label);
        if(b==null){out.add(Sub12Reasons.BATON_RESULT_MISSING,subject,null);return null;}
        if(b.status!=Sub124060QcAnalyzer.Status.FOUND){out.add(Sub12Reasons.batonStatusCode(b.status),subject,emptyToNull(b.note));return null;}
        if(b.result==null||!b.result.valid){out.add(Sub12Reasons.BATON_GEOMETRY_INCOMPLETE,subject,null);return null;}
        boolean repeatable=Sub124060QcAnalyzer.batonRepeatable(b.result);
        diagnostics.diagnostics.put("baton_"+label+"_repeatable",repeatable);
        if(!repeatable){out.add(Sub12Reasons.BATON_RESIZE_NOT_REPEATABLE,subject,emptyToNull(b.note));return null;}
        double[] pt=batonCentre(f,b.result.geometry);
        if(pt==null)out.add(Sub12Reasons.BATON_GEOMETRY_INCOMPLETE,subject,null);
        return pt;
    }

    /** Baton centre: mean of the four outer-edge outline corners, ellipse-corrected and in dial radii. */
    static double[] batonCentre(GmtRoundMarkerAnalyzer.DialFrame f,GmtSixLandmarkAnalyzer.Geometry g){
        if(g==null||!g.outerEdge||g.outerLeft==null||g.outerRight==null||g.innerLeft==null||g.innerRight==null)return null;
        double x=(g.outerLeft[0]+g.outerRight[0]+g.innerLeft[0]+g.innerRight[0])/4.0;
        double y=(g.outerLeft[1]+g.outerRight[1]+g.innerLeft[1]+g.innerRight[1])/4.0;
        return rectNorm(f,x,y);
    }

    /** Relational calibration was fitted on ellipse-corrected coordinates of a reproducible edge-fitted dial. */
    private static MeasurementDecisions.Reason dialGate(Sub124060QcAnalyzer.Result r){
        if(r.frame==null)return reason(Sub12Reasons.UNCODED,null,"no dial frame");
        if(r.edge==null)return reason(Sub12Reasons.DIAL_MANUAL_CIRCLE,null,null);
        if(!Boolean.TRUE.equals(r.dialReproducible))return reason(Sub12Reasons.DIAL_EDGE_NOT_REPRODUCIBLE,null,emptyToNull(r.dialReproNote));
        return null;
    }

    /** Why no 12 reference direction exists for the marker spacing: no triangle, or no 60-minute tick on it. */
    private static MeasurementDecisions.Reason twelveReference(Sub124060QcAnalyzer.Result r){
        if(r.tick60!=null)return reason(Sub12Reasons.RELATION_DEGENERATE,null,null);
        return r.triangle==null?reason(Sub12Reasons.TRIANGLE_NOT_FOUND,null,triangleDetail(r)):reason(Sub12Reasons.MINUTE_TICK_NOT_FOUND,null,null);
    }

    private static void markerDiagnostics(MeasurementDecisions.Metric m,Sub124060QcAnalyzer.Result r){
        int found=0,hand=0,low=0,missing=0;
        for(Sub124060QcAnalyzer.Round q:r.rounds){
            if(q.status==Sub124060QcAnalyzer.Status.FOUND)found++;
            else if(q.status==Sub124060QcAnalyzer.Status.HAND)hand++;
            else if(q.status==Sub124060QcAnalyzer.Status.LOW_CONFIDENCE)low++;
            else missing++;
        }
        m.diagnostics.put("markers_found",found);
        m.diagnostics.put("markers_hand",hand);
        m.diagnostics.put("markers_low_confidence",low);
        m.diagnostics.put("markers_not_found",missing);
    }

    private static MeasurementDecisions.Layout layoutEvidence(Sub124060QcAnalyzer.Result r,MeasurementDecisions.LayoutSpec expected,boolean blocked){
        MeasurementDecisions.Layout l=new MeasurementDecisions.Layout(expected);
        if(blocked)return l;
        Sub124060QcAnalyzer.Baton b3=baton(r,GmtSixLandmarkAnalyzer.Position.THREE);
        // FOUND already requires a located 12, the angle check, no hand and a stable detection. A
        // photo whose analysis failed closed afterwards keeps the finding at low confidence.
        boolean present=b3!=null&&b3.status==Sub124060QcAnalyzer.Status.FOUND;
        boolean high=present&&r.triangle!=null&&r.dialAssessable();
        l.observations.add(new MeasurementDecisions.Observation(OBS_BATON_AT_3,present,high,BATON_DETECTOR_ID,BATON_DETECTOR_VERSION,
                Collections.singletonList(LAYOUT_NO_DATE),Collections.singletonList(LAYOUT_DATE)));
        l.hypotheses.addAll(LayoutCompatibility.hypotheses(Arrays.asList(LAYOUT_NO_DATE,LAYOUT_DATE),l.observations));
        l.compatibility=LayoutCompatibility.resolve(expected.layoutId,l.hypotheses);
        l.reliability=l.compatibility==LayoutCompatibility.Result.UNRESOLVED?"low":"high";
        l.quarantined=LayoutCompatibility.quarantined(l.compatibility);
        return l;
    }

    private static MeasurementDecisions.Outcome layoutOutcome(LayoutCompatibility.Result c){
        switch(c){
            case COMPATIBLE:return MeasurementDecisions.Outcome.COMPATIBLE;
            case INCOMPATIBLE:return MeasurementDecisions.Outcome.INCOMPATIBLE;
            case CONFLICTING:return MeasurementDecisions.Outcome.CONFLICTING;
            default:return MeasurementDecisions.Outcome.UNRESOLVED;
        }
    }

    /** Failing conditions in the order one path evaluated them. */
    private static final class Failures {
        final List<MeasurementDecisions.Reason> list=new ArrayList<>();
        void add(String code,String subject,String detail){list.add(reason(code,subject,detail));}
    }

    /**
     * Fix the state from what was decided: accepted when the gates admitted the value; otherwise an
     * inherited photo failure, a late analysis failure, or the path's own first failure.
     */
    private static MeasurementDecisions.Metric finish(MeasurementDecisions.Metric m,boolean accepted,double eligible,Failures raw,Failures gate,
                                                     MeasurementDecisions.Stage failed,MeasurementDecisions.Reason late){
        if(accepted){
            m.state=MeasurementDecisions.State.ACCEPTED;m.eligible=eligible;
            m.terminalStage=MeasurementDecisions.STAGE_METRIC_RELIABILITY;
            return m;
        }
        if(failed!=null)return MeasurementDecisions.inherited(m.id,failed.id,failed.reason);
        boolean rawExists=Double.isFinite(m.raw);
        m.state=rawExists?MeasurementDecisions.State.WITHHELD:MeasurementDecisions.State.UNAVAILABLE;
        m.terminalStage=rawExists?MeasurementDecisions.STAGE_METRIC_RELIABILITY:MeasurementDecisions.STAGE_METRIC_RAW;
        if(late!=null){m.reason=late;return m;}
        List<MeasurementDecisions.Reason> all=new ArrayList<>();
        if(!rawExists)all.addAll(raw.list);
        all.addAll(gate.list);
        m.reason=all.isEmpty()?reason(Sub12Reasons.UNCODED,null,null):all.get(0);
        for(MeasurementDecisions.Reason x:all){
            if(x.sameAs(m.reason))continue;
            boolean seen=false;for(MeasurementDecisions.Reason s:m.secondary)if(s.sameAs(x)){seen=true;break;}
            if(!seen)m.secondary.add(x);
        }
        return m;
    }

    private static MeasurementDecisions.Stage addStage(MeasurementDecisions.Photo p,String id,MeasurementDecisions.Outcome outcome,MeasurementDecisions.Reason why){
        MeasurementDecisions.Stage s=p.failedStage()!=null?new MeasurementDecisions.Stage(id,MeasurementDecisions.Outcome.BLOCKED,null)
                :new MeasurementDecisions.Stage(id,outcome,why);
        p.stages.add(s);
        return s;
    }

    private static MeasurementDecisions.Reason reason(String code,String subject,String detail){
        return new MeasurementDecisions.Reason(code==null?Sub12Reasons.UNCODED:code,subject,emptyToNull(detail));
    }
    private static String triangleDetail(Sub124060QcAnalyzer.Result r){return emptyToNull(r.triangleReason);}
    private static String emptyToNull(String s){return s==null||s.trim().isEmpty()?null:s;}
    private static Double finiteOrNull(double v){return Double.isFinite(v)?v:null;}

    static Sub124060QcAnalyzer.Baton baton(Sub124060QcAnalyzer.Result r,GmtSixLandmarkAnalyzer.Position p){
        for(Sub124060QcAnalyzer.Baton b:r.batons)if(b.position==p)return b;
        return null;
    }

    static double[] rectNorm(GmtRoundMarkerAnalyzer.DialFrame f,double x,double y){
        if(f==null||!(f.r>0)||!Double.isFinite(x)||!Double.isFinite(y))return null;
        double[] p=f.rect(x,y);return new double[]{p[0]/f.r,p[1]/f.r};
    }

    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double[] p){return p==null||p.length<2?Double.NaN:rectClock(f,p[0],p[1]);}
    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double x,double y){
        if(f==null)return Double.NaN;double[] p=f.rect(x,y);return Math.toDegrees(Math.atan2(p[0],-p[1]));
    }
    static double wrap180(double a){while(a<=-180)a+=360;while(a>180)a-=360;return a;}

    static double median(List<Double> values){
        List<Double> x=new ArrayList<>();for(Double v:values)if(v!=null&&Double.isFinite(v))x.add(v);
        if(x.isEmpty())return Double.NaN;Collections.sort(x);int n=x.size();return n%2==1?x.get(n/2):(x.get(n/2-1)+x.get(n/2))/2.0;
    }
    static double spacingRms(List<Double> values){
        List<Double> x=new ArrayList<>();for(Double v:values)if(v!=null&&Double.isFinite(v))x.add(v);
        if(x.size()<4)return Double.NaN;double mean=0;for(double v:x)mean+=v;mean/=x.size();double ss=0;for(double v:x){double d=v-mean;ss+=d*d;}return Math.sqrt(ss/x.size());
    }
    static double lineOffset(double[] p,double[] q){
        if(p==null||q==null||p.length<2||q.length<2)return Double.NaN;
        double dx=q[0]-p[0],dy=q[1]-p[1],d=Math.hypot(dx,dy);if(!(d>0))return Double.NaN;return Math.abs(p[0]*q[1]-q[0]*p[1])/d;
    }
}
