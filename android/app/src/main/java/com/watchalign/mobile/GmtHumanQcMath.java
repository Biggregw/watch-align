package com.watchalign.mobile;

/**
 * Pure decision math for the human-style GMT 12 o'clock QC layer.
 *
 * This is deliberately an attention system, not a Rolex manufacturing tolerance or an
 * authenticity classifier. A reliable value already observed on a genuine watch is treated as
 * normal variation for replica QC, even when it is a small genuine imperfection. A warning begins
 * only beyond the supported genuine envelope and the existing visibility/stability gates.
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

    // Low-side gap boundary. Genuine stable GMT readings in the regression evidence reach down to
    // about 0.081, while the warning boundary remains 0.070. A large gap is not graded as a defect.
    static final double LOW_CLEARANCE_ATTENTION = 0.070;
    static final double MAX_PLAUSIBLE_ROTATION_DEG = 8.0;
    /** Genuine corroborated turns have reached about 1.7 deg; warnings begin at 2.0 deg. */
    static final double ROTATION_CHECK_DEG = 2.0;
    /** Genuine level-top skew has reached 2.2 deg; leave a small envelope guard before warning. */
    static final double SKEW_ONLY_MIN_DEG = 2.5;

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
            // Perspective is helping the apparent gap. If it still looks low, correction can only
            // make it worse.
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
                    "near-frontal observed gap is in the low-clearance attention region");
        }

        return new ClearanceDecision(Attention.CLEAR,trend,observedGap,corrected,perspectiveScale,
                "no low-clearance attention condition is supported; large clearance is not graded as a defect");
    }

    // ---- Off-centre 12 triangle ---------------------------------------------------------
    // Genuine stable spacing asymmetry has reached 0.080. CHECK remains at 0.10, beyond the
    // observed genuine envelope, with pixel minimums as an additional visibility guard.
    static final double OFF_CENTRE_CHECK = 0.10, OFF_CENTRE_STRONG = 0.15;
    static final double OFF_CENTRE_CHECK_PX = 2.0, OFF_CENTRE_STRONG_PX = 3.0;

    static Attention assessOffCentre(double asym,double asymMin,double asymMax,double widthPx,PoseLabel pose,boolean stable){
        if(!Double.isFinite(asym)||!(widthPx>0)||!stable)return Attention.UNASSESSABLE;
        if(pose==PoseLabel.RETAKE||pose==PoseLabel.UNASSESSABLE)return Attention.UNASSESSABLE;
        double lo=Double.isFinite(asymMin)?Math.min(Math.abs(asymMin),Math.abs(asymMax)):Math.abs(asym);
        boolean sameSide=!Double.isFinite(asymMin)||Math.signum(asymMin)==Math.signum(asymMax);
        double a=Math.abs(asym);
        if(sameSide&&a>=OFF_CENTRE_STRONG&&lo>=OFF_CENTRE_STRONG&&a*widthPx>=OFF_CENTRE_STRONG_PX)return Attention.STRONG;
        if(sameSide&&a>=OFF_CENTRE_CHECK&&lo>=OFF_CENTRE_CHECK&&a*widthPx>=OFF_CENTRE_CHECK_PX)return Attention.CHECK;
        if(a>=OFF_CENTRE_CHECK)return Attention.UNASSESSABLE;
        return Attention.CLEAR;
    }

    static RotationDecision assessRotation(double wholeAxisErrorDeg,
                                           double topEdgeErrorDeg,
                                           double sideClearanceAsymmetry,
                                           double markerWidthPx,
                                           PoseLabel pose,
                                           boolean detectorStable) {
        if (!Double.isFinite(wholeAxisErrorDeg) || !Double.isFinite(markerWidthPx) || markerWidthPx <= 0)
            return new RotationDecision(Attention.UNASSESSABLE,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,Double.NaN,false,false,"whole-marker orientation is not resolved");

        if (Math.abs(wholeAxisErrorDeg)>MAX_PLAUSIBLE_ROTATION_DEG)
            return new RotationDecision(Attention.UNASSESSABLE,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,Double.NaN,false,false,
                    String.format(java.util.Locale.US,"a %.0f° reading is far outside any real marker rotation; the 12 landmarks were not found correctly on this photo",wholeAxisErrorDeg));
        double rise=Math.abs(Math.tan(Math.toRadians(wholeAxisErrorDeg))*markerWidthPx);
        double a=Math.abs(wholeAxisErrorDeg);
        boolean axisVisible=a>=ROTATION_CHECK_DEG && rise>=0.55;
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

        // A turned triangle turns its top edge with it. Genuine corroborated turns have reached
        // about 1.7 deg, so values below ROTATION_CHECK_DEG stay clear for replica QC. A level-top
        // skew is even less specific: a genuine Phillips GMT reached 2.2 deg, so that path waits
        // until SKEW_ONLY_MIN_DEG.
        if (!baseCorroborates && a<SKEW_ONLY_MIN_DEG) {
            if (!spacingStrong) {
                return new RotationDecision(Attention.CLEAR,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                        String.format(java.util.Locale.US,"the point leans %.1f° but remains inside observed genuine variation",a));
            }
        }
        if (axisVisible && (baseCorroborates || spacingCorroborates)) {
            boolean strong=a>=3.0 || spacingStrong;
            return new RotationDecision(strong?Attention.STRONG:Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    strong?"whole-marker rotation is clearly beyond the supported genuine envelope":"whole-marker rotation is beyond the supported genuine envelope and is corroborated");
        }
        if (axisVisible) {
            return new RotationDecision(Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    "whole-marker rotation is beyond the supported genuine envelope");
        }
        if (spacingStrong) {
            return new RotationDecision(Attention.CHECK,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                    "59/01 side spacing is beyond the supported genuine envelope even though the marker-axis angle is small");
        }
        return new RotationDecision(Attention.CLEAR,wholeAxisErrorDeg,topEdgeErrorDeg,sideClearanceAsymmetry,rise,baseCorroborates,spacingCorroborates,
                "no marker rotation outside observed genuine variation is resolved at this image scale");
    }

    // ---- 6 o'clock baton ---------------------------------------------------------------
    // Genuine centring has reached 0.075 and rotation 1.79 deg. Existing CHECK boundaries of
    // 0.10 and 2.0 deg are already outside that observed genuine envelope.
    static final double SIX_CENTRING_CHECK = 0.10, SIX_CENTRING_STRONG = 0.20;
    static final double SIX_ROTATION_CHECK_DEG = 2.0, SIX_ROTATION_STRONG_DEG = 3.5;
    static final double MIN_BATON_PX = 20.0;

    static final class SixDecision {
        final Attention attention;final boolean offCentre,rotated,tooSmall;final String reason;
        SixDecision(Attention a,boolean off,boolean rot,boolean small,String why){attention=a;offCentre=off;rotated=rot;tooSmall=small;reason=why;}
    }

    static SixDecision assessSix(double centring,double rotationDeg,double widthPx,double lengthPx,
                                 PoseLabel pose,boolean stable){
        if(!Double.isFinite(centring)||!Double.isFinite(rotationDeg)||!(widthPx>0))
            return new SixDecision(Attention.UNASSESSABLE,false,false,false,"6 baton not measured");
        if(widthPx<MIN_BATON_PX)
            return new SixDecision(Attention.UNASSESSABLE,false,false,true,
                    String.format(java.util.Locale.US,"the 6 baton is only %.0f px wide in this photo (minimum %.0f)",Math.floor(widthPx),MIN_BATON_PX));
        if(Math.abs(rotationDeg)>MAX_PLAUSIBLE_ROTATION_DEG||Math.abs(centring)>0.6)
            return new SixDecision(Attention.UNASSESSABLE,false,false,false,"the 6 reading is far outside any real marker error; the baton or ticks were not found correctly");
        double c=Math.abs(centring),offPx=c*widthPx;
        double a=Math.abs(rotationDeg),rise=Math.abs(Math.tan(Math.toRadians(rotationDeg)))*(lengthPx>0?lengthPx:2.5*widthPx);
        boolean off=c>=SIX_CENTRING_CHECK&&offPx>=1.0, offStrong=c>=SIX_CENTRING_STRONG&&offPx>=2.0;
        boolean rot=a>=SIX_ROTATION_CHECK_DEG&&rise>=1.0, rotStrong=a>=SIX_ROTATION_STRONG_DEG&&rise>=2.0;
        boolean poor=pose==PoseLabel.RETAKE||pose==PoseLabel.UNASSESSABLE;
        if(!stable||poor){
            if(offStrong)return new SixDecision(Attention.CHECK,true,false,false,
                    "a clear offset is visible but the 6 landmarks or the photo angle are not reliable enough for a firm verdict");
            return new SixDecision(Attention.UNASSESSABLE,false,false,false,
                    poor?"photo too angled to clear the 6 baton":"6 landmarks measured with low confidence");
        }
        if(offStrong||rotStrong)return new SixDecision(Attention.STRONG,off,rot,false,"6 baton visibly off-centre or rotated");
        if(off||rot)return new SixDecision(Attention.CHECK,off,rot,false,"6 baton outside observed genuine centring/rotation variation");
        return new SixDecision(Attention.CLEAR,false,false,false,"6 baton inside observed genuine centring/rotation variation");
    }

    // Round hour markers. Genuine judged offset has reached 0.103 and size difference 0.072.
    // Existing CHECK boundaries remain safely outside that evidence.
    static final double ROUND_OFFSET_CHECK = 0.15, ROUND_OFFSET_STRONG = 0.25;
    /** Compatibility constant used by the summary/tests. Genuine corroborated turns are kept clear below this level. */
    static final double GENUINE_ROTATION_SEEN_DEG = ROTATION_CHECK_DEG;
    static final double ROUND_SIZE_CHECK = 0.12;
    static final double MIN_ROUND_PX = 24.0;

    static final class RoundDecision {
        final Attention attention;final boolean offCentre,sizeOdd,tooSmall;final String reason;
        RoundDecision(Attention a,boolean off,boolean size,boolean small,String why){attention=a;offCentre=off;sizeOdd=size;tooSmall=small;reason=why;}
    }

    static RoundDecision assessRound(double offset,double diameterPx,double sizeRatio,PoseLabel pose,boolean stable){
        if(!Double.isFinite(offset)||!(diameterPx>0))return new RoundDecision(Attention.UNASSESSABLE,false,false,false,"not measured");
        if(diameterPx<MIN_ROUND_PX)return new RoundDecision(Attention.UNASSESSABLE,false,false,true,
                String.format(java.util.Locale.US,"only %.0f px across in this photo (minimum %.0f)",Math.floor(diameterPx),MIN_ROUND_PX));
        if(Math.abs(offset)>0.6)return new RoundDecision(Attention.UNASSESSABLE,false,false,false,
                "the reading is far outside any real marker error; the marker or ticks were not found correctly");
        double o=Math.abs(offset),px=o*diameterPx;
        boolean off=o>=ROUND_OFFSET_CHECK&&px>=2.0, offStrong=o>=ROUND_OFFSET_STRONG&&px>=4.0;
        boolean size=Double.isFinite(sizeRatio)&&Math.abs(sizeRatio-1)>=ROUND_SIZE_CHECK&&Math.abs(sizeRatio-1)*diameterPx>=2.0;
        boolean poor=pose==PoseLabel.RETAKE||pose==PoseLabel.UNASSESSABLE;
        if(!stable||poor){
            return new RoundDecision(Attention.UNASSESSABLE,false,false,false,poor?"photo too angled to clear the round markers":"measured with low confidence");
        }
        if(offStrong)return new RoundDecision(Attention.STRONG,true,size,false,"visibly off-centre");
        if(off||size)return new RoundDecision(Attention.CHECK,off,size,false,off&&size?"outside genuine placement and size variation":off?"outside genuine placement variation":"outside genuine size variation");
        return new RoundDecision(Attention.CLEAR,false,false,false,"centred within observed genuine variation");
    }

    static double wrap90(double deg) {
        double x=deg%180.0;
        if(x<=-90.0)x+=180.0;
        if(x>90.0)x-=180.0;
        return x;
    }

    private GmtHumanQcMath() {}
}
