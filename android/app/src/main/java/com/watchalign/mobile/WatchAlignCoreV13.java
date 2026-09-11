package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alpha20: robust genuine-reference distributions drive marker-position QC. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha20";

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
        ReferenceDistributionAnalyzer.Result dist=ReferenceDistributionAnalyzer.analyse(watch,refs,modelRef);
        Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);
        String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)
                + "\n\nQC guide: cyan=dial boundary; yellow=marker-ring consensus; grey spokes=roll-corrected ideal hour axes; white x=ideal marker position; coloured circle=measured marker position."
                + ext.report
                + dist.report
                + "\nInterpretation: alpha20 uses the genuine reference set as the baseline for marker angular/radial position. Each marker is compared with the median genuine measurement and robust MAD-derived spread, with minimum tolerance floors to avoid false precision. A single genuine image remains a fallback only. Marker-body rotation, cyclops rotation and apparent date magnification remain diagnostic until their component boundaries can be validated reliably.";
        String report=QcSummaryFormatter.prependSummary(detail);
        return new AnalysisResult(combined,base.reference,base.aligned,report,base.registrationConfidence);
    }

    private WatchAlignCoreV13(){}
}
