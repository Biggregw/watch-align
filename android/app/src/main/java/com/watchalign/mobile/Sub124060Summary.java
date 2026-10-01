package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-English summary for the experimental 124060 path. Geometry that survives the confidence
 * gates is labelled MEASURED / NOT YET JUDGED. The presentation now follows the mature GMT user
 * experience, but no GMT QC tolerance or verdict is imported.
 */
final class Sub124060Summary {
    static final String EXPERIMENTAL="124060 experimental support: geometry is measured where reliable, but 124060 QC limits are not calibrated yet.";
    static final String MEASURED="MEASURED / NOT YET JUDGED";
    static final String NOT_CHECKED="Not checked: bezel and pearl, rehaut, hands, printing, lume.";

    private Sub124060Summary(){}

    static String build(Sub124060QcAnalyzer.Result r){
        StringBuilder s=new StringBuilder("SUMMARY\n");
        s.append(EXPERIMENTAL).append("\n");
        s.append("Dial: ").append(dialLine(r)).append("\n");
        if(!r.dialAssessable()){
            s.append("Markers: not looked for, because the dial geometry is not available.\n");
            s.append(NOT_CHECKED).append("\n");
            s.append("\nNext: use Align dial edge by hand below (tap the dial edge at 12 and at 6), or a sharper, straight-on photo with the whole dial visible.\n");
            return s.toString();
        }
        s.append("12 triangle: ").append(triangleLine(r)).append("\n");
        s.append("12 rotation: ").append(r.rotationWithheld!=null?"not measured ("+r.rotationWithheld+")"
                :String.format(Locale.US,"%+.2f° against the line from the dial centre through the 60-minute tick (+ = clockwise) · %s",r.rotationDeg,MEASURED)).append("\n");
        s.append("12 gap to minute track: ").append(r.gapWithheld!=null?"not measured ("+r.gapWithheld+")"
                :String.format(Locale.US,"%.3f of the dial radius between the triangle's top edge and the minute-track inner edge · %s",r.gapR,MEASURED)).append("\n");
        s.append("12 centring: ").append(r.centringWithheld!=null?"not measured ("+r.centringWithheld+")"
                :String.format(Locale.US,"%+.3f of the triangle width from the 60-minute tick (+ = towards 01) · %s",r.centringW,MEASURED)).append("\n");
        s.append("Batons 3/6/9: ").append(batonLine(r)).append("\n");
        s.append("Round markers: ").append(roundLine(r)).append("\n");
        s.append(NOT_CHECKED).append("\n");
        s.append("Overlay: GMT-style whole-dial markup. Cyan M = measured but not yet judged; grey dash = not judged on this photo, with the reason beside it. Every hour position is accounted for.\n");
        if(r.needsManual())s.append("\nNext: if the outlines are not on the right markers, try Align dial edge by hand below.\n");
        return s.toString();
    }

    static String dialLine(Sub124060QcAnalyzer.Result r){
        switch(r.dialSource){
            case AUTO_EDGE_FIT:
                return "automatic edge fit"+(Boolean.TRUE.equals(r.dialReproducible)?" (the same dial is found at 94% and 88%)"
                        :Boolean.FALSE.equals(r.dialReproducible)?"; it changes when the photo is reduced, so the 12 values are not measured":"");
            case MANUAL_EDGE_FIT:
                return "hand-aligned, then the dial edge was re-fitted"+(Boolean.FALSE.equals(r.dialReproducible)?"; the fit changes when the photo is reduced, so the 12 values are not measured":"");
            case MANUAL_CIRCLE:
                return "hand-aligned circle only (the dial edge could not be re-fitted); markers are located but the 12 values are not measured";
            default:
                return "not assessable: "+(r.dialReason.isEmpty()?"the dial could not be located":r.dialReason);
        }
    }

    static String triangleLine(Sub124060QcAnalyzer.Result r){
        if(r.triangle==null)return "not found ("+r.triangleReason+")";
        String what=r.lumeOutline?"detected (inner lume outline)":"detected";
        return r.twelveWithheld!=null?what+", not measured: "+r.twelveWithheld:what+", measured";
    }

    static String batonLine(Sub124060QcAnalyzer.Result r){
        List<String> parts=new ArrayList<>();int detected=0,measurable=0;
        for(Sub124060QcAnalyzer.Baton b:r.batons){
            boolean seen=b.status!=Sub124060QcAnalyzer.Status.NOT_FOUND&&b.status!=Sub124060QcAnalyzer.Status.WRONG_PLACE;
            if(seen)detected++;if(b.status==Sub124060QcAnalyzer.Status.FOUND)measurable++;
            parts.add(b.position.label+": "+word(b.status));
        }
        return detected+"/3 detected, "+measurable+"/3 measurable ("+String.join(" · ",parts)+")";
    }

    static String roundLine(Sub124060QcAnalyzer.Result r){
        List<String> parts=new ArrayList<>();int detected=0,measurable=0;
        for(Sub124060QcAnalyzer.Round m:r.rounds){
            if(m.status!=Sub124060QcAnalyzer.Status.NOT_FOUND)detected++;
            if(m.status==Sub124060QcAnalyzer.Status.FOUND)measurable++;
            if(m.status!=Sub124060QcAnalyzer.Status.FOUND)parts.add(m.marker.hour+": "+word(m.status));
        }
        int n=r.rounds.isEmpty()?Sub124060Layout.ROUND_HOURS.length:r.rounds.size();
        return detected+"/"+n+" detected, "+measurable+"/"+n+" measurable"+(parts.isEmpty()?"":" ("+String.join(" · ",parts)+")");
    }

    static String word(Sub124060QcAnalyzer.Status s){
        switch(s){
            case FOUND:return "measured";
            case LOW_CONFIDENCE:return "detected, not measured (low confidence)";
            case HAND:return "detected, not measured (hand in the way)";
            case WRONG_PLACE:return "not found where expected";
            default:return "not found";
        }
    }

    /** The Full results detail: raw measurements, reused confidence diagnostics and why anything was withheld. */
    static String details(Sub124060QcAnalyzer.Result r,String cropNote){
        StringBuilder s=new StringBuilder();
        s.append("Submariner 124060 path: the user-facing presentation follows the mature GMT QC contract (whole-dial marker markup and detailed 12 geometry), while the measurements remain model-specific where evidence requires it. The dial uses the shared GMT seed and edge fit; the 12 uses the Submariner detector and radial rotation reference; the 3/6/9 batons and round markers reuse the GMT marker detectors. Mature GMT resize/re-measure and marker-layout pose lessons are reused as confidence diagnostics. No GMT QC tolerance is copied into the 124060.\n");
        if(cropNote!=null)s.append(cropNote).append("\n");
        s.append(String.format(Locale.US,"Dial seed: centre %.1f, %.1f; radius %.1f px; quality %.2f.\n",r.seedX,r.seedY,r.seedR,r.seedQuality));
        s.append("Dial source: ").append(r.dialSource.words).append(".\n");
        if(r.edge!=null)s.append(String.format(Locale.US,"Dial edge fit: centre %.1f, %.1f; semi-axes %.1f / %.1f px (ratio %.4f); angle %.1f°; %d of %d rays; RMS %.2f px.\n",
                r.edge.cx,r.edge.cy,r.edge.axisA,r.edge.axisB,Math.min(r.edge.axisA,r.edge.axisB)/Math.max(r.edge.axisA,r.edge.axisB),r.edge.angleDeg,r.edge.points,r.edge.rays,r.edge.rmsPx));
        if(!r.dialReproNote.isEmpty())s.append("Dial-edge resize check: ").append(r.dialReproNote).append(".\n");
        if(!r.dialAssessable()){s.append("No markers were looked for.\n");return s.toString();}

        SubTwelveTriangle.Result tr=r.triangles;
        s.append("\n12 TRIANGLE\n");
        if(tr!=null)s.append(String.format(Locale.US,"Candidates: %d (plausible %d).\n",tr.cands.size(),countPlausible(tr)));
        SubTwelveTriangle.Cand c=r.triangle;
        if(c==null)s.append("Not found: ").append(r.triangleReason).append(".\n");
        else{
            s.append(String.format(Locale.US,"Selected: %s outline, fit %s, score %.2f; centre at %.3f R, %.2f° from the 60 tick; width %.3f R, height %.3f R, apex %.1f° (not gated).\n",
                    c.outline,c.fit,c.score,c.rho,c.dthetaDeg,c.widthR,c.heightR,c.apex));
            s.append(String.format(Locale.US,"Minute track: inner edge %.3f R (ticks 2-4 minutes either side), spread %.3f R; tick pitch %.2f°.\n",
                    Double.isFinite(c.trackR)&&r.frame!=null?c.trackR/r.frame.r:Double.NaN,c.trackSpreadR,c.tickPitch));
            if(r.triangleResizeStable!=null)s.append("Resize outline check (94%, 88%): ").append(r.triangleResizeNote).append(".\n");
            appendTriangleRepeatability(s,r);
            s.append(String.format(Locale.US,"Rotation references (diagnostic only): radial axis %+.2f°, local symmetric-tick chord %+.2f°, base edge %+.2f°; radial/chord disagreement %.2f°, radial/base disagreement %.2f°.\n",
                    c.rotationDeg,c.rotationChordDeg,c.baseEdgeDeg,angleDifference(c.rotationDeg,c.rotationChordDeg),angleDifference(c.rotationDeg,c.baseEdgeDeg)));
            s.append(String.format(Locale.US,"Raw values: rotation %+.2f°, gap %.4f R, centring %+.4f of width.\n",c.rotationDeg,c.gapR,c.centring));
            s.append(r.twelveWithheld!=null?"Reported: none ("+r.twelveWithheld+").\n":"Reported as MEASURED / NOT YET JUDGED where the summary shows a value; an individual value can still be withheld when its numeric resize check is not repeatable.\n");
        }

        s.append("\nBATONS 3/6/9\n");
        for(Sub124060QcAnalyzer.Baton b:r.batons){
            GmtSixLandmarkAnalyzer.Result x=b.result;
            s.append(b.position.label).append(": ").append(word(b.status));
            if(b.note!=null&&!b.note.isEmpty())s.append(" - ").append(b.note);
            if(x!=null&&x.valid){
                s.append(String.format(Locale.US,"; centring %+.3f of width, rotation %+.2f°, gap %.3f, %.0f px wide",x.centring,x.rotationDeg,x.gap,x.widthPx));
                if(x.stabilityRun){
                    double cp=MeasurementRepeatability.widthShiftPx(x.centringMin,x.centringMax,x.widthPx);
                    double rp=MeasurementRepeatability.angleShiftPx(x.rotMin,x.rotMax,x.lengthPx);
                    s.append(String.format(Locale.US,"; resize offset %+.3f to %+.3f (%.1f px), rotation %+.2f° to %+.2f° (%.1f px end travel), %s edge",
                            x.centringMin,x.centringMax,cp,x.rotMin,x.rotMax,rp,x.stabilitySameEdge?"same":"different"));
                }
            }
            s.append(".\n");
        }

        s.append("\nROUND MARKERS\n");
        for(Sub124060QcAnalyzer.Round m:r.rounds){
            GmtRoundMarkerAnalyzer.Marker k=m.marker;
            s.append(k.hour).append(": ").append(word(m.status));
            if(m.note!=null&&!m.note.isEmpty())s.append(" - ").append(m.note);
            if(k.found){
                s.append(String.format(Locale.US,"; offset %+.3f, inset %.3f, diameter %.1f px, outline %.0f%% off-circle",k.offset,k.inset,k.diameterPx(),100*k.rejectFraction));
                if(k.stabilityRun){
                    double op=MeasurementRepeatability.widthShiftPx(k.offMin,k.offMax,k.diameterPx());
                    s.append(String.format(Locale.US,"; resize offset %+.3f to %+.3f (%.1f px), %s edge",k.offMin,k.offMax,op,k.stabilitySameEdge?"same":"different"));
                }
            }
            s.append(".\n");
        }

        s.append("\nROUND-MARKER POSE DIAGNOSTIC\n");
        if(r.markerPose==null)s.append("Not run.\n");
        else if(!r.markerPose.valid)s.append("Not available: ").append(r.markerPose.reason).append(". No 124060 photo-angle decision is inferred.\n");
        else s.append(String.format(Locale.US,"Marker-layout estimate: %.1f° tilt, uncertainty upper estimate %.1f°, from %d round markers%s; fit residual %.4f. Diagnostic only: the GMT 5° near-frontal threshold is not applied to the 124060.\n",
                    r.markerPose.tiltDeg,r.markerPose.tiltHighDeg,r.markerPose.markers,
                    r.markerPose.dropped.isEmpty()?"":", marker "+r.markerPose.dropped+" left out by the robust fit",r.markerPose.residual));

        s.append("\nPresentation parity does not imply calibration parity: the overlay deliberately mirrors GMT, but no 124060 QC tolerances exist yet, so none of these geometric values is used as a pass/fail or authenticity decision.\n");
        return s.toString();
    }

    static void appendTriangleRepeatability(StringBuilder s,Sub124060QcAnalyzer.Result r){
        if(r.rotationResizeStable!=null){
            s.append(Double.isFinite(r.rotationShiftPx)
                    ?String.format(Locale.US,"Numeric resize check - rotation: %+.2f° to %+.2f°; %.1f px tip travel; %s.\n",r.rotationMin,r.rotationMax,r.rotationShiftPx,Boolean.TRUE.equals(r.rotationResizeStable)?"repeatable to about one pixel":"not repeatable to one pixel")
                    :"Numeric resize check - rotation: not reproduced at both scales.\n");
        }
        if(r.gapResizeStable!=null){
            s.append(Double.isFinite(r.gapShiftPx)
                    ?String.format(Locale.US,"Numeric resize check - gap: %.4f to %.4f R; %.1f px radial movement; %s.\n",r.gapMin,r.gapMax,r.gapShiftPx,Boolean.TRUE.equals(r.gapResizeStable)?"repeatable to about one pixel":"not repeatable to one pixel")
                    :"Numeric resize check - gap: not reproduced at both scales.\n");
        }
        if(r.centringResizeStable!=null){
            s.append(Double.isFinite(r.centringShiftPx)
                    ?String.format(Locale.US,"Numeric resize check - centring: %+.4f to %+.4f of width; %.1f px movement; %s.\n",r.centringMin,r.centringMax,r.centringShiftPx,Boolean.TRUE.equals(r.centringResizeStable)?"repeatable to about one pixel":"not repeatable to one pixel")
                    :"Numeric resize check - centring: not reproduced at both scales.\n");
        }
    }

    static double angleDifference(double a,double b){
        if(!Double.isFinite(a)||!Double.isFinite(b))return Double.NaN;
        return Math.abs(SubTwelveTriangle.wrap90(a-b));
    }

    private static int countPlausible(SubTwelveTriangle.Result tr){int n=0;for(SubTwelveTriangle.Cand c:tr.cands)if(c.plausible)n++;return n;}
}
