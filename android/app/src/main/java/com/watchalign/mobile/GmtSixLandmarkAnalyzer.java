package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * Measures the 6 o'clock baton against the 29/30/31 minute ticks (alpha55).
 *
 * The image is turned 180 degrees about the dial centre (a point reflection, so no
 * resampling) which puts the 6 baton where the 12 triangle normally is. That lets the
 * 12 marker's minute-tick finder and outer-edge line fitting be reused unchanged.
 * Everything returned is mapped back to the original image.
 *
 * Measured, each as a fraction of the baton width:
 *   centring  - sideways offset of the baton's outer end from the midpoint of the 29 and
 *               31 tick ends; positive = towards the 29 tick, i.e. to the viewer's RIGHT at 6
 *   gap       - outer end to the 29-31 tick line
 * and rotation, the baton axis against the square to the 29-31 chord, degrees, positive = CW.
 */
final class GmtSixLandmarkAnalyzer {
    static final class Geometry {
        /** Outer-end corners (viewer's left / right at 6), inner corners, ticks 31/30/29 inner ends. */
        final double[] outerLeft,outerRight,innerLeft,innerRight,tick31,tick30,tick29;
        final boolean outerEdge;
        Geometry(double[] ol,double[] or,double[] il,double[] ir,double[] t31,double[] t30,double[] t29,boolean outer){
            outerLeft=ol;outerRight=or;innerLeft=il;innerRight=ir;tick31=t31;tick30=t30;tick29=t29;outerEdge=outer;
        }
        double[][] polygon(){return new double[][]{innerLeft,outerLeft,outerRight,innerRight};}
    }

    static final class Result {
        final boolean valid,stable;final String reason;
        final double gap,centring,rotationDeg,widthPx,lengthPx;
        final Geometry geometry;
        Result(String why){valid=false;stable=false;reason=why;gap=centring=rotationDeg=widthPx=lengthPx=Double.NaN;geometry=null;}
        Result(double gap,double centring,double rot,double width,double length,boolean stable,Geometry g){
            valid=true;reason="";this.gap=gap;this.centring=centring;rotationDeg=rot;widthPx=width;lengthPx=length;this.stable=stable;geometry=g;
        }
        Result lowConfidence(){return valid?new Result(gap,centring,rotationDeg,widthPx,lengthPx,false,geometry):this;}
    }

    // Plausible baton size, dial radii (measured master: half-length 0.150R, half-width 0.060R).
    static final double MIN_LEN_R=0.20, MAX_LEN_R=0.40, MIN_WID_R=0.07, MAX_WID_R=0.18;
    static final double PARALLEL_TOLERANCE_DEG=2.0;

    static boolean DEBUG=Boolean.getBoolean("wa.six.debug");
    private GmtSixLandmarkAnalyzer(){}

    static Result analyse(Mat bgr,double cx,double cy,double r){
        if(bgr==null||bgr.empty()||!(r>20))return new Result("invalid dial seed");
        Mat flipped=new Mat(),gray=new Mat(),enh=new Mat();
        try{
            Core.flip(bgr,flipped,-1);
            final int W=flipped.cols(),H=flipped.rows();
            final double fx=W-1-cx,fy=H-1-cy;
            Imgproc.cvtColor(flipped,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
            clahe.apply(gray,enh);

            double[][] rect=batonCandidate(enh,fx,fy,r);
            if(rect==null)return new Result("6 baton not found");
            // rect = {innerLeft, outerLeft, outerRight, innerRight} in the flipped image
            double[] om=mid(rect[1],rect[2]);
            double[][] frame=GmtTwelveLandmarkAnalyzer.tickFrameNear(enh,fx,fy,r,om[0],om[1]);
            if(frame==null)return new Result("29/30/31 minute-track frame not sufficiently constrained");

            boolean outerEdge=false;
            double[][] refined=refine(enh,rect,frame,r);
            if(refined!=null){
                double[] om2=mid(refined[1],refined[2]);
                double[][] again=GmtTwelveLandmarkAnalyzer.tickFrameNear(enh,fx,fy,r,om2[0],om2[1]);
                if(again!=null){rect=refined;frame=again;outerEdge=true;}
            }

            // Back to the original image: p -> (W-1-x, H-1-y).
            double[] il=back(rect[0],W,H),ol=back(rect[1],W,H),or=back(rect[2],W,H),ir=back(rect[3],W,H);
            double[] tA=back(frame[0],W,H),t30=back(frame[1],W,H),tB=back(frame[2],W,H);
            // In the flipped image, left is the 29 tick; after flipping back, the 29 tick is
            // on the viewer's right at 6. Name by distance so it cannot be swapped.
            double[] t29=tA,t31=tB;
            // Viewer's left at 6 is image-left for an upright photo; name corners by the
            // 31->29 direction so a rotated photo still gets them right.
            double sx=t29[0]-t31[0],sy=t29[1]-t31[1],sl=Math.hypot(sx,sy);
            if(sl<1e-9)return new Result("29/31 ticks coincide");
            sx/=sl;sy/=sl;
            if((or[0]-ol[0])*sx+(or[1]-ol[1])*sy<0){double[] z=ol;ol=or;or=z;z=il;il=ir;ir=z;}

            double width=Math.hypot(or[0]-ol[0],or[1]-ol[1]);
            if(!(width>1))return new Result("6 baton width is degenerate");
            double[] outerMid=mid(ol,or),innerMid=mid(il,ir);
            double length=Math.hypot(outerMid[0]-innerMid[0],outerMid[1]-innerMid[1]);

            // Local reference only: the midpoint of the 29 and 31 tick ends and the chord
            // between them. There is no printed 30 tick (the SWISS MADE coronet sits there),
            // and a line from the fitted dial centre swings with any centre error (a genuine
            // photo read 3.9 deg that way while its 12 read -2.5 deg).
            double[] tm=mid(t31,t29);
            double gap=Math.abs(pointLineDistance(outerMid,t31,t29))/width;
            double centring=((outerMid[0]-tm[0])*sx+(outerMid[1]-tm[1])*sy)/width;
            double ux=-sy,uy=sx;                                   // square to the chord
            if((tm[0]-cx)*ux+(tm[1]-cy)*uy<0){ux=-ux;uy=-uy;}      // pointing outward
            double axis=Math.atan2(outerMid[1]-innerMid[1],outerMid[0]-innerMid[0]);
            double rot=wrap90(Math.toDegrees(axis-Math.atan2(uy,ux)));

            double pitch=frame[3][1],score=frame[3][2],inferred=frame[3][3];
            boolean stable=outerEdge&&score>=5.0&&pitch>=5.35&&pitch<=6.65&&inferred<=1;
            Geometry g=new Geometry(ol,or,il,ir,t31,t30,t29,outerEdge);
            return new Result(gap,centring,rot,width,length,stable,g);
        }catch(Throwable t){
            return new Result("6 marker analysis failed: "+t.getClass().getSimpleName());
        }finally{flipped.release();gray.release();enh.release();}
    }

    /** Baton as a rotated rectangle near the top of the (flipped) image: {innerL, outerL, outerR, innerR}. */
    private static double[][] batonCandidate(Mat gray,double cx,double cy,double r){
        int x0=Math.max(0,(int)Math.floor(cx-.30*r)),x1=Math.min(gray.cols(),(int)Math.ceil(cx+.30*r));
        int y0=Math.max(0,(int)Math.floor(cy-1.01*r)),y1=Math.min(gray.rows(),(int)Math.ceil(cy-.45*r));
        if(x1<=x0||y1<=y0)return null;
        Mat patch=gray.submat(new Rect(x0,y0,x1-x0,y1-y0));
        List<Mat> masks=new ArrayList<>();
        double[][] best=null;double bestScore=-Double.MAX_VALUE;
        try{
            Mat otsu=new Mat();Imgproc.threshold(patch,otsu,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);masks.add(otsu);
            Mat adaptive=new Mat();int block=Math.max(15,((int)Math.round(r*.10))|1);
            Imgproc.adaptiveThreshold(patch,adaptive,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,-3);masks.add(adaptive);
            for(Mat input:masks){
                Mat m=input.clone();
                Mat k=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));
                Imgproc.morphologyEx(m,m,Imgproc.MORPH_OPEN,k);k.release();
                List<MatOfPoint> contours=new ArrayList<>();Mat hier=new Mat();
                Imgproc.findContours(m,contours,hier,Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
                m.release();hier.release();
                for(MatOfPoint c:contours){
                    double area=Imgproc.contourArea(c);
                    if(area<20){c.release();continue;}
                    MatOfPoint2f c2=new MatOfPoint2f(c.toArray());
                    RotatedRect rr=Imgproc.minAreaRect(c2);c2.release();c.release();
                    double mx=rr.center.x+x0,my=rr.center.y+y0;
                    double rad=Math.hypot(mx-cx,my-cy)/r;
                    double ang=Math.toDegrees(Math.atan2(mx-cx,cy-my));
                    if(rad<.62||rad>.88||Math.abs(ang)>15)continue;
                    Point[] v=new Point[4];rr.points(v);
                    for(Point p:v){p.x+=x0;p.y+=y0;}
                    // Long axis and its direction relative to the radial line.
                    double e1=Math.hypot(v[1].x-v[0].x,v[1].y-v[0].y),e2=Math.hypot(v[2].x-v[1].x,v[2].y-v[1].y);
                    double len=Math.max(e1,e2),wid=Math.min(e1,e2);
                    if(len<MIN_LEN_R*r||len>MAX_LEN_R*r||wid<MIN_WID_R*r||wid>MAX_WID_R*r)continue;
                    double aspect=len/wid;if(aspect<1.8||aspect>3.8)continue;
                    if(area/(len*wid)<0.80)continue;                       // rectangle, not a blob or a ring
                    double lx,ly;if(e1>=e2){lx=v[1].x-v[0].x;ly=v[1].y-v[0].y;}else{lx=v[2].x-v[1].x;ly=v[2].y-v[1].y;}
                    double rx=mx-cx,ry=my-cy;
                    double cosA=Math.abs(lx*rx+ly*ry)/(Math.hypot(lx,ly)*Math.hypot(rx,ry));
                    if(cosA<Math.cos(Math.toRadians(15)))continue;           // long axis must be radial
                    double s=area-.3*area*Math.min(1,Math.abs(ang)/12.0)-.1*area*Math.abs(aspect-2.5);
                    if(s>bestScore){bestScore=s;best=orderCorners(v,cx,cy);}
                }
            }
            return best;
        }finally{for(Mat m:masks)m.release();patch.release();}
    }

    /** Orders rectangle corners as {innerLeft, outerLeft, outerRight, innerRight} (image left/right). */
    private static double[][] orderCorners(Point[] v,double cx,double cy){
        double mx=0,my=0;for(Point p:v){mx+=p.x;my+=p.y;}mx/=4;my/=4;
        double on=Math.hypot(mx-cx,my-cy);final double ox=(mx-cx)/on,oy=(my-cy)/on;
        double tx=-oy,ty=ox;
        Point[] s=v.clone();
        java.util.Arrays.sort(s,(a,b)->Double.compare(a.x*ox+a.y*oy,b.x*ox+b.y*oy));
        Point i1=s[0],i2=s[1],o1=s[2],o2=s[3];
        // "left" = smaller tangential coordinate
        if((i1.x-cx)*tx+(i1.y-cy)*ty>(i2.x-cx)*tx+(i2.y-cy)*ty){Point z=i1;i1=i2;i2=z;}
        if((o1.x-cx)*tx+(o1.y-cy)*ty>(o2.x-cx)*tx+(o2.y-cy)*ty){Point z=o1;o1=o2;o2=z;}
        double[][] q={{i1.x,i1.y},{o1.x,o1.y},{o2.x,o2.y},{i2.x,i2.y}};
        // Image-left first for an upright (flipped) marker.
        if(q[1][0]>q[2][0]){double[] z=q[0];q[0]=q[3];q[3]=z;z=q[1];q[1]=q[2];q[2]=z;}
        return q;
    }

    /** Long sides and outer end re-fitted on the outer edge; null keeps the rough rectangle. */
    private static double[][] refine(Mat gray,double[][] q,double[][] frame,double r){
        try{
            final int w=gray.cols(),h=gray.rows();
            final byte[] px=new byte[w*h];gray.get(0,0,px);
            DialEdgeEllipseFit.Intensity img=(x,y)->{
                int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double ffx=x-x0,ffy=y-y0;int i=y0*w+x0;
                double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+w]&0xff,d=px[i+w+1]&0xff;
                return (a*(1-ffx)+b*ffx)*(1-ffy)+(c*(1-ffx)+d*ffx)*ffy;
            };
            double gx=(q[0][0]+q[1][0]+q[2][0]+q[3][0])/4,gy=(q[0][1]+q[1][1]+q[2][1]+q[3][1])/4;
            double[] left=TriangleEdgeRefiner.fitSide(img,w,h,q[0],q[1],gx,gy,r,0.15,0.85,0.035,null,null);
            double[] right=TriangleEdgeRefiner.fitSide(img,w,h,q[3],q[2],gx,gy,r,0.15,0.85,0.035,null,null);
            double[] end=TriangleEdgeRefiner.fitSide(img,w,h,q[1],q[2],gx,gy,r,0.15,0.85,0.045,frame[0],frame[2]);
            double[] inner=TriangleEdgeRefiner.fitSide(img,w,h,q[0],q[3],gx,gy,r,0.15,0.85,0.035,null,null);
            if(DEBUG)System.err.println("six refine: left="+(left!=null)+" right="+(right!=null)+" end="+(end!=null)+" inner="+(inner!=null));
            if(left==null||right==null||end==null)return null;
            double par=Math.toDegrees(Math.acos(Math.min(1,Math.abs(left[2]*right[2]+left[3]*right[3]))));
            if(DEBUG)System.err.printf("six refine: parallel %.2f%n",par);
            if(par>PARALLEL_TOLERANCE_DEG)return null;
            double ax=left[2]+Math.signum(left[2]*right[2]+left[3]*right[3])*right[2],ay=left[3]+Math.signum(left[2]*right[2]+left[3]*right[3])*right[3];
            double sq=Math.toDegrees(Math.acos(Math.min(1,Math.abs(ax*end[2]+ay*end[3])/Math.hypot(ax,ay))));
            if(DEBUG)System.err.printf("six refine: square %.2f%n",90-sq);
            // The end is short (about 0.7 baton widths of usable edge), so its own direction
            // is noisy (+/-5 deg on a 26 px baton). The baton is a rectangle: keep the end's
            // position from the fit and take its direction square to the long sides.
            double an=Math.hypot(ax,ay);
            end=new double[]{end[0],end[1],-ay/an,ax/an};
            if(inner!=null)inner=new double[]{inner[0],inner[1],-ay/an,ax/an};
            double[] ol=TriangleEdgeRefiner.intersect(left,end),or=TriangleEdgeRefiner.intersect(right,end);
            double[] il=inner!=null?TriangleEdgeRefiner.intersect(left,inner):null,ir=inner!=null?TriangleEdgeRefiner.intersect(right,inner):null;
            if(ol==null||or==null)return null;
            if(il==null||ir==null){il=q[0];ir=q[3];}
            double lim=0.06*r;
            if(Math.hypot(ol[0]-q[1][0],ol[1]-q[1][1])>lim||Math.hypot(or[0]-q[2][0],or[1]-q[2][1])>lim)return null;
            if(Math.hypot(il[0]-q[0][0],il[1]-q[0][1])>lim||Math.hypot(ir[0]-q[3][0],ir[1]-q[3][1])>lim){il=q[0];ir=q[3];}
            return new double[][]{il,ol,or,ir};
        }catch(Throwable t){return null;}
    }

    private static double[] back(double[] p,int W,int H){return new double[]{W-1-p[0],H-1-p[1]};}
    private static double[] mid(double[] a,double[] b){return new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2};}
    private static double pointLineDistance(double[] p,double[] a,double[] b){
        double dx=b[0]-a[0],dy=b[1]-a[1],l=Math.hypot(dx,dy);if(l<1e-9)return Double.NaN;
        return ((p[0]-a[0])*dy-(p[1]-a[1])*dx)/l;
    }
    private static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
}
