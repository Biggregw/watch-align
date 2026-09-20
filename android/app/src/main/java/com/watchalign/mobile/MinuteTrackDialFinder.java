package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Finds GMT dial geometry from the minute track first, then validates the next
 * strong concentric boundary outward as the dial edge. The minute track owns
 * centre, scale and phase. The outward ring is validation evidence only.
 */
final class MinuteTrackDialFinder {
    private static final double TRACK_R = Gmt126710BlnrMaster.MINUTE_TRACK_R;
    private static final double TICK_RADIAL_HALF = 0.025;
    private static final double TICK_ANGULAR_HALF = Math.toRadians(0.34);
    private static final double LOSS_CAP_PX = 10.0;
    private static final int MAX_SHAPE_CANDIDATES = 14;
    private static final double TOP_PHASE_LIMIT_DEG = 3.10;

    static final class Result {
        final RotatedRect dialEllipse;
        final double rollDeg;
        final double fitMedianPx;
        final double outerBoundaryRadius;
        final double outerBoundaryMedianPx;
        final double outerBoundaryP90Px;
        final int candidateCount;
        final boolean boundaryConfirmed;
        final double topPhaseErrorDeg;
        final boolean topPhaseAccepted;
        final boolean usable;

        Result(RotatedRect ellipse,double roll,double fit,double boundaryRadius,
               double boundaryMedian,double boundaryP90,int candidates,
               boolean confirmed,double topError,boolean topAccepted,boolean usable){
            this.dialEllipse=ellipse;
            this.rollDeg=roll;
            this.fitMedianPx=fit;
            this.outerBoundaryRadius=boundaryRadius;
            this.outerBoundaryMedianPx=boundaryMedian;
            this.outerBoundaryP90Px=boundaryP90;
            this.candidateCount=candidates;
            this.boundaryConfirmed=confirmed;
            this.topPhaseErrorDeg=topError;
            this.topPhaseAccepted=topAccepted;
            this.usable=usable;
        }
    }

    static final class TopPhaseResult {
        final double anchoredRollDeg;
        final double errorDeg;
        final boolean accepted;
        TopPhaseResult(double roll,double error,boolean accepted){
            this.anchoredRollDeg=roll;this.errorDeg=error;this.accepted=accepted;
        }
    }

    private static final class ShapeCandidate {
        final RotatedRect ellipse;
        final double rank;
        ShapeCandidate(RotatedRect ellipse,double rank){this.ellipse=ellipse;this.rank=rank;}
    }

    private static final class PoseCandidate {
        final RotatedRect source;
        final double scale;
        final double roll;
        final double score;
        PoseCandidate(RotatedRect source,double scale,double roll,double score){
            this.source=source;this.scale=scale;this.roll=roll;this.score=score;
        }
    }

    private static final class BoundaryResult {
        final double radius;
        final double median;
        final double p90;
        BoundaryResult(double radius,double median,double p90){
            this.radius=radius;this.median=median;this.p90=p90;
        }
    }

    static Result find(Mat edges,double seedX,double seedY,double seedR){
        if(edges==null||edges.empty()||!(seedR>20.0))return null;
        List<ShapeCandidate> shapes=findConcentricShapes(edges,seedX,seedY,seedR);
        if(shapes.isEmpty())return null;

        Mat distance=distanceField(edges);
        try{
            PoseCandidate best=null;
            int count=Math.min(MAX_SHAPE_CANDIDATES,shapes.size());
            for(int i=0;i<count;i++){
                PoseCandidate candidate=coarseSearch(distance,shapes.get(i).ellipse);
                if(candidate!=null&&(best==null||candidate.score<best.score))best=candidate;
            }
            if(best==null)return null;

            best=fineSearch(distance,best);
            RotatedRect trackBased=scaledEllipse(best.source,best.scale);

            // Minor ticks repeat every 6 degrees. QC photos are defined to be upright, so
            // resolve that periodic ambiguity by declaring the tick nearest image-up to be 12.
            TopPhaseResult topPhase=anchorToImageUp(trackBased,best.roll);
            trackBased=refineCentre(distance,trackBased,topPhase.anchoredRollDeg);
            topPhase=anchorToImageUp(trackBased,topPhase.anchoredRollDeg);
            double trackMedian=fitMedian(distance,trackBased,topPhase.anchoredRollDeg,false);

            BoundaryResult boundary=findNextOuterBoundary(distance,trackBased,topPhase.anchoredRollDeg);
            if(boundary==null)return new Result(trackBased,topPhase.anchoredRollDeg,trackMedian,Double.NaN,
                    LOSS_CAP_PX,LOSS_CAP_PX,count,false,topPhase.errorDeg,topPhase.accepted,false);

            double dialRadius=(Math.max(trackBased.size.width,trackBased.size.height)
                    +Math.min(trackBased.size.width,trackBased.size.height))/4.0;
            double boundaryMedianLimit=Math.max(2.8,dialRadius*0.020);
            double boundaryP90Limit=Math.max(5.0,dialRadius*0.040);
            boolean boundaryConfirmed=Math.abs(boundary.radius-1.0)<=0.035
                    &&boundary.median<=boundaryMedianLimit
                    &&boundary.p90<=boundaryP90Limit;

            // Scale is locked by the minute track. The outward ring validates the predicted
            // dial edge but is never allowed to resize the master.
            RotatedRect finalEllipse=finalEllipseFromTrack(trackBased,boundary.radius);
            double finalMedian=fitMedian(distance,finalEllipse,topPhase.anchoredRollDeg,false);
            double finalRadius=(Math.max(finalEllipse.size.width,finalEllipse.size.height)
                    +Math.min(finalEllipse.size.width,finalEllipse.size.height))/4.0;
            double tickLimit=Math.max(2.2,finalRadius*0.020);
            boolean usable=topPhase.accepted&&boundaryConfirmed&&finalMedian<=tickLimit;

            return new Result(finalEllipse,topPhase.anchoredRollDeg,finalMedian,boundary.radius,
                    boundary.median,boundary.p90,count,boundaryConfirmed,
                    topPhase.errorDeg,topPhase.accepted,usable);
        }finally{
            distance.release();
        }
    }

    /** Mathematical conversion used by diagnostics/tests. */
    static double expectedDialRadiusFromTrackRadius(double trackRadius){
        return trackRadius/TRACK_R;
    }

    /** The outward boundary is validation only and must not perturb minute-track scale. */
    static RotatedRect finalEllipseFromTrack(RotatedRect trackBased,double ignoredBoundaryRadius){
        if(trackBased==null)return null;
        return new RotatedRect(new Point(trackBased.center.x,trackBased.center.y),
                new Size(trackBased.size.width,trackBased.size.height),trackBased.angle);
    }

    /**
     * Resolve the 6 degree periodic tick ambiguity using the QC-photo contract: the image is
     * upright and the true 12 minute-track tick is the tick nearest image-up.
     */
    static TopPhaseResult anchorToImageUp(RotatedRect ellipse,double rollDeg){
        if(ellipse==null)return new TopPhaseResult(rollDeg,180.0,false);
        double bestRoll=rollDeg,bestError=Double.POSITIVE_INFINITY;
        boolean bestUpper=false;
        for(int k=-10;k<=10;k++){
            double candidate=rollDeg+6.0*k;
            Point p=map(ellipse,1.0,candidate,0.0,-1.0);
            double dx=p.x-ellipse.center.x,dy=p.y-ellipse.center.y;
            double len=Math.hypot(dx,dy);
            if(len<1e-9)continue;
            double dot=Math.max(-1.0,Math.min(1.0,(-dy)/len));
            double error=Math.toDegrees(Math.acos(dot));
            boolean upper=p.y<ellipse.center.y;
            if(error<bestError-1e-9 ||
                    (Math.abs(error-bestError)<=1e-9&&Math.abs(normalizeDegrees(candidate))<Math.abs(normalizeDegrees(bestRoll)))){
                bestError=error;bestRoll=candidate;bestUpper=upper;
            }
        }
        bestRoll=normalizeDegrees(bestRoll);
        boolean accepted=bestUpper&&Double.isFinite(bestError)&&bestError<=TOP_PHASE_LIMIT_DEG;
        return new TopPhaseResult(bestRoll,bestError,accepted);
    }

    private static double normalizeDegrees(double degrees){
        double d=degrees%360.0;
        if(d>180.0)d-=360.0;
        if(d<=-180.0)d+=360.0;
        return d;
    }

    private static List<ShapeCandidate> findConcentricShapes(Mat edges,double seedX,double seedY,double seedR){
        List<MatOfPoint> contours=new ArrayList<>();
        Mat hierarchy=new Mat(),input=edges.clone();
        List<ShapeCandidate> out=new ArrayList<>();
        try{
            Imgproc.findContours(input,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            double minDim=Math.max(1.0,Math.min(edges.cols(),edges.rows()));
            double minDiameter=Math.max(70.0,minDim*0.10);
            double maxDiameter=minDim*0.94;
            double centreTolerance=Math.max(seedR*0.48,minDim*0.11);
            for(MatOfPoint contour:contours){
                if(contour.rows()<32)continue;
                Point[] points=contour.toArray();
                MatOfPoint2f f=new MatOfPoint2f(points);
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);
                    double major=Math.max(e.size.width,e.size.height);
                    double minor=Math.min(e.size.width,e.size.height);
                    if(major<minDiameter||major>maxDiameter||minor<minDiameter*0.70)continue;
                    if(major<0.75*seedR||major>3.20*seedR)continue;
                    double axisRatio=minor/Math.max(1.0,major);
                    if(axisRatio<0.66)continue;
                    double centreDistance=Math.hypot(e.center.x-seedX,e.center.y-seedY);
                    if(centreDistance>centreTolerance)continue;
                    double coverage=angularCoverage(e,points,24);
                    if(coverage<0.28)continue;
                    double centrePenalty=centreDistance/Math.max(1.0,centreTolerance);
                    double shapePenalty=Math.max(0.0,0.76-axisRatio);
                    double rank=1.8*centrePenalty+1.4*(1.0-coverage)+1.5*shapePenalty;
                    out.add(new ShapeCandidate(e,rank));
                }catch(Throwable ignored){}finally{f.release();}
            }
            Collections.sort(out,Comparator.comparingDouble(c->c.rank));
            return deduplicate(out);
        }finally{
            input.release();hierarchy.release();for(MatOfPoint c:contours)c.release();
        }
    }

    private static List<ShapeCandidate> deduplicate(List<ShapeCandidate> source){
        List<ShapeCandidate> out=new ArrayList<>();
        for(ShapeCandidate candidate:source){
            boolean duplicate=false;
            for(ShapeCandidate kept:out){
                double r=(Math.max(kept.ellipse.size.width,kept.ellipse.size.height)
                        +Math.min(kept.ellipse.size.width,kept.ellipse.size.height))/4.0;
                double dc=Math.hypot(candidate.ellipse.center.x-kept.ellipse.center.x,
                        candidate.ellipse.center.y-kept.ellipse.center.y);
                double da=Math.abs(Math.max(candidate.ellipse.size.width,candidate.ellipse.size.height)
                        -Math.max(kept.ellipse.size.width,kept.ellipse.size.height));
                double db=Math.abs(Math.min(candidate.ellipse.size.width,candidate.ellipse.size.height)
                        -Math.min(kept.ellipse.size.width,kept.ellipse.size.height));
                if(dc<Math.max(2.0,r*0.025)&&da<Math.max(3.0,r*0.04)&&db<Math.max(3.0,r*0.04)){
                    duplicate=true;break;
                }
            }
            if(!duplicate)out.add(candidate);
            if(out.size()>=MAX_SHAPE_CANDIDATES*2)break;
        }
        return out;
    }

    private static double angularCoverage(RotatedRect e,Point[] points,int bins){
        boolean[] hit=new boolean[bins];int count=0;
        double axis=Math.toRadians(e.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double rx=Math.max(1e-6,e.size.width/2.0),ry=Math.max(1e-6,e.size.height/2.0);
        for(Point p:points){
            double dx=p.x-e.center.x,dy=p.y-e.center.y;
            double lx=ca*dx+sa*dy,ly=-sa*dx+ca*dy;
            double nx=lx/rx,ny=ly/ry;
            double rho=Math.hypot(nx,ny);if(rho<0.82||rho>1.18)continue;
            double a=Math.atan2(ny,nx);if(a<0)a+=2.0*Math.PI;
            int bin=Math.min(bins-1,(int)Math.floor(a/(2.0*Math.PI)*bins));
            if(!hit[bin]){hit[bin]=true;count++;}
        }
        return count/(double)bins;
    }

    private static PoseCandidate coarseSearch(Mat distance,RotatedRect ellipse){
        PoseCandidate best=null;
        for(double scale=0.68;scale<=1.32+1e-9;scale+=0.04){
            for(double roll=-15.0;roll<=15.0+1e-9;roll+=1.0){
                double score=fitMedianFast(distance,ellipse,scale,roll);
                if(best==null||score<best.score)best=new PoseCandidate(ellipse,scale,roll,score);
            }
        }
        return best;
    }

    private static PoseCandidate fineSearch(Mat distance,PoseCandidate coarse){
        RotatedRect coarseEllipse=scaledEllipse(coarse.source,coarse.scale);
        PoseCandidate best=new PoseCandidate(coarse.source,coarse.scale,coarse.roll,
                fitMedian(distance,coarseEllipse,coarse.roll,false));
        double minScale=Math.max(0.62,coarse.scale-0.06),maxScale=Math.min(1.38,coarse.scale+0.06);
        for(double scale=minScale;scale<=maxScale+1e-9;scale+=0.005){
            for(double roll=coarse.roll-1.5;roll<=coarse.roll+1.5+1e-9;roll+=0.10){
                if(Math.abs(roll)>16.0)continue;
                RotatedRect e=scaledEllipse(coarse.source,scale);
                double score=fitMedian(distance,e,roll,false);
                if(score<best.score)best=new PoseCandidate(coarse.source,scale,roll,score);
            }
        }
        return best;
    }

    private static RotatedRect refineCentre(Mat distance,RotatedRect ellipse,double roll){
        RotatedRect best=ellipse;double bestScore=fitMedian(distance,ellipse,roll,false);
        double radius=(Math.max(ellipse.size.width,ellipse.size.height)
                +Math.min(ellipse.size.width,ellipse.size.height))/4.0;
        double step=Math.max(0.75,radius*0.008);
        for(int ix=-2;ix<=2;ix++)for(int iy=-2;iy<=2;iy++){
            if(ix==0&&iy==0)continue;
            RotatedRect candidate=new RotatedRect(new Point(ellipse.center.x+ix*step,ellipse.center.y+iy*step),
                    new Size(ellipse.size.width,ellipse.size.height),ellipse.angle);
            double score=fitMedian(distance,candidate,roll,false);
            if(score<bestScore){bestScore=score;best=candidate;}
        }
        return best;
    }

    private static BoundaryResult findNextOuterBoundary(Mat distance,RotatedRect trackBased,double roll){
        BoundaryResult best=null;double bestObjective=Double.POSITIVE_INFINITY;
        // The minute track is at 0.925R. Search only outside it around the predicted dial edge.
        for(double radius=0.960;radius<=1.040+1e-9;radius+=0.0025){
            List<Double> groups=new ArrayList<>();
            for(int block=0;block<24;block++){
                double sum=0.0;
                for(int j=0;j<4;j++){
                    double angle=2.0*Math.PI*(block*4+j)/96.0;
                    Point p=map(trackBased,1.0,roll,radius*Math.cos(angle),radius*Math.sin(angle));
                    sum+=sample(distance,p.x,p.y);
                }
                groups.add(sum/4.0);
            }
            Collections.sort(groups);
            double median=percentileSorted(groups,0.50),p90=percentileSorted(groups,0.90);
            double dialRadius=(Math.max(trackBased.size.width,trackBased.size.height)
                    +Math.min(trackBased.size.width,trackBased.size.height))/4.0;
            double expectedPenalty=Math.abs(radius-1.0)*Math.max(20.0,dialRadius)*0.40;
            double objective=median+0.30*p90+expectedPenalty;
            if(objective<bestObjective){bestObjective=objective;best=new BoundaryResult(radius,median,p90);}
        }
        return best;
    }

    private static double fitMedianFast(Mat distance,RotatedRect ellipse,double scale,double roll){
        List<Double> scores=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0||minute%4==2)continue;
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

    private static double fitMedian(Mat distance,RotatedRect ellipse,double roll,boolean holdout){
        List<Double> scores=new ArrayList<>();
        for(int minute=0;minute<60;minute++){
            if(minute%5==0)continue;
            boolean thisHoldout=minute%4==2;if(thisHoldout!=holdout)continue;
            double angle=Math.toRadians(minute*6.0-90.0);
            scores.add(tickScore(distance,ellipse,roll,angle));
        }
        Collections.sort(scores);return percentileSorted(scores,0.50);
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

    private static Point map(RotatedRect e,double scale,double rollDeg,double x,double y){
        double roll=Math.toRadians(rollDeg),cr=Math.cos(roll),sr=Math.sin(roll);
        double xr=cr*x-sr*y,yr=sr*x+cr*y;
        double axis=Math.toRadians(e.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double localX=ca*xr+sa*yr,localY=-sa*xr+ca*yr;
        double rx=e.size.width*0.5*scale,ry=e.size.height*0.5*scale;
        double sx=rx*localX,sy=ry*localY;
        return new Point(e.center.x+ca*sx-sa*sy,e.center.y+sa*sx+ca*sy);
    }

    private static RotatedRect scaledEllipse(RotatedRect e,double scale){
        return new RotatedRect(new Point(e.center.x,e.center.y),
                new Size(e.size.width*scale,e.size.height*scale),e.angle);
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

    private static double percentileSorted(List<Double> sorted,double q){
        if(sorted.isEmpty())return LOSS_CAP_PX;
        double pos=q*(sorted.size()-1);int lo=(int)Math.floor(pos),hi=(int)Math.ceil(pos);
        if(lo==hi)return sorted.get(lo);double f=pos-lo;
        return sorted.get(lo)*(1.0-f)+sorted.get(hi)*f;
    }

    private MinuteTrackDialFinder(){}
}
