package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.photo.Photo;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.6.6 (owner, 21 Oct 2026): coloured guide lines drawn on a QC photo (e.g. magenta lines from the centre through every
 * marker, r/RepTimeQC) look to the hand check like a thin hand at every marker, so every marker was withheld as "hand
 * crosses or touches this marker". This removes them from a COPY of the photo used only by the hand / glare check
 * (Alpha99MarkerInterference); the marker measurements still read the photo itself.
 *
 * A guide line is a strongly coloured (no steel, white, black or grey) structure of one hue that is thin, long, and has
 * almost no thick part: a GMT hand keeps its arrow head, a coloured bezel, date disc or Cyclops tint is thick, so none of
 * them is removed, and a thick pixel never is. A line of the same hue as a coloured hand or bezel it crosses joins it
 * and is left in place: the photo is then checked exactly as before.
 */
final class GuideLines {
    private GuideLines(){}

    /** strongly coloured: max - min of R, G, B at least this (0-255); steel, lume, dial and print are near grey */
    static final double MIN_CHROMA=80;
    /** a coloured part at least this wide (px) is thick (a hand's arrow, a bezel, the date disc), never a drawn line */
    static final int THICK_PX=7;
    /** a line runs at least this far (fraction of the dial radius) */
    static final double MIN_LENGTH_R=0.3;
    /** a structure with more than this share of thick pixels is a coloured object, not a line */
    static final double MAX_THICK_FRAC=0.10;

    /** gray with the photo's guide lines painted out, or gray itself when it has none */
    static Mat forHandCheck(Mat rgba,Mat gray,double rpx){
        Mat mask=mask(rgba,rpx);
        try{
            if(mask==null||Core.countNonZero(mask)==0)return gray;
            Mat out=new Mat();Photo.inpaint(gray,mask,out,3,Photo.INPAINT_TELEA);
            return out;
        }finally{if(mask!=null)mask.release();}
    }

    /** the guide-line pixels (255), dilated by one pixel to take their anti-aliased edges; null when none. Coloured
     *  pixels are taken one hue band at a time (six bands of 60 deg, red centred), so a magenta line crossing a red
     *  GMT hand or a blue date disc is not joined to it; a thick pixel is never part of a line. */
    static Mat mask(Mat rgba,double rpx){
        if(rgba==null||rgba.empty()||!(rpx>20))return null;
        List<Mat> ch=new ArrayList<>();Core.split(rgba,ch);
        Mat mx=new Mat(),mn=new Mat(),chroma=new Mat(),col=new Mat();
        Core.max(ch.get(0),ch.get(1),mx);Core.max(mx,ch.get(2),mx);
        Core.min(ch.get(0),ch.get(1),mn);Core.min(mn,ch.get(2),mn);
        Core.subtract(mx,mn,chroma);
        Imgproc.threshold(chroma,col,MIN_CHROMA-1,255,Imgproc.THRESH_BINARY);
        mx.release();mn.release();chroma.release();
        if(Core.countNonZero(col)==0){for(Mat m:ch)m.release();col.release();return null;}
        Mat rgb=new Mat(),hsv=new Mat(),hue=new Mat();
        List<Mat> three=new ArrayList<>(ch.subList(0,3));Core.merge(three,rgb);for(Mat m:ch)m.release();
        Imgproc.cvtColor(rgb,hsv,Imgproc.COLOR_RGB2HSV_FULL);rgb.release();
        Core.extractChannel(hsv,hue,0);hsv.release();
        Mat thick=new Mat();
        Imgproc.morphologyEx(col,thick,Imgproc.MORPH_OPEN,Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(THICK_PX,THICK_PX)));
        int w=col.cols(),h=col.rows();
        Mat mask=Mat.zeros(h,w,CvType.CV_8U);boolean any=false;
        Mat band=new Mat(),lo=new Mat(),hi=new Mat();
        for(int b=0;b<6;b++){
            // hue 0-255 (full circle); band b covers centre 256*b/6 +- 256/12, red (0) centred
            double c=256.0*b/6,from=c-256.0/12,to=c+256.0/12;
            if(from<0){Core.inRange(hue,new Scalar(0),new Scalar(to),lo);Core.inRange(hue,new Scalar(from+256),new Scalar(255),hi);Core.bitwise_or(lo,hi,band);}
            else Core.inRange(hue,new Scalar(from),new Scalar(to),band);
            Core.bitwise_and(band,col,band);
            if(Core.countNonZero(band)==0)continue;
            any|=lines(band,thick,mask,rpx);
        }
        band.release();lo.release();hi.release();hue.release();col.release();
        if(!any){thick.release();mask.release();return null;}
        Mat notThick=new Mat();Core.bitwise_not(thick,notThick);Core.bitwise_and(mask,notThick,mask);notThick.release();thick.release();
        Imgproc.dilate(mask,mask,Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(3,3)));
        return mask;
    }

    /** adds to mask the line-like components of one hue band; true when any */
    static boolean lines(Mat band,Mat thick,Mat mask,double rpx){
        Mat lab=new Mat(),stats=new Mat(),cent=new Mat();
        int n=Imgproc.connectedComponentsWithStats(band,lab,stats,cent,8,CvType.CV_32S);
        int w=band.cols(),h=band.rows();
        // row by row (a 12 MP photo's whole label image would be 48 MB; owner's 256 MB phone, 1.6.5)
        int[] row=new int[w];byte[] trow=new byte[w];
        int[] area=new int[n],thickArea=new int[n];
        for(int y=0;y<h;y++){lab.get(y,0,row);thick.get(y,0,trow);
            for(int x=0;x<w;x++){int k=row[x];if(k>0){area[k]++;if(trow[x]!=0)thickArea[k]++;}}}
        boolean[] line=new boolean[n];boolean any=false;
        for(int k=1;k<n;k++){
            double bw=stats.get(k,Imgproc.CC_STAT_WIDTH)[0],bh=stats.get(k,Imgproc.CC_STAT_HEIGHT)[0];
            line[k]=Math.hypot(bw,bh)>=MIN_LENGTH_R*rpx&&thickArea[k]<=MAX_THICK_FRAC*area[k];
            any|=line[k];
        }
        if(any){byte[] mrow=new byte[w];
            for(int y=0;y<h;y++){lab.get(y,0,row);mask.get(y,0,mrow);
                for(int x=0;x<w;x++)if(row[x]>0&&line[row[x]])mrow[x]=(byte)255;
                mask.put(y,0,mrow);}}
        lab.release();stats.release();cent.release();
        return any;
    }
}
