package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Conservative recovery path for low-resolution screenshots/compressed QC images.
 *
 * The legacy marker-ring detector is used only to provide an approximate location
 * of the 12 marker. It does NOT supply the rotation or verdict. The recovered
 * 59/60/01 minute ticks define true 12, and a separately detected physical
 * triangle is then measured against that local frame.
 */
final class GmtTwelveRecoveryAnalyzer {
    private static final class Hint { final double angle,radius; Hint(double a,double r){angle=a;radius=r;} }
    private static final class Frame {
        final Point left,center,right; final double roll,pitch,score; final int inferred;
        Frame(Point l,Point c,Point r,double a,double p,double s,int i){left=l;center=c;right=r;roll=a;pitch=p;score=s;inferred=i;}
    }
    private static final class Triangle { final Point left,right,tip; final double score; Triangle(Point l,Point r,Point t,double s){left=l;right=r;tip=t;score=s;} }

    static GmtTwelveLandmarkAnalyzer.Result analyse(Mat bgr,double cx,double cy,double r,String primaryReason){
        if(bgr==null||bgr.empty()||!(r>20))return new GmtTwelveLandmarkAnalyzer.Result(primaryReason);
        Mat gray=new Mat(),enh=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.4,new Size(8,8));clahe.apply(gray,enh);
            Hint hint=legacyLocationHint(bgr,cx,cy,r);
            if(hint==null)hint=photometricHint(enh,cx,cy,r);
            if(hint==null)return new GmtTwelveLandmarkAnalyzer.Result(primaryReason+"; recovery could not localise 12 marker");

            Frame frame=minuteFrame(enh,cx,cy,r,hint.angle);
            if(frame==null)return new GmtTwelveLandmarkAnalyzer.Result(primaryReason+"; recovery could not resolve 59/60/01 lattice");
            Triangle tri=triangle(enh,cx,cy,r,frame.roll,hint.radius);
            if(tri==null)return new GmtTwelveLandmarkAnalyzer.Result(primaryReason+"; recovery found minute frame but not a trustworthy physical triangle");

            Point base=mid(tri.left,tri.right);double width=dist(tri.left,tri.right);
            if(width<=1)return new GmtTwelveLandmarkAnalyzer.Result(primaryReason+"; recovery triangle width degenerate");
            double gap=Math.abs(pointLineDistance(base,frame.left,frame.right))/width;

            double ux=cx-frame.center.x,uy=cy-frame.center.y,un=Math.hypot(ux,uy);
            if(un<=1e-9)return new GmtTwelveLandmarkAnalyzer.Result(primaryReason+"; recovery 60 axis degenerate");
            ux/=un;uy/=un;double vx=-uy,vy=ux;
            double dx=base.x-frame.center.x,dy=base.y-frame.center.y;
            double horiz=(dx*vx+dy*vy)/width;
            double axisErr=wrap90(Math.toDegrees(Math.atan2(tri.tip.y-base.y,tri.tip.x-base.x)-Math.atan2(uy,ux)));
            double edgeErr=parallelAngleDifferenceDeg(tri.left,tri.right,frame.left,frame.right);
            double left=dist(tri.left,frame.left)/width,right=dist(tri.right,frame.right)/width,side=right-left;
            boolean stable=frame.score>=4.0&&frame.pitch>=5.25&&frame.pitch<=6.75&&tri.score>=4.0;
            return new GmtTwelveLandmarkAnalyzer.Result(gap,horiz,axisErr,edgeErr,left,right,side,width,
                    frame.roll,frame.pitch,frame.score,frame.inferred,stable);
        }catch(Throwable t){
            return new GmtTwelveLandmarkAnalyzer.Result(primaryReason+"; recovery failed "+t.getClass().getSimpleName());
        }finally{enh.release();gray.release();}
    }

    /** Approximate 12 position only. Never use the legacy global roll as the QC reference. */
    private static Hint legacyLocationHint(Mat bgr,double cx,double cy,double r){
        try{
            Method make=WatchAlignCoreV7.class.getDeclaredMethod("createManualSeed",double.class,double.class,double.class);make.setAccessible(true);
            Object circle=make.invoke(null,cx,cy,r);
            Method measure=WatchAlignCoreV7.class.getDeclaredMethod("measureMarkerSet",Mat.class,circle.getClass());measure.setAccessible(true);
            Object set=measure.invoke(null,bgr,circle);
            double global=num(set,"globalRotation");
            Object markers=field(set,"markers");
            if(!(markers instanceof List))return null;
            for(Object m:(List<?>)markers){
                if((int)Math.round(num(m,"hour"))!=12)continue;
                double a=global+num(m,"angular"),rr=num(m,"radius");
                a=wrap180(a);if(Math.abs(a)>24||!(rr>.40*r&&rr<.95*r))return null;
                return new Hint(a,rr);
            }
        }catch(Throwable ignored){}
        return null;
    }

    private static Hint photometricHint(Mat g,double cx,double cy,double r){
        double best=-Double.MAX_VALUE,bestA=Double.NaN;
        for(double a=-20;a<=20;a+=.25){
            double[] vals=new double[36];int n=0;
            for(double rf=.50;rf<=.84;rf+=.012){
                double c=samplePolar(g,cx,cy,r*rf,a);
                double l=samplePolar(g,cx,cy,r*rf,a-1.4),rr=samplePolar(g,cx,cy,r*rf,a+1.4);
                if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr))continue;
                vals[n++]=(c-(l+rr)*.22)+Math.max(0,c-130)*.30;
            }
            if(n<8)continue;Arrays.sort(vals,0,n);double s=0;int take=Math.max(5,n/4);for(int i=n-take;i<n;i++)s+=vals[i];s/=take;
            if(s>best){best=s;bestA=a;}
        }
        if(!Double.isFinite(bestA)||best<40)return null;
        return new Hint(bestA,.68*r);
    }

    private static Frame minuteFrame(Mat g,double cx,double cy,double r,double hint){
        double best=-Double.MAX_VALUE,bestA=Double.NaN,bestP=Double.NaN;
        for(double a=hint-4.0;a<=hint+4.0;a+=.10)for(double p=5.25;p<=6.75;p+=.10){
            double s0=tickScore(g,cx,cy,r,a),s1=tickScore(g,cx,cy,r,a-p),s2=tickScore(g,cx,cy,r,a+p);
            double s3=tickScore(g,cx,cy,r,a-2*p),s4=tickScore(g,cx,cy,r,a+2*p);
            double bg=(tickScore(g,cx,cy,r,a-.5*p)+tickScore(g,cx,cy,r,a+.5*p))*.5;
            double score=1.25*s0+s1+s2+.35*(s3+s4)-.30*Math.abs(s1-s2)-.80*bg-.18*Math.abs(a-hint);
            if(score>best){best=score;bestA=a;bestP=p;}
        }
        if(!Double.isFinite(bestA))return null;
        double ca=bestA,cp=bestP;
        for(double a=ca-.20;a<=ca+.20;a+=.025)for(double p=cp-.15;p<=cp+.15;p+=.025){
            double s0=tickScore(g,cx,cy,r,a),s1=tickScore(g,cx,cy,r,a-p),s2=tickScore(g,cx,cy,r,a+p);
            double bg=(tickScore(g,cx,cy,r,a-.5*p)+tickScore(g,cx,cy,r,a+.5*p))*.5;
            double score=1.30*s0+s1+s2-.35*Math.abs(s1-s2)-.90*bg-.18*Math.abs(a-hint);
            if(score>best){best=score;bestA=a;bestP=p;}
        }
        double s59=tickScore(g,cx,cy,r,bestA-bestP),s60=tickScore(g,cx,cy,r,bestA),s01=tickScore(g,cx,cy,r,bestA+bestP);
        double bg=(tickScore(g,cx,cy,r,bestA-.5*bestP)+tickScore(g,cx,cy,r,bestA+.5*bestP))*.5;
        double frame=((s59+s60+s01)/3.0)-bg;if(!Double.isFinite(frame)||frame<1.4)return null;
        int inf=0;Point p59=tickInner(g,cx,cy,r,bestA-bestP);if(p59==null){p59=polar(cx,cy,.915*r,bestA-bestP);inf++;}
        Point p60=tickInner(g,cx,cy,r,bestA);if(p60==null){p60=polar(cx,cy,.915*r,bestA);inf++;}
        Point p01=tickInner(g,cx,cy,r,bestA+bestP);if(p01==null){p01=polar(cx,cy,.915*r,bestA+bestP);inf++;}
        Point left=p59,right=p01;if(left.x>right.x){Point z=left;left=right;right=z;}
        return new Frame(left,p60,right,bestA,bestP,frame,inf);
    }

    private static Triangle triangle(Mat g,double cx,double cy,double r,double frameAngle,double hintRadius){
        int x0=Math.max(0,(int)Math.floor(cx-.38*r)),x1=Math.min(g.cols(),(int)Math.ceil(cx+.38*r));
        int y0=Math.max(0,(int)Math.floor(cy-1.02*r)),y1=Math.min(g.rows(),(int)Math.ceil(cy-.20*r));
        if(x1<=x0||y1<=y0)return null;Rect roi=new Rect(x0,y0,x1-x0,y1-y0);Mat patch=g.submat(roi);
        List<Mat> masks=new ArrayList<>();
        try{
            Mat o=new Mat();Imgproc.threshold(patch,o,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);masks.add(o);
            for(int th:new int[]{135,155,175,195}){Mat m=new Mat();Imgproc.threshold(patch,m,th,255,Imgproc.THRESH_BINARY);masks.add(m);}
            Mat can=new Mat();Imgproc.Canny(patch,can,45,120);Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));Imgproc.dilate(can,can,k);k.release();masks.add(can);
            Triangle best=null;double bestScore=-Double.MAX_VALUE;
            for(Mat mask:masks){
                List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();Mat m=mask.clone();
                try{
                    Imgproc.findContours(m,cs,hier,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
                    for(MatOfPoint c:cs){
                        double area=Math.abs(Imgproc.contourArea(c));if(area<8||area>.14*Math.PI*r*r)continue;
                        Point[] raw=c.toArray();if(raw.length<4)continue;
                        Point[] pts=new Point[raw.length];for(int i=0;i<raw.length;i++)pts[i]=new Point(raw[i].x+x0,raw[i].y+y0);
                        Triangle t=fromContour(pts,cx,cy,r,frameAngle,hintRadius,area);if(t!=null&&t.score>bestScore){best=t;bestScore=t.score;}
                    }
                }finally{for(MatOfPoint c:cs)c.release();hier.release();m.release();}
            }
            return best;
        }finally{for(Mat m:masks)m.release();patch.release();}
    }

    private static Triangle fromContour(Point[] q,double cx,double cy,double r,double angle,double hintR,double area){
        double a=Math.toRadians(angle),ox=Math.sin(a),oy=-Math.cos(a),tx=Math.cos(a),ty=Math.sin(a);
        double minR=Double.POSITIVE_INFINITY,maxR=-Double.MAX_VALUE,sumR=0,sumT=0;
        for(Point p:q){double dx=p.x-cx,dy=p.y-cy,rr=dx*ox+dy*oy,tt=dx*tx+dy*ty;minR=Math.min(minR,rr);maxR=Math.max(maxR,rr);sumR+=rr;sumT+=tt;}
        double centroidR=sumR/q.length,centroidT=sumT/q.length;
        if(centroidR<.45*r||centroidR>.88*r||Math.abs(centroidT)>.18*r)return null;
        double depth=maxR-minR;if(depth<.06*r||depth>.38*r)return null;
        Point tip=null,left=null,right=null;double bestTip=Double.POSITIVE_INFINITY,minT=Double.POSITIVE_INFINITY,maxT=-Double.MAX_VALUE;
        for(Point p:q){double dx=p.x-cx,dy=p.y-cy,rr=dx*ox+dy*oy,tt=dx*tx+dy*ty;
            double tipCost=rr+0.35*Math.abs(tt-centroidT);if(tipCost<bestTip){bestTip=tipCost;tip=p;}
            if(rr>=minR+.62*depth){if(tt<minT){minT=tt;left=p;}if(tt>maxT){maxT=tt;right=p;}}
        }
        if(tip==null||left==null||right==null||left==right)return null;
        double w=dist(left,right),h=dist(mid(left,right),tip);if(w<.055*r||w>.38*r||h<.055*r||h>.38*r)return null;
        double ratio=w/h;if(ratio<.38||ratio>2.35)return null;
        Point base=mid(left,right);double ba=wrap180(clock(cx,cy,base.x,base.y)-angle);if(Math.abs(ba)>9.0)return null;
        double sym=Math.abs(((tip.x-cx)*tx+(tip.y-cy)*ty)-(((base.x-cx)*tx+(base.y-cy)*ty)))/Math.max(1.0,w);
        if(sym>.42)return null;
        double hintPenalty=Double.isFinite(hintR)?Math.abs(centroidR-hintR)/Math.max(1.0,r):0;
        double score=5.0*(area/(.02*Math.PI*r*r)) - 5.0*Math.abs(ba)/9.0 - 5.0*sym - 3.0*hintPenalty - 1.5*Math.abs(ratio-1.05);
        if(left.x>right.x){Point z=left;left=right;right=z;}
        return new Triangle(left,right,tip,score);
    }

    private static double tickScore(Mat g,double cx,double cy,double r,double a){
        double[]v=new double[24];int n=0;for(double rf=.84;rf<=.985;rf+=.007){double c=samplePolar(g,cx,cy,r*rf,a),l=samplePolar(g,cx,cy,r*rf,a-.72),rr=samplePolar(g,cx,cy,r*rf,a+.72);if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr))continue;v[n++]=c-(l+rr)*.5+Math.max(0,c-130)*.08;}
        if(n<6)return -100;Arrays.sort(v,0,n);int take=Math.max(4,n/3);double s=0;for(int i=n-take;i<n;i++)s+=v[i];return s/take;
    }
    private static Point tickInner(Mat g,double cx,double cy,double r,double a){
        final int N=76;double[]s=new double[N],rad=new double[N];int best=-1;double bv=-1e9;for(int i=0;i<N;i++){double rf=.82+i*(.18/(N-1));rad[i]=rf*r;double c=samplePolar(g,cx,cy,rad[i],a),l=samplePolar(g,cx,cy,rad[i],a-.72),rr=samplePolar(g,cx,cy,rad[i],a+.72);if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr)){s[i]=-999;continue;}s[i]=c-(l+rr)*.5+Math.max(0,c-135)*.05;if(s[i]>bv){bv=s[i];best=i;}}
        if(best<0||bv<6)return null;double th=Math.max(4,bv*.35);int inner=best;while(inner>0&&s[inner-1]>=th)inner--;return polar(cx,cy,rad[inner],a);
    }
    private static Point polar(double cx,double cy,double rr,double a){double t=Math.toRadians(a);return new Point(cx+Math.sin(t)*rr,cy-Math.cos(t)*rr);}
    private static double samplePolar(Mat g,double cx,double cy,double rr,double a){Point p=polar(cx,cy,rr,a);return bilinear(g,p.x,p.y);}
    private static double bilinear(Mat m,double x,double y){int x0=(int)Math.floor(x),y0=(int)Math.floor(y);if(x0<0||y0<0||x0+1>=m.cols()||y0+1>=m.rows())return Double.NaN;double fx=x-x0,fy=y-y0,a=m.get(y0,x0)[0],b=m.get(y0,x0+1)[0],c=m.get(y0+1,x0)[0],d=m.get(y0+1,x0+1)[0];return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;}
    private static double pointLineDistance(Point p,Point a,Point b){double vx=b.x-a.x,vy=b.y-a.y,len=Math.hypot(vx,vy);return len<=1e-9?Double.NaN:(vx*(p.y-a.y)-vy*(p.x-a.x))/len;}
    private static double parallelAngleDifferenceDeg(Point a0,Point a1,Point b0,Point b1){return wrap90(Math.toDegrees(Math.atan2(a1.y-a0.y,a1.x-a0.x)-Math.atan2(b1.y-b0.y,b1.x-b0.x)));}
    private static double clock(double cx,double cy,double x,double y){return Math.toDegrees(Math.atan2(x-cx,cy-y));}
    private static Point mid(Point a,Point b){return new Point((a.x+b.x)*.5,(a.y+b.y)*.5);}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    private static double wrap180(double d){while(d>180)d-=360;while(d<=-180)d+=360;return d;}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private GmtTwelveRecoveryAnalyzer(){}
}
