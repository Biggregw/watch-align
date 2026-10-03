package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Long-form measured-only calibration contract for the current Submariner 124060 route.
 *
 * This driver never emits a QC verdict. It preserves a raw metric value when geometry can be
 * measured, records whether the current reliability policy accepts that value for calibration,
 * records why a value was withheld, and emits the exact eligible value separately. The Python
 * calibration layer consumes only eligible_value, so adding diagnostics cannot widen calibration.
 *
 * Usage:
 *   tools/desktop-harness/run.sh CalibMeasureContract <model> <list.tsv> <out.csv>
 */
public final class CalibMeasureContract {
    static final String SCHEMA="1";
    static final String FAMILY="submariner_12";
    static final String ADAPTER_ID="submariner12_measured_v1";
    static final String ADAPTER_VERSION="1";
    static final String RELIABILITY_POLICY="sub124060_production_reliability_v1";
    static final String EXPECTED_LAYOUT="submariner_124060_no_date_v1";
    static final String[] KEYS={
            "twelve.rotation_deg","twelve.gap_r","twelve.centring_w",
            "round.ring_rho","round.spacing_rms_deg",
            "baton.3_9_line_offset_r","axis.12_6_line_offset_r"
    };

    private CalibMeasureContract(){}

    public static void main(String[] a)throws Exception{
        if(a.length!=3)throw new IllegalArgumentException("usage: CalibMeasureContract <model> <list.tsv> <out.csv>");
        String model=a[0].trim().toUpperCase(Locale.US);
        if(model.isEmpty())throw new IllegalArgumentException("model is required");
        // This first measured-only adapter is deliberately exact-reference bounded. Date models
        // will get their own layout-aware adapter rather than silently inheriting 124060 semantics.
        if(!"124060".equals(model)||!Sub124060QcAnalyzer.supports(model))
            throw new IllegalArgumentException("model "+model+" is not supported by submariner12_measured_v1");

        nu.pattern.OpenCV.loadLocally();
        try(PrintWriter w=new PrintWriter(new FileWriter(a[2]))){
            w.println("schema_version,physical_watch_id,model,family,path,adapter_id,adapter_version,reliability_policy,dial_source,dial_reproducible,pose_tilt_deg,expected_layout,observed_layout_state,metric,raw_value,reliability_state,reliability_reason,eligible_value");
            for(String line:Files.readAllLines(Path.of(a[1]))){
                String[] f=line.split("\\t",2);
                if(f.length<2||f[1].trim().isEmpty())continue;
                String id=f[0].trim(),p=f[1].trim();
                emitPhoto(w,id,model,p);
            }
        }
    }

    static void emitPhoto(PrintWriter w,String id,String model,String p){
        Sub124060QcAnalyzer.Result s=null;
        String photoFailure=null;
        try{
            Bitmap b=Load.photo(p);
            if(b==null)photoFailure="image unreadable";
            else{
                FullResSource full=Load.fullSource(p);
                WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(
                        b,Collections.<Bitmap>emptyList(),model,full);
                s=r.sub124060;
                if(s==null)photoFailure="configured model returned no Submariner measurement result";
            }
        }catch(Throwable t){
            photoFailure="analysis failed: "+t.getClass().getSimpleName();
        }

        if(s==null){
            for(String key:KEYS)emit(w,id,model,p,"unavailable","",Double.NaN,key,
                    Double.NaN,"unavailable",photoFailure!=null?photoFailure:"measurement unavailable",Double.NaN);
            return;
        }

        Sub124060Calibration.Assessment c=Sub124060Calibration.assess(s,false);
        double tilt=s.markerPose!=null&&s.markerPose.valid?s.markerPose.tiltDeg:Double.NaN;
        String dial=String.valueOf(s.dialSource);
        String repro=String.valueOf(s.dialReproducible);
        String observed=observedLayout(s);

        double[] raw={
                s.rotationDeg,
                s.gapR,
                s.centringW,
                rawRoundRing(s),
                rawRoundSpacing(s),
                rawBaton39(s),
                rawAxis126(s)
        };
        boolean[] accepted={
                c.rotationMeasured,c.gapMeasured,c.centringMeasured,
                c.roundRingMeasured,c.roundSpacingMeasured,c.baton39Measured,c.axis126Measured
        };
        double[] eligible={
                accepted[0]?s.rotationDeg:Double.NaN,
                accepted[1]?s.gapR:Double.NaN,
                accepted[2]?s.centringW:Double.NaN,
                accepted[3]?c.roundRingRho:Double.NaN,
                accepted[4]?c.roundSpacingRmsDeg:Double.NaN,
                accepted[5]?c.baton39LineOffsetR:Double.NaN,
                accepted[6]?c.axis126LineOffsetR:Double.NaN
        };
        String[] reasons={
                twelveReason(s.rotationDeg,c.rotationMeasured,s.rotationWithheld,s.rotationResizeStable),
                twelveReason(s.gapR,c.gapMeasured,s.gapWithheld,s.gapResizeStable),
                twelveReason(s.centringW,c.centringMeasured,s.centringWithheld,s.centringResizeStable),
                roundReason(s,c.roundRingMeasured,raw[3]),
                roundReason(s,c.roundSpacingMeasured,raw[4]),
                baton39Reason(s,c.baton39Measured,raw[5]),
                axis126Reason(s,c.axis126Measured,raw[6])
        };

        for(int i=0;i<KEYS.length;i++){
            String state=accepted[i]?"accepted":Double.isFinite(raw[i])?"withheld":"unavailable";
            String reason=accepted[i]?"":reasons[i];
            if(reason==null||reason.trim().isEmpty())reason=accepted[i]?"":"current reliability policy did not accept metric";
            emit(w,id,model,p,dial,repro,tilt,KEYS[i],raw[i],state,reason,eligible[i],observed);
        }
    }

    static String observedLayout(Sub124060QcAnalyzer.Result s){
        if(s==null||s.dialSource==Sub124060QcAnalyzer.DialSource.UNAVAILABLE)return "unresolved_no_dial";
        Sub124060QcAnalyzer.Baton b3=Sub124060Calibration.baton(s,GmtSixLandmarkAnalyzer.Position.THREE);
        if(s.triangle!=null&&b3!=null&&b3.status==Sub124060QcAnalyzer.Status.FOUND)return "compatible_no_date";
        return "unresolved";
    }

    static String twelveReason(double raw,boolean accepted,String withheld,Boolean resizeStable){
        if(accepted)return "";
        if(withheld!=null&&!withheld.trim().isEmpty())return withheld;
        if(!Double.isFinite(raw))return "metric geometry not measured";
        if(resizeStable==null)return "resize repeatability not established";
        if(!resizeStable)return "resize repeatability failed";
        return "current reliability policy withheld metric";
    }

    static String commonRelationalReason(Sub124060QcAnalyzer.Result s){
        if(s.frame==null)return "dial frame unavailable";
        if(s.edge==null)return "ellipse-corrected dial edge unavailable";
        if(!Boolean.TRUE.equals(s.dialReproducible))return "dial edge not reproducible";
        return "";
    }

    static String roundReason(Sub124060QcAnalyzer.Result s,boolean accepted,double raw){
        if(accepted)return "";
        String common=commonRelationalReason(s);if(!common.isEmpty())return common;
        int found=0,repeatable=0;
        for(Sub124060QcAnalyzer.Round q:s.rounds){
            GmtRoundMarkerAnalyzer.Marker m=q.marker;
            if(q.status==Sub124060QcAnalyzer.Status.FOUND&&m!=null&&m.found){
                found++;
                if(Sub124060QcAnalyzer.roundOffsetRepeatable(m))repeatable++;
            }
        }
        if(found<4)return "fewer than four round markers measured";
        if(repeatable<4)return "fewer than four round markers passed resize repeatability";
        return Double.isFinite(raw)?"derived round metric withheld":"round geometry insufficient";
    }

    static String baton39Reason(Sub124060QcAnalyzer.Result s,boolean accepted,double raw){
        if(accepted)return "";
        String common=commonRelationalReason(s);if(!common.isEmpty())return common;
        String a=batonReason(s,GmtSixLandmarkAnalyzer.Position.THREE,"3");
        String b=batonReason(s,GmtSixLandmarkAnalyzer.Position.NINE,"9");
        if(!a.isEmpty())return a;
        if(!b.isEmpty())return b;
        return Double.isFinite(raw)?"3/9 relation withheld by current reliability policy":"3/9 relation not measurable";
    }

    static String axis126Reason(Sub124060QcAnalyzer.Result s,boolean accepted,double raw){
        if(accepted)return "";
        String common=commonRelationalReason(s);if(!common.isEmpty())return common;
        if(s.triangle==null)return "12 triangle not found";
        if(s.twelveWithheld!=null)return s.twelveWithheld;
        String b=batonReason(s,GmtSixLandmarkAnalyzer.Position.SIX,"6");
        if(!b.isEmpty())return b;
        return Double.isFinite(raw)?"12/6 relation withheld by current reliability policy":"12/6 relation not measurable";
    }

    static String batonReason(Sub124060QcAnalyzer.Result s,GmtSixLandmarkAnalyzer.Position pos,String label){
        Sub124060QcAnalyzer.Baton b=Sub124060Calibration.baton(s,pos);
        if(b==null)return label+" baton result missing";
        if(b.status!=Sub124060QcAnalyzer.Status.FOUND){
            String note=b.note==null?"":b.note.trim();
            return label+" baton "+b.status+(note.isEmpty()?"":"; "+note);
        }
        if(b.result==null||!b.result.valid)return label+" baton geometry invalid";
        if(!Sub124060QcAnalyzer.batonRepeatable(b.result)){
            String note=b.note==null?"":b.note.trim();
            return label+" baton resize diagnostic not repeatable"+(note.isEmpty()?"":"; "+note);
        }
        return "";
    }

    static double rawRoundRing(Sub124060QcAnalyzer.Result s){
        if(s==null||s.frame==null)return Double.NaN;
        List<Double> rho=new ArrayList<>();
        for(Sub124060QcAnalyzer.Round q:s.rounds){
            GmtRoundMarkerAnalyzer.Marker m=q.marker;
            if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found)continue;
            double[] p=Sub124060Calibration.rectNorm(s.frame,m.x,m.y);
            if(p!=null)rho.add(Math.hypot(p[0],p[1]));
        }
        return rho.size()>=4?Sub124060Calibration.median(rho):Double.NaN;
    }

    static double rawRoundSpacing(Sub124060QcAnalyzer.Result s){
        if(s==null||s.frame==null)return Double.NaN;
        double ref12=Sub124060Calibration.rectClock(s.frame,s.tick60);
        if(!Double.isFinite(ref12))return Double.NaN;
        List<Double> errors=new ArrayList<>();
        for(Sub124060QcAnalyzer.Round q:s.rounds){
            GmtRoundMarkerAnalyzer.Marker m=q.marker;
            if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found)continue;
            errors.add(Sub124060Calibration.wrap180(
                    Sub124060Calibration.rectClock(s.frame,m.x,m.y)-ref12-m.hour*30.0));
        }
        return errors.size()>=4?Sub124060Calibration.spacingRms(errors):Double.NaN;
    }

    static double[] rawBatonPoint(Sub124060QcAnalyzer.Result s,GmtSixLandmarkAnalyzer.Position pos){
        if(s==null||s.frame==null)return null;
        Sub124060QcAnalyzer.Baton b=Sub124060Calibration.baton(s,pos);
        if(b==null||b.status!=Sub124060QcAnalyzer.Status.FOUND||b.result==null||!b.result.valid)return null;
        GmtSixLandmarkAnalyzer.Geometry g=b.result.geometry;
        if(g==null||!g.outerEdge||g.outerLeft==null||g.outerRight==null||g.innerLeft==null||g.innerRight==null)return null;
        double x=(g.outerLeft[0]+g.outerRight[0]+g.innerLeft[0]+g.innerRight[0])/4.0;
        double y=(g.outerLeft[1]+g.outerRight[1]+g.innerLeft[1]+g.innerRight[1])/4.0;
        return Sub124060Calibration.rectNorm(s.frame,x,y);
    }

    static double rawBaton39(Sub124060QcAnalyzer.Result s){
        return Sub124060Calibration.lineOffset(
                rawBatonPoint(s,GmtSixLandmarkAnalyzer.Position.THREE),
                rawBatonPoint(s,GmtSixLandmarkAnalyzer.Position.NINE));
    }

    static double rawAxis126(Sub124060QcAnalyzer.Result s){
        if(s==null||s.frame==null||s.triangle==null)return Double.NaN;
        double[] p12=Sub124060Calibration.rectNorm(s.frame,s.triangle.cx,s.triangle.cy);
        return Sub124060Calibration.lineOffset(p12,rawBatonPoint(s,GmtSixLandmarkAnalyzer.Position.SIX));
    }

    static void emit(PrintWriter w,String id,String model,String p,String dial,String repro,double tilt,
                     String metric,double raw,String state,String reason,double eligible){
        emit(w,id,model,p,dial,repro,tilt,metric,raw,state,reason,eligible,"unresolved");
    }

    static void emit(PrintWriter w,String id,String model,String p,String dial,String repro,double tilt,
                     String metric,double raw,String state,String reason,double eligible,String observed){
        w.println(String.join(",",
                clean(SCHEMA),clean(id),clean(model),clean(FAMILY),clean(p),clean(ADAPTER_ID),
                clean(ADAPTER_VERSION),clean(RELIABILITY_POLICY),clean(dial),clean(repro),fmt(tilt),
                clean(EXPECTED_LAYOUT),clean(observed),clean(metric),fmt(raw),clean(state),clean(reason),fmt(eligible)));
        w.flush();
    }

    static String clean(String s){
        if(s==null)return "";
        return s.replace(',',';').replace('\n',' ').replace('\r',' ').trim();
    }
    static String fmt(double x){return Double.isFinite(x)?String.format(Locale.US,"%.6f",x):"";}
}
