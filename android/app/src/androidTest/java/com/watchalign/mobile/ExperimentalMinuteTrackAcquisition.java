package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Research-only acquisition emulator. Production code is deliberately untouched. */
final class ExperimentalMinuteTrackAcquisition {
    static final class Candidate {
        final int coarseOrder;
        final int shapeIndex;
        final RotatedRect ellipse;
        final double rollDeg;
        final double fitMedianPx;
        final double halfPitchMedianPx;
        final double periodicContrastPx;
        final double pairedFraction;
        final double coarseScore;
        Candidate(int coarseOrder,int shapeIndex,RotatedRect ellipse,double roll,double fit,
                  double halfPitch,double contrast,double paired,double coarseScore){
            this.coarseOrder=coarseOrder;this.shapeIndex=shapeIndex;this.ellipse=ellipse;
            this.rollDeg=roll;this.fitMedianPx=fit;this.halfPitchMedianPx=halfPitch;
            this.periodicContrastPx=contrast;this.pairedFraction=paired;this.coarseScore=coarseScore;
        }
    }

    static final class Result {
        final Candidate currentLike;
        final Candidate proposed;
        final int refinedCandidates;
        Result(Candidate currentLike,Candidate proposed,int n){
            this.currentLike=currentLike;this.proposed=proposed;this.refinedCandidates=n;
        }
    }

    private static final double HALF_PITCH_DEG=3.0;
    private static final int REFINE_TOP=5;

    @SuppressWarnings("unchecked")
    static Result find(Mat edges,double seedX,double seedY,double seedR)throws Exception{
        Method shapesM=method("findConcentricShapes",Mat.class,double.class,double.class,double.class);
        List<Object> shapes=(List<Object>)shapesM.invoke(null,edges,seedX,seedY,seedR);
        if(shapes==null||shapes.isEmpty())return null;

        Method distanceM=method("distanceField",Mat.class);
        Mat distance=(Mat)distanceM.invoke(null,edges);
        try{
            Method coarseM=method("coarseSearch",Mat.class,RotatedRect.class);
            List<Object[]> coarse=new ArrayList<>();
            int count=Math.min(14,shapes.size());
            for(int i=0;i<count;i++){
                Object shape=shapes.get(i);
                RotatedRect ellipse=(RotatedRect)field(shape,"ellipse");
                Object pose=coarseM.invoke(null,distance,ellipse);
                if(pose==null)continue;
                double score=num(pose,"score");
                coarse.add(new Object[]{score,i,pose});
            }
            if(coarse.isEmpty())return null;
            Collections.sort(coarse,Comparator.comparingDouble(a->(Double)a[0]));

            Class<?> poseClass=coarse.get(0)[2].getClass();
            Method fineM=method("fineSearch",Mat.class,poseClass);
            Method refineCentreM=method("refineCentre",Mat.class,RotatedRect.class,double.class);
            Method fitMedianM=method("fitMedian",Mat.class,RotatedRect.class,double.class,boolean.class);
            Method tickScoreM=method("tickScore",Mat.class,RotatedRect.class,double.class,double.class);

            List<Candidate> refined=new ArrayList<>();
            for(int order=0;order<Math.min(REFINE_TOP,coarse.size());order++){
                Object[] row=coarse.get(order);
                Object fine=fineM.invoke(null,distance,row[2]);
                RotatedRect source=(RotatedRect)field(fine,"source");
                double scale=num(fine,"scale"),roll=num(fine,"roll");
                RotatedRect e=new RotatedRect(source.center,
                        new Size(source.size.width*scale,source.size.height*scale),source.angle);
                MinuteTrackDialFinder.TopPhaseResult top=MinuteTrackDialFinder.anchorToImageUp(e,roll);
                e=(RotatedRect)refineCentreM.invoke(null,distance,e,top.anchoredRollDeg);
                top=MinuteTrackDialFinder.anchorToImageUp(e,top.anchoredRollDeg);
                double fit=(Double)fitMedianM.invoke(null,distance,e,top.anchoredRollDeg,false);
                Periodic periodic=periodic(distance,e,top.anchoredRollDeg,tickScoreM);
                refined.add(new Candidate(order,(Integer)row[1],e,top.anchoredRollDeg,fit,
                        periodic.offMedian,periodic.contrast,periodic.paired,(Double)row[0]));
            }
            if(refined.isEmpty())return null;

            Candidate currentLike=refined.get(0);
            Candidate proposed=Collections.min(refined,Comparator.comparingDouble(c->c.fitMedianPx));

            // A raw distance transform can mistake a continuous rehaut/crystal ring for the
            // minute track. If the best-fit candidate has little 6-degree discrimination,
            // prefer a nearby-fit candidate that is strongly worse at half-pitch positions.
            boolean weak=proposed.periodicContrastPx<0.30&&proposed.pairedFraction<0.35;
            if(weak){
                Candidate rescue=null;
                for(Candidate c:refined){
                    boolean strong=c.periodicContrastPx>=0.60&&c.pairedFraction>=0.45;
                    boolean closeFit=c.fitMedianPx<=proposed.fitMedianPx+0.20;
                    if(strong&&closeFit&&(rescue==null||periodicScore(c)>periodicScore(rescue)))rescue=c;
                }
                if(rescue!=null)proposed=rescue;
            }
            return new Result(currentLike,proposed,refined.size());
        }finally{distance.release();}
    }

    private static double periodicScore(Candidate c){
        return c.periodicContrastPx+0.75*c.pairedFraction-0.20*c.fitMedianPx;
    }

    private static final class Periodic {
        final double offMedian,contrast,paired;
        Periodic(double off,double contrast,double paired){this.offMedian=off;this.contrast=contrast;this.paired=paired;}
    }

    private static Periodic periodic(Mat distance,RotatedRect e,double roll,Method tickScoreM)throws Exception{
        List<Double> on=new ArrayList<>(),off=new ArrayList<>(),diff=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0)continue;
            double angle=Math.toRadians(minute*6.0-90.0);
            double a=(Double)tickScoreM.invoke(null,distance,e,roll,angle);
            double b=(Double)tickScoreM.invoke(null,distance,e,roll,angle+Math.toRadians(HALF_PITCH_DEG));
            on.add(a);off.add(b);diff.add(b-a);
        }
        Collections.sort(on);Collections.sort(off);
        int paired=0;for(double d:diff)if(d>0.35)paired++;
        double onMed=median(on),offMed=median(off);
        return new Periodic(offMed,offMed-onMed,paired/(double)Math.max(1,diff.size()));
    }

    private static double median(List<Double> x){
        if(x.isEmpty())return 10.0;int n=x.size();return n%2==1?x.get(n/2):(x.get(n/2-1)+x.get(n/2))*0.5;
    }

    private static Method method(String name,Class<?>...types)throws Exception{
        Method m=MinuteTrackDialFinder.class.getDeclaredMethod(name,types);m.setAccessible(true);return m;
    }
    private static Object field(Object o,String name)throws Exception{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }
    private static double num(Object o,String name)throws Exception{return ((Number)field(o,name)).doubleValue();}
    private ExperimentalMinuteTrackAcquisition(){}
}
