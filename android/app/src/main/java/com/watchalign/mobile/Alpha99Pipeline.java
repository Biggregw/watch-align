package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alpha99 analysis of one photo of a given model (ModelSpec + its ModelReference), shared by the app and the desktop
 * preview: frozen pose -> unchanged Alpha94
 * measurement -> unchanged Alpha98 date window -> Alpha99 interference check -> Alpha99 evidence -> overview and
 * close-ups for the tiles. Nothing here feeds back into the pose or the measurement.
 */
final class Alpha99Pipeline {
    static final class Output {
        AutomaticDialOverlay.Result pose;
        Alpha94MarkerMeasurement.Report measurement;
        Alpha98DateWindow.Result date;
        Map<Integer,Alpha99MarkerInterference.Check> checks;
        /** Alpha102: hand-near round markers re-measured from the outline away from the hand (hour -> marker). */
        Map<Integer,Alpha94MarkerMeasurement.Marker> partial;
        Alpha99Findings.Summary summary;
        Bitmap overview;
        /** Transparent full-photo overlay: yellow expected geometry + cyan actual fitted baton edges. */
        Bitmap diagnosticOverlay;
        /** Full-photo ring-adjusted scoring reference, kept separate from the fixed-master overlay. */
        Bitmap scoringOverlay;
        final Map<String,Bitmap> closeups=new LinkedHashMap<>();
        final Map<Integer,MarkerMismatchResearch.Result> mismatch=new LinkedHashMap<>();
        String technical;
        ModelSpec model;ModelReference reference;
        /** Watch Align alignment-only results (every marker), their close-ups (hour -> image) and technical text. */
        AlignmentAnalysis.Output alignment;
        final Map<Integer,Bitmap> alignmentCloseups=new LinkedHashMap<>();
        String alignmentTechnical;AlignmentReference alignmentReference;Bitmap alignmentOverview;
        /** Watch Align 1.6.0: clearly-off check against the printed minute track (decides the alignment results). */
        ClearlyOff.Result clearlyOff;
        /** 1.6.6: why the clearly-off check failed on this photo as a whole (exception type and message), or null */
        String clearlyOffError;
        boolean ok(){return pose!=null&&pose.valid&&pose.homography!=null&&summary!=null;}
    }

    private Alpha99Pipeline(){}

    static Output run(Bitmap photo,ModelSpec model,ModelReference ref){return run(photo,model,ref,null,null);}

    /**
     * @param align the model's genuine luminous alignment reference (null: every marker "Cannot assess")
     * @param setups fit setups; null = from align (the calibration driver passes its own)
     */
    static Output run(Bitmap photo,ModelSpec model,ModelReference ref,AlignmentReference align,AlignmentAnalysis.Setups setups){
        Output o=new Output();o.model=model;o.reference=ref;o.alignmentReference=align;
        o.pose=AutomaticDialOverlay.build(photo,model);
        if(o.pose==null||!o.pose.valid||o.pose.homography==null)return o;
        double[] H=o.pose.homography;
        o.measurement=Alpha94MarkerMeasurement.analyse(photo,H,model);
        try{o.diagnosticOverlay=BatonFitDiagnosticOverlay.render(o.pose.overlay,o.measurement,H);}
        catch(Throwable t){o.diagnosticOverlay=o.pose.overlay;}
        Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(photo,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
        try{o.date=model.date==null?null:Alpha98DateWindow.analyse(gray,H,model.date);}catch(Throwable t){o.date=null;}
        try{o.checks=Alpha99MarkerInterference.analyse(gray,H,model);}catch(Throwable t){o.checks=null;}
        try{o.partial=partialRounds(photo,H,o.measurement,o.checks,model);}catch(Throwable t){o.partial=null;}
        o.summary=Alpha99Findings.build(o.measurement,o.date,o.checks,o.partial,model,ref);
        // Watch Align alignment-only layer: reads the frozen pose, the measured ring of the OTHER markers and the hand /
        // glare checks; changes none of them.
        try{
            AlignmentAnalysis.Setups su=setups!=null?setups:align==null?s->null:align::setup;
            o.alignment=AlignmentAnalysis.run((mk,ring)->AlignmentAnalysis.image(gray,H,mk,ring),H,o.measurement,o.checks,model,ref,align,su);
            // outer-metal alignment of the triangle and batons (existing Alpha107 measurement and genuine reference)
            try{MetalAlignment.apply(o.alignment,o.summary,o.measurement,model,ref,align==null?null:align.metalExtra);}
            catch(Throwable t){MetalAlignment.notAssessed(o.alignment,"the outer-metal comparison failed");}   // fail closed, never a silent pass
            o.alignmentTechnical=AlignmentAnalysis.technical(o.alignment,align);
            try{o.clearlyOff=ClearlyOff.analyse(gray,H,o.measurement.dialRadiusPx,clearlyOffInputs(o.alignment,o.checks),insets(align),model.minuteTrackInnerR,ClearlyOff.forModel(model.id));}
            catch(Throwable t){o.clearlyOff=null;o.clearlyOffError=failure(t);}   // 1.6.6: reported, not masked
            // 1.6.0: the clearly-off check decides the answers; earlier results stay in the technical details
            String legacy=o.alignmentTechnical;
            AlignmentDecision.applyClearlyOff(o.alignment.markers,o.clearlyOff,o.alignment.photoReason);
            o.alignmentTechnical=(o.clearlyOff==null?"Marker alignment check: could not be run on this photo"+(o.clearlyOffError==null?".":" (internal analysis failure: "+o.clearlyOffError+")."):ClearlyOff.technical(o.clearlyOff))
                    +"\n\nEarlier luminous / outer-metal analysis (kept for reference; not used for the results above):\n"+legacy;
            o.alignmentCloseups.clear();
            for(AlignmentDecision.Marker am:o.alignment.markers){
                try{o.alignmentCloseups.put(am.hour,ClearlyOffEvidence.render(rgba,H,model,o.clearlyOff,am));}catch(Throwable t){/* evidence picture optional */}
            }
            try{o.alignmentOverview=AlignmentOverview.render(rgba,H,o.alignment,model);}catch(Throwable t){o.alignmentOverview=null;}
        }catch(Throwable t){o.alignment=null;}
        double[] ring=o.measurement.ring!=null&&o.measurement.ring.usable?o.measurement.ring.model:null;
        List<Alpha99Findings.Finding> tiles=o.summary.tiles();
        for(Alpha99Findings.Finding f:o.summary.all){
            // Only individual marker outlines are scored, each under its own hour (never a ring/date/size feature).
            if(InspectionView.marker(f)&&model.atHour(f.hour)!=null)
                o.mismatch.put(f.hour,MarkerMismatchResearch.score(o.measurement,model.atHour(f.hour),ref,f,o.checks==null?null:o.checks.get(f.hour)));
            if(!tiles.contains(f)&&(f.dialWide||model.atHour(f.hour)==null))continue;
            // Use the existing detector result. This is a display-only diagnostic: no rerun or changed measurement.
            Alpha94MarkerMeasurement.Marker measured=f.shape==Alpha99Findings.Shape.BATON?o.measurement.atHour(f.hour):null;
            MarkerMismatchResearch.Result comparison=InspectionView.result(f,o.mismatch);
            Bitmap c=null;try{c=Alpha98Closeups.forFinding(rgba,H,f,o.date,ring,model,ref,measured,comparison);}catch(Throwable t){c=null;}
            if(c!=null)o.closeups.put(f.key,c);
        }
        try{o.overview=Alpha99Overview.render(rgba,H,o.summary,model,o.mismatch);}catch(Throwable t){o.overview=null;}
        try{o.scoringOverlay=MarkerMismatchOverlay.fullWatch(photo.getWidth(),photo.getHeight(),H,o.mismatch);}catch(Throwable t){o.scoringOverlay=null;}
        o.technical=technical(o);
        rgba.release();gray.release();
        return o;
    }

    /** The app's markers as the clearly-off check reads them: yellow outer-metal reference and hand / glare check. */
    /** a failure in plain words for the technical details: its type and message (out of memory says so) */
    static String failure(Throwable t){
        if(t instanceof OutOfMemoryError)return "out of memory (the photo is too large for this phone's memory)";
        String m=t.getMessage();return t.getClass().getSimpleName()+(m==null||m.isEmpty()?"":": "+m);
    }

    static List<ClearlyOff.Input> clearlyOffInputs(AlignmentAnalysis.Output a,Map<Integer,Alpha99MarkerInterference.Check> checks){
        List<ClearlyOff.Input> in=new ArrayList<>();
        for(AlignmentDecision.Marker m:a.markers){
            Alpha99MarkerInterference.Check c=checks==null?null:checks.get(m.hour);
            in.add(new ClearlyOff.Input(m.hour,m.shape,m.yellow,c==null?null:c.clean,
                    c==null?"its surroundings could not be checked for hands or glare":c.reason,
                    c!=null&&c.secondsHand?c.secondsPsi:Double.NaN));
        }
        return in;
    }

    /** Genuine luminous inset per supported shape (the luminous outline inside the yellow outer-metal reference). */
    static Map<ModelSpec.Shape,Double> insets(AlignmentReference align){
        Map<ModelSpec.Shape,Double> m=new java.util.HashMap<>();
        if(align==null)return m;
        for(ModelSpec.Shape s:ModelSpec.Shape.values()){AlignmentReference.ShapeRef r=align.shape(s);
            if(r!=null&&r.supported&&Double.isFinite(r.insetR))m.put(s,r.insetR);}
        return m;
    }

    /** Ignored arc around the hand direction for a partial round fit (+/- deg). */
    static final double PARTIAL_HALF_DEG=45.0;

    /**
     * Alpha102: round markers the hand check withholds because a hand is near but does not touch the marker's outline
     * (gap > 0 px) are re-measured with the arc of outline facing the hand ignored and the size fixed to the dial's own
     * round size. Only usable re-measurements are returned; Alpha99Findings caps them at worth a look.
     */
    static Map<Integer,Alpha94MarkerMeasurement.Marker> partialRounds(Bitmap photo,double[] H,Alpha94MarkerMeasurement.Report r,
                                                                     Map<Integer,Alpha99MarkerInterference.Check> checks,ModelSpec model){
        Map<Integer,Alpha94MarkerMeasurement.Marker> out=new LinkedHashMap<>();
        if(photo==null||H==null||r==null||checks==null)return out;
        for(ModelSpec.Marker mk:model.withShape(ModelSpec.Shape.ROUND)){
            Alpha99MarkerInterference.Check c=checks.get(mk.hour);
            Alpha94MarkerMeasurement.Marker fm=r.atHour(mk.hour);
            if(c!=null&&c.clean&&fm!=null&&edgeGapOnly(fm)&&arcIsOuterEdge(fm,r)){
                // Alpha105: clean marker whose outline edge vanishes on one side only (its polished surround reflecting
                // the dark, NecoClock frame 4's 10): position from the rest of the outline, the edgeless arc ignored
                double half=Math.max(PARTIAL_HALF_DEG,fm.edgeGapDeg/2+5);
                Alpha94MarkerMeasurement.Marker pm=Alpha94MarkerMeasurement.remeasureRound(photo,H,r,mk,fm.edgeGapDirDeg,half);
                if(pm.usable&&Double.isFinite(pm.localOffsetPx)){pm.edgeGapDeg=fm.edgeGapDeg;out.put(mk.hour,pm);}
                continue;
            }
            if(c==null||c.clean||!Alpha99MarkerInterference.HAND.equals(c.reason)||!(c.touchGapPx>0)||!Double.isFinite(c.touchGapPx))continue;
            double dir=Alpha99MarkerInterference.handDirection(c,mk,H);
            if(!Double.isFinite(dir))continue;
            Alpha94MarkerMeasurement.Marker pm=Alpha94MarkerMeasurement.remeasureRound(photo,H,r,mk,dir,PARTIAL_HALF_DEG);
            if(pm.usable&&Double.isFinite(pm.localOffsetPx))out.put(mk.hour,pm);
        }
        return out;
    }

    /** Largest edgeless arc allowed for a lighting-gap partial fit (deg); the rest (>= 235 deg) carries the position. */
    static final double MAX_EDGE_GAP_DEG=125.0;

    /** Alpha105: the round fit failed only on edge coverage, with a clean outline (integrity >= 0.95) and one edgeless
     *  arc no wider than MAX_EDGE_GAP_DEG. */
    static boolean edgeGapOnly(Alpha94MarkerMeasurement.Marker m){
        return !m.usable&&m.reason!=null&&m.reason.startsWith("outline coverage")&&Double.isFinite(m.fitScorePx)&&m.fitScorePx<=0.05
                &&Double.isFinite(m.edgeGapDeg)&&m.edgeGapDeg<=MAX_EDGE_GAP_DEG;
    }

    /** Alpha105g: the polished surround reads about 0.016 R wide (alpha96 README, inner-lume-edge study). A visible arc
     *  traced on the lume edge fits a circle about that much smaller than the dial's round markers, and fixing it to their
     *  size would pull the centre sideways; so the lighting-gap fit is used only when the arc's own (free) radius matches
     *  the dial's round-marker size to within half the surround width. */
    static final double HALF_SURROUND_R=0.008;
    static boolean arcIsOuterEdge(Alpha94MarkerMeasurement.Marker fm,Alpha94MarkerMeasurement.Report r){
        if(!Double.isFinite(fm.failedRadiusErrPx)||r==null||!(r.dialRadiusPx>0))return false;
        List<Double> sz=new ArrayList<>();
        for(Alpha94MarkerMeasurement.Marker o:r.markers)if("round".equals(o.kind)&&o.usable&&Double.isFinite(o.radiusErrPx))sz.add(o.radiusErrPx);
        if(sz.size()<3)return false;
        java.util.Collections.sort(sz);int n=sz.size();double med=n%2==1?sz.get(n/2):0.5*(sz.get(n/2-1)+sz.get(n/2));
        return Math.abs(fm.failedRadiusErrPx-med)<=HALF_SURROUND_R*r.dialRadiusPx;
    }

    /** Technical numbers for the collapsed "Technical details" section. */
    static String technical(Output o){
        AutomaticDialOverlay.Result q=o.pose;Alpha94MarkerMeasurement.Report m=o.measurement;Alpha98DateWindow.Result d=o.date;
        StringBuilder s=new StringBuilder("Model: "+o.model.label+"\n");
        s.append(String.format(Locale.US,"Pose: %d ticks · %d sectors · tick RMS %.2f px · dial radius %.0f px · 12 phase %s",
                q.detectedTicks,q.completePairs,q.fitAfter,m.dialRadiusPx,q.twelvePhaseUsed?(q.markerBranchTurn==0?"locked":"set by the hour markers ("+q.markerBranchTurn+" min from the 12 cue)"):"guarded by coarse pose"));
        s.append("\n").append(o.reference.triangle!=null&&m.triangle!=null?Alpha97TwelveReadout.summary(m,o.reference):m.compactSummary());
        if(d!=null&&d.usable)s.append(String.format(Locale.US,"\nDate window: tilt %+.2f° (+ = clockwise)",d.windowTiltDeg));
        else s.append("\nDate window: not measured").append(d==null?"":" ("+d.reason+")");
        s.append("\n\nInterference check (hand / glare at each marker):");
        if(o.checks==null)s.append(" unavailable - every marker withheld");
        else for(Alpha99MarkerInterference.Check c:o.checks.values())
            s.append(String.format(Locale.US,"\n  %d: %s%s%s",c.hour,c.clean?"clear":c.reason,c.secondsHand?" (seconds-hand line)":"",
                    o.partial!=null&&o.partial.containsKey(c.hour)&&!c.clean?String.format(Locale.US," - hand %.1f px away; position measured from the outline away from it",c.touchGapPx):""));
        s.append(String.format(Locale.US,"\n\nEvidence: value / genuine max / allowance (%.0f sigma, photo-to-photo spread of genuine watches):",o.reference.kSigma));
        for(Alpha99Findings.Finding f:o.summary.all){
            if(f.status==Alpha99Findings.Status.NOT_ASSESSED){s.append("\n  ").append(f.title).append(": not assessed (").append(f.reason).append(")");continue;}
            for(Alpha99Findings.Measure x:f.measures)
                s.append(String.format(Locale.US,"\n  %s %s: %s / %s / %s -> %s",f.title,x.name,Alpha99Findings.fmt(x.value,x.unit),Alpha99Findings.fmt(x.genuineMax,x.unit),
                        x.hasAllowance()?Alpha99Findings.fmt(x.allowance(),x.unit):"none",
                        x.status()==Alpha99Findings.Status.MINOR?"OUTSIDE GENUINE RANGE (below visibility guideline: under "+Alpha99Findings.fmt(x.visibleBar,x.unit)+")":x.status()));
            if(o.partial!=null&&o.partial.containsKey(f.hour)&&f.shape==Alpha99Findings.Shape.ROUND&&o.checks!=null&&o.checks.get(f.hour)!=null&&o.checks.get(f.hour).clean)
                s.append(String.format(Locale.US,"\n    (outline edge not visible over %.0f° of the %d marker; position measured from the rest)",o.partial.get(f.hour).edgeGapDeg,f.hour));
        }
        s.append("\n\nMeasurement only - comparisons with genuine watches, not a verdict. Directions in the upright dial frame (12 at top).");
        s.append("\n\nIndividual marker outline mismatch: ring-adjusted nominal outer metal; denominator = filled yellow template area. Continuous geometric area of fitted polygons; photographic boundary uncertainty is separate. No QC severity limits. Full-photo inspection offers the scoring reference and a separate fixed master. Global ring placement and minute-track gaps are separate.");
        for(Map.Entry<Integer,MarkerMismatchResearch.Result> entry:o.mismatch.entrySet())
            s.append("\n  ").append(entry.getKey()).append(": ").append(entry.getValue().display());
        return s.toString();
    }
}
