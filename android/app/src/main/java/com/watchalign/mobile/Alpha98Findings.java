package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha98 plain-language findings for the results screen.
 *
 * Rule (owner decision 2026-10-07): a feature is reported as "outside the measured genuine range" only when it reads
 * further than every genuine reference watch for that feature. There are no tuned numbers: the limit is the furthest
 * genuine reference watch (Alpha98Reference, Alpha97TwelveReadout). Wording never says genuine / fake; it compares
 * measurements with genuine watches. Round-marker and ring-shift offsets inflate on small dials, so for those a photo is
 * compared only with genuine watches photographed at a similar or lower resolution (dial radius <= 1.3 x this photo's);
 * fewer than 8 such watches -> not assessed. Fail-closed: anything the measurement withheld is "not assessed" with a
 * reason, never guessed. Read-only over the unchanged Alpha96 measurement.
 */
final class Alpha98Findings {
    enum Status{OUTSIDE,WITHIN,NOT_ASSESSED}
    enum Shape{BATON,TRIANGLE,ROUND,RING,DATE}

    static final double RES_MATCH=1.3;
    static final int MIN_MATCHED=8;
    static final String CHECK_HINT="Check the close-up: a hand, reflection or dust over the marker can also cause this.";

    static final class Finding {
        final String key,title;Status status=Status.WITHIN;
        final List<String> lines=new ArrayList<>();
        String reason="";
        /** Close-up region in canonical dial units (dial radius 1, 12 at the top) and what outline to draw. */
        double cx,cy,half;Shape shape;int hour;
        Finding(String key,String title){this.key=key;this.title=title;}
        String text(){
            if(status==Status.NOT_ASSESSED)return title+": not assessed - "+reason+".";
            if(status==Status.WITHIN)return title+": within the measured genuine range.";
            return title+": "+String.join(" ",lines);
        }
    }

    static final class Summary {
        final List<Finding> all=new ArrayList<>();
        List<Finding> outside(){List<Finding> o=new ArrayList<>();for(Finding f:all)if(f.status==Status.OUTSIDE)o.add(f);return o;}
        List<Finding> within(){List<Finding> o=new ArrayList<>();for(Finding f:all)if(f.status==Status.WITHIN)o.add(f);return o;}
        List<Finding> notAssessed(){List<Finding> o=new ArrayList<>();for(Finding f:all)if(f.status==Status.NOT_ASSESSED)o.add(f);return o;}
        String headline(){
            int n=outside().size();
            if(n==0)return "No measured feature is outside the measured genuine range.";
            return n+(n==1?" feature is":" features are")+" outside the measured genuine range.";
        }
    }

    static final String DISCLAIMER="These results compare this photo's measurements with genuine watches. They are not a verdict on authenticity.";

    private Alpha98Findings(){}

    static Summary build(Alpha94MarkerMeasurement.Report r,Alpha98DateWindow.Result date){
        Summary s=new Summary();
        double R=r==null?Double.NaN:r.dialRadiusPx;
        s.all.add(twelve(r));
        s.all.add(baton(r,6,R));
        s.all.add(baton(r,9,R));
        s.all.add(rounds(r,R));
        s.all.add(ring(r,R));
        s.all.add(date(date));
        return s;
    }

    // ------------------------------------------------------------------ features
    static Finding twelve(Alpha94MarkerMeasurement.Report r){
        Finding f=new Finding("twelve","12 o'clock triangle");
        f.cx=0;f.cy=-0.75;f.half=0.22;f.shape=Shape.TRIANGLE;f.hour=12;
        Alpha97TwelveReadout.Result t=Alpha97TwelveReadout.from(r);
        if(!t.usable){f.status=Status.NOT_ASSESSED;f.reason=reasonFor(t.reason);return f;}
        int n=Alpha97TwelveReadout.N_WATCHES;
        if(t.atLeastCentreline==0)f.lines.add(String.format(Locale.US,"It points %.1f° %s of the genuine direction - further than all %d genuine reference watches (largest %.1f°).",
                Math.abs(t.centrelineDeg),cw(t.centrelineDeg),n,max(Alpha97TwelveReadout.GENUINE_CENTRELINE_DEG)));
        if(t.atLeastSides==0){boolean left=Math.abs(t.leftSideDeg)>=Math.abs(t.rightSideDeg);double v=left?t.leftSideDeg:t.rightSideDeg;
            f.lines.add(String.format(Locale.US,"Its %s side is angled %.1f° %s - further than all %d genuine reference watches (largest %.1f°).",
                    left?"left":"right",Math.abs(v),cw(v),n,max(Alpha97TwelveReadout.GENUINE_SIDES_DEG)));}
        if(t.atLeastLateral==0)f.lines.add(String.format(Locale.US,"It sits %.1f px to the %s (%.2f%% of the dial radius) - further than all %d genuine reference watches (largest %.2f%%).",
                Math.abs(t.lateralPx),t.lateralPx<0?"left":"right",100*Math.abs(t.lateralR),n,100*max(Alpha97TwelveReadout.GENUINE_LATERAL_R)));
        if(!f.lines.isEmpty()){f.status=Status.OUTSIDE;f.lines.add(CHECK_HINT);}
        return f;
    }

    static Finding baton(Alpha94MarkerMeasurement.Report r,int hour,double R){
        Finding f=new Finding(hour==6?"six":"nine",hour+" o'clock marker");
        double a=Math.toRadians(hour*30.0);f.cx=Math.sin(a)*Alpha92GmtMaster.BATON_CENTER_R;f.cy=-Math.cos(a)*Alpha92GmtMaster.BATON_CENTER_R;
        f.half=0.22;f.shape=Shape.BATON;f.hour=hour;
        Alpha94MarkerMeasurement.Marker m=r==null?null:r.atHour(hour);
        if(m==null||!m.usable){f.status=Status.NOT_ASSESSED;f.reason=reasonFor(m==null?"unavailable":m.reason);return f;}
        double[] rotRef=hour==6?Alpha98Reference.SIX_ROT_FAR:Alpha98Reference.NINE_ROT_FAR;
        double[] offRef=hour==6?Alpha98Reference.SIX_OFF_FAR:Alpha98Reference.NINE_OFF_FAR;
        double nom=hour==6?Alpha98Reference.NOMINAL_SIX_ROT:Alpha98Reference.NOMINAL_NINE_ROT;
        double rot=m.rotationDeg-nom;
        if(Double.isFinite(rot)&&beyond(Math.abs(rot),max(rotRef)))
            f.lines.add(String.format(Locale.US,"It is rotated %.1f° %s - further than all %d genuine reference watches (largest %.1f°).",
                    Math.abs(rot),cw(rot),rotRef.length,max(rotRef)));
        double off=m.localOffsetPx/R;
        if(Double.isFinite(off)&&beyond(off,max(offRef)))
            f.lines.add(String.format(Locale.US,"It sits %.1f px out of place relative to the other markers (%.2f%% of the dial radius, mostly %s) - further than all %d genuine reference watches (largest %.2f%%).",
                    m.localOffsetPx,100*off,direction(m),offRef.length,100*max(offRef)));
        if(!f.lines.isEmpty()){f.status=Status.OUTSIDE;f.lines.add(CHECK_HINT);}
        return f;
    }

    static Finding rounds(Alpha94MarkerMeasurement.Report r,double R){
        Finding f=new Finding("rounds","Round hour markers");f.shape=Shape.ROUND;f.half=0.17;
        if(r==null){f.status=Status.NOT_ASSESSED;f.reason="no measurement";return f;}
        int usable=0;Alpha94MarkerMeasurement.Marker worst=null;
        for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind)&&m.usable&&Double.isFinite(m.localOffsetPx)){usable++;if(worst==null||m.localOffsetPx>worst.localOffsetPx)worst=m;}
        if(usable<5||worst==null){f.status=Status.NOT_ASSESSED;f.reason="too few round markers could be measured cleanly";return f;}
        double a=Math.toRadians(worst.hour*30.0);f.cx=Math.sin(a)*Alpha92GmtMaster.ROUND_CENTER_R;f.cy=-Math.cos(a)*Alpha92GmtMaster.ROUND_CENTER_R;f.hour=worst.hour;
        double lim=matchedMax(Alpha98Reference.ROUNDS_OFF_FAR,Alpha98Reference.ROUNDS_OFF_R,R);int n=matchedCount(Alpha98Reference.ROUNDS_OFF_R,R);
        if(n<MIN_MATCHED){f.status=Status.NOT_ASSESSED;f.reason="the photo's resolution is too low to compare round markers with genuine photos";return f;}
        double off=worst.localOffsetPx/R;
        if(beyond(off,lim)){f.status=Status.OUTSIDE;
            f.lines.add(String.format(Locale.US,"The %d o'clock round marker sits %.1f px out of place (%.2f%% of the dial radius, mostly %s) - further than all %d comparable genuine reference watches (largest %.2f%%).",
                    worst.hour,worst.localOffsetPx,100*off,direction(worst),n,100*lim));
            f.lines.add(CHECK_HINT);}
        return f;
    }

    static Finding ring(Alpha94MarkerMeasurement.Report r,double R){
        Finding f=new Finding("ring","Marker ring");f.shape=Shape.RING;f.cx=0;f.cy=0;f.half=1.05;
        Alpha94MarkerMeasurement.Ring g=r==null?null:r.ring;
        if(g==null||!g.usable){f.status=Status.NOT_ASSESSED;f.reason="too few markers could be measured cleanly";return f;}
        double rot=g.rotationDeg-Alpha98Reference.NOMINAL_RING_ROT;
        if(Double.isFinite(rot)&&beyond(Math.abs(rot),max(Alpha98Reference.RING_ROT_FAR)))
            f.lines.add(String.format(Locale.US,"The hour markers as a set are turned %.2f° %s relative to the printed minute track - further than all %d genuine reference watches (largest %.2f°).",
                    Math.abs(rot),cw(rot),Alpha98Reference.RING_ROT_FAR.length,max(Alpha98Reference.RING_ROT_FAR)));
        int n=matchedCount(Alpha98Reference.RING_SHIFT_R,R);
        if(n>=MIN_MATCHED){double lim=matchedMax(Alpha98Reference.RING_SHIFT_FAR,Alpha98Reference.RING_SHIFT_R,R);double sh=g.shiftPx/R;
            if(Double.isFinite(sh)&&beyond(sh,lim))f.lines.add(String.format(Locale.US,"The hour markers as a set are off-centre by %.1f px (%.2f%% of the dial radius) - further than all %d comparable genuine reference watches (largest %.2f%%).",
                    g.shiftPx,100*sh,n,100*lim));}
        if(!f.lines.isEmpty()){f.status=Status.OUTSIDE;f.lines.add("Check the full overlay as well as the close-up.");}
        return f;
    }

    static Finding date(Alpha98DateWindow.Result d){
        Finding f=new Finding("date","Date window");f.shape=Shape.DATE;f.half=0.30;
        if(d==null||!d.usable){f.status=Status.NOT_ASSESSED;f.reason=dateReason(d==null?"":d.reason);
            f.cx=d!=null&&Double.isFinite(d.cx)?d.cx:Alpha98DateWindow.EXP_X;f.cy=d!=null&&Double.isFinite(d.cy)?d.cy:0;return f;}
        f.cx=d.cx;f.cy=d.cy;
        double t=d.windowTiltDeg-Alpha98Reference.NOMINAL_DATE_TILT;
        if(beyond(Math.abs(t),max(Alpha98Reference.DATE_TILT_FAR))){f.status=Status.OUTSIDE;
            f.lines.add(String.format(Locale.US,"It is tilted %.1f° %s relative to the dial - further than all %d genuine reference watches (largest %.1f°).",
                    Math.abs(t),cw(t),Alpha98Reference.DATE_TILT_FAR.length,max(Alpha98Reference.DATE_TILT_FAR)));
            f.lines.add("Check the close-up: glare on the magnifier can also cause this.");}
        return f;
    }

    // ------------------------------------------------------------------ wording helpers
    static String cw(double deg){return deg>=0?"clockwise":"anticlockwise";}

    /** Dominant direction of a marker's local offset in plain words (radial + = outward, tangential + = clockwise). */
    static String direction(Alpha94MarkerMeasurement.Marker m){
        double rd=m.localRadialPx,tg=m.localTangentialPx;
        if(!Double.isFinite(rd)||!Double.isFinite(tg))return "in no single direction";
        if(Math.abs(rd)>=Math.abs(tg))return rd>=0?"towards the edge of the dial":"towards the centre";
        return tg>=0?"clockwise":"anticlockwise";
    }

    static String reasonFor(String raw){
        String s=raw==null?"":raw.toLowerCase(Locale.US);
        if(s.contains("occluded")||s.contains("hand")||s.contains("occluder"))return "covered by a hand or not clearly visible";
        if(s.contains("coverage")||s.contains("clean"))return "the marker's edge is not clear enough (glare, blur or a hand)";
        if(s.contains("ring"))return "too few other markers could be measured to compare it with";
        return "it could not be measured reliably in this photo";
    }

    static String dateReason(String raw){
        String s=raw==null?"":raw.toLowerCase(Locale.US);
        if(s.contains("glare")||s.contains("reflection")||s.contains("rectangular"))return "glare or a reflection on the magnifier";
        if(s.contains("hand")||s.contains("mid-change")||s.contains("cut"))return "a hand or a date change covers the date";
        if(s.contains("pose"))return "the dial could not be located";
        return "the date window could not be found clearly";
    }

    static double max(double[] v){double m=Double.NEGATIVE_INFINITY;for(double x:v)if(Double.isFinite(x))m=Math.max(m,x);return m;}
    /** Strictly beyond the furthest genuine reference watch, allowing for the 6-decimal storage of the reference values
     *  (a reference watch's own photo must never read as beyond itself). */
    static boolean beyond(double v,double lim){return v>lim+Math.max(1e-6,1e-4*Math.abs(lim));}
    static int matchedCount(double[] refR,double R){int n=0;for(double x:refR)if(Double.isFinite(x)&&x<=RES_MATCH*R)n++;return n;}
    static double matchedMax(double[] far,double[] refR,double R){double m=Double.NEGATIVE_INFINITY;for(int i=0;i<far.length;i++)if(Double.isFinite(refR[i])&&refR[i]<=RES_MATCH*R)m=Math.max(m,far[i]);return m;}
}
