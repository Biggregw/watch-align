package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Alpha12: alpha11 analysis plus explicit visual QC geometry guides. */
public final class WatchAlignCoreV12 {
    public static final String CORE_VERSION="1.3.0-alpha12";

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
        String report=base.report.replace("1.3.0-alpha11",CORE_VERSION);
        report += "\n\nQC guide: cyan=dial boundary; yellow=marker-ring consensus; grey spokes=roll-corrected ideal hour axes; white x=ideal marker position; coloured circle=measured marker position. Green is within visual tolerance, amber merits inspection, red is a stronger deviation. Guides are photo-geometry aids, not proof of authenticity.";
        return new AnalysisResult(guide,base.reference,base.aligned,report,base.registrationConfidence);
    }

    private WatchAlignCoreV12(){}
}
