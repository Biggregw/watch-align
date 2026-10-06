package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alpha94 measurement-only layer.
 *
 * The minute-lattice pose is already frozen before this class runs. Nothing measured
 * here can alter H. This first Android port intentionally measures only the reliable
 * research subset: round-marker centres, 6/9 baton centres + rotation, and a common
 * marker-ring translation/scale/rotation. There are no QC tolerances or verdicts.
 */
final class Alpha94MarkerMeasurement {
    static final int[] HOURS={1,2,4,5,6,7,8,9,10,11};
    static final int[] ROUND_HOURS={1,2,4,5,7,8,10,11};

    static final class Marker {
        final int hour;
        final String kind;
        boolean usable;
        String reason="";
        double rawDxPx=Double.NaN,rawDyPx=Double.NaN,rawOffsetPx=Double.NaN;
        double radialPx=Double.NaN,tangentialPx=Double.NaN;
        double rotationDeg=Double.NaN;
        double localOffsetPx=Double.NaN,localRadialPx=Double.NaN,localTangentialPx=Double.NaN;
        double fitScorePx=Double.NaN,fitSupport=Double.NaN;
        double canonDx=Double.NaN,canonDy=Double.NaN;
        Marker(int hour,String kind){this.hour=hour;this.kind=kind;}
    }

    static final class Ring {
        boolean usable;
        int n;
        double shiftXPx=Double.NaN,shiftYPx=Double.NaN,shiftPx=Double.NaN;
        double scalePct=Double.NaN,rotationDeg=Double.NaN;
    }

    static final class Report {
        final List<Marker> markers;
        final Ring ring;
        final double dialRadiusPx;
        Report(List<Marker> m,Ring r,double dialRadiusPx){markers=m;ring=r;this.dialRadiusPx=dialRadiusPx;}

        Marker atHour(int h){for(Marker m:markers)if(m.hour==h)return m;return null;}

        String compactSummary(){
            StringBuilder s=new StringBuilder();
            if(ring!=null&&ring.usable){
                s.append(String.format(Locale.US,"Ring: shift %.2f px · scale %+.2f%% · rot %+.2f°",
                        ring.shiftPx,ring.scalePct,ring.rotationDeg));
            }else s.append("Ring: insufficient clean markers");
            Marker m6=atHour(6),m9=atHour(9);
            s.append("\n").append(markerLine(m6));
            s.append("\n").append(markerLine(m9));
            int usableRounds=0;double maxLocal=Double.NaN;
            for(Marker m:markers)if("round".equals(m.kind)&&m.usable){
                usableRounds++;
                if(!Double.isFinite(maxLocal)||m.localOffsetPx>maxLocal)maxLocal=m.localOffsetPx;
            }
            s.append("\nRounds: ").append(usableRounds).append("/8 measured");
            if(Double.isFinite(maxLocal))s.append(String.format(Locale.US," · max local %.2f px",maxLocal));
            return s.toString();
        }

        String detailedSummary(){
            StringBuilder s=new StringBuilder(compactSummary());
            s.append("\n\nMeasurement only — no pass/fail thresholds.");
            for(Marker m:markers){
                s.append("\n").append(m.hour).append(": ");
                if(!m.usable){s.append("OCCLUDED / INSUFFICIENT CLEAN EDGE");continue;}
                s.append(String.format(Locale.US,"raw %.2f px · radial %+.2f · tang %+.2f",
                        m.rawOffsetPx,m.radialPx,m.tangentialPx));
                if(Double.isFinite(m.localOffsetPx))
                    s.append(String.format(Locale.US," · local %.2f",m.localOffsetPx));
                if("baton".equals(m.kind)&&Double.isFinite(m.rotationDeg))
                    s.append(String.format(Locale.US," · rot %+.2f°",m.rotationDeg));
            }
            return s.toString();
        }

        private static String markerLine(Marker m){
            if(m==null)return "marker unavailable";
            if(!m.usable)return m.hour+": OCCLUDED / INSUFFICIENT CLEAN EDGE";
            return String.format(Locale.US,"%d: raw %.2f px · local %.2f · rot %+.2f°",
                    m.hour,m.rawOffsetPx,m.localOffsetPx,m.rotationDeg);
        }
    }

    private static final double SEARCH_R=0.040;
    private static final double MIN_SEARCH_PX=4.0,MAX_SEARCH_PX=12.0;
    private static final double SEARCH_ROT_DEG=5.0;
    private static final double SUPPORT_DISTANCE_PX=1.5,DISTANCE_CLIP_PX=4.0;
    private static final double MIN_SUPPORT=0.70,MAX_SCORE_PX=2.25;
    private static final int MIN_COMPONENT_AREA=18;

    private Alpha94MarkerMeasurement(){}

    static Report analyse(Bitmap watch,double[] H){
        List<Marker> out=new ArrayList<>();
        double rpx=equivalentDialRadiusPx(H);
        if(watch==null||H==null||H.length<9||!(rpx>20)){
            for(int h:HOURS){Marker m=new Marker(h,isRound(h)?"round":"baton");m.reason="pose unavailable";out.add(m);}
            return new Report(out,new Ring(),rpx);
        }

        Mat rgba=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(watch,rgba);
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);

            for(int hour:HOURS){
                String kind=isRound(hour)?"round":"baton";
                Marker m=new Marker(hour,kind);
                Point cp=masterPoint(hour);
                List<Point> refImage=projectedOutline(hour,H);
                if(refImage.size()<20){m.reason="projected master unavailable";out.add(m);continue;}
                RefShape ref=RefShape.build(refImage,localScalePxPerR(H,cp),watch.getWidth(),watch.getHeight());
                if(ref==null){m.reason="projected master degenerate";out.add(m);continue;}
                CandidateBoundary cand=CandidateBoundary.build(gray,ref);
                if(cand==null){m.reason="marker boundary not isolated";out.add(m);continue;}

                LocalFit fit=fit(ref,cand,localScalePxPerR(H,cp),!isRound(hour));
                if(fit==null||fit.hitLimit||fit.support<MIN_SUPPORT||fit.meanDistancePx>MAX_SCORE_PX||cand.componentBoxRatio>2.8){
                    m.reason=fit==null?"local fit failed":"weak/occluded marker edge";
                    if(fit!=null){m.fitScorePx=fit.meanDistancePx;m.fitSupport=fit.support;}
                    out.add(m);continue;
                }

                double[] dc=imageShiftToCanonical(H,cp,fit.dx,fit.dy);
                if(dc==null){m.reason="local pose Jacobian unavailable";out.add(m);continue;}
                m.usable=true;m.fitScorePx=fit.meanDistancePx;m.fitSupport=fit.support;
                m.rawDxPx=fit.dx;m.rawDyPx=fit.dy;m.rawOffsetPx=Math.hypot(fit.dx,fit.dy);
                m.canonDx=dc[0];m.canonDy=dc[1];

                double a=Math.toRadians(hour*30.0);
                double erx=Math.sin(a),ery=-Math.cos(a),etx=Math.cos(a),ety=Math.sin(a);
                m.radialPx=(dc[0]*erx+dc[1]*ery)*rpx;
                m.tangentialPx=(dc[0]*etx+dc[1]*ety)*rpx;
                m.rotationDeg=isRound(hour)?0.0:canonicalRotationFromImage(H,cp,erx,ery,fit.rotationDeg);
                out.add(m);
            }
        }catch(Throwable ignored){
            for(Marker m:out)if(!m.usable&&m.reason.isEmpty())m.reason="measurement failed";
        }finally{
            gray.release();rgba.release();
        }

        Ring ring=fitRing(out,H,rpx);
        return new Report(out,ring,rpx);
    }

    private static Ring fitRing(List<Marker> markers,double[] H,double rpx){
        Ring ring=new Ring();
        List<Marker> ms=new ArrayList<>();
        for(Marker m:markers)if(m.usable)ms.add(m);
        if(ms.size()<5)return ring;

        double[] x={0,0,0,0};
        double[] w=new double[ms.size()*2];
        for(int i=0;i<w.length;i++)w[i]=1.0;
        for(int iter=0;iter<10;iter++){
            double[][] N=new double[4][4];double[] b=new double[4];
            for(int i=0;i<ms.size();i++){
                Marker m=ms.get(i);Point p=masterPoint(m.hour);
                double[][] rows={{1,0,p.x,-p.y},{0,1,p.y,p.x}};
                double[] obs={m.canonDx,m.canonDy};
                for(int rr=0;rr<2;rr++){
                    double ww=w[2*i+rr];
                    for(int a=0;a<4;a++){
                        b[a]+=rows[rr][a]*obs[rr]*ww;
                        for(int c=0;c<4;c++)N[a][c]+=rows[rr][a]*rows[rr][c]*ww;
                    }
                }
            }
            double[] sol=solve4(N,b);if(sol==null)return ring;x=sol;
            for(int i=0;i<ms.size();i++){
                Marker m=ms.get(i);Point p=masterPoint(m.hour);
                double mdx=x[0]+x[2]*p.x-x[3]*p.y;
                double mdy=x[1]+x[2]*p.y+x[3]*p.x;
                double rx=(m.canonDx-mdx)*rpx,ry=(m.canonDy-mdy)*rpx;
                double mag=Math.hypot(rx,ry),hw=mag<=1.0?1.0:1.0/Math.max(mag,1e-9);
                w[2*i]=w[2*i+1]=hw;
            }
        }

        ring.usable=true;ring.n=ms.size();ring.scalePct=100*x[2];ring.rotationDeg=Math.toDegrees(x[3]);
        double[][] J=jacobian(H,new Point(0,0));
        if(J!=null){
            ring.shiftXPx=J[0][0]*x[0]+J[0][1]*x[1];
            ring.shiftYPx=J[1][0]*x[0]+J[1][1]*x[1];
            ring.shiftPx=Math.hypot(ring.shiftXPx,ring.shiftYPx);
        }

        for(Marker m:ms){
            Point p=masterPoint(m.hour);
            double mdx=x[0]+x[2]*p.x-x[3]*p.y;
            double mdy=x[1]+x[2]*p.y+x[3]*p.x;
            double lx=m.canonDx-mdx,ly=m.canonDy-mdy;
            double a=Math.toRadians(m.hour*30.0);
            double erx=Math.sin(a),ery=-Math.cos(a),etx=Math.cos(a),ety=Math.sin(a);
            m.localOffsetPx=Math.hypot(lx,ly)*rpx;
            m.localRadialPx=(lx*erx+ly*ery)*rpx;
            m.localTangentialPx=(lx*etx+ly*ety)*rpx;
        }
        return ring;
    }

    private static final class RefShape {
        final int x0,y0,w,h;final double cx,cy,major;final double[][] pts;
        RefShape(int x0,int y0,int w,int h,double cx,double cy,double major,double[][] pts){
            this.x0=x0;this.y0=y0;this.w=w;this.h=h;this.cx=cx;this.cy=cy;this.major=major;this.pts=pts;
        }
        static RefShape build(List<Point> src,double scale,int imageW,int imageH){
            if(src==null||src.size()<12)return null;
            double minX=Double.POSITIVE_INFINITY,maxX=Double.NEGATIVE_INFINITY,minY=Double.POSITIVE_INFINITY,maxY=Double.NEGATIVE_INFINITY,cx=0,cy=0;
            for(Point p:src){minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);cx+=p.x;cy+=p.y;}
            cx/=src.size();cy/=src.size();
            int pad=Math.max(6,(int)Math.round(0.05*scale));
            int x0=clamp((int)Math.floor(minX)-pad,0,imageW-1),y0=clamp((int)Math.floor(minY)-pad,0,imageH-1);
            int x1=clamp((int)Math.ceil(maxX)+pad+1,x0+1,imageW),y1=clamp((int)Math.ceil(maxY)+pad+1,y0+1,imageH);
            double major=0;for(Point p:src)major=Math.max(major,Math.hypot(p.x-cx,p.y-cy));
            double[][] pts=new double[src.size()][2];
            for(int i=0;i<src.size();i++){pts[i][0]=src.get(i).x-x0;pts[i][1]=src.get(i).y-y0;}
            return new RefShape(x0,y0,x1-x0,y1-y0,cx-x0,cy-y0,major,pts);
        }
    }

    private static final class CandidateBoundary {
        final int w,h;final float[] distance;final double componentBoxRatio;
        CandidateBoundary(int w,int h,float[] d,double boxRatio){this.w=w;this.h=h;distance=d;componentBoxRatio=boxRatio;}
        static CandidateBoundary build(Mat gray,RefShape ref){
            Mat crop=new Mat(),blur=new Mat(),binary=new Mat(),opened=new Mat(),labels=new Mat(),stats=new Mat(),centroids=new Mat();
            Mat component=new Mat(),boundary=new Mat(),inverse=new Mat(),dist=new Mat(),kernel=null;
            try{
                crop=new Mat(gray,new Rect(ref.x0,ref.y0,ref.w,ref.h));
                Imgproc.GaussianBlur(crop,blur,new Size(3,3),0.8);
                Imgproc.threshold(blur,binary,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
                kernel=Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,new Size(3,3));
                Imgproc.morphologyEx(binary,opened,Imgproc.MORPH_OPEN,kernel);
                int count=Imgproc.connectedComponentsWithStats(opened,labels,stats,centroids,8,CvType.CV_32S);
                int best=-1;double bestD=Double.POSITIVE_INFINITY;
                for(int i=1;i<count;i++){
                    double area=stats.get(i,Imgproc.CC_STAT_AREA)[0];if(area<MIN_COMPONENT_AREA)continue;
                    double x=centroids.get(i,0)[0],y=centroids.get(i,1)[0];
                    double d=Math.hypot(x-ref.cx,y-ref.cy);if(d<bestD){bestD=d;best=i;}
                }
                if(best<0||bestD>0.14*Math.max(ref.w,ref.h))return null;
                double bw=stats.get(best,Imgproc.CC_STAT_WIDTH)[0],bh=stats.get(best,Imgproc.CC_STAT_HEIGHT)[0];
                double refArea=Math.max(1.0,(2*ref.major)*(2*ref.major));
                double boxRatio=(bw*bh)/refArea;
                Core.compare(labels,new org.opencv.core.Scalar(best),component,Core.CMP_EQ);
                Imgproc.morphologyEx(component,boundary,Imgproc.MORPH_GRADIENT,kernel);
                Core.bitwise_not(boundary,inverse);Imgproc.distanceTransform(inverse,dist,Imgproc.DIST_L2,3);
                float[] values=new float[ref.w*ref.h];dist.get(0,0,values);
                return new CandidateBoundary(ref.w,ref.h,values,boxRatio);
            }finally{
                if(kernel!=null)kernel.release();dist.release();inverse.release();boundary.release();component.release();
                centroids.release();stats.release();labels.release();opened.release();binary.release();blur.release();crop.release();
            }
        }
    }

    private static final class LocalFit {
        final double dx,dy,rotationDeg,meanDistancePx,support;final boolean hitLimit;
        LocalFit(double dx,double dy,double rot,double score,double support,boolean hit){this.dx=dx;this.dy=dy;rotationDeg=rot;meanDistancePx=score;this.support=support;hitLimit=hit;}
    }
    private static final class Score {final double mean,support,obj;Score(double m,double s){mean=m;support=s;obj=m-.35*s;}}

    private static LocalFit fit(RefShape ref,CandidateBoundary c,double scale,boolean rotation){
        double maxShift=clampD(SEARCH_R*scale,MIN_SEARCH_PX,MAX_SEARCH_PX);
        double bestObj=Double.POSITIVE_INFINITY,bx=0,by=0,br=0,bm=Double.POSITIVE_INFINITY,bs=0;
        for(double dx=-maxShift;dx<=maxShift+1e-9;dx+=1.0)for(double dy=-maxShift;dy<=maxShift+1e-9;dy+=1.0){
            if(rotation){
                for(double r=-SEARCH_ROT_DEG;r<=SEARCH_ROT_DEG+1e-9;r+=1.0){Score s=score(ref,c,dx,dy,r);if(s.obj<bestObj){bestObj=s.obj;bx=dx;by=dy;br=r;bm=s.mean;bs=s.support;}}
            }else{Score s=score(ref,c,dx,dy,0);if(s.obj<bestObj){bestObj=s.obj;bx=dx;by=dy;br=0;bm=s.mean;bs=s.support;}}
        }
        double cx=bx,cy=by,cr=br;bestObj=Double.POSITIVE_INFINITY;
        for(double dx=cx-1;dx<=cx+1+1e-9;dx+=.5)for(double dy=cy-1;dy<=cy+1+1e-9;dy+=.5){
            if(rotation){
                for(double r=cr-1;r<=cr+1+1e-9;r+=.25){Score s=score(ref,c,dx,dy,r);if(s.obj<bestObj){bestObj=s.obj;bx=dx;by=dy;br=r;bm=s.mean;bs=s.support;}}
            }else{Score s=score(ref,c,dx,dy,0);if(s.obj<bestObj){bestObj=s.obj;bx=dx;by=dy;br=0;bm=s.mean;bs=s.support;}}
        }
        boolean hit=Math.abs(bx)>=maxShift-.25||Math.abs(by)>=maxShift-.25||(rotation&&Math.abs(br)>=SEARCH_ROT_DEG+.75);
        return new LocalFit(bx,by,br,bm,bs,hit);
    }

    private static Score score(RefShape ref,CandidateBoundary c,double dx,double dy,double rot){
        double a=Math.toRadians(rot),ca=Math.cos(a),sa=Math.sin(a),sum=0;int supported=0,n=0;
        for(double[] p:ref.pts){
            double qx=p[0]-ref.cx,qy=p[1]-ref.cy;
            double x=ref.cx+ca*qx-sa*qy+dx,y=ref.cy+sa*qx+ca*qy+dy;
            double d=sample(c,x,y);if(!Double.isFinite(d))d=DISTANCE_CLIP_PX;d=Math.min(DISTANCE_CLIP_PX,d);
            sum+=d;if(d<=SUPPORT_DISTANCE_PX)supported++;n++;
        }
        return n==0?new Score(DISTANCE_CLIP_PX,0):new Score(sum/n,supported/(double)n);
    }

    private static double sample(CandidateBoundary c,double x,double y){
        if(x<0||y<0||x>=c.w-1||y>=c.h-1)return DISTANCE_CLIP_PX;
        int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double fx=x-x0,fy=y-y0;int i=y0*c.w+x0;
        return c.distance[i]*(1-fx)*(1-fy)+c.distance[i+1]*fx*(1-fy)+c.distance[i+c.w]*(1-fx)*fy+c.distance[i+c.w+1]*fx*fy;
    }

    private static List<Point> projectedOutline(int hour,double[] H){
        List<Point> out=new ArrayList<>();
        if(isRound(hour)){
            Point c=masterPoint(hour);
            for(int i=0;i<96;i++){
                double t=2*Math.PI*i/96.0;
                Point p=project(H,c.x+Alpha92GmtMaster.ROUND_OUTER_R*Math.cos(t),c.y+Alpha92GmtMaster.ROUND_OUTER_R*Math.sin(t));
                if(p!=null)out.add(p);
            }
        }else{
            Point[] v=batonVertices(hour);
            for(int k=0;k<4;k++)for(int i=0;i<32;i++){
                double q=i/31.0;
                Point p=project(H,v[k].x+q*(v[(k+1)%4].x-v[k].x),v[k].y+q*(v[(k+1)%4].y-v[k].y));
                if(p!=null)out.add(p);
            }
        }
        return out;
    }

    private static Point[] batonVertices(int hour){
        double a=Alpha92GmtMaster.angleForHour(hour),ca=Math.cos(a),sa=Math.sin(a),tx=-sa,ty=ca;
        double cr=Alpha92GmtMaster.BATON_CENTER_R,rh=Alpha92GmtMaster.BATON_RADIAL_HALF,th=Alpha92GmtMaster.BATON_TANGENTIAL_HALF;
        Point[] v=new Point[4];
        for(int k=0;k<4;k++){
            double rr=cr+((k==0||k==1)?rh:-rh),tt=((k==0||k==3)?-th:th);
            v[k]=new Point(rr*ca+tt*tx,rr*sa+tt*ty);
        }
        return v;
    }

    private static Point masterPoint(int hour){
        double a=Math.toRadians(hour*30.0),r=isRound(hour)?Alpha92GmtMaster.ROUND_CENTER_R:Alpha92GmtMaster.BATON_CENTER_R;
        return new Point(Math.sin(a)*r,-Math.cos(a)*r);
    }
    private static boolean isRound(int h){for(int q:ROUND_HOURS)if(q==h)return true;return false;}

    private static double[] imageShiftToCanonical(double[] H,Point p,double dx,double dy){
        double[][] J=jacobian(H,p);if(J==null)return null;double det=J[0][0]*J[1][1]-J[0][1]*J[1][0];if(Math.abs(det)<1e-9)return null;
        return new double[]{( J[1][1]*dx-J[0][1]*dy)/det,(-J[1][0]*dx+J[0][0]*dy)/det};
    }

    private static double canonicalRotationFromImage(double[] H,Point p,double erx,double ery,double imageDeg){
        double[][] J=jacobian(H,p);if(J==null)return imageDeg;
        double base=Math.atan2(J[1][0]*erx+J[1][1]*ery,J[0][0]*erx+J[0][1]*ery);
        double q=Math.toRadians(1.0),rx=Math.cos(q)*erx-Math.sin(q)*ery,ry=Math.sin(q)*erx+Math.cos(q)*ery;
        double one=Math.atan2(J[1][0]*rx+J[1][1]*ry,J[0][0]*rx+J[0][1]*ry);
        double response=wrapAxis(Math.toDegrees(one-base));if(Math.abs(response)<0.1)return imageDeg;
        return imageDeg/response;
    }

    private static double[][] jacobian(double[] H,Point p){
        double d=1e-4;Point c=project(H,p.x,p.y),x=project(H,p.x+d,p.y),y=project(H,p.x,p.y+d);
        if(c==null||x==null||y==null)return null;
        return new double[][]{{(x.x-c.x)/d,(y.x-c.x)/d},{(x.y-c.y)/d,(y.y-c.y)/d}};
    }

    private static double localScalePxPerR(double[] H,Point p){
        double[][] J=jacobian(H,p);if(J==null)return Double.NaN;
        double sx=Math.hypot(J[0][0],J[1][0]),sy=Math.hypot(J[0][1],J[1][1]);
        return Math.sqrt(Math.max(1e-9,sx*sy));
    }

    private static double equivalentDialRadiusPx(double[] H){
        if(H==null||H.length<9)return Double.NaN;int n=120;Point[] p=new Point[n];
        double area=0;for(int i=0;i<n;i++){double t=2*Math.PI*i/n;p[i]=project(H,Math.cos(t),Math.sin(t));if(p[i]==null)return Double.NaN;}
        for(int i=0;i<n;i++){Point q=p[(i+1)%n];area+=p[i].x*q.y-q.x*p[i].y;}
        area=Math.abs(area)*.5;return Math.sqrt(area/Math.PI);
    }

    private static Point project(double[] h,double x,double y){
        double w=h[6]*x+h[7]*y+h[8];if(!Double.isFinite(w)||Math.abs(w)<1e-9)return null;
        double px=(h[0]*x+h[1]*y+h[2])/w,py=(h[3]*x+h[4]*y+h[5])/w;
        return Double.isFinite(px)&&Double.isFinite(py)?new Point(px,py):null;
    }

    private static double[] solve4(double[][] A,double[] b){
        double[][] m=new double[4][5];for(int i=0;i<4;i++){System.arraycopy(A[i],0,m[i],0,4);m[i][4]=b[i];}
        for(int col=0;col<4;col++){
            int piv=col;for(int r=col+1;r<4;r++)if(Math.abs(m[r][col])>Math.abs(m[piv][col]))piv=r;
            if(Math.abs(m[piv][col])<1e-12)return null;double[] tmp=m[col];m[col]=m[piv];m[piv]=tmp;
            double d=m[col][col];for(int c=col;c<5;c++)m[col][c]/=d;
            for(int r=0;r<4;r++)if(r!=col){double f=m[r][col];for(int c=col;c<5;c++)m[r][c]-=f*m[col][c];}
        }
        return new double[]{m[0][4],m[1][4],m[2][4],m[3][4]};
    }

    private static double wrapAxis(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    private static double clampD(double v,double lo,double hi){return Math.max(lo,Math.min(hi,v));}
}
