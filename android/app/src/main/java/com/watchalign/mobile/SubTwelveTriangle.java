package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Submariner 124060 12-triangle detector: production port of the frozen research detector v2
 * (feature/submariner-research-hardening, SubTriangle.java at fed2303; see
 * docs/research/submariner/SUB_TRIANGLE_DETECTOR.md on that branch).
 *
 * Ported unchanged: candidate enumeration (four binarisations), the local minute-track frame (track
 * radius from the ticks 2-4 minutes either side, never the 59/60/01 ends the triangle contaminates),
 * the outer-edge refit, the features, the transparent score, the plausibility windows and the score
 * ceiling. The GMT 44.3 deg apex gate is NOT used anywhere here.
 *
 * NOT ported: the research 13-variant perturbation set and its cross-variant consensus. On the
 * development partition the single-photo best candidate at the original photo was the same outline
 * as the consensus selection on 48 of 48 photos, so production uses best() on the one photo.
 *
 * This class only finds and measures the triangle. It issues no QC verdict.
 */
final class SubTwelveTriangle {
    static final String VERSION="sub-triangle-v2";
    // ---- broad priors from the DEVELOPMENT partition (edge-fitted dials, refitted candidates) ----
    static final double RHO_LO=0.74, RHO_HI=0.86;          // centroid radius / R
    static final double WIDTH_LO=0.20, WIDTH_HI=0.30;      // base width / R (outer surround)
    static final double APEX_LO=40.0, APEX_HI=49.0;        // deg; soft, NOT a gate
    static final double AXIS_FREE_DEG=8.0;                 // no orientation penalty below this
    static final double POS_SCALE_DEG=4.0;
    // Plausibility windows for "is this the 12 marker at all" (DEVELOPMENT candidates with complete edge
    // refits: rho p5-p95 0.745-0.827, width p5-p95 0.139-0.261 incl. inner/lume outlines), widened.
    // Outside them a candidate is reported but not selectable (e.g. the crown logo at rho ~0.5 when a
    // hand hides the triangle). Shape (apex) is deliberately NOT part of this.
    static final double PLAUS_RHO_LO=0.68, PLAUS_RHO_HI=0.92, PLAUS_W_LO=0.12, PLAUS_W_HI=0.35;
    // Frame consistency: the triangle's base sits just inside its own minute track (DEVELOPMENT p5-p95 of
    // track radius minus base radius 0.020-0.075 R). A larger gap means the "track" found is something
    // else (bezel graduations or rehaut engraving when the dial fit took the wrong ring).
    static final double PLAUS_GAP_LO=-0.01, PLAUS_GAP_HI=0.10;
    // Broad shape plausibility (DEVELOPMENT refitted candidates: apex p5-p95 37.9-46.8 deg, height/width
    // 1.15-1.46). Far wider than the GMT 44.3 +/-2 deg gate, which is NOT used: these only exclude shapes
    // that are not a 12 triangle at all (development review: an inscribed shape in a round marker when the
    // watch is turned >30 deg, apex 62-68; hand-merged contours, apex 20-23 / h/w 2.4-2.8; a non-dial).
    static final double PLAUS_APEX_LO=30.0, PLAUS_APEX_HI=58.0, PLAUS_HW_LO=0.9, PLAUS_HW_HI=1.9;
    // Score ceiling: on the development review every correct selection scored <= 5.3 and the wrong ones
    // (hand-merged, annotation, round-marker pieces) 7.4-14.5; above this nothing is selected.
    static final double SCORE_MAX=6.5;
    // ---- enumeration windows (generic, wide) ----
    static final double ROI_HALF_ANGLE_DEG=30.0, ROI_RHO_LO=0.45, ROI_RHO_HI=1.0;
    static final double MIN_W_R=0.06, MAX_W_R=0.40;
    static final int SAMPLES=30;

    static final class Side{double[] line;double support=0,rms=Double.NaN;}

    static final class Cand{
        int id;String source="";int masks=1;
        double[] L,R,T;String fit="contour";
        Side base,left,right;
        // features
        double cx,cy,rho,thetaDeg,dthetaDeg,widthR,heightR,apex,square,sym,axisDeg,completeness,residualR;
        double tickAngle=Double.NaN,tickPitch=Double.NaN,tickScore=Double.NaN,trackR=Double.NaN,trackSpreadR=Double.NaN;
        double[] tickRaw59,tickRaw60,tickRaw01;double raw59R=Double.NaN,raw60R=Double.NaN,raw01R=Double.NaN;
        double[][] trackPts=new double[9][];   // tick inner ends at -4..+4 minutes (index k+4); +-1 and 0 unused for the frame
        int chordPairs=0;double rotationDialRadialDeg=Double.NaN,rotationChordDeg=Double.NaN;boolean plausible=true;String implausible="";
        double[] ref59,ref01;
        double gapR=Double.NaN,baseRho=Double.NaN,rotationDeg=Double.NaN,baseEdgeDeg=Double.NaN,centring=Double.NaN;
        String outline="single";boolean outsideTrack=false;
        // score terms
        double sPos,sRho,sSize,sApex,sSym,sSquare,sAxis,sFit,sTrack,sOutline,sMasks,score;
        int rank;
    }

    static final class Result{
        List<Cand> cands=new ArrayList<>();
        Cand best(){return cands.isEmpty()||!cands.get(0).plausible?null:cands.get(0);}
        String reason="";
    }

    private SubTwelveTriangle(){}

    static Result detect(Mat bgr,GmtRoundMarkerAnalyzer.DialFrame frame){
        Result res=new Result();
        double cx=frame.cx,cy=frame.cy,r=frame.r;
        Mat enh=GmtTwelveLandmarkAnalyzer.enhance(bgr);
        try{
            GmtTwelveLandmarkAnalyzer.Px px=GmtTwelveLandmarkAnalyzer.Px.of(enh);
            DialEdgeEllipseFit.Intensity I=intensity(enh);
            List<Cand> raw=enumerate(enh,cx,cy,r);
            if(raw.isEmpty()){res.reason="no triangle-shaped outline near 12";return res;}
            int id=0;
            for(Cand c:raw){
                c.id=id++;
                localFrame(px,c,cx,cy,r);
                refit(I,enh.cols(),enh.rows(),c,r);
                features(c,frame,cx,cy,r);
            }
            nesting(raw,r);
            for(Cand c:raw){score(c);classify(c);}
            rank(raw);
            res.cands=raw;
            return res;
        }finally{enh.release();}
    }

    // ------------------------------------------------------------------------------------------ enumerate
    static List<Cand> enumerate(Mat enh,double cx,double cy,double r){
        int x0=(int)Math.max(0,Math.floor(cx-0.6*r)),x1=(int)Math.min(enh.cols(),Math.ceil(cx+0.6*r));
        int y0=(int)Math.max(0,Math.floor(cy-1.05*r)),y1=(int)Math.min(enh.rows(),Math.ceil(cy-0.3*r));
        List<Cand> out=new ArrayList<>();
        if(x1-x0<10||y1-y0<10)return out;
        Mat patch=enh.submat(new Rect(x0,y0,x1-x0,y1-y0));
        List<Mat> masks=new ArrayList<>();List<String> names=new ArrayList<>();
        try{
            Mat otsu=new Mat();Imgproc.threshold(patch,otsu,0,255,Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);masks.add(otsu);names.add("otsu");
            int block=Math.max(15,((int)Math.round(r*.10))|1);
            Mat a1=new Mat();Imgproc.adaptiveThreshold(patch,a1,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,-3);masks.add(a1);names.add("adaptive3");
            Mat a2=new Mat();Imgproc.adaptiveThreshold(patch,a2,255,Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,Imgproc.THRESH_BINARY,block,-12);masks.add(a2);names.add("adaptive12");
            MatOfDouble mu=new MatOfDouble(),sd=new MatOfDouble();Core.meanStdDev(patch,mu,sd);
            Mat hi=new Mat();Imgproc.threshold(patch,hi,Math.min(250,mu.toArray()[0]+1.0*sd.toArray()[0]),255,Imgproc.THRESH_BINARY);masks.add(hi);names.add("mean+1sd");
            mu.release();sd.release();
            for(int k=0;k<masks.size();k++){
                Mat m=masks.get(k).clone();
                Mat ker=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));
                Imgproc.morphologyEx(m,m,Imgproc.MORPH_OPEN,ker);ker.release();
                List<MatOfPoint> cs=new ArrayList<>();Mat hier=new Mat();
                Imgproc.findContours(m,cs,hier,Imgproc.RETR_LIST,Imgproc.CHAIN_APPROX_NONE);
                m.release();hier.release();
                for(MatOfPoint c:cs){
                    Cand t=fromContour(c,x0,y0,cx,cy,r);
                    c.release();
                    if(t==null)continue;
                    t.source=names.get(k);
                    Cand dup=null;
                    for(Cand o:out)if(close(o,t,0.012*r)){dup=o;break;}
                    if(dup!=null){dup.masks++;dup.source+="+"+t.source;}else out.add(t);
                }
            }
        }finally{for(Mat m:masks)m.release();patch.release();}
        return out;
    }

    static boolean close(Cand a,Cand b,double tol){
        return Math.hypot(a.L[0]-b.L[0],a.L[1]-b.L[1])<tol&&Math.hypot(a.R[0]-b.R[0],a.R[1]-b.R[1])<tol&&Math.hypot(a.T[0]-b.T[0],a.T[1]-b.T[1])<tol;
    }

    static Cand fromContour(MatOfPoint c,int x0,int y0,double cx,double cy,double r){
        double area=Imgproc.contourArea(c);
        if(area<20||area>0.10*Math.PI*r*r)return null;
        Point[] p=c.toArray();if(p.length<5)return null;
        double sx=0,sy=0;for(Point q:p){sx+=q.x+x0;sy+=q.y+y0;}
        double mx=sx/p.length,my=sy/p.length,rr=Math.hypot(mx-cx,my-cy)/r,ang=clock(cx,cy,mx,my);
        if(rr<ROI_RHO_LO||rr>ROI_RHO_HI||Math.abs(ang)>ROI_HALF_ANGLE_DEG)return null;
        MatOfInt hi=new MatOfInt();Imgproc.convexHull(c,hi);int[] idx=hi.toArray();hi.release();
        Point[] hp=new Point[idx.length];for(int i=0;i<idx.length;i++)hp[i]=p[idx[i]];
        MatOfPoint2f hull=new MatOfPoint2f(hp),poly=new MatOfPoint2f();
        double per=Imgproc.arcLength(hull,true);
        Imgproc.approxPolyDP(hull,poly,.025*per,true);
        Point[] q=poly.toArray();hull.release();poly.release();
        if(q.length<3||q.length>8)return null;
        double[][] g=new double[q.length][];for(int i=0;i<q.length;i++)g[i]=new double[]{q[i].x+x0,q[i].y+y0};
        double[][] t=fromHull(g,cx,cy);if(t==null)return null;
        double triArea=Math.abs((t[1][0]-t[0][0])*(t[2][1]-t[0][1])-(t[2][0]-t[0][0])*(t[1][1]-t[0][1]))/2.0;
        if(!(triArea>1)||area/triArea<0.75||area/triArea>1.30)return null;
        double w=Math.hypot(t[1][0]-t[0][0],t[1][1]-t[0][1]);
        if(w<MIN_W_R*r||w>MAX_W_R*r)return null;
        // Apex must point inward (towards the dial centre): the 12 triangle's tip is its innermost point.
        double[] bm=mid(t[0],t[1]);
        if(Math.hypot(t[2][0]-cx,t[2][1]-cy)>=Math.hypot(bm[0]-cx,bm[1]-cy))return null;
        Cand k=new Cand();k.L=t[0];k.R=t[1];k.T=t[2];
        return k;
    }

    /** Tip = innermost hull point; base corners = extreme tangential points in the outer 40% (as the GMT primitive). */
    static double[][] fromHull(double[][] q,double cx,double cy){
        double mx=0,my=0;for(double[] p:q){mx+=p[0];my+=p[1];}mx/=q.length;my/=q.length;
        double ox=mx-cx,oy=my-cy,on=Math.hypot(ox,oy);if(on<=1e-9)return null;ox/=on;oy/=on;
        double tx=-oy,ty=ox,minRad=Double.POSITIVE_INFINITY,maxRad=-Double.MAX_VALUE;double[] tip=null;
        for(double[] p:q){double rad=(p[0]-cx)*ox+(p[1]-cy)*oy;if(rad<minRad){minRad=rad;tip=p;}maxRad=Math.max(maxRad,rad);}
        double cut=minRad+.60*(maxRad-minRad);double[] l=null,rt=null;double minT=Double.POSITIVE_INFINITY,maxT=-Double.MAX_VALUE;
        for(double[] p:q){double rad=(p[0]-cx)*ox+(p[1]-cy)*oy;if(rad<cut)continue;double tan=(p[0]-cx)*tx+(p[1]-cy)*ty;
            if(tan<minT){minT=tan;l=p;}if(tan>maxT){maxT=tan;rt=p;}}
        if(tip==null||l==null||rt==null||l==rt)return null;
        if(l[0]>rt[0]){double[] z=l;l=rt;rt=z;}
        return new double[][]{l,rt,tip};
    }

    // ------------------------------------------------------------------------------------------ local frame
    /** Tick phase/pitch near the candidate; track inner radius from ticks +-2..+-4 minutes away. */
    static void localFrame(GmtTwelveLandmarkAnalyzer.Px px,Cand c,double cx,double cy,double r){
        double[] bm=mid(c.L,c.R);
        double ang=clock(cx,cy,bm[0],bm[1]);
        double[] ta=GmtTwelveLandmarkAnalyzer.tickAnglesAt(px,cx,cy,r,ang);
        if(ta==null)return;
        double a60=ta[0]+ta[1]*Math.round(wrap180(ang-ta[0])/ta[1]);
        c.tickAngle=a60;c.tickPitch=ta[1];c.tickScore=ta[2];
        List<Double> rad=new ArrayList<>();
        for(int k:new int[]{-4,-3,-2,2,3,4}){
            double[] e=GmtTwelveLandmarkAnalyzer.tickInnerEnd(px,cx,cy,r,a60+k*ta[1],null);
            c.trackPts[k+4]=e;
            if(e!=null)rad.add(Math.hypot(e[0]-cx,e[1]-cy));
        }
        if(rad.size()>=3){
            double[] v=new double[rad.size()];for(int i=0;i<v.length;i++)v[i]=rad.get(i);Arrays.sort(v);
            c.trackR=v.length%2==1?v[v.length/2]:(v[v.length/2-1]+v[v.length/2])/2;
            c.trackSpreadR=(v[v.length-1]-v[0])/r;
        }
        c.tickRaw59=GmtTwelveLandmarkAnalyzer.tickInnerEnd(px,cx,cy,r,a60-ta[1],null);
        c.tickRaw60=GmtTwelveLandmarkAnalyzer.tickInnerEnd(px,cx,cy,r,a60,null);
        c.tickRaw01=GmtTwelveLandmarkAnalyzer.tickInnerEnd(px,cx,cy,r,a60+ta[1],null);
        if(c.tickRaw59!=null)c.raw59R=Math.hypot(c.tickRaw59[0]-cx,c.tickRaw59[1]-cy)/r;
        if(c.tickRaw60!=null)c.raw60R=Math.hypot(c.tickRaw60[0]-cx,c.tickRaw60[1]-cy)/r;
        if(c.tickRaw01!=null)c.raw01R=Math.hypot(c.tickRaw01[0]-cx,c.tickRaw01[1]-cy)/r;
        if(Double.isFinite(c.trackR)){
            c.ref59=polar(cx,cy,c.trackR,a60-ta[1]);c.ref01=polar(cx,cy,c.trackR,a60+ta[1]);
        }
    }

    // ------------------------------------------------------------------------------------------ edge refit
    static void refit(DialEdgeEllipseFit.Intensity I,int w,int h,Cand c,double r){
        for(boolean band:new boolean[]{false,true}){
            double gx=(c.L[0]+c.R[0]+c.T[0])/3.0,gy=(c.L[1]+c.R[1]+c.T[1])/3.0;
            Side b=side(I,w,h,c.L,c.R,gx,gy,r,0.12,0.88,0.045,c.ref59,c.ref01,band);
            Side rs=side(I,w,h,c.R,c.T,gx,gy,r,0.10,0.75,0.035,null,null,band);
            Side ls=side(I,w,h,c.T,c.L,gx,gy,r,0.25,0.90,0.035,null,null,band);
            if(b.line==null||rs.line==null||ls.line==null)continue;
            double[] l=TriangleEdgeRefiner.intersect(ls.line,b.line),rt=TriangleEdgeRefiner.intersect(b.line,rs.line),t=TriangleEdgeRefiner.intersect(rs.line,ls.line);
            if(l==null||rt==null||t==null)continue;
            // A refit that moves a corner more than 0.08 R has left this outline: keep the contour corners.
            double lim=0.08*r;
            if(Math.hypot(l[0]-c.L[0],l[1]-c.L[1])>lim||Math.hypot(rt[0]-c.R[0],rt[1]-c.R[1])>lim||Math.hypot(t[0]-c.T[0],t[1]-c.T[1])>lim)continue;
            if(l[0]>rt[0]){double[] z=l;l=rt;rt=z;}
            c.L=l;c.R=rt;c.T=t;c.base=b;c.right=rs;c.left=ls;c.fit=band?"edge_refit_band":"edge_refit";
            return;
        }
    }

    /** TriangleEdgeRefiner.fitSide's sampling with its diagnostics kept (support fraction, RMS). */
    static Side side(DialEdgeEllipseFit.Intensity img,int w,int h,double[] a,double[] b,double gx,double gy,double dialR,
                     double t0,double t1,double outFrac,double[] stopA,double[] stopB,boolean band){
        Side s=new Side();
        double ux=b[0]-a[0],uy=b[1]-a[1],len=Math.hypot(ux,uy);
        if(len<4)return s;
        ux/=len;uy/=len;double nx=-uy,ny=ux;double mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2;
        if((mx-gx)*nx+(my-gy)*ny<0){nx=-nx;ny=-ny;}
        double step=0.25,din=0.05*dialR;List<double[]> pts=new ArrayList<>();
        for(int k=0;k<SAMPLES;k++){
            double t=t0+(t1-t0)*k/(SAMPLES-1.0),px=a[0]+(b[0]-a[0])*t,py=a[1]+(b[1]-a[1])*t,dout=outFrac*dialR;
            if(stopA!=null&&stopB!=null){
                double lx=stopB[0]-stopA[0],ly=stopB[1]-stopA[1],den=nx*(-ly)+ny*lx;
                if(Math.abs(den)>1e-9){double q=((stopA[0]-px)*(-ly)+(stopA[1]-py)*lx)/den;if(q>0)dout=Math.min(dout,q-1.0);}
            }
            if(dout<1.5)continue;
            int n=(int)Math.floor((din+dout)/step)+1;double[] v=new double[n];boolean ok=true;
            for(int i=0;i<n;i++){double q=-din+i*step,x=px+nx*q,y=py+ny*q;if(x<1||y<1||x>w-2||y>h-2){ok=false;break;}v[i]=img.at(x,y);}
            if(!ok)continue;
            double peak=Double.NEGATIVE_INFINITY;for(double z:v)peak=Math.max(peak,z);
            int tail=Math.max(3,(int)Math.round(1.0/step));double bg=Double.POSITIVE_INFINITY;for(int i=n-tail;i<n;i++)bg=Math.min(bg,v[i]);
            if(!(peak-bg>=40.0))continue;
            double level=band?TriangleEdgeRefiner.edgeLevel(v,peak,bg,step,dialR):0.5*(peak+bg);
            if(Double.isNaN(level))continue;
            int cross=-1;for(int i=0;i<n-1;i++)if(v[i]>=level&&v[i+1]<level)cross=i;
            if(cross<0)continue;
            double frac=(v[cross]-level)/Math.max(1e-9,v[cross]-v[cross+1]),q=-din+(cross+frac)*step;
            pts.add(new double[]{px+nx*q,py+ny*q});
        }
        s.support=pts.size()/(double)SAMPLES;
        if(pts.size()<0.6*SAMPLES)return s;
        double[] line=TriangleEdgeRefiner.robustLine(pts,dialR);
        if(line==null)return s;
        if(Math.abs(line[2]*ux+line[3]*uy)<Math.cos(Math.toRadians(12)))return s;
        double ss=0;for(double[] p:pts){double d=(p[0]-line[0])*(-line[3])+(p[1]-line[1])*line[2];ss+=d*d;}
        s.rms=Math.sqrt(ss/pts.size());s.line=line;
        return s;
    }

    // ------------------------------------------------------------------------------------------ features
    static void features(Cand c,GmtRoundMarkerAnalyzer.DialFrame f,double cx,double cy,double r){
        double[] L=f.rect(c.L[0],c.L[1]),R=f.rect(c.R[0],c.R[1]),T=f.rect(c.T[0],c.T[1]);   // dial frame, squash undone (origin = centre)
        double[] bm={(L[0]+R[0])/2,(L[1]+R[1])/2},cen={(L[0]+R[0]+T[0])/3,(L[1]+R[1]+T[1])/3};
        c.cx=(c.L[0]+c.R[0]+c.T[0])/3;c.cy=(c.L[1]+c.R[1]+c.T[1])/3;
        c.rho=Math.hypot(cen[0],cen[1])/f.r;c.baseRho=Math.hypot(bm[0],bm[1])/f.r;
        c.thetaDeg=Math.toDegrees(Math.atan2(cen[0],-cen[1]));
        double width=Math.hypot(R[0]-L[0],R[1]-L[1]),height=Math.hypot(T[0]-bm[0],T[1]-bm[1]);
        c.widthR=width/f.r;c.heightR=height/f.r;
        c.apex=TriangleEdgeRefiner.apexAngleDeg(L,R,T);c.square=TriangleEdgeRefiner.squarenessDeg(L,R,T);
        double sl=Math.hypot(T[0]-L[0],T[1]-L[1]),sr=Math.hypot(T[0]-R[0],T[1]-R[1]);c.sym=Math.abs(sl-sr)/((sl+sr)/2);
        // Axis (base mid -> tip) against the inward radial through the 60 tick (or through the base mid without ticks).
        double ref=Double.isFinite(c.tickAngle)?rectClock(f,cx,cy,r,c.tickAngle):Math.toDegrees(Math.atan2(bm[0],-bm[1]));
        c.dthetaDeg=Double.isFinite(c.tickAngle)?wrap180(c.thetaDeg-ref):Double.NaN;
        double inward=Math.toRadians(ref+180);   // clock direction pointing to the centre
        double ax=Math.atan2(T[0]-bm[0],-(T[1]-bm[1]));   // clock angle of base mid -> tip
        c.axisDeg=wrap90(Math.toDegrees(ax-inward));
        c.rotationDialRadialDeg=c.axisDeg;
        // Diagnostic rotation: LOCAL track tangent from symmetric tick pairs (-k,+k), k=2..4 (none of these
        // ticks lies under the triangle). Noisier than the dial-radial reference on development data.
        double sx=0,sy=0;int n=0;
        for(int k=2;k<=4;k++){
            double[] a=c.trackPts[4-k],b=c.trackPts[4+k];
            if(a==null||b==null)continue;
            double vx=b[0]-a[0],vy=b[1]-a[1],vn=Math.hypot(vx,vy);if(vn<1e-6)continue;
            sx+=vx/vn;sy+=vy/vn;n++;
        }
        c.chordPairs=n;
        if(n>0){
            double tx=sx/Math.hypot(sx,sy),ty=sy/Math.hypot(sx,sy);
            double nx=-ty,ny=tx;   // normal; orient towards the dial centre
            if((cx-c.cx)*nx+(cy-c.cy)*ny<0){nx=-nx;ny=-ny;}
            double ix=c.T[0]-(c.L[0]+c.R[0])/2,iy=c.T[1]-(c.L[1]+c.R[1])/2;   // image-space axis, base mid -> tip
            c.rotationChordDeg=wrap90(Math.toDegrees(Math.atan2(iy,ix)-Math.atan2(ny,nx)));
        }
        // Primary rotation (development: median spread 0.4 deg vs 2.5 deg for the chord, once variants whose
        // dial fit changed ring are excluded): axis against the radial through the 60 tick in the dial frame.
        c.rotationDeg=c.rotationDialRadialDeg;
        // Base edge against the tangent at the 60 tick.
        {double be=Math.toDegrees(Math.atan2(R[1]-L[1],R[0]-L[0]));c.baseEdgeDeg=wrap90(be-tanAngle(ref));}
        if(Double.isFinite(c.trackR)){
            double tr=c.trackR/r;   // circular track radius (px of r) ~ dial frame for edge-fitted dials
            c.gapR=tr-c.baseRho;c.outsideTrack=c.baseRho>tr+0.01;
            // Centring: base mid offset along the tangent from the radial through the 60 tick, over width.
            double t=Math.toRadians(ref);double tx=Math.cos(t),ty=Math.sin(t);
            c.centring=(bm[0]*tx+bm[1]*ty)/width;
        }
        double sup=1,res=0;
        for(Side s:new Side[]{c.base,c.left,c.right}){if(s==null){sup=Math.min(sup,0);continue;}sup=Math.min(sup,s.support);if(Double.isFinite(s.rms))res=Math.max(res,s.rms);}
        c.completeness=c.base==null?0:sup;c.residualR=c.base==null?Double.NaN:res/r;
    }

    /** Image-axis angle (deg) of the tangent direction at clock angle ref (pointing clockwise). */
    static double tanAngle(double refClockDeg){double t=Math.toRadians(refClockDeg);return Math.toDegrees(Math.atan2(Math.sin(t),Math.cos(t)));}

    /** Clock angle, in the rectified dial frame, of the image point on the circle (cx,cy,r) at clockDeg. */
    static double rectClock(GmtRoundMarkerAnalyzer.DialFrame f,double cx,double cy,double r,double clockDeg){
        double[] p=polar(cx,cy,r,clockDeg),q=f.rect(p[0],p[1]);
        return Math.toDegrees(Math.atan2(q[0],-q[1]));
    }

    static double[] polar(double cx,double cy,double radius,double clockDeg){double t=Math.toRadians(clockDeg);return new double[]{cx+Math.sin(t)*radius,cy-Math.cos(t)*radius};}

    /** A candidate whose centroid sits inside a larger one with the same centre is the inner (lume) outline. */
    static void nesting(List<Cand> cs,double r){
        for(Cand a:cs)for(Cand b:cs){
            if(a==b)continue;
            if(Math.hypot(a.cx-b.cx,a.cy-b.cy)<0.03*r&&b.widthR>0&&a.widthR/b.widthR>0.55&&a.widthR/b.widthR<0.92){a.outline="inner";if(!b.outline.equals("inner"))b.outline="outer";}
        }
    }

    /** Plausibility windows (frozen v2): outside any of them a candidate is kept but cannot be selected. */
    static void classify(Cand c){
        if(c.rho<PLAUS_RHO_LO||c.rho>PLAUS_RHO_HI){c.plausible=false;c.implausible="rho";}
        else if(c.widthR<PLAUS_W_LO||c.widthR>PLAUS_W_HI){c.plausible=false;c.implausible="width";}
        else if(Double.isFinite(c.gapR)&&(c.gapR<PLAUS_GAP_LO||c.gapR>PLAUS_GAP_HI)){c.plausible=false;c.implausible="track_gap";}
        else if(!(c.apex>=PLAUS_APEX_LO&&c.apex<=PLAUS_APEX_HI)||!(c.heightR/c.widthR>=PLAUS_HW_LO&&c.heightR/c.widthR<=PLAUS_HW_HI)){c.plausible=false;c.implausible="shape";}
        else if(c.score>SCORE_MAX){c.plausible=false;c.implausible="score";}
    }

    /** Plausible candidates first, each group by score; rank 1 is the selection when plausible. */
    static void rank(List<Cand> raw){
        raw.sort((a,b)->a.plausible!=b.plausible?(a.plausible?-1:1):Double.compare(a.score,b.score));
        for(int i=0;i<raw.size();i++)raw.get(i).rank=i+1;
    }

    static double over(double v,double lo,double hi,double scale){return !Double.isFinite(v)?3:v<lo?(lo-v)/scale:v>hi?(v-hi)/scale:0;}

    static void score(Cand c){
        c.sPos=Double.isFinite(c.dthetaDeg)?Math.min(3,Math.abs(c.dthetaDeg)/POS_SCALE_DEG):1.5;
        c.sRho=Math.min(3,over(c.rho,RHO_LO,RHO_HI,0.02));
        c.sSize=Math.min(3,over(c.widthR,WIDTH_LO,WIDTH_HI,0.02));
        c.sApex=Math.min(3,over(c.apex,APEX_LO,APEX_HI,2.0));
        c.sSym=Math.min(3,c.sym/0.05);
        c.sSquare=Math.min(3,Math.abs(c.square)/3.0);
        c.sAxis=Math.min(3,Math.max(0,Math.abs(c.axisDeg)-AXIS_FREE_DEG)/4.0);
        c.sFit=c.base==null?2.0:Math.min(3,(1-c.completeness)/0.2+(Double.isFinite(c.residualR)?c.residualR/0.004:0));
        c.sTrack=c.outsideTrack?2.0:Double.isFinite(c.trackR)?0:0.5;
        c.sOutline=c.outline.equals("inner")?1.0:0;
        c.sMasks=-0.1*Math.min(4,c.masks-1);
        c.score=c.sPos+c.sRho+c.sSize+c.sApex+c.sSym+c.sSquare+c.sAxis+c.sFit+c.sTrack+c.sOutline+c.sMasks;
    }

    // ---- small helpers (from the research driver SubMeasure, unchanged) --------------------------------
    static DialEdgeEllipseFit.Intensity intensity(Mat gray){
        final int W=gray.cols(),H=gray.rows();final byte[] px=new byte[W*H];gray.get(0,0,px);
        return (x,y)->{int x0=(int)Math.floor(x),y0=(int)Math.floor(y);double fx=x-x0,fy=y-y0;int i=y0*W+x0;
            double a=px[i]&0xff,b=px[i+1]&0xff,c=px[i+W]&0xff,d=px[i+W+1]&0xff;return (a*(1-fx)+b*fx)*(1-fy)+(c*(1-fx)+d*fx)*fy;};
    }
    static double clock(double cx,double cy,double x,double y){return Math.toDegrees(Math.atan2(x-cx,cy-y));}
    static double[] mid(double[] a,double[] b){return new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2};}
    static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    static double wrap180(double d){while(d>180)d-=360;while(d<=-180)d+=360;return d;}
}
