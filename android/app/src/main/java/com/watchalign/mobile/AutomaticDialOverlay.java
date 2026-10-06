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
 * Alpha92 integration proof of the Alpha91 minute-lattice registration.
 *
 * Candidate evidence is restricted to the physical black-dial boundary and printed
 * minute track. Applied hour markers, hands, date/cyclops, text and logo never pull
 * the final homography. A local 12 minute-frame may be used only to lock the 6-degree
 * phase branch; it does not provide precision geometry.
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
        Result(Bitmap overlay,DialEdgeEllipseFit.Fit e,Alpha91MinuteLatticeFitter.Result d,boolean phaseUsed){
            valid=true;this.overlay=overlay;reason="";twelvePhaseUsed=phaseUsed;projectiveAccepted=d!=null&&d.accepted;
            dialCx=e.cx;dialCy=e.cy;dialRadius=e.meanRadius();ellipseRatio=Math.min(e.axisA,e.axisB)/Math.max(e.axisA,e.axisB);edgeRms=e.rmsPx;
            fitBefore=Double.NaN;fitAfter=d==null?Double.NaN:d.tickRmsPx;
            holdoutBefore=holdoutAfter=Double.NaN;
            detectedTicks=d==null?0:d.ticksUsed;completePairs=d==null?0:d.sectorsUsed;inliers=d==null?0:d.ticksUsed;
        }
    }

    private AutomaticDialOverlay(){}

    static Result build(Bitmap input){
        if(input==null)return new Result("no candidate image");
        Mat rgba=new Mat(),bgr=new Mat(),gray=new Mat(),enh=new Mat();
        Mat h0=null,coarse=null,h=null;
        Alpha91MinuteLatticeFitter.Result lattice=null;
        try{
            Utils.bitmapToMat(input,rgba);
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);

            StrictDialBoundarySeedAnalyzer.Result seed=StrictDialBoundarySeedAnalyzer.analyse(bgr);
            if(seed==null||!seed.valid)return new Result(seed==null?"automatic dial seed failed":seed.reason);

            DialEdgeEllipseFit.Fit edge=DialEdgeFitter.fitBgr(bgr,seed.x,seed.y,seed.r);
            if(edge==null)return new Result("physical black-dial boundary could not be fitted");
            if(edge.points<70||edge.rmsPx>Math.max(5.0,edge.meanRadius()*0.030))
                return new Result("dial boundary fit was not stable enough");

            // The 12 marker is allowed only to identify which repeating minute position is 60.
            // Precision direction comes from the local 59/60/01 minute frame.
            GmtTwelveLandmarkAnalyzer.Result phase=GmtTwelveLandmarkAnalyzer.analyse(bgr,edge.cx,edge.cy,edge.meanRadius());
            if(phase==null||!phase.valid){
                String why=phase==null?"12 frame unavailable":phase.reason;
                phase=GmtTwelveRecoveryAnalyzer.analyse(bgr,edge.cx,edge.cy,edge.meanRadius(),why);
            }
            boolean phaseUsed=phase!=null&&phase.valid&&Double.isFinite(phase.trackRollClockDeg);
            double phaseDeg=phaseUsed?phase.trackRollClockDeg:0.0;
            double pr=Math.toRadians(phaseDeg);
            h0=ellipsePose(edge,Math.sin(pr),-Math.cos(pr));
            if(h0==null)return new Result("ellipse pose could not be constructed");

            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            CLAHE clahe=Imgproc.createCLAHE(2.0,new Size(8,8));
            clahe.apply(gray,enh);
            Imgproc.GaussianBlur(enh,enh,new Size(3,3),0.65);

            // Basin only: this is the existing minute-track detector with its final-QC
            // acceptance rules intentionally removed. If it cannot improve the seed it
            // simply returns the ellipse pose. No applied marker geometry is used.
            coarse=OpposingMinuteHomographyFitter.coarseSeed(enh,h0);
            if(coarse==null||coarse.empty())coarse=h0.clone();

            lattice=Alpha91MinuteLatticeFitter.fit(gray,coarse);
            if(lattice==null||!lattice.accepted||lattice.homography==null||lattice.homography.empty()){
                String why=lattice==null?"minute-lattice solve unavailable":lattice.reason;
                return new Result("overlay unavailable: "+why);
            }

            h=lattice.homography.clone();

            // Explicit 12-branch guard where the local minute frame was available.
            if(phaseUsed){
                double finalPhase=Alpha91MinuteLatticeFitter.clock12Deg(h);
                double d=wrap180(finalPhase-phaseDeg);
                if(!Double.isFinite(d)||Math.abs(d)>=2.0)
                    return new Result("overlay unavailable: 12/minute-lattice phase check failed");
            }

            String guard=physicalGuardReason(h,edge);
            if(guard!=null)return new Result("overlay unavailable: "+guard);

            Bitmap overlay=warpOutline(input.getWidth(),input.getHeight(),h);
            if(overlay==null)return new Result("dial outline rendering failed");
            return new Result(overlay,edge,lattice,phaseUsed);
        }catch(Throwable t){
            return new Result("automatic overlay failed: "+t.getClass().getSimpleName());
        }finally{
            if(lattice!=null&&lattice.homography!=null)lattice.homography.release();
            if(h!=null)h.release();if(coarse!=null)coarse.release();if(h0!=null)h0.release();
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
        if(c==null||Math.hypot(c.x-e.cx,c.y-e.cy)>0.090*e.meanRadius())
            return "minute fit rejected: projected centre moved too far";

        // Alpha92's original guard compared the final projective dial boundary
        // pixel-for-pixel with the *initial* ellipse fit. On oblique photos the raised
        // flange can hide parts of the physical dial edge and bias that seed ellipse,
        // even while the printed minute lattice is fit correctly. Keep the edge fit as
        // a coarse physical scale/centre sanity check, not a precision veto.
        double area=projectedUnitCircleArea(fitted);
        double seedArea=Math.PI*e.axisA*e.axisB;
        if(!Double.isFinite(area)||!Double.isFinite(seedArea)||seedArea<=1.0)
            return "minute fit rejected: projected dial area unavailable";
        double ratio=area/seedArea;
        if(ratio<0.72||ratio>1.38)
            return "minute fit rejected: projected dial scale disagreed with physical edge";
        return null;
    }

    private static double projectedUnitCircleArea(Mat h){
        final int n=120;
        Point[] p=new Point[n];
        for(int i=0;i<n;i++){
            double t=2.0*Math.PI*i/n;
            p[i]=project(h,Math.cos(t),Math.sin(t));
            if(p[i]==null)return Double.NaN;
        }
        double a=0.0;
        for(int i=0;i<n;i++){
            Point q=p[(i+1)%n];
            a+=p[i].x*q.y-q.x*p[i].y;
        }
        return Math.abs(a)*0.5;
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

    private static double wrap180(double d){while(d>180)d-=360;while(d<=-180)d+=360;return d;}

    private static Bitmap warpOutline(int w,int h,Mat canonicalToImage){
        Bitmap ref=Alpha92BakedDialOutline.bitmap();
        Mat src=new Mat(),dst=new Mat(),n=Mat.eye(3,3,CvType.CV_64F),m=null;
        try{
            Utils.bitmapToMat(ref,src);
            double invR=1.0/Alpha92BakedDialOutline.R;
            n.put(0,0,invR,0,-Alpha92BakedDialOutline.CX*invR,
                    0,invR,-Alpha92BakedDialOutline.CY*invR,
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
