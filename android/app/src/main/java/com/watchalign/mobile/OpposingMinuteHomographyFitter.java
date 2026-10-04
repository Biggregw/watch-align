package com.watchalign.mobile;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Perspective proof based on explicit minor-minute correspondences.
 *
 * Alpha84 precision path:
 *  - detect the actual INNER end of every non-hour minute tick;
 *  - refine each detection below the coarse search-grid spacing with local
 *    parabolic interpolation in radius and angle;
 *  - admit only complete opposing pairs i <-> i+30;
 *  - reject weak pairs relative to the image's own confidence distribution;
 *  - obtain a robust RANSAC seed;
 *  - reject a whole opposing pair if either side has a large reprojection residual;
 *  - refit the final homography by least squares on the surviving complete pairs.
 *
 * Applied hour markers, 12 triangle, date/cyclops, text and hands are never used,
 * so they remain independent visual checks of the resulting perspective overlay.
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

    private static final double TRUE_INNER_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double SEARCH_R_MIN = TRUE_INNER_R - 0.065;
    private static final double SEARCH_R_MAX = TRUE_INNER_R + 0.035;
    private static final double SEARCH_R_STEP = 0.0015;
    private static final double SEARCH_A_DEG = 1.65;
    private static final double SEARCH_A_STEP_DEG = 0.12;
    private static final double EDGE_DELTA_R = 0.0065;
    private static final double BODY_DELTA_R = 0.017;
    private static final double MIN_EDGE_SCORE = 20.0;
    private static final int MIN_COMPLETE_PAIRS = 7;
    private static final int MIN_FINAL_PAIRS = 6;
    private static final int MIN_INLIERS = 12;
    private static final double RANSAC_PX = 1.85;

    private static final class Tick {
        final int minute;
        final Point canonical;
        final Point image;
        final double score;
        Tick(int minute, Point canonical, Point image, double score) {
            this.minute = minute; this.canonical = canonical; this.image = image; this.score = score;
        }
    }

    private static final class Pair {
        final Tick a,b;
        Pair(Tick a,Tick b){this.a=a;this.b=b;}
        double confidence(){return Math.min(a.score,b.score);}
    }

    private OpposingMinuteHomographyFitter() {}

    static Result fit(Mat gray, Mat h0) {
        if (gray == null || gray.empty() || h0 == null || h0.empty())
            return new Result(h0 == null ? null : h0.clone(), false, 0, 0, 0,
                    Double.NaN, Double.NaN, "missing image or seed pose");

        Tick[] ticks = new Tick[60];
        int detected = 0;
        for (int minute = 0; minute < 60; minute++) {
            if (minute % 5 == 0) continue;
            Tick t = detectTickInnerEnd(gray, h0, minute);
            if (t != null) { ticks[minute] = t; detected++; }
        }

        List<Pair> candidatePairs = new ArrayList<>();
        for (int minute = 0; minute < 30; minute++) {
            if (minute % 5 == 0) continue;
            Tick a = ticks[minute], b = ticks[minute + 30];
            if (a != null && b != null) candidatePairs.add(new Pair(a,b));
        }
        if (candidatePairs.size() < MIN_COMPLETE_PAIRS) {
            return new Result(h0.clone(), false, detected, candidatePairs.size(), 0,
                    Double.NaN, Double.NaN, "not enough opposing minor-minute pairs");
        }

        // Relative confidence gate. A weak reflection/hand edge should not survive just
        // because it barely passed the fixed threshold on an otherwise very sharp image.
        double[] conf = new double[candidatePairs.size()];
        for(int i=0;i<conf.length;i++) conf[i]=candidatePairs.get(i).confidence();
        Arrays.sort(conf);
        double medianConf = conf[conf.length/2];
        double confidenceFloor = Math.max(MIN_EDGE_SCORE, medianConf * 0.58);
        List<Pair> pairs = new ArrayList<>();
        for(Pair p:candidatePairs) if(p.confidence()>=confidenceFloor) pairs.add(p);
        if(pairs.size()<MIN_COMPLETE_PAIRS) pairs=candidatePairs; // fail soft, RANSAC still protects us

        List<Point> src = new ArrayList<>(), dst = new ArrayList<>();
        flattenPairs(pairs,src,dst);
        double seedRms=rms(h0,src,dst,null);

        MatOfPoint2f srcPts=new MatOfPoint2f(),dstPts=new MatOfPoint2f();
        Mat mask=new Mat(); Mat robust=null; Mat finalFit=null;
        try{
            srcPts.fromList(src); dstPts.fromList(dst);
            robust=Calib3d.findHomography(srcPts,dstPts,Calib3d.RANSAC,RANSAC_PX,mask);
            if(robust==null||robust.empty()){
                if(robust!=null)robust.release();
                return new Result(h0.clone(),false,detected,pairs.size(),0,seedRms,Double.NaN,
                        "robust minute homography failed");
            }

            // Compute per-point residuals from the robust model and derive an image-specific
            // cutoff using median/MAD. Reject WHOLE opposite pairs, never one side alone.
            double[] residual=new double[src.size()];
            for(int i=0;i<src.size();i++) residual[i]=residual(robust,src.get(i),dst.get(i));
            double med=median(residual),mad=medianAbsDeviation(residual,med);
            double sigma=Math.max(0.10,1.4826*mad);
            double cutoff=Math.max(0.70,Math.min(2.25,med+2.6*sigma));

            List<Pair> cleanPairs=new ArrayList<>();
            for(int i=0;i<pairs.size();i++){
                double r0=residual[2*i],r1=residual[2*i+1];
                if(Double.isFinite(r0)&&Double.isFinite(r1)&&Math.max(r0,r1)<=cutoff) cleanPairs.add(pairs.get(i));
            }
            if(cleanPairs.size()<MIN_FINAL_PAIRS){
                robust.release();
                return new Result(h0.clone(),false,detected,pairs.size(),cleanPairs.size()*2,
                        seedRms,Double.NaN,"too few clean opposing pairs after precision rejection");
            }

            List<Point> cleanSrc=new ArrayList<>(),cleanDst=new ArrayList<>();
            flattenPairs(cleanPairs,cleanSrc,cleanDst);
            MatOfPoint2f cs=new MatOfPoint2f(),cd=new MatOfPoint2f();
            try{
                cs.fromList(cleanSrc);cd.fromList(cleanDst);
                // Least-squares polish after robust pair-level rejection. No judged feature enters.
                finalFit=Calib3d.findHomography(cs,cd,0);
            }finally{cs.release();cd.release();}
            if(finalFit==null||finalFit.empty()){
                if(finalFit!=null)finalFit.release();
                robust.release();
                return new Result(h0.clone(),false,detected,pairs.size(),cleanSrc.size(),
                        seedRms,Double.NaN,"precision minute refit failed");
            }

            double fitRms=rms(finalFit,cleanSrc,cleanDst,null);
            int inliers=cleanSrc.size();
            boolean enough=inliers>=MIN_INLIERS;
            boolean improves=Double.isFinite(seedRms)&&Double.isFinite(fitRms)
                    && fitRms<seedRms*0.78&&fitRms+0.45<seedRms;
            if(!enough||!improves){
                finalFit.release();robust.release();
                return new Result(h0.clone(),false,detected,pairs.size(),inliers,seedRms,fitRms,
                        !enough?"too few precision minute inliers":"precision minute fit did not materially improve seed pose");
            }

            Mat out=finalFit.clone();
            finalFit.release();robust.release();
            return new Result(out,true,detected,pairs.size(),inliers,seedRms,fitRms,"");
        }finally{
            if(finalFit!=null&&!finalFit.empty())finalFit.release();
            if(robust!=null&&!robust.empty())robust.release();
            mask.release();srcPts.release();dstPts.release();
        }
    }

    private static void flattenPairs(List<Pair> pairs,List<Point> src,List<Point> dst){
        for(Pair p:pairs){src.add(p.a.canonical);dst.add(p.a.image);src.add(p.b.canonical);dst.add(p.b.image);}
    }

    private static Tick detectTickInnerEnd(Mat gray,Mat h0,int minute){
        double baseA=Math.toRadians(minute*6.0-90.0);
        double best=-Double.MAX_VALUE,bestR=Double.NaN,bestA=Double.NaN;
        for(double daDeg=-SEARCH_A_DEG;daDeg<=SEARCH_A_DEG+1e-9;daDeg+=SEARCH_A_STEP_DEG){
            double a=baseA+Math.toRadians(daDeg);
            for(double r=SEARCH_R_MIN;r<=SEARCH_R_MAX+1e-9;r+=SEARCH_R_STEP){
                double s=edgeScore(gray,h0,r,a);
                if(s>best){best=s;bestR=r;bestA=a;}
            }
        }
        if(!(best>=MIN_EDGE_SCORE)||!Double.isFinite(bestR))return null;

        // Sub-grid refinement. A quadratic peak estimate removes most of the visible
        // one-to-two-pixel quantisation produced by the Alpha83 radial/angular grid.
        double rm=edgeScore(gray,h0,bestR-SEARCH_R_STEP,bestA);
        double r0=edgeScore(gray,h0,bestR,bestA);
        double rp=edgeScore(gray,h0,bestR+SEARCH_R_STEP,bestA);
        bestR+=parabolicOffset(rm,r0,rp,SEARCH_R_STEP);

        double da=Math.toRadians(SEARCH_A_STEP_DEG);
        double am=edgeScore(gray,h0,bestR,bestA-da);
        double a0=edgeScore(gray,h0,bestR,bestA);
        double ap=edgeScore(gray,h0,bestR,bestA+da);
        bestA+=parabolicOffset(am,a0,ap,da);
        best=edgeScore(gray,h0,bestR,bestA);
        if(!(best>=MIN_EDGE_SCORE))return null;

        Point actual=project(h0,bestR*Math.cos(bestA),bestR*Math.sin(bestA));
        Point canonical=new Point(TRUE_INNER_R*Math.cos(baseA),TRUE_INNER_R*Math.sin(baseA));
        return actual==null?null:new Tick(minute,canonical,actual,best);
    }

    private static double edgeScore(Mat gray,Mat h,double r,double a){
        if(r<SEARCH_R_MIN-0.01||r>SEARCH_R_MAX+0.01)return Double.NaN;
        double ca=Math.cos(a),sa=Math.sin(a);
        double inner=intensity(gray,h,(r-EDGE_DELTA_R)*ca,(r-EDGE_DELTA_R)*sa);
        double outer=intensity(gray,h,(r+EDGE_DELTA_R)*ca,(r+EDGE_DELTA_R)*sa);
        double body=intensity(gray,h,(r+BODY_DELTA_R)*ca,(r+BODY_DELTA_R)*sa);
        if(!Double.isFinite(inner)||!Double.isFinite(outer)||!Double.isFinite(body))return Double.NaN;
        return 0.65*(outer-inner)+0.35*(body-inner);
    }

    private static double parabolicOffset(double ym,double y0,double yp,double step){
        if(!Double.isFinite(ym)||!Double.isFinite(y0)||!Double.isFinite(yp))return 0.0;
        double den=ym-2.0*y0+yp;
        if(!(den<-1e-6))return 0.0; // only refine a genuine local maximum
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

    private static double rms(Mat h,List<Point> src,List<Point> dst,boolean[] keep){
        double ss=0;int n=0;
        for(int i=0;i<src.size();i++){
            if(keep!=null&&!keep[i])continue;
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
