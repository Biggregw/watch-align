package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Test-only selective review layer for frozen Alpha90.
 *
 * Alpha90 remains authoritative for global pose and the projected genuine master. This class
 * runs only after Alpha90 has finished. It uses the already-projected yellow marker outline as a
 * local template and searches the raw photograph for the small translation/rotation that best
 * aligns that same shape to the physical marker boundary. Nothing measured here can feed back
 * into Alpha90 centre, scale, phase, perspective or rendering.
 *
 * The local fit deliberately avoids the older marker detectors. Those detectors can introduce
 * their own local-frame bias and can alternate between lume and outer-surround edges. Here the
 * candidate marker is isolated only as a bright connected component near the fixed master
 * location, then the master outline itself is fitted to that component boundary.
 */
final class Alpha90SelectiveMarkerReporter {
    enum Level { NORMAL, BORDERLINE, CLEAR_DIFFERENCE, UNASSESSABLE }

    static final class Feature {
        final int hour;
        final String label;
        final Level level;

        final double excursionPx;
        final double excursionOverDialR;
        final double centreShiftPx;
        final double rotationEdgePx;
        final double rotationDeg;

        final double fitDxOverR;
        final double fitDyOverR;
        final double fitRotationDeg;
        final double fitScorePx;
        final double fitSupport;
        final double majorHalfExtentOverR;
        final double dialRadiusPx;
        final double borderlineThresholdR;
        final double clearThresholdR;
        final String reason;

        Feature(int hour, Level level,
                double excursionPx, double excursionOverDialR,
                double centreShiftPx, double rotationEdgePx, double rotationDeg,
                double fitDxOverR, double fitDyOverR, double fitRotationDeg,
                double fitScorePx, double fitSupport, double majorHalfExtentOverR,
                double dialRadiusPx, double borderlineThresholdR, double clearThresholdR,
                String reason) {
            this.hour=hour;
            this.label=hour+" o'clock";
            this.level=level;
            this.excursionPx=excursionPx;
            this.excursionOverDialR=excursionOverDialR;
            this.centreShiftPx=centreShiftPx;
            this.rotationEdgePx=rotationEdgePx;
            this.rotationDeg=rotationDeg;
            this.fitDxOverR=fitDxOverR;
            this.fitDyOverR=fitDyOverR;
            this.fitRotationDeg=fitRotationDeg;
            this.fitScorePx=fitScorePx;
            this.fitSupport=fitSupport;
            this.majorHalfExtentOverR=majorHalfExtentOverR;
            this.dialRadiusPx=dialRadiusPx;
            this.borderlineThresholdR=borderlineThresholdR;
            this.clearThresholdR=clearThresholdR;
            this.reason=reason==null?"":reason;
        }

        boolean showCloseUp(){
            return level==Level.BORDERLINE||level==Level.CLEAR_DIFFERENCE;
        }

        boolean rawFitUsable(){
            return level!=Level.UNASSESSABLE
                    &&Double.isFinite(fitDxOverR)&&Double.isFinite(fitDyOverR)
                    &&Double.isFinite(fitRotationDeg)&&Double.isFinite(fitScorePx)
                    &&fitSupport>=MIN_CALIBRATION_SUPPORT
                    &&fitScorePx<=MAX_CALIBRATION_SCORE_PX;
        }
    }

    static final class Report {
        final List<Feature> features;
        Report(List<Feature> f){features=f;}
        Feature atHour(int h){for(Feature f:features)if(f.hour==h)return f;return null;}
        int shown(){int n=0;for(Feature f:features)if(f.showCloseUp())n++;return n;}
        int unassessable(){int n=0;for(Feature f:features)if(f.level==Level.UNASSESSABLE)n++;return n;}
    }

    static final class GenuineEnvelope {
        final Map<Integer,HourEnvelope> byHour=new HashMap<>();
        int calibratedHours(){return byHour.size();}
    }

    private static final class HourEnvelope {
        final double dxR,dyR,rotationDeg,borderlineR,clearR;
        HourEnvelope(double dxR,double dyR,double rotationDeg,double borderlineR,double clearR){
            this.dxR=dxR;this.dyR=dyR;this.rotationDeg=rotationDeg;
            this.borderlineR=borderlineR;this.clearR=clearR;
        }
    }

    static final double MIN_BORDERLINE_R=0.0050;
    static final double GENUINE_MARGIN_R=0.0020;
    static final double CLEAR_MARGIN_R=0.0040;
    static final double MIN_CLEAR_R=0.0100;

    private static final double REFERENCE_MIN_R=0.55;
    private static final double REFERENCE_MAX_R=0.915;
    private static final double REFERENCE_HALF_SECTOR_DEG=13.0;
    private static final int MIN_REFERENCE_PIXELS=24;
    private static final int MAX_TEMPLATE_POINTS=180;
    private static final double SEARCH_TRANSLATION_R=0.040;
    private static final double MAX_SEARCH_PX=10.0;
    private static final double MIN_SEARCH_PX=4.0;
    private static final double SEARCH_ROTATION_DEG=6.0;
    private static final double COARSE_ROTATION_STEP_DEG=1.0;
    private static final double FINE_TRANSLATION_STEP_PX=0.5;
    private static final double FINE_ROTATION_STEP_DEG=0.25;
    private static final double DISTANCE_CLIP_PX=4.0;
    private static final double SUPPORT_DISTANCE_PX=1.5;
    private static final double MIN_FIT_SUPPORT=0.50;
    private static final double MAX_FIT_SCORE_PX=2.75;
    private static final double MIN_CALIBRATION_SUPPORT=0.70;
    private static final double MAX_CALIBRATION_SCORE_PX=2.25;
    private static final double MAX_COMPONENT_CENTRE_R=0.12;
    private static final int MIN_COMPONENT_AREA_PX=20;

    private Alpha90SelectiveMarkerReporter(){}

    static Report analyse(Bitmap watch, AutomaticDialOverlay.Result alpha) {
        if(alpha==null||!alpha.valid)return unavailable("Alpha90 overlay unavailable");
        return analyse(watch,alpha.overlay,alpha.dialCx,alpha.dialCy,alpha.dialRadius);
    }

    static Report analyse(Bitmap watch, Bitmap overlay, double dialCx, double dialCy, double dialRadius) {
        if(watch==null||overlay==null||!(dialRadius>20))return unavailable("Alpha90 overlay unavailable");
        Map<Integer,List<double[]>> refs=referencePixelsByHour(overlay,dialCx,dialCy,dialRadius);
        List<Feature> out=new ArrayList<>();

        Mat rgba=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(watch,rgba);
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);

            for(int hour=1;hour<=12;hour++){
                if(hour==3){
                    out.add(unassessable(hour,dialRadius,"no applied marker exists at 3 in the frozen master"));
                    continue;
                }
                List<double[]> refPts=refs.get(hour);
                if(refPts==null||refPts.size()<MIN_REFERENCE_PIXELS){
                    out.add(unassessable(hour,dialRadius,"projected genuine marker outline was not isolated"));
                    continue;
                }
                RefShape ref=RefShape.build(refPts,dialRadius,watch.getWidth(),watch.getHeight());
                if(ref==null){
                    out.add(unassessable(hour,dialRadius,"projected genuine marker geometry was degenerate"));
                    continue;
                }

                CandidateBoundary candidate=CandidateBoundary.build(gray,ref,dialRadius);
                if(candidate==null){
                    out.add(unassessable(hour,dialRadius,"physical marker boundary could not be isolated near the fixed master"));
                    continue;
                }

                boolean allowRotation=hour==12||hour==6||hour==9;
                LocalFit fit=fit(ref,candidate,dialRadius,allowRotation);
                if(fit==null){
                    out.add(unassessable(hour,dialRadius,"local marker template fit failed"));
                    continue;
                }
                if(fit.hitLimit){
                    out.add(unassessable(hour,dialRadius,"local marker fit reached the bounded search limit"));
                    continue;
                }
                if(fit.support<MIN_FIT_SUPPORT||fit.meanDistancePx>MAX_FIT_SCORE_PX){
                    out.add(unassessable(hour,dialRadius,String.format(Locale.US,
                            "local marker boundary fit was weak (support %.0f%%, score %.2f px)",
                            100.0*fit.support,fit.meanDistancePx)));
                    continue;
                }

                double centre=Math.hypot(fit.dx,fit.dy);
                double rotationEdge=allowRotation
                        ?Math.abs(Math.sin(Math.toRadians(fit.rotationDeg)))*ref.majorHalfExtent:0.0;
                double rawExcursion=Math.max(centre,rotationEdge);
                out.add(new Feature(hour,Level.NORMAL,
                        rawExcursion,rawExcursion/dialRadius,
                        centre,rotationEdge,fit.rotationDeg,
                        fit.dx/dialRadius,fit.dy/dialRadius,fit.rotationDeg,
                        fit.meanDistancePx,fit.support,ref.majorHalfExtent/dialRadius,
                        dialRadius,Double.NaN,Double.NaN,
                        String.format(Locale.US,"raw local-template fit: score %.2f px, support %.0f%%",
                                fit.meanDistancePx,100.0*fit.support)));
            }
        }catch(Throwable t){
            return unavailable("selective reporter failed: "+t.getClass().getSimpleName());
        }finally{
            gray.release();rgba.release();
        }
        return new Report(out);
    }

    static GenuineEnvelope calibrate(List<Report> genuineReports){
        GenuineEnvelope out=new GenuineEnvelope();
        if(genuineReports==null)return out;
        for(int hour=1;hour<=12;hour++){
            if(hour==3)continue;
            List<Feature> good=new ArrayList<>();
            for(Report r:genuineReports){
                if(r==null)continue;
                Feature f=r.atHour(hour);
                if(f!=null&&f.rawFitUsable())good.add(f);
            }
            if(good.size()<2)continue;

            double[] dx=new double[good.size()],dy=new double[good.size()],rot=new double[good.size()];
            for(int i=0;i<good.size();i++){
                dx[i]=good.get(i).fitDxOverR;dy[i]=good.get(i).fitDyOverR;rot[i]=good.get(i).fitRotationDeg;
            }
            double mdx=median(dx),mdy=median(dy),mrot=median(rot);
            double maxGenuineResidual=0.0;
            for(Feature f:good){
                double centre=Math.hypot(f.fitDxOverR-mdx,f.fitDyOverR-mdy);
                double dRot=wrapAxisDeg(f.fitRotationDeg-mrot);
                double rotEdge=Math.abs(Math.sin(Math.toRadians(dRot)))*f.majorHalfExtentOverR;
                maxGenuineResidual=Math.max(maxGenuineResidual,Math.max(centre,rotEdge));
            }
            double borderline=Math.max(MIN_BORDERLINE_R,maxGenuineResidual+GENUINE_MARGIN_R);
            double clear=Math.max(MIN_CLEAR_R,borderline+CLEAR_MARGIN_R);
            out.byHour.put(hour,new HourEnvelope(mdx,mdy,mrot,borderline,clear));
        }
        return out;
    }

    static Report applyEnvelope(Report raw,GenuineEnvelope env){
        if(raw==null)return unavailable("local-template report unavailable");
        List<Feature> out=new ArrayList<>();
        for(Feature f:raw.features){
            if(f.level==Level.UNASSESSABLE){out.add(f);continue;}
            HourEnvelope e=env==null?null:env.byHour.get(f.hour);
            if(e==null){
                out.add(unassessable(f.hour,f.dialRadiusPx,"no reliable genuine local envelope is available for this marker position"));
                continue;
            }
            double centreR=Math.hypot(f.fitDxOverR-e.dxR,f.fitDyOverR-e.dyR);
            double dRot=wrapAxisDeg(f.fitRotationDeg-e.rotationDeg);
            double rotEdgeR=Math.abs(Math.sin(Math.toRadians(dRot)))*f.majorHalfExtentOverR;
            double residualR=Math.max(centreR,rotEdgeR);
            Level level=residualR>=e.clearR?Level.CLEAR_DIFFERENCE
                    :residualR>=e.borderlineR?Level.BORDERLINE:Level.NORMAL;
            out.add(new Feature(f.hour,level,
                    residualR*f.dialRadiusPx,residualR,
                    centreR*f.dialRadiusPx,rotEdgeR*f.dialRadiusPx,dRot,
                    f.fitDxOverR,f.fitDyOverR,f.fitRotationDeg,
                    f.fitScorePx,f.fitSupport,f.majorHalfExtentOverR,
                    f.dialRadiusPx,e.borderlineR,e.clearR,
                    String.format(Locale.US,
                            "local fit score %.2f px, support %.0f%%; genuine review envelope %.4fR / %.4fR",
                            f.fitScorePx,100.0*f.fitSupport,e.borderlineR,e.clearR)));
        }
        return new Report(out);
    }

    private static final class RefShape {
        final int x0,y0,w,h;
        final double localCx,localCy,majorHalfExtent;
        final double[][] points;
        RefShape(int x0,int y0,int w,int h,double cx,double cy,double major,double[][] points){
            this.x0=x0;this.y0=y0;this.w=w;this.h=h;
            localCx=cx;localCy=cy;majorHalfExtent=major;this.points=points;
        }

        static RefShape build(List<double[]> src,double dialRadius,int imageW,int imageH){
            if(src==null||src.size()<MIN_REFERENCE_PIXELS)return null;
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY;
            double minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY;
            double cx=0,cy=0;int n=0;
            for(double[] p:src){
                if(p==null||p.length<2)continue;
                minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);
                minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);
                cx+=p[0];cy+=p[1];n++;
            }
            if(n<MIN_REFERENCE_PIXELS)return null;
            cx/=n;cy/=n;
            int pad=Math.max(6,(int)Math.round(0.06*dialRadius));
            int x0=clamp((int)Math.floor(minX)-pad,0,imageW-1);
            int y0=clamp((int)Math.floor(minY)-pad,0,imageH-1);
            int x1=clamp((int)Math.ceil(maxX)+pad+1,x0+1,imageW);
            int y1=clamp((int)Math.ceil(maxY)+pad+1,y0+1,imageH);
            int w=x1-x0,h=y1-y0;

            double xx=0,xy=0,yy=0;
            for(double[] p:src){
                double dx=p[0]-cx,dy=p[1]-cy;
                xx+=dx*dx;xy+=dx*dy;yy+=dy*dy;
            }
            xx/=n;xy/=n;yy/=n;
            double a=0.5*Math.atan2(2.0*xy,xx-yy),ca=Math.cos(a),sa=Math.sin(a);
            double major=0;
            for(double[] p:src){
                double dx=p[0]-cx,dy=p[1]-cy;
                major=Math.max(major,Math.abs(dx*ca+dy*sa));
            }
            if(!(major>1))return null;

            int keep=Math.min(MAX_TEMPLATE_POINTS,n);
            double[][] pts=new double[keep][2];
            for(int i=0;i<keep;i++){
                int j=keep==n?i:(int)Math.floor(i*(n-1.0)/Math.max(1,keep-1));
                double[] p=src.get(j);
                pts[i][0]=p[0]-x0;pts[i][1]=p[1]-y0;
            }
            return new RefShape(x0,y0,w,h,cx-x0,cy-y0,major,pts);
        }
    }

    private static final class CandidateBoundary {
        final int w,h;
        final float[] distance;
        CandidateBoundary(int w,int h,float[] d){this.w=w;this.h=h;distance=d;}

        static CandidateBoundary build(Mat gray,RefShape ref,double dialRadius){
            Mat crop=new Mat(),blur=new Mat(),binary=new Mat(),opened=new Mat();
            Mat labels=new Mat(),stats=new Mat(),centroids=new Mat(),component=new Mat();
            Mat boundary=new Mat(),inverse=new Mat(),dist=new Mat();
            Mat kernel=null;
            try{
                crop=new Mat(gray,new Rect(ref.x0,ref.y0,ref.w,ref.h));
                Imgproc.GaussianBlur(crop,blur,new Size(3,3),0.8);
                Imgproc.threshold(blur,binary,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
                kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3));
                Imgproc.morphologyEx(binary,opened,Imgproc.MORPH_OPEN,kernel);

                int count=Imgproc.connectedComponentsWithStats(opened,labels,stats,centroids,8,CvType.CV_32S);
                int best=-1;double bestD=Double.POSITIVE_INFINITY;
                for(int i=1;i<count;i++){
                    double area=stats.get(i,Imgproc.CC_STAT_AREA)[0];
                    if(area<MIN_COMPONENT_AREA_PX)continue;
                    double x=centroids.get(i,0)[0],y=centroids.get(i,1)[0];
                    double d=Math.hypot(x-ref.localCx,y-ref.localCy);
                    if(d<bestD){bestD=d;best=i;}
                }
                if(best<0||bestD>MAX_COMPONENT_CENTRE_R*dialRadius)return null;

                Core.compare(labels,new org.opencv.core.Scalar(best),component,Core.CMP_EQ);
                Imgproc.morphologyEx(component,boundary,Imgproc.MORPH_GRADIENT,kernel);
                Core.bitwise_not(boundary,inverse);
                Imgproc.distanceTransform(inverse,dist,Imgproc.DIST_L2,3);
                float[] values=new float[ref.w*ref.h];
                dist.get(0,0,values);
                return new CandidateBoundary(ref.w,ref.h,values);
            }finally{
                if(kernel!=null)kernel.release();
                dist.release();inverse.release();boundary.release();component.release();
                centroids.release();stats.release();labels.release();opened.release();binary.release();blur.release();crop.release();
            }
        }
    }

    private static final class LocalFit {
        final double dx,dy,rotationDeg,meanDistancePx,support;
        final boolean hitLimit;
        LocalFit(double dx,double dy,double r,double mean,double support,boolean hit){
            this.dx=dx;this.dy=dy;rotationDeg=r;meanDistancePx=mean;this.support=support;hitLimit=hit;
        }
    }

    private static final class Score {
        final double mean,support,objective;
        Score(double mean,double support){this.mean=mean;this.support=support;objective=mean-0.35*support;}
    }

    private static LocalFit fit(RefShape ref,CandidateBoundary candidate,double dialRadius,boolean allowRotation){
        double maxShift=clampDouble(SEARCH_TRANSLATION_R*dialRadius,MIN_SEARCH_PX,MAX_SEARCH_PX);
        double bestObj=Double.POSITIVE_INFINITY,bestDx=0,bestDy=0,bestRot=0,bestMean=Double.POSITIVE_INFINITY,bestSupport=0;

        for(double dx=-maxShift;dx<=maxShift+1e-9;dx+=1.0){
            for(double dy=-maxShift;dy<=maxShift+1e-9;dy+=1.0){
                if(allowRotation){
                    for(double rot=-SEARCH_ROTATION_DEG;rot<=SEARCH_ROTATION_DEG+1e-9;rot+=COARSE_ROTATION_STEP_DEG){
                        Score s=score(ref,candidate,dx,dy,rot);
                        if(s.objective<bestObj){bestObj=s.objective;bestDx=dx;bestDy=dy;bestRot=rot;bestMean=s.mean;bestSupport=s.support;}
                    }
                }else{
                    Score s=score(ref,candidate,dx,dy,0);
                    if(s.objective<bestObj){bestObj=s.objective;bestDx=dx;bestDy=dy;bestRot=0;bestMean=s.mean;bestSupport=s.support;}
                }
            }
        }

        double coarseDx=bestDx,coarseDy=bestDy,coarseRot=bestRot;
        bestObj=Double.POSITIVE_INFINITY;
        for(double dx=coarseDx-1.0;dx<=coarseDx+1.0+1e-9;dx+=FINE_TRANSLATION_STEP_PX){
            for(double dy=coarseDy-1.0;dy<=coarseDy+1.0+1e-9;dy+=FINE_TRANSLATION_STEP_PX){
                if(allowRotation){
                    for(double rot=coarseRot-1.0;rot<=coarseRot+1.0+1e-9;rot+=FINE_ROTATION_STEP_DEG){
                        Score s=score(ref,candidate,dx,dy,rot);
                        if(s.objective<bestObj){bestObj=s.objective;bestDx=dx;bestDy=dy;bestRot=rot;bestMean=s.mean;bestSupport=s.support;}
                    }
                }else{
                    Score s=score(ref,candidate,dx,dy,0);
                    if(s.objective<bestObj){bestObj=s.objective;bestDx=dx;bestDy=dy;bestRot=0;bestMean=s.mean;bestSupport=s.support;}
                }
            }
        }

        boolean hit=Math.abs(bestDx)>=maxShift-0.25||Math.abs(bestDy)>=maxShift-0.25
                ||(allowRotation&&Math.abs(bestRot)>=SEARCH_ROTATION_DEG+0.75);
        return new LocalFit(bestDx,bestDy,bestRot,bestMean,bestSupport,hit);
    }

    private static Score score(RefShape ref,CandidateBoundary c,double dx,double dy,double rotationDeg){
        double a=Math.toRadians(rotationDeg),ca=Math.cos(a),sa=Math.sin(a);
        double sum=0;int supported=0,n=0;
        for(double[] p:ref.points){
            double qx=p[0]-ref.localCx,qy=p[1]-ref.localCy;
            double x=ref.localCx+ca*qx-sa*qy+dx;
            double y=ref.localCy+sa*qx+ca*qy+dy;
            double d=sample(c,x,y);
            if(!Double.isFinite(d))d=DISTANCE_CLIP_PX;
            sum+=Math.min(DISTANCE_CLIP_PX,d);
            if(d<=SUPPORT_DISTANCE_PX)supported++;
            n++;
        }
        if(n==0)return new Score(DISTANCE_CLIP_PX,0);
        return new Score(sum/n,supported/(double)n);
    }

    private static double sample(CandidateBoundary c,double x,double y){
        if(x<0||y<0||x>=c.w-1||y>=c.h-1)return DISTANCE_CLIP_PX;
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);
        double fx=x-x0,fy=y-y0;
        int i00=y0*c.w+x0,i01=i00+1,i10=i00+c.w,i11=i10+1;
        return c.distance[i00]*(1-fx)*(1-fy)+c.distance[i01]*fx*(1-fy)
                +c.distance[i10]*(1-fx)*fy+c.distance[i11]*fx*fy;
    }

    private static Map<Integer,List<double[]>> referencePixelsByHour(Bitmap overlay,double dialCx,double dialCy,double dialRadius){
        Map<Integer,List<double[]>> out=new HashMap<>();
        for(int h=1;h<=12;h++)out.put(h,new ArrayList<double[]>());
        int xmin=clamp((int)Math.floor(dialCx-dialRadius),0,overlay.getWidth()-1);
        int xmax=clamp((int)Math.ceil(dialCx+dialRadius),0,overlay.getWidth()-1);
        int ymin=clamp((int)Math.floor(dialCy-dialRadius),0,overlay.getHeight()-1);
        int ymax=clamp((int)Math.ceil(dialCy+dialRadius),0,overlay.getHeight()-1);
        double maxDa=Math.toRadians(REFERENCE_HALF_SECTOR_DEG);
        for(int y=ymin;y<=ymax;y++)for(int x=xmin;x<=xmax;x++){
            int color=overlay.getPixel(x,y);
            if(Color.alpha(color)<80||Color.red(color)<180||Color.green(color)<180||Color.blue(color)>140)continue;
            double dx=x-dialCx,dy=y-dialCy;
            double rr=Math.hypot(dx,dy)/dialRadius;
            if(rr<REFERENCE_MIN_R||rr>REFERENCE_MAX_R)continue;
            double angle=Math.atan2(dy,dx);
            int hour=nearestHour(angle);
            double expected=Math.toRadians(hour*30.0-90.0);
            if(Math.abs(wrapPi(angle-expected))>maxDa)continue;
            out.get(hour).add(new double[]{x,y});
        }
        return out;
    }

    private static int nearestHour(double angle){
        int slot=(int)Math.round((Math.toDegrees(angle)+90.0)/30.0);
        slot=((slot%12)+12)%12;
        return slot==0?12:slot;
    }

    private static Report unavailable(String why){
        List<Feature> out=new ArrayList<>();
        for(int h=1;h<=12;h++)out.add(unassessable(h,Double.NaN,why));
        return new Report(out);
    }

    private static Feature unassessable(int hour,double dialRadius,String why){
        return new Feature(hour,Level.UNASSESSABLE,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,
                Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,Double.NaN,
                dialRadius,Double.NaN,Double.NaN,why);
    }

    private static double median(double[] v){
        double[] c=v.clone();Arrays.sort(c);
        int n=c.length;return (n&1)==1?c[n/2]:0.5*(c[n/2-1]+c[n/2]);
    }

    private static double wrapAxisDeg(double d){
        while(d>90)d-=180;while(d<=-90)d+=180;return d;
    }
    private static double wrapPi(double x){while(x>Math.PI)x-=2*Math.PI;while(x<=-Math.PI)x+=2*Math.PI;return x;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static double clampDouble(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
