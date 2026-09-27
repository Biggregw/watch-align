package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plain-English summary shown at the top of the GMT QC checks. It only restates the
 * decisions already made by the 12-marker QC; it adds no thresholds of its own and
 * never calls a watch genuine or fake.
 */
final class GmtHumanSummary {
    static final class Input {
        GmtHumanQcMath.PoseLabel pose=GmtHumanQcMath.PoseLabel.UNASSESSABLE;
        boolean twelveValid;
        boolean stableFrame;
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.GapTrend gapTrend=GmtHumanQcMath.GapTrend.UNKNOWN;
        double observedGap=Double.NaN;
        GmtHumanQcMath.Attention alignment=GmtHumanQcMath.Attention.UNASSESSABLE;
        double rotationDeg=Double.NaN;
        double baseTiltDeg=Double.NaN;   // top edge vs the 59-01 tick line, same sign as rotationDeg
        double spacing59=Double.NaN, spacing01=Double.NaN;
        boolean overlayDrawn;
        boolean gapResolutionLimited;
        boolean tooSmall, handAtTwelve;
        double trianglePx=Double.NaN;
        double gapPx=Double.NaN, pxPerGap=Double.NaN;
        // Resampling check on the 12 (alpha56)
        boolean stabilityRun,stabilitySameEdge,gapUnstable,rotUnstable;
        double gapMin=Double.NaN,gapMax=Double.NaN,rotMin=Double.NaN,rotMax=Double.NaN;
        double stabilityGapSpread=Double.NaN,stabilityRotSpreadDeg=Double.NaN;
        // 6 o'clock baton (alpha55)
        boolean sixValid,sixStable,sixTooSmall,handAtSix,sixOffCentre,sixRotated;
        GmtHumanQcMath.Attention sixAttention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double sixCentring=Double.NaN,sixRotationDeg=Double.NaN,sixGap=Double.NaN,sixWidthPx=Double.NaN;
    }

    // Reference only, for the reader: what genuine images have measured on the same
    // outer-edge gap definition. Official renders 0.087-0.095; real genuine photos 0.081,
    // 0.083, 0.090, 0.099, 0.102, 0.107 (user's dealer photo and r/Watchexchange listings,
    // alpha55 corpus run). Not a decision boundary; decisions come from GmtHumanQcMath.
    static final String GENUINE_GAP_SEEN = "about 0.08–0.11";

    private GmtHumanSummary(){}

    static String build(Input in){
        StringBuilder s=new StringBuilder("SUMMARY\n");
        s.append("Photo: ").append(photoLine(in)).append("\n");
        s.append("12 gap: ").append(gapLine(in)).append("\n");
        s.append("12 alignment: ").append(alignmentLine(in)).append("\n");
        s.append("6 baton: ").append(sixLine(in)).append("\n");
        s.append("Overlay: ").append(in.overlayDrawn&&(in.tooSmall||in.handAtTwelve)
                ?"shows the 12 triangle that was found, grey and dashed because it was not judged. The close-up shows it enlarged."
                :in.overlayDrawn
                ?"shows what was measured at 12: the detected triangle, the 59/60/01 tick ends, the gap and the spacing either side, coloured green (clear), amber (check) or red. The close-ups show them enlarged; Inspect overlay zooms the whole photo."
                :"nothing at 12 could be measured, so only the dial edge is shown.").append("\n");
        s.append("\n").append(bottomLine(in)).append("\n");
        s.append("This flags things to look at closely. It does not prove a watch is genuine or fake.\n");
        return s.toString();
    }

    static String photoLine(Input in){
        switch(in.pose){
            case GOOD: return "good, near straight-on angle. Measurements are reliable.";
            case CORRECTABLE: return "slight angle. Usable; the gap check allows for it.";
            case RETAKE: return "too angled for fine checks. Retake straight-on before trusting the 12 marker results.";
            default: return "angle could not be judged. Treat the 12 marker results with caution.";
        }
    }

    static String gapLine(Input in){
        if(!in.twelveValid)return "could not be measured on this photo (12 triangle or minute track not found). This is not a pass.";
        String v=Double.isFinite(in.observedGap)?String.format(Locale.US," Measured %.2f; genuine photos tested so far read %s.",in.observedGap,GENUINE_GAP_SEEN):"";
        if(!in.stableFrame)v+=" The 12 marker could only be measured with low confidence on this photo, so treat this with caution.";
        if(in.tooSmall)return String.format(Locale.US,"not measured: the 12 triangle is only %.0f px wide in this photo. Take a closer photo so the dial fills more of the frame.",in.trianglePx);
        if(in.handAtTwelve)return "not judged: a hand is next to the 12 marker. Retake with the hands away from 12.";
        if(in.gapUnstable){
            String range=Double.isFinite(in.gapMax)?String.format(Locale.US," (it read between %.2f and %.2f)",in.gapMin,in.gapMax):"";
            if(in.gap==GmtHumanQcMath.Attention.CHECK)
                return "small in every re-measurement"+range+", but the edges are hard to pin down on this photo. Check by eye.";
            return "not judged: the reading changes when the photo is resized slightly"+range+", so the edges can't be pinned down on this photo. A closer, sharper, straight-on photo usually fixes this.";
        }
        if(in.gapResolutionLimited){
            String px=Double.isFinite(in.pxPerGap)?String.format(Locale.US," Here 1 pixel is %.3f of gap, so the difference is within a pixel.",in.pxPerGap):"";
            return String.format(Locale.US,"too close to call at this photo's resolution. Measured %.2f; genuine images tested so far read %s, and the attention level is %.2f.",
                    in.observedGap,GENUINE_GAP_SEEN,GmtHumanQcMath.LOW_CLEARANCE_ATTENTION)+px
                    +" Take a closer photo so the dial fills more of the frame.";
        }
        switch(in.gap){
            case CLEAR: return "normal. Clear space between the triangle and the minute track."+v;
            case CHECK:
                if(in.gapTrend==GmtHumanQcMath.GapTrend.COMPRESSED)
                    return "looks small, but the photo angle may be making it look smaller. Check by eye or retake straight-on."+v;
                return "small. The triangle sits closer to the minute track than expected. Check by eye."+v;
            case STRONG:
                if(Double.isFinite(in.gapPx)&&in.gapPx<1.0)
                    return "touching or almost touching the minute track."+v;
                return "clearly small, and the photo angle would make it look bigger rather than smaller. Check by eye."+v;
            default: return "could not be judged reliably on this photo.";
        }
    }

    static String alignmentLine(Input in){
        if(!in.twelveValid)return "could not be measured on this photo. This is not a pass.";
        if(in.tooSmall)return "not measured: the 12 triangle is too small in this photo.";
        if(in.handAtTwelve)return "not judged: a hand is next to the 12 marker.";
        if(in.rotUnstable&&in.alignment!=GmtHumanQcMath.Attention.CHECK){
            String range=Double.isFinite(in.rotMax)?String.format(Locale.US," (it read between %+.1f° and %+.1f°)",in.rotMin,in.rotMax):"";
            return "not judged: the reading changes when the photo is resized slightly"+range+", so it can't be trusted on this photo.";
        }
        String sp=Double.isFinite(in.spacing59)&&Double.isFinite(in.spacing01)
                ?String.format(Locale.US," (rotation %+.1f°, space to 59 tick %.2f vs 01 tick %.2f)",in.rotationDeg,in.spacing59,in.spacing01):"";
        String caution=in.rotUnstable
                ?String.format(Locale.US," It read %+.1f° to %+.1f° as the photo was resized slightly, so treat the exact angle with caution.",in.rotMin,in.rotMax)
                :in.stableFrame?"":" The 12 marker could only be measured with low confidence on this photo, so treat this with caution.";
        switch(in.alignment){
            case CLEAR: return "straight and centred. No visible rotation, even spacing either side."+sp+caution;
            case CHECK: return "possibly "+describe(in)+". Look closely; a hand touching the triangle can cause this."+sp+caution;
            case STRONG: return "visibly "+describe(in)+"."+sp+caution;
            default: return "could not be judged reliably on this photo.";
        }
    }

    static String sixLine(Input in){
        if(!in.sixValid)return "not measured on this photo (baton or minute track at 6 not found, often because a hand covers it).";
        if(in.sixTooSmall)return String.format(Locale.US,"not measured: the baton is only %.0f px wide in this photo.",in.sixWidthPx);
        if(in.handAtSix)return "not judged: a hand is next to the 6 baton.";
        String side=in.sixCentring>0?"right (towards the 29 tick)":"left (towards the 31 tick)";
        String nums=String.format(Locale.US," (offset %+.2f of its width, rotation %+.1f°)",in.sixCentring,in.sixRotationDeg);
        String caution=in.sixStable?"":" Measured with low confidence, so treat this with caution.";
        String what;
        if(in.sixOffCentre&&in.sixRotated)what="off-centre to the "+side+" and rotated "+(in.sixRotationDeg>0?"clockwise":"anticlockwise");
        else if(in.sixOffCentre)what="off-centre: it sits to the "+side;
        else if(in.sixRotated)what="rotated "+(in.sixRotationDeg>0?"clockwise":"anticlockwise");
        else what="off-centre or rotated";
        switch(in.sixAttention){
            case CLEAR: return "centred between the 29 and 31 ticks and straight."+nums;
            case CHECK: return "possibly "+what+". Look closely."+nums+caution;
            case STRONG: return "visibly "+what+"."+nums+caution;
            default: return "could not be judged reliably on this photo."+nums+caution;
        }
    }

    enum AlignmentKind { TURNED, TIP_LEANS, OFF_CENTRE, UNCLEAR }

    /**
     * What kind of misalignment the numbers describe. People describe these differently:
     * a triangle whose point leans but whose top edge is level looks "skewed", not
     * "rotated" (r/RepTimeQC p3hHVMB: owner saw CCW, moderator saw a CW cant; the app
     * measured the point leaning CW with a level top edge).
     */
    static AlignmentKind kind(Input in){
        double a=in.rotationDeg,b=in.baseTiltDeg;
        boolean axis=Double.isFinite(a)&&Math.abs(a)>=1.0;
        if(axis&&Double.isFinite(b)&&Math.abs(b)>=0.75&&Math.signum(a)==Math.signum(b))return AlignmentKind.TURNED;
        if(axis&&Double.isFinite(b)&&Math.abs(b)<0.75)return AlignmentKind.TIP_LEANS;
        if(Double.isFinite(in.spacing59)&&Double.isFinite(in.spacing01)&&Math.abs(in.spacing01-in.spacing59)>=0.06)return AlignmentKind.OFF_CENTRE;
        if(axis)return AlignmentKind.TURNED;
        return AlignmentKind.UNCLEAR;
    }

    static String describe(Input in){
        String dir=Double.isFinite(in.rotationDeg)?(in.rotationDeg>0?"clockwise":"anticlockwise"):"";
        switch(kind(in)){
            case TURNED: return String.format(Locale.US,"rotated: the whole triangle is turned %s by about %.1f°, top edge included",dir,Math.abs(in.rotationDeg));
            case TIP_LEANS: return String.format(Locale.US,"skewed: the point leans %s by about %.1f° but the top edge is level",dir,Math.abs(in.rotationDeg));
            case OFF_CENTRE: return "off-centre: the triangle sits closer to the "+(in.spacing59<in.spacing01?"59":"01")+" tick than the "+(in.spacing59<in.spacing01?"01":"59")+" tick";
            default: return "rotated or off-centre";
        }
    }

    static String bottomLine(Input in){
        List<String> items=new ArrayList<>();
        boolean gapFlag=in.twelveValid&&(in.gap==GmtHumanQcMath.Attention.CHECK||in.gap==GmtHumanQcMath.Attention.STRONG);
        boolean alignFlag=in.twelveValid&&(in.alignment==GmtHumanQcMath.Attention.CHECK||in.alignment==GmtHumanQcMath.Attention.STRONG);
        boolean sixFlag=in.sixValid&&(in.sixAttention==GmtHumanQcMath.Attention.CHECK||in.sixAttention==GmtHumanQcMath.Attention.STRONG);
        if(gapFlag)items.add("the gap at 12");
        if(alignFlag)items.add("the 12 marker alignment");
        if(sixFlag)items.add("the 6 baton position");
        String six6=sixFlag?" Separately, check the 6 baton position.":"";
        if(!in.twelveValid)return "Bottom line: the 12 marker could not be checked on this photo. Try a clearer, straight-on photo with the hands away from 12."+six6;
        if(in.twelveValid&&in.tooSmall)return "Bottom line: the 12 marker is too small in this photo to check. Take a closer photo so the dial fills more of the frame."+six6;
        if(in.twelveValid&&in.handAtTwelve)return "Bottom line: a hand is covering the area around the 12 marker, so it could not be checked. Retake with the hands away from 12."+six6;
        String closer=in.gapResolutionLimited&&!in.gapUnstable?" The gap at 12 is too close to call at this resolution; a closer photo would settle it.":"";
        boolean sixClear0=in.sixValid&&in.sixAttention==GmtHumanQcMath.Attention.CLEAR;
        boolean bothUnstable=in.gapUnstable&&in.rotUnstable;
        boolean anyUnstable=in.gapUnstable||in.rotUnstable;
        boolean clearElsewhere=(in.gapUnstable?true:in.gap==GmtHumanQcMath.Attention.CLEAR)&&(in.rotUnstable?true:in.alignment==GmtHumanQcMath.Attention.CLEAR);
        if(anyUnstable&&in.pose!=GmtHumanQcMath.PoseLabel.RETAKE&&(bothUnstable||!items.isEmpty()||clearElsewhere)){
            String what=bothUnstable?"the 12 reading":in.gapUnstable?"the 12 gap reading":"the 12 rotation reading";
            String unstable=what+" changes when the photo is resized slightly, so it isn't a reliable measurement here";
            if(items.isEmpty())return "Bottom line: nothing flagged, but "+unstable+". A closer, sharper, straight-on photo with the hands away from 12 usually fixes this."
                    +(sixClear0?" The 6 baton is centred and straight.":"");
            return "Bottom line: "+items.size()+(items.size()==1?" thing":" things")+" to check: "+join(items)+". "
                    +Character.toUpperCase(unstable.charAt(0))+unstable.substring(1)+", so confirm by eye.";
        }
        if(in.pose==GmtHumanQcMath.PoseLabel.RETAKE)
            return items.isEmpty()?"Bottom line: nothing flagged, but the photo is too angled to rely on that. Retake straight-on."
                    :"Bottom line: flagged "+join(items)+", but the photo is too angled to be sure. Retake straight-on.";
        boolean sixClear=in.sixValid&&in.sixAttention==GmtHumanQcMath.Attention.CLEAR;
        if(items.isEmpty())return (in.stableFrame?"Bottom line: nothing flagged at 12"+(sixClear?" or 6":"")+"."+(closer.isEmpty()?(sixClear?" The other markers are not checked yet, so look over the rest of the dial by eye.":" The 6 baton could not be judged here and the other markers are not checked yet, so look over the rest of the dial by eye."):closer)
                :"Bottom line: nothing flagged, but the 12 marker was only measured with low confidence. A clearer photo with the hands away from 12 would help."+closer);
        String line="Bottom line: "+items.size()+(items.size()==1?" thing":" things")+" to check: "+join(items)+".";
        if(!in.stableFrame)line+=" Measured with low confidence, so confirm by eye or with a clearer photo with the hands away from 12.";
        return line+closer;
    }

    private static String join(List<String> items){
        if(items.size()==1)return items.get(0);
        return String.join(", ",items.subList(0,items.size()-1))+" and "+items.get(items.size()-1);
    }
}
