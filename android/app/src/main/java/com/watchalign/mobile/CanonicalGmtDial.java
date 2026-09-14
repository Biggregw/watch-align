package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.PointF;
import com.watchalign.mobile.qc.RectificationConfidenceService;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** One reusable canonical GMT dial. All planar fine-geometry modules consume this warp. */
final class CanonicalGmtDial implements AutoCloseable {
    static final int SIZE=1200;static final double CENTER=SIZE/2.0,RADIUS=500.0;
    private static final ThreadLocal<String> LAST_FAILURE = new ThreadLocal<>();
    final Mat bgr;final Mat gray;final PerspectiveMasterRenderer.Pose pose;final RectificationConfidenceService.Assessment rectification;

    private CanonicalGmtDial(Mat b,Mat g,PerspectiveMasterRenderer.Pose p,RectificationConfidenceService.Assessment r){bgr=b;gray=g;pose=p.copy();rectification=r;}

    static CanonicalGmtDial create(Bitmap source,PerspectiveMasterRenderer.Pose pose){
        LAST_FAILURE.remove();
        if(source==null){LAST_FAILURE.set("source image is unavailable");return null;}
        if(pose==null||!pose.anchorMode||!pose.perspectiveMode){LAST_FAILURE.set("corrected anchor pose is unavailable");return null;}
        if(!finitePose(pose)){LAST_FAILURE.set("corrected anchor pose contains non-finite coordinates");return null;}
        if(anchorArea(pose)<25.0){LAST_FAILURE.set("corrected anchor quadrilateral is degenerate");return null;}
        Mat rgba=new Mat(),src=new Mat(),dst=new Mat(),gray=new Mat();MatOfPoint2f from=null,to=null;Mat h=null;String stage="bitmapToMat";
        try{
            Utils.bitmapToMat(source,rgba);
            stage="RGBA to BGR";Imgproc.cvtColor(rgba,src,Imgproc.COLOR_RGBA2BGR);
            stage="anchor matrix";from=new MatOfPoint2f(new Point(pose.anchor12X,pose.anchor12Y),new Point(pose.anchor3X,pose.anchor3Y),new Point(pose.anchor6X,pose.anchor6Y),new Point(pose.anchor9X,pose.anchor9Y));
            to=new MatOfPoint2f(new Point(CENTER,CENTER-RADIUS),new Point(CENTER+RADIUS,CENTER),new Point(CENTER,CENTER+RADIUS),new Point(CENTER-RADIUS,CENTER));
            stage="getPerspectiveTransform";h=Imgproc.getPerspectiveTransform(from,to);
            if(h==null||h.empty()){LAST_FAILURE.set("getPerspectiveTransform returned an empty homography");return null;}
            stage="warpPerspective";Imgproc.warpPerspective(src,dst,h,new Size(SIZE,SIZE),Imgproc.INTER_CUBIC,Core.BORDER_REPLICATE);
            if(dst.empty()){LAST_FAILURE.set("warpPerspective returned an empty canonical dial");return null;}
            stage="canonical grayscale";Imgproc.cvtColor(dst,gray,Imgproc.COLOR_BGR2GRAY);

            // The anchors themselves cannot prove that the correct physical edge was selected.
            // Independently inspect the rectified radial edge profile: radius 1.0 must be a strong
            // dial-side boundary and a second strong, concentric rehaut edge must sit OUTSIDE it.
            // If that pair cannot be verified, rectification confidence is capped at MEDIUM.
            stage="rectification diagnostics";
            double pairScore=safeInnerBoundaryPairScore(gray);
            double axis=Math.min(Math.hypot(pose.anchor6X-pose.anchor12X,pose.anchor6Y-pose.anchor12Y),Math.hypot(pose.anchor9X-pose.anchor3X,pose.anchor9Y-pose.anchor3Y))/Math.max(1e-9,Math.max(Math.hypot(pose.anchor6X-pose.anchor12X,pose.anchor6Y-pose.anchor12Y),Math.hypot(pose.anchor9X-pose.anchor3X,pose.anchor9Y-pose.anchor3Y)));
            double repro=safeReprojectionRms(h,pose)/RADIUS;
            boolean observed=Double.isFinite(pairScore)&&pairScore>=.50;
            RectificationConfidenceService.Validation validation=new RectificationConfidenceService.Validation(repro,axis,Double.NaN,pairScore,observed);
            RectificationConfidenceService.Assessment confidence=RectificationConfidenceService.assess(pose.anchor12X,pose.anchor12Y,pose.anchor3X,pose.anchor3Y,pose.anchor6X,pose.anchor6Y,pose.anchor9X,pose.anchor9Y,validation);
            Mat resultBgr=dst;Mat resultGray=gray;dst=null;gray=null;return new CanonicalGmtDial(resultBgr,resultGray,pose,confidence);
        }catch(Throwable t){LAST_FAILURE.set(stage+" failed: "+t.getClass().getSimpleName()+(t.getMessage()==null?"":": "+t.getMessage()));return null;}
        finally{if(from!=null)from.release();if(to!=null)to.release();if(h!=null)h.release();rgba.release();src.release();if(dst!=null)dst.release();if(gray!=null)gray.release();}
    }

    static String lastFailureReason(){String reason=LAST_FAILURE.get();return reason==null?"unknown warp failure":reason;}

    Point sourcePoint(double canonicalX,double canonicalY){PointF p=PerspectiveMasterRenderer.projectPoint(pose,(canonicalX-CENTER)/RADIUS,(canonicalY-CENTER)/RADIUS);return new Point(p.x,p.y);}
    Bitmap bitmap(){Bitmap out=Bitmap.createBitmap(SIZE,SIZE,Bitmap.Config.ARGB_8888);Mat rgba=new Mat();Imgproc.cvtColor(bgr,rgba,Imgproc.COLOR_BGR2RGBA);Utils.matToBitmap(rgba,out);rgba.release();return out;}

    private static boolean finitePose(PerspectiveMasterRenderer.Pose p){return Float.isFinite(p.anchor12X)&&Float.isFinite(p.anchor12Y)&&Float.isFinite(p.anchor3X)&&Float.isFinite(p.anchor3Y)&&Float.isFinite(p.anchor6X)&&Float.isFinite(p.anchor6Y)&&Float.isFinite(p.anchor9X)&&Float.isFinite(p.anchor9Y);}
    private static double anchorArea(PerspectiveMasterRenderer.Pose p){double[] x={p.anchor12X,p.anchor3X,p.anchor6X,p.anchor9X},y={p.anchor12Y,p.anchor3Y,p.anchor6Y,p.anchor9Y};double s=0;for(int i=0;i<4;i++){int j=(i+1)%4;s+=x[i]*y[j]-x[j]*y[i];}return Math.abs(s)*.5;}

    private static double reprojectionRms(Mat h,PerspectiveMasterRenderer.Pose p){
        Point[] in={new Point(p.anchor12X,p.anchor12Y),new Point(p.anchor3X,p.anchor3Y),new Point(p.anchor6X,p.anchor6Y),new Point(p.anchor9X,p.anchor9Y)};
        Point[] target={new Point(CENTER,CENTER-RADIUS),new Point(CENTER+RADIUS,CENTER),new Point(CENTER,CENTER+RADIUS),new Point(CENTER-RADIUS,CENTER)};
        double h00=h.get(0,0)[0],h01=h.get(0,1)[0],h02=h.get(0,2)[0];
        double h10=h.get(1,0)[0],h11=h.get(1,1)[0],h12=h.get(1,2)[0];
        double h20=h.get(2,0)[0],h21=h.get(2,1)[0],h22=h.get(2,2)[0];
        double ss=0;
        for(int i=0;i<4;i++){
            double den=h20*in[i].x+h21*in[i].y+h22;
            if(Math.abs(den)<1e-12||!Double.isFinite(den))throw new IllegalStateException("homography denominator is invalid");
            double x=(h00*in[i].x+h01*in[i].y+h02)/den;
            double y=(h10*in[i].x+h11*in[i].y+h12)/den;
            if(!Double.isFinite(x)||!Double.isFinite(y))throw new IllegalStateException("homography reprojection is non-finite");
            ss+=(x-target[i].x)*(x-target[i].x)+(y-target[i].y)*(y-target[i].y);
        }
        return Math.sqrt(ss/4.0);
    }

    private static double safeReprojectionRms(Mat h,PerspectiveMasterRenderer.Pose p){try{return reprojectionRms(h,p);}catch(Throwable ignored){return Double.NaN;}}
    private static double safeInnerBoundaryPairScore(Mat gray){try{return innerBoundaryPairScore(gray);}catch(Throwable ignored){return Double.NaN;}}

    /**
     * Independent verification that canonical radius 1.0 is the smaller/inner rehaut boundary.
     * We require a strong edge at 1.0 and a second strong edge just outside it. A stronger nearby
     * edge on the inside reduces confidence because that is the signature of selecting the outer
     * rehaut boundary by mistake.
     */
    private static double innerBoundaryPairScore(Mat gray){
        double selected=radialEdgeStrength(gray,1.000);
        double outer=0,outerF=Double.NaN;
        for(double f=1.015;f<=1.095;f+=.005){double s=radialEdgeStrength(gray,f);if(s>outer){outer=s;outerF=f;}}
        double inner=0;
        for(double f=.915;f<=.985;f+=.005)inner=Math.max(inner,radialEdgeStrength(gray,f));
        if(selected<.028||outer<.025||!Double.isFinite(outerF))return 0;
        double selectedQ=ramp(selected,.028,.105),outerQ=ramp(outer,.025,.095);
        double separationQ=ramp(outerF-1.0,.016,.055)*(1-ramp(outerF-1.0,.080,.105));
        double dominance=outer/(Math.max(.012,inner));
        double directionQ=ramp(dominance,.70,1.20);
        return clamp(.32*selectedQ+.30*outerQ+.20*separationQ+.18*directionQ);
    }

    private static double radialEdgeStrength(Mat gray,double factor){
        double r=RADIUS*factor,sum=0;int n=0;
        for(int deg=0;deg<360;deg+=4){
            // Skip the most intrusive cyclops/date sector, but retain the rest of the ring.
            if(deg>=340||deg<=20)continue;
            double a=Math.toRadians(deg),ca=Math.cos(a),sa=Math.sin(a),best=0;
            for(double off=-3;off<=3;off+=1.5){
                double rr=r+off;
                int xi=(int)Math.round(CENTER+ca*(rr-3.0)),yi=(int)Math.round(CENTER+sa*(rr-3.0));
                int xo=(int)Math.round(CENTER+ca*(rr+3.0)),yo=(int)Math.round(CENTER+sa*(rr+3.0));
                if(xi<0||yi<0||xo<0||yo<0||xi>=gray.cols()||xo>=gray.cols()||yi>=gray.rows()||yo>=gray.rows())continue;
                double[]vi=gray.get(yi,xi),vo=gray.get(yo,xo);if(vi!=null&&vo!=null)best=Math.max(best,Math.abs(vo[0]-vi[0])/255.0);
            }
            sum+=best;n++;
        }
        return n>0?sum/n:0;
    }

    private static double ramp(double v,double lo,double hi){return v<=lo?0:v>=hi?1:(v-lo)/(hi-lo);}
    private static double clamp(double v){return Math.max(0,Math.min(1,v));}
    @Override public void close(){bgr.release();gray.release();}
}
