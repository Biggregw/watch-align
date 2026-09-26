package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * GMT-specific dial seed detector that tolerates close crops/screenshots where the
 * dial can occupy far more than half the image width. The detector deliberately
 * avoids an absolute image-scale prior: screenshots and crops can place the same
 * physical dial at very different pixel radii. Selection is driven by the dark
 * dial, the 12-marker ring and the persistent outer boundary instead.
 */
final class GmtDialSeedAnalyzer {
    static final class Result {
        final boolean valid;
        final double x,y,r,quality;
        final String reason;
        Result(String reason){valid=false;x=y=r=quality=Double.NaN;this.reason=reason;}
        Result(double x,double y,double r,double q){valid=true;this.x=x;this.y=y;this.r=r;quality=q;reason="";}
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
                Candidate q=score(gray,c[0],c[1],c[2],min);
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
                        Candidate q=score(eq,c[0],c[1],c[2],min);
                        if(q!=null&&(best==null||better(q,best)))best=q;
                    }
                }finally{c2.release();eq.release();}
            }
            if(best==null)return new Result("wide-scale GMT dial boundary not found");
            return new Result(best.x,best.y,best.r,Math.max(0,Math.min(1,best.score)));
        }finally{circles.release();blur.release();gray.release();}
    }

    private static final class Candidate{
        final double x,y,r,score,centre;
        final int markerHits;
        Candidate(double x,double y,double r,double s,double centre,int hits){
            this.x=x;this.y=y;this.r=r;score=s;this.centre=centre;markerHits=hits;
        }
    }

    private static boolean better(Candidate a,Candidate b){
        // Primary decision is evidence score. For effectively tied candidates,
        // favour the one supported by more hour sectors, then the one whose
        // centre is more plausible. Never favour a radius merely because it is
        // closer to some assumed fraction of the screenshot dimensions.
        if(a.score>b.score+0.004)return true;
        if(b.score>a.score+0.004)return false;
        if(a.markerHits!=b.markerHits)return a.markerHits>b.markerHits;
        if(Math.abs(a.centre-b.centre)>0.01)return a.centre<b.centre;
        return a.score>b.score;
    }

    private static Candidate score(Mat g,double cx,double cy,double r,int min){
        double rn=r/min;if(rn<.10||rn>.46)return null;
        double nx=cx/g.cols(),ny=cy/g.rows();
        if(nx<.06||nx>.94||ny<.05||ny>.94)return null;
        double centre=Math.hypot(nx-.5,ny-.48);if(centre>.50)return null;

        double core=darkFraction(g,cx,cy,r*.56);
        double wide=darkFraction(g,cx,cy,r*.88);
        double ann=annulusDark(g,cx,cy,r*.61,r*.88);
        int hits=markerHits(g,cx,cy,r);
        double edge=ringEdge(g,cx,cy,r);
        if(core<.42||wide<.34||ann<.30||hits<6||edge<.035)return null;

        double darkFit=Math.min(1,(.45*core+.30*wide+.25*ann)/.68);
        double markerFit=Math.min(1,hits/10.0);
        double edgeFit=Math.min(1,edge/.16);
        double centreFit=1-Math.min(1,centre/.50);

        // No absolute radius/scale term. Alpha37's weak preference for r≈0.27
        // of the image width was enough to make a close-cropped screenshot pick
        // an inner ring even when the true outer dial had stronger 12-sector and
        // centre evidence. Human inspection has no such screenshot-scale prior.
        double score=.34*markerFit+.26*darkFit+.20*edgeFit+.20*centreFit;
        return new Candidate(cx,cy,r,score,centre,hits);
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
    private static double ringEdge(Mat g,double cx,double cy,double r){
        double sum=0;int n=0;
        for(int deg=0;deg<360;deg+=5){double a=Math.toRadians(deg),best=0;
            for(double f=.93;f<=1.07;f+=.02){
                int xi=(int)Math.round(cx+Math.cos(a)*r*(f-.02)),yi=(int)Math.round(cy+Math.sin(a)*r*(f-.02));
                int xo=(int)Math.round(cx+Math.cos(a)*r*(f+.02)),yo=(int)Math.round(cy+Math.sin(a)*r*(f+.02));
                if(!inside(g,xi,yi)||!inside(g,xo,yo))continue;
                double[]vi=g.get(yi,xi),vo=g.get(yo,xo);if(vi!=null&&vo!=null)best=Math.max(best,Math.abs(vo[0]-vi[0])/255.0);
            }
            if(best>0){sum+=best;n++;}
        }
        return n>0?sum/n:0;
    }
    private static boolean inside(Mat m,int x,int y){return x>=0&&y>=0&&x<m.cols()&&y<m.rows();}
    private GmtDialSeedAnalyzer(){}
}
