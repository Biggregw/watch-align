package com.watchalign.mobile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;

/** Demonstrates the residual limit of the current symmetric four-point pose. */
public class PerspectiveGmtProjectivePoseTest {
    private static final double[][] TRUE_H={
            {352.4,110.6,420.0},
            { 94.2,274.8,360.0},
            {  0.22, 0.18,  1.0}
    };

    @Test public void symmetricEllipsePoseCannotRecoverKnownNonAffineProjection() {
        RotatedRect projectedDial=ellipseFromProjectedUnitCircle(TRUE_H);
        Point[] card=PerspectiveGmtOverlay.ellipseCardinalPoints(projectedDial,0.0);
        double[][] recovered=symmetricCardinalPose(card);

        double[] errors=new double[12];
        double max=0.0,sum=0.0;
        for(int hour=1;hour<=12;hour++){
            double a=Math.toRadians(hour*30.0-90.0);
            Point expected=project(TRUE_H,Math.cos(a),Math.sin(a));
            Point actual=project(recovered,Math.cos(a),Math.sin(a));
            double error=Math.hypot(actual.x-expected.x,actual.y-expected.y);
            errors[hour-1]=error;max=Math.max(max,error);sum+=error;
            System.out.printf(java.util.Locale.US,"hour=%02d error_px=%.6f%n",hour,error);
        }

        Point true12=project(TRUE_H,0,-1),true6=project(TRUE_H,0,1);
        Point trueCenter=project(TRUE_H,0,0);
        double oppositeMidpointShift=Math.hypot(
                (true12.x+true6.x)*0.5-trueCenter.x,
                (true12.y+true6.y)*0.5-trueCenter.y);

        // The synthetic pose has unmistakable near-side/far-side perspective.
        assertTrue(oppositeMidpointShift>20.0);
        assertTrue(Math.abs(TRUE_H[2][0])>0.1);
        assertTrue(Math.abs(TRUE_H[2][1])>0.1);

        // Symmetric ellipse cardinals necessarily produce an affine pose.
        assertEquals(0.0,recovered[2][0],1e-12);
        assertEquals(0.0,recovered[2][1],1e-12);

        // Therefore the known projective positions cannot all be recovered.
        assertEquals(12,errors.length);
        assertTrue(sum/12.0>20.0);
        assertTrue(max>40.0);
    }

    /** Equivalent to the current four-point solve when both opposite pairs are symmetric. */
    private static double[][] symmetricCardinalPose(Point[] p){
        Point center=new Point((p[0].x+p[2].x)*0.5,(p[0].y+p[2].y)*0.5);
        return new double[][]{
                {(p[1].x-p[3].x)*0.5,(p[2].x-p[0].x)*0.5,center.x},
                {(p[1].y-p[3].y)*0.5,(p[2].y-p[0].y)*0.5,center.y},
                {0.0,0.0,1.0}
        };
    }

    private static RotatedRect ellipseFromProjectedUnitCircle(double[][] h){
        double[][] inv=invert3(h);
        double[][] circle={{1,0,0},{0,1,0},{0,0,-1}};
        double[][] conic=multiply(transpose(inv),multiply(circle,inv));
        double a=conic[0][0],b=2.0*conic[0][1],c=conic[1][1];
        double d=2.0*conic[0][2],e=2.0*conic[1][2],f=conic[2][2];
        double det=4.0*a*c-b*b;
        double cx=(b*e-2.0*c*d)/det;
        double cy=(b*d-2.0*a*e)/det;
        double fc=a*cx*cx+b*cx*cy+c*cy*cy+d*cx+e*cy+f;

        double trace=a+c,diff=a-c,root=Math.hypot(diff,b);
        double lambda1=(trace+root)*0.5,lambda2=(trace-root)*0.5;
        double angle=0.5*Math.atan2(b,diff);
        double r1=Math.sqrt(-fc/lambda1),r2=Math.sqrt(-fc/lambda2);
        return new RotatedRect(new Point(cx,cy),new Size(2.0*r1,2.0*r2),Math.toDegrees(angle));
    }

    private static Point project(double[][] h,double x,double y){
        double w=h[2][0]*x+h[2][1]*y+h[2][2];
        return new Point((h[0][0]*x+h[0][1]*y+h[0][2])/w,
                (h[1][0]*x+h[1][1]*y+h[1][2])/w);
    }

    private static double[][] invert3(double[][] m){
        double a=m[0][0],b=m[0][1],c=m[0][2],d=m[1][0],e=m[1][1],f=m[1][2],g=m[2][0],h=m[2][1],i=m[2][2];
        double det=a*(e*i-f*h)-b*(d*i-f*g)+c*(d*h-e*g);
        return new double[][]{
                {(e*i-f*h)/det,(c*h-b*i)/det,(b*f-c*e)/det},
                {(f*g-d*i)/det,(a*i-c*g)/det,(c*d-a*f)/det},
                {(d*h-e*g)/det,(b*g-a*h)/det,(a*e-b*d)/det}
        };
    }

    private static double[][] transpose(double[][] m){
        double[][] out=new double[m[0].length][m.length];
        for(int r=0;r<m.length;r++)for(int c=0;c<m[0].length;c++)out[c][r]=m[r][c];
        return out;
    }

    private static double[][] multiply(double[][] a,double[][] b){
        double[][] out=new double[a.length][b[0].length];
        for(int r=0;r<a.length;r++)for(int c=0;c<b[0].length;c++)for(int k=0;k<b.length;k++)out[r][c]+=a[r][k]*b[k][c];
        return out;
    }
}
