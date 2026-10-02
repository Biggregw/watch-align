package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Frozen family-specific alignment calibration for the Rolex Submariner 124060.
 *
 * Source: Watch-family Calibrator run 36927036008, artifact
 * watch-calibrator-124060-36927036008, SHA-256
 * 5a23dcf54486d99aa6a0b50cbd7b7027c0a190a777030ff67586706b88d311da.
 *
 * The calibrator froze limits from development genuine watches, then validation and holdout could
 * reject them but never move them. Replica evidence was stress-test evidence only. These limits are
 * provisional alignment-QC tolerances, not authenticity thresholds.
 *
 * The 12-gap metric is deliberately absent. Calibration marked it INSUFFICIENT because it was
 * pose/scale sensitive, so the product continues to measure it but never uses it for a verdict.
 */
final class Sub124060Calibration {
    static final class Band {
        final String key;
        final double clearLow,clearHigh,checkLow,checkHigh;
        Band(String key,double clearLow,double clearHigh,double checkLow,double checkHigh){
            this.key=key;this.clearLow=clearLow;this.clearHigh=clearHigh;this.checkLow=checkLow;this.checkHigh=checkHigh;
        }
        GmtHumanQcMath.Attention judge(double v){
            if(!Double.isFinite(v))return GmtHumanQcMath.Attention.UNASSESSABLE;
            if(v>=clearLow&&v<=clearHigh)return GmtHumanQcMath.Attention.CLEAR;
            if(v>=checkLow&&v<=checkHigh)return GmtHumanQcMath.Attention.CHECK;
            return GmtHumanQcMath.Attention.STRONG;
        }
    }

    static final class Assessment {
        GmtHumanQcMath.Attention rotation=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention centring=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention roundRing=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention roundSpacing=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention baton39=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention axis126=GmtHumanQcMath.Attention.UNASSESSABLE;
        double roundRingRho=Double.NaN,roundSpacingRmsDeg=Double.NaN;
        double baton39LineOffsetR=Double.NaN,axis126LineOffsetR=Double.NaN;

        GmtHumanQcMath.Attention twelve(){return worst(rotation,centring,axis126);}
        GmtHumanQcMath.Attention rounds(){return worst(roundRing,roundSpacing);}
        GmtHumanQcMath.Attention baton(String label){
            if("3".equals(label)||"9".equals(label))return baton39;
            if("6".equals(label))return axis126;
            return GmtHumanQcMath.Attention.UNASSESSABLE;
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

    private Sub124060Calibration(){}

    static String words(GmtHumanQcMath.Attention a){
        switch(a){
            case CLEAR:return "CLEAR";
            case CHECK:return "CHECK";
            case STRONG:return "CHECK CLOSELY";
            default:return "NOT JUDGED";
        }
    }

    static GmtHumanQcMath.Attention worst(GmtHumanQcMath.Attention... values){
        boolean any=false;
        GmtHumanQcMath.Attention out=GmtHumanQcMath.Attention.UNASSESSABLE;
        for(GmtHumanQcMath.Attention v:values){
            if(v==null||v==GmtHumanQcMath.Attention.UNASSESSABLE)continue;
            any=true;
            if(v==GmtHumanQcMath.Attention.STRONG)return v;
            if(v==GmtHumanQcMath.Attention.CHECK)out=v;
            else if(out==GmtHumanQcMath.Attention.UNASSESSABLE)out=GmtHumanQcMath.Attention.CLEAR;
        }
        return any?out:GmtHumanQcMath.Attention.UNASSESSABLE;
    }

    /** Evaluate only measurements that survive the existing alpha70/alpha71 reliability gates. */
    static Assessment assess(Sub124060QcAnalyzer.Result r){
        Assessment a=new Assessment();
        if(r==null)return a;

        if(r.rotationWithheld==null&&Double.isFinite(r.rotationDeg)&&Boolean.TRUE.equals(r.rotationResizeStable))
            a.rotation=TWELVE_ROTATION.judge(r.rotationDeg);
        if(r.centringWithheld==null&&Double.isFinite(r.centringW)&&Boolean.TRUE.equals(r.centringResizeStable))
            a.centring=TWELVE_CENTRING.judge(r.centringW);

        // Relational calibration was performed on ellipse-corrected coordinates and requires a
        // reproducible edge-fitted dial. Never invent a verdict on a manual-circle fallback.
        if(r.frame==null||r.edge==null||!Boolean.TRUE.equals(r.dialReproducible))return a;

        double ref12=rectClock(r.frame,r.tick60);
        List<Double> rho=new ArrayList<>(),angleErrors=new ArrayList<>();
        for(Sub124060QcAnalyzer.Round q:r.rounds){
            GmtRoundMarkerAnalyzer.Marker m=q.marker;
            if(q.status!=Sub124060QcAnalyzer.Status.FOUND||m==null||!m.found||!Sub124060QcAnalyzer.roundOffsetRepeatable(m))continue;
            double[] p=rectNorm(r.frame,m.x,m.y);
            if(p==null)continue;
            rho.add(Math.hypot(p[0],p[1]));
            if(Double.isFinite(ref12))angleErrors.add(wrap180(rectClock(r.frame,m.x,m.y)-ref12-m.hour*30.0));
        }
        if(rho.size()>=4){
            a.roundRingRho=median(rho);
            a.roundRing=ROUND_RING_RHO.judge(a.roundRingRho);
        }
        if(angleErrors.size()>=4){
            a.roundSpacingRmsDeg=spacingRms(angleErrors);
            a.roundSpacing=ROUND_SPACING_RMS.judge(a.roundSpacingRmsDeg);
        }

        Sub124060QcAnalyzer.Baton b3=baton(r,GmtSixLandmarkAnalyzer.Position.THREE);
        Sub124060QcAnalyzer.Baton b6=baton(r,GmtSixLandmarkAnalyzer.Position.SIX);
        Sub124060QcAnalyzer.Baton b9=baton(r,GmtSixLandmarkAnalyzer.Position.NINE);

        double[] p3=batonPoint(r.frame,b3),p9=batonPoint(r.frame,b9);
        if(p3!=null&&p9!=null){
            a.baton39LineOffsetR=lineOffset(p3,p9);
            a.baton39=BATON_3_9_LINE_OFFSET.judge(a.baton39LineOffsetR);
        }

        double[] p12=r.triangle!=null&&r.twelveWithheld==null?rectNorm(r.frame,r.triangle.cx,r.triangle.cy):null;
        double[] p6=batonPoint(r.frame,b6);
        if(p12!=null&&p6!=null){
            a.axis126LineOffsetR=lineOffset(p12,p6);
            a.axis126=AXIS_12_6_LINE_OFFSET.judge(a.axis126LineOffsetR);
        }
        return a;
    }

    static Sub124060QcAnalyzer.Baton baton(Sub124060QcAnalyzer.Result r,GmtSixLandmarkAnalyzer.Position p){
        for(Sub124060QcAnalyzer.Baton b:r.batons)if(b.position==p)return b;
        return null;
    }

    static double[] batonPoint(GmtRoundMarkerAnalyzer.DialFrame f,Sub124060QcAnalyzer.Baton b){
        if(b==null||b.status!=Sub124060QcAnalyzer.Status.FOUND||b.result==null||!b.result.valid||!Sub124060QcAnalyzer.batonRepeatable(b.result))return null;
        GmtSixLandmarkAnalyzer.Geometry g=b.result.geometry;
        if(g==null||!g.outerEdge||g.outerLeft==null||g.outerRight==null||g.innerLeft==null||g.innerRight==null)return null;
        double x=(g.outerLeft[0]+g.outerRight[0]+g.innerLeft[0]+g.innerRight[0])/4.0;
        double y=(g.outerLeft[1]+g.outerRight[1]+g.innerLeft[1]+g.innerRight[1])/4.0;
        return rectNorm(f,x,y);
    }

    static double[] rectNorm(GmtRoundMarkerAnalyzer.DialFrame f,double x,double y){
        if(f==null||!(f.r>0)||!Double.isFinite(x)||!Double.isFinite(y))return null;
        double[] p=f.rect(x,y);return new double[]{p[0]/f.r,p[1]/f.r};
    }

    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double[] p){return p==null||p.length<2?Double.NaN:rectClock(f,p[0],p[1]);}
    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double x,double y){
        if(f==null)return Double.NaN;double[] p=f.rect(x,y);return Math.toDegrees(Math.atan2(p[0],-p[1]));
    }
    static double wrap180(double a){while(a<=-180)a+=360;while(a>180)a-=360;return a;}

    static double median(List<Double> values){
        List<Double> x=new ArrayList<>();for(Double v:values)if(v!=null&&Double.isFinite(v))x.add(v);
        if(x.isEmpty())return Double.NaN;Collections.sort(x);int n=x.size();return n%2==1?x.get(n/2):(x.get(n/2-1)+x.get(n/2))/2.0;
    }
    static double spacingRms(List<Double> values){
        List<Double> x=new ArrayList<>();for(Double v:values)if(v!=null&&Double.isFinite(v))x.add(v);
        if(x.size()<4)return Double.NaN;double mean=0;for(double v:x)mean+=v;mean/=x.size();double ss=0;for(double v:x){double d=v-mean;ss+=d*d;}return Math.sqrt(ss/x.size());
    }
    static double lineOffset(double[] p,double[] q){
        if(p==null||q==null||p.length<2||q.length<2)return Double.NaN;
        double dx=q[0]-p[0],dy=q[1]-p[1],d=Math.hypot(dx,dy);if(!(d>0))return Double.NaN;return Math.abs(p[0]*q[1]-q[0]*p[1])/d;
    }
}
