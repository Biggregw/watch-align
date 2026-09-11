package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Alpha18: conservative reliability gating backed by genuine-image regression cases. */
public final class WatchAlignCoreV13 {
    public static final String CORE_VERSION="1.3.0-alpha18";

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
        WatchAlignCoreV11.AnalysisResult base=WatchAlignCoreV11.analyse(watch,reference,modelRef);
        Bitmap guide=QcGuideRenderer.render(watch);
        QcExtendedAnalyzer.Result ext=QcExtendedAnalyzer.analyse(watch,reference,modelRef);
        Bitmap combined=QcOverlayComposer.compose(watch,guide,ext.annotated);
        String detail=base.report.replace("1.3.0-alpha11",CORE_VERSION)
                + "\n\nQC guide: cyan=dial boundary; yellow=marker-ring consensus; grey spokes=roll-corrected ideal hour axes; white x=ideal marker position; coloured circle=measured marker position."
                + ext.report
                + "\nInterpretation: green/amber/red are geometry cues only. Alpha18 suppresses unstable local-axis outliers rather than inventing marker or cyclops rotation defects. Date vertical ink position is diagnostic only because it depends on the displayed numeral; horizontal centring drives the automatic date-centre verdict. GMT bezel/pip alignment is visual-only until a shape-validated detector replaces the unreliable brightness scan. Date magnification remains a relative image measurement against the selected genuine reference, not a laboratory optical magnification value.";
        String report=QcSummaryFormatter.prependSummary(detail);
        return new AnalysisResult(combined,base.reference,base.aligned,report,base.registrationConfidence);
    }

    private WatchAlignCoreV13(){}
}
