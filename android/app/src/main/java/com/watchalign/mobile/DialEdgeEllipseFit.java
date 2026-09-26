package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Re-measures the dial centre and perspective ellipse directly from the physical
 * dark-dial to bright-rehaut boundary.
 *
 * Why this exists: the automatic dial seed takes its centre straight from a
 * HoughCircles accumulator and only refines the radius. Hough's minDist merges
 * concentric circles, so the reported centre usually belongs to whichever circle
 * voted strongest (case, bezel or crystal edge). Those parts sit above the dial,
 * so in any photo that is not perfectly square-on their projected centre is
 * displaced from the dial centre by parallax. The fitted contour ellipse was then
 * re-centred on that Hough centre, so the whole fixed master was drawn with a
 * translation error that nothing downstream measured or corrected.
 *
 * This class casts rays from the seed centre, finds the outermost persistent
 * dark-to-bright transition on each ray, and fits an ellipse to those points with
 * iterative outlier trimming. It is pure Java so it can be unit tested without
 * the native OpenCV library.
 */
final class DialEdgeEllipseFit {
    interface Intensity { double at(double x, double y); }

    static final class Fit {
        final double cx, cy;      // ellipse centre, image pixels
        final double axisA;       // semi-axis along angleDeg
        final double axisB;       // semi-axis perpendicular to angleDeg
        final double angleDeg;    // OpenCV RotatedRect convention: width axis direction
        final int points;         // inlier edge points used in the final fit
        final int rays;           // rays cast
        final double rmsPx;       // RMS radial residual of inliers
        Fit(double cx,double cy,double a,double b,double ang,int points,int rays,double rms){
            this.cx=cx;this.cy=cy;this.axisA=a;this.axisB=b;this.angleDeg=ang;
            this.points=points;this.rays=rays;this.rmsPx=rms;
        }
        double meanRadius(){return Math.sqrt(axisA*axisB);}
    }

    static final int RAYS = 180;
    private static final double MIN_CONTRAST = 18.0;   // grey levels, 0..255 image

    private DialEdgeEllipseFit(){}

    /** Returns null when the boundary is not clear enough to trust. */
    static Fit fit(Intensity img, int width, int height, double seedX, double seedY, double seedR){
        if(img==null||!(seedR>20)||!Double.isFinite(seedX)||!Double.isFinite(seedY))return null;
        List<double[]> pts=new ArrayList<>();
        double step=0.5;
        double r0=0.86*seedR, r1=1.14*seedR;
        int n=(int)Math.floor((r1-r0)/step)+1;
        int w=Math.max(6,(int)Math.round(0.035*seedR/step));   // window, in samples
        double[] v=new double[n];
        for(int k=0;k<RAYS;k++){
            double a=2*Math.PI*k/RAYS, ca=Math.cos(a), sa=Math.sin(a);
            boolean ok=true;
            for(int i=0;i<n;i++){
                double rr=r0+i*step, x=seedX+ca*rr, y=seedY+sa*rr;
                if(x<1||y<1||x>width-2||y>height-2){ok=false;break;}
                v[i]=img.at(x,y);
            }
            if(!ok)continue;
            double[] s=new double[n];Arrays.fill(s,Double.NEGATIVE_INFINITY);
            double sMax=Double.NEGATIVE_INFINITY;
            // Windows symmetric about sample i (which itself is skipped), so the score peaks
            // sharply at the boundary instead of forming a plateau that biases the pick.
            for(int i=w;i<n-w;i++){
                double in=0,out=0;
                for(int j=1;j<=w;j++){in+=v[i-j];out+=v[i+j];}
                s[i]=(out-in)/w;
                if(s[i]>sMax)sMax=s[i];
            }
            if(!(sMax>=MIN_CONTRAST))continue;
            // Outermost strong local maximum: the dial edge, not a printed minute tick
            // or hand tip that starts a little further in.
            double thr=Math.max(MIN_CONTRAST,0.55*sMax);
            int pick=-1;
            for(int i=w+1;i<n-w-1;i++){
                if(s[i]>=thr&&s[i]>=s[i-1]&&s[i]>=s[i+1])pick=i;
            }
            if(pick<0)continue;
            // Noise can leave a spurious local maximum on the outer flank of the edge peak;
            // climb to the top of the peak it belongs to.
            int lo=Math.max(w+1,pick-w/2),hi=Math.min(n-w-2,pick+w/2);
            for(int i=lo;i<=hi;i++)if(s[i]>s[pick])pick=i;
            double num=s[pick-1]-s[pick+1], den=s[pick-1]-2*s[pick]+s[pick+1];
            double sub=Math.abs(den)>1e-9?Math.max(-0.5,Math.min(0.5,0.5*num/den)):0.0;
            double rr=r0+(pick+sub)*step;
            pts.add(new double[]{seedX+ca*rr,seedY+sa*rr});
        }
        if(pts.size()<0.4*RAYS)return null;

        double[] e=null;boolean[] keep=new boolean[pts.size()];Arrays.fill(keep,true);
        double rms=Double.NaN;int used=pts.size();
        for(int iter=0;iter<5;iter++){
            e=fitConic(pts,keep,seedX,seedY,seedR);
            if(e==null)return null;
            double[] res=new double[pts.size()];
            for(int i=0;i<pts.size();i++)res[i]=radialResidual(e,pts.get(i)[0],pts.get(i)[1]);
            double[] abs=new double[pts.size()];
            for(int i=0;i<abs.length;i++)abs[i]=Math.abs(res[i]);
            double[] sorted=abs.clone();Arrays.sort(sorted);
            double mad=sorted[sorted.length/2];
            double lim=Math.max(1.0,3.0*1.4826*mad);
            boolean changed=false;used=0;double ss=0;
            for(int i=0;i<abs.length;i++){
                boolean k=abs[i]<=lim;
                if(k!=keep[i])changed=true;
                keep[i]=k;
                if(k){used++;ss+=res[i]*res[i];}
            }
            rms=used>0?Math.sqrt(ss/used):Double.NaN;
            if(used<0.4*RAYS)return null;
            if(!changed&&iter>0)break;
        }
        e=fitConic(pts,keep,seedX,seedY,seedR);
        if(e==null)return null;
        double cx=e[0],cy=e[1],a=e[2],b=e[3];
        double ratio=Math.min(a,b)/Math.max(a,b), mean=Math.sqrt(a*b);
        if(ratio<0.72)return null;
        if(mean<0.88*seedR||mean>1.12*seedR)return null;
        if(Math.hypot(cx-seedX,cy-seedY)>0.20*seedR)return null;
        if(!(rms<=Math.max(1.5,0.012*seedR)))return null;
        return new Fit(cx,cy,a,b,e[4],used,RAYS,rms);
    }

    /**
     * Linear least-squares conic with the A+C=1 normalisation, in coordinates
     * centred on the seed and scaled by its radius for conditioning.
     * Returns {cx, cy, semiAxisAlongAngle, semiAxisPerp, angleDeg} or null.
     */
    static double[] fitConic(List<double[]> pts, boolean[] keep, double ox, double oy, double scale){
        // Unknowns: B, C, D, E, F with A = 1 - C.
        // A x^2 + B xy + C y^2 + D x + E y + F = 0  ->  B xy + C (y^2 - x^2) + D x + E y + F = -x^2
        double[][] m=new double[5][6];
        int count=0;
        for(int i=0;i<pts.size();i++){
            if(keep!=null&&!keep[i])continue;
            double x=(pts.get(i)[0]-ox)/scale, y=(pts.get(i)[1]-oy)/scale;
            double[] row={x*y, y*y-x*x, x, y, 1.0};
            double rhs=-x*x;
            for(int r=0;r<5;r++){
                for(int c=0;c<5;c++)m[r][c]+=row[r]*row[c];
                m[r][5]+=row[r]*rhs;
            }
            count++;
        }
        if(count<6)return null;
        double[] sol=solve(m);
        if(sol==null)return null;
        double B=sol[0],C=sol[1],D=sol[2],E=sol[3],F=sol[4],A=1.0-C;
        double det=4*A*C-B*B;
        if(!(det>1e-12))return null;                   // not an ellipse
        double x0=(B*E-2*C*D)/det, y0=(B*D-2*A*E)/det;
        double f0=A*x0*x0+B*x0*y0+C*y0*y0+D*x0+E*y0+F;
        double th=0.5*Math.atan2(B,A-C);
        double ct=Math.cos(th),st=Math.sin(th);
        double l1=A*ct*ct+B*st*ct+C*st*st;
        double l2=A*st*st-B*st*ct+C*ct*ct;
        if(!(l1>0)||!(l2>0)||!(f0<0))return null;
        double a=Math.sqrt(-f0/l1)*scale, b=Math.sqrt(-f0/l2)*scale;
        double cx=ox+x0*scale, cy=oy+y0*scale;
        double ang=Math.toDegrees(th);
        if(b>a){double t=a;a=b;b=t;ang+=90.0;}          // major axis first, same ellipse
        while(ang>90.0)ang-=180.0;
        while(ang<=-90.0)ang+=180.0;
        double[] out={cx,cy,a,b,ang};
        for(double d:out)if(!Double.isFinite(d))return null;
        return out;
    }

    /** Signed distance along the ray from the ellipse centre: point radius minus ellipse radius. */
    static double radialResidual(double[] e,double x,double y){
        double dx=x-e[0],dy=y-e[1];
        double r=Math.hypot(dx,dy);
        if(r<1e-9)return -Math.min(e[2],e[3]);
        double t=Math.toRadians(e[4]),c=Math.cos(t),s=Math.sin(t);
        double lx=(c*dx+s*dy)/r, ly=(-s*dx+c*dy)/r;
        double denom=Math.sqrt(lx*lx/(e[2]*e[2])+ly*ly/(e[3]*e[3]));
        return r-(denom>1e-12?1.0/denom:0.0);
    }

    private static double[] solve(double[][] m){
        int n=5;
        for(int col=0;col<n;col++){
            int piv=col;
            for(int r=col+1;r<n;r++)if(Math.abs(m[r][col])>Math.abs(m[piv][col]))piv=r;
            if(Math.abs(m[piv][col])<1e-14)return null;
            double[] tmp=m[col];m[col]=m[piv];m[piv]=tmp;
            for(int r=0;r<n;r++){
                if(r==col)continue;
                double f=m[r][col]/m[col][col];
                if(f==0)continue;
                for(int c=col;c<=n;c++)m[r][c]-=f*m[col][c];
            }
        }
        double[] x=new double[n];
        for(int i=0;i<n;i++)x[i]=m[i][n]/m[i][i];
        return x;
    }
}
