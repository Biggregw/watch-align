package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * The one family-agnostic layout rule (measurement contract v2, section 5). Families supply
 * observations and candidate layout IDs; this turns them into hypotheses and one compatibility
 * result. Absence of a feature is never evidence: only a present, high-confidence observation can
 * support or contradict a layout. The Python validator re-checks the result as an invariant.
 */
final class LayoutCompatibility {
    enum Result {
        COMPATIBLE("compatible"),UNRESOLVED("unresolved"),INCOMPATIBLE("incompatible"),CONFLICTING("conflicting");
        final String wire;
        Result(String w){wire=w;}
    }

    private LayoutCompatibility(){}

    /** One hypothesis per candidate layout, in the order given. */
    static List<MeasurementDecisions.Hypothesis> hypotheses(List<String> layouts,List<MeasurementDecisions.Observation> observations){
        List<MeasurementDecisions.Hypothesis> out=new ArrayList<>();
        for(String layout:layouts){
            List<String> support=new ArrayList<>(),contradiction=new ArrayList<>();
            for(MeasurementDecisions.Observation o:observations){
                if(!o.decides())continue;
                if(o.supports.contains(layout))support.add(o.feature);
                if(o.contradicts.contains(layout))contradiction.add(o.feature);
            }
            if(!support.isEmpty())out.add(new MeasurementDecisions.Hypothesis(layout,MeasurementDecisions.Hypothesis.SUPPORTED,support));
            else if(!contradiction.isEmpty())out.add(new MeasurementDecisions.Hypothesis(layout,MeasurementDecisions.Hypothesis.CONTRADICTED,contradiction));
            else out.add(new MeasurementDecisions.Hypothesis(layout,MeasurementDecisions.Hypothesis.UNKNOWN,new ArrayList<String>()));
        }
        return out;
    }

    /**
     * compatible: the expected layout is supported and no other layout is;
     * incompatible: another layout is supported and the expected one is not;
     * conflicting: both are supported; unresolved: everything else.
     */
    static Result resolve(String expectedLayoutId,List<MeasurementDecisions.Hypothesis> hypotheses){
        boolean expected=false,other=false;
        for(MeasurementDecisions.Hypothesis h:hypotheses){
            if(!MeasurementDecisions.Hypothesis.SUPPORTED.equals(h.state))continue;
            if(h.layoutId.equals(expectedLayoutId))expected=true;else other=true;
        }
        if(expected&&other)return Result.CONFLICTING;
        if(expected)return Result.COMPATIBLE;
        if(other)return Result.INCOMPATIBLE;
        return Result.UNRESOLVED;
    }

    static boolean quarantined(Result r){return r==Result.INCOMPATIBLE||r==Result.CONFLICTING;}
}
