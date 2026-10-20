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
 * a low-resolution photo, a gap placed from the rest of the outline all count as unreliable). Decision order (owner,
 * 20 Oct 2026: a confirmed failure takes precedence over unrelated unreadable markers):
 *   CANT_DETERMINE     first, a photo-wide failure: the check failed, the dial/pose could not be used (photo-wide reason)
 *   REPLICA_QC_ISSUES  any CREDIBLE reading over the replica-QC limits, even with another marker unreadable
 *   REPLICA            otherwise any CREDIBLE reading outside the strict genuine standard, even with another unreadable
 *                      (owner, 21 Oct 2026: one marker is enough; a soft edge-fit warning does not discard it; credible())
 *   CANT_DETERMINE     otherwise any required marker missing or not reliable
 *   GENUINE            every required marker reliable and within every strict genuine limit
 * Genuine still needs every required marker RELIABLE. A Replica or QC Issues result with some markers not assessed is PARTIAL
 * (the screen warns that more issues may exist).
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
        /** a usable reading that may decide a FAILURE: measured, nothing withheld, not mostly reconstructed, not an
         *  uncertain fit beside a confirmed hand (credible(); a reliable reading is always credible) */
        final boolean credible;
        /** 1.6.6: the strict genuine judgement of an uncertain reading from its clean refit beside a confirmed hand */
        final Alt alt;
        MarkerCheck(int hour,ModelSpec.Shape shape,ClearlyOff.Marker reading,ClearlyOff.Marker strict,boolean reliable){
            this.hour=hour;this.shape=shape;this.reading=reading;this.strict=strict;this.reliable=reliable;
            this.credible=reliable||credible(reading);
            this.alt=reliable?Alt.NONE:alt(reading,strict);}
        ClearlyOff.State qc(){return reading==null?ClearlyOff.State.CANT_CHECK:reading.state;}
        /** within the replica-QC limits (its existing answer is No visible issue) */
        boolean qcPass(){return qc()==ClearlyOff.State.NO_VISIBLE_ISSUE;}
        /** outside the replica-QC limits on a credible reading (Clearly off, or Possible from a slightly imperfect edge) */
        boolean qcFail(){return credible&&!over(reading).isEmpty();}
        /** reliably within every strict genuine limit (or, from the clean refit, by more than its validated uncertainty) */
        boolean genuinePass(){return reliable&&strict!=null&&strict.state==ClearlyOff.State.NO_VISIBLE_ISSUE||alt==Alt.PASS;}
        /** a credible reading outside a strict genuine limit (or, from the clean refit, by more than its validated
         *  uncertainty) */
        boolean genuineFail(){return credible&&!over(strict).isEmpty()||alt==Alt.FAIL;}
        /** the strict genuine standard is decided for this marker, from a reliable reading or the validated refit */
        boolean assessed(){return reliable||alt==Alt.PASS||alt==Alt.FAIL;}
        /** the genuine standard could not be decided for this marker */
        boolean genuineUndecided(){return !genuinePass()&&!genuineFail();}
    }

    static final class Result {
        Verdict verdict;
        final List<MarkerCheck> markers=new ArrayList<>();
        /** the analysis failure (technical details), or null */
        String failure;
        int required;
        /** a Replica / QC Issues result from partial coverage: some required markers could not be assessed */
        boolean partial;
        MarkerCheck at(int h){for(MarkerCheck m:markers)if(m.hour==h)return m;return null;}
        String title(){return WatchClassification.title(verdict);}
        String message(){return WatchClassification.message(verdict);}
    }

    static final String PARTIAL="Some markers could not be assessed on this photo, so additional QC issues may exist.";

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

    /** 1.6.6 strict genuine judgement from the clean refit (MetalFit.Fit.alt) of an uncertain baton beside a confirmed hand */
    enum Alt{NONE,PASS,FAIL,UNDECIDED}

    /**
     * Only for a baton whose own reading is uncertain but within the replica-QC limits (No visible issue; over a limit it
     * stays "Possible, inspect" and undecided), nothing withheld, a clean refit without the side beside the hand, and that
     * refit within MetalFit.ALT_LUME_AGREE_R of the independently measured luminous centre.
     * With U the one-side uncertainty measured on the verified genuine photos (MetalFit.ALT_U_POS_R, ALT_U_ROT_DEG):
     *   PASS       |position| + U and |rotation| + U both inside the strict limits (this photo's allowance included)
     *   FAIL       |position| - U or |rotation| - U outside a strict limit
     *   UNDECIDED  the uncertainty could cross a limit (Can't Determine)
     */
    static Alt alt(ClearlyOff.Marker m,ClearlyOff.Marker strict){
        if(m==null||strict==null||m.shape!=ModelSpec.Shape.BATON||m.altSide<0||!m.gate.isEmpty()||m.state!=ClearlyOff.State.NO_VISIBLE_ISSUE)return Alt.NONE;
        double uPos=100*MetalFit.ALT_U_POS_R,uRot=MetalFit.ALT_U_ROT_DEG;
        if(!Double.isFinite(m.altSidePct)||!Double.isFinite(m.altInoutPct)||!Double.isFinite(m.altTiltDeg)||!Double.isFinite(uPos)||!Double.isFinite(uRot)
                ||!Double.isFinite(strict.posLimitPct)||!Double.isFinite(strict.tiltLimitDeg))return Alt.NONE;
        if(!(m.altLumeDiffPct<=100*MetalFit.ALT_LUME_AGREE_R))return Alt.NONE;   // NaN (lume not measured, or no tolerance) fails
        double pos=Math.hypot(m.altSidePct,m.altInoutPct),tilt=Math.abs(m.altTiltDeg);
        if(pos-uPos>strict.posLimitPct||tilt-uRot>strict.tiltLimitDeg)return Alt.FAIL;
        if(pos+uPos<=strict.posLimitPct&&tilt+uRot<=strict.tiltLimitDeg)return Alt.PASS;
        return Alt.UNDECIDED;
    }

    /**
     * Owner, 21 Oct 2026: one credible out-of-range measurement is enough for Replica; a modest edge-fit imperfection
     * (slightly rough, partly inconsistent, loosely fixed, low resolution) is not the same as having no measurement.
     * NOT credible (unavailable or geometrically corrupted), with the engine's existing definitions only: no reading,
     * anything withheld (Can't check, a gate), no finite measurement, an outline mostly reconstructed (under
     * MetalFit.MIN_COVER seen), a fit whose typical residual is itself an inconsistent edge point (RMS over
     * MetalFit.EDGE_BAD_PX), more of its seen edge inconsistent than consistent, or an uncertain fit at a marker where
     * the per-marker check confirmed a hand (a hand can pull the fit: owner's genuine Submariner Date, 6 baton; the
     * validated refit decides those). A genuine PASS still needs a reliable reading (reliable()).
     */
    static boolean credible(ClearlyOff.Marker m){
        if(m==null||m.state==ClearlyOff.State.CANT_CHECK||!m.gate.isEmpty()||!m.measured())return false;
        if(Double.isFinite(m.cover)&&m.cover<MetalFit.MIN_COVER)return false;
        if(Double.isFinite(m.fitRmsPx)&&m.fitRmsPx>MetalFit.EDGE_BAD_PX)return false;
        if(Double.isFinite(m.badFrac)&&m.badFrac>0.5)return false;
        if(m.handConfirmed&&m.uncertain!=null&&!m.uncertain.isEmpty())return false;
        return true;
    }

    /** the limits a judged reading is outside (its own flags); a gap placed from the rest of the outline is not
     *  measured and never counts */
    static List<String> over(ClearlyOff.Marker x){
        List<String> f=failed(x);
        if(x!=null&&x.gapFromFit)f.remove("gap");
        return f;
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
    static Result classify(ClearlyOff.Result r,ModelSpec model,String failure){return classify(r,model,failure,null);}

    /** @param photoReason a photo-wide reason the markers can't be checked (dial or pose not usable), or null */
    static Result classify(ClearlyOff.Result r,ModelSpec model,String failure,String photoReason){
        Result out=new Result();out.failure=failure;
        if(model==null){out.verdict=Verdict.CANT_DETERMINE;return out;}
        out.required=model.markers.size();
        for(ModelSpec.Marker mk:model.markers){
            ClearlyOff.Marker m=r==null?null:r.at(mk.hour);
            out.markers.add(new MarkerCheck(mk.hour,mk.shape,m,r==null?null:strict(m,r.rpx),reliable(m)));
        }
        boolean allReliable=true,qcIssue=false,anyStrictFail=false,allGenuinePass=true;
        for(MarkerCheck c:out.markers){
            allReliable&=c.assessed();
            qcIssue|=c.qcFail();
            anyStrictFail|=c.genuineFail();
            allGenuinePass&=c.genuinePass();
        }
        boolean photoWide=r==null||failure!=null||photoReason!=null||r.photoReason!=null;
        if(photoWide)out.verdict=Verdict.CANT_DETERMINE;
        else if(qcIssue)out.verdict=Verdict.REPLICA_QC_ISSUES;
        else if(anyStrictFail)out.verdict=Verdict.REPLICA;
        else if(!allReliable)out.verdict=Verdict.CANT_DETERMINE;
        else if(allGenuinePass)out.verdict=Verdict.GENUINE;
        else out.verdict=Verdict.CANT_DETERMINE;
        out.partial=(out.verdict==Verdict.REPLICA_QC_ISSUES||out.verdict==Verdict.REPLICA)&&!allReliable;
        return out;
    }

    // ------------------------------------------------------------------------------------------------ marker wording

    /** one short line for the marker list: the two standards */
    static String line(MarkerCheck c){
        String g=c.alt==Alt.PASS?"meets the genuine standard (measured without the edge beside the hand)"
                :c.alt==Alt.FAIL?"outside the genuine standard (measured without the edge beside the hand)"
                :c.genuinePass()?"meets the genuine standard":c.genuineFail()?"outside the genuine standard ("+String.join(", ",failed(c.strict))+")"
                :"genuine standard not assessed";
        String q=c.qcFail()?"outside replica QC":c.qcPass()?"within replica QC":c.qc()==ClearlyOff.State.POSSIBLE?"replica QC: inspect":"replica QC not assessed";
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
        if(c.alt!=Alt.NONE){
            double uPos=100*MetalFit.ALT_U_POS_R,uRot=MetalFit.ALT_U_ROT_DEG,pos=Math.hypot(q.altSidePct,q.altInoutPct);
            out.add("This reading is uncertain ("+q.uncertain+"). A hand was found beside it, so for the genuine standard it was measured again without the edge beside the hand:");
            out.add(String.format(Locale.US,"Position %.2f%% R ± %.2f · genuine max %.2f%% R %s",pos,uPos,s.posLimitPct,c.alt==Alt.UNDECIDED?"(could cross the limit)":mark(pos+(c.alt==Alt.PASS?uPos:-uPos),s.posLimitPct,"max")));
            out.add(String.format(Locale.US,"Rotation %.2f° ± %.2f · genuine max %.2f° %s",Math.abs(q.altTiltDeg),uRot,s.tiltLimitDeg,c.alt==Alt.UNDECIDED?"(could cross the limit)":mark(Math.abs(q.altTiltDeg)+(c.alt==Alt.PASS?uRot:-uRot),s.tiltLimitDeg,"max")));
            if(c.alt==Alt.UNDECIDED)out.add("Not decided for the genuine standard: the measurement's uncertainty could cross a limit.");
        }
        else if(!c.reliable&&c.credible&&(c.genuineFail()||c.qcFail()))out.add("Measured from a slightly imperfect edge"+(q.uncertain==null||q.uncertain.isEmpty()?"":" ("+q.uncertain+")")
                +"; the reading is outside the range above and counts.");
        else if(!c.reliable)out.add(q.state==ClearlyOff.State.CANT_CHECK?"Not used for the genuine standard: "+q.reason+"."
                :"Not enough for a genuine pass: measured from an uncertain edge"+(q.uncertain==null||q.uncertain.isEmpty()?"":" ("+q.uncertain+")")
                +(c.credible?".":"; not used to decide a failure either (the edge fit is too poor"+(q.handConfirmed?", and a hand is beside it":"")+")."));
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
