package com.watchalign.mobile;

/**
 * Pure decision math for the human-style GMT 12 o'clock QC layer.
 *
 * This is deliberately an attention system, not a Rolex manufacturing tolerance.
 * The app should surface geometry that a careful human could plausibly notice,
 * while refusing to turn poor pose or unstable landmarks into a confident verdict.
 */
final class GmtHumanQcMath {
    enum PoseLabel { GOOD, CORRECTABLE, RETAKE, UNASSESSABLE }
    enum Attention { CLEAR, CHECK, STRONG, UNASSESSABLE }
    enum GapTrend { INFLATED, COMPRESSED, NEUTRAL, UNKNOWN }

    static final class PoseDecision {
        final PoseLabel label;
        final String reason;
        PoseDecision(PoseLabel label, String reason) { this.label=label; this.reason=reason; }
    }

    static final class RotationDecision {
        final Attention attention;
        final double axisErrorDeg;
        final double baseErrorDeg;
        final double sideAsymmetry;
        final double visibleRisePx;
        final boolean baseCorroborates;
        final boolean spacingCorroborates;
        final String reason;
        RotationDecision(Attention a,double axis,double base,double side,double rise,
                         boolean baseOk,boolean spacingOk,String reason) {
            this.attention=a; this.axisErrorDeg=axis; this.baseErrorDeg=base;
            this.sideAsymmetry=side; this.visibleRisePx=rise;
            this.baseCorroborates=baseOk; this.spacingCorroborates=spacingOk;
            this.reason=reason;
        }
    }

    static final class ClearanceDecision {
        final Attention attention;
        final GapTrend trend;
        final double observedGap;
        final double correctedEstimate;
        final double perspectiveScale;
        final String reason;
        ClearanceDecision(Attention a,GapTrend t,double observed,double corrected,double scale,String reason) {
            this.attention=a; this.trend=t; this.observedGap=observed;
            this.correctedEstimate=corrected; this.perspectiveScale=scale; this.reason=reason;
        }
    }

    // Provisional research attention boundary inherited from the validated GMT12
    // work. It is NOT a Rolex tolerance and is intentionally only used on the low
    // side. A slightly large gap is not treated as a defect by this layer.
    static final double LOW_CLEARANCE_ATTENTION = 0.129;

    static PoseDecision classifyPose(double minWidthOverMean,
                                     double edgeCoverage,
                                     double fitResidual,
                                     double harmonicStrength,
                                     double ellipseTiltDeg,
                                     double cueAxisDisagreementDeg) {
        if (!Double.isFinite(minWidthOverMean) || !Double.isFinite(edgeCoverage)
                || !Double.isFinite(fitResidual) || !Double.isFinite(harmonicStrength)) {
            return new PoseDecision(PoseLabel.UNASSESSABLE,"rehaut pose signal is incomplete");
        }
        if (edgeCoverage < 0.20) return new PoseDecision(PoseLabel.UNASSESSABLE,"insufficient rehaut edge coverage");
        if (fitResidual > 0.20) return new PoseDecision(PoseLabel.UNASSESSABLE,"rehaut fit residual is too large");
        if (Double.isFinite(cueAxisDisagreementDeg) && Double.isFinite(ellipseTiltDeg)
                && ellipseTiltDeg >= 8.0 && harmonicStrength >= 0.08 && cueAxisDisagreementDeg > 50.0) {
            return new PoseDecision(PoseLabel.UNASSESSABLE,
                    String.format(java.util.Locale.US,"rehaut and ellipse tilt axes disagree by %.1f°",cueAxisDisagreementDeg));
        }
        if (minWidthOverMean < 0.50) return new PoseDecision(PoseLabel.RETAKE,"one section of visible rehaut collapses below 50% of mean width");
        if (fitResidual > 0.16) return new PoseDecision(PoseLabel.RETAKE,"rehaut fit becomes unstable at this angle/lighting");
        if (Double.isFinite(ellipseTiltDeg) && ellipseTiltDeg >= 14.0)
            return new PoseDecision(PoseLabel.RETAKE,"dial ellipse implies severe obliqueness");
        if (minWidthOverMean < 0.80) return new PoseDecision(PoseLabel.CORRECTABLE,"moderate rehaut compression");
        if (harmonicStrength >= 0.15) return new PoseDecision(PoseLabel.CORRECTABLE,"moderate directional rehaut asymmetry");
        if (Double.isFinite(ellipseTiltDeg) && ellipseTiltDeg >= 8.0)
            return new PoseDecision(PoseLabel.CORRECTABLE,"moderate planar foreshortening");
        return new PoseDecision(PoseLabel.GOOD,"rehaut and planar cues are near frontal");
    }

    /**
     * Relative scale of a radial 12-o'clock gap divided by a tangential marker width.
     * axisRatio is minor/major ellipse axis. Angles use watch-clock convention:
     * 0°=12, 90°=3, increasing clockwise in the image.
     *
     * >1 means perspective makes normalized top clearance look larger than reality.
     * <1 means it makes the normalized clearance look smaller.
     */
    static double normalizedClearancePerspectiveScale(double axisRatio,
                                                      double minorAxisClockDeg,
                                                      double watchTwelveClockDeg) {
        if (!Double.isFinite(axisRatio) || axisRatio <= 0 || axisRatio > 1.0
                || !Double.isFinite(minorAxisClockDeg) || !Double.isFinite(watchTwelveClockDeg)) return Double.NaN;
        double radialScale = projectedScale(axisRatio, wrap90(watchTwelveClockDeg - minorAxisClockDeg));
        double tangentScale = projectedScale(axisRatio, wrap90((watchTwelveClockDeg + 90.0) - minorAxisClockDeg));
        if (!(radialScale > 0) || !(tangentScale > 0)) return Double.NaN;
        return radialScale / tangentScale;
    }

    private static double projectedScale(double q,double angleFromMinorDeg) {
        double d=Math.toRadians(angleFromMinorDeg);
        double c=Math.cos(d),s=Math.sin(d);
        return Math.sqrt(q*q*c*c+s*s);
    }

    static GapTrend gapTrend(double perspectiveScale) {
        if (!Double.isFinite(perspectiveScale) || perspectiveScale <= 0) return GapTrend.UNKNOWN;
        if (perspectiveScale > 1.01) return GapTrend.INFLATED;
        if (perspectiveScale < 0.99) return GapTrend.COMPRESSED;
        return GapTrend.NEUTRAL;
    }

    static ClearanceDecision assessLowClearance(double observedGap,double perspectiveScale,PoseLabel pose) {
        if (!Double.isFinite(observedGap))
            return new ClearanceDecision(Attention.UNASSESSABLE,GapTrend.UNKNOWN,observedGap,Double.NaN,perspectiveScale,"12 clearance measurement is missing");

        GapTrend trend=gapTrend(perspectiveScale);
        double corrected=(Double.isFinite(perspectiveScale)&&perspectiveScale>0)?observedGap/perspectiveScale:Double.NaN;

        // A zero/touching observation cannot be rescued by any finite perspective scale.
        if (observedGap <= 0.0) {
            return new ClearanceDecision(Attention.STRONG,trend,observedGap,corrected,perspectiveScale,
                    "triangle-to-minute-track separation is zero/touching; perspective cannot create a real gap from zero");
        }

        boolean poorPose=pose==PoseLabel.RETAKE||pose==PoseLabel.UNASSESSABLE;
        if (poorPose) {
            if (observedGap < LOW_CLEARANCE_ATTENTION)
                return new ClearanceDecision(Attention.CHECK,trend,observedGap,corrected,perspectiveScale,
                        "gap looks small but photo pose is too poor for a precise verdict; inspect or retake");
            return new ClearanceDecision(Attention.UNASSESSABLE,trend,observedGap,corrected,perspectiveScale,
                    "photo pose is too poor to clear fine 12-marker spacing");
        }

        if (trend==GapTrend.INFLATED) {
            // This is the important one-sided human rule: perspective is helping the
            // apparent gap. If it still looks low, correction can only make it worse.
            if (observedGap < LOW_CLEARANCE_ATTENTION)
                return new ClearanceDecision(Attention.STRONG,trend,observedGap,corrected,perspectiveScale,
                        "observed gap is already low even though perspective inflates it; true gap can only be smaller");
            if (Double.isFinite(corrected) && corrected < LOW_CLEARANCE_ATTENTION)
                return new ClearanceDecision(Attention.CHECK,trend,observedGap,corrected,perspectiveScale,
                        "perspective appears to enlarge the visible gap enough that corrected clearance enters the low-attention region");
        } else if (trend==GapTrend.COMPRESSED) {
            if (observedGap < LOW_CLEARANCE_ATTENTION)
                return new ClearanceDecision(Attention.CHECK,trend,observedGap,corrected,perspectiveScale,
                        "observed gap is low but perspective compresses it; do not call a defect without correction/retake");
        } else if (observedGap < LOW_CLEARANCE_ATTENTION) {
            return new ClearanceDecision(Attention.CHECK,trend,observedGap,corrected,perspectiveScale,
                    "near-frontal observed gap is in the provisional low-clearance attention region");
        }

        return new ClearanceDecision(Attention.CLEAR,trend,observedGap,corrected,perspectiveScale,
                "no low-clearance attention condition is supported; large clearance is not graded as a defect");
    }

    /**
     * Sensitive human-review rotation rule. Whole-marker symmetry axis is primary;
     * top-edge angle and 59/01 side spacing are corroborating cues.
     */
    static RotationDecision assessRotation(double wholeAxisErrorDeg,
                                           double topEdgeErrorDeg,
                                           double sideClearanceAsymmetry,
                                           double markerWidthPx,
                                           PoseLabel pose,
                                           boolean detectorStable) {
        if (!Double.isFinite(wholeAxisErrorDeg) || !Double.isFinite(markerWidthPx) || markerWidthPx <= 0)
            return new RotationDecision(Attention.UNASSESSABLE,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,Double.NaN,false,false,"whole-marker orientation is not resolved");

        double rise=Math.abs(Math.tan(Math.toRadians(wholeAxisErrorDeg))*markerWidthPx);
        double a=Math.abs(wholeAxisErrorDeg);
        boolean axisVisible=a>=1.0 && rise>=0.55;
        boolean baseCorroborates=Double.isFinite(topEdgeErrorDeg)
                && Math.abs(topEdgeErrorDeg)>=0.75
                && Math.abs(wrap90(topEdgeErrorDeg-wholeAxisErrorDeg))<=1.75;
        boolean spacingCorroborates=Double.isFinite(sideClearanceAsymmetry)
                && Math.abs(sideClearanceAsymmetry)>=0.06;
        boolean spacingStrong=Double.isFinite(sideClearanceAsymmetry)
                && Math.abs(sideClearanceAsymmetry)>=0.12;

        boolean poorPose=pose==PoseLabel.RETAKE||pose==PoseLabel.UNASSESSABLE;
        if (!detectorStable) {
            if ((a>=3.0&&rise>=1.0)||spacingStrong)
                return new RotationDecision(Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                        "visible skew signal exists but landmarks are unstable; inspect rather than trust the angle");
            return new RotationDecision(Attention.UNASSESSABLE,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    "landmark stability is insufficient to clear a small rotation");
        }
        if (poorPose) {
            if ((a>=3.0&&rise>=1.0)||spacingStrong)
                return new RotationDecision(Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                        "apparent marker skew is visible but pose is too oblique for a precise angular verdict");
            return new RotationDecision(Attention.UNASSESSABLE,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    "pose is too oblique to silently clear a subtle rotation");
        }

        if (axisVisible && (baseCorroborates || spacingCorroborates)) {
            boolean strong=a>=3.0 || rise>=1.5 || spacingStrong;
            return new RotationDecision(strong?Attention.STRONG:Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    strong?"whole-marker rotation is visible and independently corroborated":"slight whole-marker rotation is plausibly visible on close inspection and is corroborated");
        }
        if (axisVisible) {
            return new RotationDecision(Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    "whole-marker rotation is plausibly visible on close inspection; highlight for human review");
        }
        if (spacingStrong) {
            return new RotationDecision(Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    "59/01 side spacing is visibly unequal even though the marker-axis angle is small; inspect centring/rotation");
        }
        return new RotationDecision(Attention.CLEAR,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                "no marker rotation is resolved strongly enough to be visible at this image scale");
    }

    static double wrap90(double deg) {
        double x=deg%180.0;
        if(x<=-90.0)x+=180.0;
        if(x>90.0)x-=180.0;
        return x;
    }

    private GmtHumanQcMath() {}
}
