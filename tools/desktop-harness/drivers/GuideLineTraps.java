package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.6.6 (exit 1 on failure): GuideLines on a synthetic dial (RGBA, radius 400 px). Removed: thin magenta guide lines
 * from edge to edge through the centre. Kept: a red GMT hand with its arrow head, a blue / red bezel ring, a blue date
 * disc, white lume and grey steel.
 */
public class GuideLineTraps {
    public static void main(String[] a){
        nu.pattern.OpenCV.loadLocally();
        int S=1000;double R=400,c=S/2.0;
        Mat img=new Mat(S,S,CvType.CV_8UC4,new Scalar(20,20,20,255));
        Imgproc.circle(img,new Point(c,c),(int)(R*1.12),new Scalar(30,60,200,255),(int)(R*0.12));       // blue bezel
        Imgproc.ellipse(img,new Point(c,c),new org.opencv.core.Size(R*1.12,R*1.12),0,-90,90,new Scalar(200,30,40,255),(int)(R*0.12)); // red half
        Imgproc.rectangle(img,new Point(c+0.55*R,c-0.08*R),new Point(c+0.75*R,c+0.08*R),new Scalar(40,70,200,255),-1);   // date disc
        Imgproc.circle(img,new Point(c-0.75*R,c),(int)(0.05*R),new Scalar(235,235,230,255),-1);              // lume
        Imgproc.line(img,new Point(c,c),new Point(c-0.3*R,c+0.6*R),new Scalar(150,150,150,255),12);          // steel hand
        // red GMT hand: thin shaft and an arrow head
        Point tip=new Point(c+0.6*R,c-0.6*R);Imgproc.line(img,new Point(c,c),tip,new Scalar(210,30,30,255),3);
        List<MatOfPoint> arrow=new ArrayList<>();arrow.add(new MatOfPoint(new Point(tip.x+12,tip.y-12),new Point(tip.x-30,tip.y-2),new Point(tip.x+2,tip.y+30)));
        Imgproc.fillPoly(img,arrow,new Scalar(210,30,30,255));
        Mat keep=Mat.zeros(S,S,CvType.CV_8U);   // pixels that must not be removed
        Mat colKeep=img.clone();
        // magenta guide lines through the centre, drawn last (over everything)
        for(int k=0;k<6;k++){double t=Math.PI*k/6;Imgproc.line(img,new Point(c-1.05*R*Math.cos(t),c-1.05*R*Math.sin(t)),new Point(c+1.05*R*Math.cos(t),c+1.05*R*Math.sin(t)),new Scalar(220,40,200,255),2);}
        Mat m=GuideLines.mask(img,R);
        int bad=0;
        if(m==null){System.out.println("GUIDELINE FAIL: no guide lines found");System.exit(1);}
        // every guide-line pixel away from the other objects is masked
        int lineTotal=0,lineMasked=0;
        for(int k=0;k<6;k++){double t=Math.PI*k/6;for(double r=0.1;r<0.95;r+=0.01){int x=(int)Math.round(c+r*R*Math.cos(t)),y=(int)Math.round(c+r*R*Math.sin(t));
            double[] p=colKeep.get(y,x);if(p[0]!=20||p[1]!=20)continue;lineTotal++;if(m.get(y,x)[0]>0)lineMasked++;}}
        // the GMT arrow head, bezel and date disc are kept (sampled away from the guide lines)
        int[][] mustKeep={{(int)(tip.x+2),(int)(tip.y+14)},{(int)(c),(int)(c+1.12*R)},{(int)(c+0.65*R),(int)(c+0.04*R)},{(int)(c+1.12*R*Math.cos(1.2)),(int)(c+1.12*R*Math.sin(1.2))}};
        String[] names={"GMT arrow head","bezel","date disc","bezel"};
        for(int i=0;i<mustKeep.length;i++){boolean masked=m.get(mustKeep[i][1],mustKeep[i][0])[0]>0;if(masked)bad++;
            System.out.println("GUIDELINE "+names[i]+(masked?" REMOVED (FAIL)":" kept"));}
        // the red GMT shaft (away from the guide lines) is kept with its arrow
        int shaftMasked=0,shaftTotal=0;for(double r=0.15;r<0.75;r+=0.02){int x=(int)Math.round(c+r*0.6*R/0.8485*0.8485),y=(int)Math.round(c-r*0.6*R/0.8485*0.8485);
            double[] p=colKeep.get(y,x);if(p[0]!=210)continue;shaftTotal++;if(m.get(y,x)[0]>0)shaftMasked++;}
        System.out.println("GUIDELINE magenta lines masked "+lineMasked+" of "+lineTotal+"; GMT shaft masked "+shaftMasked+" of "+shaftTotal);
        if(lineMasked<0.95*lineTotal)bad++;
        if(shaftMasked>0)bad++;
        System.out.println("GUIDELINES "+(bad==0?"all as expected":bad+" FAILED"));
        if(bad>0)System.exit(1);
    }
}
