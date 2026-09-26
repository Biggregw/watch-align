package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
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
 * Focused 12-o'clock GMT landmark detector ported from the validated research
 * workflow. It measures the relationships a human actually inspects: top gap,
 * whole-triangle rotation, top-edge rotation, centring and 59/01 side spacing.
 * Missing or contradictory evidence fails closed.
 */
final class GmtTwelveLandmarkAnalyzer {
    static final class Result {
        final boolean valid,detectorStable;
        final String reason;
        final double topClearance,horizontalOffset,wholeAxisErrorDeg,topEdgeErrorDeg;
        final double leftClearance,rightClearance,sideAsymmetry,triangleWidthPx;
        final int inferredMinutePoints;
        Result(String reason){
            valid=false;detectorStable=false;this.reason=reason;
            topClearance=horizontalOffset=wholeAxisErrorDeg=topEdgeErrorDeg=Double.NaN;
            leftClearance=rightClearance=sideAsymmetry=triangleWidthPx=Double.NaN;inferredMinutePoints=3;
        }
        Result(double gap,double horiz,double axis,double edge,double left,double right,double side,double width,int inferred){
            valid=true;detectorStable=inferred<=1;reason="";topClearance=gap;horizontalOffset=horiz;
            wholeAxisErrorDeg=axis;topEdgeErrorDeg=edge;leftClearance=left;rightClearance=right;
            sideAsymmetry=side;triangleWidthPx=width;inferredMinutePoints=inferred;
        }
    }

    private static final class Triangle { final Point left,right,tip; Triangle(Point l,Point r,Point t){left=l;right=r;tip=t;} }
    private static final class Tick { final double x,y,w,h,area; Tick(double x,double y,double w,double h,double a){this.x=x;this.y=y;this.w=w;this.h=h;area=a;} }
    private static final class Triple { final Point left,center,right; final double score; final int inferred; Triple(Point l,Point c,Point r,double s,int n){left=l;center=c;right=r;score=s;inferred=n;} }

    static Result analyse(Mat bgr,double cx,double cy,double r,double globalRollClockDeg){
        if(bgr==null||bgr.empty()||!(r>20))return new Result("invalid dial seed");
        Mat aligned=new Mat(),gray=new Mat(),affine=new Mat();
        try{
            affine=Imgproc.getRotationMatrix2D(new Point(cx,cy),globalRollClockDeg,1.0);
            Imgproc.warpAffine(bgr,aligned,affine,new Size(bgr.cols(),bgr.rows()),Imgproc.INTER_LINEAR,Core.BORDER_REFLECT);
            Imgproc.cvtColor(aligned,gray,Imgproc.COLOR_BGR2GRAY);
            Triangle tri=triangleCandidate(gray,cx,cy,r);
            if(tri==null)return new Result("12 triangle physical contour not found");
            Triple ticks=minuteTicks(gray,cx,cy,r,tri.left,tri.right);
            if(ticks==null)return new Result("59/60/01 minute-track sequence not sufficiently constrained");

            Point topMid=mid(tri.left,tri.right);
            double width=dist(tri.left,tri.right);if(width<=1e-9)return new Result("triangle top edge is degenerate");
            double gap=signedPointLineDistance(topMid,ticks.left,ticks.right)/width;
            double axisX=xOnLineAtY(topMid,tri.tip,ticks.center.y);
            double horiz=(axisX-ticks.center.x)/width;
            double edge=parallelAngleDifferenceDeg(tri.left,tri.right,ticks.left,ticks.right);

            double triAxis=Math.atan2(tri.tip.y-topMid.y,tri.tip.x-topMid.x);
            double track=Math.atan2(ticks.right.y-ticks.left.y,ticks.right.x-ticks.left.x);
            double normal=track+Math.PI/2.0; // left->right track normal points inward/down near 12
            double axisErr=wrap90(Math.toDegrees(triAxis-normal));

            double left=dist(tri.left,ticks.left)/width,right=dist(tri.right,ticks.right)/width;
            return new Result(gap,horiz,axisErr,edge,left,right,right-left,width,ticks.inferred);
        }catch(Throwable t){return new Result("12 local geometry failed: "+t.getClass().getSimpleName());}
        finally{gray.release();aligned.release();affine.release();}
    }

    private static Triangle triangleCandidate(Mat gray,double cx,double cy,double r){
        int x0=Math.max(0,(int)(cx-.18*r)),x1=Math.min(gray.cols(),(int)(cx+.18*r));
        int y0=Math.max(0,(int)(cy-.90*r)),y1=Math.min(gray.rows(),(int)(cy-.30*r));
        if(x1<=x0||y1<=y0)return null;
        Rect roi=new Rect(x0,y0,x1-x0,y1-y0);Mat p=gray.submat(roi),bw=new Mat();
        List<MatOfPoint> contours=new ArrayList<>();Mat hier=new Mat();Triangle best=null;double bestArea=-1;
        try{
            Imgproc.threshold(p,bw,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
            Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));Imgproc.morphologyEx(bw,bw,Imgproc.MORPH_OPEN,kernel);kernel.release();
            Imgproc.findContours(bw,contours,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
            for(MatOfPoint c:contours){
                double area=Imgproc.contourArea(c);if(area<4)continue;
                Point[] raw=c.toArray();if(raw.length<3)continue;
                double minx=Double.POSITIVE_INFINITY,maxx=-1,miny=Double.POSITIVE_INFINITY,maxy=-1;
                for(Point q:raw){minx=Math.min(minx,q.x);maxx=Math.max(maxx,q.x);miny=Math.min(miny,q.y);maxy=Math.max(maxy,q.y);}
                if(minx<=0||maxx>=roi.width-1||miny<=0||maxy>=roi.height-1)continue;
                double xs=maxx-minx,ys=maxy-miny;if(xs<.10*r||xs>.28*r||ys<.12*r||ys>.30*r)continue;

                MatOfInt hi=new MatOfInt();Imgproc.convexHull(c,hi);int[] idx=hi.toArray();Point[] hp=new Point[idx.length];
                for(int i=0;i<idx.length;i++)hp[i]=raw[idx[i]];
                MatOfPoint2f hull=new MatOfPoint2f(hp),poly=new MatOfPoint2f();double per=Imgproc.arcLength(hull,true);
                if(per<=0){hi.release();hull.release();poly.release();continue;}
                Imgproc.approxPolyDP(hull,poly,.03*per,true);Point[] q=poly.toArray();hi.release();hull.release();poly.release();
                if(q.length<3||q.length>6)continue;
                Arrays.sort(q,Comparator.comparingDouble(a->a.y));Point u0=q[0],u1=q[1],tip=q[q.length-1];
                Point l=u0.x<=u1.x?u0:u1,rr=u0.x<=u1.x?u1:u0;
                double w=rr.x-l.x,h=tip.y-(l.y+rr.y)/2.0;if(w<=0||h<=0||w/h<.55||w/h>1.65)continue;
                double mid=(l.x+rr.x)/2.0;if(Math.abs(tip.x-mid)>.22*w||Math.abs((mid+roi.x)-cx)>.10*r)continue;
                Triangle cand=new Triangle(new Point(l.x+roi.x,l.y+roi.y),new Point(rr.x+roi.x,rr.y+roi.y),new Point(tip.x+roi.x,tip.y+roi.y));
                if(area>bestArea){bestArea=area;best=cand;}
            }
            return best;
        }finally{for(MatOfPoint c:contours)c.release();hier.release();bw.release();p.release();}
    }

    private static Triple minuteTicks(Mat gray,double cx,double cy,double r,Point tl,Point tr){
        double mid=(tl.x+tr.x)/2.0,top=(tl.y+tr.y)/2.0;
        int x0=Math.max(0,(int)(mid-.42*r)),x1=Math.min(gray.cols(),(int)(mid+.42*r));
        int y0Full=Math.max(0,(int)(top-.20*r)),y1=Math.min(gray.rows(),(int)(top+.035*r));
        if(x1<=x0||y1<=y0Full)return null;
        int y0Trim=trimBezelBand(gray,x0,y0Full,x1,y1);
        int[] bands=y0Trim==y0Full?new int[]{y0Full}:new int[]{y0Full,y0Trim};
        Triple bestDirect=null,bestSeq=null;
        for(int y0:bands){
            Mat patch=gray.submat(new Rect(x0,y0,x1-x0,y1-y0));
            List<Mat> masks=tickMasks(patch);
            try{
                for(Mat mask:masks){
                    List<Tick> ts=ticks(mask,x0,y0,r);if(ts.size()<3)continue;
                    Triple d=direct(ts,mid,r,cx,cy);if(d!=null&&(bestDirect==null||d.score<bestDirect.score))bestDirect=d;
                    Triple s=sequence(ts,mid,r,cx,cy);if(s!=null&&(bestSeq==null||s.score<bestSeq.score))bestSeq=s;
                }
            }finally{for(Mat m:masks)m.release();patch.release();}
        }
        return bestDirect!=null?bestDirect:bestSeq;
    }

    private static List<Mat> tickMasks(Mat p){
        List<Mat> out=new ArrayList<>();
        Mat a=new Mat();Imgproc.threshold(p,a,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);out.add(a);
        Mat c=new Mat();CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,4));clahe.apply(p,c);
        Mat b=new Mat();Imgproc.threshold(c,b,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);out.add(b);
        int kh=Math.max(5,(p.rows()/2)|1),kw=Math.max(9,(p.cols()/12)|1);
        Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(kw,kh)),top=new Mat();Imgproc.morphologyEx(c,top,Imgproc.MORPH_TOPHAT,k);k.release();c.release();
        Mat d=new Mat();Imgproc.threshold(top,d,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);top.release();out.add(d);return out;
    }

    private static List<Tick> ticks(Mat input,int x0,int y0,double r){
        Mat mask=new Mat();input.copyTo(mask);Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,1));Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,k);k.release();
        List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();List<Tick> out=new ArrayList<>();
        try{
            Imgproc.findContours(mask,cs,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_SIMPLE);
            for(MatOfPoint c:cs){Rect q=Imgproc.boundingRect(c);double area=Math.abs(Imgproc.contourArea(c));
                if(area<4||q.height<.015*r||q.height>.11*r||q.width>.050*r||q.height<1.2*Math.max(q.width,1))continue;
                out.add(new Tick(x0+q.x+q.width/2.0,y0+q.y+q.height-1.0,q.width,q.height,area));}
            out.sort(Comparator.comparingDouble(t->t.x));return out;
        }finally{for(MatOfPoint c:cs)c.release();hier.release();mask.release();}
    }

    private static Triple direct(List<Tick> t,double mid,double r,double cx,double cy){
        Triple best=null;
        for(int i=1;i<t.size()-1;i++){
            Tick l=t.get(i-1),c=t.get(i),rr=t.get(i+1);double p1=c.x-l.x,p2=rr.x-c.x;
            if(p1<=.025*r||p2<=.025*r||p1>.11*r||p2>.11*r)continue;
            double pitch=(p1+p2)/2,reg=Math.abs(p1-p2)/pitch,axis=Math.abs(c.x-mid)/pitch;if(reg>.30||axis>.45)continue;
            double ys=Math.max(l.y,Math.max(c.y,rr.y))-Math.min(l.y,Math.min(c.y,rr.y));if(ys>.055*r)continue;
            Triple regTri=regularize(new Point(l.x,l.y),new Point(c.x,c.y),new Point(rr.x,rr.y),cx,cy,reg+.35*axis+.20*ys/r,0);
            if(regTri!=null&&(best==null||regTri.score<best.score))best=regTri;
        }return best;
    }

    private static Triple sequence(List<Tick> t,double mid,double r,double cx,double cy){
        if(t.size()<3)return null;Triple best=null;int n=t.size();double[] xs=new double[n],ys=new double[n];for(int i=0;i<n;i++){xs[i]=t.get(i).x;ys[i]=t.get(i).y;}
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            double dx=xs[j]-xs[i];for(int steps=1;steps<=4;steps++){
                double pitch=dx/steps;if(pitch<=.025*r||pitch>.11*r)continue;
                double x60=xs[i]+Math.rint((mid-xs[i])/pitch)*pitch;
                List<Integer> use=new ArrayList<>();for(int z=0;z<n;z++){int kk=(int)Math.rint((xs[z]-x60)/pitch);if(Math.abs(kk)<=5&&Math.abs(xs[z]-(x60+kk*pitch))<=.22*pitch)use.add(z);}
                if(use.size()<3)continue;
                boolean neg=false,pos=false,zero=false;for(int z:use){int kk=(int)Math.rint((xs[z]-x60)/pitch);neg|=kk<0;pos|=kk>0;zero|=kk==0;}if(!((neg&&pos)||(zero&&(neg||pos))))continue;
                double sk=0,sx=0,skk=0,skx=0;for(int z:use){double kk=Math.rint((xs[z]-x60)/pitch);sk+=kk;sx+=xs[z];skk+=kk*kk;skx+=kk*xs[z];}
                double den=use.size()*skk-sk*sk;if(Math.abs(den)<1e-9)continue;double pf=(use.size()*skx-sk*sx)/den,x60f=(sx-pf*sk)/use.size();if(pf<=.025*r||pf>.11*r)continue;
                double maxFit=0;for(int z:use){double kk=Math.rint((xs[z]-x60f)/pf);maxFit=Math.max(maxFit,Math.abs(xs[z]-(x60f+kk*pf))/pf);}if(maxFit>.24)continue;
                double axis=Math.abs(x60f-mid)/pf;if(axis>.60)continue;
                double[] line=robustLine(use,xs,ys,x60f,pf);if(line==null)continue;double y0=line[0],slope=line[1];double y59=y0-slope,y60=y0,y1=y0+slope;if(Math.max(y59,Math.max(y60,y1))-Math.min(y59,Math.min(y60,y1))>.055*r)continue;
                int obs=0;for(int q=-1;q<=1;q++){boolean found=false;for(int z:use){int kk=(int)Math.rint((xs[z]-x60f)/pf);if(kk==q){found=true;break;}}if(found)obs++;}int inf=3-obs;if(inf>0&&use.size()<4&&obs<2)continue;
                double meanFit=maxFit,score=meanFit+.80*axis+.08*inf+.02*Math.abs(slope/Math.max(pf,1));
                Triple cand=regularize(new Point(x60f-pf,y59),new Point(x60f,y60),new Point(x60f+pf,y1),cx,cy,score,inf);
                if(cand!=null&&(best==null||cand.score<best.score))best=cand;
            }
        }return best;
    }

    private static double[] robustLine(List<Integer> use,double[] xs,double[] ys,double x60,double pitch){
        boolean[] keep=new boolean[use.size()];Arrays.fill(keep,true);double a=Double.NaN,b=Double.NaN;
        for(int iter=0;iter<3;iter++){
            double sk=0,sy=0,skk=0,sky=0;int n=0;for(int i=0;i<use.size();i++)if(keep[i]){int z=use.get(i);double k=Math.rint((xs[z]-x60)/pitch);sk+=k;sy+=ys[z];skk+=k*k;sky+=k*ys[z];n++;}
            if(n<3)return null;double den=n*skk-sk*sk;if(Math.abs(den)<1e-9){b=0;a=sy/n;}else{b=(n*sky-sk*sy)/den;a=(sy-b*sk)/n;}
            double[] res=new double[n];int ri=0;for(int i=0;i<use.size();i++)if(keep[i]){int z=use.get(i);double k=Math.rint((xs[z]-x60)/pitch);res[ri++]=ys[z]-(a+b*k);}double med=median(res),madArr[]=new double[res.length];for(int i=0;i<res.length;i++)madArr[i]=Math.abs(res[i]-med);double lim=Math.max(1.5,2.8*1.4826*median(madArr));boolean changed=false;
            for(int i=0;i<use.size();i++)if(keep[i]){int z=use.get(i);double k=Math.rint((xs[z]-x60)/pitch);if(Math.abs((ys[z]-(a+b*k))-med)>lim){keep[i]=false;changed=true;}}
            if(!changed)break;
        }return new double[]{a,b};
    }

    private static Triple regularize(Point l,Point c,Point rr,double cx,double cy,double score,int inferred){
        double dx=c.x-cx,dy=c.y-cy;if(Math.abs(dy)<1e-6)return new Triple(l,c,rr,score,inferred);
        double slope=-dx/dy;if(Math.abs(slope)>.35)return null;
        Point a=new Point(l.x,c.y+slope*(l.x-c.x)),b=new Point(c.x,c.y),d=new Point(rr.x,c.y+slope*(rr.x-c.x));
        return new Triple(a,b,d,score,inferred);
    }

    private static int trimBezelBand(Mat gray,int x0,int y0,int x1,int y1){
        int h=y1-y0;if(h<12)return y0;int minRun=Math.max(3,(int)Math.round((8.0/109.0)*h)),margin=Math.max(3,(int)Math.round((15.0/109.0)*h));
        double[] frac=new double[h];for(int y=0;y<h;y++){int bright=0,n=0;for(int x=x0;x<x1;x++){double[]v=gray.get(y0+y,x);if(v!=null){n++;if(v[0]>140)bright++;}}frac[y]=n>0?bright/(double)n:0;}
        double peak=0;for(double f:frac)peak=Math.max(peak,f);if(peak<.20)return y0;double cutoff=.5*peak;int runEnd=-1,i=0;
        while(i<h/2){if(frac[i]>=cutoff){int j=i;while(j<h/2&&frac[j]>=cutoff)j++;if(j-i>=minRun)runEnd=j-1;i=j;}else i++;}
        return runEnd<0?y0:y0+Math.min(runEnd+margin,h-1);
    }

    private static Point mid(Point a,Point b){return new Point((a.x+b.x)/2.0,(a.y+b.y)/2.0);}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}
    private static double signedPointLineDistance(Point p,Point a,Point b){double vx=b.x-a.x,vy=b.y-a.y,len=Math.hypot(vx,vy);if(len<=1e-9)throw new IllegalArgumentException("degenerate minute line");double wx=p.x-a.x,wy=p.y-a.y;return (vx*wy-vy*wx)/len;}
    private static double xOnLineAtY(Point a,Point b,double y){double dy=b.y-a.y;if(Math.abs(dy)<=1e-9)throw new IllegalArgumentException("degenerate triangle axis");double t=(y-a.y)/dy;return a.x+t*(b.x-a.x);}
    private static double parallelAngleDifferenceDeg(Point a0,Point a1,Point b0,Point b1){double d=Math.toDegrees(Math.atan2(a1.y-a0.y,a1.x-a0.x)-Math.atan2(b1.y-b0.y,b1.x-b0.x));return wrap90(d);}
    private static double wrap90(double d){while(d>=90)d-=180;while(d< -90)d+=180;return d;}
    private static double median(double[]x){double[]c=x.clone();Arrays.sort(c);int n=c.length;return n==0?Double.NaN:(n%2==1?c[n/2]:(c[n/2-1]+c[n/2])*.5);}
    private GmtTwelveLandmarkAnalyzer(){}
}
