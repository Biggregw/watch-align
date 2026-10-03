package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Family-agnostic measured-only decision types (measurement contract v2).
 *
 * A watch family's shared core (for the 124060: Sub124060Calibration.decide) is the only author of
 * these decisions. The Android app, the desktop harness and calibration all read the same objects;
 * none of them re-derives a state, a reason or a value. Nothing here knows a dial shape, a marker,
 * a layout or what a metric means: those live in the family core and its catalogues.
 *
 * A metric decision always ends in exactly one state:
 *  - UNAVAILABLE: no meaningful raw measurement exists (raw NaN, eligible NaN, reason required);
 *  - WITHHELD: a raw measurement exists but the reliability policy does not admit it;
 *  - ACCEPTED: the eligible value is what calibration and verdicts may use (no reason).
 */
final class MeasurementDecisions {
    private MeasurementDecisions(){}

    /** Core photo stages owned by the shared core, in canonical order (0 and 1 are upstream). */
    static final String STAGE_READABLE="readable",STAGE_PREFLIGHT="preflight",STAGE_REGION="region",
            STAGE_GEOMETRY="geometry",STAGE_LAYOUT="layout";
    static final List<String> PHOTO_STAGES=Collections.unmodifiableList(java.util.Arrays.asList(
            STAGE_READABLE,STAGE_PREFLIGHT,STAGE_REGION,STAGE_GEOMETRY,STAGE_LAYOUT));
    /** Core metric stages: a metric ends at one of these unless a photo stage failed first. */
    static final String STAGE_METRIC_RAW="metric.raw",STAGE_METRIC_RELIABILITY="metric.reliability";

    enum State {
        ACCEPTED("accepted"),WITHHELD("withheld"),UNAVAILABLE("unavailable");
        final String wire;
        State(String w){wire=w;}
    }

    /** Photo stage outcomes. The layout stage reports its compatibility result as the outcome. */
    enum Outcome {
        PASS("pass"),FLAG("flag"),FAIL("fail"),NOT_APPLICABLE("not_applicable"),BLOCKED("blocked"),
        COMPATIBLE("compatible"),UNRESOLVED("unresolved"),INCOMPATIBLE("incompatible"),CONFLICTING("conflicting");
        final String wire;
        Outcome(String w){wire=w;}
    }

    /** A registered reason code, an optional family token for the failing part (e.g. baton:9) and optional human text. */
    static final class Reason {
        final String code,subject,detail;
        Reason(String code,String subject,String detail){
            if(code==null||code.isEmpty())throw new IllegalArgumentException("reason code is required");
            this.code=code;this.subject=subject;this.detail=detail;
        }
        boolean sameAs(Reason o){return o!=null&&code.equals(o.code)&&Objects.equals(subject,o.subject);}
    }

    static final class Metric {
        final String id;
        State state=State.UNAVAILABLE;
        String terminalStage=STAGE_METRIC_RAW;
        /** True when the metric only inherits a failed photo stage. */
        boolean photoOrigin;
        double raw=Double.NaN,eligible=Double.NaN;
        Reason reason;
        /** Other failures the normal execution path already evaluated, in evaluation order; never filled by extra checks. */
        final List<Reason> secondary=new ArrayList<>();
        final Map<String,Object> rawSupport=new LinkedHashMap<>(),eligibleSupport=new LinkedHashMap<>(),diagnostics=new LinkedHashMap<>();
        Metric(String id){this.id=id;}
        boolean accepted(){return state==State.ACCEPTED;}
        /** Whether the primary reason or a secondary reason has this code and subject. */
        boolean cites(String code,String subject){
            if(reason!=null&&reason.code.equals(code)&&Objects.equals(reason.subject,subject))return true;
            for(Reason s:secondary)if(s.code.equals(code)&&Objects.equals(s.subject,subject))return true;
            return false;
        }
    }

    static final class Stage {
        final String id;
        Outcome outcome;
        Reason reason;
        final Map<String,Object> diagnostics=new LinkedHashMap<>();
        Stage(String id,Outcome outcome,Reason reason){this.id=id;this.outcome=outcome;this.reason=reason;}
    }

    /** The layout a model claims, as an opaque family layout ID and version. */
    static final class LayoutSpec {
        final String layoutId;final int version;
        LayoutSpec(String layoutId,int version){this.layoutId=layoutId;this.version=version;}
    }

    /** One family detector finding. A not_detected finding never decides a layout. */
    static final class Observation {
        final String feature;final boolean present,highConfidence;
        final String detectorId;final int detectorVersion;
        final List<String> supports,contradicts;
        Observation(String feature,boolean present,boolean highConfidence,String detectorId,int detectorVersion,
                    List<String> supports,List<String> contradicts){
            this.feature=feature;this.present=present;this.highConfidence=present&&highConfidence;
            this.detectorId=detectorId;this.detectorVersion=detectorVersion;
            this.supports=Collections.unmodifiableList(new ArrayList<>(supports));
            this.contradicts=Collections.unmodifiableList(new ArrayList<>(contradicts));
        }
        /** Only a confident positive detection can support or contradict a layout. */
        boolean decides(){return present&&highConfidence;}
    }

    static final class Hypothesis {
        static final String SUPPORTED="supported",CONTRADICTED="contradicted",UNKNOWN="unknown";
        final String layoutId,state;final List<String> decidedBy;
        Hypothesis(String layoutId,String state,List<String> decidedBy){
            this.layoutId=layoutId;this.state=state;this.decidedBy=Collections.unmodifiableList(new ArrayList<>(decidedBy));
        }
    }

    static final class Layout {
        final LayoutSpec expected;
        final List<Observation> observations=new ArrayList<>();
        final List<Hypothesis> hypotheses=new ArrayList<>();
        /** Null when the layout stage was blocked by an earlier photo failure. */
        LayoutCompatibility.Result compatibility;
        /** high when every deciding observation is high confidence, else low. */
        String reliability="low";
        boolean quarantined;
        Layout(LayoutSpec expected){this.expected=expected;}
    }

    /** Every decision the shared core makes for one photo. */
    static final class Photo {
        final List<Stage> stages=new ArrayList<>();
        Layout layout;
        final List<Metric> metrics=new ArrayList<>();

        Stage stage(String id){for(Stage s:stages)if(s.id.equals(id))return s;return null;}
        Metric metric(String id){
            for(Metric m:metrics)if(m.id.equals(id))return m;
            throw new IllegalArgumentException("no decision for metric "+id);
        }
        /** The first photo stage that failed, or null. */
        Stage failedStage(){for(Stage s:stages)if(s.outcome==Outcome.FAIL)return s;return null;}
    }

    /**
     * A photo whose input never reached the family analysis (unreadable image, analysis crash before
     * a result existed). Every later stage is blocked and every metric inherits the input failure.
     */
    static Photo inputFailure(String code,String detail,LayoutSpec expected,String... metricIds){
        Photo p=new Photo();
        Reason r=new Reason(code,null,detail);
        p.stages.add(new Stage(STAGE_READABLE,Outcome.FAIL,r));
        for(String s:PHOTO_STAGES)if(!s.equals(STAGE_READABLE))p.stages.add(new Stage(s,Outcome.BLOCKED,null));
        p.layout=new Layout(expected);
        for(String id:metricIds)p.metrics.add(inherited(id,STAGE_READABLE,r));
        return p;
    }

    /** A metric that ends unavailable because a photo stage failed: same stage, same reason, photo origin. */
    static Metric inherited(String id,String stage,Reason photoReason){
        Metric m=new Metric(id);
        m.state=State.UNAVAILABLE;m.terminalStage=stage;m.photoOrigin=true;m.reason=photoReason;
        return m;
    }
}
