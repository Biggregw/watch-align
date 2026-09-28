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
    /** Largest tick-chord vs dial-centre axis difference trusted for rotation (alpha58). */
    static final double MAX_AXIS_REFERENCE_DISAGREEMENT_DEG = 1.5;
    static final class Result {
        final String report;
        final GmtHumanQcMath.PoseLabel poseLabel;
        final GmtHumanQcMath.Attention rotationAttention;
        final GmtHumanQcMath.Attention clearanceAttention;
        final double localTrackRollDeg;
        final boolean localFrameValid;
        final GmtHumanSummary.Input summary;
        MeasuredOverlayRenderer.Drawing drawing=new MeasuredOverlayRenderer.Drawing();
        GmtSixLandmarkAnalyzer.Result six,nine;
        java.util.List<GmtRoundMarkerAnalyzer.Marker> round=new java.util.ArrayList<>();
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
            GmtHumanQcMath.GapTrend rehautTrend=sectors.gapTrendAt12();
            // Resize check on the photo-angle rating and the gap-direction cue (alpha60). Both
            // come from how wide the rehaut ring looks around the dial, and on a borderline photo
            // a sub-grey-level loading difference flipped the rating between "slight angle" and
            // "too angled" (user's ARF Pepsi photo: 1 of 6 near-identical loads). "Too angled"
            // withholds the 6 and 9 and reverses the gap wording, so the rating is taken as the
            // median over the photo and its 94% and 88% copies, and the gap direction is used only
            // when all three agree.
            String poseNote="";
            {
                GmtHumanQcMath.PoseLabel[] labels={pose.label,null,null};
                GmtHumanQcMath.GapTrend[] trends={rehautTrend,null,null};
                double[] sc=GmtTwelveLandmarkAnalyzer.STABILITY_SCALES;
                for(int k=0;k<sc.length;k++){
                    Mat m=new Mat();
                    try{
                        Imgproc.resize(src,m,new org.opencv.core.Size(Math.round(src.cols()*sc[k]),Math.round(src.rows()*sc[k])),0,0,Imgproc.INTER_LINEAR);
                        PoseAt pq=poseAt(m,cx*sc[k],cy*sc[k],r*sc[k],poseRoll);
                        labels[k+1]=pq.pose.label;trends[k+1]=pq.trend;
                    }finally{m.release();}
                }
                GmtHumanQcMath.PoseLabel med=medianPose(labels);
                if(med!=pose.label){
                    poseNote=String.format(Locale.US," Angle rating across the resize check: %s / %s / %s; using %s.",labels[0],labels[1],labels[2],med);
                    pose=new GmtHumanQcMath.PoseDecision(med,pose.reason+" (rating differed between the photo and its resized copies; the median is used)");
                }
                if(!(trends[0]==trends[1]&&trends[1]==trends[2])&&rehautTrend!=GmtHumanQcMath.GapTrend.UNKNOWN){
                    poseNote+=String.format(Locale.US," Gap direction cue across the resize check: %s / %s / %s; not used.",trends[0],trends[1],trends[2]);
                    rehautTrend=GmtHumanQcMath.GapTrend.UNKNOWN;
                }
            }
            GmtHumanQcMath.RotationDecision rotation;
            GmtHumanQcMath.ClearanceDecision clearance;
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
            // Axis cross-check (alpha58): rotation is measured square to the 59-01 tick chord. When
            // that disagrees with the dial-centre line by more than MAX_AXIS_REFERENCE_DISAGREEMENT
            // one of the two is wrong (a tick end or the centre misplaced), so no rotation verdict.
            boolean axisConflict=twelve.valid&&Double.isFinite(twelve.axisReferenceDisagreementDeg)
                    &&twelve.axisReferenceDisagreementDeg>MAX_AXIS_REFERENCE_DISAGREEMENT_DEG;
            if(axisConflict)
                rotation=new GmtHumanQcMath.RotationDecision(GmtHumanQcMath.Attention.UNASSESSABLE,rotation.axisErrorDeg,rotation.baseErrorDeg,rotation.sideAsymmetry,
                        rotation.visibleRisePx,false,false,String.format(Locale.US,
                        "the minute-track ticks and the dial centre disagree by %.1f° about where 12 points, so rotation can't be measured on this photo",
                        twelve.axisReferenceDisagreementDeg));
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

            // Off-centre at 12 (alpha57). Uneven 59/01 spacing only ever corroborated a rotation,
            // so a triangle shifted sideways with no rotation was reported "straight and
            // centred" (user photo: spacing 0.084 vs 0.156). Flag it on its own when it is well
            // outside the genuine spread, visible in pixels and the same at every resize scale.
            if(twelve.valid&&!recovered&&rotation.attention==GmtHumanQcMath.Attention.CLEAR){
                GmtHumanQcMath.Attention oc=GmtHumanQcMath.assessOffCentre(twelve.sideAsymmetry,twelve.asymMin,twelve.asymMax,
                        twelve.triangleWidthPx,pose.label,stableFrame);
                if(oc==GmtHumanQcMath.Attention.CHECK||oc==GmtHumanQcMath.Attention.STRONG)
                    rotation=new GmtHumanQcMath.RotationDecision(oc,rotation.axisErrorDeg,rotation.baseErrorDeg,rotation.sideAsymmetry,
                            rotation.visibleRisePx,rotation.baseCorroborates,true,
                            String.format(Locale.US,"no rotation, but the triangle sits off-centre: 59-side spacing %.3f vs 01-side %.3f (difference %.3f, %.1f px); genuine photos so far differ by at most 0.04",
                                    twelve.leftClearance,twelve.rightClearance,Math.abs(twelve.sideAsymmetry),Math.abs(twelve.sideAsymmetry)*twelve.triangleWidthPx));
            }

            // Size and hand gates (alpha52). Field set: every wrong 12-marker result had a
            // triangle under ~35 px or a hand beside the triangle.
            boolean tooSmall=false,handAtTwelve=false;
            if(twelve.valid&&Double.isFinite(twelve.triangleWidthPx)&&twelve.triangleWidthPx<MIN_TRIANGLE_PX){
                tooSmall=true;
                String why=String.format(Locale.US,"the 12 triangle is only %.0f px wide in this photo (minimum %.0f); too small to measure",Math.floor(twelve.triangleWidthPx),MIN_TRIANGLE_PX);
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

            // Batons at 6 (alpha55) and 9 (alpha59): measured on the same fitted dial, gated the same way.
            BatonOutcome sixOut=measureBaton(GmtSixLandmarkAnalyzer.Position.SIX,src,cx,cy,r,twelve,pose.label);
            BatonOutcome nineOut=measureBaton(GmtSixLandmarkAnalyzer.Position.NINE,src,cx,cy,r,twelve,pose.label);
            // Round markers (alpha61), oriented by the 12's 60 tick.
            GmtRoundMarkerAnalyzer.DialFrame dialFrame=edge!=null?new GmtRoundMarkerAnalyzer.DialFrame(edge.cx,edge.cy,edge.axisA,edge.axisB,edge.angleDeg)
                    :GmtRoundMarkerAnalyzer.DialFrame.circle(cx,cy,r);
            double[] tick60=twelve.valid&&twelve.geometry!=null?twelve.geometry.tick60:null;
            java.util.List<GmtRoundMarkerAnalyzer.Marker> round=GmtRoundMarkerAnalyzer.analyse(src,dialFrame,tick60);
            GmtRoundMarkerAnalyzer.measureStability(src,dialFrame,tick60,round);
            judgeRound(round,src,cx,cy,r,pose.label);
            GmtSixLandmarkAnalyzer.Result six=sixOut.result;
            GmtHumanQcMath.SixDecision sixDecision=sixOut.decision;
            boolean sixUnstable=sixOut.unstable,handAtSix=sixOut.hand;

            StringBuilder out=new StringBuilder("\n\nHUMAN 12-MARKER QC\n");
            if(twelve.valid){
                out.append("12 marker: ").append(clearance.attention).append(" - ").append(clearanceSummary(clearance)).append("\n");
                out.append("Alignment: ").append(rotation.attention).append(" - ").append(rotationSummary(rotation)).append("\n");
            }else out.append("12 marker: UNASSESSABLE - local minute-track/triangle landmarks were not verified; no pass is inferred.\n");
            out.append("Perspective: ").append(pose.label).append(" - ").append(pose.reason).append(".").append(poseNote).append("\n");

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

            appendBatonReport(out,sixOut);
            appendBatonReport(out,nineOut);
            appendRoundReport(out,round);

            GmtHumanSummary.Input sum=new GmtHumanSummary.Input();
            sum.sixValid=six.valid;sum.sixAttention=sixDecision.attention;sum.sixTooSmall=sixDecision.tooSmall;sum.handAtSix=handAtSix;
            sum.sixStable=six.stable;sum.sixLowReason=six.lowReason;sum.sixUnstable=sixUnstable;sum.sixCentringMin=six.centringMin;sum.sixCentringMax=six.centringMax;sum.sixRotMin=six.rotMin;sum.sixRotMax=six.rotMax;sum.sixCentring=six.centring;sum.sixRotationDeg=six.rotationDeg;sum.sixGap=six.gap;
            sum.sixOffCentre=sixDecision.offCentre;sum.sixRotated=sixDecision.rotated;sum.sixWidthPx=six.widthPx;
            sum.nine=nineOut.summary();
            sum.round=round;
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
            res.six=six;res.nine=nineOut.result;res.round=round;
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
            if(nineOut.result.valid){
                dr.nine=nineOut.result.geometry;dr.nineAttention=nineOut.decision.attention;dr.nineCentring=nineOut.result.centring;
                dr.nineNotJudged=nineOut.decision.tooSmall?"9 baton too small":nineOut.hand?"a hand is at 9":null;
            }
            dr.round=round;
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
    /** Dial centre and radius {x, y, r} on a bitmap (seed, then dial-edge fit), or null. */
    static double[] locateDial(Bitmap watch){
        Mat src=new Mat();
        try{
            Utils.bitmapToMat(watch,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);
            GmtDialSeedAnalyzer.Result dial=GmtDialSeedAnalyzer.analyse(src);
            if(!dial.valid||dial.quality<0.45||!(dial.r>20))return null;
            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(src,dial.x,dial.y,dial.r);
            return edge!=null?new double[]{edge.cx,edge.cy,edge.meanRadius()}:new double[]{dial.x,dial.y,dial.r};
        }catch(Throwable t){return null;}
        finally{src.release();}
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

    /** One baton's measurement, decision and gates (6 and 9). */
    static final class BatonOutcome {
        final GmtSixLandmarkAnalyzer.Position position;
        GmtSixLandmarkAnalyzer.Result result;
        GmtHumanQcMath.SixDecision decision;
        boolean unstable,hand;
        BatonOutcome(GmtSixLandmarkAnalyzer.Position p){position=p;}
        GmtHumanSummary.Baton summary(){
            GmtHumanSummary.Baton b=new GmtHumanSummary.Baton(position.label,position.before,position.after);
            b.valid=result.valid;b.attention=decision.attention;b.tooSmall=decision.tooSmall;b.hand=hand;
            b.stable=result.stable;b.lowReason=result.lowReason;b.unstable=unstable;
            b.centringMin=result.centringMin;b.centringMax=result.centringMax;b.rotMin=result.rotMin;b.rotMax=result.rotMax;b.centring=result.centring;
            b.rotationDeg=result.rotationDeg;b.gap=result.gap;b.widthPx=result.widthPx;
            b.offCentre=decision.offCentre;b.rotated=decision.rotated;
            return b;
        }
    }

    /**
     * Finds and judges one baton. Gates, in order: orientation against the 12 (the marker
     * must sit where that baton belongs, or the photo is turned), the resize check, then
     * the hand check (wedge and thin line).
     */
    private static BatonOutcome measureBaton(GmtSixLandmarkAnalyzer.Position pos,Mat src,double cx,double cy,double r,
                                             GmtTwelveLandmarkAnalyzer.Result twelve,GmtHumanQcMath.PoseLabel pose){
        BatonOutcome o=new BatonOutcome(pos);
        GmtSixLandmarkAnalyzer.Result b=GmtSixLandmarkAnalyzer.analyse(src,cx,cy,r,pos);
        String L=pos.label;
        if(b.valid){
            if(twelve.valid&&twelve.geometry!=null){
                double a12=Math.atan2(twelve.geometry.tick60[1]-cy,twelve.geometry.tick60[0]-cx);
                double[] m={(b.geometry.tickAfter[0]+b.geometry.tickBefore[0])/2,(b.geometry.tickAfter[1]+b.geometry.tickBefore[1])/2};
                double am=Math.atan2(m[1]-cy,m[0]-cx);
                double d=Math.toDegrees(am-a12)-pos.angleFromTwelveDeg;while(d>180)d-=360;while(d<=-180)d+=360;
                if(Math.abs(d)>8.0)b=new GmtSixLandmarkAnalyzer.Result("the marker found at "+L+" is not where the "+L+" baton should be relative to the 12 marker (photo turned?)");
            }else b=b.lowConfidence("the 12 marker was not found, so the dial orientation is unknown");
            b.position=pos;
        }
        if(b.valid&&b.stable)GmtSixLandmarkAnalyzer.measureStability(src,cx,cy,r,b);
        GmtHumanQcMath.SixDecision dec=b.valid
                ?GmtHumanQcMath.assessSix(b.centring,b.rotationDeg,b.widthPx,b.lengthPx,pose,b.stable)
                :new GmtHumanQcMath.SixDecision(GmtHumanQcMath.Attention.UNASSESSABLE,false,false,false,b.reason);
        if(b.valid&&dec.reason!=null)dec=new GmtHumanQcMath.SixDecision(dec.attention,dec.offCentre,dec.rotated,dec.tooSmall,dec.reason.replace("6 ",L+" "));
        boolean unstable=b.valid&&b.stable&&!b.resampleStable();
        if(unstable&&!dec.tooSmall){
            boolean concern=dec.attention==GmtHumanQcMath.Attention.CHECK||dec.attention==GmtHumanQcMath.Attention.STRONG;
            boolean agreed=Double.isFinite(b.centringMax)&&(
                    (dec.offCentre&&Math.min(Math.abs(b.centringMin),Math.abs(b.centringMax))>=GmtHumanQcMath.SIX_CENTRING_CHECK&&Math.signum(b.centringMin)==Math.signum(b.centringMax))
                    ||(dec.rotated&&Math.min(Math.abs(b.rotMin),Math.abs(b.rotMax))>=GmtHumanQcMath.SIX_ROTATION_CHECK_DEG&&Math.signum(b.rotMin)==Math.signum(b.rotMax)));
            String moved=Double.isFinite(b.centringMax)
                    ?String.format(Locale.US,"the %s reading moves when the photo is reduced by 6%% and 12%% (offset %+.2f to %+.2f, rotation %+.1f° to %+.1f°)",
                            L,b.centringMin,b.centringMax,b.rotMin,b.rotMax)
                    :"the "+L+" baton is not found again when the photo is reduced by 6% or 12%";
            dec=new GmtHumanQcMath.SixDecision(concern&&agreed?GmtHumanQcMath.Attention.CHECK:GmtHumanQcMath.Attention.UNASSESSABLE,
                    concern&&agreed&&dec.offCentre,concern&&agreed&&dec.rotated,false,
                    (concern&&agreed?"agreed at every scale, but ":"")+moved);
        }
        boolean hand=false;
        if(b.valid&&!dec.tooSmall){
            Mat g8=new Mat();
            try{
                Imgproc.cvtColor(src,g8,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(g8,g8,new org.opencv.core.Size(5,5),1.2);
                GmtSixLandmarkAnalyzer.Geometry sg=b.geometry;
                HandIntrusion.Result hi=HandIntrusion.measure(intensityOf(g8),g8.cols(),g8.rows(),cx,cy,r,sg.polygon(),sg.tickAfter,sg.tickCentre,sg.tickBefore);
                if(hi.present){
                    hand=true;
                    dec=new GmtHumanQcMath.SixDecision(GmtHumanQcMath.Attention.UNASSESSABLE,false,false,false,
                            "a hand is next to the "+L+" baton; it corrupts the outline and tick detection");
                }
            }finally{g8.release();}
        }
        o.result=b;o.decision=dec;o.unstable=unstable;o.hand=hand;
        return o;
    }

    private static void appendBatonReport(StringBuilder out,BatonOutcome o){
        GmtSixLandmarkAnalyzer.Result b=o.result;GmtSixLandmarkAnalyzer.Position p=o.position;String L=p.label;
        out.append("\nHUMAN ").append(L).append("-MARKER QC\n");
        if(!b.valid){out.append(L).append(" baton: UNASSESSABLE - ").append(b.reason).append("\n");return;}
        out.append(L).append(" baton: ").append(o.decision.attention).append(" - ").append(o.decision.reason).append("\n");
        out.append(String.format(Locale.US,"%s geometry: centring %+.3f of baton width (+ = towards the %d tick); rotation %+.2f° (+ = clockwise); gap to %d-%d tick line %.3f of width; baton %.0f px wide; %s.\n",
                L,b.centring,p.before,b.rotationDeg,p.before,p.after,b.gap,b.widthPx,b.stable?"outer edges fitted":"low confidence: "+b.lowReason));
        out.append(String.format(Locale.US,"%s fit detail: long edges %.1f° from parallel; ticks score %.1f, pitch %.2f°, %.0f inferred.\n",
                L,b.parallelDeg,b.tickScore,b.tickPitchDeg,b.ticksInferred));
        if(b.stabilityRun)out.append(Double.isFinite(b.centringMax)
                ?String.format(Locale.US,"%s resize check (94%%, 88%%): offset %+.3f to %+.3f, rotation %+.2f° to %+.2f°, %s edge; %s.\n",
                        L,b.centringMin,b.centringMax,b.rotMin,b.rotMax,b.stabilitySameEdge?"same":"different",o.unstable?"UNSTABLE":"stable")
                :L+" resize check (94%, 88%): the baton was not found again at another scale; UNSTABLE.\n");
    }

    /**
     * Verdict for each round marker (alpha61): size and offset levels, then the resize check,
     * then the hand check (wedge and thin line), as for the batons.
     */
    static void judgeRound(java.util.List<GmtRoundMarkerAnalyzer.Marker> round,Mat src,double cx,double cy,double r,GmtHumanQcMath.PoseLabel pose){
        Mat g8=new Mat();
        try{
            Imgproc.cvtColor(src,g8,Imgproc.COLOR_BGR2GRAY);Imgproc.GaussianBlur(g8,g8,new org.opencv.core.Size(5,5),1.2);
            DialEdgeEllipseFit.Intensity img=intensityOf(g8);
            for(GmtRoundMarkerAnalyzer.Marker m:round){
                if(!m.found){m.attention=GmtHumanQcMath.Attention.UNASSESSABLE;m.note=m.reason;continue;}
                if(m.stabilityRun&&!m.stabilitySameEdge)m.sizeRatio=Double.NaN;   // edge changed with scale: size not compared
                GmtHumanQcMath.RoundDecision d=GmtHumanQcMath.assessRound(m.offset,m.diameterPx(),m.sizeRatio,pose,m.stable);
                m.tooSmall=d.tooSmall;
                GmtHumanQcMath.Attention a=d.attention;String note=d.reason;boolean off=d.offCentre,size=d.sizeOdd;
                if(!d.tooSmall&&!m.resampleStable()){
                    m.unstable=true;
                    boolean concern=a==GmtHumanQcMath.Attention.CHECK||a==GmtHumanQcMath.Attention.STRONG;
                    boolean agreed=off&&Double.isFinite(m.offMax)&&Math.min(Math.abs(m.offMin),Math.abs(m.offMax))>=GmtHumanQcMath.ROUND_OFFSET_CHECK
                            &&Math.signum(m.offMin)==Math.signum(m.offMax);
                    String moved=Double.isFinite(m.offMax)
                            ?String.format(Locale.US,"the reading moves when the photo is reduced by 6%% and 12%% (offset %+.2f to %+.2f)",m.offMin,m.offMax)
                            :"the marker is not found the same way when the photo is reduced by 6% or 12%";
                    a=concern&&agreed?GmtHumanQcMath.Attention.CHECK:GmtHumanQcMath.Attention.UNASSESSABLE;
                    off=concern&&agreed;size=false;
                    note=(concern&&agreed?"off-centre at every scale, but ":"")+moved;
                }
                {
                    // Checked even when the marker reads too small: a hand over it is the likelier
                    // reason for a small outline, and it is the more useful thing to say.
                    // A local ring rather than the 12's wide wedge: a wedge ±14° wide around a round
                    // marker reaches a marker's width either side and caught hands that were near
                    // but not over it (3KSuGhC image_02: 4 of 8 markers).
                    double[] beside={0};
                    m.ringBright=HandIntrusion.ringBrightFraction(img,g8.cols(),g8.rows(),m.x,m.y,m.radiusPx,m.expectedRadiusPx*1.05,m.tickBefore,m.tickAfter,cx,cy,beside);
                    m.coloured=GmtRoundMarkerAnalyzer.colouredFraction(src,m,cx,cy);
                    // A hand passing beside the marker, clear of its surround, doesn't touch the
                    // outline, so it isn't a reason to withhold (the outline gates still apply).
                    m.handBeside=beside[0]>0;
                    if((m.ringBright>HandIntrusion.MAX_RING_BRIGHT_FRACTION&&!m.handBeside)||m.coloured>GmtRoundMarkerAnalyzer.MAX_COLOURED_FRACTION){
                        m.hand=true;m.tooSmall=false;a=GmtHumanQcMath.Attention.UNASSESSABLE;off=size=false;note="a hand is over or next to it";
                    }
                }
                m.attention=a;m.note=note;m.offCentre=off;m.sizeOdd=size;
            }
        }finally{g8.release();}
    }

    private static void appendRoundReport(StringBuilder out,java.util.List<GmtRoundMarkerAnalyzer.Marker> round){
        out.append("\nHUMAN ROUND-MARKER QC\n");
        out.append("Offset: sideways from midway between the minute ticks either side, fraction of the marker diameter, + = clockwise. Inset: centre to the tick line over the tick spacing. Size: against the median round marker on this dial.\n");
        for(GmtRoundMarkerAnalyzer.Marker m:round){
            String t=String.format(Locale.US,"%d (%02d/%02d/%02d)",m.hour,m.before(),m.minute(),m.after());
            if(!m.found){out.append(t).append(": UNASSESSABLE - ").append(m.reason).append("\n");continue;}
            out.append(t).append(": ").append(m.attention).append(" - ").append(m.note).append("\n");
            out.append(String.format(Locale.US,"   offset %+.3f (centre tick %+.3f), inset %.3f, edge gap %.3f, diameter %.1f px, size %.3f; outline %.0f%% off-circle, contrast %.0f, ticks score %.0f pitch %.2f°, hand ring %.3f, colour %.3f%s%s.\n",
                    m.offset,m.offsetFromCentreTick,m.inset,m.gap,m.diameterPx(),m.sizeRatio,100*m.rejectFraction,m.contrast,m.tickScore,m.tickPitchDeg,m.ringBright,m.coloured,
                    m.stable?"":"; low confidence: "+m.lowReason,
                    m.stabilityRun?(Double.isFinite(m.offMax)?String.format(Locale.US,"; resize check offset %+.3f to %+.3f, %s edge, %s",m.offMin,m.offMax,m.stabilitySameEdge?"same":"different",m.unstable?"UNSTABLE":"stable"):"; resize check: not found again, UNSTABLE"):""));
        }
    }

    /** Photo-angle rating and gap-direction cue at one scale (used by the resize check). */
    static final class PoseAt {GmtHumanQcMath.PoseDecision pose;GmtHumanQcMath.GapTrend trend;}

    static PoseAt poseAt(Mat src,double cx,double cy,double r,double poseRoll){
        GmtRehautPoseAnalyzer.Result rehaut=GmtRehautPoseAnalyzer.analyse(src,cx,cy,r,poseRoll);
        GmtRehautSectorAnalyzer.Result sectors;
        if(rehaut.valid){
            sectors=GmtRehautSectorAnalyzer.analyse(src,cx,cy,rehaut.innerSeedPx,rehaut.outerSeedPx,poseRoll);
            if(!sectors.valid)sectors=GmtRehautSectorAutoAnalyzer.analyse(src,cx,cy,r,poseRoll);
        }else sectors=GmtRehautSectorAutoAnalyzer.analyse(src,cx,cy,r,poseRoll);
        GmtEllipsePoseAnalyzer.Result ellipse=GmtEllipsePoseAnalyzer.analyse(src,cx,cy,r);
        double disagreement=Double.NaN;
        if(rehaut.valid&&ellipse.valid&&rehaut.firstHarmonicStrength>=0.08)
            disagreement=axisDisagreement(rehaut.widestClockDeg,ellipse.minorAxisClockDeg);
        PoseAt o=new PoseAt();
        o.pose=GmtHumanPosePolicy.classify(rehaut,sectors,ellipse,disagreement);
        o.trend=sectors.gapTrendAt12();
        return o;
    }

    /**
     * Median of three angle ratings, ordered GOOD < CORRECTABLE < RETAKE. UNASSESSABLE ratings
     * are left out when at least two others exist; with fewer the first rating stands.
     */
    static GmtHumanQcMath.PoseLabel medianPose(GmtHumanQcMath.PoseLabel[] labels){
        java.util.List<GmtHumanQcMath.PoseLabel> v=new java.util.ArrayList<>();
        for(GmtHumanQcMath.PoseLabel l:labels)if(l!=null&&l!=GmtHumanQcMath.PoseLabel.UNASSESSABLE)v.add(l);
        if(v.size()<2)return labels[0];
        java.util.Collections.sort(v);
        if(v.size()==2)return v.get(0)==v.get(1)?v.get(0):GmtHumanQcMath.PoseLabel.CORRECTABLE;
        return v.get(1);
    }
}
