package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single supported analysis entry point.
 *
 * The earlier version-stacked WatchAlignCoreV* classes were experimental snapshots.
 * Alpha54 consolidates the live pipeline here and delegates narrow responsibilities
 * to named helpers rather than chaining versioned cores.
 */
public final class WatchAlignCore {
    public static final String CORE_VERSION="1.3.0";

    public static final class AnalysisResult {
        public final Bitmap annotated,reference,aligned,perspectiveOverlay,rectified;
        public final String report;
        public final double registrationConfidence,perspectiveConfidence;
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,Bitmap po,Bitmap rect,String rep,double c,double pc){
            annotated=a;reference=r;aligned=al;perspectiveOverlay=po;rectified=rect;report=rep;
            registrationConfidence=c;perspectiveConfidence=pc;
        }
        public Bitmap overlay(float alpha){
            if(reference==null||aligned==null)return annotated;
            Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference,0,0,p);p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));
            c.drawBitmap(aligned,0,0,p);return out;
        }
    }

    public static AnalysisResult analyse(Bitmap watch,Bitmap reference,String modelRef){
        List<Bitmap> refs=reference==null?Collections.emptyList():Collections.singletonList(reference);
        return analyse(watch,refs,modelRef);
    }

    public static AnalysisResult analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        List<Bitmap> refs=references==null?Collections.emptyList():new ArrayList<>(references);
        Bitmap primary=refs.isEmpty()?null:refs.get(0);
        ReferenceComparisonEngine.Result base=ReferenceComparisonEngine.analyse(watch,primary,modelRef);
        Bitmap guide=QcGuideRenderer.render(watch);
        QcExtendedAnalyzer.Result ext=QcExtendedAnalyzer.analyse(watch,primary,modelRef);
        boolean canonicalGmt=CanonicalGmtGeometryAnalyzer.supports(modelRef);
        Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);
        PerspectiveGmtOverlay.Result perspective=canonicalGmt?PerspectiveGmtOverlay.build(watch,modelRef):null;
        String perspectiveReport=perspective==null&&canonicalGmt
                ?"\n\nAUTOMATIC TEMPLATE\nUnavailable for this photo. Manual ruler alignment does not depend on this.\n"
                :perspective==null?"":perspective.report;
        String report;
        if(canonicalGmt){
            report=modelRef+" · Watch Align Core "+CORE_VERSION+"\n\nTRUE PERSPECTIVE QC RULER\n"
                    +"Four independent dial-edge correspondences at 12, 3, 6 and 9 solve the planar projective transform. "
                    +"The pinion is an independent CENTER CHECK. The 12-triangle relation check is measured after perspective rectification.\n"
                    +perspectiveReport
                    +"\nThe ruler and QC ranges are visual aids derived from genuine-image controls, not Rolex factory CAD or factory tolerance data. Automatic analysis remains secondary.\n";
        } else {
            String baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;
            String detail=base.report+ext.report+baselineReport+"\nInterpretation: non-GMT models use reference-distribution diagnostics.";
            report=QcSummaryFormatter.prependSummary(detail);
        }
        return new AnalysisResult(combined,base.reference,base.aligned,
                perspective==null?null:perspective.nativeOverlay,
                perspective==null?null:perspective.rectified,
                report,base.registrationConfidence,perspective==null?0.0:perspective.confidence);
    }

    public static double referenceScore(Bitmap watch,Bitmap reference){
        return ReferenceComparisonEngine.referenceScore(watch,reference);
    }

    private WatchAlignCore(){}
}
