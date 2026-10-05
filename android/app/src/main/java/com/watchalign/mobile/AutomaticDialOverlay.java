package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

/**
 * Fixed-genuine-master perspective proof.
 *
 * Candidate evidence is restricted to:
 *   1) the physical black-dial boundary, used only as a coarse geometric seed/guard; and
 *   2) the 48 printed minor-minute ticks, admitted only as complete opposite pairs,
 *      used to estimate the camera pose/perspective.
 *
 * Applied hour markers, the 12 triangle, date/cyclops, hands and text are never used
 * for centre, scale, clock phase, rotation or perspective. The rendered dial geometry
 * always comes from the fixed genuine 126710BLNR master and is never reshaped from
 * candidate QC features. Canonical 12 is locked to image-up in this proof build.
 */
final class AutomaticDialOverlay {
    static final class Result {
        final boolean valid;
        final Bitmap overlay;
        final String reason;
        final boolean twelvePhaseUsed;
        final boolean projectiveAccepted;
        final double dialCx,dialCy,dialRadius,ellipseRatio,edgeRms;
        final double fitBefore,fitAfter,holdoutBefore,holdoutAfter;
        final int detectedTicks,completePairs,inliers;

        Result(String reason){
            valid=false;overlay=null;this.reason=reason;twelvePhaseUsed=false;projectiveAccepted=false;
            dialCx=dialCy=dialRadius=ellipseRatio=edgeRms=fitBefore=fitAfter=holdoutBefore=holdoutAfter=Double.NaN;
            detectedTicks=completePairs=inliers=0;
        }
        Result(Bitmap overlay,DialEdgeEllipseFit.Fit e, OpposingMinuteHomographyFitter.Result d){
            valid=true;this.overlay=overlay;reason="";twelvePhaseUsed=false;projectiveAccepted=d!=null&&d.accepted;
            dialCx=e.cx;dialCy=e.cy;dialRadius=e.meanRadius();ellipseRatio=Math.min(e.axisA,e.axisB)/Math.max(e.axisA,e.axisB);edgeRms=e.rmsPx;
            fitBefore=d==null?Double.NaN:d.seedRmsPx;fitAfter=d==null?Double.NaN:d.fittedRmsPx;
            holdoutBefore=holdoutAfter=Double.NaN;
            detectedTicks=d==null?0:d.detectedTicks;completePairs=d==null?0:d.completePairs;inliers=d==null?0:d.inliers;
        }
    }

    private AutomaticDialOverlay(){}

    static Result build(Bitmap input){
        if(input==null)return new Result("no candidate image");
        Mat rgba=new Mat(),bgr=new Mat(),gray=new Mat(),enh=new Mat();
        Mat h0=null,h=null;
        OpposingMinuteHomographyFitter.Result pairFit=null;
        try{
            Utils.bitmapToMat(input,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);

            // Strict coarse location: physical dark-dial boundary only. No hour-marker
            // brightness or other judged dial feature is allowed into the seed.
            StrictDialBoundarySeedAnalyzer.Result seed=StrictDialBoundarySeedAnalyzer.analyse(bgr);
            if(seed==null||!seed.valid)return new Result(seed==null?"automatic dial seed failed":seed.reason);

            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(bgr,seed.x,seed.y,seed.r);
            if(edge==null)return new Result("physical black-dial boundary could not be fitted");
            if(edge.points<70||edge.rmsPx>Math.max(5.0,edge.meanRadius()*0.030))
                return new Result("dial boundary fit was not stable enough");

            // No triangle/hour marker is consulted. Canonical 12 is image-up. The minor
            // ticks may estimate camera pose, but judged dial features cannot steer it.
            h0=ellipsePose(edge,0.0,-1.0);
            if(h0==null)return new Result("ellipse pose could not be constructed");

            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
            clahe.apply(gray,enh);
            Imgproc.GaussianBlur(enh,enh,new Size(3,3),0.65);

            // The final pose is estimated from explicit opposing MINOR minute ticks only.
            // The fixed genuine master is not compared with any hour marker/triangle.
            pairFit=OpposingMinuteHomographyFitter.fit(enh,h0);

            // Keep the minute-derived pose only if it remains physically consistent with
            // the independently fitted physical dial edge. This guard does not look at
            // any judged dial feature and prevents an implausibly flexible homography.
            if(pairFit!=null&&pairFit.accepted&&pairFit.homography!=null){
                String guard=physicalGuardReason(pairFit.homography,edge);
                if(guard!=null){
                    OpposingMinuteHomographyFitter.Result old=pairFit;
                    pairFit=new OpposingMinuteHomographyFitter.Result(
                            h0.clone(),false,old.detectedTicks,old.completePairs,old.inliers,
                            old.seedRmsPx,old.fittedRmsPx,guard);
                    if(old.homography!=null)old.homography.release();
                }
            }

            h=(pairFit!=null&&pairFit.homography!=null)?pairFit.homography.clone():h0.clone();
            if(h==null||h.empty())return new Result("opposing-minute perspective fit failed");

            // Render only the untouched fixed genuine master through the estimated pose.
            Bitmap overlay=warpOutline(input.getWidth(),input.getHeight(),h);
            if(overlay==null)return new Result("dial outline rendering failed");
            return new Result(overlay,edge,pairFit);
        }catch(Throwable t){
            return new Result("automatic overlay failed: "+t.getClass().getSimpleName());
        }finally{
            if(pairFit!=null&&pairFit.homography!=null)pairFit.homography.release();
            if(h!=null)h.release();if(h0!=null)h0.release();
            enh.release();gray.release();bgr.release();rgba.release();
        }
    }

    private static String physicalGuardReason(Mat fitted,DialEdgeEllipseFit.Fit e){
        double[] m=new double[9];
        fitted.get(0,0,m);
        if(m.length<9||!Double.isFinite(m[8])||Math.abs(m[8])<1e-9)
            return "minute fit rejected by physical-pose guard";
        double s=m[8];for(int i=0;i<9;i++)m[i]/=s;

        double projective=Math.hypot(m[6],m[7]);
        if(!Double.isFinite(projective)||projective>0.18)
            return "minute fit rejected: projective warp exceeded proof limit";

        Point c=project(fitted,0,0);
        if(c==null||Math.hypot(c.x-e.cx,c.y-e.cy)>0.055*e.meanRadius())
            return "minute fit rejected: projected centre moved too far";

        double edgeRms=ellipseConformanceRms(fitted,e);
        double limit=Math.max(2.5,e.rmsPx*1.35+0.75);
        if(!Double.isFinite(edgeRms)||edgeRms>limit)
            return "minute fit rejected: projected dial no longer matched physical edge";
        return null;
    }

    private static double ellipseConformanceRms(Mat h,DialEdgeEllipseFit.Fit e){
        double a=Math.toRadians(e.angleDeg),ca=Math.cos(a),sa=Math.sin(a);
        double ss=0;int n=0;
        for(int i=0;i<120;i++){
            double t=2.0*Math.PI*i/120.0;
            Point p=project(h,Math.cos(t),Math.sin(t));
            if(p==null)continue;
            double dx=p.x-e.cx,dy=p.y-e.cy;
            double u= ca*dx+sa*dy;
            double v=-sa*dx+ca*dy;
            double rho=Math.sqrt((u*u)/(e.axisA*e.axisA)+(v*v)/(e.axisB*e.axisB));
            double d=(rho-1.0)*e.meanRadius();
            if(Double.isFinite(d)){ss+=d*d;n++;}
        }
        return n<80?Double.NaN:Math.sqrt(ss/n);
    }

    private static Mat ellipsePose(DialEdgeEllipseFit.Fit e,double targetDx,double targetDy){
        double a=Math.toRadians(e.angleDeg),ca=Math.cos(a),sa=Math.sin(a);
        double a00=ca*e.axisA,a01=-sa*e.axisB;
        double a10=sa*e.axisA,a11= ca*e.axisB;
        double det=a00*a11-a01*a10;if(Math.abs(det)<1e-9)return null;

        double vx=( a11*targetDx-a01*targetDy)/det;
        double vy=(-a10*targetDx+a00*targetDy)/det;
        double vn=Math.hypot(vx,vy);if(!(vn>1e-9)){vx=0;vy=-1;vn=1;}
        vx/=vn;vy/=vn;
        double phi=Math.atan2(vx,-vy),cp=Math.cos(phi),sp=Math.sin(phi);
        double r00=cp,r01=-sp,r10=sp,r11=cp;

        double h00=a00*r00+a01*r10,h01=a00*r01+a01*r11;
        double h10=a10*r00+a11*r10,h11=a10*r01+a11*r11;
        Mat out=Mat.eye(3,3,CvType.CV_64F);
        out.put(0,0,h00,h01,e.cx,h10,h11,e.cy,0,0,1);
        return out;
    }

    private static Point project(Mat h,double x,double y){
        double[] m=new double[9];h.get(0,0,m);
        double w=m[6]*x+m[7]*y+m[8];
        if(Math.abs(w)<1e-9)return null;
        return new Point((m[0]*x+m[1]*y+m[2])/w,(m[3]*x+m[4]*y+m[5])/w);
    }

    private static Bitmap warpOutline(int w,int h,Mat canonicalToImage){
        Bitmap ref=BakedDialOutline.bitmap();
        Mat src=new Mat(),dst=new Mat(),n=Mat.eye(3,3,CvType.CV_64F),m=null;
        try{
            Utils.bitmapToMat(ref,src);
            double invR=1.0/BakedDialOutline.R;
            n.put(0,0,invR,0,-BakedDialOutline.CX*invR,
                    0,invR,-BakedDialOutline.CY*invR,
                    0,0,1);
            m=multiply(canonicalToImage,n);
            Imgproc.warpPerspective(src,dst,m,new Size(w,h),Imgproc.INTER_LINEAR,org.opencv.core.Core.BORDER_CONSTANT,new Scalar(0,0,0,0));
            Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(dst,out);
            return out;
        }finally{
            if(m!=null)m.release();n.release();dst.release();src.release();
        }
    }

    private static Mat multiply(Mat a,Mat b){
        double[] av=new double[9],bv=new double[9],cv=new double[9];a.get(0,0,av);b.get(0,0,bv);
        for(int r=0;r<3;r++)for(int c=0;c<3;c++)for(int k=0;k<3;k++)cv[r*3+c]+=av[r*3+k]*bv[k*3+c];
        Mat out=new Mat(3,3,CvType.CV_64F);out.put(0,0,cv);return out;
    }
}
