package com.watchalign.mobile;

/**
 * Pose policy for human-style GMT QC.
 *
 * Rehaut is valuable when its own fit is coherent, but a noisy rehaut fit must
 * never erase otherwise usable local marker evidence. Ellipse pose remains an
 * independent fallback and severe-image rejection cue.
 */
final class GmtHumanPosePolicy {
    static GmtHumanQcMath.PoseDecision classify(GmtRehautPoseAnalyzer.Result rehaut,
                                                GmtEllipsePoseAnalyzer.Result ellipse,
                                                double cueAxisDisagreementDeg) {
        boolean ellipseUsable=ellipse!=null&&ellipse.valid&&Double.isFinite(ellipse.tiltDeg);
        boolean rehautUsable=rehaut!=null&&rehaut.valid
                &&Double.isFinite(rehaut.edgeCoverage)&&rehaut.edgeCoverage>=0.25
                &&Double.isFinite(rehaut.fitResidual)&&rehaut.fitResidual<=0.16
                &&Double.isFinite(rehaut.minWidthOverMean)
                &&Double.isFinite(rehaut.firstHarmonicStrength);

        if(ellipseUsable&&ellipse.tiltDeg>=15.0)
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.RETAKE,
                    "dial ellipse shows severe obliqueness");

        if(rehautUsable){
            if(Double.isFinite(cueAxisDisagreementDeg)&&ellipseUsable&&ellipse.tiltDeg>=9.0
                    &&rehaut.firstHarmonicStrength>=0.12&&cueAxisDisagreementDeg>55.0){
                // Disagreement means the rehaut direction is not trustworthy. Fall
                // back to the independent ellipse rather than rejecting useful QC.
                if(ellipse.tiltDeg>=9.0)
                    return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                            "rehaut direction disagrees with ellipse, so ellipse pose is used and rehaut is advisory only");
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                        "rehaut direction is inconsistent, but independent planar pose is near frontal");
            }
            if(rehaut.minWidthOverMean<0.48)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.RETAKE,
                        "a coherent rehaut fit shows one side collapsing below about half the mean visible width");
            if(rehaut.minWidthOverMean<0.78||rehaut.firstHarmonicStrength>=0.16)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "coherent rehaut asymmetry shows moderate directional perspective");
        }

        if(ellipseUsable){
            if(ellipse.tiltDeg>=8.0)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "independent dial ellipse shows moderate planar foreshortening");
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                    rehautUsable?"rehaut and planar cues are suitable for close inspection":"planar pose is near frontal; noisy rehaut evidence is ignored");
        }

        if(rehautUsable){
            if(rehaut.minWidthOverMean<0.60||rehaut.firstHarmonicStrength>=0.20)
                return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.CORRECTABLE,
                        "rehaut is coherent but no independent ellipse cue is available");
            return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.GOOD,
                    "coherent rehaut pose is mild; independent ellipse cue unavailable");
        }

        return new GmtHumanQcMath.PoseDecision(GmtHumanQcMath.PoseLabel.UNASSESSABLE,
                "neither rehaut nor planar pose could be constrained reliably");
    }

    private GmtHumanPosePolicy(){}
}
