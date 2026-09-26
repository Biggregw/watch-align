package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Human-first GMT 12-o'clock measurement.
 *
 * The local minute track defines the coordinate frame. The 12 triangle is never
 * used for its own orientation reference, so a crooked marker cannot be rotated
 * straight by the measurement process. The triangle centre is used only to
 * disambiguate which one of the repeating minute ticks is 60; the actual frame
 * angle comes from the detected 60 tick and dial centre.
 */
final class GmtTwelveLandmarkAnalyzer {
    static final class Result {
        final boolean valid,detectorStable;
        final String reason;
        final double topClearance,horizontalOffset,wholeAxisErrorDeg,topEdgeErrorDeg;
        final double leftClearance,rightClearance,sideAsymmetry,triangleWidthPx;
        final double trackRollClockDeg,tickPitchDeg,minuteFrameScore;
        final int inferredMinutePoints;
        Result(String reason){
            valid=false;detectorStable=false;this.reason=reason;
            topClearance=horizontalOffset=wholeAxisErrorDeg=topEdgeErrorDeg=Double.NaN;
            leftClearance=rightClearance=sideAsymmetry=triangleWidthPx=Double.NaN;
            trackRollClockDeg=tickPitchDeg=minuteFrameScore=Double.NaN;
            inferredMinutePoints=3;
        }
        Result(double gap,double horiz,double axis,double edge,double left,double right,double side,double width,
               double roll,double pitch,double frameScore,int inferred,boolean stable){
            valid=true;detectorStable=stable;reason="";
            topClearance=gap;horizontalOffset=horiz;wholeAxisErrorDeg=axis;topEdgeErrorDeg=edge;
            leftClearance=left;rightClearance=right;sideAsymmetry=side;triangleWidthPx=width;
            trackRollClockDeg=roll;tickPitchDeg=pitch;minuteFrameScore=frameScore;inferredMinutePoints=inferred;
        }
    }

    private static final class Triangle {
        final Point left,right,tip;
        Triangle(Point l,Point r,Point t){left=l;right=r;tip=t;}
    }
    private static final class MinuteFrame {
        final Point left,center,right;
        final double rollDeg,pitchDeg,score;
        final int inferred;
        MinuteFrame(Point l,Point c,Point r,double roll,double pitch,double score,int inferred){
            left=l;center=c;right=r;rollDeg=roll;pitchDeg=pitch;this.score=score;this.inferred=inferred;
        }
    }

    static Result analyse(Mat bgr,double cx,double cy,double r){
        if(bgr==null||bgr.empty()||!(r>20))return new Result("invalid dial seed");
        Mat gray=new Mat(),enhanced=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
            clahe.apply(gray,enhanced);

            Triangle tri=triangleCandidate(enhanced,cx,cy,r);
            if(tri==null)return new Result("12 triangle physical contour not found");

            MinuteFrame frame=minuteFrame(enhanced,cx,cy,r,tri);
            if(frame==null)return new Result("59/60/01 minute-track frame not sufficiently constrained");

            Point baseMid=mid(tri.left,tri.right);
            double width=dist(tri.left,tri.right);
            if(width<=1e-9)return new Result("triangle top edge is degenerate");

            double gap=Math.abs(pointLineDistance(baseMid,frame.left,frame.right))/width;

            // True local 12 is centre -> detected 60 tick. Use that radial axis for
            // centring and whole-marker orientation. The marker cannot define itself.
            double ux=cx-frame.center.x,uy=cy-frame.center.y;
            double un=Math.hypot(ux,uy);
            if(un<=1e-9)return new Result("60-minute radial axis is degenerate");
            ux/=un;uy/=un;
            double vx=-uy,vy=ux;
            double dx=baseMid.x-frame.center.x,dy=baseMid.y-frame.center.y;
            double horiz=(dx*vx+dy*vy)/width;

            double triAx=Math.atan2(tri.tip.y-baseMid.y,tri.tip.x-baseMid.x);
            double refAx=Math.atan2(uy,ux);
            double axisErr=wrap90(Math.toDegrees(triAx-refAx));
            double edgeErr=parallelAngleDifferenceDeg(tri.left,tri.right,frame.left,frame.right);

            double left=dist(tri.left,frame.left)/width;
            double right=dist(tri.right,frame.right)/width;
            double side=right-left;
            boolean stable=frame.score>=5.0 && frame.pitchDeg>=5.35 && frame.pitchDeg<=6.65 && frame.inferred<=1;

            return new Result(gap,horiz,axisErr,edgeErr,left,right,side,width,
                    frame.rollDeg,frame.pitchDeg,frame.score,frame.inferred,stable);
        }catch(Throwable t){
            return new Result("12 local geometry failed: "+t.getClass().getSimpleName());
        }finally{
            enhanced.release();gray.release();
        }
    }

    /** Backward-compatible overload. Legacy global roll is intentionally ignored. */
    static Result analyse(Mat bgr,double cx,double cy,double r,double ignoredGlobalRoll){
        return analyse(bgr,cx,cy,r);
    }

    /**
     * Detect the physical triangle without rotating the image first. This preserves
     * the very rotation we are trying to measure.
     */
    private static Triangle triangleCandidate(Mat gray,double cx,double cy,double r){
        int x0=Math.max(0,(int)Math.floor(cx-.33*r));
        int x1=Math.min(gray.cols(),(int)Math.ceil(cx+.33*r));
        int y0=Math.max(0,(int)Math.floor(cy-1.01*r));
        int y1=Math.min(gray.rows(),(int)Math.ceil(cy-.25*r));
        if(x1<=x0||y1<=y0)return null;
        Rect roi=new Rect(x0,y0,x1-x0,y1-y0);
        Mat patch=gray.submat(roi);
        List<Mat> masks=new ArrayList<>();
        List<MatOfPoint> contours=new ArrayList<>();
        Mat hierarchy=new Mat();
        Triangle best=null;double bestScore=-Double.MAX_VALUE;
        try{
            Mat otsu=new Mat();Imgproc.threshold(patch,otsu,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);masks.add(otsu);
            Mat adaptive=new Mat();
            int block=Math.max(15,((int)Math.round(r*.10))|1);
            Imgproc.adaptiveThreshold(patch,adaptive,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,-3);masks.add(adaptive);

            for(Mat input:masks){
                Mat m=input.clone();
                Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));
                Imgproc.morphologyEx(m,m,Imgproc.MORPH_OPEN,kernel);kernel.release();
                contours.clear();hierarchy.release();hierarchy=new Mat();
                Imgproc.findContours(m,contours,hierarchy,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
                m.release();
                for(MatOfPoint c:contours){
                    double area=Imgproc.contourArea(c);
                    if(area<20||area>.08*Math.PI*r*r)continue;
                    Point[] raw=c.toArray();if(raw.length<5)continue;

                    double sx=0,sy=0;for(Point p:raw){sx+=p.x+x0;sy+=p.y+y0;}
                    double mx=sx/raw.length,my=sy/raw.length;
                    double rr=Math.hypot(mx-cx,my-cy)/r;
                    double ang=Math.abs(clockAngle(cx,cy,mx,my));
                    if(rr<.52||rr>.88||ang>22.0)continue;

                    MatOfInt hi=new MatOfInt();Imgproc.convexHull(c,hi);
                    int[] idx=hi.toArray();Point[] hp=new Point[idx.length];
                    for(int i=0;i<idx.length;i++)hp[i]=raw[idx[i]];
                    MatOfPoint2f hull=new MatOfPoint2f(hp),poly=new MatOfPoint2f();
                    double per=Imgproc.arcLength(hull,true);
                    if(per<=0){hi.release();hull.release();poly.release();continue;}
                    Imgproc.approxPolyDP(hull,poly,.025*per,true);
                    Point[] q=poly.toArray();hi.release();hull.release();poly.release();
                    if(q.length<3||q.length>8)continue;

                    Point[] g=new Point[q.length];
                    for(int i=0;i<q.length;i++)g[i]=new Point(q[i].x+x0,q[i].y+y0);
                    Triangle t=triangleFromHull(g,cx,cy);
                    if(t==null)continue;
                    double w=dist(t.left,t.right),h=dist(mid(t.left,t.right),t.tip);
                    if(w<.09*r||w>.29*r||h<.10*r||h>.30*r)continue;
                    double ratio=w/h;if(ratio<.55||ratio>1.75)continue;
                    double centreErr=Math.abs(clockAngle(cx,cy,mid(t.left,t.right).x,mid(t.left,t.right).y));
                    double score=area-.30*area*Math.min(1.0,centreErr/18.0)-.12*area*Math.abs(ratio-1.05);
                    if(score>bestScore){bestScore=score;best=t;}
                }
                for(MatOfPoint c:contours)c.release();contours.clear();
            }
            return best;
        }finally{
            for(MatOfPoint c:contours)c.release();
            for(Mat m:masks)m.release();
            hierarchy.release();patch.release();
        }
    }

    private static Triangle triangleFromHull(Point[] q,double cx,double cy){
        if(q.length<3)return null;
        double mx=0,my=0;for(Point p:q){mx+=p.x;my+=p.y;}mx/=q.length;my/=q.length;
        double ox=mx-cx,oy=my-cy,on=Math.hypot(ox,oy);if(on<=1e-9)return null;ox/=on;oy/=on;
        double tx=-oy,ty=ox;

        Point tip=null;double minRad=Double.POSITIVE_INFINITY,maxRad=-Double.MAX_VALUE;
        for(Point p:q){double rad=(p.x-cx)*ox+(p.y-cy)*oy;minRad=Math.min(minRad,rad);maxRad=Math.max(maxRad,rad);}
        for(Point p:q){double rad=(p.x-cx)*ox+(p.y-cy)*oy;if(rad<minRad+1e-9)tip=p;}
        if(tip==null)return null;

        double cut=minRad+.60*(maxRad-minRad);
        Point left=null,right=null;double minTan=Double.POSITIVE_INFINITY,maxTan=-Double.MAX_VALUE;
        for(Point p:q){
            double rad=(p.x-cx)*ox+(p.y-cy)*oy;if(rad<cut)continue;
            double tan=(p.x-cx)*tx+(p.y-cy)*ty;
            if(tan<minTan){minTan=tan;left=p;}
            if(tan>maxTan){maxTan=tan;right=p;}
        }
        if(left==null||right==null||left==right)return null;
        // Ensure image-left/image-right naming for human side-spacing diagnostics.
        if(left.x>right.x){Point z=left;left=right;right=z;}
        return new Triangle(left,right,tip);
    }

    /**
     * Find the 59/60/01 frame in polar space. The triangle's centre only chooses
     * which repeating minute tick is 60. The tick itself supplies roll and frame.
     */
    private static MinuteFrame minuteFrame(Mat gray,double cx,double cy,double r,Triangle tri){
        Point baseMid=mid(tri.left,tri.right);
        double markerAngle=clockAngle(cx,cy,baseMid.x,baseMid.y);
        if(Math.abs(markerAngle)>22.0)return null;

        double best=-Double.MAX_VALUE,bestA=Double.NaN,bestP=Double.NaN;
        for(double a=markerAngle-3.4;a<=markerAngle+3.4+1e-9;a+=0.10){
            for(double p=5.35;p<=6.65+1e-9;p+=0.10){
                double s0=tickAngleScore(gray,cx,cy,r,a);
                double s1=tickAngleScore(gray,cx,cy,r,a-p);
                double s2=tickAngleScore(gray,cx,cy,r,a+p);
                double s3=tickAngleScore(gray,cx,cy,r,a-2*p);
                double s4=tickAngleScore(gray,cx,cy,r,a+2*p);
                double between=(tickAngleScore(gray,cx,cy,r,a-p*.5)+tickAngleScore(gray,cx,cy,r,a+p*.5))*.5;
                double symmetry=Math.abs(s1-s2);
                double score=1.20*s0+s1+s2+.45*(s3+s4)-.30*symmetry-.75*between-.15*Math.abs(a-markerAngle);
                if(score>best){best=score;bestA=a;bestP=p;}
            }
        }
        if(!Double.isFinite(bestA))return null;

        // Sub-step refinement around the winning phase/pitch.
        double coarseA=bestA,coarseP=bestP;
        for(double a=coarseA-.20;a<=coarseA+.20+1e-9;a+=.025){
            for(double p=coarseP-.15;p<=coarseP+.15+1e-9;p+=.025){
                if(p<5.2||p>6.8)continue;
                double s0=tickAngleScore(gray,cx,cy,r,a),s1=tickAngleScore(gray,cx,cy,r,a-p),s2=tickAngleScore(gray,cx,cy,r,a+p);
                double between=(tickAngleScore(gray,cx,cy,r,a-p*.5)+tickAngleScore(gray,cx,cy,r,a+p*.5))*.5;
                double score=1.25*s0+s1+s2-.35*Math.abs(s1-s2)-.85*between-.15*Math.abs(a-markerAngle);
                if(score>best){best=score;bestA=a;bestP=p;}
            }
        }

        double s59=tickAngleScore(gray,cx,cy,r,bestA-bestP);
        double s60=tickAngleScore(gray,cx,cy,r,bestA);
        double s01=tickAngleScore(gray,cx,cy,r,bestA+bestP);
        double bg=(tickAngleScore(gray,cx,cy,r,bestA-bestP*.5)+tickAngleScore(gray,cx,cy,r,bestA+bestP*.5))*.5;
        double frameScore=((s59+s60+s01)/3.0)-bg;
        if(!Double.isFinite(frameScore)||frameScore<2.0)return null;

        int inferred=0;
        Point p59=tickInnerPoint(gray,cx,cy,r,bestA-bestP);if(p59==null){p59=polarPoint(cx,cy,.915*r,bestA-bestP);inferred++;}
        Point p60=tickInnerPoint(gray,cx,cy,r,bestA);if(p60==null){p60=polarPoint(cx,cy,.915*r,bestA);inferred++;}
        Point p01=tickInnerPoint(gray,cx,cy,r,bestA+bestP);if(p01==null){p01=polarPoint(cx,cy,.915*r,bestA+bestP);inferred++;}

        // 59/01 labels are image-left/image-right after local frame construction.
        Point left=p59,right=p01;
        if(left.x>right.x){Point z=left;left=right;right=z;}
        return new MinuteFrame(left,p60,right,bestA,bestP,frameScore,inferred);
    }

    private static double tickAngleScore(Mat gray,double cx,double cy,double r,double clockDeg){
        double[] vals=new double[20];int n=0;
        for(double rf=.855;rf<=.975+1e-9;rf+=.007){
            double c=samplePolar(gray,cx,cy,r*rf,clockDeg);
            double l=samplePolar(gray,cx,cy,r*rf,clockDeg-.72);
            double rr=samplePolar(gray,cx,cy,r*rf,clockDeg+.72);
            if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr))continue;
            double contrast=c-(l+rr)*.5;
            double bright=Math.max(0,c-135.0)*.08;
            vals[n++]=contrast+bright;
        }
        if(n<6)return -100;
        Arrays.sort(vals,0,n);
        int take=Math.max(4,n/3);double sum=0;
        for(int i=n-take;i<n;i++)sum+=vals[i];
        return sum/take;
    }

    private static Point tickInnerPoint(Mat gray,double cx,double cy,double r,double a){
        final int N=71;double[] score=new double[N],rad=new double[N];int best=-1;double bestV=-Double.MAX_VALUE;
        for(int i=0;i<N;i++){
            double rf=.835+i*(.155/(N-1));rad[i]=rf*r;
            double c=samplePolar(gray,cx,cy,rad[i],a);
            double l=samplePolar(gray,cx,cy,rad[i],a-.72);
            double rr=samplePolar(gray,cx,cy,rad[i],a+.72);
            if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr)){score[i]=-999;continue;}
            score[i]=c-(l+rr)*.5+Math.max(0,c-140)*.05;
            if(score[i]>bestV){bestV=score[i];best=i;}
        }
        if(best<0||bestV<8.0)return null;
        double threshold=Math.max(5.0,bestV*.38);
        int inner=best;
        while(inner>0&&score[inner-1]>=threshold)inner--;
        return polarPoint(cx,cy,rad[inner],a);
    }

    private static Point polarPoint(double cx,double cy,double radius,double clockDeg){
        double t=Math.toRadians(clockDeg);
        return new Point(cx+Math.sin(t)*radius,cy-Math.cos(t)*radius);
    }
    private static double samplePolar(Mat gray,double cx,double cy,double radius,double clockDeg){
        Point p=polarPoint(cx,cy,radius,clockDeg);return bilinear(gray,p.x,p.y);
    }
    private static double bilinear(Mat m,double x,double y){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);if(x0<0||y0<0||x0+1>=m.cols()||y0+1>=m.rows())return Double.NaN;
        double fx=x-x0,fy=y-y0;
        double a=m.get(y0,x0)[0],b=m.get(y0,x0+1)[0],c=m.get(y0+1,x0)[0],d=m.get(y0+1,x0+1)[0];
        return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;
    }

    private static double clockAngle(double cx,double cy,double x,double y){
        return Math.toDegrees(Math.atan2(x-cx,cy-y));
    }
    private static Point mid(Point a,Point b){return new Point((a.x+b.x)/2.0,(a.y+b.y)/2.0);}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static double pointLineDistance(Point p,Point a,Point b){
        double vx=b.x-a.x,vy=b.y-a.y,len=Math.hypot(vx,vy);if(len<=1e-9)return Double.NaN;
        return (vx*(p.y-a.y)-vy*(p.x-a.x))/len;
    }
    private static double parallelAngleDifferenceDeg(Point a0,Point a1,Point b0,Point b1){
        double d=Math.toDegrees(Math.atan2(a1.y-a0.y,a1.x-a0.x)-Math.atan2(b1.y-b0.y,b1.x-b0.x));return wrap90(d);
    }
    private static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    private GmtTwelveLandmarkAnalyzer(){}
}
