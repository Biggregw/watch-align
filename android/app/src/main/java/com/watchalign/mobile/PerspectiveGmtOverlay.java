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
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Visual QC overlay. The dial edge establishes centre/scale/ellipse pose. Dial rotation is
 * solved separately from minor minute-track ticks. A disjoint set of minor ticks validates
 * the final pose before the automatic master is trusted.
 */
final class PerspectiveGmtOverlay {
    static final class Result {
        final Bitmap nativeOverlay;
        final Bitmap rectified;
        final String report;
        final double confidence;
        final boolean automaticAccepted;
        Result(Bitmap n,Bitmap r,String s,double c,boolean accepted){
            nativeOverlay=n;rectified=r;report=s;confidence=c;automaticAccepted=accepted;
        }
    }

    static final class DialSeed {
        final double x,y,r,quality,rollDeg;
        DialSeed(double x,double y,double r,double q,double roll){this.x=x;this.y=y;this.r=r;this.quality=q;this.rollDeg=roll;}
    }

    static boolean supports(String modelRef){return CanonicalGmtGeometryAnalyzer.supports(modelRef);}

    static Result build(Bitmap input,String modelRef){
        return build(input,modelRef,null,Color.rgb(255,45,45));
    }

    static Result build(Bitmap input,String modelRef,DialSeed manualSeed,int overlayColor){
        if(input==null||!supports(modelRef))return null;
        Mat src=new Mat(),gray=new Mat(),blur=new Mat(),edges=new Mat();
        try{
            Utils.bitmapToMat(input,src);
            Imgproc.cvtColor(src,gray,Imgproc.COLOR_RGBA2GRAY);
            Imgproc.GaussianBlur(gray,blur,new Size(5,5),1.2);
            Imgproc.Canny(blur,edges,55,145);

            DialSeed detectedSeed=seed(src);
            DialSeed seed=manualSeed!=null?manualSeed:detectedSeed;
            if(seed==null||!(seed.r>40))return null;

            RotatedRect detectedEllipse=detectedSeed==null?null:findDialEllipse(edges,detectedSeed);
            double rawEllipseMajor=Double.NaN,rawEllipseMinor=Double.NaN,rawTwelveRadius=Double.NaN,seedToRawScale=Double.NaN;
            if(detectedEllipse!=null){
                rawEllipseMajor=Math.max(detectedEllipse.size.width,detectedEllipse.size.height);
                rawEllipseMinor=Math.min(detectedEllipse.size.width,detectedEllipse.size.height);
                Point rawTwelve=ellipseCardinalPoints(detectedEllipse,0.0)[0];
                rawTwelveRadius=Math.hypot(rawTwelve.x-detectedEllipse.center.x,rawTwelve.y-detectedEllipse.center.y);
                if(rawTwelveRadius>1.0&&Double.isFinite(rawTwelveRadius))seedToRawScale=seed.r/rawTwelveRadius;
            }

            RotatedRect ellipse;
            String seedSource;
            boolean perspectiveFallback=false;
            if(manualSeed!=null){
                if(detectedEllipse!=null){
                    double measured=rayEllipseRadius(detectedEllipse,Math.toRadians(manualSeed.rollDeg-90.0));
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
                ellipse=anchorFittedEllipseToSeedCenter(detectedEllipse,seed);
                seedSource="fitted dial ellipse scale with detected dial centre";
            }else{
                ellipse=new RotatedRect(new Point(seed.x,seed.y),new Size(seed.r*2.0,seed.r*2.0),0.0);
                seedSource="detected dial seed with circular fallback";
                perspectiveFallback=true;
            }

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));
            double dialRadiusPx=(major+minor)/4.0;

            // Base pose comes only from the dial edge/ellipse. Do not use applied markers to set it.
            double startingRoll=manualSeed!=null?manualSeed.rollDeg:0.0;
            Point[] baseCard=ellipseCardinalPoints(ellipse,startingRoll);
            Mat edgeH=homographyFromUnitSquare(baseCard);
            if(edgeH==null||edgeH.empty())return null;

            // Automatic rotation is a separate phase using minor minute-track ticks only.
            MinuteTrackPoseValidator.RotationResult initialRotation=null;
            Mat rotatedH;
            double solvedRoll=startingRoll;
            if(manualSeed==null){
                initialRotation=MinuteTrackPoseValidator.solveRotation(edges,edgeH,15.0);
                rotatedH=initialRotation.homography;
                solvedRoll=initialRotation.deltaDeg;
            }else{
                rotatedH=edgeH.clone();
            }
            edgeH.release();

            double[] h0Values=matrixValues(rotatedH);
            double projectiveLimit=Math.max(0.015,Math.min(0.32,0.45*Math.sin(Math.toRadians(tiltDeg))));
            DialProjectiveRefiner.MatResult refinement=DialProjectiveRefiner.refineWithDiagnostics(edges,rotatedH,projectiveLimit);
            rotatedH.release();

            Mat H;
            MinuteTrackPoseValidator.RotationResult fineRotation=null;
            if(manualSeed==null){
                // The refiner may make a small rotation-like affine move. Re-solve only a narrow
                // final rotation from the minute track, keeping rotation conceptually separate.
                fineRotation=MinuteTrackPoseValidator.fineTuneRotation(edges,refinement.homography);
                H=fineRotation.homography;
                solvedRoll+=fineRotation.deltaDeg;
            }else{
                H=refinement.homography.clone();
            }
            refinement.homography.release();

            MinuteTrackPoseValidator.ValidationResult validation=MinuteTrackPoseValidator.validate(edges,H,dialRadiusPx);
            boolean automaticAccepted=manualSeed!=null||(!perspectiveFallback&&validation.accepted);

            Point[] expectedCard=ellipseCardinalPoints(ellipse,solvedRoll);
            double reproj=reprojectionError(H,expectedCard);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double confidence=confidence(seed.quality,reproj,centerErr,axisRatio);
            if(perspectiveFallback)confidence*=0.65;
            if(!automaticAccepted)confidence=Math.min(confidence,0.35);

            Bitmap overlay=renderNative(input,H,modelRef,overlayColor);
            Bitmap rectified=rectify(src,H,input);
            String master=Gmt126710BlnrMaster.supports(modelRef)?Gmt126710BlnrMaster.ID:"canonical GMT fallback";
            double initialFit=initialRotation==null?Double.NaN:initialRotation.fitMedianPx;
            double fineDelta=fineRotation==null?0.0:fineRotation.deltaDeg;
            String report=String.format(Locale.US,
                    "\n\nVISUAL QC MASTER\n"+
                    "Pose source: %s. Dial edge establishes centre/scale/ellipse pose; applied hour markers are not used to fit the master.\n"+
                    "Inspection geometry: %s. Red outlines are the fixed master; white outlines are lume references.\n"+
                    "Ellipse axes: %.1f × %.1f px; apparent tilt %.1f°; minute-track roll %+.2f°; raw ellipse axis %+.2f°.\n"+
                    "Scale diagnostics: seed diameter %.1f px; raw fitted ellipse %.1f × %.1f px; seed/raw 12-radius ratio %.4f. Fitted ellipse scale preserved.\n"+
                    "Rotation solve: minor ticks only; initial fit median %.2f px; final fine correction %+.2f°.\n"+
                    "Independent minute-track validation: %s; %d held-out ticks; median %.2f px (limit %.2f), p90 %.2f px (limit %.2f), inliers %.0f%% at %.2f px.\n"+
                    "Automatic master: %s. %s\n"+
                    "Dial-centre agreement: %.2f%% of dial radius. Pose residual: %.2f px. Confidence: %.0f%%.\n"+
                    "Projective refinement: %s.\n"+
                    "H0 projective terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Refined candidate terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Fit evidence: %.4f before, %.4f after. Holdout evidence: %.4f before, %.4f after.\n"+
                    "H0 fallback used: %s.\n"+
                    "Use Native Template with opacity/blink and manual move/resize/rotate when required. Automated QC checks remain separate.\n",
                    seedSource,master,major,minor,tiltDeg,solvedRoll,
                    detectedEllipse!=null?detectedEllipse.angle:Double.NaN,
                    seed.r*2.0,rawEllipseMajor,rawEllipseMinor,seedToRawScale,
                    initialFit,fineDelta,
                    validation.accepted?"ACCEPTED":"REJECTED",validation.holdoutTicks,
                    validation.medianPx,validation.medianLimitPx,validation.p90Px,validation.p90LimitPx,
                    validation.inlierFraction*100.0,validation.inlierLimitPx,
                    automaticAccepted?"ACCEPTED":"REJECTED",
                    automaticAccepted?"Safe to show automatically.":"Not shown automatically; open Native Template and align manually.",
                    centerErr*100.0,reproj,confidence*100.0,
                    refinement.diagnostics.accepted?"ACCEPTED":"REJECTED",
                    normalizedTerm(h0Values,6),normalizedTerm(h0Values,7),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,0),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,1),
                    refinement.diagnostics.fitBefore,refinement.diagnostics.evaluatedFitAfter,
                    refinement.diagnostics.holdoutBefore,refinement.diagnostics.evaluatedHoldoutAfter,
                    refinement.diagnostics.accepted?"NO":"YES");
            H.release();
            return new Result(overlay,rectified,report,confidence,automaticAccepted);
        }catch(Throwable ignored){return null;}
        finally{src.release();gray.release();blur.release();edges.release();}
    }

    /** Retained for diagnostic/unit-test coverage of ellipse-axis normalization. */
    static Point undoEllipseDistortion(RotatedRect ellipse,double dx,double dy){
        double axis=Math.toRadians(ellipse.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double rx=Math.max(1e-6,ellipse.size.width/2.0),ry=Math.max(1e-6,ellipse.size.height/2.0);
        double localX=ca*dx+sa*dy;
        double localY=-sa*dx+ca*dy;
        double scaledX=localX/rx;
        double scaledY=localY/ry;
        return new Point(ca*scaledX-sa*scaledY,sa*scaledX+ca*scaledY);
    }

    private static DialSeed seed(Mat rgba)throws Exception{
        Mat bgr=new Mat();
        try{
            Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);
            Method detect=WatchAlignCoreV7.class.getDeclaredMethod("detectDial",Mat.class);detect.setAccessible(true);
            Object d=detect.invoke(null,bgr);if(d==null)return null;
            double roll=0.0;
            try{
                Method markers=WatchAlignCoreV7.class.getDeclaredMethod("measureMarkerSet",Mat.class,d.getClass());
                markers.setAccessible(true);
                Object set=markers.invoke(null,bgr,d);
                double r=num(set,"globalRotation");
                if(Double.isFinite(r)&&Math.abs(r)<=15.0)roll=r;
            }catch(Throwable ignored){}
            return new DialSeed(num(d,"x"),num(d,"y"),num(d,"r"),num(d,"quality"),roll);
        }finally{bgr.release();}
    }

    private static RotatedRect findDialEllipse(Mat edges,DialSeed s){
        List<MatOfPoint> contours=new ArrayList<>();Mat hierarchy=new Mat();Mat contourInput=edges.clone();
        try{
            Imgproc.findContours(contourInput,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
            RotatedRect best=null;double bestScore=Double.POSITIVE_INFINITY;
            for(MatOfPoint c:contours){
                if(c.rows()<40)continue;
                MatOfPoint2f f=new MatOfPoint2f(c.toArray());
                try{
                    RotatedRect e=Imgproc.fitEllipse(f);
                    double a=Math.max(e.size.width,e.size.height),b=Math.min(e.size.width,e.size.height);
                    if(a<1.55*s.r||a>2.35*s.r||b<1.35*s.r||b>2.30*s.r)continue;
                    double dc=Math.hypot(e.center.x-s.x,e.center.y-s.y)/s.r;
                    if(dc>0.22)continue;
                    double sizeErr=Math.abs(a/(2.0*s.r)-1.0)+Math.abs(b/(2.0*s.r)-1.0);
                    double ratio=b/Math.max(1.0,a);
                    if(ratio<0.72)continue;
                    double score=2.4*dc+sizeErr;
                    if(score<bestScore){bestScore=score;best=e;}
                }catch(Throwable ignored){}finally{f.release();}
            }
            return best;
        }finally{contourInput.release();for(MatOfPoint c:contours)c.release();hierarchy.release();}
    }

    private static Mat homographyFromUnitSquare(Point[] dst){
        MatOfPoint2f srcPts=new MatOfPoint2f(new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0));
        MatOfPoint2f dstPts=new MatOfPoint2f(dst);
        try{return Calib3d.findHomography(srcPts,dstPts,0);}
        finally{srcPts.release();dstPts.release();}
    }

    static Point[] ellipseCardinalPoints(RotatedRect e,double rollDeg){
        double rx=Math.max(1e-6,e.size.width/2.0),ry=Math.max(1e-6,e.size.height/2.0);
        double axis=Math.toRadians(e.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double roll=Math.toRadians(rollDeg),cr=Math.cos(roll),sr=Math.sin(roll);
        double[][] canonical={{0,-1},{1,0},{0,1},{-1,0}};
        Point[] mapped=new Point[canonical.length];
        for(int i=0;i<canonical.length;i++){
            double x=cr*canonical[i][0]-sr*canonical[i][1];
            double y=sr*canonical[i][0]+cr*canonical[i][1];
            double localX=ca*x+sa*y,localY=-sa*x+ca*y;
            double scaledX=rx*localX,scaledY=ry*localY;
            mapped[i]=new Point(e.center.x+ca*scaledX-sa*scaledY,e.center.y+sa*scaledX+ca*scaledY);
        }
        return mapped;
    }

    static RotatedRect anchorFittedEllipseToSeedCenter(RotatedRect ellipse,DialSeed seed){
        return new RotatedRect(new Point(seed.x,seed.y),new Size(ellipse.size.width,ellipse.size.height),ellipse.angle);
    }

    private static Point rayEllipseIntersection(RotatedRect e,double imageAngle){
        double rx=Math.max(1e-6,e.size.width/2.0),ry=Math.max(1e-6,e.size.height/2.0);
        double t=Math.toRadians(e.angle);
        double dx=Math.cos(imageAngle),dy=Math.sin(imageAngle);
        double lx=dx*Math.cos(t)+dy*Math.sin(t);
        double ly=-dx*Math.sin(t)+dy*Math.cos(t);
        double denom=Math.sqrt((lx*lx)/(rx*rx)+(ly*ly)/(ry*ry));
        double s=denom>1e-9?1.0/denom:0.0;
        return new Point(e.center.x+s*dx,e.center.y+s*dy);
    }

    private static double rayEllipseRadius(RotatedRect e,double imageAngle){
        Point p=rayEllipseIntersection(e,imageAngle);
        return Math.hypot(p.x-e.center.x,p.y-e.center.y);
    }

    private static double reprojectionError(Mat H,Point[] expected){
        Point[] canonical={new Point(0,-1),new Point(1,0),new Point(0,1),new Point(-1,0)};
        double sum=0;for(int i=0;i<4;i++){Point p=project(H,canonical[i].x,canonical[i].y);sum+=Math.hypot(p.x-expected[i].x,p.y-expected[i].y);}return sum/4.0;
    }

    private static double confidence(double q,double reproj,double centerErr,double axisRatio){
        double a=Math.max(0,Math.min(1,(q-0.45)/0.45));
        double b=Math.max(0,1-reproj/3.0);
        double c=Math.max(0,1-centerErr/0.16);
        double d=Math.max(0,Math.min(1,(axisRatio-0.72)/0.20));
        return Math.max(0,Math.min(1,0.35*a+0.20*b+0.30*c+0.15*d));
    }

    private static Bitmap renderNative(Bitmap source,Mat H,String modelRef,int overlayColor){
        Bitmap out=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);Canvas c=new Canvas(out);
        float scale=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        Paint outer=paint(overlayColor,1.25f*scale,245);
        Paint inner=paint(Color.WHITE,0.8f*scale,150);
        Paint guide=paint(overlayColor,0.75f*scale,65);
        if(Gmt126710BlnrMaster.supports(modelRef)){
            drawProjectedCircle(c,H,Gmt126710BlnrMaster.DIAL_EDGE_R,guide);
            drawProjectedCircle(c,H,Gmt126710BlnrMaster.MINUTE_TRACK_R,guide);
            for(int h:new int[]{1,2,4,5,7,8,10,11}){
                double a=Gmt126710BlnrMaster.angleForHour(h);
                drawCircleTarget(c,H,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.ROUND_OUTER_R,a,outer);
                drawCircleTarget(c,H,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.ROUND_LUME_R,a,inner);
            }
            for(int h:new int[]{6,9}){
                double a=Gmt126710BlnrMaster.angleForHour(h);
                drawRectTarget(c,H,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.BATON_TANGENTIAL_HALF,Gmt126710BlnrMaster.BATON_RADIAL_HALF,a,outer);
                drawRectTarget(c,H,Gmt126710BlnrMaster.MARKER_CENTER_R,Gmt126710BlnrMaster.BATON_LUME_TANGENTIAL_HALF,Gmt126710BlnrMaster.BATON_LUME_RADIAL_HALF,a,inner);
            }
            drawMasterTriangle(c,H,outer,false);drawMasterTriangle(c,H,inner,true);
        }else drawProjectedCircle(c,H,1.00,outer);
        return out;
    }

    private static void drawMasterTriangle(Canvas c,Mat H,Paint p,boolean lume){
        double a=Gmt126710BlnrMaster.angleForHour(12),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double center=lume?Gmt126710BlnrMaster.TRI_LUME_CENTER_R:Gmt126710BlnrMaster.TRI_CENTER_R;
        double baseOut=lume?Gmt126710BlnrMaster.TRI_LUME_BASE_OUTWARD:Gmt126710BlnrMaster.TRI_BASE_OUTWARD;
        double apexIn=lume?Gmt126710BlnrMaster.TRI_LUME_APEX_INWARD:Gmt126710BlnrMaster.TRI_APEX_INWARD;
        double halfBase=lume?Gmt126710BlnrMaster.TRI_LUME_HALF_BASE:Gmt126710BlnrMaster.TRI_HALF_BASE;
        double cx=center*ux,cy=center*uy;
        double[][] pts={{cx+ux*baseOut+vx*halfBase,cy+uy*baseOut+vy*halfBase},{cx+ux*baseOut-vx*halfBase,cy+uy*baseOut-vy*halfBase},{cx-ux*apexIn,cy-uy*apexIn}};
        drawQuad(c,H,pts,p);
    }

    private static Bitmap rectify(Mat rgba,Mat H,Bitmap source){
        int side=Math.max(700,Math.min(1200,Math.max(source.getWidth(),source.getHeight())));
        Mat S=Mat.eye(3,3,CvType.CV_64F);S.put(0,0,side/2.15);S.put(1,1,side/2.15);S.put(0,2,side/2.0);S.put(1,2,side/2.0);
        Mat Hinv=new Mat(),M=new Mat(),zero=new Mat();Core.invert(H,Hinv);Core.gemm(S,Hinv,1,zero,0,M);
        Mat dst=new Mat();Imgproc.warpPerspective(rgba,dst,M,new Size(side,side),Imgproc.INTER_CUBIC,Core.BORDER_CONSTANT,new Scalar(8,17,31,255));
        Bitmap b=Bitmap.createBitmap(side,side,Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,b);S.release();Hinv.release();M.release();zero.release();dst.release();return b;
    }

    private static void drawProjectedCircle(Canvas c,Mat H,double r,Paint p){Path path=new Path();for(int i=0;i<=180;i++){double a=2*Math.PI*i/180.0;Point q=project(H,r*Math.cos(a),r*Math.sin(a));if(i==0)path.moveTo((float)q.x,(float)q.y);else path.lineTo((float)q.x,(float)q.y);}c.drawPath(path,p);}
    private static void drawCircleTarget(Canvas c,Mat H,double rr,double size,double a,Paint p){double cx=rr*Math.cos(a),cy=rr*Math.sin(a);Path path=new Path();for(int i=0;i<=48;i++){double q=2*Math.PI*i/48.0;Point x=project(H,cx+size*Math.cos(q),cy+size*Math.sin(q));if(i==0)path.moveTo((float)x.x,(float)x.y);else path.lineTo((float)x.x,(float)x.y);}c.drawPath(path,p);}
    private static void drawRectTarget(Canvas c,Mat H,double rr,double tangentialHalf,double radialHalf,double a,Paint p){double cx=rr*Math.cos(a),cy=rr*Math.sin(a),ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;double[][] pts={{cx-ux*radialHalf-vx*tangentialHalf,cy-uy*radialHalf-vy*tangentialHalf},{cx-ux*radialHalf+vx*tangentialHalf,cy-uy*radialHalf+vy*tangentialHalf},{cx+ux*radialHalf+vx*tangentialHalf,cy+uy*radialHalf+vy*tangentialHalf},{cx+ux*radialHalf-vx*tangentialHalf,cy+uy*radialHalf-vy*tangentialHalf}};drawQuad(c,H,pts,p);}
    private static void drawQuad(Canvas c,Mat H,double[][] pts,Paint p){Path path=new Path();for(int i=0;i<pts.length;i++){Point q=project(H,pts[i][0],pts[i][1]);if(i==0)path.moveTo((float)q.x,(float)q.y);else path.lineTo((float)q.x,(float)q.y);}path.close();c.drawPath(path,p);}
    private static Paint paint(int color,float width,int alpha){Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setColor(color);p.setAlpha(alpha);return p;}
    private static double[] matrixValues(Mat h){double[] values=new double[9];h.get(0,0,values);return values;}
    private static double normalizedTerm(double[] h,int index){return h[index]/h[8];}
    private static double normalizedTerm(double[][] h,int row,int col){return h[row][col]/h[2][2];}
    private static Point project(Mat H,double x,double y){double[] h=new double[9];H.get(0,0,h);double w=h[6]*x+h[7]*y+h[8];if(Math.abs(w)<1e-9)w=1e-9;return new Point((h[0]*x+h[1]*y+h[2])/w,(h[3]*x+h[4]*y+h[5])/w);}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private static double num(Object o,String n)throws Exception{return ((Number)field(o,n)).doubleValue();}
    private PerspectiveGmtOverlay(){}
}
