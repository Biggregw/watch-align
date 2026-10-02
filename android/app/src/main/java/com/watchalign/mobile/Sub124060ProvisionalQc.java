package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Frozen provisional alignment-QC bands for Rolex Submariner 124060.
 *
 * Source: Watch-family Calibrator run 36927036008, artifact
 * watch-calibrator-124060-36927036008, sha256
 * 5a23dcf54486d99aa6a0b50cbd7b7027c0a190a777030ff67586706b88d311da.
 * The bands were frozen from development genuine watches before holdout and then survived the
 * validation/holdout gates. They are alignment tolerances only, not authenticity thresholds.
 *
 * The 12 gap metric is deliberately absent. Calibration marked it INSUFFICIENT because it was
 * pose/scale sensitive in development, so production must continue to measure but not judge it.
 */
final class Sub124060ProvisionalQc {
    enum Judgement { CLEAR, CHECK, OUTSIDE, UNJUDGED }

    static final class Band {
        final String key;
        final double clearLow,clearHigh,checkLow,checkHigh;
        Band(String key,double clearLow,double clearHigh,double checkLow,double checkHigh){
            this.key=key;this.clearLow=clearLow;this.clearHigh=clearHigh;this.checkLow=checkLow;this.checkHigh=checkHigh;
        }
        Judgement judge(double v){
            if(!Double.isFinite(v))return Judgement.UNJUDGED;
            if(v>=clearLow&&v<=clearHigh)return Judgement.CLEAR;
            if(v>=checkLow&&v<=checkHigh)return Judgement.CHECK;
            return Judgement.OUTSIDE;
        }
    }

    static final Band TWELVE_ROTATION=new Band("twelve.rotation_deg",
            -13.88038488935,15.22676818935,-21.157173159025,22.503556459025);
    static final Band TWELVE_CENTRING=new Band("twelve.centring_w",
            -0.147616360146,0.136472106146,-0.233275680438,0.222131426438);
    static final Band ROUND_RING_RHO=new Band("round.ring_rho",
            0.7826128775166086,0.8526717081695576,0.7650981698533712,0.870186415832795);
    static final Band ROUND_SPACING_RMS=new Band("round.spacing_rms_deg",
            -5.587231836527973,6.370183430972006,-9.573036925694634,10.355988520138666);
    static final Band BATON_3_9_LINE_OFFSET=new Band("baton.3_9_line_offset_r",
            -0.08170984795356724,0.09378104194951688,-0.14020681125459528,0.1522780052505449);
    static final Band AXIS_12_6_LINE_OFFSET=new Band("axis.12_6_line_offset_r",
            -0.1343132175899482,0.1396427365740998,-0.21825249276636563,0.22358201175051726);

    private Sub124060ProvisionalQc(){}

    static String words(Judgement j){
        switch(j){
            case CLEAR:return "PROVISIONAL CLEAR";
            case CHECK:return "PROVISIONAL CHECK";
            case OUTSIDE:return "OUTSIDE PROVISIONAL RANGE";
            default:return "NOT JUDGED";
        }
    }

    static double median(List<Double> values){
        List<Double> x=new ArrayList<>();
        for(Double v:values)if(v!=null&&Double.isFinite(v))x.add(v);
        if(x.isEmpty())return Double.NaN;
        Collections.sort(x);int n=x.size();
        return n%2==1?x.get(n/2):(x.get(n/2-1)+x.get(n/2))/2.0;
    }

    /** RMS spacing residual after removing the common angular offset, matching calibration. */
    static double spacingRms(List<Double> angleErrorsDeg){
        List<Double> x=new ArrayList<>();
        for(Double v:angleErrorsDeg)if(v!=null&&Double.isFinite(v))x.add(v);
        if(x.size()<4)return Double.NaN;
        double mean=0;for(double v:x)mean+=v;mean/=x.size();
        double ss=0;for(double v:x){double d=v-mean;ss+=d*d;}
        return Math.sqrt(ss/x.size());
    }

    /** Perpendicular distance of the infinite line through two rectified, R-normalised points from 0,0. */
    static double lineOffset(double[] p,double[] q){
        if(p==null||q==null||p.length<2||q.length<2)return Double.NaN;
        double dx=q[0]-p[0],dy=q[1]-p[1],d=Math.hypot(dx,dy);
        if(!(d>0))return Double.NaN;
        return Math.abs(p[0]*q[1]-q[0]*p[1])/d;
    }
}
