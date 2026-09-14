package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;

import com.watchalign.mobile.qc.QcModuleResult;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Automatic OUTER APPLIED-METAL 12-triangle measurement on the shared rectified GMT dial. */
final class GmtTriangleAutoAnalyzer {
    static final class Result {
        final boolean available;
        final PointF left,right,apex;
        final double tangentialOffsetR,radialOffsetR,rotationDeg;
        final double widthDeltaPct,heightDeltaPct,trackGapOverBase,trackGapDelta;
        final double confidence;
        final String summary;
        Result(boolean ok,PointF l,PointF r,PointF a,double tang,double radial,double rot,
               double width,double height,double gap,double gapDelta,double conf,String text){
            available=ok;left=l;right=r;apex=a;tangentialOffsetR=tang;radialOffsetR=radial;
            rotationDeg=rot;widthDeltaPct=width;heightDeltaPct=height;trackGapOverBase=gap;
            this.trackGapDelta=gapDelta;confidence=conf;summary=text;
        }
    }

    private static final class Candidate {Point left,right,apex;double score;}
    private GmtTriangleAutoAnalyzer(){}

    static Result analyse(CanonicalGmtDial dial){
        if(dial==null)return unavailable("canonical dial unavailable");
        if(dial.rectification.confidence()==QcModuleResult.Confidence.LOW)return unavailable("rectification confidence is low; outer-metal triangle measurement is withheld");
        Mat blur=new Mat(),edges=new Mat(),binary=new Mat(),combined=new Mat(),hierarchy=new Mat();
        List<MatOfPoint> contours=new ArrayList<>();
        try{
            Imgproc.GaussianBlur(dial.gray,blur,new Size(5,5),0);
            Imgproc.Canny(blur,edges,38,122);
            Imgproc.adaptiveThreshold(blur,binary,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,41,-4);
            Core.bitwise_or(edges,binary,combined);
            Imgproc.findContours(combined,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            Candidate best=null;
            for(MatOfPoint contour:contours){Candidate c=score(contour);if(c!=null&&(best==null||c.score>best.score))best=c;}
            if(best==null||best.score<0.55)return unavailable("no outer applied-metal 12-triangle contour passed the calibrated position, size and shape gates; an inner lume contour is not used as a substitute");
            return result(best,dial);
        }catch(Throwable t){return unavailable(t.getClass().getSimpleName());}
        finally{for(MatOfPoint c:contours)c.release();blur.release();edges.release();binary.release();combined.release();hierarchy.release();}
    }

    static Result unavailable(String reason){String why=reason==null||reason.trim().isEmpty()?"unknown reason":reason;return new Result(false,null,null,null,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,0,"12 TRIANGLE · OUTER METAL AUTO\nMeasurement withheld: "+why+".");}

    static Bitmap drawMeasured(Bitmap source,Result r){
        if(source==null)return null;Bitmap out=source.copy(Bitmap.Config.ARGB_8888,true);if(r==null||!r.available)return out;
        Canvas c=new Canvas(out);float u=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.MAGENTA);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.8f*u);p.setStrokeJoin(Paint.Join.ROUND);
        Path path=new Path();path.moveTo(r.left.x,r.left.y);path.lineTo(r.right.x,r.right.y);path.lineTo(r.apex.x,r.apex.y);path.close();c.drawPath(path,p);
        c.drawCircle(r.left.x,r.left.y,5*u,p);c.drawCircle(r.right.x,r.right.y,5*u,p);c.drawCircle(r.apex.x,r.apex.y,5*u,p);return out;
    }

    private static Candidate score(MatOfPoint contour){
        double area=Math.abs(Imgproc.contourArea(contour));if(area<1400||area>42000)return null;
        Moments m=Imgproc.moments(contour);if(Math.abs(m.m00)<1e-6)return null;
        double cx=m.m10/m.m00,cy=m.m01/m.m00,nx=(cx-CanonicalGmtDial.CENTER)/CanonicalGmtDial.RADIUS,ny=(cy-CanonicalGmtDial.CENTER)/CanonicalGmtDial.RADIUS;
        if(Math.abs(nx)>.18||ny<-.99||ny>-.45)return null;
        Rect box=Imgproc.boundingRect(contour);double w=box.width/CanonicalGmtDial.RADIUS,h=box.height/CanonicalGmtDial.RADIUS;
        // Reject the smaller inner-lume triangle. The first-party trace puts the outer metal body
        // at roughly 0.173 DR wide and 0.296 DR tall in the corrected 126710BLNR reference.
        if(w<.145||w>.34||h<.21||h>.43)return null;
        Point[] vertices=extract(contour.toArray(),box);if(vertices==null)return null;
        double base=dist(vertices[0],vertices[1])/CanonicalGmtDial.RADIUS,height=pointLineDistance(vertices[2],vertices[0],vertices[1])/CanonicalGmtDial.RADIUS;
        if(base<.150||base>.30||height<.22||height>.40)return null;

        double idealWidth=referenceWidth(),idealHeight=referenceHeight(),idealCy=referenceCentroidY();
        double position=clamp(1-Math.abs(nx)/.10)*clamp(1-Math.abs(ny-idealCy)/.15);
        double widthScore=clamp(1-Math.abs(base-idealWidth)/.10),heightScore=clamp(1-Math.abs(height-idealHeight)/.13);
        double ratioScore=clamp(1-Math.abs(height/base-idealHeight/idealWidth)/.65);
        double apexX=Math.abs((vertices[2].x-(vertices[0].x+vertices[1].x)/2.0)/Math.max(1.0,dist(vertices[0],vertices[1])));double symmetry=clamp(1-apexX/.30);
        double fill=area/Math.max(1.0,box.area()),fillScore=clamp(1-Math.abs(fill-.42)/.35);
        Candidate c=new Candidate();c.left=vertices[0];c.right=vertices[1];c.apex=vertices[2];c.score=.20*position+.26*widthScore+.26*heightScore+.13*ratioScore+.10*symmetry+.05*fillScore;return c;
    }

    private static Point[] extract(Point[] points,Rect box){
        if(points==null||points.length<5)return null;double topCut=box.y+box.height*.48,centre=box.x+box.width*.5;Point left=null,right=null,apex=null;double apexScore=-Double.MAX_VALUE;
        for(Point p:points){if(p.y<=topCut){if(left==null||p.x<left.x)left=p;if(right==null||p.x>right.x)right=p;}double s=p.y-.20*Math.abs(p.x-centre);if(s>apexScore){apexScore=s;apex=p;}}
        if(left==null||right==null||apex==null||right.x<=left.x||apex.y<=Math.min(left.y,right.y))return null;return new Point[]{left,right,apex};
    }

    private static Result result(Candidate c,CanonicalGmtDial dial){
        double lx=nx(c.left.x),ly=ny(c.left.y),rx=nx(c.right.x),ry=ny(c.right.y),ax=nx(c.apex.x),ay=ny(c.apex.y);
        double actualCx=(lx+rx+ax)/3.0,actualCy=(ly+ry+ay)/3.0;
        double idealWidth=referenceWidth(),idealHeight=referenceHeight(),idealCy=referenceCentroidY();
        double width=Math.hypot(rx-lx,ry-ly),height=pointLineDistance(new Point(ax,ay),new Point(lx,ly),new Point(rx,ry));
        double rotation=Math.toDegrees(Math.atan2(ry-ly,rx-lx));while(rotation>90)rotation-=180;while(rotation<=-90)rotation+=180;
        double widthDelta=100*(width-idealWidth)/idealWidth,heightDelta=100*(height-idealHeight)/idealHeight;
        double baseRadial=-(ly+ry)/2.0;
        double idealGap=GenTriangle12RelationalReference.BASE_TO_60_MEDIAN;
        // Combine the traced outer-metal base radius with the genuine-control gap median to define
        // the image-calibrated 60-minute inner position. This is a QC reference, not factory CAD.
        double referenceMinuteInnerR=referenceBaseRadius()+idealWidth*idealGap;
        double gap=(referenceMinuteInnerR-baseRadial)/Math.max(1e-9,width),gapDelta=gap-idealGap;
        Point sl=dial.sourcePoint(c.left.x,c.left.y),sr=dial.sourcePoint(c.right.x,c.right.y),sa=dial.sourcePoint(c.apex.x,c.apex.y);
        double conf=clamp((c.score-.45)/.40);if(dial.rectification.confidence()==QcModuleResult.Confidence.MEDIUM)conf=Math.min(conf,.65);
        String text=String.format(Locale.US,
                "12 TRIANGLE · OUTER METAL AUTO · %.0f%% component confidence\n"+
                "Centre residual: tangential %+.3f DR · radial %+.3f DR (positive radial = inward)\n"+
                "Rotation: %+.2f° from 12 axis · outer-body width %+.1f%% · height %+.1f%% vs first-party traced reference\n"+
                "Track gap: %.3f BW · genuine-control median %.3f BW · delta %+.3f BW\n"+
                "Dimensions are image-derived QC references, not Rolex factory CAD/tolerances.",
                conf*100,actualCx,actualCy-idealCy,rotation,widthDelta,heightDelta,gap,idealGap,gapDelta);
        return new Result(true,new PointF((float)sl.x,(float)sl.y),new PointF((float)sr.x,(float)sr.y),new PointF((float)sa.x,(float)sa.y),actualCx,actualCy-idealCy,rotation,widthDelta,heightDelta,gap,gapDelta,conf,text);
    }

    private static double referenceWidth(){float[][]v=Gmt126710BlnrMeasured.TRI_OUTER;return Math.hypot(v[1][0]-v[0][0],v[1][1]-v[0][1]);}
    private static double referenceHeight(){float[][]v=Gmt126710BlnrMeasured.TRI_OUTER;return pointLineDistance(new Point(v[2][0],v[2][1]),new Point(v[0][0],v[0][1]),new Point(v[1][0],v[1][1]));}
    private static double referenceBaseRadius(){float[][]v=Gmt126710BlnrMeasured.TRI_OUTER;return Gmt126710BlnrMeasured.TRI_CENTER_R+(v[0][1]+v[1][1])/2.0;}
    private static double referenceCentroidY(){float[][]v=Gmt126710BlnrMeasured.TRI_OUTER;double y0=-(Gmt126710BlnrMeasured.TRI_CENTER_R+v[0][1]),y1=-(Gmt126710BlnrMeasured.TRI_CENTER_R+v[1][1]),y2=-(Gmt126710BlnrMeasured.TRI_CENTER_R+v[2][1]);return (y0+y1+y2)/3.0;}
    private static double nx(double x){return (x-CanonicalGmtDial.CENTER)/CanonicalGmtDial.RADIUS;}private static double ny(double y){return (y-CanonicalGmtDial.CENTER)/CanonicalGmtDial.RADIUS;}
    private static double dist(Point a,Point b){return Math.hypot(a.x-b.x,a.y-b.y);}private static double pointLineDistance(Point p,Point a,Point b){double dx=b.x-a.x,dy=b.y-a.y,d=Math.hypot(dx,dy);return d<1e-9?0:Math.abs(dy*p.x-dx*p.y+b.x*a.y-b.y*a.x)/d;}private static double clamp(double x){return Math.max(0,Math.min(1,x));}
}
