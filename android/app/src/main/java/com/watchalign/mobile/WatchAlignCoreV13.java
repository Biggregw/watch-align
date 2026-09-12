package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha22: visual-first perspective-corrected GMT template plus legacy diagnostic QC. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha22";

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

    public static AnalysisResult analyse(Bitmap watch,Bitmap reference,String modelRef){
        List<Bitmap> refs=reference==null?Collections.emptyList():Collections.singletonList(reference);
        return analyse(watch,refs,modelRef);
    }

    public static AnalysisResult analyse(Bitmap watch,List<Bitmap> references,String modelRef){
        List<Bitmap> refs=references==null?Collections.emptyList():new ArrayList<>(references);
        Bitmap primary=refs.isEmpty()?null:refs.get(0);
        WatchAlignCoreV11.AnalysisResult base=WatchAlignCoreV11.analyse(watch,primary,modelRef);
        Bitmap guide=QcGuideRenderer.render(watch);
        QcExtendedAnalyzer.Result ext=QcExtendedAnalyzer.analyse(watch,primary,modelRef);
        boolean canonicalGmt=CanonicalGmtGeometryAnalyzer.supports(modelRef);
        String baselineReport;
        if(canonicalGmt){baselineReport=CanonicalGmtGeometryAnalyzer.analyse(watch,refs,modelRef).report;}
        else{baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;}
        Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);

        PerspectiveGmtOverlay.Result perspective=canonicalGmt?PerspectiveGmtOverlay.build(watch,modelRef):null;
        String perspectiveReport=perspective==null&&canonicalGmt
                ?"\n\nPERSPECTIVE GMT OVERLAY\nUnavailable: a stable dial ellipse could not be fitted. Use a clearer photo or manual overlay alignment.\n"
                :perspective==null?"":perspective.report;

        String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)
                + "\n\nQC guide: cyan=dial boundary; yellow=marker-ring consensus; grey spokes=roll-corrected ideal hour axes; white x=ideal marker position; coloured circle=measured marker position."
                + ext.report + baselineReport + perspectiveReport
                + (canonicalGmt
                ? "\nInterpretation: alpha22 is visual-first. The primary GMT inspection surface is a canonical template projected into the photographed dial perspective from an independently fitted dial ellipse. Hour markers are not used to fit the template. Rectified view normalizes the dial to a front-on plane. Automated marker/date/cyclops measurements remain secondary diagnostics rather than a GL/RL score."
                : "\nInterpretation: non-GMT models continue to use the existing reference-distribution diagnostics.");
        String report=QcSummaryFormatter.prependSummary(detail);
        return new AnalysisResult(combined,base.reference,base.aligned,
                perspective==null?null:perspective.nativeOverlay,
                perspective==null?null:perspective.rectified,
                report,base.registrationConfidence,perspective==null?0.0:perspective.confidence);
    }

    private WatchAlignCoreV13(){}
}
