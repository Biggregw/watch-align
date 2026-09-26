package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.Arrays;

/**
 * GMT-specific black-dial seed detector.
 *
 * Hough circles are used only to propose plausible centres. Their radius is NOT
 * trusted because close screenshots often make the stronger bezel/rehaut circle
 * win. For every proposed centre we re-scan radius and look for the physical dial
 * boundary itself: a persistent DARK-INSIDE -> BRIGHT-OUTSIDE radial transition.
 * This is scale-free and distinguishes the black dial edge from later bezel edges.
 */
final class GmtDialSeedAnalyzer {
    static final class Result {
        final boolean valid;
        final double x,y,r,quality,boundaryStrength,boundaryCoverage;
        final int markerHits;
        final String reason;
        Result(String reason){valid=false;x=y=r=quality=boundaryStrength=boundaryCoverage=Double.NaN;markerHits=0;this.reason=reason;}
        Result(double x,double y,double r,double q,double boundary,double coverage,int hits){
            valid=true;this.x=x;this.y=y;this.r=r;quality=q;boundaryStrength=boundary;boundaryCoverage=coverage;markerHits=hits;reason="";
        }
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
            Imgproc.HoughCircles(blur,circles,Imgproc.HOUGH_GRADIENT,1.10,min/10.0,115,23,minR,maxR);
            Candidate best=null;
            for(int i=0;i<circles.cols();i++){
                double[] c=circles.get(0,i);if(c==null||c.length<3)continue;
                Candidate q=scoreCentre(gray,c[0],c[1],min,minR,maxR);
                if(q!=null&&(best==null||better(q,best)))best=q;
            }
            if(best==null){
                Mat eq=new Mat(),c2=new Mat();
                try{
                    Imgproc.createCLAHE(2.4,new Size(8,8)).apply(gray,eq);
                    Imgproc.GaussianBlur(eq,eq,new Size(7,7),0);
                    Imgproc.HoughCircles(eq,c2,Imgproc.HOUGH_GRADIENT,1.10,min/10.0,112,21,minR,maxR);
                    for(int i=0;i<c2.cols();i++){
                        double[] c=c2.get(0,i);if(c==null||c.length<3)continue;
                        Candidate q=scoreCentre(eq,c[0],c[1],min,minR,maxR);
                        if(q!=null&&(best==null||better(q,best)))best=q;
                    }
                }finally{c2.release();eq.release();}
            }
            if(best==null)return new Result("physical dark-to-rehaut dial boundary not found");
            return new Result(best.x,best.y,best.r,clamp01(best.score),best.boundary,best.coverage,best.markerHits);
        }finally{circles.release();blur.release();gray.release();}
    }

    private static final class RadiusFit{
        final double r,boundary,positiveFraction,coverage;
        RadiusFit(double r,double b,double p,double c){this.r=r;boundary=b;positiveFraction=p;coverage=c;}
    }
    private static final class Boundary{
        final double strength,positiveFraction,coverage;
        Boundary(double s,double p,double c){strength=s;positiveFraction=p;coverage=c;}
    }
    private static final class Candidate{
        final double x,y,r,score,boundary,coverage;
        final int markerHits;
        Candidate(double x,double y,double r,double score,double boundary,double coverage,int hits){
            this.x=x;this.y=y;this.r=r;this.score=score;this.boundary=boundary;this.coverage=coverage;markerHits=hits;
        }
    }

    private static Candidate scoreCentre(Mat g,double cx,double cy,int min,int minR,int maxR){
        double nx=cx/g.cols(),ny=cy/g.rows();
        if(nx<.04||nx>.96||ny<.04||ny>.96)return null;

        RadiusFit fit=refineRadius(g,cx,cy,minR,maxR);
        if(fit==null||fit.boundary<.045||fit.coverage<.55||fit.positiveFraction<.56)return null;
        double r=fit.r;

        double core=darkFraction(g,cx,cy,r*.56);
        double wide=darkFraction(g,cx,cy,r*.88);
        double ann=annulusDark(g,cx,cy,r*.61,r*.88);
        int hits=markerHits(g,cx,cy,r);
        if(core<.42||wide<.34||ann<.30||hits<6)return null;

        double darkFit=clamp01((.45*core+.30*wide+.25*ann)/.68);
        double markerFit=clamp01(hits/10.0);
        double boundaryFit=clamp01((fit.boundary-.035)/.19);
        double polarityFit=clamp01((fit.positiveFraction-.50)/.38);

        // No image-size or image-centre preference. Cropping is irrelevant.
        // The physical dial boundary and the repeating marker ring carry the vote.
        double score=.35*boundaryFit+.31*markerFit+.22*darkFit+.12*polarityFit;
        return new Candidate(cx,cy,r,score,fit.boundary,fit.coverage,hits);
    }

    private static RadiusFit refineRadius(Mat g,double cx,double cy,int minR,int maxR){
        int border=(int)Math.floor(Math.min(Math.min(cx,g.cols()-1.0-cx),Math.min(cy,g.rows()-1.0-cy)))-3;
        int hi=Math.min(maxR,border>minR?border:maxR);
        if(hi<=minR+8)return null;
        RadiusFit best=null;
        for(int r=minR;r<=hi;r+=2){
            Boundary b=signedBoundary(g,cx,cy,r);
            if(b.coverage<.55)continue;
            double merit=b.strength+0.055*b.positiveFraction;
            if(best==null||merit>best.boundary+0.055*best.positiveFraction)
                best=new RadiusFit(r,b.strength,b.positiveFraction,b.coverage);
        }
        if(best==null)return null;
        // One-pixel refinement around the coarse winner.
        RadiusFit fine=best;
        int lo=(int)Math.max(minR,Math.round(best.r)-3),fh=(int)Math.min(hi,Math.round(best.r)+3);
        for(int r=lo;r<=fh;r++){
            Boundary b=signedBoundary(g,cx,cy,r);if(b.coverage<.55)continue;
            double merit=b.strength+0.055*b.positiveFraction;
            if(merit>fine.boundary+0.055*fine.positiveFraction)
                fine=new RadiusFit(r,b.strength,b.positiveFraction,b.coverage);
        }
        return fine;
    }

    /** Median signed radial contrast. Positive means dark inside and brighter outside. */
    private static Boundary signedBoundary(Mat g,double cx,double cy,double r){
        double delta=Math.max(3.0,Math.min(10.0,r*.025));
        double[] diff=new double[72];int n=0,pos=0,valid=0;
        for(int deg=0;deg<360;deg+=5){
            double a=Math.toRadians(deg),co=Math.cos(a),si=Math.sin(a);
            int xi=(int)Math.round(cx+co*(r-delta)),yi=(int)Math.round(cy+si*(r-delta));
            int xo=(int)Math.round(cx+co*(r+delta)),yo=(int)Math.round(cy+si*(r+delta));
            if(!inside(g,xi,yi)||!inside(g,xo,yo))continue;
            double[] vi=g.get(yi,xi),vo=g.get(yo,xo);if(vi==null||vo==null)continue;
            double d=(vo[0]-vi[0])/255.0;diff[n++]=d;valid++;if(d>.025)pos++;
        }
        if(n<24)return new Boundary(Double.NEGATIVE_INFINITY,0,n/72.0);
        double[] used=Arrays.copyOf(diff,n);Arrays.sort(used);
        double med=n%2==1?used[n/2]:(used[n/2-1]+used[n/2])*.5;
        return new Boundary(med,pos/(double)n,n/72.0);
    }

    private static boolean better(Candidate a,Candidate b){
        if(a.score>b.score+.004)return true;if(b.score>a.score+.004)return false;
        if(Math.abs(a.boundary-b.boundary)>.010)return a.boundary>b.boundary;
        if(a.markerHits!=b.markerHits)return a.markerHits>b.markerHits;
        return a.coverage>b.coverage;
    }

    private static int markerHits(Mat g,double cx,double cy,double r){
        int hits=0;
        for(int h=1;h<=12;h++){
            double target=Math.toRadians(h==12?0:h*30.0),best=0,innerDark=255;
            for(double rf=.64;rf<=.86;rf+=.025)for(double dd=-5;dd<=5;dd+=2.5){
                double a=target+Math.toRadians(dd);
                int x=(int)Math.round(cx+Math.sin(a)*r*rf),y=(int)Math.round(cy-Math.cos(a)*r*rf);
                int xi=(int)Math.round(cx+Math.sin(a)*r*Math.max(.50,rf-.12)),yi=(int)Math.round(cy-Math.cos(a)*r*Math.max(.50,rf-.12));
                if(!inside(g,x,y)||!inside(g,xi,yi))continue;
                double[]v=g.get(y,x),vi=g.get(yi,xi);if(v==null||vi==null)continue;
                best=Math.max(best,v[0]);innerDark=Math.min(innerDark,vi[0]);
            }
            if(best>=155&&innerDark<=155)hits++;
        }
        return hits;
    }

    private static double darkFraction(Mat g,double cx,double cy,double rr){
        int step=Math.max(2,(int)Math.round(rr/50.0)),n=0,d=0;
        int x0=Math.max(0,(int)(cx-rr)),x1=Math.min(g.cols()-1,(int)(cx+rr));
        int y0=Math.max(0,(int)(cy-rr)),y1=Math.min(g.rows()-1,(int)(cy+rr));
        for(int y=y0;y<=y1;y+=step)for(int x=x0;x<=x1;x+=step){if(Math.hypot(x-cx,y-cy)>rr)continue;double[]v=g.get(y,x);if(v==null)continue;n++;if(v[0]<145)d++;}
        return n>0?d/(double)n:0;
    }
    private static double annulusDark(Mat g,double cx,double cy,double ri,double ro){
        int step=Math.max(2,(int)Math.round(ro/55.0)),n=0,d=0;
        int x0=Math.max(0,(int)(cx-ro)),x1=Math.min(g.cols()-1,(int)(cx+ro));
        int y0=Math.max(0,(int)(cy-ro)),y1=Math.min(g.rows()-1,(int)(cy+ro));
        for(int y=y0;y<=y1;y+=step)for(int x=x0;x<=x1;x+=step){double q=Math.hypot(x-cx,y-cy);if(q<ri||q>ro)continue;double[]v=g.get(y,x);if(v==null)continue;n++;if(v[0]<145)d++;}
        return n>0?d/(double)n:0;
    }
    private static boolean inside(Mat m,int x,int y){return x>=0&&y>=0&&x<m.cols()&&y<m.rows();}
    private static double clamp01(double v){return Math.max(0,Math.min(1,v));}
    private GmtDialSeedAnalyzer(){}
}
