package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfInt;
import org.opencv.core.Point;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alpha98: date-window tilt (research display). Port of tools/research/alpha96_calibration/date_window.py (window
 * detection, edge refinement, edge-line tilt and its fail-closed rules); digit centring / row tilt stay research-only.
 *
 * The 3 o'clock region is rectified on the frozen production pose with the same cubic B-spline interpolant and region
 * as the harness DateCrop (the model spec's date_window crop; GMT: canonical x 0.22..1.02, y -0.38..0.38 R at
 * 0.0025 R/px, 12 at the top). Inside the
 * magnified window only angles are used, so the cyclops magnification cancels. Window tilt is reported against the dial
 * horizontal (+ = clockwise). Read-only: nothing here feeds back into pose or marker measurement.
 */
final class Alpha98DateWindow {
    static final double AREA_MIN=0.012,AREA_MAX=0.09,ASPECT_MIN=1.3,ASPECT_MAX=2.6,MIN_RECT_FILL=0.88;

    static final class Result {
        boolean usable;String reason="";
        double windowTiltDeg=Double.NaN;
        /** Window centre and size in canonical dial units (for the close-up). */
        double cx=Double.NaN,cy=Double.NaN,wR=Double.NaN,hR=Double.NaN,angleDeg=0;
    }

    private Alpha98DateWindow(){}

    /** Upright rectified 3 o'clock crop (8-bit grey), identical sampling to the harness DateCrop. */
    static Mat crop(Mat gray,double[] H,ModelSpec.DateWindow dw){
        final double X0=dw.x0,X1=dw.x1,Y0=dw.y0,Y1=dw.y1,STEP=dw.step;
        double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
        for(int k=0;k<72;k++){double t=2*Math.PI*k/72;double[] p=project(H,1.1*Math.cos(t),1.1*Math.sin(t));
            if(p==null)continue;minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
        if(!Double.isFinite(minX)){minX=0;minY=0;maxX=gray.cols();maxY=gray.rows();}
        Alpha91SplineImage img=new Alpha91SplineImage(gray,(int)Math.floor(minX)-4,(int)Math.floor(minY)-4,(int)Math.ceil(maxX)+5,(int)Math.ceil(maxY)+5);
        int w=(int)Math.round((X1-X0)/STEP),h=(int)Math.round((Y1-Y0)/STEP);
        byte[] px=new byte[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double[] p=project(H,X0+(x+0.5)*STEP,Y0+(y+0.5)*STEP);
            double v=p==null?Double.NaN:img.at(p[0],p[1]);
            px[y*w+x]=(byte)Math.max(0,Math.min(255,Double.isFinite(v)?Math.round(v):0));
        }
        Mat m=new Mat(h,w,CvType.CV_8UC1);m.put(0,0,px);return m;
    }

    static double[] project(double[] H,double x,double y){
        double q=H[6]*x+H[7]*y+H[8];if(!Double.isFinite(q)||Math.abs(q)<1e-12)return null;
        return new double[]{(H[0]*x+H[1]*y+H[2])/q,(H[3]*x+H[4]*y+H[5])/q};
    }

    static Result analyse(Mat gray,double[] H,ModelSpec.DateWindow dw){
        if(dw==null){Result r=new Result();r.reason="this model has no date window";return r;}
        if(gray==null||H==null||H.length<9){Result r=new Result();r.reason="pose unavailable";return r;}
        return measure(crop(gray,H,dw),dw);
    }

    // ------------------------------------------------------------------ measurement (port of date_window.py)
    static Result measure(Mat g,ModelSpec.DateWindow dw){
        final double X0=dw.x0,Y0=dw.y0,STEP=dw.step;
        Result out=new Result();
        double[] rect=findWindow(g,out,dw);
        if(rect==null)return out;
        double[] ref=refineWindow(g,rect);
        if(ref==null){out.reason="window edges not found";return out;}
        if(!(ASPECT_MIN<=ref[2]/ref[3]&&ref[2]/ref[3]<=ASPECT_MAX)){out.reason=String.format(java.util.Locale.US,"refined window aspect %.2f implausible",ref[2]/ref[3]);return out;}
        rect=ref;
        Mat win=windowFrame(g,rect);int H=win.rows(),W=win.cols();
        int band=Math.max(2,(int)Math.rint(0.10*H)),side=Math.max(2,(int)Math.rint(0.06*W));
        Mat inner=win.submat(band,H-band,side,W-side);
        double t=Imgproc.threshold(inner,new Mat(),0,255,Imgproc.THRESH_BINARY_INV|Imgproc.THRESH_OTSU);
        Mat ink=lessThan(win,t);                                                        // win < t
        Mat lab=new Mat(),st=new Mat(),cen=new Mat();
        int n=Imgproc.connectedComponentsWithStats(ink,lab,st,cen);
        boolean touch=false;int kept=0,by0=Integer.MAX_VALUE,by1=-1;
        for(int i=1;i<n;i++){
            int x=(int)st.get(i,0)[0],y=(int)st.get(i,1)[0],w_=(int)st.get(i,2)[0],h_=(int)st.get(i,3)[0],a=(int)st.get(i,4)[0];
            if(a<Math.max(6,0.002*W*H)||h_<0.25*H)continue;
            if(x==0||x+w_>=W)continue;
            if(y<=1||y+h_>=H-1)touch=true;
            kept++;by0=Math.min(by0,y);by1=Math.max(by1,y+h_);
        }
        out.cx=X0+(rect[0]+0.5)*STEP;out.cy=Y0+(rect[1]+0.5)*STEP;out.wR=rect[2]*STEP;out.hR=rect[3]*STEP;out.angleDeg=rect[4];
        if(kept==0){out.reason="no digit ink found";return out;}
        if(touch){out.reason="numeral cut by the window edge (date mid-change / hand)";return out;}
        double hfrac=(by1-by0)/(double)H;
        if(!(0.35<=hfrac&&hfrac<=0.92)){out.reason=String.format(java.util.Locale.US,"digit height %.2f of window implausible",hfrac);return out;}
        double[] w=edgeLinesTilt(g,rect,by0,by1-1);
        if(!(w[1]>=15&&Double.isFinite(w[0]))){out.reason="too few clean window edges for tilt";return out;}
        out.usable=true;out.windowTiltDeg=rect[4]+w[0];
        return out;
    }

    /** {cx, cy, w, h, angleDeg} in crop pixels, or null with out.reason set. */
    static double[] findWindow(Mat g,Result out,ModelSpec.DateWindow dw){
        final double X0=dw.x0,Y0=dw.y0,STEP=dw.step,EXP_X=dw.expX,EXP_Y=dw.expY;
        double ex=(EXP_X-X0)/STEP-0.5,ey=(EXP_Y-Y0)/STEP-0.5;
        Mat blur=new Mat();Imgproc.GaussianBlur(g,blur,new Size(5,5),0);
        int r0=Math.max(0,(int)(ey-0.25/STEP)),r1=Math.min(blur.rows(),(int)(ey+0.25/STEP)),c0=Math.max(0,(int)(ex-0.3/STEP)),c1=Math.min(blur.cols(),(int)(ex+0.3/STEP));
        Mat roi=blur.submat(r0,r1,c0,c1);
        double t1=Imgproc.threshold(roi,new Mat(),0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
        byte[] rb=new byte[(int)roi.total()];roi.clone().get(0,0,rb);
        int nu=0;for(byte b:rb)if((b&0xff)>t1)nu++;
        double t=t1;
        if(nu>50){byte[] up=new byte[nu];int k=0;for(byte b:rb)if((b&0xff)>t1)up[k++]=b;Mat um=new Mat(nu,1,CvType.CV_8UC1);um.put(0,0,up);
            t=Imgproc.threshold(um,new Mat(),0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);}
        Mat bw=greaterThan(blur,t);Core.multiply(bw,new org.opencv.core.Scalar(255),bw);
        Imgproc.morphologyEx(bw,bw,Imgproc.MORPH_OPEN,Mat.ones(3,3,CvType.CV_8UC1));
        List<MatOfPoint> cnts=new ArrayList<>();Imgproc.findContours(bw,cnts,new Mat(),Imgproc.RETR_EXTERNAL,Imgproc.CHAIN_APPROX_NONE);
        double bestD=Double.POSITIVE_INFINITY;double[] best=null;MatOfPoint bestC=null;
        for(MatOfPoint c:cnts){
            double area=Imgproc.contourArea(c)*STEP*STEP;
            if(!(AREA_MIN<=area&&area<=AREA_MAX))continue;
            RotatedRect rr=Imgproc.minAreaRect(new MatOfPoint2f(c.toArray()));
            double rw=rr.size.width,rh=rr.size.height,ang=rr.angle;
            if(rw<rh){double tmp=rw;rw=rh;rh=tmp;ang+=90;}
            ang=normAngle(ang);                    // OpenCV builds differ in minAreaRect angle convention
            double d=Math.hypot(rr.center.x-ex,rr.center.y-ey)*STEP;
            if(d>0.2)continue;
            if(d<bestD){bestD=d;best=new double[]{rr.center.x,rr.center.y,rw,rh,ang};bestC=c;}
        }
        if(best==null){out.reason="no plausible window";return null;}
        MatOfInt hull=new MatOfInt();Imgproc.convexHull(bestC,hull);
        Point[] pts=bestC.toArray();int[] hi=hull.toArray();Point[] hp=new Point[hi.length];for(int i=0;i<hi.length;i++)hp[i]=pts[hi[i]];
        double fill=Imgproc.contourArea(new MatOfPoint(hp))/Math.max(best[2]*best[3],1);
        if(!(ASPECT_MIN<=best[2]/best[3]&&best[2]/best[3]<=ASPECT_MAX)){out.reason=String.format(java.util.Locale.US,"window aspect %.2f implausible",best[2]/best[3]);return null;}
        if(fill<MIN_RECT_FILL){out.reason=String.format(java.util.Locale.US,"window not rectangular (fill %.2f: glare / reflection / occlusion)",fill);return null;}
        return best;
    }

    /** Upright resample of a rotated rect (crop pixel scale), INTER_CUBIC, replicate border. */
    static Mat windowFrame(Mat g,double[] rect){
        double cx=rect[0],cy=rect[1],rw=rect[2],rh=rect[3],a=Math.toRadians(rect[4]);
        int W=(int)Math.rint(rw),H=(int)Math.rint(rh);
        Mat M=new Mat(2,3,CvType.CV_64F);
        M.put(0,0,Math.cos(a),-Math.sin(a),cx-(W/2.0)*Math.cos(a)+(H/2.0)*Math.sin(a),
                  Math.sin(a),Math.cos(a),cy-(W/2.0)*Math.sin(a)-(H/2.0)*Math.cos(a));
        Mat M32=new Mat();M.convertTo(M32,CvType.CV_32F);M32.convertTo(M,CvType.CV_64F);   // python builds M as float32
        Mat out=new Mat();Imgproc.warpAffine(g,out,M,new Size(W,H),Imgproc.INTER_CUBIC|Imgproc.WARP_INVERSE_MAP,Core.BORDER_REPLICATE);
        return out;
    }

    static double[] refineWindow(Mat g,double[] rect){
        double cx=rect[0],cy=rect[1],rw=rect[2],rh=rect[3],ang=rect[4];
        Mat bigM=windowFrame(g,new double[]{cx,cy,rw*1.5,rh*1.8,ang});
        int Hb=bigM.rows(),Wb=bigM.cols();float[][] big=toFloat(bigM);
        double ox=(Wb-rw)/2.0,oy=(Hb-rh)/2.0;
        int cr0=(int)(oy+0.2*rh),cr1=(int)(oy+0.8*rh),cc0=(int)(ox+0.1*rw),cc1=(int)(ox+0.9*rw);
        if(cr1<=cr0||cc1<=cc0)return null;
        double[] core=new double[(cr1-cr0)*(cc1-cc0)];int k=0;
        for(int y=cr0;y<cr1;y++)for(int x=cc0;x<cc1;x++)core[k++]=big[y][x];
        double white=percentile(core,90);
        Mat coreM=new Mat(cr1-cr0,cc1-cc0,CvType.CV_8UC1);byte[] cb=new byte[core.length];for(int i=0;i<core.length;i++)cb[i]=(byte)(int)core[i];coreM.put(0,0,cb);
        double t=Imgproc.threshold(coreM,new Mat(),0,255,Imgproc.THRESH_BINARY_INV|Imgproc.THRESH_OTSU);
        Mat inkm=new Mat(Hb,Wb,CvType.CV_8UC1,new org.opencv.core.Scalar(0));byte[] ib=new byte[Hb*Wb];
        int oyI=(int)oy,oyE=(int)(oy+rh),oxI=(int)ox,oxE=(int)(ox+rw);
        for(int y=0;y<Hb;y++)for(int x=0;x<Wb;x++)ib[y*Wb+x]=(byte)((big[y][x]<t&&y>=oyI&&y<oyE&&x>=oxI&&x<oxE)?1:0);
        inkm.put(0,0,ib);
        Mat lab=new Mat(),st=new Mat(),cen=new Mat();int n=Imgproc.connectedComponentsWithStats(inkm,lab,st,cen);
        int iy0=Integer.MAX_VALUE,iy1=-1,ix0=Integer.MAX_VALUE,ix1=-1;boolean any=false;
        for(int i=1;i<n;i++){int x=(int)st.get(i,0)[0],y=(int)st.get(i,1)[0],w=(int)st.get(i,2)[0],h=(int)st.get(i,3)[0],a=(int)st.get(i,4)[0];
            if(h>=0.25*rh&&a>=6){any=true;iy0=Math.min(iy0,y);iy1=Math.max(iy1,y+h-1);ix0=Math.min(ix0,x);ix1=Math.max(ix1,x+w-1);}}
        if(!any)return null;
        int cb0=(int)(ox+0.1*rw),cb1=(int)(ox+0.9*rw);
        double[] vprof=new double[Hb],hprof=new double[Wb];
        for(int y=0;y<Hb;y++){double[] v=new double[cb1-cb0];for(int x=cb0;x<cb1;x++)v[x-cb0]=big[y][x];vprof[y]=median(v);}
        for(int x=0;x<Wb;x++){double[] v=new double[iy1-iy0+1];for(int y=iy0;y<=iy1;y++)v[y-iy0]=big[y][x];hprof[x]=median(v);}
        Double top=crossing(vprof,Math.max(1,iy0-1),-1,white),bot=crossing(vprof,Math.min(Hb-2,iy1+1),+1,white);
        Double lef=crossing(hprof,Math.max(1,ix0-1),-1,white),rig=crossing(hprof,Math.min(Wb-2,ix1+1),+1,white);
        if(top==null||bot==null||lef==null||rig==null)return null;
        double nw=rig-lef,nh=bot-top;if(nw<=0||nh<=0)return null;
        double dxw=(lef+rig)/2.0-Wb/2.0,dyw=(top+bot)/2.0-Hb/2.0,a=Math.toRadians(ang);
        return new double[]{cx+dxw*Math.cos(a)-dyw*Math.sin(a),cy+dxw*Math.sin(a)+dyw*Math.cos(a),nw,nh,ang};
    }

    private static Double crossing(double[] prof,int start,int dir,double white){
        double dark=Double.POSITIVE_INFINITY;
        if(dir<0){for(int i=0;i<Math.max(1,start);i++)dark=Math.min(dark,prof[i]);}else{for(int i=start;i<prof.length;i++)dark=Math.min(dark,prof[i]);}
        dark=Math.min(dark,white);double mid=0.5*(white+dark);
        int i=start;
        for(int s=0;s<6;s++){if(prof[i]>=mid||!(0<i-dir&&i-dir<prof.length-1))break;i-=dir;}
        while(0<i&&i<prof.length-1&&prof[i]>=mid)i+=dir;
        if(!(0<i&&i<prof.length-1))return null;
        int j=i-dir;double a=prof[j],b=prof[i];
        return j+dir*(a-mid)/Math.max(a-b,1e-6);
    }

    /** {tilt residual deg, n columns}: per-column sub-pixel top/bottom edge crossings, Theil-Sen line per edge. */
    static double[] edgeLinesTilt(Mat g,double[] rect,int inkTop,int inkBot){
        double rh=rect[3];
        Mat bm=windowFrame(g,new double[]{rect[0],rect[1],rect[2],rh*1.6,rect[4]});
        int Hb=bm.rows(),Wb=bm.cols();float[][] big=toFloat(bm);
        double oy=(Hb-rh)/2.0;int t0=(int)(oy+inkTop),t1=(int)(oy+inkBot);
        int r0=(int)(oy+0.2*rh),r1=(int)(oy+0.8*rh);
        double[] wv=new double[(r1-r0)*Wb];int k=0;for(int y=r0;y<r1;y++)for(int x=0;x<Wb;x++)wv[k++]=big[y][x];
        double white=percentile(wv,90);
        double[] angs=new double[2];int n=0;
        int[][] runs={{t0,-1},{t1,+1}};
        for(int e=0;e<2;e++){
            int start=runs[e][0],d=runs[e][1];
            List<double[]> pts=new ArrayList<>();
            for(int c=(int)(0.15*Wb);c<(int)(0.85*Wb);c++){Double y=edgeAt(big,c,start,d,white,Hb);if(y!=null)pts.add(new double[]{c,y});}
            n+=pts.size();
            if(pts.size()<10)return new double[]{Double.NaN,n};
            List<Double> sl=new ArrayList<>();
            for(int i=0;i<pts.size();i+=2)for(int j=i+1;j<pts.size();j+=3)sl.add((pts.get(j)[1]-pts.get(i)[1])/(pts.get(j)[0]-pts.get(i)[0]));
            double[] sa=new double[sl.size()];for(int i=0;i<sa.length;i++)sa[i]=sl.get(i);
            angs[e]=Math.toDegrees(Math.atan(median(sa)));
        }
        return new double[]{(angs[0]+angs[1])/2.0,n};
    }

    private static Double edgeAt(float[][] big,int col,int start,int d,double white,int Hb){
        double dark=Double.POSITIVE_INFINITY;
        if(d<0){for(int i=0;i<=start;i++)dark=Math.min(dark,big[i][col]);}else{for(int i=start;i<Hb;i++)dark=Math.min(dark,big[i][col]);}
        dark=Math.min(dark,white);double mid=0.5*(white+dark);
        Integer prev=null;
        if(d<0){for(int i=start;i>0;i--){if(big[i][col]<mid&&prev!=null){double a=big[prev][col],b=big[i][col];return prev+d*(a-mid)/Math.max(a-b,1e-6);}if(big[i][col]>=mid)prev=i;}}
        else{for(int i=start;i<Hb-1;i++){if(big[i][col]<mid&&prev!=null){double a=big[prev][col],b=big[i][col];return prev+d*(a-mid)/Math.max(a-b,1e-6);}if(big[i][col]>=mid)prev=i;}}
        return null;
    }

    /** Long-axis angle normalised to (-90, 90] (a rectangle at 180 deg is the same rectangle at 0 deg). */
    static double normAngle(double a){while(a>90)a-=180;while(a<=-90)a+=180;return a;}

    // ------------------------------------------------------------------ helpers (numpy-equivalent)
    private static float[][] toFloat(Mat m){int H=m.rows(),W=m.cols();byte[] b=new byte[H*W];m.get(0,0,b);float[][] f=new float[H][W];
        for(int y=0;y<H;y++)for(int x=0;x<W;x++)f[y][x]=b[y*W+x]&0xff;return f;}
    private static Mat lessThan(Mat m,double t){int H=m.rows(),W=m.cols();byte[] b=new byte[H*W];m.get(0,0,b);byte[] o=new byte[H*W];
        for(int i=0;i<o.length;i++)o[i]=(byte)((b[i]&0xff)<t?1:0);Mat r=new Mat(H,W,CvType.CV_8UC1);r.put(0,0,o);return r;}
    private static Mat greaterThan(Mat m,double t){int H=m.rows(),W=m.cols();byte[] b=new byte[H*W];m.get(0,0,b);byte[] o=new byte[H*W];
        for(int i=0;i<o.length;i++)o[i]=(byte)((b[i]&0xff)>t?1:0);Mat r=new Mat(H,W,CvType.CV_8UC1);r.put(0,0,o);return r;}
    /** numpy.percentile (linear interpolation). */
    static double percentile(double[] v,double p){double[] s=v.clone();Arrays.sort(s);double k=(s.length-1)*p/100.0;int lo=(int)Math.floor(k),hi=Math.min(lo+1,s.length-1);return s[lo]+(s[hi]-s[lo])*(k-lo);}
    /** numpy.median. */
    static double median(double[] v){double[] s=v.clone();Arrays.sort(s);int n=s.length;return n%2==1?s[n/2]:0.5*(s[n/2-1]+s[n/2]);}
}
