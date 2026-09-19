package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha30: visual-first GMT inspection with precision assisted alignment and visible QC results. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha30";

    public static final class AnalysisResult {
        public final Bitmap annotated,reference,aligned,perspectiveOverlay,rectified;
        public final String report;
        public final double registrationConfidence,perspectiveConfidence;
        public final boolean perspectiveAccepted;
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,Bitmap po,Bitmap rect,String rep,double c,double pc,boolean accepted){
            annotated=a;reference=r;aligned=al;perspectiveOverlay=po;rectified=rect;report=rep;
            registrationConfidence=c;perspectiveConfidence=pc;perspectiveAccepted=accepted;
        }
        public Bitmap overlay(float alpha){
            if(reference==null||aligned==null)return annotated;
            Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));c.drawBitmap(aligned,0,0,p);return out;
        }
    }

    public static AnalysisResult analyse(Bitmap watch,Bitmap reference,String modelRef){List<Bitmap> refs=reference==null?Collections.emptyList():Collections.singletonList(reference);return analyse(watch,refs,modelRef);}

    public static AnalysisResult analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        List<Bitmap> refs=references==null?Collections.emptyList():new ArrayList<>(references);Bitmap primary=refs.isEmpty()?null:refs.get(0);
        WatchAlignCoreV11.AnalysisResult base=WatchAlignCoreV11.analyse(watch,primary,modelRef);
        Bitmap guide=QcGuideRenderer.render(watch);QcExtendedAnalyzer.Result ext=QcExtendedAnalyzer.analyse(watch,primary,modelRef);
        boolean canonicalGmt=CanonicalGmtGeometryAnalyzer.supports(modelRef);Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);
        PerspectiveGmtOverlay.DialSeed manualSeed=InspectionImageStore.hasManualSeed?
                new PerspectiveGmtOverlay.DialSeed(InspectionImageStore.manualCx,InspectionImageStore.manualCy,InspectionImageStore.manualR,0.98,InspectionImageStore.manualRoll):null;
        PerspectiveGmtOverlay.Result perspective=canonicalGmt?PerspectiveGmtOverlay.build(watch,modelRef,manualSeed,android.graphics.Color.rgb(255,45,45)):null;
        String perspectiveReport=perspective==null&&canonicalGmt?"\n\nVISUAL QC MASTER\nUnavailable: a stable dial ellipse could not be fitted. Use a clearer photo or precision dial-edge alignment.\n":perspective==null?"":perspective.report;
        String report;
        if(canonicalGmt){
            report=modelRef+" · Watch Align Core "+CORE_VERSION+"\n\nVISUAL INSPECTION MODE\n"
                    +"Projected model master provides the primary QC surface. Automated QC findings are also available from View QC checks.\n"
                    +perspectiveReport
                    +ext.report;
        }else{
            String baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;
            String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)+ext.report+baselineReport+"\nInterpretation: non-GMT models continue to use the existing reference-distribution diagnostics.";
            report=QcSummaryFormatter.prependSummary(detail);
        }
        return new AnalysisResult(combined,base.reference,base.aligned,
                perspective==null?null:perspective.nativeOverlay,
                perspective==null?null:perspective.rectified,
                report,base.registrationConfidence,
                perspective==null?0.0:perspective.confidence,
                perspective!=null&&perspective.automaticAccepted);
    }
    private WatchAlignCoreV13(){}
}
