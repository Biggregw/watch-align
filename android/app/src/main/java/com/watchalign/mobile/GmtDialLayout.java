package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Which side the date window is on (alpha61). Current GMT-Master IIs have the date at 3 and a
 * baton at 9, except the 126720VTNR "Sprite", which is mirrored: date at 9, baton at 3. The app
 * checks one generic GMT, so the layout is read from the photo, not from a model choice.
 *
 * Probe: a strip where a baton's lume would be (0.66-0.85 dial radii out, 0.06 radii wide) at
 * 3 and at 9, oriented by the 12. A baton's lume is uniformly bright; the date window is white
 * with black numerals. The share of the strip darker than half its bright level separates them:
 * batons 0.00-0.09 and date windows 0.15-0.43 on the photos checked. When the two sides don't
 * separate clearly (a hand over one of them) the layout is UNKNOWN.
 */
final class GmtDialLayout {
    enum Layout {
        /** Date at 3, baton at 9: every current GMT-Master II except the Sprite. */
        DATE_AT_3,
        /** Date at 9, baton at 3: 126720VTNR Sprite. */
        DATE_AT_9,
        /** The probe could not tell. */
        UNKNOWN
    }

    static final class Result {
        final Layout layout;final double dark3,dark9;
        Result(Layout l,double d3,double d9){layout=l;dark3=d3;dark9=d9;}
        String describe(){
            return String.format(Locale.US,"Date side: %s (dark share in the baton strip at 3 %.2f, at 9 %.2f).",
                    layout==Layout.DATE_AT_3?"3, baton at 9":layout==Layout.DATE_AT_9?"9, baton at 3 (Sprite layout)":"not determined",dark3,dark9);
        }
    }

    /** At least this dark share is a date window; at most BATON_MAX_DARK is a clean baton. */
    static final double DATE_MIN_DARK = 0.12, BATON_MAX_DARK = 0.05;

    private GmtDialLayout(){}

    static Layout decide(double dark3,double dark9){
        if(!Double.isFinite(dark3)||!Double.isFinite(dark9))return Layout.UNKNOWN;
        if(dark3>=DATE_MIN_DARK&&dark9<=BATON_MAX_DARK)return Layout.DATE_AT_3;
        if(dark9>=DATE_MIN_DARK&&dark3<=BATON_MAX_DARK)return Layout.DATE_AT_9;
        return Layout.UNKNOWN;
    }

    /** @param twelveClockDeg clock angle of the 12's 60 tick about (cx,cy); NaN gives UNKNOWN. */
    static Result detect(Mat bgr,double cx,double cy,double r,double twelveClockDeg){
        if(bgr==null||bgr.empty()||!(r>20)||!Double.isFinite(twelveClockDeg))return new Result(Layout.UNKNOWN,Double.NaN,Double.NaN);
        Mat g=new Mat();
        try{
            Imgproc.cvtColor(bgr,g,Imgproc.COLOR_BGR2GRAY);
            int w=g.cols(),h=g.rows();byte[] px=new byte[w*h];g.get(0,0,px);
            double d3=darkShare(px,w,h,cx,cy,r,twelveClockDeg+90),d9=darkShare(px,w,h,cx,cy,r,twelveClockDeg-90);
            return new Result(decide(d3,d9),d3,d9);
        }finally{g.release();}
    }

    static double darkShare(byte[] px,int w,int h,double cx,double cy,double r,double clockDeg){
        double t=Math.toRadians(clockDeg),ux=Math.sin(t),uy=-Math.cos(t),vx=-uy,vy=ux;
        List<Double> v=new ArrayList<>();
        for(double rf=0.66;rf<=0.85+1e-9;rf+=0.004)for(double s=-0.03;s<=0.03+1e-9;s+=0.004){
            int x=(int)Math.round(cx+ux*rf*r+vx*s*r),y=(int)Math.round(cy+uy*rf*r+vy*s*r);
            if(x<0||y<0||x>=w||y>=h)continue;
            v.add((double)(px[y*w+x]&0xff));
        }
        if(v.size()<20)return Double.NaN;
        Collections.sort(v);
        double p90=v.get((int)(0.9*(v.size()-1)));
        if(!(p90>60))return Double.NaN;   // nothing bright there at all: not a marker or a date
        int dark=0;for(double q:v)if(q<0.5*p90)dark++;
        return dark/(double)v.size();
    }
}
