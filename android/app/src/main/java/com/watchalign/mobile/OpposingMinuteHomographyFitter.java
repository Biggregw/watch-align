package com.watchalign.mobile;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Camera-pose solver based only on the genuine dial's 60 fixed minute positions.
 *
 * The canonical master has exact 6 degree spacing and therefore exactly 30
 * opposing pairs: 0<->30, 1<->31, ... 29<->59. Normal minor ticks are located
 * from their inner ends. At the 12 hour positions (minute % 5 == 0) the detector
 * stays in the outer minute-track annulus and samples the printed hour-position
 * tick/stub there, deliberately away from the applied hour marker.
 *
 * Missing or weak ticks are never invented. Only complete observed opposing pairs
 * enter the perspective solve. If there are too few clean, well-distributed pairs,
 * the photo is rejected as unsuitable rather than falling back to a weaker fit.
 */
final class OpposingMinuteHomographyFitter {
    static final class Result {
        final Mat homography;
        final boolean accepted;
        final int detectedTicks;
        final int completePairs;
        final int inliers;
        final double seedRmsPx;
        final double fittedRmsPx;
        final String reason;

        Result(Mat h, boolean accepted, int detectedTicks, int completePairs,
               int inliers, double seedRmsPx, double fittedRmsPx, String reason) {
            this.homography = h;
            this.accepted = accepted;
            this.detectedTicks = detectedTicks;
            this.completePairs = completePairs;
            this.inliers = inliers;
            this.seedRmsPx = seedRmsPx;
            this.fittedRmsPx = fittedRmsPx;
            this.reason = reason;
        }
    }

    private static final double NORMAL_CANON_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double HOUR_CANON_R = Gmt126710BlnrMaster.HOUR_TICK_SAMPLE_R;

    private static final double SEARCH_R_MIN = NORMAL_CANON_R - 0.065;
    private static final double SEARCH_R_MAX = NORMAL_CANON_R + 0.035;
    private static final double SEARCH_R_STEP = 0.0015;
    private static final double SEARCH_A_DEG = 1.65;
    private static final double SEARCH_A_STEP_DEG = 0.12;
    private static final double EDGE_DELTA_R = 0.0065;
    private static final double BODY_DELTA_R = 0.017;
    private static final double MIN_EDGE_SCORE = 20.0;

    // Hour-position ticks are sampled only in the outer annulus. Tangential contrast
    // is used to find the printed tick while avoiding the much more inward hour marker.
    private static final double HOUR_SEARCH_A_DEG = 1.45;
    private static final double HOUR_SEARCH_A_STEP_DEG = 0.10;
    private static final double HOUR_SIDE_DEG = 1.05;
    private static final double HOUR_RADIAL_DELTA = 0.008;
    private static final double MIN_HOUR_SCORE = 15.0;

    // 30 opposing pairs exist. Eight clean, spread pairs means 16 real observations,
    // twice the mathematical minimum for a homography while still tolerating hands,
    // glare and the cyclops obscuring a substantial section of the minute track.
    private static final int MIN_COMPLETE_PAIRS = 8;
    private static final int MIN_FINAL_PAIRS = 8;
    private static final int MIN_INLIERS = 16;
    private static final int MIN_ORIENTATION_BINS = 4;
    private static final double RANSAC_PX = 1.85;

    private static final class Tick {
        final int minute;
        final Point canonical;
        final Point image;
        final double score;
        final boolean hourPosition;
        Tick(int minute, Point canonical, Point image, double score, boolean hourPosition) {
            this.minute = minute;
            this.canonical = canonical;
            this.image = image;
            this.score = score;
            this.hourPosition = hourPosition;
        }
    }

    private static final class Pair {
        final Tick a,b;
        Pair(Tick a,Tick b){this.a=a;this.b=b;}
        double confidence(){
            double q=Math.min(a.score,b.score);
            // Hour-position outer stubs are valid evidence but are deliberately given
            // a slightly more conservative vote than the full-length minor ticks.
            return (a.hourPosition||b.hourPosition)?q*0.82:q;
        }
    }

    /**
     * Alpha91 basin-only seed. This deliberately relaxes Alpha90's final acceptance
     * rules: its only job is to move the ellipse seed close enough for the full
     * 60-position lattice refiner. It still uses only printed minute-track evidence.
     * Applied hour markers, hands, text and date geometry never enter this estimate.
     */
    static Mat coarseSeed(Mat gray, Mat h0) {
        if (gray == null || gray.empty() || h0 == null || h0.empty()) return h0 == null ? null : h0.clone();

        Tick[] ticks = new Tick[60];
        for (int minute = 0; minute < 60; minute++) {
            Tick t = (minute % 5 == 0)
                    ? detectHourPositionTick(gray, h0, minute)
                    : detectNormalTickInnerEnd(gray, h0, minute);
            ticks[minute] = t;
        }

        List<Pair> pairs = new ArrayList<>();
        for (int minute = 0; minute < 30; minute++) {
            Tick a = ticks[minute], b = ticks[minute + 30];
            if (a != null && b != null) pairs.add(new Pair(a, b));
        }
        if (pairs.size() < 4 || orientationBins(pairs) < 3) return h0.clone();

        List<Point> src = new ArrayList<>(), dst = new ArrayList<>();
        flattenPairs(pairs, src, dst);
        MatOfPoint2f s = new MatOfPoint2f(), d = new MatOfPoint2f();
        Mat mask = new Mat();
        Mat rough = null;
        try {
            s.fromList(src);
            d.fromList(dst);
            rough = Calib3d.findHomography(s, d, Calib3d.RANSAC, RANSAC_PX, mask);
            if (rough == null || rough.empty()) {
                if (rough != null) rough.release();
                return h0.clone();
            }
            int inliers = 0;
            for (int i = 0; i < mask.rows(); i++) {
                double[] v = mask.get(i, 0);
                if (v != null && v.length > 0 && v[0] != 0) inliers++;
            }
            if (inliers < 8 || !finiteHomography(rough)) {
                rough.release();
                return h0.clone();
            }
            Mat out = rough.clone();
            rough.release();
            return out;
        } catch (Throwable ignored) {
            if (rough != null) rough.release();
            return h0.clone();
        } finally {
            mask.release();
            s.release();
            d.release();
        }
    }

    private static boolean finiteHomography(Mat h) {
        if (h == null || h.empty() || h.rows() != 3 || h.cols() != 3) return false;
        double[] m = new double[9];
        h.get(0, 0, m);
        if (m.length < 9 || !Double.isFinite(m[8]) || Math.abs(m[8]) < 1e-9) return false;
        for (double v : m) if (!Double.isFinite(v)) return false;
        return true;
    }

    private OpposingMinuteHomographyFitter() {}

    static Result fit(Mat gray, Mat h0) {
        if (gray == null || gray.empty() || h0 == null || h0.empty())
            return new Result(h0 == null ? null : h0.clone(), false, 0, 0, 0,
                    Double.NaN, Double.NaN, "missing image or seed pose");

        Tick[] ticks = new Tick[60];
        int detected = 0;
        for (int minute = 0; minute < 60; minute++) {
            Tick t=(minute%5==0)
                    ?detectHourPositionTick(gray,h0,minute)
                    :detectNormalTickInnerEnd(gray,h0,minute);
            if (t != null) { ticks[minute] = t; detected++; }
        }

        List<Pair> candidatePairs = new ArrayList<>();
        for (int minute = 0; minute < 30; minute++) {
            Tick a = ticks[minute], b = ticks[minute + 30];
            if (a != null && b != null) candidatePairs.add(new Pair(a,b));
        }
        if (candidatePairs.size() < MIN_COMPLETE_PAIRS || orientationBins(candidatePairs) < MIN_ORIENTATION_BINS) {
            return new Result(h0.clone(), false, detected, candidatePairs.size(), 0,
                    Double.NaN, Double.NaN,
                    "candidate photo has too few well-spread opposing minute pairs");
        }

        // Relative confidence gate. Weak or ambiguous pairs are dropped, never guessed.
        double[] conf = new double[candidatePairs.size()];
        for(int i=0;i<conf.length;i++) conf[i]=candidatePairs.get(i).confidence();
        Arrays.sort(conf);
        double medianConf = conf[conf.length/2];
        double confidenceFloor = Math.max(13.0, medianConf * 0.55);
        List<Pair> pairs = new ArrayList<>();
        for(Pair p:candidatePairs) if(p.confidence()>=confidenceFloor) pairs.add(p);
        if(pairs.size()<MIN_COMPLETE_PAIRS || orientationBins(pairs)<MIN_ORIENTATION_BINS) {
            return new Result(h0.clone(),false,detected,pairs.size(),0,
                    Double.NaN,Double.NaN,
                    "candidate photo quality insufficient after weak-pair rejection");
        }

        List<Point> src = new ArrayList<>(), dst = new ArrayList<>();
        flattenPairs(pairs,src,dst);
        double seedRms=rms(h0,src,dst);

        MatOfPoint2f srcPts=new MatOfPoint2f(),dstPts=new MatOfPoint2f();
        Mat mask=new Mat(); Mat robust=null; Mat finalFit=null;
        try{
            srcPts.fromList(src); dstPts.fromList(dst);
            robust=Calib3d.findHomography(srcPts,dstPts,Calib3d.RANSAC,RANSAC_PX,mask);
            if(robust==null||robust.empty()){
                if(robust!=null)robust.release();
                return new Result(h0.clone(),false,detected,pairs.size(),0,seedRms,Double.NaN,
                        "robust opposing-minute perspective solve failed");
            }

            // Derive an image-specific residual limit, then reject WHOLE opposing pairs.
            double[] residual=new double[src.size()];
            for(int i=0;i<src.size();i++) residual[i]=residual(robust,src.get(i),dst.get(i));
            double med=median(residual),mad=medianAbsDeviation(residual,med);
            double sigma=Math.max(0.10,1.4826*mad);
            double cutoff=Math.max(0.70,Math.min(2.25,med+2.6*sigma));

            List<Pair> cleanPairs=new ArrayList<>();
            for(int i=0;i<pairs.size();i++){
                double r0=residual[2*i],r1=residual[2*i+1];
                if(Double.isFinite(r0)&&Double.isFinite(r1)&&Math.max(r0,r1)<=cutoff)
                    cleanPairs.add(pairs.get(i));
            }
            if(cleanPairs.size()<MIN_FINAL_PAIRS || orientationBins(cleanPairs)<MIN_ORIENTATION_BINS){
                robust.release();
                return new Result(h0.clone(),false,detected,pairs.size(),cleanPairs.size()*2,
                        seedRms,Double.NaN,
                        "candidate photo has too few clean, well-spread opposing pairs");
            }

            // Fit on one subset and validate on held-out opposing pairs. This prevents the
            // final master from being accepted merely because a flexible transform reduced
            // error on every point it was allowed to see.
            List<Pair> fitPairs=new ArrayList<>(),holdoutPairs=new ArrayList<>();
            for(int i=0;i<cleanPairs.size();i++){
                if((i%3)==2)holdoutPairs.add(cleanPairs.get(i));else fitPairs.add(cleanPairs.get(i));
            }
            if(fitPairs.size()<6||holdoutPairs.size()<2){
                return new Result(h0.clone(),false,detected,pairs.size(),cleanPairs.size()*2,
                        seedRms,Double.NaN,"not enough opposing pairs for fit/holdout validation");
            }

            List<Point> fitSrc=new ArrayList<>(),fitDst=new ArrayList<>();
            flattenPairs(fitPairs,fitSrc,fitDst);
            MatOfPoint2f fs=new MatOfPoint2f(),fd=new MatOfPoint2f();
            try{
                fs.fromList(fitSrc);fd.fromList(fitDst);
                finalFit=Calib3d.findHomography(fs,fd,0);
            }finally{fs.release();fd.release();}
            if(finalFit==null||finalFit.empty()){
                if(finalFit!=null)finalFit.release();
                return new Result(h0.clone(),false,detected,pairs.size(),fitSrc.size(),
                        seedRms,Double.NaN,"opposing-minute perspective refit failed");
            }

            List<Point> holdSrc=new ArrayList<>(),holdDst=new ArrayList<>();
            flattenPairs(holdoutPairs,holdSrc,holdDst);
            double fitRms=rms(finalFit,fitSrc,fitDst);
            double holdRms=rms(finalFit,holdSrc,holdDst);
            double seedHoldRms=rms(h0,holdSrc,holdDst);
            int inliers=fitSrc.size()+holdSrc.size();

            boolean enough=inliers>=MIN_INLIERS;
            boolean fitImproves=Double.isFinite(seedRms)&&Double.isFinite(fitRms)
                    && fitRms<seedRms*0.82&&fitRms+0.35<seedRms;
            boolean holdoutGood=Double.isFinite(seedHoldRms)&&Double.isFinite(holdRms)
                    && holdRms<Math.max(2.25,seedHoldRms*0.90);
            if(!enough||!fitImproves||!holdoutGood){
                finalFit.release();
                return new Result(h0.clone(),false,detected,pairs.size(),inliers,seedRms,fitRms,
                        !enough?"too few clean opposing-minute observations":
                                (!fitImproves?"minute-pair pose did not materially improve the seed":
                                        "held-out opposing pairs did not confirm the perspective"));
            }

            Mat out=finalFit.clone();
            finalFit.release();
            return new Result(out,true,detected,cleanPairs.size(),inliers,seedRms,fitRms,"");
        }finally{
            if(finalFit!=null&&!finalFit.empty())finalFit.release();
            if(robust!=null&&!robust.empty())robust.release();
            mask.release();srcPts.release();dstPts.release();
        }
    }

    /** Six 30-degree orientation sectors across the first half of the dial. */
    private static int orientationBins(List<Pair> pairs){
        boolean[] hit=new boolean[6];
        for(Pair p:pairs){
            int m=p.a.minute;
            int bin=Math.max(0,Math.min(5,m/5));
            hit[bin]=true;
        }
        int n=0;for(boolean b:hit)if(b)n++;return n;
    }

    private static void flattenPairs(List<Pair> pairs,List<Point> src,List<Point> dst){
        for(Pair p:pairs){
            src.add(p.a.canonical);dst.add(p.a.image);
            src.add(p.b.canonical);dst.add(p.b.image);
        }
    }

    private static Tick detectNormalTickInnerEnd(Mat gray,Mat h0,int minute){
        double baseA=Gmt126710BlnrMaster.angleForMinute(minute);
        double best=-Double.MAX_VALUE,bestR=Double.NaN,bestA=Double.NaN;
        for(double daDeg=-SEARCH_A_DEG;daDeg<=SEARCH_A_DEG+1e-9;daDeg+=SEARCH_A_STEP_DEG){
            double a=baseA+Math.toRadians(daDeg);
            for(double r=SEARCH_R_MIN;r<=SEARCH_R_MAX+1e-9;r+=SEARCH_R_STEP){
                double s=normalEdgeScore(gray,h0,r,a);
                if(Double.isFinite(s)&&s>best){best=s;bestR=r;bestA=a;}
            }
        }
        if(!(best>=MIN_EDGE_SCORE)||!Double.isFinite(bestR))return null;

        double rm=normalEdgeScore(gray,h0,bestR-SEARCH_R_STEP,bestA);
        double r0=normalEdgeScore(gray,h0,bestR,bestA);
        double rp=normalEdgeScore(gray,h0,bestR+SEARCH_R_STEP,bestA);
        bestR+=parabolicOffset(rm,r0,rp,SEARCH_R_STEP);

        double da=Math.toRadians(SEARCH_A_STEP_DEG);
        double am=normalEdgeScore(gray,h0,bestR,bestA-da);
        double a0=normalEdgeScore(gray,h0,bestR,bestA);
        double ap=normalEdgeScore(gray,h0,bestR,bestA+da);
        bestA+=parabolicOffset(am,a0,ap,da);
        best=normalEdgeScore(gray,h0,bestR,bestA);
        if(!(best>=MIN_EDGE_SCORE))return null;

        Point actual=project(h0,bestR*Math.cos(bestA),bestR*Math.sin(bestA));
        Point canonical=new Point(NORMAL_CANON_R*Math.cos(baseA),NORMAL_CANON_R*Math.sin(baseA));
        return actual==null?null:new Tick(minute,canonical,actual,best,false);
    }

    /**
     * Detect an hour-position minute mark using only the outer minute-track annulus.
     * We sample a fixed genuine radius that lies outside every applied hour marker,
     * then search only for the narrow angular bright ridge of the printed tick/stub.
     */
    private static Tick detectHourPositionTick(Mat gray,Mat h0,int minute){
        double baseA=Gmt126710BlnrMaster.angleForMinute(minute);
        double best=-Double.MAX_VALUE,bestA=Double.NaN;
        for(double daDeg=-HOUR_SEARCH_A_DEG;daDeg<=HOUR_SEARCH_A_DEG+1e-9;daDeg+=HOUR_SEARCH_A_STEP_DEG){
            double a=baseA+Math.toRadians(daDeg);
            double s=hourStubScore(gray,h0,a);
            if(Double.isFinite(s)&&s>best){best=s;bestA=a;}
        }
        if(!(best>=MIN_HOUR_SCORE)||!Double.isFinite(bestA))return null;

        double da=Math.toRadians(HOUR_SEARCH_A_STEP_DEG);
        double am=hourStubScore(gray,h0,bestA-da);
        double a0=hourStubScore(gray,h0,bestA);
        double ap=hourStubScore(gray,h0,bestA+da);
        bestA+=parabolicOffset(am,a0,ap,da);
        best=hourStubScore(gray,h0,bestA);
        if(!(best>=MIN_HOUR_SCORE))return null;

        Point actual=project(h0,HOUR_CANON_R*Math.cos(bestA),HOUR_CANON_R*Math.sin(bestA));
        Point canonical=new Point(HOUR_CANON_R*Math.cos(baseA),HOUR_CANON_R*Math.sin(baseA));
        return actual==null?null:new Tick(minute,canonical,actual,best,true);
    }

    private static double normalEdgeScore(Mat gray,Mat h,double r,double a){
        if(r<SEARCH_R_MIN-0.01||r>SEARCH_R_MAX+0.01)return Double.NaN;
        double ca=Math.cos(a),sa=Math.sin(a);
        double inner=intensity(gray,h,(r-EDGE_DELTA_R)*ca,(r-EDGE_DELTA_R)*sa);
        double outer=intensity(gray,h,(r+EDGE_DELTA_R)*ca,(r+EDGE_DELTA_R)*sa);
        double body=intensity(gray,h,(r+BODY_DELTA_R)*ca,(r+BODY_DELTA_R)*sa);
        if(!Double.isFinite(inner)||!Double.isFinite(outer)||!Double.isFinite(body))return Double.NaN;
        return 0.65*(outer-inner)+0.35*(body-inner);
    }

    private static double hourStubScore(Mat gray,Mat h,double a){
        double side=Math.toRadians(HOUR_SIDE_DEG);
        double r=HOUR_CANON_R;
        double c0=intensity(gray,h,r*Math.cos(a),r*Math.sin(a));
        double cm=intensity(gray,h,r*Math.cos(a-side),r*Math.sin(a-side));
        double cp=intensity(gray,h,r*Math.cos(a+side),r*Math.sin(a+side));
        double ri=intensity(gray,h,(r-HOUR_RADIAL_DELTA)*Math.cos(a),(r-HOUR_RADIAL_DELTA)*Math.sin(a));
        double ro=intensity(gray,h,(r+HOUR_RADIAL_DELTA)*Math.cos(a),(r+HOUR_RADIAL_DELTA)*Math.sin(a));
        if(!Double.isFinite(c0)||!Double.isFinite(cm)||!Double.isFinite(cp)||!Double.isFinite(ri)||!Double.isFinite(ro))
            return Double.NaN;
        double angularRidge=c0-0.5*(cm+cp);
        double radialSupport=Math.min(c0,0.5*(ri+ro));
        return angularRidge+0.12*radialSupport;
    }

    private static double parabolicOffset(double ym,double y0,double yp,double step){
        if(!Double.isFinite(ym)||!Double.isFinite(y0)||!Double.isFinite(yp))return 0.0;
        double den=ym-2.0*y0+yp;
        if(!(den<-1e-6))return 0.0;
        double off=0.5*(ym-yp)/den*step;
        return Math.max(-0.75*step,Math.min(0.75*step,off));
    }

    private static double intensity(Mat gray,Mat h,double x,double y){
        Point p=project(h,x,y);
        if(p==null||p.x<1||p.y<1||p.x>=gray.cols()-2||p.y>=gray.rows()-2)return Double.NaN;
        int x0=(int)Math.floor(p.x),y0=(int)Math.floor(p.y);
        double fx=p.x-x0,fy=p.y-y0;
        double d00=gray.get(y0,x0)[0],d10=gray.get(y0,x0+1)[0];
        double d01=gray.get(y0+1,x0)[0],d11=gray.get(y0+1,x0+1)[0];
        return (d00*(1-fx)+d10*fx)*(1-fy)+(d01*(1-fx)+d11*fx)*fy;
    }

    private static Point project(Mat h,double x,double y){
        double[] m=new double[9];h.get(0,0,m);double w=m[6]*x+m[7]*y+m[8];
        if(Math.abs(w)<1e-9)return null;
        return new Point((m[0]*x+m[1]*y+m[2])/w,(m[3]*x+m[4]*y+m[5])/w);
    }

    private static double residual(Mat h,Point src,Point dst){
        Point p=project(h,src.x,src.y);if(p==null)return Double.NaN;
        return Math.hypot(p.x-dst.x,p.y-dst.y);
    }

    private static double rms(Mat h,List<Point> src,List<Point> dst){
        double ss=0;int n=0;
        for(int i=0;i<src.size();i++){
            double r=residual(h,src.get(i),dst.get(i));if(!Double.isFinite(r))continue;
            ss+=r*r;n++;
        }
        return n==0?Double.NaN:Math.sqrt(ss/n);
    }

    private static double median(double[] v){
        double[] a=Arrays.stream(v).filter(Double::isFinite).toArray();
        if(a.length==0)return Double.NaN;Arrays.sort(a);
        return a.length%2==1?a[a.length/2]:0.5*(a[a.length/2-1]+a[a.length/2]);
    }

    private static double medianAbsDeviation(double[] v,double med){
        if(!Double.isFinite(med))return Double.NaN;
        double[] a=Arrays.stream(v).filter(Double::isFinite).map(x->Math.abs(x-med)).toArray();
        return median(a);
    }
}
