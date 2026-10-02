package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative family-specific alignment calibration for the Rolex Submariner 124060.
 *
 * Source: Watch-family Calibrator run 37054338303 (2026-10-02). The run acquired 40 genuine
 * 124060 watches from six sources and fitted the production envelope from every reliable genuine
 * photo measurement across development, validation and holdout. No cross-watch statistical
 * trimming is used. Only an obvious photo-level failure within the same physical watch may be
 * removed; this run rejected zero such measurements for all seven metrics.
 *
 * Product rule: a reliable value already observed on a genuine watch is accepted genuine variation
 * for replica QC, including a small genuine imperfection. CHECK begins only beyond the retained
 * genuine envelope plus the repeatability guard. Replica evidence never moves a genuine-derived
 * boundary. The six current replica stress watches did not separate on these seven metrics, so the
 * bands are deliberately conservative rather than claims of authenticity or Rolex tolerances.
 */
final class Sub124060Calibration {
    /** Single switch: no CLEAR/CHECK/CHECK CLOSELY verdict reaches the user while this is false. */
    static final boolean VERDICTS_ENABLED=true;
    static final String MEASURED_ONLY="MEASURED / NOT YET JUDGED";
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
        GmtHumanQcMath.Attention gap=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention roundRing=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention roundSpacing=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention baton39=GmtHumanQcMath.Attention.UNASSESSABLE;
        GmtHumanQcMath.Attention axis126=GmtHumanQcMath.Attention.UNASSESSABLE;
        double roundRingRho=Double.NaN,roundSpacingRmsDeg=Double.NaN;
        double baton39LineOffsetR=Double.NaN,axis126LineOffsetR=Double.NaN;
        /** A reliable value exists (passed the production gates), whether or not it was judged. */
        boolean rotationMeasured,gapMeasured,centringMeasured,roundRingMeasured,roundSpacingMeasured,baton39Measured,axis126Measured;

        boolean twelveMeasured(){return rotationMeasured||gapMeasured||centringMeasured||axis126Measured;}
        boolean roundsMeasured(){return roundRingMeasured||roundSpacingMeasured;}
        boolean batonMeasured(String label){
            if("3".equals(label)||"9".equals(label))return baton39Measured;
            if("6".equals(label))return axis126Measured;
            return false;
        }

        GmtHumanQcMath.Attention twelve(){return worst(rotation,gap,centring,axis126);}
        GmtHumanQcMath.Attention rounds(){return worst(roundRing,roundSpacing);}
        GmtHumanQcMath.Attention baton(String label){
            if("3".equals(label)||"9".equals(label))return baton39;
            if("6".equals(label))return axis126;
            return GmtHumanQcMath.Attention.UNASSESSABLE;
        }
    }

    // Run 37054338303, all-reliable-genuine-photo envelope. CLEAR includes every retained genuine
    // observation plus one repeatability guard. CHECK is the next guard-width outside CLEAR.
    static final Band TWELVE_ROTATION=new Band("twelve.rotation_deg",
            -1.438497,3.555616,-1.638497,3.755616);
    static final boolean GAP_JUDGED=true;
    static final boolean RING_JUDGED=true;
    static final Band TWELVE_GAP=new Band("twelve.gap_r",
            0.0155867,0.0463993,0.0131864,0.0487996);
    static final Band TWELVE_CENTRING=new Band("twelve.centring_w",
            -0.080044,0.050926,-0.090044,0.060926);
    static final Band ROUND_RING_RHO=new Band("round.ring_rho",
            0.784912,0.827718,0.782912,0.829718);
    /** One-sided: an RMS cannot be negative, so only an upper warning boundary exists. */
    static final Band ROUND_SPACING_RMS=new Band("round.spacing_rms_deg",
            Double.NEGATIVE_INFINITY,4.033474,Double.NEGATIVE_INFINITY,4.133474);
    /** One-sided: an absolute offset cannot be negative. */
    static final Band BATON_3_9_LINE_OFFSET=new Band("baton.3_9_line_offset_r",
            Double.NEGATIVE_INFINITY,0.023287,Double.NEGATIVE_INFINITY,0.025287);
    /** One-sided: an absolute offset cannot be negative. */
    static final Band AXIS_12_6_LINE_OFFSET=new Band("axis.12_6_line_offset_r",
            Double.NEGATIVE_INFINITY,0.014611,Double.NEGATIVE_INFINITY,0.016611);

    private Sub124060Calibration(){}

    static String words(GmtHumanQcMath.Attention a){
        switch(a){
            case CLEAR:return "CLEAR";
            case CHECK:return "CHECK";
            case STRONG:return "CHECK CLOSELY";
            default:return "NOT JUDGED";
        }
    }

    /** User-facing word for a metric: the verdict if judged, otherwise whether a reliable value exists. */
    static String label(boolean measured,GmtHumanQcMath.Attention a){
        if(a!=null&&a!=GmtHumanQcMath.Attention.UNASSESSABLE)return words(a);
        return measured?MEASURED_ONLY:"NOT JUDGED";
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

    /** Evaluate only measurements that survive the existing production reliability gates. */
    static Assessment assess(Sub124060QcAnalyzer.Result r){return assess(r,VERDICTS_ENABLED);}

    /** judge=false computes the same gated values but leaves every verdict UNASSESSABLE. */
    static Assessment assess(Sub124060QcAnalyzer.Result r,boolean judge){
        Assessment a=new Assessment();
        if(r==null)return a;

        if(r.rotationWithheld==null&&Double.isFinite(r.rotationDeg)&&Boolean.TRUE.equals(r.rotationResizeStable)){
            a.rotationMeasured=true;if(judge)a.rotation=TWELVE_ROTATION.judge(r.rotationDeg);
        }
        if(r.gapWithheld==null&&Double.isFinite(r.gapR)&&Boolean.TRUE.equals(r.gapResizeStable)){
            a.gapMeasured=true;if(judge&&GAP_JUDGED)a.gap=TWELVE_GAP.judge(r.gapR);
        }
        if(r.centringWithheld==null&&Double.isFinite(r.centringW)&&Boolean.TRUE.equals(r.centringResizeStable)){
            a.centringMeasured=true;if(judge)a.centring=TWELVE_CENTRING.judge(r.centringW);
        }

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
            a.roundRingRho=median(rho);a.roundRingMeasured=true;
            if(judge&&RING_JUDGED)a.roundRing=ROUND_RING_RHO.judge(a.roundRingRho);
        }
        if(angleErrors.size()>=4){
            a.roundSpacingRmsDeg=spacingRms(angleErrors);a.roundSpacingMeasured=Double.isFinite(a.roundSpacingRmsDeg);
            if(judge)a.roundSpacing=ROUND_SPACING_RMS.judge(a.roundSpacingRmsDeg);
        }

        Sub124060QcAnalyzer.Baton b3=baton(r,GmtSixLandmarkAnalyzer.Position.THREE);
        Sub124060QcAnalyzer.Baton b6=baton(r,GmtSixLandmarkAnalyzer.Position.SIX);
        Sub124060QcAnalyzer.Baton b9=baton(r,GmtSixLandmarkAnalyzer.Position.NINE);

        double[] p3=batonPoint(r.frame,b3),p9=batonPoint(r.frame,b9);
        if(p3!=null&&p9!=null){
            a.baton39LineOffsetR=lineOffset(p3,p9);a.baton39Measured=Double.isFinite(a.baton39LineOffsetR);
            if(judge&&BATON_3_9_LINE_OFFSET!=null)a.baton39=BATON_3_9_LINE_OFFSET.judge(a.baton39LineOffsetR);
        }

        double[] p12=r.triangle!=null&&r.twelveWithheld==null?rectNorm(r.frame,r.triangle.cx,r.triangle.cy):null;
        double[] p6=batonPoint(r.frame,b6);
        if(p12!=null&&p6!=null){
            a.axis126LineOffsetR=lineOffset(p12,p6);a.axis126Measured=Double.isFinite(a.axis126LineOffsetR);
            if(judge)a.axis126=AXIS_12_6_LINE_OFFSET.judge(a.axis126LineOffsetR);
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
