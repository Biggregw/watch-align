package com.watchalign.mobile;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Independent candidate-identity evidence for an already-resolved minute-track pose.
 *
 * This never searches for, fits, moves, resizes or rotates a pose; it only samples broad,
 * pose-projected regions (dial-interior darkness, 12/6/9 marker presence) at the ellipse and
 * roll the caller already solved. The minute-tick distance-transform fit that produces a pose
 * can lock onto the wrong concentric ring (a bezel insert, rehaut or crystal edge) and still be
 * perfectly self-consistent with its own predicted tick grid, including on held-out ticks drawn
 * from that same wrong grid. A wrong ring is far less likely to also have a dark dial interior
 * and three genuine-shaped bright markers sitting where 126710BLNR markers actually are, so this
 * gate gives a signal that does not depend on the pose fitter's own self-consistency.
 *
 * This logic previously lived only inside the rescue selector's semantic sanity gate; it is
 * shared here so the primary acquisition path can use the same evidence.
 */
final class MinuteTrackIdentityGate {
    enum Verdict { PASS, AMBIGUOUS, FAIL }

    static final class Evidence {
        final Verdict verdict;
        final double dialInteriorMedian;
        final int markersFound;
        Evidence(Verdict verdict,double dialInteriorMedian,int markersFound){
            this.verdict=verdict;this.dialInteriorMedian=dialInteriorMedian;this.markersFound=markersFound;
        }
    }

    private static final double DARK_DIAL_MAX=135.0;

    /**
     * PASS requires a dark dial interior and all three of 12/6/9 isolated as plausible bright
     * markers. AMBIGUOUS is exactly two of three found with a dark interior (one marker can be
     * legitimately washed out by lume glare or reflection on a real dial). Anything else is FAIL.
     */
    static Evidence evaluate(Mat gray,RotatedRect ellipse,double roll){
        double dialMedian=dialInteriorMedian(gray,ellipse,roll);
        boolean dark=Double.isFinite(dialMedian)&&dialMedian<=DARK_DIAL_MAX;
        int found=0;
        if(markerGood(gray,ellipse,roll,12))found++;
        if(markerGood(gray,ellipse,roll,6))found++;
        if(markerGood(gray,ellipse,roll,9))found++;
        Verdict verdict;
        if(!dark)verdict=Verdict.FAIL;
        else if(found>=3)verdict=Verdict.PASS;
        else if(found==2)verdict=Verdict.AMBIGUOUS;
        else verdict=Verdict.FAIL;
        return new Evidence(verdict,dialMedian,found);
    }

    static double dialInteriorMedian(Mat gray,RotatedRect ellipse,double roll){
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

    static boolean markerGood(Mat gray,RotatedRect ellipse,double roll,int hour){
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

    private static Point map(RotatedRect e,double scale,double rollDeg,double x,double y){
        double roll=Math.toRadians(rollDeg),cr=Math.cos(roll),sr=Math.sin(roll);
        double xr=cr*x-sr*y,yr=sr*x+cr*y;
        double axis=Math.toRadians(e.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double localX=ca*xr+sa*yr,localY=-sa*xr+ca*yr;
        double rx=e.size.width*0.5*scale,ry=e.size.height*0.5*scale;
        double sx=rx*localX,sy=ry*localY;
        return new Point(e.center.x+ca*sx-sa*sy,e.center.y+sa*sx+ca*sy);
    }

    private static double sampleGray(Mat gray,double x,double y){
        if(x<1||y<1||x>=gray.cols()-1||y>=gray.rows()-1)return Double.NaN;
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double fx=x-x0,fy=y-y0;
        double[] a=gray.get(y0,x0),b=gray.get(y0,x0+1),c=gray.get(y0+1,x0),d=gray.get(y0+1,x0+1);
        if(a==null||b==null||c==null||d==null)return Double.NaN;
        return (a[0]*(1.0-fx)+b[0]*fx)*(1.0-fy)+(c[0]*(1.0-fx)+d[0]*fx)*fy;
    }

    private static double percentileSorted(List<Double> sorted,double q){
        if(sorted.isEmpty())return Double.NaN;
        double pos=q*(sorted.size()-1),f=pos-Math.floor(pos);
        int lo=(int)Math.floor(pos),hi=(int)Math.ceil(pos);
        if(lo==hi)return sorted.get(lo);
        return sorted.get(lo)*(1.0-f)+sorted.get(hi)*f;
    }

    private MinuteTrackIdentityGate(){}
}
