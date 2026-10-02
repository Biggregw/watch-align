package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Plain-English report for the experimental 124060 path and its frozen provisional alignment bands. */
final class Sub124060Summary {
    static final String EXPERIMENTAL="124060 experimental support: provisional alignment QC only; not an authenticity or overall-watch verdict.";
    static final String MEASURED="MEASURED / NOT YET JUDGED";
    static final String NOT_CHECKED="Not checked: bezel and pearl, rehaut, hands, printing, lume.";

    private Sub124060Summary(){}

    static final class Alignment {
        double ringRho=Double.NaN,roundSpacingRmsDeg=Double.NaN,line39OffsetR=Double.NaN,line126OffsetR=Double.NaN;
        String ringReason="",spacingReason="",line39Reason="",line126Reason="";
        Sub124060ProvisionalQc.Judgement rotation=Sub124060ProvisionalQc.Judgement.UNJUDGED;
        Sub124060ProvisionalQc.Judgement centring=Sub124060ProvisionalQc.Judgement.UNJUDGED;
        Sub124060ProvisionalQc.Judgement ring=Sub124060ProvisionalQc.Judgement.UNJUDGED;
        Sub124060ProvisionalQc.Judgement spacing=Sub124060ProvisionalQc.Judgement.UNJUDGED;
        Sub124060ProvisionalQc.Judgement line39=Sub124060ProvisionalQc.Judgement.UNJUDGED;
        Sub124060ProvisionalQc.Judgement line126=Sub124060ProvisionalQc.Judgement.UNJUDGED;
    }

    static String build(Sub124060QcAnalyzer.Result r){
        Alignment a=alignment(r);
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
        s.append("12 rotation: ").append(twelveRotationLine(r,a)).append("\n");
        s.append("12 gap to minute track: ").append(r.gapWithheld!=null?"not measured ("+r.gapWithheld+")"
                :String.format(Locale.US,"%.3f of the dial radius between the triangle's top edge and the minute-track inner edge · %s (no tolerance: calibration found pose/scale sensitivity)",r.gapR,MEASURED)).append("\n");
        s.append("12 centring: ").append(twelveCentringLine(r,a)).append("\n");
        s.append("Batons 3/6/9: ").append(batonLine(r)).append("\n");
        s.append("Round markers: ").append(roundLine(r)).append("\n");
        appendAlignmentLines(s,a);
        s.append(NOT_CHECKED).append("\n");
        s.append("Overlay: cyan outlines are landmarks found and measured; grey dashed outlines were found but not measured. Provisional alignment labels come from the frozen 124060 calibration bands, not GMT tolerances.\n");
        if(r.needsManual())s.append("\nNext: if the outlines are not on the right markers, try Align dial edge by hand below.\n");
        return s.toString();
    }

    static String twelveRotationLine(Sub124060QcAnalyzer.Result r,Alignment a){
        if(r.rotationWithheld!=null)return "not measured ("+r.rotationWithheld+")";
        String raw=String.format(Locale.US,"%+.2f° against the line from the dial centre through the 60-minute tick (+ = clockwise)",r.rotationDeg);
        return a.rotation==Sub124060ProvisionalQc.Judgement.UNJUDGED?raw+" · "+MEASURED:raw+" · "+Sub124060ProvisionalQc.words(a.rotation);
    }

    static String twelveCentringLine(Sub124060QcAnalyzer.Result r,Alignment a){
        if(r.centringWithheld!=null)return "not measured ("+r.centringWithheld+")";
        String raw=String.format(Locale.US,"%+.3f of the triangle width from the 60-minute tick (+ = towards 01)",r.centringW);
        return a.centring==Sub124060ProvisionalQc.Judgement.UNJUDGED?raw+" · "+MEASURED:raw+" · "+Sub124060ProvisionalQc.words(a.centring);
    }

    static void appendAlignmentLines(StringBuilder s,Alignment a){
        if(Double.isFinite(a.ringRho))s.append(String.format(Locale.US,"Round-marker ring radius: %.4f R · %s\n",a.ringRho,Sub124060ProvisionalQc.words(a.ring)));
        else if(!a.ringReason.isEmpty())s.append("Round-marker ring radius: not judged (").append(a.ringReason).append(")\n");
        if(Double.isFinite(a.roundSpacingRmsDeg))s.append(String.format(Locale.US,"Round-marker spacing RMS: %.2f° · %s\n",a.roundSpacingRmsDeg,Sub124060ProvisionalQc.words(a.spacing)));
        else if(!a.spacingReason.isEmpty())s.append("Round-marker spacing RMS: not judged (").append(a.spacingReason).append(")\n");
        if(Double.isFinite(a.line39OffsetR))s.append(String.format(Locale.US,"3-9 baton axis offset: %.4f R · %s\n",a.line39OffsetR,Sub124060ProvisionalQc.words(a.line39)));
        else if(!a.line39Reason.isEmpty())s.append("3-9 baton axis offset: not judged (").append(a.line39Reason).append(")\n");
        if(Double.isFinite(a.line126OffsetR))s.append(String.format(Locale.US,"12-6 marker axis offset: %.4f R · %s\n",a.line126OffsetR,Sub124060ProvisionalQc.words(a.line126)));
        else if(!a.line126Reason.isEmpty())s.append("12-6 marker axis offset: not judged (").append(a.line126Reason).append(")\n");
    }

    static String dialLine(Sub124060QcAnalyzer.Result r){
        switch(r.dialSource){
            case AUTO_EDGE_FIT:
                return "automatic edge fit"+(Boolean.TRUE.equals(r.dialReproducible)?" (the same dial is found at 94% and 88%)"
                        :Boolean.FALSE.equals(r.dialReproducible)?"; it changes when the photo is reduced, so alignment QC is withheld":"");
            case MANUAL_EDGE_FIT:
                return "hand-aligned, then the dial edge was re-fitted"+(Boolean.FALSE.equals(r.dialReproducible)?"; the fit changes when the photo is reduced, so alignment QC is withheld":"");
            case MANUAL_CIRCLE:
                return "hand-aligned circle only (the dial edge could not be re-fitted); markers are located but alignment QC is withheld";
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

    /** Evaluate only measurements whose production confidence gates support a provisional judgement. */
    static Alignment alignment(Sub124060QcAnalyzer.Result r){
        Alignment a=new Alignment();
        if(r.rotationWithheld==null&&Double.isFinite(r.rotationDeg)&&Boolean.TRUE.equals(r.rotationResizeStable))
            a.rotation=Sub124060ProvisionalQc.TWELVE_ROTATION.judge(r.rotationDeg);
        if(r.centringWithheld==null&&Double.isFinite(r.centringW)&&Boolean.TRUE.equals(r.centringResizeStable))
            a.centring=Sub124060ProvisionalQc.TWELVE_CENTRING.judge(r.centringW);

        if(r.frame==null||r.edge==null||!Boolean.TRUE.equals(r.dialReproducible)){
            String why=r.edge==null?"a reproducible dial-edge fit is required":"the dial-edge fit is not reproducible at 94% and 88%";
            a.ringReason=a.spacingReason=a.line39Reason=a.line126Reason=why;
            return a;
        }

        double ref12=rectClock(r.frame,r.tick60);
        List<Double> rho=new ArrayList<>(),dtheta=new ArrayList<>();
        for(Sub124060QcAnalyzer.Round q:r.rounds){
            GmtRoundMarkerAnalyzer.Marker m=q.marker;
            if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found||!Double.isFinite(m.x)||!Double.isFinite(m.y))continue;
            double[] p=rectNorm(r.frame,m.x,m.y);
            if(p==null)continue;
            rho.add(Math.hypot(p[0],p[1]));
            if(Double.isFinite(ref12))dtheta.add(wrap180(rectClock(r.frame,m.x,m.y)-ref12-m.hour*30.0));
        }
        if(rho.size()>=4){
            a.ringRho=Sub124060ProvisionalQc.median(rho);
            a.ring=Sub124060ProvisionalQc.ROUND_RING_RHO.judge(a.ringRho);
        }else a.ringReason="fewer than four reliable round-marker centres are available";
        if(dtheta.size()>=4){
            a.roundSpacingRmsDeg=Sub124060ProvisionalQc.spacingRms(dtheta);
            a.spacing=Sub124060ProvisionalQc.ROUND_SPACING_RMS.judge(a.roundSpacingRmsDeg);
        }else a.spacingReason=Double.isFinite(ref12)?"fewer than four reliable round-marker angles are available":"the 60-minute tick orientation is unavailable";

        Sub124060QcAnalyzer.Baton b3=baton(r,GmtSixLandmarkAnalyzer.Position.THREE),b6=baton(r,GmtSixLandmarkAnalyzer.Position.SIX),b9=baton(r,GmtSixLandmarkAnalyzer.Position.NINE);
        double[] p3=batonPoint(r.frame,b3),p9=batonPoint(r.frame,b9);
        if(p3!=null&&p9!=null){
            a.line39OffsetR=Sub124060ProvisionalQc.lineOffset(p3,p9);
            a.line39=Sub124060ProvisionalQc.BATON_3_9_LINE_OFFSET.judge(a.line39OffsetR);
        }else a.line39Reason="reliable edge-fitted 3 and 9 baton centres are both required";

        double[] p12=r.triangle!=null&&r.twelveWithheld==null?rectNorm(r.frame,r.triangle.cx,r.triangle.cy):null;
        double[] p6=batonPoint(r.frame,b6);
        if(p12!=null&&p6!=null){
            a.line126OffsetR=Sub124060ProvisionalQc.lineOffset(p12,p6);
            a.line126=Sub124060ProvisionalQc.AXIS_12_6_LINE_OFFSET.judge(a.line126OffsetR);
        }else a.line126Reason="a reliable 12 triangle and edge-fitted 6 baton centre are both required";
        return a;
    }

    static Sub124060QcAnalyzer.Baton baton(Sub124060QcAnalyzer.Result r,GmtSixLandmarkAnalyzer.Position p){
        for(Sub124060QcAnalyzer.Baton b:r.batons)if(b.position==p)return b;
        return null;
    }

    static double[] batonPoint(GmtRoundMarkerAnalyzer.DialFrame f,Sub124060QcAnalyzer.Baton b){
        if(b==null||b.status!=Sub124060QcAnalyzer.Status.FOUND||b.result==null||!b.result.valid||b.result.geometry==null||!b.result.geometry.outerEdge)return null;
        GmtSixLandmarkAnalyzer.Geometry g=b.result.geometry;
        double x=(g.outerLeft[0]+g.outerRight[0]+g.innerLeft[0]+g.innerRight[0])/4.0;
        double y=(g.outerLeft[1]+g.outerRight[1]+g.innerLeft[1]+g.innerRight[1])/4.0;
        return rectNorm(f,x,y);
    }

    static double[] rectNorm(GmtRoundMarkerAnalyzer.DialFrame f,double x,double y){
        if(f==null||!(f.r>0)||!Double.isFinite(x)||!Double.isFinite(y))return null;
        double[] p=f.rect(x,y);return new double[]{p[0]/f.r,p[1]/f.r};
    }

    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double[] p){return p==null||p.length<2?Double.NaN:rectClock(f,p[0],p[1]);}
    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double x,double y){
        if(f==null)return Double.NaN;double[] p=f.rect(x,y);return Math.toDegrees(Math.atan2(p[0],-p[1]));
    }
    static double wrap180(double d){return (d+180.0)%360.0<0?(d+180.0)%360.0+180.0:(d+180.0)%360.0-180.0;}

    /** Full results: raw measurements, confidence diagnostics, provisional bands and provenance. */
    static String details(Sub124060QcAnalyzer.Result r,String cropNote){
        Alignment a=alignment(r);
        StringBuilder s=new StringBuilder();
        s.append("Experimental Submariner 124060 path. Detector geometry remains unchanged from alpha70. Six alignment metrics now use frozen provisional 124060 calibration bands; 12 gap remains measurement-only because calibration marked it pose/scale sensitive. These bands are not authenticity thresholds and do not assess overall watch quality.\n");
        s.append("Calibration provenance: Watch-family Calibrator run 36927036008; frozen before holdout; validation and holdout accepted the six calibrated metrics.\n");
        if(cropNote!=null)s.append(cropNote).append("\n");
        s.append(String.format(Locale.US,"Dial seed: centre %.1f, %.1f; radius %.1f px; quality %.2f.\n",r.seedX,r.seedY,r.seedR,r.seedQuality));
        s.append("Dial source: ").append(r.dialSource.words).append(".\n");
        if(r.edge!=null)s.append(String.format(Locale.US,"Dial edge fit: centre %.1f, %.1f; semi-axes %.1f / %.1f px (ratio %.4f); angle %.1f°; %d of %d rays; RMS %.2f px.\n",
                r.edge.cx,r.edge.cy,r.edge.axisA,r.edge.axisB,Math.min(r.edge.axisA,r.edge.axisB)/Math.max(r.edge.axisA,r.edge.axisB),r.edge.angleDeg,r.edge.points,r.edge.rays,r.edge.rmsPx));
        if(!r.dialReproNote.isEmpty())s.append("Dial-edge resize check: ").append(r.dialReproNote).append(".\n");
        if(!r.dialAssessable()){s.append("No markers were looked for.\n");return s.toString();}

        s.append("\nPROVISIONAL ALIGNMENT BANDS\n");
        appendBand(s,"12 rotation",Sub124060ProvisionalQc.TWELVE_ROTATION,"deg");
        s.append("12 gap: no band; INSUFFICIENT in calibration (pose/scale sensitive).\n");
        appendBand(s,"12 centring",Sub124060ProvisionalQc.TWELVE_CENTRING,"triangle widths");
        appendBand(s,"round-marker ring radius",Sub124060ProvisionalQc.ROUND_RING_RHO,"R");
        appendBand(s,"round-marker spacing RMS",Sub124060ProvisionalQc.ROUND_SPACING_RMS,"deg");
        appendBand(s,"3-9 baton axis offset",Sub124060ProvisionalQc.BATON_3_9_LINE_OFFSET,"R");
        appendBand(s,"12-6 marker axis offset",Sub124060ProvisionalQc.AXIS_12_6_LINE_OFFSET,"R");

        s.append("\n12 TRIANGLE\n");
        SubTwelveTriangle.Result tr=r.triangles;
        if(tr!=null)s.append(String.format(Locale.US,"Candidates: %d (plausible %d).\n",tr.cands.size(),countPlausible(tr)));
        SubTwelveTriangle.Cand c=r.triangle;
        if(c==null)s.append("Not found: ").append(r.triangleReason).append(".\n");
        else{
            s.append(String.format(Locale.US,"Selected: %s outline, fit %s, score %.2f; centre at %.3f R, %.2f° from the 60 tick; width %.3f R, height %.3f R, apex %.1f° (not gated).\n",
                    c.outline,c.fit,c.score,c.rho,c.dthetaDeg,c.widthR,c.heightR,c.apex));
            if(r.triangleResizeStable!=null)s.append("Resize outline check (94%, 88%): ").append(r.triangleResizeNote).append(".\n");
            appendTriangleRepeatability(s,r);
            s.append(String.format(Locale.US,"Raw values: rotation %+.2f°, gap %.4f R, centring %+.4f of width.\n",c.rotationDeg,c.gapR,c.centring));
            s.append("Published rotation: ").append(r.rotationWithheld!=null?"withheld: "+r.rotationWithheld:Double.isFinite(r.rotationDeg)?String.format(Locale.US,"%+.2f° · %s",r.rotationDeg,a.rotation==Sub124060ProvisionalQc.Judgement.UNJUDGED?MEASURED:Sub124060ProvisionalQc.words(a.rotation)):"unavailable").append(".\n");
            s.append("Published gap: ").append(r.gapWithheld!=null?"withheld: "+r.gapWithheld:Double.isFinite(r.gapR)?String.format(Locale.US,"%.4f R · %s",r.gapR,MEASURED):"unavailable").append(".\n");
            s.append("Published centring: ").append(r.centringWithheld!=null?"withheld: "+r.centringWithheld:Double.isFinite(r.centringW)?String.format(Locale.US,"%+.4f · %s",r.centringW,a.centring==Sub124060ProvisionalQc.Judgement.UNJUDGED?MEASURED:Sub124060ProvisionalQc.words(a.centring)):"unavailable").append(".\n");
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

        s.append("\nPROVISIONAL ALIGNMENT RESULTS\n");
        appendAlignmentLines(s,a);
        s.append("No overall pass/fail or authenticity decision is produced.\n");
        return s.toString();
    }

    static void appendBand(StringBuilder s,String name,Sub124060ProvisionalQc.Band b,String unit){
        s.append(String.format(Locale.US,"%s: clear %.6f to %.6f %s; check %.6f to %.6f %s.\n",name,b.clearLow,b.clearHigh,unit,b.checkLow,b.checkHigh,unit));
    }

    static void appendTriangleRepeatability(StringBuilder s,Sub124060QcAnalyzer.Result r){
        if(r.rotationResizeStable!=null)s.append(Double.isFinite(r.rotationShiftPx)
                ?String.format(Locale.US,"Numeric resize check - rotation: %+.2f° to %+.2f°; %.1f px tip travel; %s.\n",r.rotationMin,r.rotationMax,r.rotationShiftPx,Boolean.TRUE.equals(r.rotationResizeStable)?"repeatable to about one pixel":"not repeatable to one pixel")
                :"Numeric resize check - rotation: not reproduced at both scales.\n");
        if(r.gapResizeStable!=null)s.append(Double.isFinite(r.gapShiftPx)
                ?String.format(Locale.US,"Numeric resize check - gap: %.4f to %.4f R; %.1f px radial movement; %s.\n",r.gapMin,r.gapMax,r.gapShiftPx,Boolean.TRUE.equals(r.gapResizeStable)?"repeatable to about one pixel":"not repeatable to one pixel")
                :"Numeric resize check - gap: not reproduced at both scales.\n");
        if(r.centringResizeStable!=null)s.append(Double.isFinite(r.centringShiftPx)
                ?String.format(Locale.US,"Numeric resize check - centring: %+.4f to %+.4f of width; %.1f px movement; %s.\n",r.centringMin,r.centringMax,r.centringShiftPx,Boolean.TRUE.equals(r.centringResizeStable)?"repeatable to about one pixel":"not repeatable to one pixel")
                :"Numeric resize check - centring: not reproduced at both scales.\n");
    }

    static double angleDifference(double a,double b){
        if(!Double.isFinite(a)||!Double.isFinite(b))return Double.NaN;
        return Math.abs(SubTwelveTriangle.wrap90(a-b));
    }

    private static int countPlausible(SubTwelveTriangle.Result tr){int n=0;for(SubTwelveTriangle.Cand c:tr.cands)if(c.plausible)n++;return n;}
}
