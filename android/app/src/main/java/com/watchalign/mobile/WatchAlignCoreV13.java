package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha21: canonical GMT geometry plus genuine-calibrated radial tolerances. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha21";

    public static final class AnalysisResult {
        public final Bitmap annotated,reference,aligned;
        public final String report;
        public final double registrationConfidence;
        AnalysisResult(Bitmap a,Bitmap r,Bitmap al,String rep,double c){annotated=a;reference=r;aligned=al;report=rep;registrationConfidence=c;}
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
        if(canonicalGmt){
            baselineReport=CanonicalGmtGeometryAnalyzer.analyse(watch,refs,modelRef).report;
        } else {
            baselineReport=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef).report;
        }
        Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);
        String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)
                + "\n\nQC guide: cyan=dial boundary; yellow=marker-ring consensus; grey spokes=roll-corrected ideal hour axes; white x=ideal marker position; coloured circle=measured marker position."
                + ext.report
                + baselineReport
                + (canonicalGmt
                ? "\nInterpretation: alpha21 treats the 126710 GMT hour layout as canonical geometry. Hour centres use the exact 30-degree grid after roll removal. Marker radial placement is measured independently as a percentage of detected dial radius so the marker ring cannot hide a high/low marker. Genuine references calibrate detector bias and normal radial spread. Fewer than three usable radial samples never produce a radial pass/fail. Marker-body rotation, cyclops rotation and apparent date magnification remain diagnostic until their component boundaries can be validated reliably."
                : "\nInterpretation: non-GMT models continue to use the alpha20 genuine-reference distribution for marker position. Marker-body rotation, cyclops rotation and apparent date magnification remain diagnostic until their component boundaries can be validated reliably.");
        String report=QcSummaryFormatter.prependSummary(detail);
        return new AnalysisResult(combined,base.reference,base.aligned,report,base.registrationConfidence);
    }

    private WatchAlignCoreV13(){}
}
