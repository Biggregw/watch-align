package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

/**
 * Alpha99 results hero: the whole dial, upright and perspective-corrected on the frozen pose, with one small badge per
 * assessed feature (green tick = within, amber ! = worth a look, red !! = clear finding, grey dash = not assessed for a
 * reason at that marker; markers withheld only because of the photo's resolution get no badge).
 * No numbers on the picture. Display only.
 */
final class Alpha99Overview {
    static final int SIZE=720;
    static final double HALF=1.12;
    static final Scalar GREEN=new Scalar(52,199,89,255),AMBER=new Scalar(255,159,10,255),RED=new Scalar(255,59,48,255),
            GREY=new Scalar(142,142,147,255),WHITE=new Scalar(255,255,255,255),YELLOW=new Scalar(255,214,10,255);

    private Alpha99Overview(){}

    static Bitmap render(Mat rgba,double[] H,Alpha99Findings.Summary s,ModelSpec model){
        Mat m=Alpha98Closeups.render(rgba,H,0,0,HALF);
        Mat big=new Mat();Imgproc.resize(m,big,new org.opencv.core.Size(SIZE,SIZE),0,0,Imgproc.INTER_CUBIC);
        double scale=SIZE/(2*HALF);
        for(Alpha99Findings.Finding f:s.all){
            if(!hasBadge(f))continue;
            double[] p=badgeAt(f,model);if(p==null)continue;
            badge(big,new Point((p[0]+HALF)*scale,(p[1]+HALF)*scale),f.status,f.shape==Alpha99Findings.Shape.RING?"ring":null);
        }
        Bitmap b=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(big,b);return b;
    }

    /** A badge for every assessed feature and every marker withheld for its own reason (hand, glare, edge). Markers
     *  withheld as a group for a photo-wide reason (e.g. round markers at too low a resolution) get none: the reason is
     *  the photo, not the marker, and the not-assessed line under the grid says so. */
    static boolean hasBadge(Alpha99Findings.Finding f){
        return !(f.status==Alpha99Findings.Status.NOT_ASSESSED&&f.group!=null);
    }

    /** Badge position in canonical units: just inside each marker (towards the centre), beside the date window, the
     *  ring badge in the top-left corner. */
    static double[] badgeAt(Alpha99Findings.Finding f,ModelSpec model){
        switch(f.shape){
            case RING:return new double[]{-HALF+0.12,-HALF+0.12};
            case DATE:return new double[]{f.cx-0.27,f.cy};
            default:{
                ModelSpec.Marker mk=model.atHour(f.hour);if(mk==null)return null;
                double rin=mk.innerR();
                double r=rin-0.09,a=Math.toRadians(f.hour*30.0);
                return new double[]{Math.sin(a)*r,-Math.cos(a)*r};
            }
        }
    }

    static void badge(Mat m,Point c,Alpha99Findings.Status st,String label){
        int rad=SIZE/38,th=Math.max(2,SIZE/240);
        Scalar fill=st==Alpha99Findings.Status.CLEAR?RED:st==Alpha99Findings.Status.WORTH?AMBER:st==Alpha99Findings.Status.WITHIN?GREEN:GREY;
        Imgproc.circle(m,c,rad+th,new Scalar(0,0,0,255),-1,Imgproc.LINE_AA);
        Imgproc.circle(m,c,rad,fill,-1,Imgproc.LINE_AA);
        double x=c.x,y=c.y,u=rad*0.5;
        switch(st){
            case WITHIN:
                Imgproc.line(m,new Point(x-u,y),new Point(x-u*0.25,y+u*0.7),WHITE,th+1,Imgproc.LINE_AA);
                Imgproc.line(m,new Point(x-u*0.25,y+u*0.7),new Point(x+u,y-u*0.7),WHITE,th+1,Imgproc.LINE_AA);break;
            case WORTH:bang(m,x,y,u,th);break;
            case CLEAR:bang(m,x-u*0.45,y,u,th);bang(m,x+u*0.45,y,u,th);break;
            default:Imgproc.line(m,new Point(x-u,y),new Point(x+u,y),WHITE,th+1,Imgproc.LINE_AA);
        }
        if(label!=null)Imgproc.putText(m,label,new Point(x+rad+6,y+rad*0.45),Imgproc.FONT_HERSHEY_SIMPLEX,SIZE/1200.0,WHITE,Math.max(1,th-1),Imgproc.LINE_AA);
    }

    private static void bang(Mat m,double x,double y,double u,int th){
        Imgproc.line(m,new Point(x,y-u),new Point(x,y+u*0.3),WHITE,th+1,Imgproc.LINE_AA);
        Imgproc.circle(m,new Point(x,y+u*0.8),Math.max(1,th),WHITE,-1,Imgproc.LINE_AA);
    }
}
