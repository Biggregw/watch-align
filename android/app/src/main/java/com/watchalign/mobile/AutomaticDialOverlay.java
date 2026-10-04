package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

/**
 * One-photo proof path. The physical black-dial edge gives an initial ellipse pose.
 * Perspective is then solved from EXPLICIT measured minor-minute inner ends, admitted
 * only as complete opposing pairs. Applied hour markers and the 12 triangle are not
 * used by the projective fit and remain independent visual checks.
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
        Result(Bitmap overlay,DialEdgeEllipseFit.Fit e,boolean twelve, OpposingMinuteHomographyFitter.Result d){
            valid=true;this.overlay=overlay;reason="";twelvePhaseUsed=twelve;projectiveAccepted=d!=null&&d.accepted;
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

            GmtDialSeedAnalyzer.Result seed=GmtDialSeedAnalyzer.analyse(bgr);
            if(seed==null||!seed.valid)return new Result(seed==null?"automatic dial seed failed":seed.reason);

            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(bgr,seed.x,seed.y,seed.r);
            if(edge==null)return new Result("physical black-dial boundary could not be fitted");
            if(edge.points<70||edge.rmsPx>Math.max(5.0,edge.meanRadius()*0.030))
                return new Result("dial boundary fit was not stable enough");

            // Local 12 establishes clock phase only. It is never used to change scale,
            // centre or projective terms, so the triangle cannot align itself.
            boolean twelveUsed=false;
            double targetDx=0.0,targetDy=-1.0;
            try{
                GmtTwelveLandmarkAnalyzer.Result twelve=GmtTwelveLandmarkAnalyzer.analyse(bgr,edge.cx,edge.cy,edge.meanRadius());
                if(twelve!=null&&twelve.valid&&twelve.geometry!=null&&twelve.geometry.tick60!=null){
                    double dx=twelve.geometry.tick60[0]-edge.cx,dy=twelve.geometry.tick60[1]-edge.cy;
                    if(Math.hypot(dx,dy)>0.4*edge.meanRadius()){
                        targetDx=dx;targetDy=dy;twelveUsed=true;
                    }
                }
            }catch(Throwable ignored){}

            h0=ellipsePose(edge,targetDx,targetDy);
            if(h0==null)return new Result("ellipse pose could not be constructed");

            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
            clahe.apply(gray,enh);
            Imgproc.GaussianBlur(enh,enh,new Size(3,3),0.65);

            // IMPORTANT: unlike the old annular distance-transform refiner, this
            // explicitly locates each minor minute tick and fits only complete
            // opposing pairs. No generic outer-ring edges enter this homography.
            pairFit=OpposingMinuteHomographyFitter.fit(enh,h0);
            h=(pairFit!=null&&pairFit.homography!=null)?pairFit.homography.clone():h0.clone();
            if(h==null||h.empty())return new Result("opposing-minute perspective fit failed");

            Bitmap overlay=warpOutline(input.getWidth(),input.getHeight(),h);
            if(overlay==null)return new Result("dial outline rendering failed");
            return new Result(overlay,edge,twelveUsed,pairFit);
        }catch(Throwable t){
            return new Result("automatic overlay failed: "+t.getClass().getSimpleName());
        }finally{
            if(pairFit!=null&&pairFit.homography!=null)pairFit.homography.release();
            if(h!=null)h.release();if(h0!=null)h0.release();
            enh.release();gray.release();bgr.release();rgba.release();
        }
    }

    /** Map the canonical unit dial onto the fitted ellipse, choosing the free circle
     * rotation so canonical 12 points along the detected local-12 direction. */
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
