package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/** Alpha9 facade: fixes alpha8's false post-warp centre validation failures. */
public final class WatchAlignCoreV9 {
    public static final String CORE_VERSION = "1.3.0-alpha9";

    public static final class AnalysisResult {
        public final Bitmap annotated;
        public final Bitmap reference;
        public final Bitmap aligned;
        public final String report;
        public final double registrationConfidence;

        AnalysisResult(Bitmap annotated, Bitmap reference, Bitmap aligned, String report, double registrationConfidence) {
            this.annotated=annotated; this.reference=reference; this.aligned=aligned;
            this.report=report; this.registrationConfidence=registrationConfidence;
        }
        public Bitmap overlay(float alpha) {
            if(reference==null || aligned==null) return annotated;
            Bitmap out=Bitmap.createBitmap(reference.getWidth(),reference.getHeight(),Bitmap.Config.ARGB_8888);
            Canvas c=new Canvas(out); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference,0,0,p); p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255)))); c.drawBitmap(aligned,0,0,p);
            return out;
        }
    }

    public static double referenceScore(Bitmap watch, Bitmap reference) {
        return WatchAlignCoreV7.referenceScore(watch,reference);
    }

    public static AnalysisResult analyse(Bitmap watch, Bitmap reference, String modelRef) {
        WatchAlignCoreV7.AnalysisResult base=WatchAlignCoreV7.analyse(watch,reference,modelRef);
        Bitmap aligned=base.aligned;
        double conf=base.registrationConfidence;
        String report=base.report.replace("1.3.0-alpha7",CORE_VERSION);

        if(reference!=null && aligned==null && Double.isFinite(conf) && conf>=0.58) {
            GeometryOverlayRepair.Result repaired=GeometryOverlayRepair.build(watch,reference);
            conf=Math.max(conf,repaired.confidence);
            if(repaired.aligned!=null) {
                aligned=repaired.aligned;
                int cut=report.lastIndexOf("\nGeometry alignment failed final validation");
                if(cut<0) cut=report.lastIndexOf("\nAligned watch dial could not be re-detected");
                if(cut>=0) report=report.substring(0,cut);
                report += String.format(java.util.Locale.US,
                        "\nReference comparison ready. Deterministic geometry transform passed analytic validation (centre %.2f%%, radius %.2f%%, marker RMS %.2f°).",
                        repaired.centreError*100.0,repaired.radiusError*100.0,repaired.markerRms);
            } else {
                report += "\nDeterministic transform check also refused overlay: " + repaired.reason;
            }
        }
        return new AnalysisResult(base.annotated,base.reference,aligned,report,conf);
    }

    private WatchAlignCoreV9() {}
}
