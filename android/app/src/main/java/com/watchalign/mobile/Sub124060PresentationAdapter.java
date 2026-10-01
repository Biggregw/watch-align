package com.watchalign.mobile;

import java.util.Locale;

/**
 * Converts 124060 analysis output into the model-neutral input consumed by the mature measured
 * overlay/close-up renderer. Model-specific geometry and confidence stay in the Sub analyser;
 * presentation state ends here.
 */
final class Sub124060PresentationAdapter {
    static final String BANNER="124060 experimental · geometry measured · QC limits not calibrated";
    static final double MARKER_CENTER_R=0.817;

    private Sub124060PresentationAdapter(){}

    static MeasuredOverlayRenderer.Drawing adapt(Sub124060QcAnalyzer.Result r){
        MeasuredOverlayRenderer.Drawing d=new MeasuredOverlayRenderer.Drawing();
        d.measurementOnly=true;
        d.genericBatonsOnly=true;
        d.markerCenterR=MARKER_CENTER_R;
        d.bannerText=BANNER;
        if(r==null||!r.dialAssessable()||r.frame==null)return d;

        if(r.edge!=null){
            d.dialCx=r.edge.cx;d.dialCy=r.edge.cy;d.dialA=r.edge.axisA;d.dialB=r.edge.axisB;d.dialAngleDeg=r.edge.angleDeg;
        }else{
            d.dialCx=r.frame.cx;d.dialCy=r.frame.cy;d.dialA=r.frame.a;d.dialB=r.frame.b;
        }

        SubTwelveTriangle.Cand c=r.triangle;
        if(c!=null&&c.L!=null&&c.R!=null&&c.T!=null){
            d.twelve=new GmtTwelveLandmarkAnalyzer.Geometry(cloneP(c.L),cloneP(c.R),cloneP(c.T),
                    cloneP(r.tick59),cloneP(r.tick60),cloneP(r.tick01),!r.lumeOutline);
            d.gapValue=r.gapR;
            d.twelveRotationDeg=r.rotationDeg;
            d.gapMeasured=r.gapWithheld==null&&Double.isFinite(r.gapR);
            d.alignmentMeasured=(r.rotationWithheld==null&&Double.isFinite(r.rotationDeg))
                    ||(r.centringWithheld==null&&Double.isFinite(r.centringW));
            d.twelveMeasured=d.gapMeasured||d.alignmentMeasured;
            if(!d.twelveMeasured)d.notJudged=shortReason(r.twelveWithheld!=null?r.twelveWithheld:first(r.rotationWithheld,r.gapWithheld,r.centringWithheld));
            d.twelveMeasuredText=twelveText(r);
        }

        for(Sub124060QcAnalyzer.Baton b:r.batons){
            MeasuredOverlayRenderer.BatonDrawing x=new MeasuredOverlayRenderer.BatonDrawing();
            x.key=b.position.label;x.label=b.position.label;x.clockDeg=b.position.angleFromTwelveDeg;
            x.geometry=b.result!=null?b.result.geometry:null;
            x.measured=b.status==Sub124060QcAnalyzer.Status.FOUND;
            x.notJudged=x.measured?null:statusReason(b.status,b.note);
            if(x.measured&&b.result!=null&&b.result.valid){
                x.measuredText=String.format(Locale.US,"centring %+.3fw; rot %+.2f°; gap %.3f",b.result.centring,b.result.rotationDeg,b.result.gap);
            }
            d.batons.add(x);
        }

        double phi12=r.tick60!=null?r.frame.phiOf(r.tick60[0],r.tick60[1]):-Math.PI/2.0;
        for(Sub124060QcAnalyzer.Round rr:r.rounds){
            GmtRoundMarkerAnalyzer.Marker m=rr.marker;
            if(m==null)continue;
            if(!Double.isFinite(m.seedX)||!Double.isFinite(m.seedY)){
                double[] q=r.frame.at(phi12+Math.toRadians(30.0*m.hour),MARKER_CENTER_R);
                m.seedX=q[0];m.seedY=q[1];
            }
            if(!(m.expectedRadiusPx>0))m.expectedRadiusPx=0.086*r.frame.r;
            d.round.add(m);
            if(rr.status==Sub124060QcAnalyzer.Status.FOUND)d.roundMeasuredHours.add(m.hour);
            else d.roundNotJudged.put(m.hour,statusReason(rr.status,rr.note));
        }
        return d;
    }

    static String twelveText(Sub124060QcAnalyzer.Result r){
        StringBuilder s=new StringBuilder("12 MEASURED · not yet judged");
        java.util.List<String> v=new java.util.ArrayList<>();
        if(r.rotationWithheld==null&&Double.isFinite(r.rotationDeg))v.add(String.format(Locale.US,"rot %+.2f°",r.rotationDeg));
        if(r.gapWithheld==null&&Double.isFinite(r.gapR))v.add(String.format(Locale.US,"gap %.3fR",r.gapR));
        if(r.centringWithheld==null&&Double.isFinite(r.centringW))v.add(String.format(Locale.US,"centre %+.3fw",r.centringW));
        if(!v.isEmpty())s.append(" · ").append(String.join("; ",v));
        return s.toString();
    }

    static String statusReason(Sub124060QcAnalyzer.Status s,String note){
        switch(s){
            case HAND:return "hand in the way";
            case WRONG_PLACE:return "not found where expected";
            case LOW_CONFIDENCE:return shortReason(note!=null&&!note.isEmpty()?note:"low confidence");
            case NOT_FOUND:return shortReason(note!=null&&!note.isEmpty()?note:"not found");
            default:return shortReason(note);
        }
    }

    static String shortReason(String s){
        if(s==null||s.trim().isEmpty())return "not measured";
        String x=s.trim();
        if(x.startsWith("a hand"))return "hand in the way";
        if(x.contains("too small")||x.contains("only ")&&x.contains(" px"))return "too small";
        if(x.contains("not found"))return "not found";
        if(x.length()>42)x=x.substring(0,39)+"…";
        return x;
    }

    private static String first(String... xs){for(String x:xs)if(x!=null&&!x.isEmpty())return x;return "not measured";}
    private static double[] cloneP(double[] p){return p==null?null:p.clone();}
}
