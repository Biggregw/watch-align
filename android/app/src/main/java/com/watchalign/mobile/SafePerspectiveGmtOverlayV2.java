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
 * Safe GMT master/rectification using a GMT-specific wide-scale dial seed.
 * This avoids the legacy 28%-of-image radius ceiling which can lock onto the
 * hour-marker ring on close crops and scatter the fixed master across the watch.
 */
final class SafePerspectiveGmtOverlayV2 {
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

            GmtDialSeedAnalyzer.Result wide=GmtDialSeedAnalyzer.analyse(bgr);
            PerspectiveGmtOverlay.DialSeed autoSeed=wide.valid?
                    new PerspectiveGmtOverlay.DialSeed(wide.x,wide.y,wide.r,wide.quality,0.0):null;
            PerspectiveGmtOverlay.DialSeed seed=manualSeed!=null?manualSeed:autoSeed;
            if(seed==null||!(seed.r>40))return null;

            // The automatic seed centre is a raw HoughCircles centre, which usually belongs to the
            // strongest concentric circle (case/bezel/crystal). Those sit above the dial, so parallax
            // displaces their centre from the dial's. Re-measure centre and ellipse from the physical
            // dial edge before anything (roll, pose, master) is built on it.
            double houghX=seed.x,houghY=seed.y,houghR=seed.r;
            DialEdgeEllipseFit.Fit edgeFit=null;
            if(manualSeed==null){
                edgeFit=fitDialEdge(blur,seed);
                if(edgeFit!=null)seed=new PerspectiveGmtOverlay.DialSeed(edgeFit.cx,edgeFit.cy,edgeFit.meanRadius(),seed.quality,0.0);
            }

            String rollSource;
            GmtTwelveLandmarkAnalyzer.Result localFrame=null;
            boolean rollRecovered=false;
            if(manualSeed==null){
                localFrame=GmtTwelveLandmarkAnalyzer.analyse(bgr,seed.x,seed.y,seed.r);
                if(!localFrame.valid){
                    GmtTwelveLandmarkAnalyzer.Result rec=GmtTwelveRecoveryAnalyzer.analyse(bgr,seed.x,seed.y,seed.r,localFrame.reason);
                    if(rec.valid){localFrame=rec;rollRecovered=true;}
                }
                boolean stableLocalRoll=localFrame.valid&&localFrame.detectorStable
                        &&Double.isFinite(localFrame.minuteFrameScore)&&localFrame.minuteFrameScore>=10.0
                        &&Double.isFinite(localFrame.trackRollClockDeg)&&Math.abs(localFrame.trackRollClockDeg)<=18.0;
                if(stableLocalRoll){
                    seed=new PerspectiveGmtOverlay.DialSeed(seed.x,seed.y,seed.r,seed.quality,localFrame.trackRollClockDeg);
                    rollSource=String.format(Locale.US,"trusted local detected 60-minute tick%s (frame score %.1f)",rollRecovered?" recovered":"",localFrame.minuteFrameScore);
                }else{
                    seed=new PerspectiveGmtOverlay.DialSeed(seed.x,seed.y,seed.r,seed.quality,0.0);
                    rollSource=localFrame!=null&&localFrame.valid
                            ?String.format(Locale.US,"uncorrected image roll because local 59/60/01 frame is low confidence (score %.1f)",localFrame.minuteFrameScore)
                            :"uncorrected image roll because local 59/60/01 frame was unresolved";
                }
            }else rollSource="manual precision-alignment roll";

            RotatedRect ellipse;
            String seedSource;
            boolean perspectiveFallback=false;
            double centerErr;
            String centreNote;
            RotatedRect detectedEllipse=edgeFit!=null?null:(RotatedRect)call("findDialEllipse",
                    new Class[]{Mat.class,PerspectiveGmtOverlay.DialSeed.class},edges,seed);
            if(edgeFit!=null){
                ellipse=new RotatedRect(new Point(edgeFit.cx,edgeFit.cy),new Size(2.0*edgeFit.axisA,2.0*edgeFit.axisB),edgeFit.angleDeg);
                seedSource="wide-scale GMT dial seed, centre and ellipse re-fitted to the physical dial edge";
                double shift=Math.hypot(edgeFit.cx-houghX,edgeFit.cy-houghY);
                centerErr=0.0;
                centreNote=String.format(Locale.US,"Dial centre: re-fitted to dial edge (%d/%d edge points, RMS %.2f px). Moved %.1f px (%.2f%% of radius) from the Hough proposal %.1f, %.1f; radius %.1f → %.1f px.",
                        edgeFit.points,edgeFit.rays,edgeFit.rmsPx,shift,100.0*shift/Math.max(1.0,houghR),houghX,houghY,houghR,edgeFit.meanRadius());
            }else if(detectedEllipse!=null){
                // Measured before normalisation re-centres the ellipse on the seed; the old
                // post-normalisation value was always 0.00% and could never flag a bad centre.
                centerErr=Math.hypot(detectedEllipse.center.x-seed.x,detectedEllipse.center.y-seed.y)/Math.max(1.0,seed.r);
                ellipse=(RotatedRect)call("normalizeEllipseToOuterRadius",
                        new Class[]{RotatedRect.class,PerspectiveGmtOverlay.DialSeed.class},detectedEllipse,seed);
                seedSource=manualSeed!=null?"manual dial-edge seed with fitted perspective ellipse":"wide-scale GMT dial seed with fitted perspective ellipse";
                centreNote=manualSeed!=null?"Dial centre: from manual 12/6 dial-edge taps.":
                        "Dial centre: UNREFINED Hough proposal (dial-edge re-fit unavailable for this photo).";
            }else{
                ellipse=new RotatedRect(new Point(seed.x,seed.y),new Size(seed.r*2.0,seed.r*2.0),0.0);
                seedSource=manualSeed!=null?"manual dial-edge seed with circular fallback":"wide-scale GMT dial seed with circular fallback";
                perspectiveFallback=true;
                centerErr=0.0;
                centreNote=manualSeed!=null?"Dial centre: from manual 12/6 dial-edge taps.":
                        "Dial centre: UNREFINED Hough proposal (dial-edge re-fit unavailable for this photo).";
            }

            double major=Math.max(ellipse.size.width,ellipse.size.height),minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));

            // A fixed 2D master should not be shown when perspective is so severe
            // that an ellipse-only H0 cannot preserve the internal dial relationships.
            // The real dealer test that triggered this gate measured about 27 degrees
            // and produced an obviously false overlay. Human/rehaut QC can still run.
            if(manualSeed==null&&!perspectiveFallback&&tiltDeg>=15.0){
                String report=String.format(Locale.US,
                        "\n\nVISUAL QC MASTER\nWithheld: fitted dial ellipse implies %.1f° apparent tilt, beyond the safe range for the fixed 2D master. %s. Use a squarer QC photo for the master overlay; human rehaut/perspective analysis can still run.\n",
                        tiltDeg,rollSource);
                return new PerspectiveGmtOverlay.Result(null,null,report,0.0);
            }

            Point[] card=(Point[])call("ellipseCardinalPoints",new Class[]{RotatedRect.class,double.class},ellipse,seed.rollDeg);
            h0=(Mat)call("homographyFromUnitSquare",new Class[]{Point[].class},(Object)card);
            if(h0==null||h0.empty())return null;
            double[] h0Values=matrixValues(h0);

            DialProjectiveRefiner.MatResult refinement=DialProjectiveRefiner.refineWithDiagnostics(edges,h0);
            diagnosticH=refinement.homography;
            double reproj=((Number)call("reprojectionError",new Class[]{Mat.class,Point[].class},h0,(Object)card)).doubleValue();
            double confidence=((Number)call("confidence",new Class[]{double.class,double.class,double.class,double.class},seed.quality,reproj,centerErr,axisRatio)).doubleValue();
            if(perspectiveFallback)confidence*=0.70;
            if(manualSeed==null&&(localFrame==null||!localFrame.valid||!localFrame.detectorStable))confidence*=0.82;

            if(manualSeed==null&&confidence<0.52)return new PerspectiveGmtOverlay.Result(null,null,
                    String.format(Locale.US,"\n\nVISUAL QC MASTER\nWithheld: automatic dial pose confidence %.0f%% is too low for a trustworthy fixed-master overlay. The human 12-marker analysis can still run independently.\n",confidence*100.0),confidence);

            Bitmap overlay=(Bitmap)call("renderNative",new Class[]{Bitmap.class,Mat.class,String.class,int.class},input,h0,modelRef,overlayColor);
            Bitmap rectified=(Bitmap)call("rectify",new Class[]{Mat.class,Mat.class,Bitmap.class},src,h0,input);
            String master=Gmt126710BlnrMaster.supports(modelRef)?Gmt126710BlnrMaster.ID:"canonical GMT fallback";
            String report=String.format(Locale.US,
                    "\n\nVISUAL QC MASTER\n"+
                    "Pose source: %s. Dial edge establishes scale/perspective; 12-marker geometry is not fitted away.\n"+
                    "Roll source: %s. Applied roll %+.2f°.\n"+
                    "Inspection geometry: %s. Red outlines are the fixed master; white outlines are lume references.\n"+
                    "Dial seed: centre %.1f, %.1f; radius %.1f px; seed quality %.2f.\n"+
                    "%s\n"+
                    "Ellipse axes: %.1f × %.1f px; apparent tilt %.1f°.\n"+
                    "Dial-centre agreement: %.2f%% of dial radius. H0 pose residual: %.2f px. Confidence: %.0f%%.\n"+
                    "SAFE RECTIFICATION: ellipse-derived H0 only. Projective h31/h32 refinement is DIAGNOSTIC ONLY and is not applied to the watch image or master overlay.\n"+
                    "H0 projective terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Diagnostic projective candidate: %s; h31=%+.6f, h32=%+.6f.\n"+
                    "Diagnostic fit evidence: %.4f before, %.4f after. Holdout: %.4f before, %.4f after.\n"+
                    "Applied homography: H0 (projective candidate ignored regardless of diagnostic acceptance).\n",
                    seedSource,rollSource,seed.rollDeg,master,seed.x,seed.y,seed.r,seed.quality,centreNote,
                    major,minor,tiltDeg,centerErr*100.0,reproj,confidence*100.0,
                    normalizedTerm(h0Values,6),normalizedTerm(h0Values,7),
                    refinement.diagnostics.accepted?"ACCEPTED FOR RESEARCH":"REJECTED",
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,0),normalizedTerm(refinement.diagnostics.evaluatedHomography,2,1),
                    refinement.diagnostics.fitBefore,refinement.diagnostics.evaluatedFitAfter,
                    refinement.diagnostics.holdoutBefore,refinement.diagnostics.evaluatedHoldoutAfter);
            return new PerspectiveGmtOverlay.Result(overlay,rectified,report,confidence);
        }catch(Throwable ignored){return null;}
        finally{
            if(diagnosticH!=null)diagnosticH.release();if(h0!=null)h0.release();
            src.release();bgr.release();gray.release();blur.release();edges.release();
        }
    }

    private static DialEdgeEllipseFit.Fit fitDialEdge(Mat blurredGray,PerspectiveGmtOverlay.DialSeed seed){
        try{
            final int w=blurredGray.cols(),h=blurredGray.rows();
            if(w<8||h<8||blurredGray.channels()!=1)return null;
            final byte[] px=new byte[w*h];
            blurredGray.get(0,0,px);
            DialEdgeEllipseFit.Intensity img=(x,y)->{
                int x0=(int)Math.floor(x),y0=(int)Math.floor(y);
                double fx=x-x0,fy=y-y0;
                int i=y0*w+x0;
                double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+w]&0xff,d=px[i+w+1]&0xff;
                return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;
            };
            return DialEdgeEllipseFit.fit(img,w,h,seed.x,seed.y,seed.r);
        }catch(Throwable t){return null;}
    }

    private static Object call(String name,Class<?>[] types,Object...args)throws Exception{
        Method m=PerspectiveGmtOverlay.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(null,args);
    }
    private static double[] matrixValues(Mat h){double[]v=new double[9];h.get(0,0,v);return v;}
    private static double normalizedTerm(double[]h,int i){return h[i]/h[8];}
    private static double normalizedTerm(double[][]h,int r,int c){return h[r][c]/h[2][2];}
    private SafePerspectiveGmtOverlayV2(){}
}
