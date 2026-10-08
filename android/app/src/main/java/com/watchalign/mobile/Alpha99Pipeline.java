package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.LinkedHashMap;
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
        final Map<String,Bitmap> closeups=new LinkedHashMap<>();
        String technical;
        ModelSpec model;ModelReference reference;
        boolean ok(){return pose!=null&&pose.valid&&pose.homography!=null&&summary!=null;}
    }

    private Alpha99Pipeline(){}

    static Output run(Bitmap photo,ModelSpec model,ModelReference ref){
        Output o=new Output();o.model=model;o.reference=ref;
        o.pose=AutomaticDialOverlay.build(photo,model);
        if(o.pose==null||!o.pose.valid||o.pose.homography==null)return o;
        double[] H=o.pose.homography;
        o.measurement=Alpha94MarkerMeasurement.analyse(photo,H,model);
        Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(photo,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
        try{o.date=model.date==null?null:Alpha98DateWindow.analyse(gray,H,model.date);}catch(Throwable t){o.date=null;}
        try{o.checks=Alpha99MarkerInterference.analyse(gray,H,model);}catch(Throwable t){o.checks=null;}
        try{o.partial=partialRounds(photo,H,o.measurement,o.checks,model);}catch(Throwable t){o.partial=null;}
        o.summary=Alpha99Findings.build(o.measurement,o.date,o.checks,o.partial,model,ref);
        try{o.overview=Alpha99Overview.render(rgba,H,o.summary,model);}catch(Throwable t){o.overview=null;}
        double[] ring=o.measurement.ring!=null&&o.measurement.ring.usable?o.measurement.ring.model:null;
        for(Alpha99Findings.Finding f:o.summary.tiles()){
            Bitmap c=null;try{c=Alpha98Closeups.forFinding(rgba,H,f,o.date,ring,model,ref);}catch(Throwable t){c=null;}
            if(c!=null)o.closeups.put(f.key,c);
        }
        o.technical=technical(o);
        rgba.release();gray.release();
        return o;
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
            if(c==null||c.clean||!Alpha99MarkerInterference.HAND.equals(c.reason)||!(c.touchGapPx>0)||!Double.isFinite(c.touchGapPx))continue;
            double dir=Alpha99MarkerInterference.handDirection(c,mk,H);
            if(!Double.isFinite(dir))continue;
            Alpha94MarkerMeasurement.Marker pm=Alpha94MarkerMeasurement.remeasureRound(photo,H,r,mk,dir,PARTIAL_HALF_DEG);
            if(pm.usable&&Double.isFinite(pm.localOffsetPx))out.put(mk.hour,pm);
        }
        return out;
    }

    /** Technical numbers for the collapsed "Technical details" section. */
    static String technical(Output o){
        AutomaticDialOverlay.Result q=o.pose;Alpha94MarkerMeasurement.Report m=o.measurement;Alpha98DateWindow.Result d=o.date;
        StringBuilder s=new StringBuilder("Model: "+o.model.label+"\n");
        s.append(String.format(Locale.US,"Pose: %d ticks · %d sectors · tick RMS %.2f px · dial radius %.0f px · 12 phase %s",
                q.detectedTicks,q.completePairs,q.fitAfter,m.dialRadiusPx,q.twelvePhaseUsed?"locked":"guarded by coarse pose"));
        s.append("\n").append(o.reference.triangle!=null&&m.triangle!=null?Alpha97TwelveReadout.summary(m,o.reference):m.compactSummary());
        if(d!=null&&d.usable)s.append(String.format(Locale.US,"\nDate window: tilt %+.2f° (+ = clockwise)",d.windowTiltDeg));
        else s.append("\nDate window: not measured").append(d==null?"":" ("+d.reason+")");
        s.append("\n\nInterference check (hand / glare at each marker):");
        if(o.checks==null)s.append(" unavailable - every marker withheld");
        else for(Alpha99MarkerInterference.Check c:o.checks.values())
            s.append(String.format(Locale.US,"\n  %d: %s%s%s",c.hour,c.clean?"clear":c.reason,c.secondsHand?" (seconds-hand line)":"",
                    o.partial!=null&&o.partial.containsKey(c.hour)?String.format(Locale.US," - hand %.1f px away; position measured from the outline away from it",c.touchGapPx):""));
        s.append(String.format(Locale.US,"\n\nEvidence: value / genuine max / allowance (%.0f sigma, photo-to-photo spread of genuine watches):",o.reference.kSigma));
        for(Alpha99Findings.Finding f:o.summary.all){
            if(f.status==Alpha99Findings.Status.NOT_ASSESSED){s.append("\n  ").append(f.title).append(": not assessed (").append(f.reason).append(")");continue;}
            for(Alpha99Findings.Measure x:f.measures)
                s.append(String.format(Locale.US,"\n  %s %s: %s / %s / %s -> %s",f.title,x.name,Alpha99Findings.fmt(x.value,x.unit),Alpha99Findings.fmt(x.genuineMax,x.unit),
                        x.hasAllowance()?Alpha99Findings.fmt(x.allowance(),x.unit):"none",x.status()));
        }
        s.append("\n\nMeasurement only - comparisons with genuine watches, not a verdict. Directions in the upright dial frame (12 at top).");
        return s.toString();
    }
}
