package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Watch Align 1.6.6 (owner, 20 Oct 2026): the whole-watch alignment classification. It reads the finished clearly-off
 * marker results (nothing is measured again) and applies two tolerance standards to the same measurements:
 *   replica QC     the existing answer of each marker (ClearlyOff.decide at the replica-QC limits, the owner's saved
 *                  limits when set): Clearly off, Possible, No visible issue, Can't check
 *   strict genuine the same decide() at the fixed ClearlyOff.Limits.genuine() (pooled genuine Rolex research)
 * A marker is RELIABLE when it was measured with no withheld part and no uncertain edge (an uncertain outer-metal fit,
 * a low-resolution photo, a gap placed from the rest of the outline all count as unreliable). Classification:
 *   REPLICA_QC_ISSUES  any marker Clearly off at the replica-QC limits (that state is only ever given to reliable readings)
 *   REPLICA            the model's required markers all present and within the replica-QC limits (No visible issue), and at
 *                      least one RELIABLE marker outside the strict genuine standard
 *   GENUINE            the required markers all present, reliable, within the replica-QC limits and within every strict
 *                      genuine limit
 *   CANT_DETERMINE     anything else: the check failed, a marker is missing, can't be checked, possible (uncertain over a
 *                      limit), or an uncertain reading leaves the genuine standard undecided
 * Each marker is judged on its own; nothing is averaged. These are alignment-based screening classifications, never an
 * authenticity finding.
 */
final class WatchClassification {
    private WatchClassification(){}

    enum Verdict{GENUINE,REPLICA,REPLICA_QC_ISSUES,CANT_DETERMINE}

    static final String NOT_PROOF="Alignment-based prediction only. This result does not prove authenticity. Genuine watches can fail, and accurately aligned replicas can pass.";

    /** one marker against both standards */
    static final class MarkerCheck {
        final int hour;final ModelSpec.Shape shape;
        /** the marker's measured result (replica-QC answer), null when the check produced none for this hour */
        final ClearlyOff.Marker reading;
        /** the same measurements judged at the strict genuine limits (a copy; the reading is never changed) */
        final ClearlyOff.Marker strict;
        final boolean reliable;
        MarkerCheck(int hour,ModelSpec.Shape shape,ClearlyOff.Marker reading,ClearlyOff.Marker strict,boolean reliable){
            this.hour=hour;this.shape=shape;this.reading=reading;this.strict=strict;this.reliable=reliable;}
        ClearlyOff.State qc(){return reading==null?ClearlyOff.State.CANT_CHECK:reading.state;}
        /** within the replica-QC limits (its existing answer is No visible issue) */
        boolean qcPass(){return qc()==ClearlyOff.State.NO_VISIBLE_ISSUE;}
        /** outside the replica-QC limits on a reliable reading */
        boolean qcFail(){return qc()==ClearlyOff.State.CLEARLY_OFF;}
        /** reliably within every strict genuine limit */
        boolean genuinePass(){return reliable&&strict!=null&&strict.state==ClearlyOff.State.NO_VISIBLE_ISSUE;}
        /** reliably outside a strict genuine limit */
        boolean genuineFail(){return reliable&&strict!=null&&strict.state==ClearlyOff.State.CLEARLY_OFF;}
        /** the genuine standard could not be decided for this marker */
        boolean genuineUndecided(){return !genuinePass()&&!genuineFail();}
    }

    static final class Result {
        Verdict verdict;
        final List<MarkerCheck> markers=new ArrayList<>();
        /** the analysis failure (technical details), or null */
        String failure;
        int required;
        MarkerCheck at(int h){for(MarkerCheck m:markers)if(m.hour==h)return m;return null;}
        String title(){return WatchClassification.title(verdict);}
        String message(){return WatchClassification.message(verdict);}
    }

    static String title(Verdict v){
        switch(v){case GENUINE:return "Genuine";case REPLICA:return "Replica";case REPLICA_QC_ISSUES:return "Replica — QC Issues";default:return "Can't Determine";}
    }
    static String message(Verdict v){
        switch(v){
            case GENUINE:return "All hour markers meet our strict genuine Rolex alignment standard.";
            case REPLICA:return "One or more markers fall outside genuine Rolex alignment tolerances. Try another perfectly straight-on photograph to verify the result.";
            case REPLICA_QC_ISSUES:return "One or more markers show significant alignment deviations.";
            default:return "We couldn't reliably assess every required marker. Try another clear, straight-on photograph.";
        }
    }

    /** a reading with nothing withheld and no uncertain edge */
    static boolean reliable(ClearlyOff.Marker m){
        if(m==null||m.state==ClearlyOff.State.CANT_CHECK||!m.gate.isEmpty())return false;
        if(m.uncertain!=null&&!m.uncertain.isEmpty())return false;
        if(m.shape==ModelSpec.Shape.TRIANGLE&&m.gapFromFit)return false;   // the gap was placed from the rest of the outline
        return m.measured();
    }

    /** the measurements of m judged at the strict genuine standard (a copy) */
    static ClearlyOff.Marker strict(ClearlyOff.Marker m,double rpx){
        if(m==null)return null;
        ClearlyOff.Marker s=m.copy();
        ClearlyOff.decide(s,rpx,null,ClearlyOff.Limits.genuine());
        return s;
    }

    /**
     * @param r       the finished clearly-off result (after any photo-wide reason has been applied), or null when the check
     *                failed on this photo
     * @param model   the dial: its markers are the required hours (12 without a date, 11 with one; the date window is not
     *                a marker)
     * @param failure why the check failed (technical details), or null
     */
    static Result classify(ClearlyOff.Result r,ModelSpec model,String failure){
        Result out=new Result();out.failure=failure;
        if(model==null){out.verdict=Verdict.CANT_DETERMINE;return out;}
        out.required=model.markers.size();
        for(ModelSpec.Marker mk:model.markers){
            ClearlyOff.Marker m=r==null?null:r.at(mk.hour);
            out.markers.add(new MarkerCheck(mk.hour,mk.shape,m,r==null?null:strict(m,r.rpx),reliable(m)));
        }
        boolean qcIssue=false,anyStrictFail=false,allQcPass=r!=null,allGenuinePass=r!=null;
        for(MarkerCheck c:out.markers){
            qcIssue|=c.qcFail();
            anyStrictFail|=c.genuineFail();
            allQcPass&=c.qcPass();
            allGenuinePass&=c.genuinePass();
        }
        if(qcIssue)out.verdict=Verdict.REPLICA_QC_ISSUES;
        else if(allQcPass&&anyStrictFail)out.verdict=Verdict.REPLICA;
        else if(allQcPass&&allGenuinePass)out.verdict=Verdict.GENUINE;
        else out.verdict=Verdict.CANT_DETERMINE;
        return out;
    }

    // ------------------------------------------------------------------------------------------------ marker wording

    /** one short line for the marker list: the two standards */
    static String line(MarkerCheck c){
        String g=c.genuinePass()?"meets the genuine standard":c.genuineFail()?"outside the genuine standard ("+String.join(", ",failed(c.strict))+")"
                :"genuine standard not assessed";
        String q=c.qcPass()?"within replica QC":c.qcFail()?"outside replica QC":c.qc()==ClearlyOff.State.POSSIBLE?"replica QC: inspect":"replica QC not assessed";
        return AlignmentDecision.capital(g)+" · "+q+".";
    }

    /** the strict genuine measures a marker is outside */
    static List<String> failed(ClearlyOff.Marker s){
        List<String> f=new ArrayList<>();if(s==null)return f;
        if(s.posOff)f.add("position");if(s.sideOff)f.add("sideways");if(s.inoutOff)f.add("in/out");if(s.tiltOff)f.add("rotation");if(s.gapOff)f.add("gap");
        return f;
    }

    /** every measurement of a marker with both limits (the allowance for this photo included) and whether it passes */
    static List<String> measures(MarkerCheck c){
        List<String> out=new ArrayList<>();
        ClearlyOff.Marker q=c.reading,s=c.strict;
        if(q==null||s==null||!q.measured()){out.add("Not measured on this photo.");return out;}
        if(c.shape==ModelSpec.Shape.TRIANGLE){
            out.add(row("Sideways",Math.abs(q.sidePct),"% R","max",s.sideLimitPct,q.sideLimitPct));
            out.add(row("In / out",Math.abs(q.inoutPct),"% R","max",s.inoutLimitPct,q.inoutLimitPct));
            if(Double.isFinite(q.tiltDeg)||Double.isFinite(s.tiltLimitDeg))out.add(row("Rotation",Math.abs(q.tiltDeg),"°","max",s.tiltLimitDeg,q.tiltLimitDeg));
            out.add(row("Gap under the minute track",q.gapPct,"% R","min",s.gapLimitPct,q.gapLimitPct));
        }else{
            out.add(row("Position",q.posPct,"% R","max",s.posLimitPct,q.posLimitPct));
            if(c.shape==ModelSpec.Shape.BATON)out.add(row("Rotation",Math.abs(q.tiltDeg),"°","max",s.tiltLimitDeg,q.tiltLimitDeg));
        }
        if(!c.reliable)out.add(q.state==ClearlyOff.State.CANT_CHECK?"Not used for the genuine standard: "+q.reason+"."
                :"Not used for the genuine standard: measured from an uncertain edge"+(q.uncertain==null||q.uncertain.isEmpty()?"":" ("+q.uncertain+")")+".");
        return out;
    }
    static String row(String name,double v,String unit,String kind,double genuineLimit,double qcLimit){
        if(!Double.isFinite(v))return name+": not measured.";
        return String.format(Locale.US,"%s %.2f%s · genuine %s %.2f%s %s · replica QC %s %.2f%s %s",name,v,unit,kind,genuineLimit,unit,mark(v,genuineLimit,kind),
                kind,qcLimit,unit,mark(v,qcLimit,kind));
    }
    static String mark(double v,double limit,String kind){
        if(!Double.isFinite(limit))return "(not judged)";
        boolean ok=kind.equals("min")?v>=limit:v<=limit;
        return ok?"✓":"✗";
    }

    /** the strict genuine standard in words (calibration screen, technical details) */
    static String standardText(){
        ClearlyOff.Limits g=ClearlyOff.Limits.genuine();
        return String.format(Locale.US,"Strict genuine standard (fixed; from pooled genuine Rolex research, experimental screening limits, not manufacturer specifications): "
                +"round markers within %.3f%% R of where they should be, batons within %.3f%% R and turned at most %.3f°, the 12 triangle at most %.3f%% R sideways and %.3f%% R in or out, "
                +"turned at most %.3f°, and at least %.3f%% R clear under the minute track. A 0.5-pixel low-resolution allowance is added to each maximum and taken from the gap minimum. "
                +"Every marker must be measured reliably and pass every limit; your adjustable limits below are the separate replica-QC standard.",
                100*g.roundPosR,100*g.batonPosR,g.batonTiltDeg,100*g.triSideR,100*g.triInoutR,g.triTiltDeg,100*g.triGapR);
    }
}
