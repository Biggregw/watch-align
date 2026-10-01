package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-English summary for the experimental 124060 path. Every value is labelled MEASURED / NOT YET
 * JUDGED; no QC verdict word (OK, worth a look, check closely, pass, fail) is ever produced, and there
 * is no date-window or GMT date-side wording.
 */
final class Sub124060Summary {
    static final String EXPERIMENTAL="124060 experimental support: measurements are not yet QC pass/fail results.";
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
        s.append("Overlay: cyan outlines are landmarks found and measured; grey dashed outlines were found but not measured (reason beside them). Nothing is marked as passing or failing.\n");
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
        String what=r.lumeOutline?"found (inner lume outline)":"found";
        return r.twelveWithheld!=null?what+", not measured: "+r.twelveWithheld:what;
    }

    static String batonLine(Sub124060QcAnalyzer.Result r){
        List<String> parts=new ArrayList<>();
        for(Sub124060QcAnalyzer.Baton b:r.batons)parts.add(b.position.label+": "+word(b.status));
        return r.batonsFound()+" of 3 found ("+String.join(" · ",parts)+")";
    }

    static String roundLine(Sub124060QcAnalyzer.Result r){
        List<String> parts=new ArrayList<>();
        for(Sub124060QcAnalyzer.Round m:r.rounds)if(m.status!=Sub124060QcAnalyzer.Status.FOUND)parts.add(m.marker.hour+": "+word(m.status));
        int n=r.rounds.isEmpty()?Sub124060Layout.ROUND_HOURS.length:r.rounds.size();
        return r.roundsFound()+" of "+n+" found"+(parts.isEmpty()?"":" ("+String.join(" · ",parts)+")");
    }

    static String word(Sub124060QcAnalyzer.Status s){
        switch(s){
            case FOUND:return "found";
            case LOW_CONFIDENCE:return "found, low confidence";
            case HAND:return "hand in the way";
            case WRONG_PLACE:return "not found where expected";
            default:return "not found";
        }
    }

    /** The Full results detail: raw measurements and why anything was withheld. */
    static String details(Sub124060QcAnalyzer.Result r,String cropNote){
        StringBuilder s=new StringBuilder();
        s.append("Experimental Submariner 124060 path: the dial is located with the same dial seed and edge fit as the GMT check; the 12 triangle uses the Submariner detector (research v2, single photo, no apex gate); the 3/6/9 batons and round markers use the GMT marker detectors. Nothing is compared with a tolerance.\n");
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
            if(r.triangleResizeStable!=null)s.append("Resize check (94%, 88%): ").append(r.triangleResizeNote).append(".\n");
            s.append(String.format(Locale.US,"Raw values: rotation %+.2f°, gap %.4f R, centring %+.4f of width.\n",c.rotationDeg,c.gapR,c.centring));
            s.append(r.twelveWithheld!=null?"Reported: none ("+r.twelveWithheld+").\n":"Reported as MEASURED / NOT YET JUDGED where the summary shows a value.\n");
        }

        s.append("\nBATONS 3/6/9\n");
        for(Sub124060QcAnalyzer.Baton b:r.batons){
            GmtSixLandmarkAnalyzer.Result x=b.result;
            s.append(b.position.label).append(": ").append(word(b.status));
            if(b.note!=null&&!b.note.isEmpty())s.append(" - ").append(b.note);
            if(x!=null&&x.valid)s.append(String.format(Locale.US,"; centring %+.3f of width, rotation %+.2f°, gap %.3f, %.0f px wide",x.centring,x.rotationDeg,x.gap,x.widthPx));
            s.append(".\n");
        }
        s.append("\nROUND MARKERS\n");
        for(Sub124060QcAnalyzer.Round m:r.rounds){
            GmtRoundMarkerAnalyzer.Marker k=m.marker;
            s.append(k.hour).append(": ").append(word(m.status));
            if(m.note!=null&&!m.note.isEmpty())s.append(" - ").append(m.note);
            if(k.found)s.append(String.format(Locale.US,"; offset %+.3f, inset %.3f, diameter %.1f px, outline %.0f%% off-circle",k.offset,k.inset,k.diameterPx(),100*k.rejectFraction));
            s.append(".\n");
        }
        s.append("\nNo 124060 tolerances exist yet, so none of these numbers is compared with anything.\n");
        return s.toString();
    }

    private static int countPlausible(SubTwelveTriangle.Result tr){int n=0;for(SubTwelveTriangle.Cand c:tr.cands)if(c.plausible)n++;return n;}
}
