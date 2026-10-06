package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

/**
 * Coarse GMT dial locator for the strict fixed-master overlay proof.
 *
 * IMPORTANT: this class deliberately uses NO hour markers, triangle, date, text,
 * hands or minute-track geometry. It only looks for a circular/elliptical physical
 * black-dial boundary: dark inside, brighter outside, with a predominantly dark
 * dial interior. Its only job is to provide a coarse centre/radius so the physical
 * dial-edge fitter and minor-minute pose solver can start in the correct place.
 */
final class StrictDialBoundarySeedAnalyzer {
    static final class Result {
        final boolean valid;
        final double x,y,r;
        final String reason;
        Result(String reason){valid=false;x=y=r=Double.NaN;this.reason=reason;}
        Result(double x,double y,double r){valid=true;this.x=x;this.y=y;this.r=r;reason="";}
    }

    private static final int HOUGH_MAX_SIDE=480;

    private static final class Boundary {
        final double strength,positiveFraction,coverage;
        Boundary(double s,double p,double c){strength=s;positiveFraction=p;coverage=c;}
    }
    private static final class RadiusFit {
        final double r,boundary,positiveFraction,coverage;
        RadiusFit(double r,double b,double p,double c){this.r=r;boundary=b;positiveFraction=p;coverage=c;}
    }
    private static final class Candidate {
        final double x,y,r,score;
        Candidate(double x,double y,double r,double score){this.x=x;this.y=y;this.r=r;this.score=score;}
    }

    static Result analyse(Mat bgr){
        if(bgr==null||bgr.empty())return new Result("image unavailable");
        Mat gray=new Mat(),blur=new Mat(),circles=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(7,7),0);
            int min=Math.min(bgr.cols(),bgr.rows());
            int minR=Math.max(24,(int)Math.round(min*0.10));
            int maxR=Math.max(minR+8,(int)Math.round(min*0.46));
            hough(blur,circles,min,115,22,minR,maxR);
            Candidate best=bestCandidate(gray,circles,min,minR,maxR);

            if(best==null){
                Mat eq=new Mat(),c2=new Mat();
                try{
                    Imgproc.createCLAHE(2.4,new Size(8,8)).apply(gray,eq);
                    Imgproc.GaussianBlur(eq,eq,new Size(7,7),0);
                    hough(eq,c2,min,112,20,minR,maxR);
                    best=bestCandidate(eq,c2,min,minR,maxR);
                }finally{c2.release();eq.release();}
            }
            return best==null?new Result("physical black-dial boundary not found"):
                    new Result(best.x,best.y,best.r);
        }finally{circles.release();blur.release();gray.release();}
    }

    private static Candidate bestCandidate(Mat gray,Mat circles,int min,int minR,int maxR){
        Candidate best=null;
        for(int i=0;i<circles.cols();i++){
            double[] c=circles.get(0,i);if(c==null||c.length<3)continue;
            Candidate q=scoreCentre(gray,c[0],c[1],min,minR,maxR);
            if(q!=null&&(best==null||q.score>best.score))best=q;
        }
        return best;
    }

    private static Candidate scoreCentre(Mat g,double cx,double cy,int min,int minR,int maxR){
        double nx=cx/g.cols(),ny=cy/g.rows();
        if(nx<.03||nx>.97||ny<.03||ny>.97)return null;
        RadiusFit fit=refineRadius(g,cx,cy,minR,maxR);
        if(fit==null||fit.boundary<.045||fit.coverage<.55||fit.positiveFraction<.56)return null;

        double core=darkFraction(g,cx,cy,fit.r*.56);
        double ann=annulusDark(g,cx,cy,fit.r*.61,fit.r*.88);
        if(core<.38||ann<.28)return null;

        double boundaryFit=clamp01((fit.boundary-.035)/.19);
        double polarityFit=clamp01((fit.positiveFraction-.50)/.38);
        double darkFit=clamp01((.60*core+.40*ann)/.70);
        double score=.56*boundaryFit+.20*polarityFit+.24*darkFit;
        return new Candidate(cx,cy,fit.r,score);
    }

    private static RadiusFit refineRadius(Mat g,double cx,double cy,int minR,int maxR){
        int border=(int)Math.floor(Math.min(Math.min(cx,g.cols()-1.0-cx),Math.min(cy,g.rows()-1.0-cy)))-3;
        int hi=Math.min(maxR,border>minR?border:maxR);
        if(hi<=minR+8)return null;

        RadiusFit strongest=null;
        for(int r=minR;r<=hi;r+=2){
            Boundary b=signedBoundary(g,cx,cy,r);if(b.coverage<.55)continue;
            double merit=b.strength+0.055*b.positiveFraction;
            if(strongest==null||merit>strongest.boundary+0.055*strongest.positiveFraction)
                strongest=new RadiusFit(r,b.strength,b.positiveFraction,b.coverage);
        }
        if(strongest==null)return null;

        // Prefer the innermost persistent dark-inside/bright-outside ring of adequate
        // strength. This rejects the brighter case/bezel boundary outside the true dial.
        double floor=Math.max(.045,.30*strongest.boundary);
        int lo=(int)Math.max(minR,Math.round(.55*strongest.r));
        int top=(int)Math.min(hi,Math.round(.98*strongest.r));
        RadiusFit inner=null;Boundary prev=null,cur=null;
        for(int r=lo;r<=top;r+=2){
            Boundary next=signedBoundary(g,cx,cy,r);
            if(cur!=null&&prev!=null&&cur.coverage>=.55&&cur.strength>=floor&&cur.positiveFraction>=.56
                    &&cur.strength>=prev.strength&&cur.strength>=next.strength){
                inner=new RadiusFit(r-2,cur.strength,cur.positiveFraction,cur.coverage);break;
            }
            prev=cur;cur=next;
        }
        RadiusFit best=inner!=null?inner:strongest;

        RadiusFit fine=best;
        int flo=(int)Math.max(minR,Math.round(best.r)-3),fhi=(int)Math.min(hi,Math.round(best.r)+3);
        for(int r=flo;r<=fhi;r++){
            Boundary b=signedBoundary(g,cx,cy,r);if(b.coverage<.55)continue;
            double merit=b.strength+0.055*b.positiveFraction;
            if(merit>fine.boundary+0.055*fine.positiveFraction)
                fine=new RadiusFit(r,b.strength,b.positiveFraction,b.coverage);
        }
        return fine;
    }

    private static Boundary signedBoundary(Mat g,double cx,double cy,double r){
        double delta=Math.max(3.0,Math.min(10.0,r*.025));
        double[] diff=new double[72];int n=0,pos=0;
        for(int deg=0;deg<360;deg+=5){
            double a=Math.toRadians(deg),co=Math.cos(a),si=Math.sin(a);
            int xi=(int)Math.round(cx+co*(r-delta)),yi=(int)Math.round(cy+si*(r-delta));
            int xo=(int)Math.round(cx+co*(r+delta)),yo=(int)Math.round(cy+si*(r+delta));
            if(!inside(g,xi,yi)||!inside(g,xo,yo))continue;
            double[] vi=g.get(yi,xi),vo=g.get(yo,xo);if(vi==null||vo==null)continue;
            double d=(vo[0]-vi[0])/255.0;diff[n++]=d;if(d>.025)pos++;
        }
        if(n<24)return new Boundary(Double.NEGATIVE_INFINITY,0,n/72.0);
        double[] used=Arrays.copyOf(diff,n);Arrays.sort(used);
        double med=n%2==1?used[n/2]:(used[n/2-1]+used[n/2])*.5;
        return new Boundary(med,pos/(double)n,n/72.0);
    }

    private static void hough(Mat img,Mat circles,int min,double p1,double p2,int minR,int maxR){
        double s=Math.min(1.0,HOUGH_MAX_SIDE/(double)min);
        if(s>=.999){
            Imgproc.HoughCircles(img,circles,Imgproc.HOUGH_GRADIENT,1.10,min/10.0,p1,p2,minR,maxR);return;
        }
        Mat small=new Mat(),c=new Mat();
        try{
            Imgproc.resize(img,small,new Size(Math.round(img.cols()*s),Math.round(img.rows()*s)),0,0,Imgproc.INTER_AREA);
            Imgproc.HoughCircles(small,c,Imgproc.HOUGH_GRADIENT,1.10,min*s/10.0,p1,p2,
                    (int)Math.max(8,Math.round(minR*s)),(int)Math.round(maxR*s));
            if(c.cols()==0){c.copyTo(circles);return;}
            Mat out=new Mat(1,c.cols(),c.type());
            for(int i=0;i<c.cols();i++){
                double[] v=c.get(0,i);out.put(0,i,v[0]/s,v[1]/s,v[2]/s);
            }
            out.copyTo(circles);out.release();
        }finally{c.release();small.release();}
    }

    private static double darkFraction(Mat g,double cx,double cy,double rr){
        int step=Math.max(2,(int)Math.round(rr/50.0)),n=0,d=0;
        int x0=Math.max(0,(int)(cx-rr)),x1=Math.min(g.cols()-1,(int)(cx+rr));
        int y0=Math.max(0,(int)(cy-rr)),y1=Math.min(g.rows()-1,(int)(cy+rr));
        for(int y=y0;y<=y1;y+=step)for(int x=x0;x<=x1;x+=step){
            if(Math.hypot(x-cx,y-cy)>rr)continue;double[]v=g.get(y,x);if(v==null)continue;n++;if(v[0]<145)d++;
        }
        return n>0?d/(double)n:0;
    }
    private static double annulusDark(Mat g,double cx,double cy,double ri,double ro){
        int step=Math.max(2,(int)Math.round(ro/55.0)),n=0,d=0;
        int x0=Math.max(0,(int)(cx-ro)),x1=Math.min(g.cols()-1,(int)(cx+ro));
        int y0=Math.max(0,(int)(cy-ro)),y1=Math.min(g.rows()-1,(int)(cy+ro));
        for(int y=y0;y<=y1;y+=step)for(int x=x0;x<=x1;x+=step){
            double q=Math.hypot(x-cx,y-cy);if(q<ri||q>ro)continue;double[]v=g.get(y,x);if(v==null)continue;n++;if(v[0]<145)d++;
        }
        return n>0?d/(double)n:0;
    }
    private static boolean inside(Mat m,int x,int y){return x>=0&&y>=0&&x<m.cols()&&y<m.rows();}
    private static double clamp01(double v){return Math.max(0,Math.min(1,v));}
    private StrictDialBoundarySeedAnalyzer(){}
}
