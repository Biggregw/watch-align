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
 * pose-projected regions (dial-interior darkness and texture, 12/6/9 marker presence) at the
 * ellipse and roll the caller already solved. The minute-tick distance-transform fit that
 * produces a pose can lock onto the wrong concentric structure (a bezel insert, rehaut, crystal
 * edge, or -- observed on a real photo -- blank background outside the watch entirely) and still
 * be perfectly self-consistent with its own predicted tick grid, including on held-out ticks
 * drawn from that same wrong grid. Darkness and marker-shaped blobs alone are not sufficient: a
 * plain dark background can be darker and more uniform than a real dial, and a cluttered scene
 * can coincidentally produce a few bright blobs near where markers would be projected. Requiring
 * measurable edge texture inside the interior (hands, date window, printed text, lume) in
 * addition to darkness closes that gap, so this gate gives a signal that does not depend on the
 * pose fitter's own self-consistency.
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
        final double interiorEdgeFraction;
        Evidence(Verdict verdict,double dialInteriorMedian,int markersFound,double interiorEdgeFraction){
            this.verdict=verdict;this.dialInteriorMedian=dialInteriorMedian;this.markersFound=markersFound;
            this.interiorEdgeFraction=interiorEdgeFraction;
        }
    }

    private static final double DARK_DIAL_MAX=135.0;

    // A blank, dark, low-texture region (presentation-box lining, a shadowed background) can be
    // darker and more uniform than a real dial, so darkness alone is not sufficient: it can be
    // satisfied by the wrong part of a photo just as easily as by a genuine dial. A real dial,
    // even a plain black one, always has hands, a date window, printed text and lume dots that
    // produce measurable edge structure inside the minute-track annulus. This threshold is a
    // heuristic pending real-corpus calibration, not a re-derived constant like the 0.22 centre
    // suspicion threshold; it exists to reject the specific false-PASS observed on a real photo
    // where a wrong, out-of-frame concentric structure sampled as dark with three coincidental
    // bright blobs but had essentially no interior edge content.
    private static final double MIN_INTERIOR_EDGE_FRACTION=0.02;

    static double minInteriorEdgeFraction(){return MIN_INTERIOR_EDGE_FRACTION;}

    /**
     * PASS requires a dark, textured dial interior and all three of 12/6/9 isolated as plausible
     * bright markers. AMBIGUOUS is exactly two of three markers found with a dark, textured
     * interior (one marker can be legitimately washed out by lume glare or reflection on a real
     * dial). Anything else, including a dark-but-textureless interior, is FAIL.
     */
    static Evidence evaluate(Mat gray,Mat edges,RotatedRect ellipse,double roll){
        double dialMedian=dialInteriorMedian(gray,ellipse,roll);
        double edgeFraction=interiorEdgeFraction(edges,ellipse,roll);
        boolean dark=Double.isFinite(dialMedian)&&dialMedian<=DARK_DIAL_MAX;
        boolean textured=edgeFraction>=MIN_INTERIOR_EDGE_FRACTION;
        int found=0;
        if(markerGood(gray,ellipse,roll,12))found++;
        if(markerGood(gray,ellipse,roll,6))found++;
        if(markerGood(gray,ellipse,roll,9))found++;
        Verdict verdict;
        if(!dark||!textured)verdict=Verdict.FAIL;
        else if(found>=3)verdict=Verdict.PASS;
        else if(found==2)verdict=Verdict.AMBIGUOUS;
        else verdict=Verdict.FAIL;
        return new Evidence(verdict,dialMedian,found,edgeFraction);
    }

    /**
     * Fraction of probe points inside the minute-track annulus that land on (a dilated) Canny
     * edge. Reuses the same edge map already computed for pose fitting; never refits anything.
     */
    static double interiorEdgeFraction(Mat edges,RotatedRect ellipse,double roll){
        if(edges==null||edges.empty())return 0.0;
        Mat dilated=new Mat();
        Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(5,5));
        try{
            Imgproc.dilate(edges,dilated,kernel);
            int hits=0,total=0;
            for(int ri=0;ri<8;ri++){
                double r=0.32+(0.60-0.32)*ri/7.0;
                for(int ai=0;ai<72;ai++){
                    double a=2.0*Math.PI*ai/72.0;
                    Point p=map(ellipse,1.0,roll,r*Math.cos(a),r*Math.sin(a));
                    Double v=sampleBinaryNearest(dilated,p.x,p.y);
                    if(v==null)continue;
                    total++;
                    if(v>0.0)hits++;
                }
            }
            return total==0?0.0:hits/(double)total;
        }finally{
            dilated.release();kernel.release();
        }
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

    private static Double sampleBinaryNearest(Mat binary,double x,double y){
        int xi=(int)Math.round(x),yi=(int)Math.round(y);
        if(xi<0||yi<0||xi>=binary.cols()||yi>=binary.rows())return null;
        double[] v=binary.get(yi,xi);
        return v==null?null:v[0];
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
