package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

/** Human-first GMT QC using the wide-scale GMT dial seed shared with the visual master. */
final class GmtHumanQcAnalyzerV2 {
    static final class Result {
        final String report;
        final GmtHumanQcMath.PoseLabel poseLabel;
        final GmtHumanQcMath.Attention rotationAttention;
        final GmtHumanQcMath.Attention clearanceAttention;
        final double localTrackRollDeg;
        final boolean localFrameValid;
        Result(String report,GmtHumanQcMath.PoseLabel pose,GmtHumanQcMath.Attention rotation,
               GmtHumanQcMath.Attention clearance,double roll,boolean valid){
            this.report=report;poseLabel=pose;rotationAttention=rotation;clearanceAttention=clearance;
            localTrackRollDeg=roll;localFrameValid=valid;
        }
    }

    static Result analyse(Bitmap watch,String modelRef){
        if(!CanonicalGmtGeometryAnalyzer.supports(modelRef))return new Result("",GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,false);
        if(watch==null)return unavailable("watch image missing");
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            GmtDialSeedAnalyzer.Result dial=GmtDialSeedAnalyzer.analyse(src);
            if(!dial.valid)return unavailable("wide-scale dial geometry could not be verified: "+dial.reason);
            double cx=dial.x,cy=dial.y,r=dial.r,q=dial.quality;
            if(!(r>20)||q<0.45)return unavailable("dial geometry confidence is too low for local 12-marker QC");
            // Use the same edge-fitted centre as the visual master. The 12-marker axis is
            // measured against centre -> 60 tick, so a few px of Hough centre error becomes
            // roughly a degree of fake marker rotation.
            DialEdgeEllipseFit.Fit edge=SafePerspectiveGmtOverlayV2.fitDialEdgeBgr(src,cx,cy,r);
            String centreNote;
            if(edge!=null){
                centreNote=String.format(Locale.US,"Dial centre re-fitted to dial edge: %.1f, %.1f; radius %.1f px (moved %.1f px).",
                        edge.cx,edge.cy,edge.meanRadius(),Math.hypot(edge.cx-cx,edge.cy-cy));
                cx=edge.cx;cy=edge.cy;r=edge.meanRadius();
            }else centreNote="Dial centre: UNREFINED Hough proposal (dial-edge re-fit unavailable).";

            GmtTwelveLandmarkAnalyzer.Result primary=GmtTwelveLandmarkAnalyzer.analyse(src,cx,cy,r);
            GmtTwelveLandmarkAnalyzer.Result twelve=primary;boolean recovered=false;
            if(!primary.valid){twelve=GmtTwelveRecoveryAnalyzer.analyse(src,cx,cy,r,primary.reason);recovered=twelve.valid;}

            // A numerically resolved 59/60/01 frame is not automatically trustworthy.
            // Alpha39 showed a real dealer photo where the frame score was only 3.2 and
            // the inferred roll was +19.9 degrees. Feeding that roll into rehaut sectors
            // rotated the meaning of 12/3/6/9. Use local roll only when the landmark
            // detector itself says it is stable and the frame score clears a modest floor.
            boolean stableFrame=twelve.valid&&twelve.detectorStable
                    &&Double.isFinite(twelve.minuteFrameScore)&&twelve.minuteFrameScore>=10.0;
            double poseRoll=stableFrame?twelve.trackRollClockDeg:0.0;

            GmtRehautPoseAnalyzer.Result rehaut=GmtRehautPoseAnalyzer.analyse(src,cx,cy,r,poseRoll);
            GmtRehautSectorAnalyzer.Result sectors;boolean selfSeeded=false;
            if(rehaut.valid){
                sectors=GmtRehautSectorAnalyzer.analyse(src,cx,cy,rehaut.innerSeedPx,rehaut.outerSeedPx,poseRoll);
                if(!sectors.valid){sectors=GmtRehautSectorAutoAnalyzer.analyse(src,cx,cy,r,poseRoll);selfSeeded=sectors.valid;}
            }else{sectors=GmtRehautSectorAutoAnalyzer.analyse(src,cx,cy,r,poseRoll);selfSeeded=sectors.valid;}
            GmtEllipsePoseAnalyzer.Result ellipse=GmtEllipsePoseAnalyzer.analyse(src,cx,cy,r);

            double disagreement=Double.NaN;
            if(rehaut.valid&&ellipse.valid&&rehaut.firstHarmonicStrength>=0.08)
                disagreement=axisDisagreement(rehaut.widestClockDeg,ellipse.minorAxisClockDeg);
            GmtHumanQcMath.PoseDecision pose=GmtHumanPosePolicy.classify(rehaut,sectors,ellipse,disagreement);
            GmtHumanQcMath.RotationDecision rotation;
            GmtHumanQcMath.ClearanceDecision clearance;
            GmtHumanQcMath.GapTrend rehautTrend=sectors.gapTrendAt12();
            if(twelve.valid){
                rotation=GmtHumanQcMath.assessRotation(twelve.wholeAxisErrorDeg,twelve.topEdgeErrorDeg,twelve.sideAsymmetry,
                        twelve.triangleWidthPx,pose.label,stableFrame);
                double scale=ellipse.valid?GmtHumanQcMath.normalizedClearancePerspectiveScale(
                        ellipse.axisRatio,ellipse.minorAxisClockDeg,poseRoll):Double.NaN;
                clearance=GmtDirectionalClearancePolicy.assess(twelve.topClearance,scale,rehautTrend,pose.label);

                // Fine spacing must fail closed when 59/60/01 or the triangle is unstable.
                // Keep a concern if one is visible, but never let unstable landmarks issue
                // a CLEAR or a precise STRONG verdict.
                if(!stableFrame){
                    if(clearance.attention==GmtHumanQcMath.Attention.CLEAR){
                        clearance=new GmtHumanQcMath.ClearanceDecision(
                                GmtHumanQcMath.Attention.UNASSESSABLE,clearance.trend,
                                clearance.observedGap,clearance.correctedEstimate,clearance.perspectiveScale,
                                "local minute/triangle landmarks are low confidence, so fine 12 clearance cannot be cleared from this photo");
                    }else if(clearance.attention==GmtHumanQcMath.Attention.STRONG){
                        clearance=new GmtHumanQcMath.ClearanceDecision(
                                GmtHumanQcMath.Attention.CHECK,clearance.trend,
                                clearance.observedGap,clearance.correctedEstimate,clearance.perspectiveScale,
                                "one-sided evidence supports concern, but local landmarks are low confidence and cannot support a STRONG verdict; "+clearance.reason);
                    }
                }else if(recovered&&!twelve.detectorStable&&clearance.attention==GmtHumanQcMath.Attention.STRONG){
                    clearance=new GmtHumanQcMath.ClearanceDecision(GmtHumanQcMath.Attention.CHECK,clearance.trend,
                            clearance.observedGap,clearance.correctedEstimate,clearance.perspectiveScale,
                            "recovered low-resolution landmarks support concern, but confidence is insufficient for a STRONG verdict; "+clearance.reason);
                }
            }else{
                rotation=new GmtHumanQcMath.RotationDecision(GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,Double.NaN,Double.NaN,Double.NaN,false,false,
                        "local 12-marker landmarks unavailable: "+twelve.reason);
                clearance=new GmtHumanQcMath.ClearanceDecision(GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.GapTrend.UNKNOWN,
                        Double.NaN,Double.NaN,Double.NaN,"local 12-marker landmarks unavailable: "+twelve.reason);
            }

            StringBuilder out=new StringBuilder("\n\nHUMAN 12-MARKER QC\n");
            if(twelve.valid){
                out.append("12 marker: ").append(clearance.attention).append(" - ").append(clearanceSummary(clearance)).append("\n");
                out.append("Alignment: ").append(rotation.attention).append(" - ").append(rotationSummary(rotation)).append("\n");
            }else out.append("12 marker: UNASSESSABLE - local minute-track/triangle landmarks were not verified; no pass is inferred.\n");
            out.append("Perspective: ").append(pose.label).append(" - ").append(pose.reason).append(".\n");

            out.append("\nDiagnostics\n");
            out.append(String.format(Locale.US,"Wide-scale GMT dial seed: centre %.1f, %.1f; radius %.1f px; quality %.2f.\n",dial.x,dial.y,dial.r,q));
            out.append(centreNote).append("\n");
            out.append("The local minute track defines true 12; the triangle is measured against it and never used to straighten itself.\n");
            if(twelve.valid){
                out.append(String.format(Locale.US,"Local minute frame: 59/60/01 RESOLVED (%s); track roll %+.2f°, pitch %.2f°, frame score %.1f%s.\n",
                        recovered?"recovered from compressed/low-resolution landmarks":"primary physical-landmark path",
                        twelve.trackRollClockDeg,twelve.tickPitchDeg,twelve.minuteFrameScore,stableFrame?"":"; LOW CONFIDENCE"));
            }else out.append("Local minute frame: UNRESOLVED - ").append(twelve.reason).append(".\n");
            out.append(String.format(Locale.US,"Pose-sector orientation: %+.2f° (%s).\n",poseRoll,
                    stableFrame?"trusted local 60-minute frame":"image axes used because local minute frame is low confidence"));

            if(sectors.valid){
                out.append(String.format(Locale.US,"Local rehaut sectors (%s): 12 %.1f px (%.2f cov), 3 %.1f (%.2f), 6 %.1f (%.2f), 9 %.1f (%.2f); V %+5.3f, H %+5.3f, min/mean %.3f; 12-gap direction %s.\n",
                        selfSeeded?"self-seeded":"global-edge-seeded",sectors.width12,sectors.coverage12,sectors.width3,sectors.coverage3,
                        sectors.width6,sectors.coverage6,sectors.width9,sectors.coverage9,sectors.verticalAsymmetry,sectors.horizontalAsymmetry,
                        sectors.minOverMean,rehautTrend.name().toLowerCase(Locale.US)));
            }else out.append("Local rehaut sectors: unavailable (").append(sectors.reason).append(").\n");
            if(rehaut.valid){
                out.append(String.format(Locale.US,"Global rehaut diagnostic: V %+5.3f, H %+5.3f; minimum/mean %.3f; harmonic %.3f; coverage %.2f; fit residual %.3f%s.\n",
                        rehaut.watchVerticalAsymmetry,rehaut.watchHorizontalAsymmetry,rehaut.minWidthOverMean,rehaut.firstHarmonicStrength,
                        rehaut.edgeCoverage,rehaut.fitResidual,rehaut.fitResidual>0.16?" - noisy, not allowed to veto local sector evidence":""));
            }else out.append("Global rehaut diagnostic: unavailable (").append(rehaut.reason).append(").\n");
            if(ellipse.valid){
                out.append(String.format(Locale.US,"Planar cue: ellipse ratio %.4f, equivalent tilt %.1f°, minor axis %.1f° clock%s.\n",
                        ellipse.axisRatio,ellipse.tiltDeg,ellipse.minorAxisClockDeg,
                        Double.isFinite(disagreement)?String.format(Locale.US,", global cue-axis disagreement %.1f°",disagreement):""));
            }else out.append("Planar cue: unavailable (").append(ellipse.reason).append(").\n");

            if(twelve.valid){
                out.append(String.format(Locale.US,"12 alignment detail: whole-marker rotation %+4.2f°, top-edge support %+4.2f°, 59/01 spacing asymmetry %+5.3f. %s.\n",
                        twelve.wholeAxisErrorDeg,twelve.topEdgeErrorDeg,twelve.sideAsymmetry,rotation.reason));
                out.append(String.format(Locale.US,"12 clearance detail: observed %.3f",twelve.topClearance));
                if(Double.isFinite(clearance.perspectiveScale)){
                    out.append(String.format(Locale.US,", ellipse scale %.3f",clearance.perspectiveScale));
                    if(Double.isFinite(clearance.correctedEstimate))out.append(String.format(Locale.US,", magnitude-only estimate %.3f",clearance.correctedEstimate));
                }
                out.append(", decision trend ").append(clearance.trend.name().toLowerCase(Locale.US)).append(". ").append(clearance.reason).append(".\n");
                out.append(String.format(Locale.US,"Human geometry: centring %+5.3f; 59-side spacing %.3f; 01-side spacing %.3f.\n",
                        twelve.horizontalOffset,twelve.leftClearance,twelve.rightClearance));
            }

            if(!stableFrame&&twelve.valid)
                out.append("Recommended action: use a squarer or clearer photo before clearing fine 12-marker geometry; directional rehaut evidence may still be informative.\n");
            else if(twelve.valid&&(rotation.attention==GmtHumanQcMath.Attention.CHECK||rotation.attention==GmtHumanQcMath.Attention.STRONG
                    ||clearance.attention==GmtHumanQcMath.Attention.CHECK||clearance.attention==GmtHumanQcMath.Attention.STRONG))
                out.append("Recommended action: inspect the highlighted 12-marker relationship closely.\n");
            else if(!twelve.valid)out.append("Recommended action: inspect manually or use a clearer/original image; unresolved local landmarks are not a pass.\n");
            else if(pose.label==GmtHumanQcMath.PoseLabel.RETAKE)out.append("Recommended action: retake more square-on before relying on fine spacing magnitude; visible one-sided evidence remains highlighted.\n");
            else out.append("Recommended action: no human-attention condition was resolved in the local 12-marker relationships.\n");

            return new Result(out.toString(),pose.label,rotation.attention,clearance.attention,
                    stableFrame?twelve.trackRollClockDeg:Double.NaN,stableFrame);
        }catch(Throwable t){return unavailable("human GMT QC failed closed: "+t.getClass().getSimpleName());}
        finally{src.release();}
    }

    private static String clearanceSummary(GmtHumanQcMath.ClearanceDecision c){
        if(c.attention==GmtHumanQcMath.Attention.STRONG&&c.trend==GmtHumanQcMath.GapTrend.INFLATED)return "gap is present but slightly small, and the photo angle is helping it look larger; inspect closely";
        if(c.attention==GmtHumanQcMath.Attention.STRONG)return "very small or touching clearance is strongly indicated; inspect closely";
        if(c.attention==GmtHumanQcMath.Attention.CHECK&&c.trend==GmtHumanQcMath.GapTrend.COMPRESSED)return "gap looks slightly small, but perspective may be making it look worse; inspect or compare with a squarer photo";
        if(c.attention==GmtHumanQcMath.Attention.CHECK)return "gap is present but appears slightly smaller than expected; inspect closely";
        if(c.attention==GmtHumanQcMath.Attention.CLEAR)return "no low-clearance issue is resolved; a slightly large gap is not treated as a defect";
        return "clearance could not be assessed reliably";
    }
    private static String rotationSummary(GmtHumanQcMath.RotationDecision r){
        if(r.attention==GmtHumanQcMath.Attention.STRONG)return "visible marker rotation/side-spacing skew is strongly indicated";
        if(r.attention==GmtHumanQcMath.Attention.CHECK)return "possible visible rotation or unequal 59/01 spacing; inspect closely";
        if(r.attention==GmtHumanQcMath.Attention.CLEAR)return "no rotation is resolved strongly enough to be visible at this image scale";
        return "alignment could not be assessed reliably";
    }
    private static Result unavailable(String reason){
        return new Result("\n\nHUMAN 12-MARKER QC\nUNASSESSABLE - "+reason+". No pass is inferred from missing evidence.\n",
                GmtHumanQcMath.PoseLabel.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,
                GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,false);
    }
    private static double axisDisagreement(double a,double b){double d=Math.abs((a-b)%180.0);if(d>90)d=180-d;return d;}
    private GmtHumanQcAnalyzerV2(){}
}
