package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.Locale;

/** Human-first GMT QC using the wide-scale GMT dial seed shared with the visual master. */
final class GmtHumanQcAnalyzerV2 {
    /** Combined base-edge and tick-end location uncertainty, pixels. */
    static final double GAP_PX_UNCERTAINTY = 0.75;
    /** Below this triangle width (px) a pixel is >= 0.025 of gap and landmarks are unreliable. */
    static final double MIN_TRIANGLE_PX = 40.0;
    static final class Result {
        final String report;
        final GmtHumanQcMath.PoseLabel poseLabel;
        final GmtHumanQcMath.Attention rotationAttention;
        final GmtHumanQcMath.Attention clearanceAttention;
        final double localTrackRollDeg;
        final boolean localFrameValid;
        final GmtHumanSummary.Input summary;
        MeasuredOverlayRenderer.Drawing drawing=new MeasuredOverlayRenderer.Drawing();
        GmtSixLandmarkAnalyzer.Result six;
        Result(String report,GmtHumanQcMath.PoseLabel pose,GmtHumanQcMath.Attention rotation,
               GmtHumanQcMath.Attention clearance,double roll,boolean valid){
            this(report,pose,rotation,clearance,roll,valid,new GmtHumanSummary.Input());
        }
        Result(String report,GmtHumanQcMath.PoseLabel pose,GmtHumanQcMath.Attention rotation,
               GmtHumanQcMath.Attention clearance,double roll,boolean valid,GmtHumanSummary.Input summary){
            this.report=report;poseLabel=pose;rotationAttention=rotation;clearanceAttention=clearance;
            localTrackRollDeg=roll;localFrameValid=valid;this.summary=summary;
        }
    }

    static Result analyse(Bitmap watch,String modelRef){return analyse(watch,modelRef,null);}

    /** @param manual hand-aligned dial (12/6 dial-edge taps), used instead of the automatic seed */
    static Result analyse(Bitmap watch,String modelRef,PerspectiveGmtOverlay.DialSeed manual){
        if(!CanonicalGmtGeometryAnalyzer.supports(modelRef))return new Result("",GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                GmtHumanQcMath.Attention.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,false);
        if(watch==null)return unavailable("watch image missing");
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            GmtDialSeedAnalyzer.Result dial=manual!=null?null:GmtDialSeedAnalyzer.analyse(src);
            if(manual==null&&!dial.valid)return unavailable("wide-scale dial geometry could not be verified: "+dial.reason);
            double cx=manual!=null?manual.x:dial.x,cy=manual!=null?manual.y:dial.y,r=manual!=null?manual.r:dial.r,q=manual!=null?0.98:dial.quality;
            double seedX=cx,seedY=cy,seedR=r;
            if(!(r>20)||q<0.45)return unavailable("dial geometry confidence is too low for local 12-marker QC");
            // Use the same edge-fitted centre as the visual master. The 12-marker axis is
            // measured against centre -> 60 tick, so a few px of Hough centre error becomes
            // roughly a degree of fake marker rotation.
            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(src,cx,cy,r);
            String centreNote;
            if(edge!=null){
                centreNote=String.format(Locale.US,"Dial centre re-fitted to dial edge: %.1f, %.1f; radius %.1f px (moved %.1f px).",
                        edge.cx,edge.cy,edge.meanRadius(),Math.hypot(edge.cx-cx,edge.cy-cy));
                cx=edge.cx;cy=edge.cy;r=edge.meanRadius();
            }else centreNote="Dial centre: UNREFINED Hough proposal (dial-edge re-fit unavailable).";

            GmtTwelveLandmarkAnalyzer.Result primary=GmtTwelveLandmarkAnalyzer.analyse(src,cx,cy,r);
            GmtTwelveLandmarkAnalyzer.Result twelve=primary;boolean recovered=false;
            if(!primary.valid){twelve=GmtTwelveRecoveryAnalyzer.analyse(src,cx,cy,r,primary.reason);recovered=twelve.valid;}
            else GmtTwelveLandmarkAnalyzer.measureStability(src,cx,cy,r,primary);

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

            // Resampling check (alpha56). The same photo decoded on the phone and on the desktop
            // differs by well under one grey level, yet one photo read gap 0.07 on one and 0.14
            // on the other. A reading that moves by more than about a pixel when the photo is
            // reduced by 6-12% is not a measurement of the watch, so it gets no verdict. A concern
            // is kept (as CHECK) only when every re-measurement agrees on it.
            boolean gapUnstable=twelve.valid&&!recovered&&!twelve.resampleGapStable();
            boolean rotUnstable=twelve.valid&&!recovered&&!twelve.resampleRotStable();
            String moved=Double.isFinite(twelve.gapMax)
                    ?String.format(Locale.US,"the 12 reading moves when the photo is reduced by 6%% and 12%% (gap %.2f to %.2f, rotation %+.1f° to %+.1f°), so it is not a reliable measurement on this photo",
                            twelve.gapMin,twelve.gapMax,twelve.rotMin,twelve.rotMax)
                    :"the 12 marker is not found again when the photo is reduced by 6% or 12%, so the reading is not reliable on this photo";
            if(gapUnstable){
                boolean concern=clearance.attention==GmtHumanQcMath.Attention.CHECK||clearance.attention==GmtHumanQcMath.Attention.STRONG;
                boolean allSmall=Double.isFinite(twelve.gapMax)&&twelve.gapMax<GmtHumanQcMath.LOW_CLEARANCE_ATTENTION;
                clearance=new GmtHumanQcMath.ClearanceDecision(concern&&allSmall?GmtHumanQcMath.Attention.CHECK:GmtHumanQcMath.Attention.UNASSESSABLE,
                        clearance.trend,clearance.observedGap,clearance.correctedEstimate,clearance.perspectiveScale,
                        (concern&&allSmall?"small in every re-measurement, but ":"")+moved);
            }
            if(rotUnstable){
                boolean concern=rotation.attention==GmtHumanQcMath.Attention.CHECK||rotation.attention==GmtHumanQcMath.Attention.STRONG;
                boolean allTurned=Double.isFinite(twelve.rotMin)&&(twelve.rotMin>=1.0||twelve.rotMax<=-1.0);
                rotation=new GmtHumanQcMath.RotationDecision(concern&&allTurned?GmtHumanQcMath.Attention.CHECK:GmtHumanQcMath.Attention.UNASSESSABLE,
                        rotation.axisErrorDeg,rotation.baseErrorDeg,rotation.sideAsymmetry,rotation.visibleRisePx,rotation.baseCorroborates,rotation.spacingCorroborates,
                        (concern&&allTurned?"turned the same way in every re-measurement, but ":"")+moved);
            }

            // Size and hand gates (alpha52). Field set: every wrong 12-marker result had a
            // triangle under ~35 px or a hand beside the triangle.
            boolean tooSmall=false,handAtTwelve=false;
            if(twelve.valid&&Double.isFinite(twelve.triangleWidthPx)&&twelve.triangleWidthPx<MIN_TRIANGLE_PX){
                tooSmall=true;
                String why=String.format(Locale.US,"the 12 triangle is only %.0f px wide in this photo (minimum %.0f); too small to measure",twelve.triangleWidthPx,MIN_TRIANGLE_PX);
                rotation=new GmtHumanQcMath.RotationDecision(GmtHumanQcMath.Attention.UNASSESSABLE,rotation.axisErrorDeg,rotation.baseErrorDeg,
                        rotation.sideAsymmetry,rotation.visibleRisePx,false,false,why);
                clearance=new GmtHumanQcMath.ClearanceDecision(GmtHumanQcMath.Attention.UNASSESSABLE,clearance.trend,clearance.observedGap,
                        clearance.correctedEstimate,clearance.perspectiveScale,why);
            }else if(twelve.valid&&twelve.geometry!=null){
                Mat g8=new Mat();
                try{
                    Imgproc.cvtColor(src,g8,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(g8,g8,new org.opencv.core.Size(5,5),1.2);
                    HandIntrusion.Result hi=HandIntrusion.measure(intensityOf(g8),g8.cols(),g8.rows(),cx,cy,r,twelve.geometry);
                    if(hi.present){
                        handAtTwelve=true;
                        String why=hi.brightFraction>HandIntrusion.MAX_BRIGHT_FRACTION
                                ?String.format(Locale.US,"a hand is next to the 12 marker (%.0f%% of the surrounding dial is marker-bright); it corrupts the triangle outline and tick detection",100*hi.brightFraction)
                                :"a thin hand crosses the 12 marker area; it corrupts the triangle outline and tick detection";
                        rotation=new GmtHumanQcMath.RotationDecision(GmtHumanQcMath.Attention.UNASSESSABLE,rotation.axisErrorDeg,rotation.baseErrorDeg,
                                rotation.sideAsymmetry,rotation.visibleRisePx,false,false,why);
                        clearance=new GmtHumanQcMath.ClearanceDecision(GmtHumanQcMath.Attention.UNASSESSABLE,clearance.trend,clearance.observedGap,
                                clearance.correctedEstimate,clearance.perspectiveScale,why);
                    }
                }finally{g8.release();}
            }

            // Resolution gate (alpha50). The gap is a fraction of the triangle width, so one
            // pixel is 1/width of gap: ~0.018 on a 55 px triangle, which is the whole spread
            // between genuine readings (~0.085) and the attention level (0.070). Edge and
            // tick-end location together are good to about GAP_PX_UNCERTAINTY px. A verdict
            // either way is only given when it survives that; otherwise "too close to call".
            boolean gapResolutionLimited=false;
            if(twelve.valid&&Double.isFinite(twelve.topClearance)&&twelve.triangleWidthPx>0&&twelve.topClearance>0){
                double u=GAP_PX_UNCERTAINTY/twelve.triangleWidthPx;
                double g=twelve.topClearance, lim=GmtHumanQcMath.LOW_CLEARANCE_ATTENTION;
                boolean flagged=clearance.attention==GmtHumanQcMath.Attention.CHECK||clearance.attention==GmtHumanQcMath.Attention.STRONG;
                boolean clear=clearance.attention==GmtHumanQcMath.Attention.CLEAR;
                if((flagged&&g+u>=lim)||(clear&&g-u<lim)){
                    gapResolutionLimited=true;
                    clearance=new GmtHumanQcMath.ClearanceDecision(GmtHumanQcMath.Attention.UNASSESSABLE,clearance.trend,
                            clearance.observedGap,clearance.correctedEstimate,clearance.perspectiveScale,
                            String.format(Locale.US,"observed %.3f is within the ±%.2f px measurement uncertainty (±%.3f at this resolution, triangle %.0f px wide) of the %.3f attention level; too close to call, a closer photo is needed",
                                    g,GAP_PX_UNCERTAINTY,u,twelve.triangleWidthPx,lim));
                }
            }

            // 6 o'clock baton (alpha55): measured on the same fitted dial, gated the same way.
            GmtSixLandmarkAnalyzer.Result six=GmtSixLandmarkAnalyzer.analyse(src,cx,cy,r);
            // Orientation check: the 6 baton must sit opposite the 12 marker. On a photo turned
            // well off upright the "bottom" baton is the 3 or 9. With no 12 found the dial's
            // orientation is unknown, so the 6 can only be reported with low confidence.
            if(six.valid){
                if(twelve.valid&&twelve.geometry!=null){
                    double a12=Math.atan2(twelve.geometry.tick60[1]-cy,twelve.geometry.tick60[0]-cx);
                    double[] m6={(six.geometry.tick31[0]+six.geometry.tick29[0])/2,(six.geometry.tick31[1]+six.geometry.tick29[1])/2};
                    double a6=Math.atan2(m6[1]-cy,m6[0]-cx);
                    double d=Math.toDegrees(a6-a12);while(d>180)d-=360;while(d<=-180)d+=360;
                    if(Math.abs(Math.abs(d)-180)>8.0)six=new GmtSixLandmarkAnalyzer.Result("the marker found at the bottom is not opposite the 12 marker (photo turned?)");
                }else six=six.lowConfidence();
            }
            GmtHumanQcMath.SixDecision sixDecision=six.valid
                    ?GmtHumanQcMath.assessSix(six.centring,six.rotationDeg,six.widthPx,six.lengthPx,pose.label,six.stable)
                    :new GmtHumanQcMath.SixDecision(GmtHumanQcMath.Attention.UNASSESSABLE,false,false,false,six.reason);
            boolean handAtSix=false;
            if(six.valid&&!sixDecision.tooSmall){
                Mat g8=new Mat();
                try{
                    Imgproc.cvtColor(src,g8,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(g8,g8,new org.opencv.core.Size(5,5),1.2);
                    GmtSixLandmarkAnalyzer.Geometry sg=six.geometry;
                    HandIntrusion.Result hi=HandIntrusion.measure(intensityOf(g8),g8.cols(),g8.rows(),cx,cy,r,sg.polygon(),sg.tick31,sg.tick30,sg.tick29);
                    if(hi.present){
                        handAtSix=true;
                        sixDecision=new GmtHumanQcMath.SixDecision(GmtHumanQcMath.Attention.UNASSESSABLE,false,false,false,
                                "a hand is next to the 6 baton; it corrupts the outline and tick detection");
                    }
                }finally{g8.release();}
            }

            StringBuilder out=new StringBuilder("\n\nHUMAN 12-MARKER QC\n");
            if(twelve.valid){
                out.append("12 marker: ").append(clearance.attention).append(" - ").append(clearanceSummary(clearance)).append("\n");
                out.append("Alignment: ").append(rotation.attention).append(" - ").append(rotationSummary(rotation)).append("\n");
            }else out.append("12 marker: UNASSESSABLE - local minute-track/triangle landmarks were not verified; no pass is inferred.\n");
            out.append("Perspective: ").append(pose.label).append(" - ").append(pose.reason).append(".\n");

            out.append("\nDiagnostics\n");
            out.append(String.format(Locale.US,"%s: centre %.1f, %.1f; radius %.1f px; quality %.2f.\n",
                    manual!=null?"Hand-aligned dial (12/6 dial-edge taps)":"Wide-scale GMT dial seed",seedX,seedY,seedR,q));
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
                if(twelve.stabilityRun)
                    out.append(Double.isFinite(twelve.gapMax)
                            ?String.format(Locale.US,"12 resize check (94%%, 88%%): gap %.3f to %.3f (%.1f px), rotation %+.2f° to %+.2f°, %s edge; gap %s, rotation %s.\n",
                                    twelve.gapMin,twelve.gapMax,(twelve.gapMax-twelve.gapMin)*twelve.triangleWidthPx,twelve.rotMin,twelve.rotMax,
                                    twelve.stabilitySameEdge?"same":"different",gapUnstable?"UNSTABLE":"stable",rotUnstable?"UNSTABLE":"stable")
                            :"12 resize check (94%, 88%): the marker was not found again at another scale; readings UNSTABLE.\n");
            }

            if(!stableFrame&&twelve.valid)
                out.append("Recommended action: use a squarer or clearer photo before clearing fine 12-marker geometry; directional rehaut evidence may still be informative.\n");
            else if(twelve.valid&&(rotation.attention==GmtHumanQcMath.Attention.CHECK||rotation.attention==GmtHumanQcMath.Attention.STRONG
                    ||clearance.attention==GmtHumanQcMath.Attention.CHECK||clearance.attention==GmtHumanQcMath.Attention.STRONG))
                out.append("Recommended action: inspect the highlighted 12-marker relationship closely.\n");
            else if(!twelve.valid)out.append("Recommended action: inspect manually or use a clearer/original image; unresolved local landmarks are not a pass.\n");
            else if(pose.label==GmtHumanQcMath.PoseLabel.RETAKE)out.append("Recommended action: retake more square-on before relying on fine spacing magnitude; visible one-sided evidence remains highlighted.\n");
            else out.append("Recommended action: no human-attention condition was resolved in the local 12-marker relationships.\n");

            out.append("\nHUMAN 6-MARKER QC\n");
            if(six.valid){
                out.append("6 baton: ").append(sixDecision.attention).append(" - ").append(sixDecision.reason).append("\n");
                out.append(String.format(Locale.US,"6 geometry: centring %+.3f of baton width (+ = towards 29 tick, viewer's right); rotation %+.2f° (+ = clockwise); gap to 29-31 tick line %.3f of width; baton %.0f px wide; %s.\n",
                        six.centring,six.rotationDeg,six.gap,six.widthPx,six.stable?"outer edges fitted":"low confidence"));
            }else out.append("6 baton: UNASSESSABLE - ").append(six.reason).append("\n");

            GmtHumanSummary.Input sum=new GmtHumanSummary.Input();
            sum.sixValid=six.valid;sum.sixAttention=sixDecision.attention;sum.sixTooSmall=sixDecision.tooSmall;sum.handAtSix=handAtSix;
            sum.sixStable=six.stable;sum.sixCentring=six.centring;sum.sixRotationDeg=six.rotationDeg;sum.sixGap=six.gap;
            sum.sixOffCentre=sixDecision.offCentre;sum.sixRotated=sixDecision.rotated;sum.sixWidthPx=six.widthPx;
            sum.pose=pose.label;sum.twelveValid=twelve.valid;sum.stableFrame=stableFrame;
            sum.gapUnstable=gapUnstable;sum.rotUnstable=rotUnstable;sum.gapMin=twelve.gapMin;sum.gapMax=twelve.gapMax;sum.rotMin=twelve.rotMin;sum.rotMax=twelve.rotMax;
            sum.stabilityRun=twelve.stabilityRun;sum.stabilitySameEdge=twelve.stabilitySameEdge;
            sum.stabilityGapSpread=twelve.stabilityGapSpread;sum.stabilityRotSpreadDeg=twelve.stabilityRotSpreadDeg;
            sum.gap=clearance.attention;sum.gapTrend=clearance.trend;
            sum.observedGap=twelve.valid?twelve.topClearance:Double.NaN;
            sum.gapResolutionLimited=gapResolutionLimited;
            sum.tooSmall=tooSmall;sum.handAtTwelve=handAtTwelve;sum.trianglePx=twelve.valid?twelve.triangleWidthPx:Double.NaN;
            sum.gapPx=twelve.valid&&twelve.triangleWidthPx>0?twelve.topClearance*twelve.triangleWidthPx:Double.NaN;
            sum.pxPerGap=twelve.valid&&twelve.triangleWidthPx>0?1.0/twelve.triangleWidthPx:Double.NaN;
            sum.alignment=rotation.attention;
            sum.rotationDeg=twelve.valid?twelve.wholeAxisErrorDeg:Double.NaN;
            sum.baseTiltDeg=twelve.valid?twelve.topEdgeErrorDeg:Double.NaN;
            sum.spacing59=twelve.valid?twelve.leftClearance:Double.NaN;
            sum.spacing01=twelve.valid?twelve.rightClearance:Double.NaN;
            Result res=new Result(out.toString(),pose.label,rotation.attention,clearance.attention,
                    stableFrame?twelve.trackRollClockDeg:Double.NaN,stableFrame,sum);
            res.six=six;
            MeasuredOverlayRenderer.Drawing dr=res.drawing;
            if(edge!=null){dr.dialCx=edge.cx;dr.dialCy=edge.cy;dr.dialA=edge.axisA;dr.dialB=edge.axisB;dr.dialAngleDeg=edge.angleDeg;}
            else{dr.dialCx=cx;dr.dialCy=cy;dr.dialA=r;dr.dialB=r;}
            if(twelve.valid&&twelve.geometry!=null){
                dr.twelve=twelve.geometry;dr.gap=clearance.attention;dr.alignment=rotation.attention;
                dr.gapValue=twelve.topClearance;dr.spacing59=twelve.leftClearance;dr.spacing01=twelve.rightClearance;
            }
            if(six.valid){
                dr.six=six.geometry;dr.sixAttention=sixDecision.attention;dr.sixCentring=six.centring;
                dr.sixNotJudged=sixDecision.tooSmall?"6 baton too small":handAtSix?"a hand is at 6":null;
            }
            dr.notJudged=tooSmall?"12 triangle too small in this photo"
                    :handAtTwelve?"a hand is at 12"
                    :!twelve.valid?"12 marker not found"
                    :null;
            return res;
        }catch(Throwable t){return unavailable("human GMT QC failed closed: "+t.getClass().getSimpleName());}
        finally{src.release();}
    }

    private static String clearanceSummary(GmtHumanQcMath.ClearanceDecision c){
        if(c.attention==GmtHumanQcMath.Attention.STRONG&&c.trend==GmtHumanQcMath.GapTrend.INFLATED)return "gap is present but slightly small, and the photo angle is helping it look larger; inspect closely";
        if(c.attention==GmtHumanQcMath.Attention.STRONG)return "clearly small clearance is indicated; inspect closely";
        if(c.attention==GmtHumanQcMath.Attention.CHECK&&c.trend==GmtHumanQcMath.GapTrend.COMPRESSED)return "gap looks slightly small, but perspective may be making it look worse; inspect or compare with a squarer photo";
        if(c.attention==GmtHumanQcMath.Attention.CHECK)return "gap is present but appears slightly smaller than expected; inspect closely";
        if(c.attention==GmtHumanQcMath.Attention.CLEAR)return "no low-clearance issue is resolved; a slightly large gap is not treated as a defect";
        if(c.reason!=null&&c.reason.contains("too close to call"))return "too close to call at this resolution; take a closer photo";
        return "clearance could not be assessed reliably";
    }
    private static String rotationSummary(GmtHumanQcMath.RotationDecision r){
        if(r.attention==GmtHumanQcMath.Attention.STRONG)return "visible marker rotation/side-spacing skew is strongly indicated";
        if(r.attention==GmtHumanQcMath.Attention.CHECK)return "possible visible rotation or unequal 59/01 spacing; inspect closely";
        if(r.attention==GmtHumanQcMath.Attention.CLEAR)return "no rotation is resolved strongly enough to be visible at this image scale";
        return "alignment could not be assessed reliably";
    }
    static DialEdgeEllipseFit.Intensity intensityOf(Mat gray){
        final int w=gray.cols(),h=gray.rows();
        final byte[] px=new byte[w*h];
        gray.get(0,0,px);
        return (x,y)->{
            int x0=(int)Math.floor(x),y0=(int)Math.floor(y);
            if(x0<0||y0<0||x0+1>=w||y0+1>=h)return 0.0;
            double fx=x-x0,fy=y-y0;int i=y0*w+x0;
            double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+w]&0xff,d=px[i+w+1]&0xff;
            return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;
        };
    }

    private static Result unavailable(String reason){
        return new Result("\n\nHUMAN 12-MARKER QC\nUNASSESSABLE - "+reason+". No pass is inferred from missing evidence.\n",
                GmtHumanQcMath.PoseLabel.UNASSESSABLE,GmtHumanQcMath.Attention.UNASSESSABLE,
                GmtHumanQcMath.Attention.UNASSESSABLE,Double.NaN,false);
    }
    private static double axisDisagreement(double a,double b){double d=Math.abs((a-b)%180.0);if(d>90)d=180-d;return d;}
    private GmtHumanQcAnalyzerV2(){}
}
