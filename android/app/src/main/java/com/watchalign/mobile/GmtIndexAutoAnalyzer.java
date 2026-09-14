package com.watchalign.mobile;

import android.graphics.Bitmap;import android.graphics.Canvas;import android.graphics.Color;import android.graphics.Paint;
import com.watchalign.mobile.qc.GmtIndexPlausibility;import com.watchalign.mobile.qc.IndexGeometryQcModule;import com.watchalign.mobile.qc.QcModuleResult;import com.watchalign.mobile.qc.RawMeasurement;
import org.opencv.core.Core;import org.opencv.core.Mat;import org.opencv.core.MatOfPoint;import org.opencv.core.MatOfPoint2f;import org.opencv.core.Point;import org.opencv.core.Rect;import org.opencv.core.RotatedRect;import org.opencv.core.Size;import org.opencv.imgproc.Imgproc;import org.opencv.imgproc.Moments;
import java.util.ArrayList;import java.util.Collections;import java.util.Comparator;import java.util.List;import java.util.Locale;

/** Sector-constrained GMT marker localisation on the shared canonical rectified dial. */
final class GmtIndexAutoAnalyzer {
    private static final int[] HOURS={1,2,4,5,6,7,8,9,10,11};
    static final class Result {final QcModuleResult geometry;final Bitmap annotated;final String summary;final int localizedCount;Result(QcModuleResult g,Bitmap a,String s,int n){geometry=g;annotated=a;summary=s;localizedCount=n;}}
    private static final class Candidate {int hour;double x,y,r,score,axisDeg,axisConfidence;boolean elongated;}
    private GmtIndexAutoAnalyzer(){}

    static Result analyse(Bitmap watch,PerspectiveMasterRenderer.Pose pose){
        try(CanonicalGmtDial dial=CanonicalGmtDial.create(watch,pose)){
            if(dial==null)return unavailableForKnownDialFailure(watch,CanonicalGmtDial.lastFailureReason());
            return analyse(watch,dial);
        }catch(Throwable t){return unavailable(watch,"Index analysis unavailable: "+t.getClass().getSimpleName()+".");}
    }

    /** Use after a CanonicalGmtDial creation attempt has already failed. Never retries the warp. */
    static Result unavailableForKnownDialFailure(Bitmap watch,String reason){
        String why=reason==null||reason.trim().isEmpty()?"unknown failure":reason;
        return unavailable(watch,"Index analysis unavailable: corrected 12/3/6/9 inner dial-edge anchors could not create a canonical dial ("+why+").");
    }

    static Result analyse(Bitmap watch,CanonicalGmtDial dial){
        try{
            if(dial.rectification.confidence()==QcModuleResult.Confidence.LOW)return unavailable(watch,"Index analysis unavailable: rectification confidence is low. "+String.join("; ",dial.rectification.evidence()));
            List<Candidate> candidates=localize(dial);if(candidates.size()<7)return unavailable(watch,"Index analysis unavailable: fewer than seven reliable non-date/non-12 markers passed strict sector, shape and contrast QC checks.");
            double medianR=medianRadius(candidates),mad=madRadius(candidates,medianR);List<Candidate> consistent=new ArrayList<>();for(Candidate c:candidates)if(Math.abs(c.r-medianR)<=Math.max(.035,3.5*mad)&&GmtIndexPlausibility.plausibleAgainstRing(c.hour,c.x,c.y,medianR))consistent.add(c);
            if(consistent.size()<7)return unavailable(watch,"Index analysis unavailable: fewer than seven markers passed robust ring consistency.");
            List<IndexGeometryQcModule.MarkerObservation> observations=new ArrayList<>();for(Candidate c:consistent){double expectedR=Gmt126710IdealOverlay.markerCenterRadius(c.hour);if(c.elongated&&c.axisConfidence>=.70){double a=Math.toRadians(c.axisDeg),ax=Math.cos(a),ay=Math.sin(a),half=.035;observations.add(IndexGeometryQcModule.MarkerObservation.of(c.hour,c.x,c.y,c.x-ax*half,c.y-ay*half,c.x+ax*half,c.y+ay*half,expectedR));}else observations.add(IndexGeometryQcModule.MarkerObservation.positionOnly(c.hour,c.x,c.y,expectedR));}
            QcModuleResult measured=new IndexGeometryQcModule().measure(IndexGeometryQcModule.Input.rectified(0,0,1,observations,dial.rectification));
            return new Result(measured,annotate(watch,dial,consistent),summarize(measured,consistent.size()),consistent.size());
        }catch(Throwable t){return unavailable(watch,"Index analysis unavailable: "+t.getClass().getSimpleName()+".");}
    }

    private static List<Candidate> localize(CanonicalGmtDial dial){
        Mat blur=new Mat(),edges=new Mat(),binary=new Mat(),combined=new Mat(),hierarchy=new Mat();List<MatOfPoint> contours=new ArrayList<>();List<Candidate> all=new ArrayList<>();
        try{Imgproc.GaussianBlur(dial.gray,blur,new Size(5,5),0);Imgproc.Canny(blur,edges,45,130);Imgproc.adaptiveThreshold(blur,binary,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,41,-4);Core.bitwise_or(edges,binary,combined);Imgproc.findContours(combined,contours,hierarchy,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_SIMPLE);
            for(int hour:HOURS){Candidate best=null;for(MatOfPoint contour:contours){Candidate c=score(contour,dial.gray,hour);if(c!=null&&(best==null||c.score>best.score))best=c;}if(best!=null&&best.score>=.58)all.add(best);}return all;
        }finally{for(MatOfPoint c:contours)c.release();blur.release();edges.release();binary.release();combined.release();hierarchy.release();}}

    private static Candidate score(MatOfPoint contour,Mat gray,int hour){
        double area=Math.abs(Imgproc.contourArea(contour));if(area<150||area>18000)return null;Moments m=Imgproc.moments(contour);if(Math.abs(m.m00)<1e-6)return null;double px=m.m10/m.m00,py=m.m01/m.m00,x=(px-CanonicalGmtDial.CENTER)/CanonicalGmtDial.RADIUS,y=(py-CanonicalGmtDial.CENTER)/CanonicalGmtDial.RADIUS,r=Math.hypot(x,y);if(!GmtIndexPlausibility.plausibleCanonicalPosition(hour,x,y))return null;
        Rect box=Imgproc.boundingRect(contour);MatOfPoint2f perimeterPts=new MatOfPoint2f(contour.toArray());double perimeter=Imgproc.arcLength(perimeterPts,true);perimeterPts.release();double circularity=perimeter>0?4*Math.PI*area/(perimeter*perimeter):0;double aspect=Math.max(box.width,box.height)/(double)Math.max(1,Math.min(box.width,box.height));boolean elongated=hour==6||hour==9;double shape=elongated?clamp((aspect-1.15)/1.2):clamp(1-Math.abs(circularity-.72)/.55);double angleError=Math.abs(wrap(Math.toDegrees(Math.atan2(x,-y))-hour*30.0));double angle=clamp(1-angleError/7.0),expectedR=Gmt126710IdealOverlay.markerCenterRadius(hour),radial=clamp(1-Math.abs(r-expectedR)/.16),areaScore=clamp(area/1800.0)*clamp(1-area/18000.0);
        double inside=Core.mean(gray.submat(box)).val[0],outside=annulusMean(gray,px,py,Math.max(box.width,box.height)*.7,Math.max(box.width,box.height)*1.1);double contrast=clamp(Math.abs(inside-outside)/55.0);double compact=clamp(area/Math.max(1.0,box.area()));double total=.27*angle+.20*radial+.14*areaScore+.16*contrast+.10*compact+.13*shape;
        Candidate c=new Candidate();c.hour=hour;c.x=x;c.y=y;c.r=r;c.score=total;c.elongated=elongated;if(elongated){MatOfPoint2f f=new MatOfPoint2f(contour.toArray());RotatedRect rr=Imgproc.minAreaRect(f);f.release();double longAngle=rr.angle;if(rr.size.width<rr.size.height)longAngle+=90;c.axisDeg=longAngle;c.axisConfidence=shape*contrast;}return c;
    }
    private static double annulusMean(Mat g,double cx,double cy,double inner,double outer){double sum=0,n=0;for(int y=(int)(cy-outer);y<=cy+outer;y+=2)for(int x=(int)(cx-outer);x<=cx+outer;x+=2){if(x<0||y<0||x>=g.cols()||y>=g.rows())continue;double d=Math.hypot(x-cx,y-cy);if(d>=inner&&d<=outer){sum+=g.get(y,x)[0];n++;}}return n>0?sum/n:0;}

    /** Cyan axes are exact GMT angular geometry. Yellow bodies/radii are image-calibrated references. */
    private static Bitmap annotate(Bitmap watch,CanonicalGmtDial dial,List<Candidate> cs){
        Bitmap out=watch.copy(Bitmap.Config.ARGB_8888,true);
        Gmt126710IdealOverlay.draw(out,dial.pose,Gmt126710IdealOverlay.markerCenterRadius());
        Canvas canvas=new Canvas(out);float u=Math.max(1f,Math.min(out.getWidth(),out.getHeight())/900f);
        Paint actual=new Paint(Paint.ANTI_ALIAS_FLAG);actual.setColor(Color.MAGENTA);actual.setStyle(Paint.Style.STROKE);actual.setStrokeWidth(2.5f*u);
        Paint residual=new Paint(actual);residual.setStrokeWidth(1.5f*u);
        Paint text=new Paint(Paint.ANTI_ALIAS_FLAG);text.setColor(Color.WHITE);text.setStyle(Paint.Style.FILL);text.setTextSize(14*u);text.setFakeBoldText(true);
        for(Candidate c:cs){
            Point q=dial.sourcePoint(CanonicalGmtDial.CENTER+c.x*CanonicalGmtDial.RADIUS,CanonicalGmtDial.CENTER+c.y*CanonicalGmtDial.RADIUS);
            android.graphics.PointF ideal=Gmt126710IdealOverlay.idealMarker(dial.pose,c.hour,Gmt126710IdealOverlay.markerCenterRadius(c.hour));
            canvas.drawLine(ideal.x,ideal.y,(float)q.x,(float)q.y,residual);
            canvas.drawCircle((float)q.x,(float)q.y,6*u,actual);
            canvas.drawText(String.valueOf(c.hour),(float)q.x+8*u,(float)q.y-8*u,text);
        }
        return out;
    }

    static String summarize(QcModuleResult result,int count){
        if(result==null||result.measurements().isEmpty())return "REFERENCE GMT GEOMETRY\nNo reliable per-index measurements.";
        List<Ranked> tangential=new ArrayList<>(),radial=new ArrayList<>();for(int h:HOURS){RawMeasurement t=result.measurement(String.format(Locale.US,"index_%02d_tangential_offset_over_dial_radius",h)),r=result.measurement(String.format(Locale.US,"index_%02d_radial_offset_over_dial_radius",h));if(t!=null)tangential.add(new Ranked(h,t.value()));if(r!=null)radial.add(new Ranked(h,r.value()));}
        Comparator<Ranked> abs=(a,b)->Double.compare(Math.abs(b.value),Math.abs(a.value));Collections.sort(tangential,abs);Collections.sort(radial,abs);
        String wt=tangential.isEmpty()?"n/a":String.format(Locale.US,"%d %+.3f DR",tangential.get(0).hour,tangential.get(0).value),wr=radial.isEmpty()?"n/a":String.format(Locale.US,"%d %+.3f DR",radial.get(0).hour,radial.get(0).value);
        List<String> body=new ArrayList<>();for(int h:new int[]{6,9}){RawMeasurement x=result.measurement(String.format(Locale.US,"index_%02d_rotation_deg",h));if(x!=null&&GmtIndexPlausibility.bodyRotationUsable(h,x.value(),result.confidence()))body.add(String.format(Locale.US,"%d %+.2f°",h,x.value()));}
        return "REFERENCE GMT GEOMETRY · "+count+" QC markers passed strict contour checks · "+result.confidence().name().toLowerCase(Locale.US)+" rectification\nExact angular model: 12 hour axes at 30° · 60 minute axes at 6° · 3/9 and 12/6 exact diameters\nImage-calibrated centres: round "+String.format(Locale.US,"%.3f DR",Gmt126710IdealOverlay.markerCenterRadius(1))+" · 6/9 baton "+String.format(Locale.US,"%.3f DR",Gmt126710IdealOverlay.markerCenterRadius(6))+"\nLargest residual: tangential "+wt+" · reference radial delta "+wr+"\n6/9 body axis: "+(body.isEmpty()?"withheld because an elongated contour was not isolated confidently":String.join(" · ",body))+"\nCyan axes are mathematical; yellow body dimensions/radii are image-derived 126710 references; magenta is measured. The 12 outer-metal triangle is measured automatically.";
    }
    private static Result unavailable(Bitmap w,String m){return new Result(null,w==null?null:w.copy(Bitmap.Config.ARGB_8888,false),"REFERENCE GMT GEOMETRY\n"+m,0);}private static double medianRadius(List<Candidate> c){List<Double>x=new ArrayList<>();for(Candidate q:c)x.add(q.r);Collections.sort(x);return x.get(x.size()/2);}private static double madRadius(List<Candidate> c,double m){List<Double>x=new ArrayList<>();for(Candidate q:c)x.add(Math.abs(q.r-m));Collections.sort(x);return x.get(x.size()/2);}private static double wrap(double x){while(x>180)x-=360;while(x<=-180)x+=360;return x;}private static double clamp(double x){return Math.max(0,Math.min(1,x));}private static final class Ranked{final int hour;final double value;Ranked(int h,double v){hour=h;value=v;}}
}
