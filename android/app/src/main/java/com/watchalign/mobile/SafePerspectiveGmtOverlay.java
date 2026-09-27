package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Safe GMT pose/rectification wrapper.
 *
 * The image and fixed master are rendered with ellipse-derived H0 only. Projective
 * refinement remains diagnostic. Automatic roll is now defined by the local 60
 * minute tick, not by the old all-marker brightness estimator, so a crooked 12
 * marker cannot be rotated straight and hands/cyclops cannot bias the frame.
 */
final class SafePerspectiveGmtOverlay {
    static PerspectiveGmtOverlay.Result build(Bitmap input,String modelRef,
                                              PerspectiveGmtOverlay.DialSeed manualSeed,
                                              int overlayColor) {
        if(input==null||!PerspectiveGmtOverlay.supports(modelRef))return null;
        Mat src=new Mat(),bgr=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat(),h0=null,diagnosticH=null;
        try{
            Utils.bitmapToMat(input,src);
            Imgproc.cvtColor(src,bgr,Imgproc.COLOR_RGBA2BGR);
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);

            PerspectiveGmtOverlay.DialSeed detectedSeed=(PerspectiveGmtOverlay.DialSeed)
                    call("seed",new Class[]{Mat.class},src);
            PerspectiveGmtOverlay.DialSeed seed=manualSeed!=null?manualSeed:detectedSeed;
            if(seed==null||!(seed.r>40))return null;

            String rollSource;
            GmtTwelveLandmarkAnalyzer.Result localFrame=null;
            if(manualSeed==null){
                localFrame=GmtTwelveLandmarkAnalyzer.analyse(bgr,seed.x,seed.y,seed.r);
                if(localFrame.valid&&Double.isFinite(localFrame.trackRollClockDeg)&&Math.abs(localFrame.trackRollClockDeg)<=18.0){
                    seed=new PerspectiveGmtOverlay.DialSeed(seed.x,seed.y,seed.r,seed.quality,localFrame.trackRollClockDeg);
                    rollSource=String.format(Locale.US,"local detected 60-minute tick (frame score %.1f)",localFrame.minuteFrameScore);
                }else{
                    // Do not fall back to the old all-marker roll. Leaving image roll
                    // unchanged is safer than inventing an upright dial from biased cues.
                    seed=new PerspectiveGmtOverlay.DialSeed(seed.x,seed.y,seed.r,seed.quality,0.0);
                    rollSource="uncorrected image roll because local 59/60/01 frame was unresolved";
                }
            }else{
                rollSource="manual precision-alignment roll";
            }

            RotatedRect detectedEllipse=detectedSeed==null?null:(RotatedRect)
                    call("findDialEllipse",new Class[]{Mat.class,PerspectiveGmtOverlay.DialSeed.class},edges,detectedSeed);

            RotatedRect ellipse;
            String seedSource;
            boolean perspectiveFallback=false;
            if(manualSeed!=null){
                if(detectedEllipse!=null){
                    double measured=((Number)call("rayEllipseRadius",
                            new Class[]{RotatedRect.class,double.class},detectedEllipse,Math.toRadians(manualSeed.rollDeg-90.0))).doubleValue();
                    double scale=(measured>1.0&&Double.isFinite(measured))?manualSeed.r/measured:1.0;
                    ellipse=new RotatedRect(new Point(manualSeed.x,manualSeed.y),
                            new Size(detectedEllipse.size.width*scale,detectedEllipse.size.height*scale),detectedEllipse.angle);
                    seedSource="2-point dial-edge seed with fitted perspective ellipse";
                }else{
                    ellipse=new RotatedRect(new Point(seed.x,seed.y),new Size(seed.r*2.0,seed.r*2.0),0.0);
                    seedSource="2-point dial-edge seed with circular fallback";
                    perspectiveFallback=true;
                }
            }else if(detectedEllipse!=null){
                ellipse=PerspectiveGmtOverlay.normalizeEllipseToOuterRadius(detectedEllipse,seed);
                seedSource="fitted dial ellipse normalized to detected outer dial radius";
            }else{
                ellipse=new RotatedRect(new Point(seed.x,seed.y),new Size(seed.r*2.0,seed.r*2.0),0.0);
                seedSource="detected dial seed with circular fallback";
                perspectiveFallback=true;
            }

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));
            Point[] card=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,seed.rollDeg);
            h0=(Mat)call("homographyFromUnitSquare",new Class[]{Point[].class},(Object)card);
            if(h0==null||h0.empty())return null;
            double[] h0Values=matrixValues(h0);

            DialProjectiveRefiner.MatResult refinement=DialProjectiveRefiner.refineWithDiagnostics(edges,h0);
            diagnosticH=refinement.homography;

            double reproj=((Number)call("reprojectionError",new Class[]{Mat.class,Point[].class},h0,(Object)card)).doubleValue();
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double confidence=((Number)call("confidence",
                    new Class[]{double.class,double.class,double.class,double.class},seed.quality,reproj,centerErr,axisRatio)).doubleValue();
            if(perspectiveFallback)confidence*=0.65;
            if(manualSeed==null&&(localFrame==null||!localFrame.valid))confidence*=0.80;

            Bitmap overlay=(Bitmap)call("renderNative",
                    new Class[]{Bitmap.class,Mat.class,String.class,int.class},input,h0,modelRef,overlayColor);
            Bitmap rectified=(Bitmap)call("rectify",
                    new Class[]{Mat.class,Mat.class,Bitmap.class},src,h0,input);

            String master=Gmt126710BlnrMaster.supports(modelRef)?Gmt126710BlnrMaster.ID:"canonical GMT fallback";
            String report=String.format(Locale.US,
                    "\n\nVISUAL QC MASTER\n"+
                    "Pose source: %s. Dial edge establishes scale/perspective; 12-marker geometry is not fitted away.\n"+
                    "Roll source: %s. Applied roll %+.2f°.\n"+
                    "Inspection geometry: %s. Red outlines are the fixed master; white outlines are lume references.\n"+
                    "Ellipse axes: %.1f × %.1f px; apparent tilt %.1f°.\n"+
                    "Dial-centre agreement: %.2f%% of dial radius. H0 pose residual: %.2f px. Confidence: %.0f%%.\n"+
                    "SAFE RECTIFICATION: ellipse-derived H0 only. Projective h31/h32 refinement is DIAGNOSTIC ONLY and is not applied to the watch image or master overlay.\n"+
                    "H0 projective terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Diagnostic projective candidate: %s; h31=%+.6f, h32=%+.6f.\n"+
                    "Diagnostic fit evidence: %.4f before, %.4f after. Holdout: %.4f before, %.4f after.\n"+
                    "Applied homography: H0 (projective candidate ignored regardless of diagnostic acceptance).\n",
                    seedSource,rollSource,seed.rollDeg,master,major,minor,tiltDeg,centerErr*100.0,reproj,confidence*100.0,
                    normalizedTerm(h0Values,6),normalizedTerm(h0Values,7),
                    refinement.diagnostics.accepted?"ACCEPTED FOR RESEARCH":"REJECTED",
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,0),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,1),
                    refinement.diagnostics.fitBefore,refinement.diagnostics.evaluatedFitAfter,
                    refinement.diagnostics.holdoutBefore,refinement.diagnostics.evaluatedHoldoutAfter);
            return new PerspectiveGmtOverlay.Result(overlay,rectified,report,confidence);
        }catch(Throwable ignored){return null;}
        finally{
            if(diagnosticH!=null)diagnosticH.release();
            if(h0!=null)h0.release();
            src.release();bgr.release();gray.release();blur.release();edges.release();
        }
    }

    private static Object call(String name,Class<?>[] types,Object...args)throws Exception{
        Method m=PerspectiveGmtOverlay.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static double[] matrixValues(Mat h){double[]v=new double[9];h.get(0,0,v);return v;}
    private static double normalizedTerm(double[]h,int i){return h[i]/h[8];}
    private static double normalizedTerm(double[][]h,int r,int c){return h[r][c]/h[2][2];}
    private SafePerspectiveGmtOverlay(){}
}
