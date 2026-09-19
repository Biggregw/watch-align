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
 * Visual QC overlay. Photo analysis establishes pose only. The fixed 126710BLNR
 * master supplies all marker geometry. QC markers never move or resize the master.
 */
final class PerspectiveGmtOverlay {
    static final class Result {
        final Bitmap nativeOverlay;
        final Bitmap rectified;
        final String report;
        final double confidence;
        Result(Bitmap n, Bitmap r, String s, double c){nativeOverlay=n;rectified=r;report=s;confidence=c;}
    }

    static final class DialSeed {
        final double x,y,r,quality,rollDeg;
        DialSeed(double x,double y,double r,double q,double roll){this.x=x;this.y=y;this.r=r;this.quality=q;this.rollDeg=roll;}
    }

    static boolean supports(String modelRef){return CanonicalGmtGeometryAnalyzer.supports(modelRef);}

    static Result build(Bitmap input,String modelRef){
        return build(input, modelRef, null, Color.rgb(255,45,45));
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
            if(manualSeed==null&&detectedEllipse!=null){
                // The circular-dial roll estimate (measureMarkerSet, in WatchAlignCoreV7) assumes
                // markers are evenly spaced at 30 degree intervals in image space, which is only
                // true for a perfectly frontal photo. Any real tilt warps that spacing unevenly,
                // biasing the roll. Since roll seeds all four homography anchor points exactly
                // (ellipseCardinalPoints -> homographyFromUnitSquare), that bias lands undiluted
                // on whichever markers sit at those anchors (12 and 9 o'clock here) while getting
                // smeared across the interpolated markers elsewhere. Re-measure roll in the fitted
                // ellipse's own normalized frame, where marker spacing is genuinely uniform, and
                // prefer that corrected estimate.
                double correctedRoll=ellipseAwareRoll(gray,detectedEllipse,seed.rollDeg);
                if(Double.isFinite(correctedRoll)&&Math.abs(correctedRoll)<=15.0){
                    seed=new DialSeed(seed.x,seed.y,seed.r,seed.quality,correctedRoll);
                }
            }
            RotatedRect ellipse;
            String seedSource;
            boolean perspectiveFallback=false;

            if(manualSeed!=null){
                if(detectedEllipse!=null){
                    double measured=rayEllipseRadius(detectedEllipse,Math.toRadians(manualSeed.rollDeg-90.0));
                    double scale=(measured>1.0&&Double.isFinite(measured))?manualSeed.r/measured:1.0;
                    ellipse=new RotatedRect(
                            new Point(manualSeed.x,manualSeed.y),
                            new Size(detectedEllipse.size.width*scale,detectedEllipse.size.height*scale),
                            detectedEllipse.angle);
                    seedSource="2-point dial-edge seed with fitted perspective ellipse";
                }else{
                    ellipse=new RotatedRect(new Point(seed.x,seed.y),new Size(seed.r*2.0,seed.r*2.0),0.0);
                    seedSource="2-point dial-edge seed with circular fallback";
                    perspectiveFallback=true;
                }
            }else if(detectedEllipse!=null){
                ellipse=normalizeEllipseToOuterRadius(detectedEllipse,seed);
                seedSource="fitted dial ellipse normalized to detected outer dial radius";
            }else{
                // A clean frontal watch can still fail contour ellipse selection because hands,
                // cyclops glare and bezel edges fragment the dial boundary. Do not throw away
                // an otherwise valid dial seed. Use its centre/radius/roll as a conservative
                // circular pose and mark the result as lower confidence so the user can refine
                // it with Precision Align if necessary.
                ellipse=new RotatedRect(new Point(seed.x,seed.y),new Size(seed.r*2.0,seed.r*2.0),0.0);
                seedSource="detected dial seed with circular fallback";
                perspectiveFallback=true;
            }

            double major=Math.max(ellipse.size.width,ellipse.size.height);
            double minor=Math.min(ellipse.size.width,ellipse.size.height);
            double axisRatio=minor/Math.max(1.0,major);
            double tiltDeg=Math.toDegrees(Math.acos(Math.max(0.0,Math.min(1.0,axisRatio))));

            Point[] card=ellipseCardinalPoints(ellipse,seed.rollDeg);
            Mat H0=homographyFromUnitSquare(card);
            if(H0==null||H0.empty())return null;
            double[] h0Values=matrixValues(H0);
            DialProjectiveRefiner.MatResult refinement=DialProjectiveRefiner.refineWithDiagnostics(edges,H0);
            Mat H=refinement.homography;
            H0.release();

            double reproj=reprojectionError(H,card);
            double centerErr=Math.hypot(ellipse.center.x-seed.x,ellipse.center.y-seed.y)/Math.max(1.0,seed.r);
            double confidence=confidence(seed.quality,reproj,centerErr,axisRatio);
            if(perspectiveFallback)confidence*=0.65;

            Bitmap overlay=renderNative(input,H,modelRef,overlayColor);
            Bitmap rectified=rectify(src,H,input);
            String master=Gmt126710BlnrMaster.supports(modelRef)?Gmt126710BlnrMaster.ID:"canonical GMT fallback";
            String report=String.format(Locale.US,
                    "\n\nVISUAL QC MASTER\n"+
                    "Pose source: %s. Assisted points use the dial edge, not hour markers, so marker QC is not fitted away.\n"+
                    "Inspection geometry: %s. Red outlines are the fixed master; white outlines are lume references.\n"+
                    "Ellipse axes: %.1f × %.1f px; apparent tilt %.1f°; dial roll %+.2f°.\n"+
                    "Dial-centre agreement: %.2f%% of dial radius. Pose residual: %.2f px. Confidence: %.0f%%.\n"+
                    "Projective refinement: %s.\n"+
                    "H0 projective terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Refined candidate terms: h31=%+.6f, h32=%+.6f.\n"+
                    "Fit evidence: %.4f before, %.4f after. Holdout evidence: %.4f before, %.4f after.\n"+
                    "H0 fallback used: %s.\n"+
                    "Use Native Template with opacity/blink and fine nudge. Automated QC checks remain available separately.\n",
                    seedSource,master,major,minor,tiltDeg,seed.rollDeg,centerErr*100.0,reproj,confidence*100.0,
                    refinement.diagnostics.accepted?"ACCEPTED":"REJECTED",
                    normalizedTerm(h0Values,6),normalizedTerm(h0Values,7),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,0),
                    normalizedTerm(refinement.diagnostics.evaluatedHomography,2,1),
                    refinement.diagnostics.fitBefore,refinement.diagnostics.evaluatedFitAfter,
                    refinement.diagnostics.holdoutBefore,refinement.diagnostics.evaluatedHoldoutAfter,
                    refinement.diagnostics.accepted?"NO":"YES");
            H.release();
            return new Result(overlay,rectified,report,confidence);
        }catch(Throwable ignored){return null;}
        finally{src.release();gray.release();blur.release();edges.release();}
    }

    /**
     * Re-measures marker angular offsets in the fitted ellipse's own normalized frame
     * (undo tilt rotation, then divide by each axis's radius) instead of raw image-space
     * angles around a plain circle. In that normalized frame a genuinely evenly-spaced
     * dial maps back to even 30-degree spacing regardless of photo tilt, so the median
     * offset from target is a much less biased estimate of true roll than the circular
     * measurement in WatchAlignCoreV7.measureMarkerSet.
     */
    private static double ellipseAwareRoll(Mat gray,RotatedRect ellipse,double fallbackRoll){
        double axis=Math.toRadians(ellipse.angle),ca=Math.cos(axis),sa=Math.sin(axis);
        double rx=Math.max(1e-6,ellipse.size.width/2.0),ry=Math.max(1e-6,ellipse.size.height/2.0);
        double cx=ellipse.center.x,cy=ellipse.center.y;
        int w=gray.cols(),h=gray.rows();
        double innerN=0.66,outerN=0.94;
        double reach=Math.max(rx,ry)+4;
        int x0=Math.max(0,(int)(cx-reach)),x1=Math.min(w-1,(int)(cx+reach));
        int y0=Math.max(0,(int)(cy-reach)),y1=Math.min(h-1,(int)(cy+reach));
        List<Double> offsets=new ArrayList<>();
        for(int hour=1;hour<=12;hour++){
            double target=hour==12?0:hour*30.0,sw=0,sd=0;int count=0;
            for(int y=y0;y<=y1;y+=2)for(int x=x0;x<=x1;x+=2){
                double dx=x-cx,dy=y-cy;
                double lx=ca*dx+sa*dy,ly=-sa*dx+ca*dy;
                double nx=lx/rx,ny=ly/ry;
                double r=Math.hypot(nx,ny);if(r<innerN||r>outerN)continue;
                double a=Math.toDegrees(Math.atan2(nx,-ny));if(a<0)a+=360;
                double d=GeometryRegistration.wrap180(a-target);if(Math.abs(d)>8.0)continue;
                double[] gv=gray.get(y,x);if(gv==null||gv[0]<155)continue;
                double wt=Math.max(1.0,(gv[0]-145.0)/18.0);sw+=wt;sd+=d*wt;count++;
            }
            if(sw>10&&count>=4)offsets.add(sd/sw);
        }
        if(offsets.size()<4)return fallbackRoll;
        double[] arr=new double[offsets.size()];for(int i=0;i<arr.length;i++)arr[i]=offsets.get(i);
        double corrected=GeometryRegistration.median(arr);
        return Double.isFinite(corrected)?corrected:fallbackRoll;
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

    static RotatedRect normalizeEllipseToOuterRadius(RotatedRect ellipse,DialSeed seed){
        Point canonicalTwelve=ellipseCardinalPoints(ellipse,seed.rollDeg)[0];
        double measured=Math.hypot(canonicalTwelve.x-ellipse.center.x,canonicalTwelve.y-ellipse.center.y);
        if(!(measured>1.0)||!Double.isFinite(measured)||!(seed.r>1.0)||!Double.isFinite(seed.r))return ellipse;
        double scale=seed.r/measured;
        return new RotatedRect(new Point(seed.x,seed.y),
                new Size(ellipse.size.width*scale,ellipse.size.height*scale),ellipse.angle);
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

    /** Returns only the transparent master graphics. The watch image is composited by the caller. */
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
            drawMasterTriangle(c,H,outer,false);
            drawMasterTriangle(c,H,inner,true);
        }else{
            drawProjectedCircle(c,H,1.00,outer);
        }
        return out;
    }

    /** Genuine 12 marker orientation: wide base toward rehaut, point toward hands. */
    private static void drawMasterTriangle(Canvas c,Mat H,Paint p,boolean lume){
        double a=Gmt126710BlnrMaster.angleForHour(12);
        double ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;
        double center=lume?Gmt126710BlnrMaster.TRI_LUME_CENTER_R:Gmt126710BlnrMaster.TRI_CENTER_R;
        double baseOut=lume?Gmt126710BlnrMaster.TRI_LUME_BASE_OUTWARD:Gmt126710BlnrMaster.TRI_BASE_OUTWARD;
        double apexIn=lume?Gmt126710BlnrMaster.TRI_LUME_APEX_INWARD:Gmt126710BlnrMaster.TRI_APEX_INWARD;
        double halfBase=lume?Gmt126710BlnrMaster.TRI_LUME_HALF_BASE:Gmt126710BlnrMaster.TRI_HALF_BASE;
        double cx=center*ux,cy=center*uy;
        double[][] pts={
                {cx+ux*baseOut+vx*halfBase,cy+uy*baseOut+vy*halfBase},
                {cx+ux*baseOut-vx*halfBase,cy+uy*baseOut-vy*halfBase},
                {cx-ux*apexIn,cy-uy*apexIn}
        };
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
    private static void drawRectTarget(Canvas c,Mat H,double rr,double tangentialHalf,double radialHalf,double a,Paint p){double cx=rr*Math.cos(a),cy=rr*Math.sin(a);double ux=Math.cos(a),uy=Math.sin(a),vx=-uy,vy=ux;double[][] pts={{cx-ux*radialHalf-vx*tangentialHalf,cy-uy*radialHalf-vy*tangentialHalf},{cx-ux*radialHalf+vx*tangentialHalf,cy-uy*radialHalf+vy*tangentialHalf},{cx+ux*radialHalf+vx*tangentialHalf,cy+uy*radialHalf+vy*tangentialHalf},{cx+ux*radialHalf-vx*tangentialHalf,cy+uy*radialHalf-vy*tangentialHalf}};drawQuad(c,H,pts,p);}
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
