package com.watchalign.mobile;

/**
 * Pose policy for human-style GMT QC.
 *
 * Robust cardinal rehaut sectors are allowed to contribute even when the global
 * 360-degree harmonic fit is noisy. The full harmonic model remains useful when
 * coherent, while the ellipse is an independent planar fallback/rejection cue.
 */
final class GmtHumanPosePolicy {
    static GmtHumanQcMath.PoseDecision classify(GmtRehautPoseAnalyzer.Result rehaut,
                                                GmtRehautSectorAnalyzer.Result sectors,
                                                GmtEllipsePoseAnalyzer.Result ellipse,
                                                double cueAxisDisagreementDeg) {
        boolean ellipseUsable=ellipse!=null&&ellipse.valid&&Double.isFinite(ellipse.tiltDeg);
        boolean sectorUsable=sectors!=null&&sectors.valid&&sectors.meanCoverage()>=0.35
                &&Double.isFinite(sectors.minOverMean);
        boolean rehautUsable=rehaut!=null&&rehaut.valid
                &&Double.isFinite(rehaut.edgeCoverage)&&rehaut.edgeCoverage>=0.25
                &&Double.isFinite(rehaut.fitResidual)&&rehaut.fitResidual<=0.16
                &&Double.isFinite(rehaut.minWidthOverMean)
                &&Double.isFinite(rehaut.firstHarmonicStrength);

        if(ellipseUsable&&ellipse.tiltDeg>=15.0)
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.RETAKE,
                    "dial ellipse shows severe obliqueness");

        // Local sector collapse is a direct human-visible warning and does not need
        // a perfect 360-degree harmonic model to be useful.
        if(sectorUsable){
            if(sectors.minOverMean<0.45)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.RETAKE,
                        "local rehaut sectors show one cardinal side collapsing below about half the mean visible width");
            double maxAsym=Math.max(Math.abs(sectors.verticalAsymmetry),Math.abs(sectors.horizontalAsymmetry));
            if(sectors.minOverMean<0.75||maxAsym>=0.14)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "local 12/3/6/9 rehaut visibility shows directional perspective");
        }

        if(rehautUsable){
            if(Double.isFinite(cueAxisDisagreementDeg)&&ellipseUsable&&ellipse.tiltDeg>=9.0
                    &&rehaut.firstHarmonicStrength>=0.12&&cueAxisDisagreementDeg>55.0){
                if(ellipse.tiltDeg>=9.0)
                    return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                            "global rehaut direction disagrees with ellipse, so only local sectors/ellipse are trusted");
            }
            if(rehaut.minWidthOverMean<0.48)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.RETAKE,
                        "a coherent global rehaut fit shows one side collapsing below about half the mean visible width");
            if(rehaut.minWidthOverMean<0.78||rehaut.firstHarmonicStrength>=0.16)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "coherent global rehaut asymmetry shows moderate directional perspective");
        }

        if(ellipseUsable){
            if(ellipse.tiltDeg>=8.0)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "independent dial ellipse shows moderate planar foreshortening");
            if(sectorUsable)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                        "local rehaut sectors and planar pose are mild enough for close inspection");
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                    rehautUsable?"global rehaut and planar cues are suitable for close inspection":"planar pose is near frontal; noisy global rehaut fit is advisory only");
        }

        if(sectorUsable)
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                    "local rehaut sectors are mild; independent ellipse cue unavailable");

        if(rehautUsable){
            if(rehaut.minWidthOverMean<0.60||rehaut.firstHarmonicStrength>=0.20)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "global rehaut is coherent but no independent ellipse cue is available");
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                    "coherent rehaut pose is mild; independent ellipse cue unavailable");
        }

        return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                "neither local rehaut sectors nor planar pose could be constrained reliably");
    }

    private GmtHumanPosePolicy(){}
}
