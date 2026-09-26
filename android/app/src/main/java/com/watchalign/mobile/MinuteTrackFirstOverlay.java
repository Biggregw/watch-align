package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Automatic GMT visual-master pipeline that acquires the minute track first.
 * The legacy detector is used only for an approximate centre/search scale.
 * Manual two-point alignment still delegates to PerspectiveGmtOverlay.
 */
final class MinuteTrackFirstOverlay {
    private static final double FINAL_TOP_PHASE_LIMIT_DEG=3.25;

    static PerspectiveGmtOverlay.Result build(Bitmap input,String modelRef,
                                              PerspectiveGmtOverlay.DialSeed manualSeed,
                                              int overlayColor){
        if(manualSeed!=null)return PerspectiveGmtOverlay.build(input,modelRef,manualSeed,overlayColor);
        if(input==null||!PerspectiveGmtOverlay.supports(modelRef))return null;

        Mat src=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(input,src);
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);

            PerspectiveGmtOverlay.DialSeed seed=legacySeed(src);
            if(seed==null||!(seed.r>40.0))return rejectedLegacyFallback(input,modelRef,overlayColor,
                    "Minute-track-first acquisition unavailable: no stable approximate dial centre was found.");

            MinuteTrackDialFinder.Result acquisition=MinuteTrackDialFinder.find(edges,seed.x,seed.y,seed.r);
            if(acquisition==null||acquisition.dialEllipse==null){
                return rejectedLegacyFallback(input,modelRef,overlayColor,
                        "Minute-track-first acquisition unavailable: minor ticks could not establish a dial ellipse.");
            }

            RotatedRect ellipse=acquisition.dialEllipse;
            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));
            double dialRadiusPx=(major+minor)/4.0;

            Point[] baseCard=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,acquisition.rollDeg);
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
            double solvedRoll=acquisition.rollDeg+fine.deltaDeg;

            MinuteTrackPoseValidator.ValidationResult validation=
                    MinuteTrackPoseValidator.validate(edges,H,dialRadiusPx);
            double finalTopError=topPhaseErrorDeg(H);
            boolean finalTopAccepted=Double.isFinite(finalTopError)&&finalTopError<=FINAL_TOP_PHASE_LIMIT_DEG
                    &&canonicalTwelveIsAboveCentre(H);

            // The minute track owns pose acceptance. The outward-ring detector is advisory
            // because crystal/rehaut edges can be stronger than the physical dial boundary.
            boolean automaticAccepted=automaticAcceptance(
                    acquisition.topPhaseAccepted,finalTopAccepted,validation.accepted);

            Point[] expectedCard=PerspectiveGmtOverlay.ellipseCardinalPoints(ellipse,solvedRoll);
            double reproj=reprojectionError(H,expectedCard);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)
                    /Math.max(1.0,dialRadiusPx);
            double confidence=confidence(seed.quality,reproj,centerErr,axisRatio,
                    acquisition.fitMedianPx,validation,automaticAccepted);

            Bitmap overlay=renderNative(input,H,modelRef,overlayColor);
            Bitmap rectified=rectify(src,H,input);
            String master=Gmt126710BlnrMaster.supports(modelRef)?
                    Gmt126710BlnrMaster.ID:"canonical GMT fallback";
            String report=String.format(Locale.US,
                    "\n\nVISUAL QC MASTER\n"+
                    "Pose source: MINUTE TRACK FIRST. The legacy detector supplies only an approximate centre/search scale; it cannot set final geometry.\n"+
                    "Inspection geometry: %s. Red outlines are the fixed master; white outlines are lume references.\n"+
                    "Minute-track acquisition: %d concentric ellipse candidates evaluated; minor-tick fit median %.2f px; anchored roll %+.2f°. Minute track sets centre and scale.\n"+
                    "Top-phase anchor: %s; canonical 12 axis %.2f° from image-up. QC photos are required to be upright with the 12 minute-track tick nearest the top.\n"+
                    "Next outward ring: %s at canonical radius %.4f (expected 1.0000); boundary median %.2f px, p90 %.2f px. ADVISORY ONLY: it does not resize or veto the automatic master. Track/dial master ratio %.4f.\n"+
                    "Selected dial ellipse: %.1f × %.1f px; apparent tilt %.1f°; centre moved %.2f%% of dial radius from the legacy seed.\n"+
                    "Final rotation correction: %+.2f° (bounded to ±%.2f°); final 12-axis error %.2f° (%s).\n"+
                    "Independent minute-track validation: %s; %d held-out ticks; median %.2f px (limit %.2f), p90 %.2f px (limit %.2f), inliers %.0f%% at %.2f px.\n"+
                    "Automatic decision gates: initial top-phase %s; final 12-axis %s; held-out minute track %s; outward ring advisory %s.\n"+
                    "Automatic master: %s. %s\n"+
                    "Pose residual: %.2f px. Confidence: %.0f%%.\n"+
                    "Projective refinement: %s.\n"+
                    "H0 projective terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Refined candidate terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Fit evidence: %.4f before, %.4f after. Holdout evidence: %.4f before, %.4f after.\n"+
                    "H0 fallback used: %s.\n"+
                    "If automatic validation rejects the pose, automated geometric QC is suppressed and Native Template remains available for manual move/resize/rotate.\n",
                    master,acquisition.candidateCount,acquisition.fitMedianPx,acquisition.rollDeg,
                    acquisition.topPhaseAccepted?"ACCEPTED":"REJECTED",acquisition.topPhaseErrorDeg,
                    acquisition.boundaryConfirmed?"CONFIRMED":"NOT CONFIRMED",
                    acquisition.outerBoundaryRadius,acquisition.outerBoundaryMedianPx,
                    acquisition.outerBoundaryP90Px,Gmt126710BlnrMaster.MINUTE_TRACK_R,
                    major,minor,tiltDeg,centerErr*100.0,fine.deltaDeg,
                    MinuteTrackPoseValidator.fineRotationLimitDeg(),finalTopError,
                    finalTopAccepted?"ACCEPTED":"REJECTED",
                    validation.accepted?"ACCEPTED":"REJECTED",validation.holdoutTicks,
                    validation.medianPx,validation.medianLimitPx,
                    validation.p90Px,validation.p90LimitPx,
                    validation.inlierFraction*100.0,validation.inlierLimitPx,
                    acquisition.topPhaseAccepted?"PASS":"FAIL",
                    finalTopAccepted?"PASS":"FAIL",
                    validation.accepted?"PASS":"FAIL",
                    acquisition.boundaryConfirmed?"CONFIRMED":"NOT CONFIRMED",
                    automaticAccepted?"ACCEPTED":"REJECTED",
                    automaticAccepted?"Safe to show automatically.":
                            "Hidden from the main QC view; use Native Template for manual alignment.",
                    reproj,confidence*100.0,
                    refinement.diagnostics.accepted?"ACCEPTED":"REJECTED",
                    normalizedTerm(h0Values,6),normalizedTerm(h0Values,7),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,0),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,1),
                    refinement.diagnostics.fitBefore,refinement.diagnostics.evaluatedFitAfter,
                    refinement.diagnostics.holdoutBefore,refinement.diagnostics.evaluatedHoldoutAfter,
                    refinement.diagnostics.accepted?"NO":"YES");

            H.release();
            return new PerspectiveGmtOverlay.Result(overlay,rectified,report,confidence,automaticAccepted);
        }catch(Throwable t){
            return rejectedLegacyFallback(input,modelRef,overlayColor,
                    "Minute-track-first acquisition failed safely: "+t.getClass().getSimpleName()+".");
        }finally{
            src.release();gray.release();blur.release();edges.release();
        }
    }

    static boolean automaticAcceptance(boolean acquisitionTopPhaseAccepted,
                                       boolean finalTopAccepted,
                                       boolean independentMinuteTrackAccepted){
        return acquisitionTopPhaseAccepted&&finalTopAccepted&&independentMinuteTrackAccepted;
    }

    private static PerspectiveGmtOverlay.Result rejectedLegacyFallback(Bitmap input,String modelRef,
                                                                       int overlayColor,String reason){
        try{
            PerspectiveGmtOverlay.Result old=PerspectiveGmtOverlay.build(input,modelRef,null,overlayColor);
            if(old==null)return null;
            String report="\n\nMINUTE-TRACK-FIRST SAFETY FALLBACK\n"+reason+
                    "\nThe legacy automatic pose is available only as a manual starting template and is NOT trusted automatically.\n"+
                    old.report;
            return new PerspectiveGmtOverlay.Result(old.nativeOverlay,old.rectified,report,
                    Math.min(0.30,old.confidence),false);
        }catch(Throwable ignored){return null;}
    }

    private static PerspectiveGmtOverlay.DialSeed legacySeed(Mat rgba)throws Exception{
        Method m=PerspectiveGmtOverlay.class.getDeclaredMethod("seed",Mat.class);
        m.setAccessible(true);
        return (PerspectiveGmtOverlay.DialSeed)m.invoke(null,rgba);
    }

    private static Mat homographyFromUnitSquare(Point[] dst){
        MatOfPoint2f srcPts=new MatOfPoint2f(new Point(0,-1),new Point(1,0),
                new Point(0,1),new Point(-1,0));
        MatOfPoint2f dstPts=new MatOfPoint2f(dst);
        try{return Calib3d.findHomography(srcPts,dstPts,0);}
        finally{srcPts.release();dstPts.release();}
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

    static double topPhaseErrorDeg(Mat H){
        Point centre=project(H,0.0,0.0),twelve=project(H,0.0,-1.0);
        double dx=twelve.x-centre.x,dy=twelve.y-centre.y,len=Math.hypot(dx,dy);
        if(len<1e-9)return Double.POSITIVE_INFINITY;
        double dot=Math.max(-1.0,Math.min(1.0,(-dy)/len));
        return Math.toDegrees(Math.acos(dot));
    }

    static boolean canonicalTwelveIsAboveCentre(Mat H){
        Point centre=project(H,0.0,0.0),twelve=project(H,0.0,-1.0);
        return twelve.y<centre.y;
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

    private static Bitmap renderNative(Bitmap source,Mat H,String modelRef,int overlayColor){
        Bitmap out=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);
        float scale=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        Paint outer=paint(overlayColor,1.25f*scale,245);
        Paint inner=paint(Color.WHITE,0.8f*scale,150);
        Paint guide=paint(overlayColor,0.75f*scale,65);
        if(Gmt126710BlnrMaster.supports(modelRef)){
            drawProjectedCircle(c,H,Gmt126710BlnrMaster.DIAL_EDGE_R,guide);
            drawProjectedCircle(c,H,Gmt126710BlnrMaster.MINUTE_TRACK_R,guide);
            for(int h:new int[]{1,2,4,5,7,8,10,11}){
                double a=Gmt126710BlnrMaster.angleForHour(h);
                drawCircleTarget(c,H,Gmt126710BlnrMaster.ROUND_CENTER_R,
                        Gmt126710BlnrMaster.ROUND_OUTER_R,a,outer);
                drawCircleTarget(c,H,Gmt126710BlnrMaster.ROUND_CENTER_R,
                        Gmt126710BlnrMaster.ROUND_LUME_R,a,inner);
            }
            for(int h:new int[]{6,9}){
                double a=Gmt126710BlnrMaster.angleForHour(h);
                drawRectTarget(c,H,Gmt126710BlnrMaster.BATON_CENTER_R,
                        Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF,
                        Gmt126710BlnrMaster.BATON_RADIAL_HALF,a,outer);
                drawRectTarget(c,H,Gmt126710BlnrMaster.BATON_CENTER_R,
                        Gmt126710BlnrMaster.BATON_LUME_TANGENTIAL_HALF,
                        Gmt126710BlnrMaster.BATON_LUME_RADIAL_HALF,a,inner);
            }
            drawMasterTriangle(c,H,outer,false);drawMasterTriangle(c,H,inner,true);
        }else drawProjectedCircle(c,H,1.0,outer);
        return out;
    }

    private static void drawMasterTriangle(Canvas c,Mat H,Paint p,boolean lume){
        double a=Gmt126710BlnrMaster.angleForHour(12),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double center=lume?Gmt126710BlnrMaster.TRI_LUME_CENTER_R:Gmt126710BlnrMaster.TRI_CENTER_R;
        double baseOut=lume?Gmt126710BlnrMaster.TRI_LUME_BASE_OUTWARD:Gmt126710BlnrMaster.TRI_BASE_OUTWARD;
        double apexIn=lume?Gmt126710BlnrMaster.TRI_LUME_APEX_INWARD:Gmt126710BlnrMaster.TRI_APEX_INWARD;
        double halfBase=lume?Gmt126710BlnrMaster.TRI_LUME_HALF_BASE:Gmt126710BlnrMaster.TRI_HALF_BASE;
        double cx=center*ux,cy=center*uy;
        double[][] pts={{cx+ux*baseOut+vx*halfBase,cy+uy*baseOut+vy*halfBase},
                {cx+ux*baseOut-vx*halfBase,cy+uy*baseOut-vy*halfBase},
                {cx-ux*apexIn,cy-uy*apexIn}};
        drawQuad(c,H,pts,p);
    }

    private static Bitmap rectify(Mat rgba,Mat H,Bitmap source){
        int side=Math.max(700,Math.min(1200,Math.max(source.getWidth(),source.getHeight())));
        Mat S=Mat.eye(3,3,CvType.CV_64F);
        S.put(0,0,side/2.15);S.put(1,1,side/2.15);S.put(0,2,side/2.0);S.put(1,2,side/2.0);
        Mat Hinv=new Mat(),M=new Mat(),zero=new Mat();Core.invert(H,Hinv);Core.gemm(S,Hinv,1,zero,0,M);
        Mat dst=new Mat();Imgproc.warpPerspective(rgba,dst,M,new Size(side,side),Imgproc.INTER_CUBIC,
                Core.BORDER_CONSTANT,new Scalar(8,17,31,255));
        Bitmap b=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,b);
        S.release();Hinv.release();M.release();zero.release();dst.release();return b;
    }

    private static void drawProjectedCircle(Canvas c,Mat H,double r,Paint p){
        Path path=new Path();
        for(int i=0;i<=180;i++){
            double a=2*Math.PI*i/180.0;Point q=project(H,r*Math.cos(a),r*Math.sin(a));
            if(i==0)path.moveTo((float)q.x,(float)q.y);else path.lineTo((float)q.x,(float)q.y);
        }
        c.drawPath(path,p);
    }

    private static void drawCircleTarget(Canvas c,Mat H,double rr,double size,double a,Paint p){
        double cx=rr*Math.cos(a),cy=rr*Math.sin(a);Path path=new Path();
        for(int i=0;i<=48;i++){
            double q=2*Math.PI*i/48.0;Point x=project(H,cx+size*Math.cos(q),cy+size*Math.sin(q));
            if(i==0)path.moveTo((float)x.x,(float)x.y);else path.lineTo((float)x.x,(float)x.y);
        }
        c.drawPath(path,p);
    }

    private static void drawRectTarget(Canvas c,Mat H,double rr,double tangentialHalf,
                                       double radialHalf,double a,Paint p){
        double cx=rr*Math.cos(a),cy=rr*Math.sin(a),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double[][] pts={{cx-ux*radialHalf-vx*tangentialHalf,cy-uy*radialHalf-vy*tangentialHalf},
                {cx-ux*radialHalf+vx*tangentialHalf,cy-uy*radialHalf+vy*tangentialHalf},
                {cx+ux*radialHalf+vx*tangentialHalf,cy+uy*radialHalf+vy*tangentialHalf},
                {cx+ux*radialHalf-vx*tangentialHalf,cy+uy*radialHalf-vy*tangentialHalf}};
        drawQuad(c,H,pts,p);
    }

    private static void drawQuad(Canvas c,Mat H,double[][] pts,Paint p){
        Path path=new Path();
        for(int i=0;i<pts.length;i++){
            Point q=project(H,pts[i][0],pts[i][1]);
            if(i==0)path.moveTo((float)q.x,(float)q.y);else path.lineTo((float)q.x,(float)q.y);
        }
        path.close();c.drawPath(path,p);
    }

    private static Paint paint(int color,float width,int alpha){
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);p.setColor(color);p.setAlpha(alpha);return p;
    }

    private static double[] matrixValues(Mat h){double[] v=new double[9];h.get(0,0,v);return v;}
    private static double normalizedTerm(double[] h,int i){return h[i]/h[8];}
    private static double normalizedTerm(double[][] h,int r,int c){return h[r][c]/h[2][2];}
    private static Point project(Mat H,double x,double y){
        double[] h=new double[9];H.get(0,0,h);double w=h[6]*x+h[7]*y+h[8];
        if(Math.abs(w)<1e-9)w=1e-9;
        return new Point((h[0]*x+h[1]*y+h[2])/w,(h[3]*x+h[4]*y+h[5])/w);
    }

    private MinuteTrackFirstOverlay(){}
}
