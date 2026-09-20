package com.watchalign.mobile;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Conservative rescue selector for minute-track acquisition.
 *
 * The normal MinuteTrackDialFinder remains authoritative. This selector is only
 * called after the complete normal automatic overlay has rejected its pose. It
 * therefore cannot replace or perturb a pose that the existing pipeline already
 * accepts.
 *
 * Rescue candidates are fitted with two disjoint minute-tick groups, ranked on
 * a third group, and validated on a fourth held-out group. A 3 degree half-pitch
 * probe rejects continuous rehaut/crystal rings that happen to score well on a
 * distance transform. Finally, 12/6/9 must contain plausible bright marker
 * structure at the candidate pose. Applied markers are a rescue sanity gate only;
 * they never move, resize or rotate the candidate.
 */
final class MinuteTrackAcquisitionRescue {
    private static final double TRACK_R=Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double TICK_RADIAL_HALF=0.025;
    private static final double TICK_ANGULAR_HALF=Math.toRadians(0.34);
    private static final double LOSS_CAP_PX=10.0;
    private static final int MAX_SHAPE_CANDIDATES=14;
    private static final int REFINE_TOP=5;
    private static final double HALF_PITCH_DEG=3.0;

    static final class Candidate {
        final RotatedRect ellipse;
        final double rollDeg;
        final int shapeIndex;
        final int coarseOrder;
        final double fitMedianPx;
        final double selectMedianPx;
        final double selectP90Px;
        final double periodicContrastPx;
        final double pairedFraction;
        final double validationMedianPx;
        final double validationP90Px;
        final double validationInlierFraction;
        final double validationMedianLimitPx;
        final double validationP90LimitPx;
        final double validationInlierLimitPx;
        final boolean validationAccepted;
        final boolean semanticAccepted;
        final double semanticDialMedian;
        final double selectObjective;

        Candidate(RotatedRect ellipse,double roll,int shapeIndex,int coarseOrder,
                  double fit,double selectMedian,double selectP90,double periodic,
                  double paired,double validationMedian,double validationP90,
                  double validationInlier,double validationMedianLimit,
                  double validationP90Limit,double validationInlierLimit,
                  boolean validationAccepted,boolean semanticAccepted,
                  double semanticDialMedian,double objective){
            this.ellipse=ellipse;this.rollDeg=roll;this.shapeIndex=shapeIndex;
            this.coarseOrder=coarseOrder;this.fitMedianPx=fit;
            this.selectMedianPx=selectMedian;this.selectP90Px=selectP90;
            this.periodicContrastPx=periodic;this.pairedFraction=paired;
            this.validationMedianPx=validationMedian;this.validationP90Px=validationP90;
            this.validationInlierFraction=validationInlier;
            this.validationMedianLimitPx=validationMedianLimit;
            this.validationP90LimitPx=validationP90Limit;
            this.validationInlierLimitPx=validationInlierLimit;
            this.validationAccepted=validationAccepted;this.semanticAccepted=semanticAccepted;
            this.semanticDialMedian=semanticDialMedian;this.selectObjective=objective;
        }
    }

    static final class Result {
        final Candidate candidate;
        final int shapeCandidates;
        final int refinedCandidates;
        Result(Candidate candidate,int shapes,int refined){
            this.candidate=candidate;this.shapeCandidates=shapes;this.refinedCandidates=refined;
        }
    }

    private static final class CoarseCandidate {
        final RotatedRect source;
        final int shapeIndex;
        final double scale,roll,score;
        CoarseCandidate(RotatedRect source,int shapeIndex,double scale,double roll,double score){
            this.source=source;this.shapeIndex=shapeIndex;this.scale=scale;this.roll=roll;this.score=score;
        }
    }

    private static final class Validation {
        final double median,p90,inlier,medianLimit,p90Limit,inlierLimit;
        final boolean accepted;
        Validation(double median,double p90,double inlier,double medianLimit,
                   double p90Limit,double inlierLimit,boolean accepted){
            this.median=median;this.p90=p90;this.inlier=inlier;
            this.medianLimit=medianLimit;this.p90Limit=p90Limit;
            this.inlierLimit=inlierLimit;this.accepted=accepted;
        }
    }

    private static final class Semantic {
        final boolean accepted;
        final double dialMedian;
        Semantic(boolean accepted,double dialMedian){this.accepted=accepted;this.dialMedian=dialMedian;}
    }

    @SuppressWarnings("unchecked")
    static Result find(Mat edges,Mat gray,double seedX,double seedY,double seedR){
        if(edges==null||edges.empty()||gray==null||gray.empty()||!(seedR>20.0))return null;
        Mat distance=null;
        try{
            Method shapesMethod=MinuteTrackDialFinder.class.getDeclaredMethod(
                    "findConcentricShapes",Mat.class,double.class,double.class,double.class);
            shapesMethod.setAccessible(true);
            List<Object> shapes=(List<Object>)shapesMethod.invoke(null,edges,seedX,seedY,seedR);
            if(shapes==null||shapes.isEmpty())return null;

            distance=distanceField(edges);
            List<CoarseCandidate> coarse=new ArrayList<>();
            int count=Math.min(MAX_SHAPE_CANDIDATES,shapes.size());
            for(int i=0;i<count;i++){
                RotatedRect ellipse=(RotatedRect)field(shapes.get(i),"ellipse");
                CoarseCandidate c=coarseSearch(distance,ellipse,i);
                if(c!=null)coarse.add(c);
            }
            if(coarse.isEmpty())return null;
            Collections.sort(coarse,Comparator.comparingDouble(c->c.score));

            Candidate best=null;
            int refined=Math.min(REFINE_TOP,coarse.size());
            for(int order=0;order<refined;order++){
                CoarseCandidate fine=fineSearch(distance,coarse.get(order));
                RotatedRect ellipse=scaledEllipse(fine.source,fine.scale);
                MinuteTrackDialFinder.TopPhaseResult top=
                        MinuteTrackDialFinder.anchorToImageUp(ellipse,fine.roll);
                ellipse=refineCentre(distance,ellipse,top.anchoredRollDeg);
                top=MinuteTrackDialFinder.anchorToImageUp(ellipse,top.anchoredRollDeg);
                if(!top.accepted)continue;

                double fit=fitMedianGroups(distance,ellipse,top.anchoredRollDeg,0,1);
                List<Double> select=tickScoresForGroup(distance,ellipse,top.anchoredRollDeg,3,0.0);
                List<Double> off=tickScoresForGroup(distance,ellipse,top.anchoredRollDeg,3,HALF_PITCH_DEG);
                if(select.isEmpty()||off.size()!=select.size())continue;
                Collections.sort(select);
                double selectMedian=percentileSorted(select,0.50);
                double selectP90=percentileSorted(select,0.90);

                List<Double> diffs=new ArrayList<>();
                List<Double> offSorted=new ArrayList<>(off);
                for(int i=0;i<off.size();i++)diffs.add(off.get(i)-tickScoresForMinuteGroupValue(
                        distance,ellipse,top.anchoredRollDeg,3,i,0.0));
                Collections.sort(offSorted);
                double offMedian=percentileSorted(offSorted,0.50);
                double periodic=offMedian-selectMedian;
                int paired=0;for(double d:diffs)if(d>0.35)paired++;
                double pairedFraction=paired/(double)Math.max(1,diffs.size());
                double objective=selectObjective(selectMedian,selectP90,periodic,pairedFraction);

                Validation validation=validateHeldout(distance,ellipse,top.anchoredRollDeg);
                if(!validation.accepted)continue;
                Semantic semantic=semanticGate(gray,ellipse,top.anchoredRollDeg);
                if(!semantic.accepted)continue;

                Candidate c=new Candidate(ellipse,top.anchoredRollDeg,fine.shapeIndex,order,
                        fit,selectMedian,selectP90,periodic,pairedFraction,
                        validation.median,validation.p90,validation.inlier,
                        validation.medianLimit,validation.p90Limit,validation.inlierLimit,
                        true,true,semantic.dialMedian,objective);
                if(best==null||c.selectObjective<best.selectObjective)best=c;
            }
            return best==null?null:new Result(best,count,refined);
        }catch(Throwable ignored){
            return null;
        }finally{
            if(distance!=null)distance.release();
        }
    }

    static boolean shouldUseRescue(boolean primaryAccepted,boolean rescueTopAccepted,
                                   boolean rescueHeldoutAccepted,boolean semanticAccepted){
        return !primaryAccepted&&rescueTopAccepted&&rescueHeldoutAccepted&&semanticAccepted;
    }

    static double selectObjective(double median,double p90,double periodic,double pairedFraction){
        double periodicReward=0.10*Math.max(0.0,periodic)*Math.min(1.0,pairedFraction/0.50);
        return median+0.22*p90-periodicReward;
    }

    private static CoarseCandidate coarseSearch(Mat distance,RotatedRect ellipse,int shapeIndex){
        CoarseCandidate best=null;
        for(double scale=0.68;scale<=1.32+1e-9;scale+=0.04){
            for(double roll=-15.0;roll<=15.0+1e-9;roll+=1.0){
                double score=fitMedianFastGroups(distance,ellipse,scale,roll,0,1);
                if(best==null||score<best.score)best=new CoarseCandidate(ellipse,shapeIndex,scale,roll,score);
            }
        }
        return best;
    }

    private static CoarseCandidate fineSearch(Mat distance,CoarseCandidate coarse){
        RotatedRect coarseEllipse=scaledEllipse(coarse.source,coarse.scale);
        double bestScore=fitMedianGroups(distance,coarseEllipse,coarse.roll,0,1);
        CoarseCandidate best=new CoarseCandidate(coarse.source,coarse.shapeIndex,
                coarse.scale,coarse.roll,bestScore);
        double minScale=Math.max(0.62,coarse.scale-0.06),maxScale=Math.min(1.38,coarse.scale+0.06);
        for(double scale=minScale;scale<=maxScale+1e-9;scale+=0.005){
            for(double roll=coarse.roll-1.5;roll<=coarse.roll+1.5+1e-9;roll+=0.10){
                if(Math.abs(roll)>16.0)continue;
                RotatedRect e=scaledEllipse(coarse.source,scale);
                double score=fitMedianGroups(distance,e,roll,0,1);
                if(score<best.score)best=new CoarseCandidate(coarse.source,coarse.shapeIndex,scale,roll,score);
            }
        }
        return best;
    }

    private static RotatedRect refineCentre(Mat distance,RotatedRect ellipse,double roll){
        RotatedRect best=ellipse;
        double bestScore=fitMedianGroups(distance,ellipse,roll,0,1);
        double radius=dialRadius(ellipse);
        double step=Math.max(0.75,radius*0.008);
        for(int ix=-2;ix<=2;ix++)for(int iy=-2;iy<=2;iy++){
            if(ix==0&&iy==0)continue;
            RotatedRect c=new RotatedRect(new Point(ellipse.center.x+ix*step,ellipse.center.y+iy*step),
                    new Size(ellipse.size.width,ellipse.size.height),ellipse.angle);
            double score=fitMedianGroups(distance,c,roll,0,1);
            if(score<bestScore){bestScore=score;best=c;}
        }
        return best;
    }

    private static double fitMedianFastGroups(Mat distance,RotatedRect ellipse,double scale,
                                              double roll,int groupA,int groupB){
        List<Double> scores=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0)continue;
            int group=minute%4;if(group!=groupA&&group!=groupB)continue;
            double angle=Math.toRadians(minute*6.0-90.0);
            double sum=0.0;int n=0;
            for(double r:new double[]{TRACK_R-TICK_RADIAL_HALF,TRACK_R+TICK_RADIAL_HALF}){
                Point p=map(ellipse,scale,roll,r*Math.cos(angle),r*Math.sin(angle));
                sum+=sample(distance,p.x,p.y);n++;
            }
            for(double a:new double[]{angle-TICK_ANGULAR_HALF,angle+TICK_ANGULAR_HALF}){
                Point p=map(ellipse,scale,roll,TRACK_R*Math.cos(a),TRACK_R*Math.sin(a));
                sum+=sample(distance,p.x,p.y);n++;
            }
            scores.add(sum/Math.max(1,n));
        }
        Collections.sort(scores);return percentileSorted(scores,0.50);
    }

    private static double fitMedianGroups(Mat distance,RotatedRect ellipse,double roll,
                                          int groupA,int groupB){
        List<Double> scores=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0)continue;
            int group=minute%4;if(group!=groupA&&group!=groupB)continue;
            double angle=Math.toRadians(minute*6.0-90.0);
            scores.add(tickScore(distance,ellipse,roll,angle));
        }
        Collections.sort(scores);return percentileSorted(scores,0.50);
    }

    private static List<Double> tickScoresForGroup(Mat distance,RotatedRect ellipse,double roll,
                                                    int group,double offsetDeg){
        List<Double> scores=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0||minute%4!=group)continue;
            double angle=Math.toRadians(minute*6.0-90.0+offsetDeg);
            scores.add(tickScore(distance,ellipse,roll,angle));
        }
        return scores;
    }

    private static double tickScoresForMinuteGroupValue(Mat distance,RotatedRect ellipse,double roll,
                                                         int group,int ordinal,double offsetDeg){
        int n=0;
        for(int minute=0;minute<60;minute++){
            if(minute%5==0||minute%4!=group)continue;
            if(n++!=ordinal)continue;
            double angle=Math.toRadians(minute*6.0-90.0+offsetDeg);
            return tickScore(distance,ellipse,roll,angle);
        }
        return LOSS_CAP_PX;
    }

    private static Validation validateHeldout(Mat distance,RotatedRect ellipse,double roll){
        List<Double> scores=tickScoresForGroup(distance,ellipse,roll,2,0.0);
        if(scores.isEmpty())return new Validation(LOSS_CAP_PX,LOSS_CAP_PX,0,0,0,0,false);
        Collections.sort(scores);
        double median=percentileSorted(scores,0.50),p90=percentileSorted(scores,0.90);
        double radius=Math.max(60.0,dialRadius(ellipse));
        double medianLimit=Math.max(1.5,radius*0.015);
        double p90Limit=Math.max(3.0,radius*0.032);
        double inlierLimit=Math.max(2.0,radius*0.022);
        int inliers=0;for(double v:scores)if(v<=inlierLimit)inliers++;
        double fraction=inliers/(double)scores.size();
        boolean accepted=scores.size()>=8&&median<=medianLimit&&p90<=p90Limit&&fraction>=0.65;
        return new Validation(median,p90,fraction,medianLimit,p90Limit,inlierLimit,accepted);
    }

    private static double tickScore(Mat distance,RotatedRect ellipse,double roll,double angle){
        double sum=0.0;int count=0;
        for(double side:new double[]{-TICK_ANGULAR_HALF,TICK_ANGULAR_HALF}){
            for(int i=0;i<4;i++){
                double r=TRACK_R-TICK_RADIAL_HALF+2.0*TICK_RADIAL_HALF*i/3.0;
                Point p=map(ellipse,1.0,roll,r*Math.cos(angle+side),r*Math.sin(angle+side));
                sum+=sample(distance,p.x,p.y);count++;
            }
        }
        for(double r:new double[]{TRACK_R-TICK_RADIAL_HALF,TRACK_R+TICK_RADIAL_HALF}){
            for(int i=-1;i<=1;i++){
                double a=angle+i*TICK_ANGULAR_HALF;
                Point p=map(ellipse,1.0,roll,r*Math.cos(a),r*Math.sin(a));
                sum+=sample(distance,p.x,p.y);count++;
            }
        }
        return sum/Math.max(1,count);
    }

    private static Semantic semanticGate(Mat gray,RotatedRect ellipse,double roll){
        double dialMedian=dialInteriorMedian(gray,ellipse,roll);
        boolean ok=Double.isFinite(dialMedian)&&dialMedian<=135.0
                &&markerGood(gray,ellipse,roll,12)
                &&markerGood(gray,ellipse,roll,6)
                &&markerGood(gray,ellipse,roll,9);
        return new Semantic(ok,dialMedian);
    }

    private static double dialInteriorMedian(Mat gray,RotatedRect ellipse,double roll){
        List<Double> values=new ArrayList<>();
        for(int ri=0;ri<8;ri++){
            double r=0.32+(0.60-0.32)*ri/7.0;
            for(int ai=0;ai<72;ai++){
                double a=2.0*Math.PI*ai/72.0;
                Point p=map(ellipse,1.0,roll,r*Math.cos(a),r*Math.sin(a));
                double v=sampleGray(gray,p.x,p.y);if(Double.isFinite(v))values.add(v);
            }
        }
        Collections.sort(values);return values.isEmpty()?Double.NaN:percentileSorted(values,0.50);
    }

    private static boolean markerGood(Mat gray,RotatedRect ellipse,double roll,int hour){
        double centerR=hour==12?0.729:0.677;
        double radialHalf=hour==12?0.15:0.14;
        double tangHalf=hour==12?0.11:0.075;
        double angle=Gmt126710BlnrMaster.angleForHour(hour);
        double ux=Math.cos(angle),uy=Math.sin(angle),vx=-uy,vy=ux;
        double cx=centerR*ux,cy=centerR*uy;
        double radialExtent=radialHalf*1.45,tangExtent=tangHalf*1.80;
        int rows=72,cols=72;
        Mat patch=new Mat(rows,cols,CvType.CV_8UC1);
        List<Double> core=new ArrayList<>(),surround=new ArrayList<>();
        try{
            for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
                double u=-radialExtent+2.0*radialExtent*y/(rows-1.0);
                double v=-tangExtent+2.0*tangExtent*x/(cols-1.0);
                double px=cx+ux*u+vx*v,py=cy+uy*u+vy*v;
                Point p=map(ellipse,1.0,roll,px,py);
                double value=sampleGray(gray,p.x,p.y);
                if(!Double.isFinite(value))value=0.0;
                patch.put(y,x,value);
                boolean inCore=Math.abs(u)<=radialHalf&&Math.abs(v)<=tangHalf;
                if(inCore)core.add(value);else surround.add(value);
            }
            if(core.isEmpty()||surround.isEmpty())return false;
            Collections.sort(core);Collections.sort(surround);
            double bg=percentileSorted(surround,0.50);
            double p90=percentileSorted(core,0.90);
            double contrast=p90-bg;
            double brightThreshold=Math.max(150.0,bg+45.0);
            int bright=0;for(double v:core)if(v>=brightThreshold)bright++;
            double brightFraction=bright/(double)core.size();

            double threshold=Math.max(150.0,Math.min(220.0,bg+45.0));
            Mat bw=new Mat();
            try{
                Imgproc.threshold(patch,bw,threshold,255.0,Imgproc.THRESH_BINARY);
                Mat labels=new Mat(),stats=new Mat(),centroids=new Mat();
                try{
                    int components=Imgproc.connectedComponentsWithStats(bw,labels,stats,centroids,8,CvType.CV_32S);
                    double largest=0.0;
                    for(int i=1;i<components;i++){
                        double[] a=stats.get(i,Imgproc.CC_STAT_AREA);
                        if(a!=null&&a.length>0)largest=Math.max(largest,a[0]);
                    }
                    double areaFraction=largest/Math.max(1.0,core.size());
                    return p90>=165.0&&contrast>=35.0&&(brightFraction>=0.05||areaFraction>=0.025);
                }finally{labels.release();stats.release();centroids.release();}
            }finally{bw.release();}
        }finally{patch.release();}
    }

    private static RotatedRect scaledEllipse(RotatedRect e,double scale){
        return new RotatedRect(new Point(e.center.x,e.center.y),
                new Size(e.size.width*scale,e.size.height*scale),e.angle);
    }

    private static double dialRadius(RotatedRect e){
        return (Math.max(e.size.width,e.size.height)+Math.min(e.size.width,e.size.height))/4.0;
    }

    private static Point map(RotatedRect e,double scale,double rollDeg,double x,double y){
        double roll=Math.toRadians(rollDeg),cr=Math.cos(roll),sr=Math.sin(roll);
        double xr=cr*x-sr*y,yr=sr*x+cr*y;
        double axis=Math.toRadians(e.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double localX=ca*xr+sa*yr,localY=-sa*xr+ca*yr;
        double rx=e.size.width*0.5*scale,ry=e.size.height*0.5*scale;
        double sx=rx*localX,sy=ry*localY;
        return new Point(e.center.x+ca*sx-sa*sy,e.center.y+sa*sx+ca*sy);
    }

    private static Mat distanceField(Mat edges){
        Mat inverted=new Mat(),distance=new Mat();
        Imgproc.threshold(edges,inverted,0.0,255.0,Imgproc.THRESH_BINARY_INV);
        Imgproc.distanceTransform(inverted,distance,Imgproc.DIST_L2,Imgproc.DIST_MASK_PRECISE);
        inverted.release();return distance;
    }

    private static double sample(Mat distance,double x,double y){
        if(x<1||y<1||x>=distance.cols()-1||y>=distance.rows()-1)return LOSS_CAP_PX;
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double fx=x-x0,fy=y-y0;
        double d00=distance.get(y0,x0)[0],d10=distance.get(y0,x0+1)[0];
        double d01=distance.get(y0+1,x0)[0],d11=distance.get(y0+1,x0+1)[0];
        double d=(d00*(1.0-fx)+d10*fx)*(1.0-fy)+(d01*(1.0-fx)+d11*fx)*fy;
        return Math.min(LOSS_CAP_PX,Math.max(0.0,d));
    }

    private static double sampleGray(Mat gray,double x,double y){
        if(x<1||y<1||x>=gray.cols()-1||y>=gray.rows()-1)return Double.NaN;
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double fx=x-x0,fy=y-y0;
        double[] a=gray.get(y0,x0),b=gray.get(y0,x0+1),c=gray.get(y0+1,x0),d=gray.get(y0+1,x0+1);
        if(a==null||b==null||c==null||d==null)return Double.NaN;
        return (a[0]*(1.0-fx)+b[0]*fx)*(1.0-fy)+(c[0]*(1.0-fx)+d[0]*fx)*fy;
    }

    private static double percentileSorted(List<Double> sorted,double q){
        if(sorted.isEmpty())return LOSS_CAP_PX;
        double pos=q*(sorted.size()-1),f=pos-Math.floor(pos);
        int lo=(int)Math.floor(pos),hi=(int)Math.ceil(pos);
        if(lo==hi)return sorted.get(lo);
        return sorted.get(lo)*(1.0-f)+sorted.get(hi)*f;
    }

    private static Object field(Object o,String name)throws Exception{
        Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
    }

    private MinuteTrackAcquisitionRescue(){}
}
