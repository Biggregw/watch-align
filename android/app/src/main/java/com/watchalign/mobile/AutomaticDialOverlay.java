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
 * One-photo proof path. The physical black-dial edge is found automatically, then
 * the minor minute track refines the projective pose. A clean bright 126710BLNR
 * outline, measured from the official straight genuine reference and an independent
 * real photo, is warped into that pose. No photographic texture, hands, text,
 * manual 12/6 points, nudges, measurements, tolerances or QC verdicts are shown.
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

        Result(String reason){
            valid=false;overlay=null;this.reason=reason;twelvePhaseUsed=false;projectiveAccepted=false;
            dialCx=dialCy=dialRadius=ellipseRatio=edgeRms=fitBefore=fitAfter=holdoutBefore=holdoutAfter=Double.NaN;
        }
        Result(Bitmap overlay,DialEdgeEllipseFit.Fit e,boolean twelve, DialProjectiveRefiner.Result d){
            valid=true;this.overlay=overlay;reason="";twelvePhaseUsed=twelve;projectiveAccepted=d!=null&&d.accepted;
            dialCx=e.cx;dialCy=e.cy;dialRadius=e.meanRadius();ellipseRatio=Math.min(e.axisA,e.axisB)/Math.max(e.axisA,e.axisB);edgeRms=e.rmsPx;
            fitBefore=d==null?Double.NaN:d.fitBefore;fitAfter=d==null?Double.NaN:d.fitAfter;
            holdoutBefore=d==null?Double.NaN:d.holdoutBefore;holdoutAfter=d==null?Double.NaN:d.holdoutAfter;
        }
    }

    private AutomaticDialOverlay(){}

    static Result build(Bitmap input){
        if(input==null)return new Result("no candidate image");
        Mat rgba=new Mat(),bgr=new Mat(),gray=new Mat(),enh=new Mat(),edges=new Mat();
        Mat h0=null,h=null;
        try{
            Utils.bitmapToMat(input,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);

            GmtDialSeedAnalyzer.Result seed=GmtDialSeedAnalyzer.analyse(bgr);
            if(seed==null||!seed.valid)return new Result(seed==null?"automatic dial seed failed":seed.reason);

            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(bgr,seed.x,seed.y,seed.r);
            if(edge==null)return new Result("physical black-dial boundary could not be fitted");
            if(edge.points<70||edge.rmsPx>Math.max(5.0,edge.meanRadius()*0.030))
                return new Result("dial boundary fit was not stable enough");

            // Establish only the clock phase automatically. The triangle/60 detector is
            // never used for scale, centre or projective terms. If it cannot resolve 12,
            // fall back to the normal upright-photo prior used by this POC.
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
            Imgproc.GaussianBlur(enh,enh,new Size(3,3),0.8);
            Imgproc.Canny(enh,edges,45,130);

            DialProjectiveRefiner.MatResult refined=DialProjectiveRefiner.refineWithDiagnostics(edges,h0);
            DialProjectiveRefiner.Result diag=refined==null?null:refined.diagnostics;
            h=refined==null?h0.clone():refined.homography;
            if(h==null||h.empty())return new Result("minute-track perspective fit failed");

            Bitmap overlay=warpOutline(input.getWidth(),input.getHeight(),h);
            if(overlay==null)return new Result("dial outline rendering failed");
            return new Result(overlay,edge,twelveUsed,diag);
        }catch(Throwable t){
            return new Result("automatic overlay failed: "+t.getClass().getSimpleName());
        }finally{
            if(h!=null)h.release();if(h0!=null)h0.release();
            edges.release();enh.release();gray.release();bgr.release();rgba.release();
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
