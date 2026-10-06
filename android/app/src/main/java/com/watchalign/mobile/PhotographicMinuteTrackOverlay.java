package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Strict photographic proof of the minute-track perspective idea.
 *
 * Both the reference dial and candidate photo are real photographs selected by the user.
 * The 48 minor minute positions are detected independently in each image. Matching minor
 * ticks alone fit a robust homography from reference-photo pixels to candidate-photo pixels.
 * Hour markers, the 12 triangle, dial text and date window never contribute to the fit.
 * The result is the actual reference dial photograph warped into candidate perspective.
 */
final class PhotographicMinuteTrackOverlay {
    static final class Result {
        final Bitmap overlay;
        final boolean valid;
        final String reason;
        final int referenceTicks, candidateTicks, matchedTicks, inlierTicks;
        final double meanInlierErrorPx;

        Result(Bitmap b,int rt,int ct,int mt,int it,double err){
            overlay=b;valid=b!=null;reason="";referenceTicks=rt;candidateTicks=ct;matchedTicks=mt;inlierTicks=it;meanInlierErrorPx=err;
        }
        Result(String why,int rt,int ct,int mt){
            overlay=null;valid=false;reason=why;referenceTicks=rt;candidateTicks=ct;matchedTicks=mt;inlierTicks=0;meanInlierErrorPx=Double.NaN;
        }
    }

    private static final double SEARCH_A_DEG=3.25;
    private static final double SEARCH_A_STEP_DEG=0.10;
    private static final double SIDE_A_DEG=0.72;
    private static final int MIN_MATCHED=18;
    private static final int MIN_INLIERS=14;

    private static final class Tick {
        final int minute; final double x,y,score;
        Tick(int m,double x,double y,double s){minute=m;this.x=x;this.y=y;score=s;}
    }

    private PhotographicMinuteTrackOverlay(){}

    static Result build(Bitmap reference,double rcx,double rcy,double rr,double rroll,
                        Bitmap candidate,double ccx,double ccy,double cr,double croll){
        if(reference==null||candidate==null||!(rr>35)||!(cr>35))return new Result("invalid reference or candidate alignment",0,0,0);
        Mat rgbaR=new Mat(),rgbaC=new Mat(),grayR=new Mat(),grayC=new Mat(),enhR=new Mat(),enhC=new Mat();
        try{
            Utils.bitmapToMat(reference,rgbaR); Utils.bitmapToMat(candidate,rgbaC);
            Imgproc.cvtColor(rgbaR,grayR,Imgproc.COLOR_RGBA2GRAY); Imgproc.cvtColor(rgbaC,grayC,Imgproc.COLOR_RGBA2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8)); clahe.apply(grayR,enhR); clahe.apply(grayC,enhC);

            Tick[] a=detectTicks(enhR,rcx,rcy,rr,rroll);
            Tick[] b=detectTicks(enhC,ccx,ccy,cr,croll);
            int na=count(a),nb=count(b);
            List<Tick> src=new ArrayList<>(),dst=new ArrayList<>();
            for(int m=0;m<60;m++) if(m%5!=0&&a[m]!=null&&b[m]!=null){src.add(a[m]);dst.add(b[m]);}
            if(src.size()<MIN_MATCHED)return new Result("not enough of the same minor minute ticks were found in both photos",na,nb,src.size());

            Mat H=fit(src,dst,cr);
            if(H==null||H.empty())return new Result("photographic minute-track homography could not be fitted",na,nb,src.size());
            try{
                double threshold=Math.max(3.0,cr*0.012);
                int inliers=0;double sum=0;
                for(int i=0;i<src.size();i++){
                    Point p=project(H,src.get(i).x,src.get(i).y); if(p==null)continue;
                    double e=Math.hypot(p.x-dst.get(i).x,p.y-dst.get(i).y);
                    if(e<=threshold){inliers++;sum+=e;}
                }
                if(inliers<MIN_INLIERS)return new Result("minute-track correspondence did not produce enough inliers",na,nb,src.size());
                if(!sane(H,reference,candidate,rcx,rcy,rr,ccx,ccy,cr))return new Result("minute-track transform was geometrically unstable",na,nb,src.size());
                Bitmap overlay=warpReference(reference,H,candidate.getWidth(),candidate.getHeight(),rcx,rcy,rr);
                if(overlay==null)return new Result("photographic warp could not be rendered",na,nb,src.size());
                return new Result(overlay,na,nb,src.size(),inliers,sum/Math.max(1,inliers));
            }finally{H.release();}
        }catch(Throwable t){
            return new Result("photographic overlay failed: "+t.getClass().getSimpleName(),0,0,0);
        }finally{enhR.release();enhC.release();grayR.release();grayC.release();rgbaR.release();rgbaC.release();}
    }

    private static Tick[] detectTicks(Mat g,double cx,double cy,double r,double roll){
        Tick[] out=new Tick[60];
        for(int minute=0;minute<60;minute++){
            if(minute%5==0)continue;
            Tick t=locateTick(g,cx,cy,r,minute,roll+minute*6.0);
            if(t!=null)out[minute]=t;
        }
        return out;
    }
    private static int count(Tick[] a){int n=0;for(Tick t:a)if(t!=null)n++;return n;}

    private static Tick locateTick(Mat g,double cx,double cy,double r,int minute,double expectedDeg){
        double bestA=Double.NaN,best=-Double.MAX_VALUE;
        for(double a=expectedDeg-SEARCH_A_DEG;a<=expectedDeg+SEARCH_A_DEG+1e-9;a+=SEARCH_A_STEP_DEG){
            double s=tickScore(g,cx,cy,r,a);if(s>best){best=s;bestA=a;}
        }
        if(!Double.isFinite(bestA)||best<2.0)return null;
        final double lo=.865,hi=.980,step=.0025;
        int n=(int)Math.floor((hi-lo)/step)+1;double[] rf=new double[n],v=new double[n];double max=-Double.MAX_VALUE;
        for(int i=0;i<n;i++){
            double q=lo+i*step;rf[i]=q;
            double c=samplePolar(g,cx,cy,r*q,bestA),l=samplePolar(g,cx,cy,r*q,bestA-SIDE_A_DEG),rr=samplePolar(g,cx,cy,r*q,bestA+SIDE_A_DEG);
            if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr))v[i]=-100;
            else v[i]=c-.5*(l+rr)+Math.max(0,c-145)*.06;
            if(v[i]>max)max=v[i];
        }
        if(max<5.0)return null;
        double th=Math.max(3.0,max*.40),sw=0,sr=0;
        for(int i=0;i<n;i++){
            if(v[i]<th)continue;
            boolean neighbour=(i>0&&v[i-1]>=th*.72)||(i+1<n&&v[i+1]>=th*.72);if(!neighbour)continue;
            double w=Math.max(.1,v[i]-th+.5);sw+=w;sr+=w*rf[i];
        }
        if(sw<=.5)return null;double q=sr/sw;if(q<.875||q>.968)return null;
        double a=Math.toRadians(bestA);double x=cx+Math.sin(a)*r*q,y=cy-Math.cos(a)*r*q;
        return new Tick(minute,x,y,best);
    }

    private static double tickScore(Mat g,double cx,double cy,double r,double aDeg){
        double[] vals=new double[40];int n=0;
        for(double q=.865;q<=.980&&n<vals.length;q+=.0065){
            double c=samplePolar(g,cx,cy,r*q,aDeg),l=samplePolar(g,cx,cy,r*q,aDeg-SIDE_A_DEG),rr=samplePolar(g,cx,cy,r*q,aDeg+SIDE_A_DEG);
            if(!Double.isFinite(c)||!Double.isFinite(l)||!Double.isFinite(rr))continue;
            vals[n++]=c-.5*(l+rr)+Math.max(0,c-145)*.06;
        }
        if(n<6)return -100;Arrays.sort(vals,0,n);int take=Math.max(4,n/3);double s=0;for(int i=n-take;i<n;i++)s+=vals[i];return s/take;
    }

    private static double samplePolar(Mat g,double cx,double cy,double radius,double aDeg){
        double a=Math.toRadians(aDeg);return sample(g,cx+Math.sin(a)*radius,cy-Math.cos(a)*radius);
    }
    private static double sample(Mat g,double x,double y){
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);if(x0<0||y0<0||x0+1>=g.cols()||y0+1>=g.rows())return Double.NaN;
        double fx=x-x0,fy=y-y0;double[] a=g.get(y0,x0),b=g.get(y0,x0+1),c=g.get(y0+1,x0),d=g.get(y0+1,x0+1);if(a==null||b==null||c==null||d==null)return Double.NaN;
        return (1-fy)*((1-fx)*a[0]+fx*b[0])+fy*((1-fx)*c[0]+fx*d[0]);
    }

    private static Mat fit(List<Tick> src,List<Tick> dst,double candidateR){
        Point[] a=new Point[src.size()],b=new Point[dst.size()];for(int i=0;i<a.length;i++){a[i]=new Point(src.get(i).x,src.get(i).y);b[i]=new Point(dst.get(i).x,dst.get(i).y);}
        MatOfPoint2f sm=new MatOfPoint2f(a),dm=new MatOfPoint2f(b);Mat mask=new Mat();
        try{
            double threshold=Math.max(3.0,candidateR*.012);Mat rough=Calib3d.findHomography(sm,dm,Calib3d.RANSAC,threshold,mask);if(rough==null||rough.empty())return rough;
            List<Point> ia=new ArrayList<>(),ib=new ArrayList<>();for(int i=0;i<a.length;i++){double[] z=mask.get(i,0);if(z!=null&&z.length>0&&z[0]!=0){ia.add(a[i]);ib.add(b[i]);}}
            if(ia.size()<MIN_INLIERS)return rough;
            MatOfPoint2f rs=new MatOfPoint2f(),rd=new MatOfPoint2f();rs.fromList(ia);rd.fromList(ib);
            try{Mat refined=Calib3d.findHomography(rs,rd,0);if(refined!=null&&!refined.empty()){rough.release();return refined;}return rough;}finally{rs.release();rd.release();}
        }finally{sm.release();dm.release();mask.release();}
    }

    private static boolean sane(Mat H,Bitmap reference,Bitmap candidate,double rcx,double rcy,double rr,double ccx,double ccy,double cr){
        Point c=project(H,rcx,rcy);if(c==null)return false;
        if(Math.hypot(c.x-ccx,c.y-ccy)>.18*cr)return false;
        if(c.x<-.1*candidate.getWidth()||c.x>1.1*candidate.getWidth()||c.y<-.1*candidate.getHeight()||c.y>1.1*candidate.getHeight())return false;
        Point[] edge={new Point(rcx,rcy-rr),new Point(rcx+rr,rcy),new Point(rcx,rcy+rr),new Point(rcx-rr,rcy)};
        for(Point e:edge){Point p=project(H,e.x,e.y);if(p==null)return false;double d=Math.hypot(p.x-c.x,p.y-c.y);if(d<.55*cr||d>1.55*cr)return false;}
        return true;
    }

    private static Bitmap warpReference(Bitmap reference,Mat H,int outW,int outH,double cx,double cy,double r){
        Mat src=new Mat(),masked=new Mat(),mask=Mat.zeros(reference.getHeight(),reference.getWidth(),CvType.CV_8UC1),warped=new Mat();
        try{
            Utils.bitmapToMat(reference,src);masked=Mat.zeros(src.size(),src.type());
            Imgproc.circle(mask,new Point(cx,cy),(int)Math.round(r*.992),new Scalar(255),-1,Imgproc.LINE_AA,0);src.copyTo(masked,mask);
            Imgproc.warpPerspective(masked,warped,H,new Size(outW,outH),Imgproc.INTER_LINEAR,Core.BORDER_CONSTANT,new Scalar(0,0,0,0));
            Bitmap out=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);Utils.matToBitmap(warped,out);return out;
        }catch(Throwable t){return null;}finally{src.release();masked.release();mask.release();warped.release();}
    }

    private static Point project(Mat H,double x,double y){
        if(H==null||H.empty())return null;double h00=v(H,0,0),h01=v(H,0,1),h02=v(H,0,2),h10=v(H,1,0),h11=v(H,1,1),h12=v(H,1,2),h20=v(H,2,0),h21=v(H,2,1),h22=v(H,2,2);double d=h20*x+h21*y+h22;if(!Double.isFinite(d)||Math.abs(d)<1e-10)return null;double px=(h00*x+h01*y+h02)/d,py=(h10*x+h11*y+h12)/d;return Double.isFinite(px)&&Double.isFinite(py)?new Point(px,py):null;
    }
    private static double v(Mat m,int r,int c){double[] z=m.get(r,c);return z==null||z.length==0?Double.NaN:z[0];}
}
