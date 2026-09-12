package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha27: visual-first GMT inspection using a fixed model-specific master. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha27";

    public static final class AnalysisResult {
        public final Bitmap annotated,reference,aligned,perspectiveOverlay,rectified;
        public final String report;
        public final double registrationConfidence,perspectiveConfidence;
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,Bitmap po,Bitmap rect,String rep,double c,double pc){annotated=a;reference=r;aligned=al;perspectiveOverlay=po;rectified=rect;report=rep;registrationConfidence=c;perspectiveConfidence=pc;}
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
        PerspectiveGmtOverlay.Result perspective=canonicalGmt?PerspectiveGmtOverlay.build(watch,modelRef):null;
        String perspectiveReport=perspective==null&&canonicalGmt?"\n\nVISUAL QC MASTER\nUnavailable: a stable dial ellipse could not be fitted. Use a clearer photo.\n":perspective==null?"":perspective.report;
        String report;
        if(canonicalGmt){
            report=modelRef+" · Watch Align Core "+CORE_VERSION+"\n\nVISUAL INSPECTION MODE\n"
                    +"Alpha27 makes the projected model master the primary QC surface. The 126710BLNR master now uses correct 12-marker orientation, separate applied-body/lume outlines and full-screen opacity/blink comparison.\n"
                    +perspectiveReport
                    +"\nAutomated GL/RL scoring and ranked marker findings remain hidden. Diagnostics is retained only as a development view.\n";
        }else{
            String baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;
            String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)+ext.report+baselineReport+"\nInterpretation: non-GMT models continue to use the existing reference-distribution diagnostics.";
            report=QcSummaryFormatter.prependSummary(detail);
        }
        return new AnalysisResult(combined,base.reference,base.aligned,perspective==null?null:perspective.nativeOverlay,perspective==null?null:perspective.rectified,report,base.registrationConfidence,perspective==null?0.0:perspective.confidence);
    }
    private WatchAlignCoreV13(){}
}
