package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Wrapper around the normal minute-track-first overlay.
 *
 * An already accepted primary pose is returned untouched. Only after the full
 * primary pipeline rejects its pose do we run the disjoint-tick rescue selector.
 * This makes the rescue path monotonic with respect to the existing behaviour:
 * it can recover rejected photos, but it cannot replace a currently accepted
 * automatic pose.
 */
final class MinuteTrackRescueOverlay {
    private static final double FINAL_TOP_PHASE_LIMIT_DEG=3.25;

    static PerspectiveGmtOverlay.Result build(Bitmap input,String modelRef,
                                              PerspectiveGmtOverlay.DialSeed manualSeed,
                                              int overlayColor){
        PerspectiveGmtOverlay.Result primary=
                MinuteTrackFirstOverlay.build(input,modelRef,manualSeed,overlayColor);
        if(manualSeed!=null||primary==null||primary.automaticAccepted)return primary;

        // Reached whenever the primary path rejects, including when its own geometry passed but
        // was vetoed by the independent identity gate for a suspicious centre displacement.
        PerspectiveGmtOverlay.Result rescued=buildRescue(input,modelRef,overlayColor);
        boolean rescueReplacedPrimary=rescued!=null&&rescued.automaticAccepted;
        String annotation=String.format(Locale.US,
                "\n\nRESCUE SELECTOR\nRescue attempted: YES. Rescue replaced primary: %s.\n",
                rescueReplacedPrimary?"YES":"NO");
        if(rescueReplacedPrimary){
            return new PerspectiveGmtOverlay.Result(rescued.nativeOverlay,rescued.rectified,
                    annotation+rescued.report,rescued.confidence,true);
        }
        return new PerspectiveGmtOverlay.Result(primary.nativeOverlay,primary.rectified,
                annotation+primary.report,primary.confidence,false);
    }

    private static PerspectiveGmtOverlay.Result buildRescue(Bitmap input,String modelRef,int overlayColor){
        if(input==null||!PerspectiveGmtOverlay.supports(modelRef))return null;
        Mat src=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(input,src);
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);

            PerspectiveGmtOverlay.DialSeed seed=legacySeed(src);
            if(seed==null||!(seed.r>40.0))return null;

            MinuteTrackAcquisitionRescue.Result rescue=
                    MinuteTrackAcquisitionRescue.find(edges,gray,seed.x,seed.y,seed.r);
            if(rescue==null||rescue.candidate==null)return null;
            MinuteTrackAcquisitionRescue.Candidate c=rescue.candidate;
            if(!MinuteTrackAcquisitionRescue.shouldUseRescue(false,true,
                    c.validationAccepted,c.semanticAccepted))return null;

            RotatedRect ellipse=c.ellipse;
            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));
            double dialRadiusPx=(major+minor)/4.0;

            Point[] baseCard=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,c.rollDeg);
            Mat h0=homographyFromUnitSquare(baseCard);
            if(h0==null||h0.empty())return null;
            double[] h0Values=matrixValues(h0);

            double projectiveLimit=Math.max(0.015,Math.min(0.32,
                    0.45*Math.sin(Math.toRadians(tiltDeg))));
            DialProjectiveRefiner.MatResult refinement=
                    DialProjectiveRefiner.refineWithDiagnostics(edges,h0,projectiveLimit);
            h0.release();

            MinuteTrackPoseValidator.RotationResult fine=
                    MinuteTrackPoseValidator.fineTuneRotation(edges,refinement.homography);
            refinement.homography.release();
            Mat H=fine.homography;

            MinuteTrackPoseValidator.ValidationResult validation=
                    MinuteTrackPoseValidator.validate(edges,H,dialRadiusPx);
            double finalTopError=topPhaseErrorDeg(H);
            boolean finalTopAccepted=Double.isFinite(finalTopError)
                    &&finalTopError<=FINAL_TOP_PHASE_LIMIT_DEG
                    &&canonicalTwelveIsAboveCentre(H);
            boolean automaticAccepted=finalTopAccepted&&validation.accepted
                    &&c.validationAccepted&&c.semanticAccepted;
            if(!automaticAccepted){H.release();return null;}

            double reproj=reprojectionError(H,baseCard);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)
                    /Math.max(1.0,dialRadiusPx);
            double confidence=confidence(seed.quality,reproj,centerErr,axisRatio,
                    c.fitMedianPx,validation,true);

            Bitmap overlay=renderNative(input,H,modelRef,overlayColor);
            Bitmap rectified=rectify(src,H,input);
            String master=Gmt126710BlnrMaster.supports(modelRef)?
                    Gmt126710BlnrMaster.ID:"canonical GMT fallback";
            String report=String.format(Locale.US,
                    "\n\nVISUAL QC MASTER\n"+
                    "Pose source: MINUTE TRACK RESCUE. The complete primary automatic path rejected first; an accepted primary pose is never replaced by this selector.\n"+
                    "Inspection geometry: %s. Applied markers are used only as a rescue sanity gate and never fit or move the pose.\n"+
                    "Rescue search: %d concentric candidates; top %d refined; selected shape %d (coarse order %d).\n"+
                    "Disjoint tick fit: %.2f px. Selection group median %.2f px, p90 %.2f px; half-pitch periodic contrast %+.2f px; paired discrimination %.0f%%.\n"+
                    "Pre-projective held-out ticks: ACCEPTED; median %.2f px (limit %.2f), p90 %.2f px (limit %.2f), inliers %.0f%% at %.2f px.\n"+
                    "12/6/9 rescue sanity gate: PASSED; dial interior median %.1f. This gate validates candidate identity only.\n"+
                    "Selected dial ellipse: %.1f × %.1f px; apparent tilt %.1f°; centre moved %.2f%% of dial radius from the legacy search seed.\n"+
                    "Final rotation correction: %+.2f°; final 12-axis error %.2f° (ACCEPTED).\n"+
                    "Independent final minute-track validation: ACCEPTED; %d held-out ticks; median %.2f px (limit %.2f), p90 %.2f px (limit %.2f), inliers %.0f%% at %.2f px.\n"+
                    "Automatic master: ACCEPTED by rescue path. Safe to show automatically.\n"+
                    "Pose residual: %.2f px. Confidence: %.0f%%.\n"+
                    "Projective refinement: %s. H0 h31=%+.6f, h32=%+.6f; candidate h31=%+.6f, h32=%+.6f.\n"+
                    "Fit evidence: %.4f before, %.4f after. Holdout evidence: %.4f before, %.4f after. H0 fallback used: %s.\n",
                    master,rescue.shapeCandidates,rescue.refinedCandidates,c.shapeIndex,c.coarseOrder,
                    c.fitMedianPx,c.selectMedianPx,c.selectP90Px,c.periodicContrastPx,c.pairedFraction*100.0,
                    c.validationMedianPx,c.validationMedianLimitPx,c.validationP90Px,c.validationP90LimitPx,
                    c.validationInlierFraction*100.0,c.validationInlierLimitPx,c.semanticDialMedian,
                    major,minor,tiltDeg,centerErr*100.0,fine.deltaDeg,finalTopError,
                    validation.holdoutTicks,validation.medianPx,validation.medianLimitPx,
                    validation.p90Px,validation.p90LimitPx,validation.inlierFraction*100.0,
                    validation.inlierLimitPx,reproj,confidence*100.0,
                    refinement.diagnostics.accepted?"ACCEPTED":"REJECTED",
                    normalizedTerm(h0Values,6),normalizedTerm(h0Values,7),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,0),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,1),
                    refinement.diagnostics.fitBefore,refinement.diagnostics.evaluatedFitAfter,
                    refinement.diagnostics.holdoutBefore,refinement.diagnostics.evaluatedHoldoutAfter,
                    refinement.diagnostics.accepted?"NO":"YES");

            H.release();
            return new PerspectiveGmtOverlay.Result(overlay,rectified,report,confidence,true);
        }catch(Throwable ignored){
            return null;
        }finally{
            src.release();gray.release();blur.release();edges.release();
        }
    }

    private static PerspectiveGmtOverlay.DialSeed legacySeed(Mat rgba)throws Exception{
        Method m=PerspectiveGmtOverlay.class.getDeclaredMethod("seed",Mat.class);
        m.setAccessible(true);
        return (PerspectiveGmtOverlay.DialSeed)m.invoke(null,rgba);
    }

    private static Bitmap renderNative(Bitmap input,Mat H,String modelRef,int overlayColor)throws Exception{
        Method m=MinuteTrackFirstOverlay.class.getDeclaredMethod(
                "renderNative",Bitmap.class,Mat.class,String.class,int.class);
        m.setAccessible(true);
        return (Bitmap)m.invoke(null,input,H,modelRef,overlayColor);
    }

    private static Bitmap rectify(Mat src,Mat H,Bitmap input)throws Exception{
        Method m=MinuteTrackFirstOverlay.class.getDeclaredMethod("rectify",Mat.class,Mat.class,Bitmap.class);
        m.setAccessible(true);
        return (Bitmap)m.invoke(null,src,H,input);
    }

    private static Mat homographyFromUnitSquare(Point[] dst){
        MatOfPoint2f srcPts=new MatOfPoint2f(new Point(0,-1),new Point(1,0),
                new Point(0,1),new Point(-1,0));
        MatOfPoint2f dstPts=new MatOfPoint2f(dst);
        try{return Calib3d.findHomography(srcPts,dstPts,0);}
        finally{srcPts.release();dstPts.release();}
    }

    private static double topPhaseErrorDeg(Mat H){
        Point centre=project(H,0.0,0.0),twelve=project(H,0.0,-1.0);
        double dx=twelve.x-centre.x,dy=twelve.y-centre.y,len=Math.hypot(dx,dy);
        if(len<1e-9)return Double.POSITIVE_INFINITY;
        double dot=Math.max(-1.0,Math.min(1.0,(-dy)/len));
        return Math.toDegrees(Math.acos(dot));
    }

    private static boolean canonicalTwelveIsAboveCentre(Mat H){
        Point centre=project(H,0.0,0.0),twelve=project(H,0.0,-1.0);
        return twelve.y<centre.y;
    }

    private static double reprojectionError(Mat H,Point[] expected){
        Point[] canonical={new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0)};
        double sum=0.0;
        for(int i=0;i<4;i++){
            Point p=project(H,canonical[i].x,canonical[i].y);
            sum+=Math.hypot(p.x-expected[i].x,p.y-expected[i].y);
        }
        return sum/4.0;
    }

    private static double confidence(double q,double reproj,double centerErr,double axisRatio,
                                     double fit,MinuteTrackPoseValidator.ValidationResult validation,
                                     boolean accepted){
        double a=Math.max(0,Math.min(1,(q-0.45)/0.45));
        double b=Math.max(0,1-reproj/4.0);
        double c=Math.max(0,1-centerErr/0.22);
        double d=Math.max(0,Math.min(1,(axisRatio-0.65)/0.30));
        double e=Math.max(0,1-fit/5.0);
        double f=validation==null?0.0:Math.max(0,1-validation.medianPx/
                Math.max(1.0,validation.medianLimitPx*1.6));
        double value=0.18*a+0.18*b+0.14*c+0.12*d+0.18*e+0.20*f;
        if(!accepted)value=Math.min(value,0.35);
        return Math.max(0,Math.min(1,value));
    }

    private static double[] matrixValues(Mat h){double[] v=new double[9];h.get(0,0,v);return v;}
    private static double normalizedTerm(double[] h,int i){return h[i]/h[8];}
    private static double normalizedTerm(double[][] h,int r,int c){return h[r][c]/h[2][2];}
    private static Point project(Mat H,double x,double y){
        double[] h=new double[9];H.get(0,0,h);double w=h[6]*x+h[7]*y+h[8];
        if(Math.abs(w)<1e-9)w=1e-9;
        return new Point((h[0]*x+h[1]*y+h[2])/w,(h[3]*x+h[4]*y+h[5])/w);
    }

    private MinuteTrackRescueOverlay(){}
}
