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
 * Reference: the model's ModelReference.Triangle (assets/models/<id>/reference/triangle_*; for the GMT the 45-watch
 * Alpha97b reference exported from tools/research/alpha96_calibration/m12_nominal.properties and
 * m12_genuine_reference.csv). Context arrays are per-watch leave-one-watch-out values.
 */
final class Alpha97TwelveReadout {
    static final String LABEL="12 (research · genuine reference)";
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

    static Result from(Alpha94MarkerMeasurement.Report r,ModelReference ref){
        Result o=new Result();
        Alpha94MarkerMeasurement.Marker m=r==null?null:r.triangle;
        double rpx=r==null?Double.NaN:r.dialRadiusPx;
        if(m==null){o.reason="unavailable";return o;}
        ModelReference.Triangle t=ref==null?null:ref.triangle;
        if(t==null){o.reason="no genuine reference for this model · withheld";return o;}
        if(!m.usable){o.reason="OCCLUDED / INSUFFICIENT CLEAN EDGE · withheld";return o;}
        if(!Double.isFinite(m.localRadialPx)||!Double.isFinite(m.localTangentialPx)||!(rpx>0)){o.reason="ring not fitted · withheld";return o;}
        o.usable=true;
        o.centrelineDeg=m.rotationDeg-t.nominalRotationDeg;
        o.leftSideDeg=m.leftSideErrDeg-t.nominalLeftSideDeg;
        o.rightSideDeg=m.rightSideErrDeg-t.nominalRightSideDeg;
        o.sidesDeg=Math.max(Math.abs(o.leftSideDeg),Math.abs(o.rightSideDeg));
        o.lateralPx=m.localTangentialPx-t.nominalTangentialR*rpx;   // 12: tangential + = right
        o.lateralR=o.lateralPx/rpx;
        o.radialPx=m.localRadialPx-t.nominalRadialR*rpx;
        o.atLeastCentreline=atLeast(t.centrelineDeg,Math.abs(o.centrelineDeg));
        o.atLeastSides=atLeast(t.sidesDeg,o.sidesDeg);
        o.atLeastLateral=atLeast(t.lateralR,Math.abs(o.lateralR));
        return o;
    }

    static String line(Result o,ModelReference ref){
        if(!o.usable)return LABEL+": "+o.reason;
        int N_WATCHES=ref.triangle.nWatches;
        return String.format(Locale.US,"%s: centreline %s · left side %+.2f° · right side %+.2f° · %.2f px %s (%.4f R)"
                        +" · toward-centre: lighting-sensitive · not assessed"
                        +" · reference watches as far or further: centreline %d/%d · sides %d/%d · left/right %d/%d",
                LABEL,Alpha94MarkerMeasurement.Report.rot(o.centrelineDeg),o.leftSideDeg,o.rightSideDeg,
                Math.abs(o.lateralPx),o.lateralPx<0?"left":"right",Math.abs(o.lateralR),
                o.atLeastCentreline,N_WATCHES,o.atLeastSides,N_WATCHES,o.atLeastLateral,N_WATCHES);
    }

    /** Alpha96 compact summary with only its 12 line replaced by the Alpha97 readout; every other line is unchanged. */
    static String summary(Alpha94MarkerMeasurement.Report r,ModelReference ref){
        String[] lines=r.compactSummary().split("\n",-1);
        lines[1]=line(from(r,ref),ref);
        return String.join("\n",lines);
    }

    private static int atLeast(double[] ref,double v){int n=0;for(double x:ref)if(x>=v-1e-12)n++;return n;}
}
