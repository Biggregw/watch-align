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

import java.lang.reflect.Field;
import java.lang.reflect.Method;

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

            Method detectDial = privateMethod("detectDial", Mat.class);
            Method measureMarkerSet = privateMethod("measureMarkerSet", Mat.class, detectDial.getReturnType());
            Method commonCount = privateMethod("commonMarkerCount", measureMarkerSet.getReturnType(), measureMarkerSet.getReturnType());
            Method commonRms = privateMethod("commonMarkerRms", measureMarkerSet.getReturnType(), measureMarkerSet.getReturnType());
            Method perspective = privateMethod("perspectiveEquivalent", Mat.class, detectDial.getReturnType());

            Object sd = detectDial.invoke(null, src), rd = detectDial.invoke(null, ref);
            if (sd == null || rd == null) return bad("Dial geometry could not be verified for both images.");
            double sx = number(sd,"x"), sy = number(sd,"y"), sr = number(sd,"r"), sq = number(sd,"quality");
            double rx = number(rd,"x"), ry = number(rd,"y"), rr = number(rd,"r"), rq = number(rd,"quality");
            if (sq < 0.56 || rq < 0.56 || sr <= 0 || rr <= 0) return bad("Dial geometry quality is below the overlay threshold.");

            Object sm = measureMarkerSet.invoke(null, src, sd), rm = measureMarkerSet.invoke(null, ref, rd);
            double smr = number(sm,"medianRadius"), rmr = number(rm,"medianRadius");
            double sga = number(sm,"globalRotation"), rga = number(rm,"globalRotation");
            int common = ((Number)commonCount.invoke(null, sm, rm)).intValue();
            double rms = ((Number)commonRms.invoke(null, sm, rm)).doubleValue();
            double sp = ((Number)perspective.invoke(null, src, sd)).doubleValue();
            double rp = ((Number)perspective.invoke(null, ref, rd)).doubleValue();
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

    private static Method privateMethod(String name, Class<?>... types) throws Exception {
        Method m = WatchAlignCoreV7.class.getDeclaredMethod(name, types); m.setAccessible(true); return m;
    }
    private static double number(Object obj,String field) throws Exception {
        Field f=obj.getClass().getDeclaredField(field); f.setAccessible(true); return ((Number)f.get(obj)).doubleValue();
    }
    private static Result bad(String why){return new Result(null,0,Double.NaN,Double.NaN,Double.NaN,why);}
    private GeometryOverlayRepair() {}
}
