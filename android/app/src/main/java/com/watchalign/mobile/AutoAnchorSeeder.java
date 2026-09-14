package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Produces an automatic starting pose for the four INNER DIAL-EDGE perspective anchors.
 *
 * The relevant physical edge is the smaller member of the two concentric rehaut boundaries.
 * Marker geometry supplies orientation/perspective, while a separately detected rehaut pair fixes
 * the physical scale. The 12 triangle is never used as a registration point.
 */
final class AutoAnchorSeeder {
    static final class Result {
        final PointF p12,p3,p6,p9;
        final float confidence;
        final boolean boundaryVerified;
        final String note;
        Result(PointF a,PointF b,PointF c,PointF d,float conf,boolean verified,String n){p12=a;p3=b;p6=c;p9=d;confidence=conf;boundaryVerified=verified;note=n;}
    }

    private static final class DialSeed {
        final double x,y,r,quality,rollDeg;
        DialSeed(double x,double y,double r,double q,double roll){this.x=x;this.y=y;this.r=r;this.quality=q;this.rollDeg=roll;}
    }

    private static final class EllipseCandidate {
        final RotatedRect ellipse;
        final double radius,edge,contrast;
        EllipseCandidate(RotatedRect e,double r,double s,double c){ellipse=e;radius=r;edge=s;contrast=c;}
    }

    private static final class RehautPair {
        final RotatedRect inner,outer;
        final double confidence;
        RehautPair(RotatedRect i,RotatedRect o,double c){inner=i;outer=o;confidence=c;}
    }

    private AutoAnchorSeeder() {}

    static Result seed(Bitmap input){
        if(input==null)return null;
        Mat src=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(input,src);
            DialSeed seed=detectSeed(src);
            if(seed==null||seed.r<40)return null;

            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);
            RehautPair pair=findRehautPair(gray,edges,seed);

            Result markerFit=markerGeometrySeed(src,seed,pair);
            if(markerFit!=null&&markerFit.confidence>=0.52f)return markerFit;

            RotatedRect ellipse=pair!=null?pair.inner:findDialEllipse(edges,seed);
            if(ellipse==null)return markerFit;

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            Point[] card=ellipseCardinalPoints(ellipse,seed.rollDeg);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double q=Math.max(0,Math.min(1,(seed.quality-0.40)/0.45));
            double c=Math.max(0,1-centerErr/0.20);
            double a=Math.max(0,Math.min(1,(axisRatio-0.68)/0.25));
            double boundaryQ=pair!=null?pair.confidence:.45;
            float conf=(float)Math.max(0,Math.min(1,0.34*q+0.24*c+0.16*a+0.26*boundaryQ));
            if(pair==null)conf=Math.min(conf,.70f);
            String level=conf>=0.78f&&pair!=null?"strong":conf>=0.45f?"usable":"uncertain";
            String boundary=pair!=null?" Inner dial edge verified as the smaller of the two rehaut boundaries.":" Inner/outer rehaut pair was not independently verified, so auto-lock is disabled.";
            return new Result(pf(card[0]),pf(card[1]),pf(card[2]),pf(card[3]),conf,pair!=null,
                    "Automatic inner dial-edge fit: "+level+"."+boundary+" The 12/3/6/9 handles belong on the yellow INNER DIAL EDGE, not on the markers or bezel.");
        }catch(Throwable ignored){
            return null;
        }finally{
            src.release();gray.release();blur.release();edges.release();
        }
    }

    /**
     * Preferred GMT registration. Uses the detected 1/2/4/5/6/7/8/9/10/11 marker population;
     * 12 is deliberately excluded and 3 is the date aperture. RANSAC prevents one misplaced or
     * poorly detected marker from controlling the perspective transform. The resulting orientation
     * is then snapped to the independently detected INNER rehaut ellipse when that pair is visible.
     */
    private static Result markerGeometrySeed(Mat rgba,DialSeed seed,RehautPair pair){
        Mat bgr=new Mat(),mask=new Mat(),h=null;MatOfPoint2f canonical=null,observed=null,edge=null,projected=null;
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle dial=DialAnalysisEngine.detectDial(bgr);if(dial==null||dial.r<=0)return null;
            DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,dial);
            if(set==null||set.markers==null||set.markers.size()<7||!Double.isFinite(set.globalRotation)||!Double.isFinite(set.medianRadius))return null;

            double ringR=set.medianRadius/Math.max(1.0,dial.r);
            if(!Double.isFinite(ringR)||ringR<0.58||ringR>0.90)return null;
            List<Point> srcPts=new ArrayList<>(),dstPts=new ArrayList<>();
            for(DialAnalysisEngine.Marker m:set.markers){
                int hour=m.hour;if(hour==3||hour==12)continue;
                double ideal=Math.toRadians(hour*30.0);
                srcPts.add(new Point(ringR*Math.sin(ideal),-ringR*Math.cos(ideal)));
                double actual=Math.toRadians(hour*30.0+set.globalRotation+m.angular);
                dstPts.add(new Point(dial.x+Math.sin(actual)*m.radius,dial.y-Math.cos(actual)*m.radius));
            }
            if(srcPts.size()<6)return null;
            canonical=new MatOfPoint2f(srcPts.toArray(new Point[0]));
            observed=new MatOfPoint2f(dstPts.toArray(new Point[0]));
            h=Calib3d.findHomography(canonical,observed,Calib3d.RANSAC,Math.max(2.5,dial.r*0.012),mask,2500,0.995);
            if(h==null||h.empty())return null;

            edge=new MatOfPoint2f(new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0),new Point(0,0));
            projected=new MatOfPoint2f();Core.perspectiveTransform(edge,projected,h);
            Point[] p=projected.toArray();if(p.length<5)return null;
            for(Point x:p)if(!Double.isFinite(x.x)||!Double.isFinite(x.y))return null;

            // The marker fit determines the four projective directions, but scale comes from the
            // physical dial-side rehaut edge. This prevents a radially shifted marker population
            // from defining its own "correct" dial radius.
            if(pair!=null){
                for(int i=0;i<4;i++){
                    double angle=Math.atan2(p[i].y-p[4].y,p[i].x-p[4].x);
                    p[i]=rayEllipseIntersection(pair.inner,angle);
                }
            }

            double area=quadArea(p[0],p[1],p[2],p[3]);if(area<Math.PI*dial.r*dial.r*0.30)return null;
            double centreErr=Math.hypot(p[4].x-dial.x,p[4].y-dial.y)/Math.max(1.0,dial.r);if(centreErr>0.20)return null;

            MatOfPoint2f repro=new MatOfPoint2f();Core.perspectiveTransform(canonical,repro,h);Point[] rp=repro.toArray();repro.release();
            double ss=0;int n=Math.min(rp.length,dstPts.size()),inliers=0;
            for(int i=0;i<n;i++){double d=dist(rp[i],dstPts.get(i));ss+=d*d;double[] mv=mask.empty()?null:mask.get(i,0);if(mv==null||mv.length==0||mv[0]>0)inliers++;}
            double rms=n>0?Math.sqrt(ss/n):99.0,inlierRatio=n>0?inliers/(double)n:0;
            double q=clamp((dial.quality-0.42)/0.40),rmsQ=clamp(1-rms/Math.max(2.0,dial.r*0.035)),centreQ=clamp(1-centreErr/0.16),boundaryQ=pair!=null?pair.confidence:.35;
            float conf=(float)clamp(0.28*q+0.28*inlierRatio+0.18*rmsQ+0.08*centreQ+0.18*boundaryQ);
            if(pair==null)conf=Math.min(conf,.70f);
            String level=conf>=0.78f&&pair!=null?"strong":conf>=0.58f?"good":"usable";
            String boundary=pair!=null?" Inner dial edge verified as the SMALLER concentric rehaut boundary.":" Inner/outer rehaut pair not independently verified; anchors will not auto-lock.";
            return new Result(pf(p[0]),pf(p[1]),pf(p[2]),pf(p[3]),conf,pair!=null,
                    "Automatic GMT perspective fit: "+level+" ("+inliers+"/"+n+" registration markers agree)."+boundary+" 12/3/6/9 are INNER DIAL-EDGE anchors.");
        }catch(Throwable ignored){return null;}
        finally{bgr.release();mask.release();if(h!=null)h.release();if(canonical!=null)canonical.release();if(observed!=null)observed.release();if(edge!=null)edge.release();if(projected!=null)projected.release();}
    }

    private static DialSeed detectSeed(Mat rgba){
        Mat bgr=new Mat();
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            DialAnalysisEngine.Circle d=DialAnalysisEngine.detectDial(bgr);if(d==null)return null;
            double roll=0.0;
            try{DialAnalysisEngine.MarkerSet set=DialAnalysisEngine.measureMarkerSet(bgr,d);double r=set.globalRotation;if(Double.isFinite(r)&&Math.abs(r)<30)roll=r;}catch(Throwable ignored){}
            return new DialSeed(d.x,d.y,d.r,d.quality,roll);
        }finally{bgr.release();}
    }

    /** Locate both rehaut boundaries and always return the smaller/inner one as the dial edge. */
    private static RehautPair findRehautPair(Mat gray,Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();List<EllipseCandidate> candidates=new ArrayList<>();Mat hierarchy=new Mat();Mat in=edges.clone();
        try{
            Imgproc.findContours(in,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            for(MatOfPoint c:contours){
                if(c.rows()<45)continue;MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);double major=Math.max(e.size.width,e.size.height),minor=Math.min(e.size.width,e.size.height);
                    if(major<1.55*s.r||major>2.35*s.r||minor<1.35*s.r||minor>2.30*s.r)continue;
                    double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/Math.max(1.0,s.r);if(dc>0.20)continue;
                    double ratio=minor/Math.max(1.0,major);if(ratio<0.68)continue;
                    double radius=Math.sqrt(Math.max(1.0,major*minor))/2.0;
                    double edge=ellipseBoundaryStrength(gray,e),contrast=ellipseBoundaryContrast(gray,e);
                    if(edge<0.028)continue;
                    candidates.add(new EllipseCandidate(e,radius,edge,contrast));
                }catch(Throwable ignored){}finally{f.release();}
            }
            RehautPair best=null;double bestScore=-1;
            for(int i=0;i<candidates.size();i++)for(int j=i+1;j<candidates.size();j++){
                EllipseCandidate a=candidates.get(i),b=candidates.get(j);EllipseCandidate inner=a.radius<b.radius?a:b,outer=a.radius<b.radius?b:a;
                double ratio=inner.radius/Math.max(1.0,outer.radius);if(ratio<0.925||ratio>0.985)continue;
                double sep=(outer.radius-inner.radius)/Math.max(1.0,outer.radius);if(sep<0.015||sep>0.085)continue;
                double centre=Math.hypot(inner.ellipse.center.x-outer.ellipse.center.x,inner.ellipse.center.y-outer.ellipse.center.y)/Math.max(1.0,s.r);if(centre>0.070)continue;
                double ari=Math.min(inner.ellipse.size.width,inner.ellipse.size.height)/Math.max(inner.ellipse.size.width,inner.ellipse.size.height);
                double aro=Math.min(outer.ellipse.size.width,outer.ellipse.size.height)/Math.max(outer.ellipse.size.width,outer.ellipse.size.height);
                double shapeDiff=Math.abs(ari-aro);if(shapeDiff>0.08)continue;
                double angleDiff=angleDiff180(inner.ellipse.angle,outer.ellipse.angle);if(angleDiff>15)continue;
                double centreQ=clamp(1-centre/.07),shapeQ=clamp(1-shapeDiff/.08),angleQ=clamp(1-angleDiff/15.0);
                double innerEdgeQ=clamp(inner.edge/.11),outerEdgeQ=clamp(outer.edge/.11),contrastQ=clamp((inner.contrast+.01)/.16);
                double sizeQ=clamp(1-Math.abs(outer.radius/s.r-1.0)/.20);
                double score=.20*centreQ+.14*shapeQ+.10*angleQ+.18*innerEdgeQ+.14*outerEdgeQ+.16*contrastQ+.08*sizeQ;
                if(score>bestScore){bestScore=score;best=new RehautPair(inner.ellipse,outer.ellipse,score);}
            }
            return bestScore>=.50?best:null;
        }finally{in.release();hierarchy.release();for(MatOfPoint c:contours)c.release();}
    }

    private static double ellipseBoundaryStrength(Mat gray,RotatedRect e){
        double sum=0;int n=0;double rx=e.size.width/2.0,ry=e.size.height/2.0,t=Math.toRadians(e.angle),ct=Math.cos(t),st=Math.sin(t);
        for(int deg=0;deg<360;deg+=6){double a=Math.toRadians(deg),ca=Math.cos(a),sa=Math.sin(a);Point pi=ellipsePoint(e,rx*.985,ry*.985,ca,sa,ct,st),po=ellipsePoint(e,rx*1.015,ry*1.015,ca,sa,ct,st);double vi=pixel(gray,pi),vo=pixel(gray,po);if(Double.isFinite(vi)&&Double.isFinite(vo)){sum+=Math.abs(vo-vi)/255.0;n++;}}
        return n>0?sum/n:0;
    }

    private static double ellipseBoundaryContrast(Mat gray,RotatedRect e){
        double sum=0;int n=0;double rx=e.size.width/2.0,ry=e.size.height/2.0,t=Math.toRadians(e.angle),ct=Math.cos(t),st=Math.sin(t);
        for(int deg=0;deg<360;deg+=8){double a=Math.toRadians(deg),ca=Math.cos(a),sa=Math.sin(a);Point pi=ellipsePoint(e,rx*.975,ry*.975,ca,sa,ct,st),po=ellipsePoint(e,rx*1.025,ry*1.025,ca,sa,ct,st);double vi=pixel(gray,pi),vo=pixel(gray,po);if(Double.isFinite(vi)&&Double.isFinite(vo)){sum+=(vo-vi)/255.0;n++;}}
        return n>0?sum/n:0;
    }

    private static Point ellipsePoint(RotatedRect e,double rx,double ry,double ca,double sa,double ct,double st){double x=rx*ca,y=ry*sa;return new Point(e.center.x+x*ct-y*st,e.center.y+x*st+y*ct);}
    private static double pixel(Mat gray,Point p){int x=(int)Math.round(p.x),y=(int)Math.round(p.y);if(x<0||y<0||x>=gray.cols()||y>=gray.rows())return Double.NaN;double[]v=gray.get(y,x);return v==null||v.length==0?Double.NaN:v[0];}

    private static RotatedRect findDialEllipse(Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Mat in=edges.clone();
        try{
            Imgproc.findContours(in,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;
            for(MatOfPoint c:contours){
                if(c.rows()<40)continue;MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);double maj=Math.max(e.size.width,e.size.height),min=Math.min(e.size.width,e.size.height);
                    if(maj<1.50*s.r||maj>2.30*s.r||min<1.30*s.r||min>2.25*s.r)continue;
                    double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/s.r;if(dc>0.24)continue;
                    double ratio=min/Math.max(1.0,maj);if(ratio<0.68)continue;
                    double sizeErr=Math.abs(maj/(2*s.r)-1)+Math.abs(min/(2*s.r)-1);
                    double score=2.8*dc+sizeErr+Math.max(0,0.82-ratio)*0.5;if(score<bestScore){bestScore=score;best=e;}
                }catch(Throwable ignored){}finally{f.release();}
            }
            return best;
        }finally{in.release();hierarchy.release();for(MatOfPoint c:contours)c.release();}
    }

    private static Point[] ellipseCardinalPoints(RotatedRect e,double rollDeg){return new Point[]{rayEllipseIntersection(e,Math.toRadians(rollDeg-90)),rayEllipseIntersection(e,Math.toRadians(rollDeg)),rayEllipseIntersection(e,Math.toRadians(rollDeg+90)),rayEllipseIntersection(e,Math.toRadians(rollDeg+180))};}
    private static Point rayEllipseIntersection(RotatedRect e,double angle){double rx=Math.max(1e-6,e.size.width/2),ry=Math.max(1e-6,e.size.height/2),t=Math.toRadians(e.angle);double dx=Math.cos(angle),dy=Math.sin(angle),lx=dx*Math.cos(t)+dy*Math.sin(t),ly=-dx*Math.sin(t)+dy*Math.cos(t);double den=Math.sqrt((lx*lx)/(rx*rx)+(ly*ly)/(ry*ry));double s=den>1e-9?1/den:0;return new Point(e.center.x+s*dx,e.center.y+s*dy);}
    private static double angleDiff180(double a,double b){double d=Math.abs(a-b)%180.0;return d>90?180-d:d;}
    private static double quadArea(Point a,Point b,Point c,Point d){Point[]p={a,b,c,d};double s=0;for(int i=0;i<4;i++){Point x=p[i],y=p[(i+1)%4];s+=x.x*y.y-y.x*x.y;}return Math.abs(s)*0.5;}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static double clamp(double x){return Math.max(0,Math.min(1,x));}
    private static PointF pf(Point p){return new PointF((float)p.x,(float)p.y);}
}
