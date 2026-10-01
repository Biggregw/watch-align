package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha40: human GMT12 QC with real-image rehaut direction calibration and stricter pose gating. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha70";

    public static final class AnalysisResult {
        public final Bitmap annotated,reference,aligned,perspectiveOverlay,rectified;
        public final String report;
        public final double registrationConfidence,perspectiveConfidence;
        /** True when the 12 marker was located and measured (overlay has its elements). */
        public final boolean twelveMeasured;
        /** Enlarged 12-marker (and 6-baton, when measured) close-ups with status strips, or null. */
        public Bitmap twelveCloseUp;
        /** True when the experimental Submariner 124060 route produced this result (never for GMT). */
        public boolean submariner;
        /** 124060 route only: the dial or the 12 could not be located automatically, so offer hand alignment. */
        public boolean dialNeedsManual;
        /** 124060 route only: the measurements behind the report, or null. */
        Sub124060QcAnalyzer.Result sub124060;
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,Bitmap po,Bitmap rect,String rep,double c,double pc){this(a,r,al,po,rect,rep,c,pc,false);}
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,Bitmap po,Bitmap rect,String rep,double c,double pc,boolean twelve){annotated=a;reference=r;aligned=al;perspectiveOverlay=po;rectified=rect;report=rep;registrationConfidence=c;perspectiveConfidence=pc;twelveMeasured=twelve;}
        public Bitmap overlay(float alpha){
            if(reference==null||aligned==null)return annotated;
            Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));c.drawBitmap(aligned,0,0,p);return out;
        }
    }

    public static AnalysisResult analyse(Bitmap watch,Bitmap reference,String modelRef){List<Bitmap> refs=reference==null?Collections.emptyList():Collections.singletonList(reference);return analyse(watch,refs,modelRef);}

    public static AnalysisResult analyse(Bitmap watch,List<Bitmap> references,String modelRef){return analyse(watch,references,modelRef,null);}

    /** @param full the original photo, for a full-resolution dial crop when the dial is small (alpha61); may be null */
    public static AnalysisResult analyse(Bitmap watch,List<Bitmap> references,String modelRef,FullResSource full){
        // Experimental Submariner 124060 route. It never reaches the GMT analysis below nor the
        // legacy non-GMT analysers; every other model takes the existing path unchanged.
        if(Sub124060QcAnalyzer.supports(modelRef))return Sub124060QcAnalyzer.analyseForCore(watch,full,manualSeed());
        List<Bitmap> refs=references==null?Collections.emptyList():new ArrayList<>(references);Bitmap primary=refs.isEmpty()?null:refs.get(0);
        WatchAlignCoreV11.AnalysisResult base=WatchAlignCoreV11.analyse(watch,primary,modelRef);
        boolean canonicalGmt=CanonicalGmtGeometryAnalyzer.supports(modelRef);

        // The extended (non-GMT) analysis is only shown for non-GMT models; skip its cost here.
        QcExtendedAnalyzer.Result ext=canonicalGmt?null:QcExtendedAnalyzer.analyse(watch,primary,modelRef);
        Bitmap guide=QcGuideRenderer.render(watch);
        Bitmap combined=canonicalGmt?guide:QcOverlayComposer.compose(watch,guide,ext.annotated);

        PerspectiveGmtOverlay.DialSeed manualSeed=manualSeed();
        // Overlay = what the analysis measured (alpha51). The fixed predicted template is no
        // longer drawn: it could disagree with the measurements when the dial edge was hard
        // to fit, and a full measured template will be built up check by check instead.
        // Full-resolution dial crop (alpha61): not with a hand-aligned dial, whose taps are in
        // preview coordinates.
        GmtDialCrop.Crop crop=canonicalGmt&&manualSeed==null&&full!=null?GmtDialCrop.make(watch,full):null;
        GmtHumanQcAnalyzerV2.Result human=!canonicalGmt?null
                :crop!=null?GmtHumanQcAnalyzerV2.analyse(crop.bitmap,modelRef,null)
                :GmtHumanQcAnalyzerV2.analyse(watch,modelRef,manualSeed);
        Bitmap cropCloseUp=null;
        if(crop!=null&&human!=null&&human.drawing!=null&&human.drawing.hasAnything()){
            // Close-ups from the crop itself (full detail), then everything mapped onto the preview.
            cropCloseUp=MeasuredOverlayRenderer.closeUp(crop.bitmap,MeasuredOverlayRenderer.render(crop.bitmap,human.drawing),human.drawing,540);
            human.drawing.mapTo(crop);
        }
        Bitmap measured=human!=null?MeasuredOverlayRenderer.render(watch,human.drawing):null;
        boolean twelveMeasured=human!=null&&human.drawing!=null&&human.drawing.twelve!=null;

        String report;
        if(canonicalGmt){
            GmtHumanSummary.Input sum=human!=null&&human.summary!=null?human.summary:new GmtHumanSummary.Input();
            sum.overlayDrawn=twelveMeasured;
            report="Rolex GMT-Master II · Watch Align Core "+CORE_VERSION+"\n\n"
                    +GmtHumanSummary.build(sum)
                    +"\n\nDETAILS\nThe 59/60/01 minute track defines local 12 and the triangle is checked against it for gap, centring, rotation and 59/01 spacing. The dial centre and scale come from the physical black-dial edge. The overlay shows only what was measured: the dial edge (faint ring) and each hour marker's outline with a badge (tick = nothing flagged, ! = worth a look, !! = check closely, dash = not judged) and a word saying why when flagged or not judged. The close-ups show only what needs a look: anything flagged or not judged (up to four, most important first), or the 12 when all is clear, each against its neighbouring tick ends.\n"
                    +(crop!=null?"\n"+crop.describe()+"\n":"")
                    +(human==null?"":human.report);
        }else{
            String baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;
            String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)+ext.report+baselineReport+"\nInterpretation: non-GMT models continue to use the existing reference-distribution diagnostics.";
            report=QcSummaryFormatter.prependSummary(detail);
        }
        AnalysisResult res=new AnalysisResult(combined,base.reference,base.aligned,
                measured,null,
                report,base.registrationConfidence,twelveMeasured?1.0:0.0,twelveMeasured);
        if(cropCloseUp!=null)res.twelveCloseUp=cropCloseUp;
        else if(human!=null&&human.drawing!=null)res.twelveCloseUp=MeasuredOverlayRenderer.closeUp(watch,measured,human.drawing,540);
        return res;
    }
    /** The hand-aligned dial (12/6 dial-edge taps), or null. */
    static PerspectiveGmtOverlay.DialSeed manualSeed(){
        return InspectionImageStore.hasManualSeed?
                new PerspectiveGmtOverlay.DialSeed(InspectionImageStore.manualCx,InspectionImageStore.manualCy,InspectionImageStore.manualR,0.98,InspectionImageStore.manualRoll):null;
    }
    private WatchAlignCoreV13(){}
}
