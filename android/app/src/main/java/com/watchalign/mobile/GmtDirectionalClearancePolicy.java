package com.watchalign.mobile;

/**
 * Adds robust rehaut direction to the existing low-clearance decision without
 * pretending the rehaut width ratio is the geometric correction magnitude.
 */
final class GmtDirectionalClearancePolicy {
    static GmtHumanQcMath.ClearanceDecision assess(double observedGap,
                                                   double perspectiveScale,
                                                   GmtHumanQcMath.GapTrend rehautDirection,
                                                   GmtHumanQcMath.PoseLabel pose){
        if(!Double.isFinite(observedGap))
            return GmtHumanQcMath.assessLowClearance(observedGap,perspectiveScale,pose);

        // Touching/zero is always strong in the base policy.
        if(observedGap<=0.0)return GmtHumanQcMath.assessLowClearance(observedGap,perspectiveScale,pose);

        double corrected=(Double.isFinite(perspectiveScale)&&perspectiveScale>0)
                ?observedGap/perspectiveScale:Double.NaN;

        // Human one-sided rule. Sector direction is allowed to strengthen or
        // qualify the observation, but never supplies the numeric correction.
        if(rehautDirection==GmtHumanQcMath.GapTrend.INFLATED
                &&observedGap<GmtHumanQcMath.LOW_CLEARANCE_ATTENTION){
            return new GmtHumanQcMath.ClearanceDecision(
                    GmtHumanQcMath.Attention.STRONG,
                    GmtHumanQcMath.GapTrend.INFLATED,
                    observedGap,corrected,perspectiveScale,
                    "gap is already slightly small even though local rehaut perspective is tending to make the 12-side separation look larger");
        }
        if(rehautDirection==GmtHumanQcMath.GapTrend.COMPRESSED
                &&observedGap<GmtHumanQcMath.LOW_CLEARANCE_ATTENTION){
            return new GmtHumanQcMath.ClearanceDecision(
                    GmtHumanQcMath.Attention.CHECK,
                    GmtHumanQcMath.GapTrend.COMPRESSED,
                    observedGap,corrected,perspectiveScale,
                    "gap looks slightly small, but local rehaut perspective is tending to compress it; inspect rather than overstate the defect");
        }

        GmtHumanQcMath.ClearanceDecision base=GmtHumanQcMath.assessLowClearance(observedGap,perspectiveScale,pose);
        if(rehautDirection==GmtHumanQcMath.GapTrend.INFLATED
                &&base.attention==GmtHumanQcMath.Attention.CLEAR){
            // A non-low observed gap stays clear. We do not manufacture a defect
            // merely because perspective direction is favourable.
            return new GmtHumanQcMath.ClearanceDecision(base.attention,rehautDirection,
                    base.observedGap,base.correctedEstimate,base.perspectiveScale,
                    "local rehaut perspective tends to enlarge the visible gap, but the observed clearance is not in the low-attention region");
        }
        if(rehautDirection==GmtHumanQcMath.GapTrend.COMPRESSED
                &&base.attention==GmtHumanQcMath.Attention.CLEAR){
            return new GmtHumanQcMath.ClearanceDecision(base.attention,rehautDirection,
                    base.observedGap,base.correctedEstimate,base.perspectiveScale,
                    "local rehaut perspective tends to compress the visible gap, but the observed clearance is not low");
        }
        return base;
    }

    private GmtDirectionalClearancePolicy(){}
}
