package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Alpha101 alignment of a mirror-symmetric printed or applied logo (e.g. the crown under the 12): where its symmetry
 * axis lies relative to the dial's own radial line through it, as a sideways offset (units of dial radius) and a tilt
 * (degrees, + = clockwise). Generic: the model spec gives the logo's canonical box (dial radius 1, 12 at the top) and
 * the hour it sits on; nothing here is model-specific.
 *
 * Method: the box is rectified upright on the frozen pose at about 2 samples per photo pixel; ink is the minority class
 * of an Otsu split (works for light-on-dark and dark-on-light print); the symmetry axis is the line (offset, tilt) that
 * maximises the grey-level correlation of the box with its own mirror image (bilinear, coarse-to-fine); the IoU of
 * the binary ink with its mirror across that axis is the symmetry score: a hand, the seconds-hand dot, glare or a cropped logo breaks the symmetry, and below MIN_SYMMETRY
 * the logo is withheld (fail closed). Read-only: nothing feeds back into pose or measurement.
 */
final class Alpha101PrintAlignment {
    static final double MIN_SYMMETRY=0.80;

    static final class Result {
        boolean usable;String reason="";
        /** sideways offset of the symmetry axis at the box centre, dial radii (+ = clockwise / to the right at 12). */
        double offsetR=Double.NaN;
        /** tilt of the symmetry axis against the dial's radial line, degrees (+ = clockwise). */
        double tiltDeg=Double.NaN;
        double symmetry=Double.NaN;
    }

    private Alpha101PrintAlignment(){}

    /**
     * @param box canonical box of the logo as seen with its hour at the top: {halfWidth, rInner, rOuter} (radii along the
     *            hour's radial line), e.g. GMT crown {0.12, 0.42, 0.57}.
     */
    static Result measure(Mat gray,double[] H,int hour,double[] box){
        Result o=new Result();
        double rpx=Alpha99MarkerInterference.pxPerR(H);
        if(gray==null||gray.empty()||!(rpx>20)){o.reason="pose unavailable";return o;}
        double hw=box[0],r0=box[1],r1=box[2];
        double step=0.5/rpx;                                   // ~2 samples per photo pixel
        int w=(int)Math.ceil(2*hw/step),h=(int)Math.ceil((r1-r0)/step);
        if(w<16||h<16){o.reason="logo too small in this photo";return o;}
        // canonical sample (col i, row j): tangential t = -hw + (i+0.5) step (+ = clockwise), radial r = r1 - (j+0.5) step
        double a=Math.toRadians(hour*30.0),erx=Math.sin(a),ery=-Math.cos(a),etx=Math.cos(a),ety=Math.sin(a);
        Mat T=new Mat(3,3,CvType.CV_64F);
        // canonical (x,y) = r*er + t*et with r = r1 - (row+0.5)*step, t = -hw + (col+0.5)*step
        double cx0=(r1-0.5*step)*erx+(-hw+0.5*step)*etx,cy0=(r1-0.5*step)*ery+(-hw+0.5*step)*ety;
        T.put(0,0,step*etx,-step*erx,cx0, step*ety,-step*ery,cy0, 0,0,1);
        Mat Hm=new Mat(3,3,CvType.CV_64F);Hm.put(0,0,H);
        Mat M=new Mat();Core.gemm(Hm,T,1,new Mat(),0,M);
        Mat crop=new Mat();
        Imgproc.warpPerspective(gray,crop,M,new Size(w,h),Imgproc.INTER_CUBIC|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0));
        Mat bin=new Mat();Imgproc.threshold(crop,bin,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
        byte[] px=new byte[w*h];bin.get(0,0,px);
        int on=0;for(byte b:px)if(b!=0)on++;
        boolean inkIsBright=on<=w*h-on;
        boolean[] ink=new boolean[w*h];int n=0;
        for(int k=0;k<px.length;k++){ink[k]=(px[k]!=0)==inkIsBright;if(ink[k])n++;}
        if(n<20){o.reason="logo not found";return o;}
        // symmetry gate on the binary ink (robust to exposure), axis from a smooth grey-level mirror correlation
        float[] g=new float[w*h];byte[] gv=new byte[w*h];crop.get(0,0,gv);
        double mean=0;for(int k=0;k<g.length;k++){g[k]=(gv[k]&0xff)*(inkIsBright?1f:-1f);mean+=g[k];}mean/=g.length;
        for(int k=0;k<g.length;k++)g[k]-=(float)mean;
        double xc=(w-1)/2.0,yc=(h-1)/2.0;
        double bestS=-2,bestDx=0,bestTh=0;
        double span=0.25*w;
        for(double dx=-span;dx<=span;dx+=1.0)for(double th=-6;th<=6;th+=1.0){double sc=mirrorCorr(g,w,h,xc+dx,yc,th);if(sc>bestS){bestS=sc;bestDx=dx;bestTh=th;}}
        double ds=0.5,dt=0.5;
        for(int it=0;it<6;it++){
            double bx=bestDx,bt=bestTh;
            for(int i=-2;i<=2;i++)for(int j=-2;j<=2;j++){double dx=bx+i*ds,th=bt+j*dt;double sc=mirrorCorr(g,w,h,xc+dx,yc,th);if(sc>bestS){bestS=sc;bestDx=dx;bestTh=th;}}
            ds/=2;dt/=2;
        }
        bestS=iou(ink,w,h,xc+bestDx,yc,bestTh);
        o.symmetry=bestS;
        if(bestS<MIN_SYMMETRY){o.reason=String.format(java.util.Locale.US,"logo not clearly visible (symmetry %.2f: a hand, glare or blur)",bestS);return o;}
        o.usable=true;
        o.offsetR=bestDx*step;
        // image rows run outward-to-inward (r decreases downward); a clockwise-tilted logo leans its outer end clockwise
        o.tiltDeg=bestTh;
        return o;
    }

    /** Normalised correlation of the (mean-removed, ink-positive) grey image with its mirror across the axis, bilinear. */
    static double mirrorCorr(float[] g,int w,int h,double ax,double ay,double th){
        double t=Math.toRadians(th),ux=Math.sin(t),uy=-Math.cos(t),nx=-uy,ny=ux;
        double sxy=0,sxx=0,syy=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double d=(x-ax)*nx+(y-ay)*ny,mx=x-2*d*nx,my=y-2*d*ny;
            if(mx<0||my<0||mx>w-1||my>h-1)continue;
            int x0=(int)mx,y0=(int)my,x1=Math.min(x0+1,w-1),y1=Math.min(y0+1,h-1);double fx=mx-x0,fy=my-y0;
            double q=(1-fy)*((1-fx)*g[y0*w+x0]+fx*g[y0*w+x1])+fy*((1-fx)*g[y1*w+x0]+fx*g[y1*w+x1]);
            double p=g[y*w+x];sxy+=p*q;sxx+=p*p;syy+=q*q;
        }
        return sxx>0&&syy>0?sxy/Math.sqrt(sxx*syy):-1;
    }

    /** IoU of the ink with its mirror image across the line through (ax, ay) tilted th degrees from vertical. */
    static double iou(boolean[] ink,int w,int h,double ax,double ay,double th){
        double t=Math.toRadians(th),ux=Math.sin(t),uy=-Math.cos(t);   // unit vector along the axis (towards the outer end)
        double nx=-uy,ny=ux;                                           // normal
        int inter=0,uni=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            boolean p=ink[y*w+x];
            double d=(x-ax)*nx+(y-ay)*ny;
            int mx=(int)Math.round(x-2*d*nx),my=(int)Math.round(y-2*d*ny);
            boolean q=mx>=0&&my>=0&&mx<w&&my<h&&ink[my*w+mx];
            if(p&&q)inter++;if(p||q)uni++;
        }
        return uni==0?0:inter/(double)uni;
    }
}
