package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;


/**
 * Deterministic overlay builder that reuses the proven V7 detector but validates
 * the transform analytically instead of re-detecting the already-warped raster.
 * The aligned layer is RGBA with a transparent background and is restricted to
 * the watch-head region so unrelated hand/background pixels cannot obscure the
 * genuine reference during overlay inspection.
 */
final class GeometryOverlayRepair {
    static final class Result {
        final Bitmap aligned;
        final double confidence;
        final double centreError;
        final double radiusError;
        final double markerRms;
        final String reason;
        Result(Bitmap aligned, double confidence, double centreError, double radiusError, double markerRms, String reason) {
            this.aligned = aligned; this.confidence = confidence; this.centreError = centreError;
            this.radiusError = radiusError; this.markerRms = markerRms; this.reason = reason;
        }
    }

    static Result build(Bitmap watch, Bitmap reference) {
        Mat src = new Mat(), ref = new Mat();
        try {
            Utils.bitmapToMat(watch, src); Imgproc.cvtColor(src, src, Imgproc.COLOR_RGBA2BGR);
            Utils.bitmapToMat(reference, ref); Imgproc.cvtColor(ref, ref, Imgproc.COLOR_RGBA2BGR);

            DialAnalysisEngine.Circle sd=DialAnalysisEngine.detectDial(src),rd=DialAnalysisEngine.detectDial(ref);
            if (sd == null || rd == null) return bad("Dial geometry could not be verified for both images.");
            double sx=sd.x,sy=sd.y,sr=sd.r,sq=sd.quality;
            double rx=rd.x,ry=rd.y,rr=rd.r,rq=rd.quality;
            if (sq < 0.56 || rq < 0.56 || sr <= 0 || rr <= 0) return bad("Dial geometry quality is below the overlay threshold.");

            DialAnalysisEngine.MarkerSet sm=DialAnalysisEngine.measureMarkerSet(src,sd),rm=DialAnalysisEngine.measureMarkerSet(ref,rd);
            double smr=sm.medianRadius,rmr=rm.medianRadius;
            double sga=sm.globalRotation,rga=rm.globalRotation;
            int common=DialAnalysisEngine.commonMarkerCount(sm,rm);
            double rms=DialAnalysisEngine.commonMarkerRms(sm,rm);
            double sp=DialAnalysisEngine.perspectiveEquivalent(src,sd);
            double rp=DialAnalysisEngine.perspectiveEquivalent(ref,rd);
            double mismatch = (Double.isFinite(sp) && Double.isFinite(rp)) ? Math.abs(sp-rp) : Double.NaN;

            GeometryRegistration.Solution sol = GeometryRegistration.solve(sr, rr, smr, rmr, sga, rga, sq, rq, common, rms, mismatch);
            if (!sol.usable) return new Result(null, sol.confidence, Double.NaN, Double.NaN, rms,
                    sol.reason != null ? sol.reason : "Geometry registration confidence is too low.");

            SimilarityTransform t = SimilarityTransform.between(sx, sy, rx, ry, sol.scale, sol.rotationDeg);
            double centreError = Math.hypot(t.mapX(sx,sy)-rx, t.mapY(sx,sy)-ry) / Math.max(1.0,rr);
            double radiusError = Math.abs(t.mappedRadius(sr)-rr) / Math.max(1.0,rr);
            if (centreError > 0.005) return new Result(null, sol.confidence, centreError, radiusError, rms,"Similarity transform does not map dial centres correctly.");
            if (radiusError > 0.13) return new Result(null, sol.confidence, centreError, radiusError, rms,"Dial and marker-ring scale solution leaves too much radius error.");
            if (common < 7 || rms > 3.0) return new Result(null, sol.confidence, centreError, radiusError, rms,"Marker geometry does not agree well enough for overlay.");

            double[][] m = t.matrix2x3();
            Mat affine = new Mat(2,3,CvType.CV_64F);
            affine.put(0,0,m[0][0],m[0][1],m[0][2],m[1][0],m[1][1],m[1][2]);

            // Build an RGBA layer and make everything outside the watch head transparent.
            Mat rgbaSrc = new Mat();
            Imgproc.cvtColor(src, rgbaSrc, Imgproc.COLOR_BGR2RGBA);
            Mat alpha = Mat.zeros(src.rows(),src.cols(),CvType.CV_8UC1);
            int focusRadius = (int)Math.round(sr * 1.42);
            Imgproc.circle(alpha,new Point(sx,sy),focusRadius,new Scalar(255),-1,Imgproc.LINE_AA,0);
            Core.insertChannel(alpha,rgbaSrc,3);

            Mat aligned = new Mat();
            Imgproc.warpAffine(rgbaSrc,aligned,affine,new Size(ref.cols(),ref.rows()),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0,0,0,0));
            Bitmap out = Bitmap.createBitmap(aligned.cols(), aligned.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(aligned,out);

            alpha.release(); rgbaSrc.release(); aligned.release(); affine.release();
            return new Result(out, sol.confidence, centreError, radiusError, rms, null);
        } catch (Throwable e) {
            return bad("Overlay transform failed: " + (e.getCause()!=null?e.getCause().getMessage():e.getMessage()));
        } finally { src.release(); ref.release(); }
    }

    private static Result bad(String why){return new Result(null,0,Double.NaN,Double.NaN,Double.NaN,why);}
    private GeometryOverlayRepair() {}
}
