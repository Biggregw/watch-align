package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha39: perspective-aware community QC ruler with fine anchor nudging. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha39";

    public static final class AnalysisResult {
        public final Bitmap annotated,reference,aligned,perspectiveOverlay,rectified;
        public final String report;
        public final double registrationConfidence,perspectiveConfidence;
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,Bitmap po,Bitmap rect,String rep,double c,double pc){annotated=a;reference=r;aligned=al;perspectiveOverlay=po;rectified=rect;report=rep;registrationConfidence=c;perspectiveConfidence=pc;}
        public Bitmap overlay(float alpha){if(reference==null||aligned==null)return annotated;Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);c.drawBitmap(reference,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));c.drawBitmap(aligned,0,0,p);return out;}
    }

    public static AnalysisResult analyse(Bitmap watch,Bitmap reference,String modelRef){List<Bitmap> refs=reference==null?Collections.emptyList():Collections.singletonList(reference);return analyse(watch,refs,modelRef);}
    public static AnalysisResult analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        List<Bitmap> refs=references==null?Collections.emptyList():new ArrayList<>(references);Bitmap primary=refs.isEmpty()?null:refs.get(0);WatchAlignCoreV11.AnalysisResult base=WatchAlignCoreV11.analyse(watch,primary,modelRef);Bitmap guide=QcGuideRenderer.render(watch);QcExtendedAnalyzer.Result ext=QcExtendedAnalyzer.analyse(watch,primary,modelRef);boolean canonicalGmt=CanonicalGmtGeometryAnalyzer.supports(modelRef);Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);PerspectiveGmtOverlay.Result perspective=canonicalGmt?PerspectiveGmtOverlay.build(watch,modelRef):null;String perspectiveReport=perspective==null&&canonicalGmt?"\n\nAUTOMATIC TEMPLATE\nUnavailable for this photo. Manual ruler alignment does not depend on this.\n":perspective==null?"":perspective.report;String report;
        if(canonicalGmt){report=modelRef+" · Watch Align Core "+CORE_VERSION+"\n\nCOMMUNITY QC RULER\nAlpha39 uses radial QC ruler lines rather than traced marker outlines. Drag each anchor close, then use 0.5 px fine nudging or 2 px coarse nudging while watching the loupe. Optional 3/9 handles apply the projective warp for angled photos, and finished anchors can be locked.\n"+perspectiveReport+"\nThe ruler is a visual alignment aid, not Rolex factory CAD. Automatic analysis remains secondary.\n";}
        else{String baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)+ext.report+baselineReport+"\nInterpretation: non-GMT models continue to use the existing reference-distribution diagnostics.";report=QcSummaryFormatter.prependSummary(detail);}
        return new AnalysisResult(combined,base.reference,base.aligned,perspective==null?null:perspective.nativeOverlay,perspective==null?null:perspective.rectified,report,base.registrationConfidence,perspective==null?0.0:perspective.confidence);
    }
    private WatchAlignCoreV13(){}
}
