package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Human-style GMT 12-marker QC.
 *
 * Local 59/60/01 geometry is primary. Rehaut and ellipse are pose/context cues,
 * not prerequisites that are allowed to erase a visible marker relationship.
 */
final class GmtHumanQcAnalyzer {
    static final class Result {
        final String report;
        final GmtHumanQcMath.PoseLabel poseLabel;
        final GmtHumanQcMath.Attention rotationAttention;
        final GmtHumanQcMath.Attention clearanceAttention;
        final double localTrackRollDeg;
        final boolean localFrameValid;
        Result(String report,GmtHumanQcMath.PoseLabel pose,
               GmtHumanQcMath.Attention rotation,GmtHumanQcMath.Attention clearance,
               double roll,boolean frameValid){
            this.report=report;poseLabel=pose;rotationAttention=rotation;clearanceAttention=clearance;
            localTrackRollDeg=roll;localFrameValid=frameValid;
        }
    }

    static Result analyse(Bitmap watch,String modelRef){
        if(!CanonicalGmtGeometryAnalyzer.supports(modelRef))
            return new Result("",GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                    GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,false);
        if(watch==null)return unavailable("watch image missing");

        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class);Object dial=detect.invoke(null,src);
            if(dial==null)return unavailable("dial geometry could not be verified");
            double cx=num(dial,"x"),cy=num(dial,"y"),r=num(dial,"r"),q=num(dial,"quality");
            if(!(r>20)||q<0.52)return unavailable("dial geometry confidence is too low for local 12-marker QC");

            // Human pipeline first: do not rotate the image using the old global marker
            // estimate. The 59/60/01 track establishes the local watch frame itself.
            GmtTwelveLandmarkAnalyzer.Result twelve=GmtTwelveLandmarkAnalyzer.analyse(src,cx,cy,r);
            double roll=twelve.valid?twelve.trackRollClockDeg:0.0;

            GmtRehautPoseAnalyzer.Result rehaut=GmtRehautPoseAnalyzer.analyse(src,cx,cy,r,roll);
            GmtEllipsePoseAnalyzer.Result ellipse=GmtEllipsePoseAnalyzer.analyse(src,cx,cy,r);

            double disagreement=Double.NaN;
            if(rehaut.valid&&ellipse.valid&&rehaut.firstHarmonicStrength>=0.08)
                disagreement=axisDisagreement(rehaut.widestClockDeg,ellipse.minorAxisClockDeg);
            GmtHumanQcMath.PoseDecision pose=GmtHumanPosePolicy.classify(rehaut,ellipse,disagreement);

            GmtHumanQcMath.RotationDecision rotation;
            GmtHumanQcMath.ClearanceDecision clearance;
            if(twelve.valid){
                rotation=GmtHumanQcMath.assessRotation(
                        twelve.wholeAxisErrorDeg,twelve.topEdgeErrorDeg,twelve.sideAsymmetry,
                        twelve.triangleWidthPx,pose.label,twelve.detectorStable);
                double perspectiveScale=ellipse.valid
                        ?GmtHumanQcMath.normalizedClearancePerspectiveScale(
                                ellipse.axisRatio,ellipse.minorAxisClockDeg,twelve.trackRollClockDeg)
                        :Double.NaN;
                clearance=GmtHumanQcMath.assessLowClearance(twelve.topClearance,perspectiveScale,pose.label);
            }else{
                rotation=new GmtHumanQcMath.RotationDecision(
                        GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,Double.NaN,Double.NaN,Double.NaN,
                        false,false,"local 12-marker landmarks unavailable: "+twelve.reason);
                clearance=new GmtHumanQcMath.ClearanceDecision(
                        GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.GapTrend.UNKNOWN,
                        Double.NaN,Double.NaN,Double.NaN,"local 12-marker landmarks unavailable: "+twelve.reason);
            }

            StringBuilder out=new StringBuilder("\n\nHUMAN 12-MARKER QC\n");
            out.append("Primary GMT12 review. The local minute track defines true 12; the triangle is measured against it and never used to straighten itself.\n");
            if(twelve.valid){
                out.append(String.format(Locale.US,
                        "Local minute frame: 59/60/01 RESOLVED; track roll %+.2f°, pitch %.2f°, frame score %.1f%s.\n",
                        twelve.trackRollClockDeg,twelve.tickPitchDeg,twelve.minuteFrameScore,
                        twelve.detectorStable?"":"; LOW CONFIDENCE"));
            }else{
                out.append("Local minute frame: UNRESOLVED — ").append(twelve.reason).append(".\n");
            }

            out.append("Photo angle: ").append(pose.label).append(" — ").append(pose.reason).append(".\n");
            if(rehaut.valid){
                out.append(String.format(Locale.US,
                        "Rehaut cue: watch V %+5.3f, H %+5.3f; minimum/mean %.3f; harmonic %.3f; coverage %.2f; fit residual %.3f%s.\n",
                        rehaut.watchVerticalAsymmetry,rehaut.watchHorizontalAsymmetry,
                        rehaut.minWidthOverMean,rehaut.firstHarmonicStrength,rehaut.edgeCoverage,rehaut.fitResidual,
                        rehaut.fitResidual>0.16?" — noisy, advisory only":""));
            }else out.append("Rehaut cue: unavailable ("+rehaut.reason+"); local marker QC is not automatically discarded.\n");
            if(ellipse.valid){
                out.append(String.format(Locale.US,"Planar cue: ellipse ratio %.4f, equivalent tilt %.1f°, minor axis %.1f° clock%s.\n",
                        ellipse.axisRatio,ellipse.tiltDeg,ellipse.minorAxisClockDeg,
                        Double.isFinite(disagreement)?String.format(Locale.US,", cue-axis disagreement %.1f°",disagreement):""));
            }else out.append("Planar cue: unavailable ("+ellipse.reason+").\n");

            if(twelve.valid){
                out.append(String.format(Locale.US,
                        "12 marker alignment: %s — whole-marker rotation %+4.2f°, top-edge support %+4.2f°, 59/01 spacing asymmetry %+5.3f.\n",
                        rotation.attention,twelve.wholeAxisErrorDeg,twelve.topEdgeErrorDeg,twelve.sideAsymmetry));
                out.append("  ").append(rotation.reason).append(".\n");
                out.append(String.format(Locale.US,
                        "12 top clearance: %s — observed %.3f",clearance.attention,twelve.topClearance));
                if(Double.isFinite(clearance.perspectiveScale)){
                    out.append(String.format(Locale.US,", perspective trend %s (scale %.3f)",
                            clearance.trend.name().toLowerCase(Locale.US),clearance.perspectiveScale));
                    if(Double.isFinite(clearance.correctedEstimate))
                        out.append(String.format(Locale.US,", directional estimate %.3f",clearance.correctedEstimate));
                }else out.append(", exact perspective scale unavailable");
                out.append(".\n  ").append(clearance.reason).append(".\n");
                out.append(String.format(Locale.US,
                        "Human geometry: centring %+5.3f; 59-side spacing %.3f; 01-side spacing %.3f.\n",
                        twelve.horizontalOffset,twelve.leftClearance,twelve.rightClearance));
            }else{
                out.append("12 marker alignment: UNASSESSABLE — local 59/60/01 frame was not verified.\n");
                out.append("12 top clearance: UNASSESSABLE — no pass is inferred from missing landmarks.\n");
            }

            if(twelve.valid&&(rotation.attention==GmtHumanQcMath.Attention.CHECK||rotation.attention==GmtHumanQcMath.Attention.STRONG
                    ||clearance.attention==GmtHumanQcMath.Attention.CHECK||clearance.attention==GmtHumanQcMath.Attention.STRONG)){
                out.append("Recommended action: inspect the highlighted 12-marker relationship closely.\n");
            }else if(!twelve.valid){
                out.append("Recommended action: inspect manually or retake more clearly; unresolved local landmarks are not a pass.\n");
            }else if(pose.label==GmtHumanQcMath.PoseLabel.RETAKE){
                out.append("Recommended action: retake more square-on before relying on fine spacing magnitude; visible one-sided evidence remains highlighted.\n");
            }else{
                out.append("Recommended action: no human-attention condition was resolved in the local 12-marker relationships.\n");
            }

            return new Result(out.toString(),pose.label,rotation.attention,clearance.attention,
                    twelve.valid?twelve.trackRollClockDeg:Double.NaN,twelve.valid);
        }catch(Throwable t){
            return unavailable("human GMT QC failed closed: "+t.getClass().getSimpleName());
        }finally{src.release();}
    }

    private static Result unavailable(String reason){
        String report="\n\nHUMAN 12-MARKER QC\nUNASSESSABLE — "+reason+". No pass is inferred from missing evidence.\n";
        return new Result(report,GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,false);
    }

    private static double axisDisagreement(double a,double b){
        double d=Math.abs((a-b)%180.0);if(d>90.0)d=180.0-d;return d;
    }
    private static Method method(String name,Class<?>...types)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String name)throws Exception{return ((Number)field(o,name)).doubleValue();}
    private GmtHumanQcAnalyzer(){}
}
