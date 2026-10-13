package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plain-language presentation of the existing results (display only). It never changes a finding, a measurement or
 * a withheld state: it only decides what the simple screens say about them.
 *
 * A marker is called "Within genuine range" only when its existing finding is WITHIN and nothing about it was
 * withheld (no part not assessed, and its area comparison was cleared). Otherwise it is "Partly assessed".
 * Outside-genuine readings below the existing visibility guideline (MINOR) are never called within range.
 */
final class InspectionView {
    private InspectionView(){}

    enum State{CLEAR,WORTH,MINOR,PARTLY,WITHIN,NOT_ASSESSED}

    static final String SHORT_DISCLAIMER="A measurement comparison with genuine watches, not an authenticity verdict.";
    static final String LEGEND_MEASURED="Yellow: expected genuine outline.  Cyan: detected edges.  Orange: extra metal.  Magenta: missing metal.  Dashed grey: corners inferred, not seen.";
    static final String LEGEND_WITHHELD="Yellow: expected genuine outline. The detected outline was not reliable enough to compare areas.";

    /** An individual marker outline (baton, triangle, round) as opposed to a dial-wide feature (ring, date, sizes). */
    static boolean marker(Alpha99Findings.Finding f){
        return !f.dialWide&&(f.shape==Alpha99Findings.Shape.BATON||f.shape==Alpha99Findings.Shape.TRIANGLE||f.shape==Alpha99Findings.Shape.ROUND);
    }
    /** The marker's own area comparison; null for features that have none (never another marker's result by hour). */
    static MarkerMismatchResearch.Result result(Alpha99Findings.Finding f,Map<Integer,MarkerMismatchResearch.Result> mismatch){
        return mismatch==null||!marker(f)?null:mismatch.get(f.hour);
    }

    static State state(Alpha99Findings.Finding f,MarkerMismatchResearch.Result q){
        switch(f.status){
            case CLEAR:return State.CLEAR;
            case WORTH:return State.WORTH;
            case MINOR:return State.MINOR;
            case NOT_ASSESSED:return State.NOT_ASSESSED;
            default:
                if(f.partNotAssessed!=null)return State.PARTLY;
                if(marker(f)&&(q==null||!q.trusted()))return State.PARTLY;
                return State.WITHIN;
        }
    }

    static String label(State s){
        switch(s){
            case CLEAR:return "Clear deviation";
            case WORTH:return "Worth a look";
            case MINOR:return "Minor difference";
            case PARTLY:return "Partly assessed";
            case NOT_ASSESSED:return "Not assessed";
            default:return "Within genuine range";
        }
    }

    /** One plain sentence under the label. */
    static String explain(Alpha99Findings.Finding f,MarkerMismatchResearch.Result q){
        State s=state(f,q);
        switch(s){
            case NOT_ASSESSED:return capital(f.shortReason==null||f.shortReason.isEmpty()?f.reason:f.shortReason)+".";
            case MINOR:return "Outside the genuine range, but below the visibility guideline.";
            case PARTLY:{
                String assessed=f.partNotAssessed!=null?(f.partAssessed!=null?f.partAssessed:"rotation"):"position and rotation";
                String missing=f.partNotAssessed!=null?f.partNotAssessed+(q!=null&&q.trusted()?"":" and area"):"area";
                return capital(assessed)+" within genuine range; "+missing+" not assessed.";
            }
            case WITHIN:return marker(f)?"Position, rotation and outline within genuine range.":"Within the measured genuine range.";
            default:return null;
        }
    }

    /** The marker's measured findings outside the genuine range, one short line each (e.g. "Rotated 1.2° anticlockwise"). */
    static List<String> findings(Alpha99Findings.Finding f){
        List<String> out=new ArrayList<>();
        if(f.status==Alpha99Findings.Status.NOT_ASSESSED)return out;
        for(Alpha99Findings.Measure m:f.measures){
            if(!m.outside())continue;
            String st=m.status()==Alpha99Findings.Status.CLEAR?"clear deviation":m.status()==Alpha99Findings.Status.WORTH?"worth a look":"minor";
            String line=capital(stripSubject(m.sentence)).replaceAll(" \\([^)]*\\)","");int stop=line.indexOf(". ");if(stop>0)line=line.substring(0,stop);
            out.add(line+" ("+st+")");
        }
        return out;
    }

    /** "Area mismatch 3.04%" when the comparison was cleared, else "Area: not assessed"; null when not applicable. */
    static String area(Alpha99Findings.Finding f,MarkerMismatchResearch.Result q){
        if(!marker(f))return null;
        if(q!=null&&q.trusted())return String.format(Locale.US,"Area mismatch %.2f%%",q.score.mismatchPct);
        return "Area: not assessed";
    }

    static String areaParts(MarkerMismatchResearch.Result q){
        if(q==null||!q.trusted())return null;
        return String.format(Locale.US,"%.2f%% extra metal  +  %.2f%% missing metal, of the expected marker area",q.score.extraPct,q.score.missingPct);
    }

    /** Short reason an area comparison was not made. */
    static String areaReason(Alpha99Findings.Finding f,MarkerMismatchResearch.Result q){
        if(!marker(f)||(q!=null&&q.trusted()))return null;
        if(f.shape==Alpha99Findings.Shape.TRIANGLE)return "The triangle's tip and corners are inferred, not seen, so its area is not compared.";
        String r=q==null?"":q.reason==null?"":q.reason;
        if(r.startsWith("hand/glare")||r.contains("interference"))return "A hand, glare or reflection touches this marker.";
        if(r.startsWith("partial measurement withheld"))return "Its opposite edges disagree (often a reflection on the polished edge), so its outline is not trusted.";
        if(r.startsWith("photo/edge reliability limited"))return "The photo or edge detail is not reliable enough here.";
        if(r.startsWith("marker ring unavailable"))return "The marker ring could not be placed in this photo.";
        if(r.startsWith("measurement was not assessed")||r.startsWith("unmeasured"))return "This marker could not be measured.";
        if(r.isEmpty())return "This marker's outline could not be compared.";
        return capital(r)+".";
    }

    static String headline(Alpha99Findings.Summary s,Map<Integer,MarkerMismatchResearch.Result> mismatch){
        int flagged=0;for(Alpha99Findings.Finding f:s.all){State st=state(f,result(f,mismatch));if(st==State.CLEAR||st==State.WORTH)flagged++;}
        return flagged==0?"No markers flagged":flagged+(flagged==1?" marker flagged":" markers flagged");
    }

    /** e.g. "1 clear · 1 worth a look · 2 not assessed · 1 partly assessed · 8 within genuine range". */
    static String counts(Alpha99Findings.Summary s,Map<Integer,MarkerMismatchResearch.Result> mismatch){
        int[] n=new int[State.values().length];
        for(Alpha99Findings.Finding f:s.all){if(f.dialWide&&f.group!=null&&f.status==Alpha99Findings.Status.NOT_ASSESSED)continue;n[state(f,result(f,mismatch)).ordinal()]++;}
        StringBuilder b=new StringBuilder();
        add(b,n[State.CLEAR.ordinal()],"clear deviation","clear deviations");
        add(b,n[State.WORTH.ordinal()],"worth a look","worth a look");
        add(b,n[State.NOT_ASSESSED.ordinal()],"not assessed","not assessed");
        add(b,n[State.PARTLY.ordinal()],"partly assessed","partly assessed");
        add(b,n[State.MINOR.ordinal()],"minor","minor");
        add(b,n[State.WITHIN.ordinal()],"within genuine range","within genuine range");
        return b.toString();
    }

    private static void add(StringBuilder b,int n,String one,String many){if(n==0)return;if(b.length()>0)b.append("  ·  ");b.append(n).append(' ').append(n==1?one:many);}
    private static String stripSubject(String s){
        if(s==null)return "";
        for(String p:new String[]{"It is ","It sits ","It reads ","Its ","It "})if(s.startsWith(p))return p.equals("Its ")?s:s.substring(p.length());
        return s;
    }
    static String capital(String s){return s==null||s.isEmpty()?"":Character.toUpperCase(s.charAt(0))+s.substring(1);}
}
