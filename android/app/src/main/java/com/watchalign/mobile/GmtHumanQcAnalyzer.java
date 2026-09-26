package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Human-style GMT 12-marker QC for the latest Android inspection flow.
 *
 * This layer is intentionally advisory. It does not move the visual master,
 * change model geometry, or claim Rolex manufacturing tolerances. It combines
 * rehaut pose, an independent ellipse cue and local 12-marker relationships to
 * decide what a careful human should inspect.
 */
final class GmtHumanQcAnalyzer {
    static final class Result {
        final String report;
        final GmtHumanQcMath.PoseLabel poseLabel;
        final GmtHumanQcMath.Attention rotationAttention;
        final GmtHumanQcMath.Attention clearanceAttention;
        Result(String report,GmtHumanQcMath.PoseLabel pose,
               GmtHumanQcMath.Attention rotation,GmtHumanQcMath.Attention clearance){
            this.report=report;poseLabel=pose;rotationAttention=rotation;clearanceAttention=clearance;
        }
    }

    static Result analyse(Bitmap watch,String modelRef){
        if(!CanonicalGmtGeometryAnalyzer.supports(modelRef))return new Result("",GmtHumanQcMath.PoseLabel.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE);
        if(watch==null)return unavailable("watch image missing");

        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            Method detect=method("detectDial",Mat.class);Object dial=detect.invoke(null,src);
            if(dial==null)return unavailable("dial geometry could not be verified");
            double cx=num(dial,"x"),cy=num(dial,"y"),r=num(dial,"r"),q=num(dial,"quality");
            if(!(r>20)||q<0.56)return unavailable("dial geometry confidence is too low for local 12-marker QC");

            Method measure=method("measureMarkerSet",Mat.class,dial.getClass());Object set=measure.invoke(null,src,dial);
            double roll=num(set,"globalRotation");if(!Double.isFinite(roll))roll=0.0;

            GmtRehautPoseAnalyzer.Result rehaut=GmtRehautPoseAnalyzer.analyse(src,cx,cy,r,roll);
            GmtEllipsePoseAnalyzer.Result ellipse=GmtEllipsePoseAnalyzer.analyse(src,cx,cy,r);

            double disagreement=Double.NaN;
            if(rehaut.valid&&ellipse.valid&&rehaut.firstHarmonicStrength>=0.08)
                disagreement=axisDisagreement(rehaut.widestClockDeg,ellipse.minorAxisClockDeg);

            GmtHumanQcMath.PoseDecision pose;
            if(rehaut.valid){
                pose=GmtHumanQcMath.classifyPose(
                        rehaut.minWidthOverMean,rehaut.edgeCoverage,rehaut.fitResidual,
                        rehaut.firstHarmonicStrength,ellipse.valid?ellipse.tiltDeg:Double.NaN,
                        disagreement);
            }else{
                pose=new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                        "rehaut pose could not be constrained: "+rehaut.reason);
            }

            GmtTwelveLandmarkAnalyzer.Result twelve=GmtTwelveLandmarkAnalyzer.analyse(src,cx,cy,r,roll);
            GmtHumanQcMath.RotationDecision rotation;
            GmtHumanQcMath.ClearanceDecision clearance;
            if(twelve.valid){
                rotation=GmtHumanQcMath.assessRotation(
                        twelve.wholeAxisErrorDeg,twelve.topEdgeErrorDeg,twelve.sideAsymmetry,
                        twelve.triangleWidthPx,pose.label,twelve.detectorStable);
                double perspectiveScale=ellipse.valid
                        ?GmtHumanQcMath.normalizedClearancePerspectiveScale(
                                ellipse.axisRatio,ellipse.minorAxisClockDeg,roll)
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
            out.append("Advisory attention layer. It highlights relationships a careful reviewer could inspect; it is not a Rolex tolerance or authenticity verdict.\n");
            out.append("Photo angle: ").append(pose.label).append(" — ").append(pose.reason).append(".\n");
            if(rehaut.valid){
                out.append(String.format(Locale.US,
                        "Rehaut pose: watch V %+5.3f, H %+5.3f; minimum/mean %.3f; harmonic %.3f; coverage %.2f; fit residual %.3f.\n",
                        rehaut.watchVerticalAsymmetry,rehaut.watchHorizontalAsymmetry,
                        rehaut.minWidthOverMean,rehaut.firstHarmonicStrength,rehaut.edgeCoverage,rehaut.fitResidual));
            }else out.append("Rehaut pose: unavailable ("+rehaut.reason+").\n");
            if(ellipse.valid){
                out.append(String.format(Locale.US,"Planar cue: ellipse ratio %.4f, equivalent tilt %.1f°, minor axis %.1f° clock%s.\n",
                        ellipse.axisRatio,ellipse.tiltDeg,ellipse.minorAxisClockDeg,
                        Double.isFinite(disagreement)?String.format(Locale.US,", cue-axis disagreement %.1f°",disagreement):""));
            }else out.append("Planar cue: unavailable ("+ellipse.reason+").\n");

            if(twelve.valid){
                out.append(String.format(Locale.US,
                        "12 marker alignment: %s — whole-marker rotation %+4.2f°, top-edge support %+4.2f°, 59/01 spacing asymmetry %+5.3f%s.\n",
                        rotation.attention,twelve.wholeAxisErrorDeg,twelve.topEdgeErrorDeg,twelve.sideAsymmetry,
                        twelve.detectorStable?"":"; local minute landmarks partly reconstructed"));
                out.append("  ").append(rotation.reason).append(".\n");
                out.append(String.format(Locale.US,
                        "12 top clearance: %s — observed %.3f",clearance.attention,twelve.topClearance));
                if(Double.isFinite(clearance.perspectiveScale)){
                    out.append(String.format(Locale.US,", perspective trend %s (scale %.3f)",
                            clearance.trend.name().toLowerCase(Locale.US),clearance.perspectiveScale));
                    if(Double.isFinite(clearance.correctedEstimate))
                        out.append(String.format(Locale.US,", directional estimate %.3f",clearance.correctedEstimate));
                }else out.append(", perspective correction magnitude unavailable");
                out.append(".\n  ").append(clearance.reason).append(".\n");
                out.append(String.format(Locale.US,
                        "Local geometry diagnostics: horizontal offset %+5.3f; left spacing %.3f; right spacing %.3f.\n",
                        twelve.horizontalOffset,twelve.leftClearance,twelve.rightClearance));
            }else{
                out.append("12 marker alignment: UNASSESSABLE — ").append(twelve.reason).append(".\n");
                out.append("12 top clearance: UNASSESSABLE — required local landmarks were not verified.\n");
            }

            if(pose.label==GmtHumanQcMath.PoseLabel.RETAKE||pose.label==GmtHumanQcMath.PoseLabel.UNASSESSABLE)
                out.append("Recommended action: retake the photo more square-on before relying on fine spacing or rotation measurements.\n");
            else if(rotation.attention==GmtHumanQcMath.Attention.CHECK||rotation.attention==GmtHumanQcMath.Attention.STRONG
                    ||clearance.attention==GmtHumanQcMath.Attention.CHECK||clearance.attention==GmtHumanQcMath.Attention.STRONG)
                out.append("Recommended action: inspect the highlighted 12-marker relationship closely rather than silently passing it.\n");
            else out.append("Recommended action: no human-attention condition was resolved in this 12-marker layer.\n");

            return new Result(out.toString(),pose.label,rotation.attention,clearance.attention);
        }catch(Throwable t){
            return unavailable("human GMT QC failed closed: "+t.getClass().getSimpleName());
        }finally{src.release();}
    }

    private static Result unavailable(String reason){
        String report="\n\nHUMAN 12-MARKER QC\nUNASSESSABLE — "+reason+". No pass is inferred from missing evidence.\n";
        return new Result(report,GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE);
    }

    private static double axisDisagreement(double a,double b){
        double d=Math.abs((a-b)%180.0);if(d>90.0)d=180.0-d;return d;
    }
    private static Method method(String name,Class<?>...types)throws Exception{Method m=WatchAlignCoreV7.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String name)throws Exception{return ((Number)field(o,name)).doubleValue();}
    private GmtHumanQcAnalyzer(){}
}
