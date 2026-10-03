package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Measurement-contract v2 decision-stream driver for the current Submariner 124060 route.
 *
 * This driver serialises the authoritative shared-Java decision object only. It does not
 * reimplement a metric, reliability gate, reason, layout rule or verdict. Python joins frozen
 * provenance/snapshot identity and writes the canonical contract JSONL.
 *
 * Input list TSV columns:
 *   photo_key<TAB>physical_watch_id<TAB>workspace_path
 *
 * Usage:
 *   tools/desktop-harness/run.sh CalibDecisionV2 <model> <list.tsv> <out.jsonl>
 */
public final class CalibDecisionV2 {
    private CalibDecisionV2(){}

    public static void main(String[] a)throws Exception{
        if(a.length!=3)throw new IllegalArgumentException(
                "usage: CalibDecisionV2 <model> <list.tsv> <out.jsonl>");
        String model=a[0].trim().toUpperCase(Locale.US);
        if(!"124060".equals(model)||!Sub124060QcAnalyzer.supports(model))
            throw new IllegalArgumentException(
                    "model "+model+" is not supported by the v2 Submariner decision stream");

        nu.pattern.OpenCV.loadLocally();
        try(PrintWriter w=new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(a[2]),StandardCharsets.UTF_8))){
            for(String line:Files.readAllLines(Path.of(a[1]),StandardCharsets.UTF_8)){
                if(line.trim().isEmpty())continue;
                String[] f=line.split("\\t",3);
                if(f.length!=3||f[0].trim().isEmpty()||f[1].trim().isEmpty()||f[2].trim().isEmpty())
                    throw new IllegalArgumentException("decision list rows require photo_key, physical_watch_id and path");
                emitPhoto(w,f[0].trim(),f[1].trim(),model,f[2].trim());
            }
        }
    }

    static void emitPhoto(PrintWriter w,String photoKey,String physicalWatchId,String model,String path){
        MeasurementDecisions.Photo decision;
        try{
            Bitmap b=Load.photo(path);
            if(b==null){
                decision=MeasurementDecisions.inputFailure(
                        CoreReasons.IMAGE_UNREADABLE,"image unreadable",
                        Sub124060Layout.EXPECTED_LAYOUT,Sub124060Calibration.METRICS);
            }else{
                FullResSource full=Load.fullSource(path);
                WatchAlignCoreV13.AnalysisResult result=WatchAlignCoreV13.analyse(
                        b,Collections.<Bitmap>emptyList(),model,full);
                if(result==null||result.sub124060==null){
                    decision=MeasurementDecisions.inputFailure(
                            CoreReasons.ANALYSIS_EXCEPTION,"configured model returned no Submariner measurement result",
                            Sub124060Layout.EXPECTED_LAYOUT,Sub124060Calibration.METRICS);
                }else decision=Sub124060Calibration.decide(result.sub124060);
            }
        }catch(Throwable t){
            decision=MeasurementDecisions.inputFailure(
                    CoreReasons.ANALYSIS_EXCEPTION,"analysis failed: "+t.getClass().getSimpleName(),
                    Sub124060Layout.EXPECTED_LAYOUT,Sub124060Calibration.METRICS);
        }

        StringBuilder s=new StringBuilder(8192);
        s.append('{');
        field(s,"photo_key",photoKey);s.append(',');
        field(s,"physical_watch_id",physicalWatchId);s.append(',');
        field(s,"model",model);s.append(',');
        s.append("\"stages\":");appendStages(s,decision.stages);s.append(',');
        s.append("\"layout\":");appendLayout(s,decision.layout);s.append(',');
        s.append("\"metrics\":");appendMetrics(s,decision.metrics);
        s.append('}');
        w.println(s.toString());
        w.flush();
    }

    static void appendStages(StringBuilder s,List<MeasurementDecisions.Stage> stages){
        s.append('[');
        for(int i=0;i<stages.size();i++){
            if(i>0)s.append(',');
            MeasurementDecisions.Stage st=stages.get(i);
            s.append('{');
            field(s,"stage",st.id);s.append(',');
            field(s,"outcome",st.outcome==null?null:st.outcome.wire);s.append(',');
            s.append("\"reason\":");appendReason(s,st.reason);s.append(',');
            s.append("\"diagnostics\":");appendMap(s,st.diagnostics);
            s.append('}');
        }
        s.append(']');
    }

    static void appendLayout(StringBuilder s,MeasurementDecisions.Layout l){
        if(l==null){s.append("null");return;}
        s.append('{');
        s.append("\"expected\":");
        if(l.expected==null)s.append("null");
        else{
            s.append('{');
            field(s,"layout_id",l.expected.layoutId);s.append(',');
            s.append("\"version\":").append(l.expected.version);
            s.append('}');
        }
        s.append(",\"observations\":[");
        for(int i=0;i<l.observations.size();i++){
            if(i>0)s.append(',');
            MeasurementDecisions.Observation o=l.observations.get(i);
            s.append('{');
            field(s,"feature",o.feature);s.append(',');
            s.append("\"present\":").append(o.present).append(',');
            s.append("\"high_confidence\":").append(o.highConfidence).append(',');
            field(s,"detector_id",o.detectorId);s.append(',');
            s.append("\"detector_version\":").append(o.detectorVersion).append(',');
            s.append("\"supports\":");appendStrings(s,o.supports);s.append(',');
            s.append("\"contradicts\":");appendStrings(s,o.contradicts);
            s.append('}');
        }
        s.append("],\"hypotheses\":[");
        for(int i=0;i<l.hypotheses.size();i++){
            if(i>0)s.append(',');
            MeasurementDecisions.Hypothesis h=l.hypotheses.get(i);
            s.append('{');
            field(s,"layout_id",h.layoutId);s.append(',');
            field(s,"state",h.state);s.append(',');
            s.append("\"decided_by\":");appendStrings(s,h.decidedBy);
            s.append('}');
        }
        s.append("],");
        field(s,"compatibility",l.compatibility==null?null:l.compatibility.wire);s.append(',');
        field(s,"reliability",l.reliability);s.append(',');
        s.append("\"quarantined\":").append(l.quarantined);
        s.append('}');
    }

    static void appendMetrics(StringBuilder s,List<MeasurementDecisions.Metric> metrics){
        s.append('[');
        for(int i=0;i<metrics.size();i++){
            if(i>0)s.append(',');
            MeasurementDecisions.Metric m=metrics.get(i);
            s.append('{');
            field(s,"metric_id",m.id);s.append(',');
            field(s,"state",m.state==null?null:m.state.wire);s.append(',');
            field(s,"terminal_stage",m.terminalStage);s.append(',');
            field(s,"origin",m.photoOrigin?"photo":"metric");s.append(',');
            s.append("\"reason\":");appendReason(s,m.reason);s.append(',');
            s.append("\"secondary_reasons\":[");
            for(int j=0;j<m.secondary.size();j++){
                if(j>0)s.append(',');
                appendReason(s,m.secondary.get(j));
            }
            s.append("],\"raw_value\":");appendDouble(s,m.raw);s.append(',');
            s.append("\"eligible_value\":");appendDouble(s,m.eligible);s.append(',');
            s.append("\"raw_support\":");appendMap(s,m.rawSupport);s.append(',');
            s.append("\"eligible_support\":");appendMap(s,m.eligibleSupport);s.append(',');
            s.append("\"diagnostics\":");appendMap(s,m.diagnostics);
            s.append('}');
        }
        s.append(']');
    }

    static void appendReason(StringBuilder s,MeasurementDecisions.Reason r){
        if(r==null){s.append("null");return;}
        s.append('{');
        field(s,"code",r.code);s.append(',');
        field(s,"subject",r.subject);s.append(',');
        field(s,"detail",r.detail);
        s.append('}');
    }

    static void appendMap(StringBuilder s,Map<String,Object> map){
        s.append('{');
        List<String> keys=new ArrayList<>(map.keySet());
        Collections.sort(keys);
        for(int i=0;i<keys.size();i++){
            if(i>0)s.append(',');
            String key=keys.get(i);
            quote(s,key);s.append(':');appendValue(s,map.get(key));
        }
        s.append('}');
    }

    static void appendStrings(StringBuilder s,List<String> values){
        s.append('[');
        for(int i=0;i<values.size();i++){
            if(i>0)s.append(',');
            quote(s,values.get(i));
        }
        s.append(']');
    }

    static void appendValue(StringBuilder s,Object v){
        if(v==null){s.append("null");return;}
        if(v instanceof Boolean){s.append(((Boolean)v).booleanValue());return;}
        if(v instanceof Byte||v instanceof Short||v instanceof Integer||v instanceof Long){
            s.append(String.valueOf(v));return;
        }
        if(v instanceof Number){appendDouble(s,((Number)v).doubleValue());return;}
        quote(s,String.valueOf(v));
    }

    /** Preserve the existing six-fractional-digit Java/Python measurement boundary. */
    static void appendDouble(StringBuilder s,double v){
        if(!Double.isFinite(v)){s.append("null");return;}
        if(v==0.0)v=0.0;
        s.append(String.format(Locale.US,"%.6f",v));
    }

    static void field(StringBuilder s,String key,String value){
        quote(s,key);s.append(':');
        if(value==null)s.append("null");else quote(s,value);
    }

    static void quote(StringBuilder s,String value){
        if(value==null){s.append("null");return;}
        s.append('"');
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '"':s.append("\\\"");break;
                case '\\':s.append("\\\\");break;
                case '\b':s.append("\\b");break;
                case '\f':s.append("\\f");break;
                case '\n':s.append("\\n");break;
                case '\r':s.append("\\r");break;
                case '\t':s.append("\\t");break;
                default:
                    if(c<0x20)s.append(String.format(Locale.US,"\\u%04x",(int)c));
                    else s.append(c);
            }
        }
        s.append('"');
    }
}
