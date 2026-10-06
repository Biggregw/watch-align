package com.watchalign.mobile;

import java.io.FileReader;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/**
 * RESEARCH PROTOTYPE (desktop harness only; not compiled into the app).
 *
 * Re-centres the production 12-triangle measurement on a genuine-calibrated nominal instead of the
 * Alpha92 master position. Genuine watches read the master's 12 toward the dial centre (58/59 watches,
 * median -0.0038 R) and slightly wider, so the shipped 12 "local offset" is mostly that shared bias.
 *
 * Input: the unchanged Alpha94MarkerMeasurement.Report (production maths, frozen pose) and a nominal written by
 * tools/research/alpha96_calibration/calibrate_m12_nominal.py (m12_nominal.properties):
 *   radial_R, tangential_R   12 local offset components of the genuine nominal, units of dial radius
 *   left_side_deg, right_side_deg, base_tilt_deg, rotation_deg   genuine nominal triangle shape / centreline
 * Output: the 12 local offset (after the ring model, as in production) measured from that nominal, in the
 * upright dial frame, plus re-centred angles. Nothing is thresholded; fail-closed exactly as production
 * (an unusable triangle or an unfitted ring gives no value).
 */
final class Alpha97TriangleNominal {
    final double radialR,tangentialR,leftSideDeg,rightSideDeg,baseTiltDeg,rotationDeg;
    final int nWatches;

    Alpha97TriangleNominal(double radialR,double tangentialR,double leftSideDeg,double rightSideDeg,
                           double baseTiltDeg,double rotationDeg,int nWatches){
        this.radialR=radialR;this.tangentialR=tangentialR;this.leftSideDeg=leftSideDeg;this.rightSideDeg=rightSideDeg;
        this.baseTiltDeg=baseTiltDeg;this.rotationDeg=rotationDeg;this.nWatches=nWatches;
    }

    static Alpha97TriangleNominal load(String path) throws IOException{
        Properties p=new Properties();
        try(FileReader r=new FileReader(path)){p.load(r);}
        return new Alpha97TriangleNominal(d(p,"radial_R"),d(p,"tangential_R"),d(p,"left_side_deg"),d(p,"right_side_deg"),
                d(p,"base_tilt_deg"),d(p,"rotation_deg"),Integer.parseInt(p.getProperty("n_watches","0").trim()));
    }
    private static double d(Properties p,String k){
        String v=p.getProperty(k);if(v==null)throw new IllegalArgumentException("nominal missing "+k);return Double.parseDouble(v.trim());
    }

    static final class Result {
        boolean usable;String reason="";
        /** Local offset from the genuine nominal, px. 12 radial is up, so right = tangential and down = -radial. */
        double radialPx=Double.NaN,tangentialPx=Double.NaN,rightPx=Double.NaN,downPx=Double.NaN,offsetPx=Double.NaN,offsetR=Double.NaN;
        double rotationDeg=Double.NaN,leftSideDeg=Double.NaN,rightSideDeg=Double.NaN,baseTiltDeg=Double.NaN;

        String line(){
            if(!usable)return "12 (genuine ref, research): "+reason;
            return String.format(Locale.US,"12 (genuine ref, research): %.2f px %s · %.2f px %s · local %.2f px (%.4f R) · centreline %s · L/R sides %+.2f°/%+.2f°",
                    Math.abs(rightPx),rightPx<0?"left":"right",Math.abs(downPx),downPx<0?"up":"down",offsetPx,offsetR,
                    Alpha94MarkerMeasurement.Report.rot(rotationDeg),leftSideDeg,rightSideDeg);
        }
    }

    Result apply(Alpha94MarkerMeasurement.Report r){
        Result o=new Result();
        Alpha94MarkerMeasurement.Marker m=r==null?null:r.triangle;
        double rpx=r==null?Double.NaN:r.dialRadiusPx;
        if(m==null){o.reason="unavailable";return o;}
        if(!m.usable){o.reason="OCCLUDED / INSUFFICIENT CLEAN EDGE";return o;}
        if(!Double.isFinite(m.localRadialPx)||!Double.isFinite(m.localTangentialPx)||!(rpx>0)){o.reason="ring not fitted; no local reference";return o;}
        o.usable=true;
        o.radialPx=m.localRadialPx-radialR*rpx;
        o.tangentialPx=m.localTangentialPx-tangentialR*rpx;
        o.rightPx=o.tangentialPx;o.downPx=-o.radialPx;
        o.offsetPx=Math.hypot(o.radialPx,o.tangentialPx);o.offsetR=o.offsetPx/rpx;
        o.rotationDeg=m.rotationDeg-rotationDeg;
        o.leftSideDeg=m.leftSideErrDeg-leftSideDeg;o.rightSideDeg=m.rightSideErrDeg-rightSideDeg;o.baseTiltDeg=m.baseTiltDeg-baseTiltDeg;
        return o;
    }
}
