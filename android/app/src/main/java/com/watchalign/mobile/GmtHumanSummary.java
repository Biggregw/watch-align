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
        /** Why the 6 was measured with low confidence (alpha57), or empty. */
        String sixLowReason="";
        boolean sixUnstable;double sixCentringMin=Double.NaN,sixCentringMax=Double.NaN,sixRotMin=Double.NaN,sixRotMax=Double.NaN;
        /** 9 o'clock baton (alpha59). The 6 keeps its own fields above for compatibility. */
        Baton nine=new Baton("9",44,46);
        /** Round hour markers (alpha61), with their verdicts; empty when not measured. */
        List<GmtRoundMarkerAnalyzer.Marker> round=new ArrayList<>();
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
        s.append("All markers: ").append(allMarkersLine(in)).append("\n");
        s.append("12 gap: ").append(gapLine(in)).append("\n");
        s.append("12 alignment: ").append(alignmentLine(in)).append("\n");
        s.append("6 baton: ").append(sixLine(in)).append("\n");
        in.nine.angled=poorPose(in);
        s.append("9 baton: ").append(batonLine(in.nine)).append("\n");
        s.append("Round markers: ").append(roundLine(in)).append("\n");
        s.append("Overlay: ").append(in.overlayDrawn&&(in.tooSmall||in.handAtTwelve)
                ?"shows the 12 triangle that was found, grey and dashed because it was not judged. The close-up shows it enlarged."
                :in.overlayDrawn
                ?"shows what was measured: at 12 the detected triangle, the 59/60/01 tick ends, the gap and the spacing either side; at 6 and 9 the baton outline and its neighbouring ticks; each round marker's outline and the ticks either side. Each is coloured green (clear), amber (check) or red. The close-ups show them enlarged; Inspect overlay zooms the whole photo."
                :"nothing at 12 could be measured, so only the dial edge is shown.").append("\n");
        s.append("\n").append(bottomLine(in)).append("\n");
        s.append("This flags things to look at closely. It does not prove a watch is genuine or fake.\n");
        return s.toString();
    }

    /**
     * Every hour position, 12 round to 11, with one word each (alpha61), so no marker can go
     * unmentioned whatever else the summary says. The 3 is the date window.
     */
    static String allMarkersLine(Input in){
        List<String> parts=new ArrayList<>();
        java.util.Map<Integer,GmtRoundMarkerAnalyzer.Marker> round=new java.util.HashMap<>();
        for(GmtRoundMarkerAnalyzer.Marker m:in.round)round.put(m.hour,m);
        Baton six=six(in);in.nine.angled=poorPose(in);
        for(int h=12,k=0;k<12;k++,h=h%12+1){
            String st;
            if(h==12)st=twelveStatus(in);
            else if(h==3)st="date window (not a marker)";
            else if(h==6)st=batonStatus(six);
            else if(h==9)st=batonStatus(in.nine);
            else{GmtRoundMarkerAnalyzer.Marker m=round.get(h);st=m==null?"not measured":roundStatus(m);}
            parts.add(h+" "+st);
        }
        return String.join(" · ",parts)+".";
    }

    static String twelveStatus(Input in){
        if(!in.twelveValid)return "not found";
        if(in.tooSmall)return "too small";
        if(in.handAtTwelve)return "hand in the way";
        boolean flag=in.gap==GmtHumanQcMath.Attention.CHECK||in.gap==GmtHumanQcMath.Attention.STRONG
                ||in.alignment==GmtHumanQcMath.Attention.CHECK||in.alignment==GmtHumanQcMath.Attention.STRONG;
        if(flag)return (in.gap==GmtHumanQcMath.Attention.STRONG||in.alignment==GmtHumanQcMath.Attention.STRONG)?"CHECK CLOSELY":"worth a look";
        if(in.gap==GmtHumanQcMath.Attention.CLEAR&&in.alignment==GmtHumanQcMath.Attention.CLEAR)return in.stableFrame?"OK":"OK (low confidence)";
        if(in.gap==GmtHumanQcMath.Attention.CLEAR||in.alignment==GmtHumanQcMath.Attention.CLEAR)return "partly judged";
        return "not judged";
    }

    static String batonStatus(Baton b){
        if(!b.valid)return "not found";
        if(b.tooSmall)return "too small";
        if(b.hand)return "hand in the way";
        switch(b.attention){
            case CLEAR:return "OK";
            case CHECK:return "worth a look";
            case STRONG:return "CHECK CLOSELY";
            default:return b.unstable?"not judged (reading unsteady)":b.angled&&b.stable?"not judged (angle)":"not judged";
        }
    }

    static String roundStatus(GmtRoundMarkerAnalyzer.Marker m){
        if(!m.found)return "not found";
        if(m.hand)return "hand in the way";
        if(m.tooSmall)return "too small";
        switch(m.attention){
            case CLEAR:return "OK";
            case CHECK:return "worth a look";
            case STRONG:return "CHECK CLOSELY";
            default:return m.unstable?"not judged (reading unsteady)":!m.stable?"not judged (unclear outline)":"not judged";
        }
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
        if(in.tooSmall)return String.format(Locale.US,"not measured: the 12 triangle is only %.0f px wide in this photo. Take a closer photo so the dial fills more of the frame.",Math.floor(in.trianglePx));
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
            case CLEAR:{
                double asym=Double.isFinite(in.spacing59)&&Double.isFinite(in.spacing01)?Math.abs(in.spacing01-in.spacing59):0;
                String spacing=asym<EVEN_SPACING?"even spacing either side"
                        :asym<GmtHumanQcMath.OFF_CENTRE_CHECK?"spacing either side slightly uneven, within what genuine photos show"
                        :"spacing either side uneven, but not consistently enough to flag";
                return "straight and centred. No visible rotation, "+spacing+"."+sp+caution;
            }
            case CHECK:{
                String border=Double.isFinite(in.rotationDeg)&&Math.abs(in.rotationDeg)<GmtHumanQcMath.GENUINE_ROTATION_SEEN_DEG&&kind(in)!=AlignmentKind.OFF_CENTRE
                        ?" Borderline: genuine watches photographed so far read up to about 1.6°."
                        :"";
                return "possibly "+describe(in)+". Look closely; a hand touching the triangle can cause this."+border+sp+caution;
            }
            case STRONG: return "visibly "+describe(in)+"."+sp+caution;
            default: return "could not be judged reliably on this photo.";
        }
    }

    /** Largest 59/01 spacing difference still described as even (half the genuine spread). */
    static final double EVEN_SPACING = 0.025;

    /** One baton's result for the summary (6 and 9, alpha59). */
    static final class Baton {
        final String label;final int before,after;
        boolean valid,stable,tooSmall,hand,offCentre,rotated,unstable;
        /** The photo is rated too angled (or its angle unknown), which withholds a clear verdict. */
        boolean angled;
        GmtHumanQcMath.Attention attention=GmtHumanQcMath.Attention.UNASSESSABLE;
        double centring=Double.NaN,rotationDeg=Double.NaN,gap=Double.NaN,widthPx=Double.NaN,centringMin=Double.NaN,centringMax=Double.NaN,rotMin=Double.NaN,rotMax=Double.NaN;
        String lowReason="";
        Baton(String label,int before,int after){this.label=label;this.before=before;this.after=after;}
        boolean flagged(){return valid&&(attention==GmtHumanQcMath.Attention.CHECK||attention==GmtHumanQcMath.Attention.STRONG);}
        boolean clear(){return valid&&attention==GmtHumanQcMath.Attention.CLEAR;}
    }

    static Baton six(Input in){
        Baton b=new Baton("6",29,31);
        b.valid=in.sixValid;b.stable=in.sixStable;b.tooSmall=in.sixTooSmall;b.hand=in.handAtSix;
        b.offCentre=in.sixOffCentre;b.rotated=in.sixRotated;b.unstable=in.sixUnstable;b.attention=in.sixAttention;
        b.centring=in.sixCentring;b.rotationDeg=in.sixRotationDeg;b.gap=in.sixGap;b.widthPx=in.sixWidthPx;
        b.angled=poorPose(in);
        b.centringMin=in.sixCentringMin;b.centringMax=in.sixCentringMax;b.rotMin=in.sixRotMin;b.rotMax=in.sixRotMax;b.lowReason=in.sixLowReason;
        return b;
    }

    static String sixLine(Input in){return batonLine(six(in));}

    static boolean poorPose(Input in){return in.pose==GmtHumanQcMath.PoseLabel.RETAKE||in.pose==GmtHumanQcMath.PoseLabel.UNASSESSABLE;}

    /**
     * Which way "towards the before tick" is for the viewer: at 6 the 29 tick is on the right;
     * at 9 the 44 tick is below.
     */
    private static String side(Baton b,boolean towardsBefore){
        if("9".equals(b.label))return towardsBefore?"low (towards the "+b.before+" tick)":"high (towards the "+b.after+" tick)";
        return towardsBefore?"right (towards the "+b.before+" tick)":"left (towards the "+b.after+" tick)";
    }

    static String batonLine(Baton b){
        String L=b.label;
        if(!b.valid)return "not measured on this photo (baton or minute track at "+L+" not found, often because a hand covers it).";
        if(b.tooSmall)return String.format(Locale.US,"not measured: the baton is only %.0f px wide in this photo.",Math.floor(b.widthPx));
        if(b.hand)return "not judged: a hand is next to the "+L+" baton.";
        boolean towardsBefore=b.centring>0;
        String sideWord="9".equals(L)?(towardsBefore?"sits low":"sits high"):"sits to the "+side(b,towardsBefore);
        String where="9".equals(L)?side(b,towardsBefore):side(b,towardsBefore);
        String nums=String.format(Locale.US," (offset %+.2f of its width, rotation %+.1f°)",b.centring,b.rotationDeg);
        if(b.unstable&&b.attention!=GmtHumanQcMath.Attention.CHECK)
            return Double.isFinite(b.centringMax)
                    ?String.format(Locale.US,"not judged: the reading changes when the photo is resized slightly (offset %+.2f to %+.2f of its width, rotation %+.1f° to %+.1f°), so it can't be trusted on this photo.",
                            b.centringMin,b.centringMax,b.rotMin,b.rotMax)
                    :"not judged: the baton is not found again when the photo is resized slightly, so the reading can't be trusted on this photo.";
        String why=b.lowReason==null||b.lowReason.isEmpty()?"":" ("+b.lowReason+")";
        String caution=b.stable?"":" Measured with low confidence"+why+", so treat this with caution.";
        String what;
        String turn=b.rotationDeg>0?"clockwise":"anticlockwise";
        if(b.offCentre&&b.rotated)what="off-centre, "+("9".equals(L)?where:"to the "+where)+", and rotated "+turn;
        else if(b.offCentre)what="off-centre: it "+("9".equals(L)?"sits "+where:sideWord);
        else if(b.rotated)what="rotated "+turn;
        else what="off-centre or rotated";
        switch(b.attention){
            case CLEAR: return "centred between the "+b.before+" and "+b.after+" ticks and straight."+nums;
            case CHECK: return "possibly "+what+". Look closely."+nums+caution;
            case STRONG: return "visibly "+what+"."+nums+caution;
            default: return b.angled&&b.stable?"not judged: the photo angle is too steep to clear the baton from this photo."+nums
                    :b.stable?"could not be judged reliably on this photo."+nums
                    :"not judged: "+(why.isEmpty()?"the "+L+" landmarks were measured with low confidence":b.lowReason)+"."+nums;
        }
    }

    /** Round markers (alpha61): what was flagged, what was clear, and why the rest were not judged. */
    static String roundLine(Input in){
        List<GmtRoundMarkerAnalyzer.Marker> ms=in.round;
        int found=0;for(GmtRoundMarkerAnalyzer.Marker m:ms)if(m.found)found++;
        if(ms.isEmpty()||found==0)return "not measured on this photo (the round markers or the minute ticks beside them were not found).";
        List<String> flags=new ArrayList<>(),clear=new ArrayList<>();
        boolean strong=false,sized=true;
        for(GmtRoundMarkerAnalyzer.Marker m:ms){
            if(m.flagged()){flags.add(roundFlag(m));strong|=m.attention==GmtHumanQcMath.Attention.STRONG;}
            else if(m.clear()){clear.add(String.valueOf(m.hour));sized&=Double.isFinite(m.sizeRatio);}
        }
        // "The same size" only when sizes were compared (markers large enough, alpha61).
        String same=sized?" and the same size":"";
        StringBuilder s=new StringBuilder();
        if(!flags.isEmpty()){
            s.append(strong?"visibly off-centre: ":"worth a look: ").append(String.join("; ",flags)).append(". Look closely.");
            if(!clear.isEmpty())s.append(" ").append(clear.size()==1?"The "+clear.get(0)+" is":"The "+join(clear)+" are").append(" centred.");
        }else if(!clear.isEmpty()){
            s.append(clear.size()==8?"all 8 centred on their minute ticks"+same+"."
                    :(clear.size()==1?"the "+clear.get(0)+" is":"the "+join(clear)+" are")+" centred on their minute ticks"+same+".");
        }
        String un=unjudgedRound(ms);
        if(!un.isEmpty())s.append(s.length()==0?"":" ").append(s.length()==0?Character.toUpperCase(un.charAt(0))+un.substring(1):un);
        String out=s.toString();
        return out.isEmpty()?"could not be judged reliably on this photo.":Character.toLowerCase(out.charAt(0))+out.substring(1);
    }

    private static String roundFlag(GmtRoundMarkerAnalyzer.Marker m){
        List<String> w=new ArrayList<>();
        if(m.offCentre){
            boolean cw=m.offset>0;
            w.add(String.format(Locale.US,"the %d sits %s, towards the %02d tick (offset %+.2f of its width)",m.hour,cw?"clockwise":"anticlockwise",cw?m.after():m.before(),m.offset));
        }
        if(m.sizeOdd)w.add(String.format(Locale.US,"%s %s than the other round markers (%.2f of their size)",m.offCentre?"and is":"the "+m.hour+" is",m.sizeRatio<1?"smaller":"larger",m.sizeRatio));
        if(w.isEmpty())return "the "+m.hour+" is off-centre or a different size";
        return String.join(" ",w);
    }

    /** "Not judged: the 2 and 11 (a hand is over them); the 7 (resized reading moves)." */
    static String unjudgedRound(List<GmtRoundMarkerAnalyzer.Marker> ms){
        java.util.LinkedHashMap<String,List<String>> by=new java.util.LinkedHashMap<>();
        for(GmtRoundMarkerAnalyzer.Marker m:ms){
            if(m.flagged()||m.clear())continue;
            String why=!m.found?"not found, often a hand over it"
                    :m.hand?"a hand is over or next to it"
                    :m.tooSmall?"too small in this photo"
                    :m.unstable?"the reading changes when the photo is resized slightly"
                    :!m.stable?shortLow(m.lowReason)
                    :m.note!=null&&m.note.contains("angled")?"the photo is too angled"
                    :"could not be judged reliably";
            by.computeIfAbsent(why,k->new ArrayList<>()).add(String.valueOf(m.hour));
        }
        if(by.isEmpty())return "";
        List<String> parts=new ArrayList<>();
        for(java.util.Map.Entry<String,List<String>> e:by.entrySet()){
            String why=e.getKey();
            if(e.getValue().size()>1)why=why.replace("a hand is over or next to it","a hand is over or next to them").replace("often a hand over it","often a hand over them");
            parts.add("the "+join(e.getValue())+" ("+why+")");
        }
        return "Not judged: "+String.join("; ",parts)+".";
    }

    /** Short, parenthesis-free form of a round marker's low-confidence reason, for grouping. */
    static String shortLow(String r){
        if(r==null||r.isEmpty())return "measured with low confidence";
        if(r.contains("not on a circle"))return "outline not clean: a hand, glare or a damaged edge";
        if(r.contains("outer edge"))return "only the lume could be traced, not the surround";
        if(r.contains("12 marker was not found"))return "the 12 was not found, so the dial orientation is unknown";
        if(r.contains("edge is faint"))return "the marker edge is faint";
        if(r.contains("ticks beside it"))return "the minute ticks beside it are unclear";
        if(r.contains("tick spacing")||r.contains("disagree"))return "the marker or its ticks were probably not found correctly";
        if(r.contains("minute tick at the marker"))return "the minute tick at the marker was not found";
        return r.replace("(","").replace(")","");
    }

    /** " 6 of the 8 round markers are centred." or "" (for bottom lines that otherwise omit them). */
    static String roundClearSentence(Input in){
        int rc=0;for(GmtRoundMarkerAnalyzer.Marker m:in.round)if(m.clear())rc++;
        if(rc==0)return "";
        return rc==8?" All 8 round markers are centred.":" "+rc+" of the 8 round markers are centred.";
    }

    static List<String> roundFlagItems(Input in){
        List<String> hours=new ArrayList<>();boolean size=false,off=false;
        for(GmtRoundMarkerAnalyzer.Marker m:in.round)if(m.flagged()){hours.add(String.valueOf(m.hour));size|=m.sizeOdd;off|=m.offCentre;}
        List<String> items=new ArrayList<>();
        if(hours.isEmpty())return items;
        String what=off&&size?"position and size":size?"size":"position";
        if(hours.size()<=2)for(String h:hours)items.add("the "+h+" marker "+what);
        else items.add("the round markers at "+join(hours)+" ("+what+")");
        return items;
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
        if(axis&&!Double.isFinite(b))return AlignmentKind.TURNED;   // no top-edge reading: describe the measured turn
        if(Double.isFinite(in.spacing59)&&Double.isFinite(in.spacing01)&&Math.abs(in.spacing01-in.spacing59)>=GmtHumanQcMath.OFF_CENTRE_CHECK)return AlignmentKind.OFF_CENTRE;
        if(axis)return AlignmentKind.TURNED;
        return AlignmentKind.UNCLEAR;
    }

    static String describe(Input in){
        String dir=Double.isFinite(in.rotationDeg)?(in.rotationDeg>0?"clockwise":"anticlockwise"):"";
        switch(kind(in)){
            case TURNED: return String.format(Locale.US,"rotated: the whole triangle is turned %s by about %.1f°, top edge included",dir,Math.abs(in.rotationDeg));
            case TIP_LEANS: return String.format(Locale.US,"skewed: the point leans %s by about %.1f° but the top edge is level",dir,Math.abs(in.rotationDeg));
            // The spacings are corner-to-tick distances, and the base is wider than the 59-01
            // gap, so each corner overhangs its tick: the triangle is shifted towards the side
            // with the LARGER spacing. Until alpha61 this named the other side (the user's
            // date-4 photo: shifted towards 01 by 2.7 px, reported as "closer to the 59 tick").
            case OFF_CENTRE: return "off-centre: the triangle sits towards the "+(in.spacing59>in.spacing01?"59":"01")+" tick side (to the "+(in.spacing59>in.spacing01?"left":"right")+" of the 60 tick)";
            default: return "rotated or off-centre";
        }
    }

    static String bottomLine(Input in){
        List<String> items=new ArrayList<>();
        boolean gapFlag=in.twelveValid&&(in.gap==GmtHumanQcMath.Attention.CHECK||in.gap==GmtHumanQcMath.Attention.STRONG);
        boolean alignFlag=in.twelveValid&&(in.alignment==GmtHumanQcMath.Attention.CHECK||in.alignment==GmtHumanQcMath.Attention.STRONG);
        Baton[] batons={six(in),in.nine};
        if(gapFlag)items.add("the gap at 12");
        if(alignFlag)items.add(kind(in)==AlignmentKind.OFF_CENTRE?"the 12 marker position (off-centre)":"the 12 marker alignment");
        List<String> batonFlags=new ArrayList<>();
        for(Baton b:batons)if(b.flagged())batonFlags.add("the "+b.label+" baton position");
        batonFlags.addAll(roundFlagItems(in));
        items.addAll(batonFlags);
        String six6=batonFlags.isEmpty()?"":" Separately, check "+join(batonFlags)+".";
        {   // When the 12 can't be checked, still say which other markers were (alpha61).
            List<String> ok=new ArrayList<>();
            for(Baton b:batons)if(b.clear())ok.add("the "+b.label+" baton");
            int rc=0;for(GmtRoundMarkerAnalyzer.Marker m:in.round)if(m.clear())rc++;
            if(rc>0)ok.add(rc==8?"all 8 round markers":rc+" of the 8 round markers");
            if(!ok.isEmpty()&&(!in.twelveValid||in.tooSmall||in.handAtTwelve))six6+=" Nothing flagged at "+joinOr(ok)+".";
        }
        if(!in.twelveValid)return "Bottom line: the 12 marker could not be checked on this photo. Try a clearer, straight-on photo with the hands away from 12."+six6;
        if(in.twelveValid&&in.tooSmall)return "Bottom line: the 12 marker is too small in this photo to check. Take a closer photo so the dial fills more of the frame."+six6;
        if(in.twelveValid&&in.handAtTwelve)return "Bottom line: a hand is covering the area around the 12 marker, so it could not be checked. Retake with the hands away from 12."+six6;
        String closer=in.gapResolutionLimited&&!in.gapUnstable?" The gap at 12 is too close to call at this resolution; a closer photo would settle it.":"";
        List<String> clearBatons=new ArrayList<>(),unjudged=new ArrayList<>();
        for(Baton b:batons){if(b.clear())clearBatons.add(b.label);else if(!b.flagged())unjudged.add(b.label);}
        boolean bothUnstable=in.gapUnstable&&in.rotUnstable;
        boolean anyUnstable=in.gapUnstable||in.rotUnstable;
        if(anyUnstable&&in.pose!=GmtHumanQcMath.PoseLabel.RETAKE){
            String what=bothUnstable?"the 12 reading":in.gapUnstable?"the 12 gap reading":"the 12 rotation reading";
            String unstable=what+" changes when the photo is resized slightly, so it isn't a reliable measurement here";
            if(items.isEmpty())return "Bottom line: nothing flagged, but "+unstable+". A closer, sharper, straight-on photo with the hands away from 12 usually fixes this."
                    +(clearBatons.isEmpty()?"":" The "+join(clearBatons)+(clearBatons.size()==1?" baton is":" batons are")+" centred and straight.")
                    +roundClearSentence(in);
            return "Bottom line: "+items.size()+(items.size()==1?" thing":" things")+" to check: "+join(items)+". "
                    +Character.toUpperCase(unstable.charAt(0))+unstable.substring(1)+", so confirm by eye.";
        }
        if(in.pose==GmtHumanQcMath.PoseLabel.RETAKE)
            return items.isEmpty()?"Bottom line: nothing flagged, but the photo is too angled to rely on that. Retake straight-on."
                    :"Bottom line: flagged "+join(items)+", but the photo is too angled to be sure. Retake straight-on.";
        List<String> clearAt=new ArrayList<>();clearAt.add("12");clearAt.addAll(clearBatons);
        int roundClear=0,roundUn=0;List<String> roundUnjudged=new ArrayList<>();
        for(GmtRoundMarkerAnalyzer.Marker m:in.round){if(m.clear())roundClear++;else if(!m.flagged()){roundUn++;roundUnjudged.add(String.valueOf(m.hour));}}
        if(in.round.isEmpty()){roundUn=8;}
        if(roundClear>0)clearAt.add(roundClear==8?"the round markers":"the "+roundClear+" round markers measured");
        List<String> notJudged=new ArrayList<>();
        if(!unjudged.isEmpty())notJudged.add("the "+join(unjudged)+(unjudged.size()==1?" baton":" batons"));
        if(roundUn==8)notJudged.add("the round markers");
        else if(roundUn>0)notJudged.add("the "+join(roundUnjudged)+" round "+(roundUn==1?"marker":"markers"));
        String rest=notJudged.isEmpty()?" Hands, bezel, date and printing are not checked, so look over those by eye."
                :" "+Character.toUpperCase(join(notJudged).charAt(0))+join(notJudged).substring(1)+" could not be judged here, so look over "+(notJudged.size()==1&&(unjudged.size()+roundUn)==1?"it":"them")+" and the rest of the dial by eye.";
        if(items.isEmpty())return (in.stableFrame?"Bottom line: nothing flagged at "+joinOr(clearAt)+"."+(closer.isEmpty()?rest:closer)
                :"Bottom line: nothing flagged, but the 12 marker was only measured with low confidence. A clearer photo with the hands away from 12 would help."+closer);
        String line="Bottom line: "+items.size()+(items.size()==1?" thing":" things")+" to check: "+join(items)+".";
        if(!in.stableFrame)line+=" Measured with low confidence, so confirm by eye or with a clearer photo with the hands away from 12.";
        return line+closer;
    }

    private static String joinOr(List<String> items){
        if(items.size()==1)return items.get(0);
        return String.join(", ",items.subList(0,items.size()-1))+" or "+items.get(items.size()-1);
    }

    private static String join(List<String> items){
        if(items.size()==1)return items.get(0);
        return String.join(", ",items.subList(0,items.size()-1))+" and "+items.get(items.size()-1);
    }
}
