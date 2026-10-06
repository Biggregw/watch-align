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
 * Frozen reference: 41 provenance-strong genuine watches (Bob's 32, Phillips 7, other 2; SWE excluded;
 * shared / stock photos excluded), one value per watch, generated from
 * tools/research/alpha96_calibration/m12_nominal.properties and m12_genuine_reference.csv (Alpha97TwelveReadoutTest pins
 * these constants to those files). Context arrays are per-watch leave-one-watch-out values.
 */
final class Alpha97TwelveReadout {
    static final String LABEL="12 (research · genuine reference)";
    static final int N_WATCHES=41;
    /** Genuine nominal relative to the Alpha92 master: 12 local offset components (units of R, radial + = outward). */
    static final double NOMINAL_RADIAL_R=-0.003708,NOMINAL_TANGENTIAL_R=-0.000764;
    /** Genuine nominal triangle angles relative to the Alpha92 master, degrees. */
    static final double NOMINAL_LEFT_SIDE_DEG=-0.136770,NOMINAL_RIGHT_SIDE_DEG=0.154120,
            NOMINAL_ROTATION_DEG=0.039850;

    /** Per-watch genuine context (leave-one-watch-out, absolute values): lateral (R), centreline (deg), max side (deg). */
    static final double[] GENUINE_LATERAL_R={
            0.000485,0.000262,0.000954,0.000113,0.000393,0.000347,0.000202,0.000160,
            0.000759,0.001723,0.000708,0.000230,0.001606,0.000624,0.000155,0.000104,
            0.000131,0.000404,0.001499,0.000724,0.000649,0.000965,0.000143,0.001080,
            0.001172,0.000598,0.001134,0.000446,0.000844,0.002213,0.000621,0.000148,
            0.000063,0.000124,0.000075,0.000099,0.001895,0.000343,0.000250,0.000036,
            0.000232};
    static final double[] GENUINE_CENTRELINE_DEG={
            0.158670,0.041340,0.399445,0.710920,0.343555,1.069845,0.231235,0.093400,
            0.151225,0.204040,0.771425,0.044220,0.018310,0.139940,0.470990,0.262440,
            0.073250,0.228155,0.953925,0.272825,0.572830,0.078400,0.355275,0.025435,
            0.007805,0.436975,0.361010,0.017630,0.305625,0.400495,0.654545,0.131765,
            0.364005,0.499530,0.295795,0.233695,0.446590,0.477995,0.594800,0.830980,
            0.141615};
    static final double[] GENUINE_SIDES_DEG={
            0.230735,0.118575,0.505620,0.849300,0.359955,1.073180,0.234620,0.100005,
            0.291200,0.188965,0.785540,0.055355,0.096195,0.272645,0.544755,0.311085,
            0.124625,0.234510,0.889790,0.351140,0.708475,0.102145,0.435920,0.014210,
            0.022410,0.439675,0.389325,0.056840,0.306200,0.483970,0.673920,0.206750,
            0.352920,0.492015,0.325690,0.269640,0.474945,0.622700,0.618325,0.884585,
            0.162105};

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
