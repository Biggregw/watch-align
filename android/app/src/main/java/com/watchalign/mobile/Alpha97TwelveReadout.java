package com.watchalign.mobile;

import java.util.Locale;

/**
 * Alpha97 research display of the 12 triangle: the robust readout recorded in docs/HANDOFF.md
 * ("Alpha96 12-triangle decision - 2026-10-06") and tools/research/alpha96_calibration/README.md.
 *
 * Display only. It reads the unchanged Alpha94MarkerMeasurement.Report (frozen pose, unchanged marker maths) and
 * re-expresses the 12 triangle from a frozen genuine-calibrated reference instead of the Alpha92 master:
 *   centreline rotation, left-side angle, right-side angle  (deg, + = clockwise)
 *   left/right position                                      (px and units of dial radius, upright dial frame)
 * The radial (toward/away from centre) displacement is lighting-sensitive and is shown as not assessed.
 * The full 2-D 12 offset is not used. No thresholds, no verdicts, no colour: genuine context is a count of reference
 * watches reading at least as far, nothing more. Fail-closed exactly as Alpha96: an occluded / unclean 12 or an unfitted
 * ring gives no value.
 *
 * Frozen reference: 45 genuine watches (Bob's 32, Owner priority 4, Phillips 7, other 2; SWE excluded;
 * shared / stock photos excluded), one value per watch, generated from
 * tools/research/alpha96_calibration/m12_nominal.properties and m12_genuine_reference.csv (Alpha97TwelveReadoutTest pins
 * these constants to those files). Context arrays are per-watch leave-one-watch-out values.
 */
final class Alpha97TwelveReadout {
    static final String LABEL="12 (research · genuine reference)";
    // BEGIN GENERATED (gen_alpha97_constants.py)
    static final int N_WATCHES=45;
    /** Genuine nominal relative to the Alpha92 master: 12 local offset components (units of R, radial + = outward). */
    static final double NOMINAL_RADIAL_R=-0.003722,NOMINAL_TANGENTIAL_R=-0.000670;
    /** Genuine nominal triangle angles relative to the Alpha92 master, degrees. */
    static final double NOMINAL_LEFT_SIDE_DEG=-0.174330,NOMINAL_RIGHT_SIDE_DEG=0.132800,
            NOMINAL_ROTATION_DEG=-0.088640;

    /** Per-watch genuine context (leave-one-watch-out, absolute values): lateral (R), centreline (deg), max side (deg). */
    static final double[] GENUINE_LATERAL_R={
            0.000544,0.000160,0.000852,0.000172,0.000452,0.000406,0.000262,0.000058,
            0.000819,0.001622,0.000767,0.000129,0.001665,0.000523,0.000053,0.000008,
            0.000190,0.000303,0.001559,0.000784,0.000548,0.000863,0.000041,0.000979,
            0.001232,0.000658,0.001032,0.000345,0.000904,0.002273,0.000681,0.000207,
            0.000123,0.000023,0.000135,0.000015,0.001794,0.000403,0.000149,0.000105,
            0.000120,0.000314,0.000703,0.000297,0.000131};
    static final double[] GENUINE_CENTRELINE_DEG={
            0.277530,0.160200,0.518305,0.632320,0.264955,0.991245,0.152635,0.212260,
            0.072625,0.322900,0.692825,0.163080,0.137170,0.258800,0.589850,0.381300,
            0.192110,0.149555,0.875325,0.194225,0.691690,0.197260,0.276675,0.107780,
            0.129940,0.555835,0.479870,0.136490,0.227025,0.321895,0.575945,0.051715,
            0.285405,0.618390,0.217195,0.155095,0.565450,0.399395,0.713660,0.949840,
            0.087495,0.913685,0.056065,0.572405,0.063015};
    static final double[] GENUINE_SIDES_DEG={
            0.278155,0.144185,0.553040,0.822375,0.333030,1.044665,0.207695,0.125615,
            0.262685,0.214575,0.758615,0.102775,0.143615,0.298255,0.592175,0.358505,
            0.150235,0.205995,0.862865,0.324215,0.734085,0.127755,0.408995,0.042945,
            0.062415,0.487095,0.414935,0.091215,0.279275,0.457045,0.646995,0.178235,
            0.325235,0.517625,0.297175,0.242715,0.522365,0.594185,0.665745,0.910195,
            0.113015,0.932205,0.281915,0.626115,0.187715};
    // END GENERATED

    static final class Result {
        boolean usable;String reason="";
        double centrelineDeg=Double.NaN,leftSideDeg=Double.NaN,rightSideDeg=Double.NaN,sidesDeg=Double.NaN;
        /** + = right in the upright dial frame. */
        double lateralPx=Double.NaN,lateralR=Double.NaN;
        /** Not assessed (lighting-sensitive); kept for research logs only, never displayed as a value. */
        double radialPx=Double.NaN;
        int atLeastCentreline,atLeastSides,atLeastLateral;
    }

    private Alpha97TwelveReadout(){}

    static Result from(Alpha94MarkerMeasurement.Report r){
        Result o=new Result();
        Alpha94MarkerMeasurement.Marker m=r==null?null:r.triangle;
        double rpx=r==null?Double.NaN:r.dialRadiusPx;
        if(m==null){o.reason="unavailable";return o;}
        if(!m.usable){o.reason="OCCLUDED / INSUFFICIENT CLEAN EDGE · withheld";return o;}
        if(!Double.isFinite(m.localRadialPx)||!Double.isFinite(m.localTangentialPx)||!(rpx>0)){o.reason="ring not fitted · withheld";return o;}
        o.usable=true;
        o.centrelineDeg=m.rotationDeg-NOMINAL_ROTATION_DEG;
        o.leftSideDeg=m.leftSideErrDeg-NOMINAL_LEFT_SIDE_DEG;
        o.rightSideDeg=m.rightSideErrDeg-NOMINAL_RIGHT_SIDE_DEG;
        o.sidesDeg=Math.max(Math.abs(o.leftSideDeg),Math.abs(o.rightSideDeg));
        o.lateralPx=m.localTangentialPx-NOMINAL_TANGENTIAL_R*rpx;   // 12: tangential + = right
        o.lateralR=o.lateralPx/rpx;
        o.radialPx=m.localRadialPx-NOMINAL_RADIAL_R*rpx;
        o.atLeastCentreline=atLeast(GENUINE_CENTRELINE_DEG,Math.abs(o.centrelineDeg));
        o.atLeastSides=atLeast(GENUINE_SIDES_DEG,o.sidesDeg);
        o.atLeastLateral=atLeast(GENUINE_LATERAL_R,Math.abs(o.lateralR));
        return o;
    }

    static String line(Result o){
        if(!o.usable)return LABEL+": "+o.reason;
        return String.format(Locale.US,"%s: centreline %s · left side %+.2f° · right side %+.2f° · %.2f px %s (%.4f R)"
                        +" · toward-centre: lighting-sensitive · not assessed"
                        +" · reference watches as far or further: centreline %d/%d · sides %d/%d · left/right %d/%d",
                LABEL,Alpha94MarkerMeasurement.Report.rot(o.centrelineDeg),o.leftSideDeg,o.rightSideDeg,
                Math.abs(o.lateralPx),o.lateralPx<0?"left":"right",Math.abs(o.lateralR),
                o.atLeastCentreline,N_WATCHES,o.atLeastSides,N_WATCHES,o.atLeastLateral,N_WATCHES);
    }

    /** Alpha96 compact summary with only its 12 line replaced by the Alpha97 readout; every other line is unchanged. */
    static String summary(Alpha94MarkerMeasurement.Report r){
        String[] lines=r.compactSummary().split("\n",-1);
        lines[1]=line(from(r));
        return String.join("\n",lines);
    }

    private static int atLeast(double[] ref,double v){int n=0;for(double x:ref)if(x>=v-1e-12)n++;return n;}
}
