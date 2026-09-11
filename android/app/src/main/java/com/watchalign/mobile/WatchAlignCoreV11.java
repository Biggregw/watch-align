package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Alpha11 core. QC runs independently of reference comparison. The overlay path
 * is deterministic geometry only, with exact-model online reference hardening
 * and a transparent watch-head comparison layer supplied by supporting classes.
 */
public final class WatchAlignCoreV11 {
    public static final String CORE_VERSION = "1.3.0-alpha11";

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
            Canvas c=new Canvas(out);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(reference,0,0,p);
            p.setAlpha(Math.max(0,Math.min(255,Math.round(alpha*255))));
            c.drawBitmap(aligned,0,0,p);
            return out;
        }
    }

    public static double referenceScore(Bitmap watch, Bitmap reference) {
        return WatchAlignCoreV7.referenceScore(watch,reference);
    }

    public static AnalysisResult analyse(Bitmap watch, Bitmap reference, String modelRef) {
        WatchAlignCoreV7.AnalysisResult qc=WatchAlignCoreV7.analyse(watch,null,modelRef);
        String report=qc.report.replace("1.3.0-alpha7",CORE_VERSION);
        int noRef=report.lastIndexOf("\nNo reference selected.");
        if(noRef>=0) report=report.substring(0,noRef);

        if(reference==null) {
            report += "\nNo reference selected.";
            return new AnalysisResult(qc.annotated,null,null,report,Double.NaN);
        }

        Bitmap refCopy=reference.copy(Bitmap.Config.ARGB_8888,false);
        GeometryOverlayRepair.Result repaired=GeometryOverlayRepair.build(watch,reference);
        if(repaired.aligned!=null) {
            report += String.format(java.util.Locale.US,
                    "\nReference comparison ready. Deterministic geometry transform passed analytic validation (centre %.2f%%, radius %.2f%%, marker RMS %.2f°).",
                    repaired.centreError*100.0,repaired.radiusError*100.0,repaired.markerRms);
        } else {
            report += "\nOverlay withheld: " + (repaired.reason!=null?repaired.reason:"deterministic geometry validation failed.");
        }
        return new AnalysisResult(qc.annotated,refCopy,repaired.aligned,report,repaired.confidence);
    }

    private WatchAlignCoreV11() {}
}
