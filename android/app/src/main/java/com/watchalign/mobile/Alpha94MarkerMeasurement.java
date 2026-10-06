package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Alpha94 measurement-only layer.
 *
 * Port of android/research/alpha91_marker_measurement.py: round markers, 6/9 batons, the
 * marker-ring decomposition, and the 12 triangle (displayed as research-only; not in the ring fit). The minute-lattice pose is
 * already frozen before this class runs and is only read here: nothing measured can alter H.
 * There are no QC tolerances or verdicts.
 *
 * Edges are the outer metal outline (marker footprint): the OUTERMOST significant bright-to-dark
 * transition along each outline normal, found in two passes (wide search around the master, then a
 * narrow re-search around the fitted outline) so the measurement follows the real marker. Rounds
 * use a RANSAC + least-squares circle; batons four freely fitted sides (RANSAC line + TLS). A marker
 * whose outline is crossed by a hand (inside band not bright / outside band not dial-dark), whose
 * edge coverage is low, or whose fit is physically implausible is reported as OCCLUDED.
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
        /** fitScorePx = 1 - outline integrity (0 = perfectly clean outline); fitSupport = edge coverage. */
        double fitScorePx=Double.NaN,fitSupport=Double.NaN;
        double radiusErrPx=Double.NaN;
        double canonDx=Double.NaN,canonDy=Double.NaN;
        /** 12 triangle only (research-only display): side angle errors and base tilt, + = clockwise. */
        double leftSideErrDeg=Double.NaN,rightSideErrDeg=Double.NaN,baseTiltDeg=Double.NaN;
        /** Offset in the upright dial frame, px: + = right / + = down (dial 12 at the top). */
        double dialRightPx=Double.NaN,dialDownPx=Double.NaN;
        Marker(int hour,String kind){this.hour=hour;this.kind=kind;}
    }

    static final class Ring {
        boolean usable;
        int n;
        double shiftXPx=Double.NaN,shiftYPx=Double.NaN,shiftPx=Double.NaN;
        double scalePct=Double.NaN,rotationDeg=Double.NaN;
        /** canonical ring model: translation (tx,ty), scale-1, rotation (rad) */
        double[] model;
    }

    static final class Report {
        final List<Marker> markers;
        final Ring ring;
        final double dialRadiusPx;
        /** 12 triangle: research-only measurement, never part of the ring fit. May be null. */
        final Marker triangle;
        Report(List<Marker> m,Ring r,double dialRadiusPx){this(m,r,dialRadiusPx,null);}
        Report(List<Marker> m,Ring r,double dialRadiusPx,Marker triangle){markers=m;ring=r;this.dialRadiusPx=dialRadiusPx;this.triangle=triangle;}

        Marker atHour(int h){for(Marker m:markers)if(m.hour==h)return m;return null;}

        String compactSummary(){
            StringBuilder s=new StringBuilder();
            if(ring!=null&&ring.usable){
                s.append(String.format(Locale.US,"Ring: shift %.2f px · scale %+.2f%% · rot %s",
                        ring.shiftPx,ring.scalePct,rot(ring.rotationDeg)));
            }else s.append("Ring: insufficient clean markers");
            s.append("\n").append(triangleLine(triangle));
            s.append("\n").append(markerLine(atHour(6)));
            s.append("\n").append(markerLine(atHour(9)));
            int usableRounds=0;double maxLocal=Double.NaN;
            for(Marker m:markers)if("round".equals(m.kind)&&m.usable){
                usableRounds++;
                if(Double.isFinite(m.localOffsetPx)&&(!Double.isFinite(maxLocal)||m.localOffsetPx>maxLocal))maxLocal=m.localOffsetPx;
            }
            s.append("\nRounds: ").append(usableRounds).append("/8 measured");
            if(Double.isFinite(maxLocal))s.append(String.format(Locale.US," · max local %.2f px",maxLocal));
            return s.toString();
        }

        String detailedSummary(){
            StringBuilder s=new StringBuilder(compactSummary());
            s.append("\n\nMeasurement only — no pass/fail thresholds. Directions are in the upright dial frame (12 at top).");
            for(Marker m:markers){
                s.append("\n").append(m.hour).append(": ");
                if(!m.usable){s.append("OCCLUDED / INSUFFICIENT CLEAN EDGE");continue;}
                s.append(offsetWords(m)).append(String.format(Locale.US," · radial %+.2f · tang %+.2f",m.radialPx,m.tangentialPx));
                if(Double.isFinite(m.localOffsetPx))
                    s.append(String.format(Locale.US," · local %.2f",m.localOffsetPx));
                if("baton".equals(m.kind)&&Double.isFinite(m.rotationDeg))
                    s.append(" · rot ").append(rot(m.rotationDeg));
            }
            return s.toString();
        }

        /** "1.24 px left · 0.83 px down" in the upright dial frame. */
        static String offsetWords(Marker m){
            return String.format(Locale.US,"%.2f px %s · %.2f px %s",
                    Math.abs(m.dialRightPx),m.dialRightPx<0?"left":"right",
                    Math.abs(m.dialDownPx),m.dialDownPx<0?"up":"down");
        }

        /** "+0.33° CW" / "-0.85° CCW" (sign kept, + = clockwise on the dial). */
        static String rot(double deg){
            if(!Double.isFinite(deg))return "n/a";
            return String.format(Locale.US,"%+.2f° %s",deg,deg>=0?"CW":"CCW");
        }

        private static String markerLine(Marker m){
            if(m==null)return "marker unavailable";
            if(!m.usable)return m.hour+": OCCLUDED / INSUFFICIENT CLEAN EDGE";
            String local=Double.isFinite(m.localOffsetPx)?String.format(Locale.US,"%.2f",m.localOffsetPx):"n/a";
            return m.hour+": "+offsetWords(m)+" · rot "+rot(m.rotationDeg)+" · local "+local;
        }

        private static String triangleLine(Marker m){
            if(m==null)return "12 (research): unavailable";
            if(!m.usable)return "12 (research): OCCLUDED / INSUFFICIENT CLEAN EDGE";
            return "12 (research): "+offsetWords(m)+" · centreline "+rot(m.rotationDeg)
                    +String.format(Locale.US," · L/R sides %+.2f°/%+.2f°",m.leftSideErrDeg,m.rightSideErrDeg);
        }
    }

    // Same constants as the research layer.
    private static final double WIDE=0.035,NARROW=0.012,STEP=0.0005;
    private static final double MIN_INTEGRITY=0.80;

    private Alpha94MarkerMeasurement(){}

    static Report analyse(Bitmap watch,double[] H){
        List<Marker> out=new ArrayList<>();
        double rpx=pxPerR(H);
        if(watch==null||H==null||H.length<9||!(rpx>20)){
            for(int h:HOURS){Marker m=new Marker(h,isRound(h)?"round":"baton");m.reason="pose unavailable";out.add(m);}
            return new Report(out,new Ring(),rpx);
        }
        Mat rgba=new Mat(),gray=new Mat();
        Sampler smp;
        try{
            Utils.bitmapToMat(watch,rgba);
            Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            smp=new Sampler(gray,H);
        }catch(Throwable t){
            for(int h:HOURS){Marker m=new Marker(h,isRound(h)?"round":"baton");m.reason="image unavailable";out.add(m);}
            return new Report(out,new Ring(),rpx);
        }finally{
            gray.release();rgba.release();
        }
        double tol=0.5/rpx;
        List<double[]> centres=new ArrayList<>();
        for(int hour:HOURS){
            Marker m=new Marker(hour,isRound(hour)?"round":"baton");
            double[] centre=null;
            try{
                centre=isRound(hour)?measureRound(smp,m,tol,rpx):measureBaton(smp,m,tol,rpx);
            }catch(Throwable t){
                m.usable=false;m.reason="measurement failed";
            }
            if(m.usable&&centre!=null)setOffsets(smp,m,centre,rpx);
            out.add(m);centres.add(centre);
        }
        Ring ring=fitRing(out,centres,smp,rpx);
        // 12 triangle: research-only, measured on the same frozen H, kept out of the ring fit.
        Marker tri=new Marker(12,"triangle");
        try{
            double[] c=measurePolygon(smp,tri,tol,rpx,trianglePolygon(),0.02);
            if(tri.usable&&c!=null){setOffsets(smp,tri,c,rpx);setLocal(smp,tri,ring,rpx);}
        }catch(Throwable t){tri.usable=false;tri.reason="measurement failed";}
        return new Report(out,ring,rpx,tri);
    }

    private static void setOffsets(Sampler smp,Marker m,double[] centre,double rpx){
        double[] cm=masterPoint(m.hour);
        double[] er=radial(m.hour),et=tangential(m.hour);
        Point po=smp.project(centre[0],centre[1]),pm=smp.project(cm[0],cm[1]);
        if(po==null||pm==null){m.usable=false;m.reason="projection unavailable";return;}
        m.rawDxPx=po.x-pm.x;m.rawDyPx=po.y-pm.y;m.rawOffsetPx=Math.hypot(m.rawDxPx,m.rawDyPx);
        m.canonDx=centre[0]-cm[0];m.canonDy=centre[1]-cm[1];
        m.radialPx=(m.canonDx*er[0]+m.canonDy*er[1])*rpx;
        m.tangentialPx=(m.canonDx*et[0]+m.canonDy*et[1])*rpx;
        m.dialRightPx=m.canonDx*rpx;m.dialDownPx=m.canonDy*rpx;
    }

    // ------------------------------------------------------------------ sampling (pose is read-only)
    /** Canonical-coordinate sampler over the frozen pose (read-only) and a cubic B-spline image. */
    private static final class Sampler {
        final Alpha91SplineImage img;final double[] H;
        Sampler(Mat gray,double[] H){
            this.H=H.clone();
            double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(int k=0;k<72;k++){double t=2*Math.PI*k/72;Point p=project(1.1*Math.cos(t),1.1*Math.sin(t));
                if(p==null)continue;minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
            if(!Double.isFinite(minX)){minX=0;minY=0;maxX=gray.cols();maxY=gray.rows();}
            img=new Alpha91SplineImage(gray,(int)Math.floor(minX)-4,(int)Math.floor(minY)-4,(int)Math.ceil(maxX)+5,(int)Math.ceil(maxY)+5);
        }
        Point project(double x,double y){
            double q=H[6]*x+H[7]*y+H[8];if(!Double.isFinite(q)||Math.abs(q)<1e-12)return null;
            return new Point((H[0]*x+H[1]*y+H[2])/q,(H[3]*x+H[4]*y+H[5])/q);
        }
        double at(double x,double y){Point p=project(x,y);return p==null?Double.NaN:img.at(p.x,p.y);}
    }

    /** Outermost significant bright-to-dark transition along +n within +/-span of p0.
     *  Returns {x,y,strength} in canonical units, or null. */
    private static double[] edgeOnNormal(Sampler s,double px,double py,double nx,double ny,double span){
        int n=(int)Math.floor(2*span/STEP+1e-9)+1;
        double[] v=new double[n];
        for(int i=0;i<n;i++){double t=-span+i*STEP;v[i]=s.at(px+t*nx,py+t*ny);if(!Double.isFinite(v[i]))return null;}
        double[] g=new double[n];
        g[0]=-(v[1]-v[0]);g[n-1]=-(v[n-1]-v[n-2]);
        for(int i=1;i<n-1;i++)g[i]=-(v[i+1]-v[i-1])*0.5;
        double gmax=Double.NEGATIVE_INFINITY;for(double z:g)gmax=Math.max(gmax,z);
        if(!(gmax>0))return null;
        int best=-1;
        for(int i=1;i<n-1;i++)if(g[i]>=g[i-1]&&g[i]>=g[i+1]&&g[i]>=0.4*gmax)best=i;
        if(best<0)return null;
        double den=g[best-1]-2*g[best]+g[best+1];
        double o=den!=0?0.5*(g[best-1]-g[best+1])/den:0.0;
        double t=-span+(best+o)*STEP;
        return new double[]{px+t*nx,py+t*ny,g[best]};
    }

    private static List<double[]> strong(List<double[]> pts){
        List<double[]> out=new ArrayList<>();if(pts.isEmpty())return out;
        double[] s=new double[pts.size()];for(int i=0;i<s.length;i++)s[i]=pts.get(i)[2];
        double med=median(s);for(double[] p:pts)if(p[2]>0.35*med)out.add(p);
        return out;
    }

    /** Fraction of outline samples whose inside (lume) band is bright and outside band dial-dark. */
    private static double outlineIntegrity(Sampler s,double[][] pts,double[][] normals,double rpx,double depth){
        int n=pts.length;double[] in=new double[n],outside=new double[n];
        for(int i=0;i<n;i++){
            in[i]=s.at(pts[i][0]-depth*normals[i][0],pts[i][1]-depth*normals[i][1]);
            outside[i]=s.at(pts[i][0]+2.5/rpx*normals[i][0],pts[i][1]+2.5/rpx*normals[i][1]);
            if(!Double.isFinite(in[i])||!Double.isFinite(outside[i]))return 0.0;
        }
        double mi=median(in),mo=median(outside),c=mi-mo;if(!(c>0))return 0.0;
        int ok=0;
        for(int i=0;i<n;i++)if(in[i]-outside[i]>0.5*c&&outside[i]<mo+0.35*c&&in[i]>mi-0.5*c)ok++;
        return ok/(double)n;
    }

    // ------------------------------------------------------------------ round markers
    private static double[] measureRound(Sampler s,Marker m,double tol,double rpx){
        double r0=Alpha92GmtMaster.ROUND_OUTER_R;
        double[] c=masterPoint(m.hour);double cx=c[0],cy=c[1],radius=r0;
        int rays=72;boolean[] inl=null;List<double[]> P=null;
        for(double span:new double[]{WIDE,NARROW}){
            List<double[]> raw=new ArrayList<>();
            for(int k=0;k<rays;k++){
                double t=Math.toRadians(5.0*k),nx=Math.cos(t),ny=Math.sin(t);
                double[] e=edgeOnNormal(s,cx+radius*nx,cy+radius*ny,nx,ny,span);if(e!=null)raw.add(e);
            }
            P=strong(raw);
            if(P.size()<12){m.reason="insufficient clean edge";m.fitSupport=P.size()/(double)rays;return null;}
            double[] fit=ransacCircle(P,r0,tol);
            if(fit==null){m.reason="no consistent circular outline";return null;}
            cx=fit[0];cy=fit[1];radius=fit[2];
            inl=new boolean[P.size()];int ni=0;
            for(int i=0;i<P.size();i++){inl[i]=Math.abs(Math.hypot(P.get(i)[0]-cx,P.get(i)[1]-cy)-radius)<tol;if(inl[i])ni++;}
            if(ni<6){m.reason="no consistent circular outline";return null;}
        }
        int ni=0;boolean[] oct=new boolean[8];
        for(int i=0;i<P.size();i++)if(inl[i]){
            ni++;double a=Math.toDegrees(Math.atan2(P.get(i)[1]-cy,P.get(i)[0]-cx));a=((a%360)+360)%360;
            oct[Math.min(7,(int)(a/45.0))]=true;
        }
        int octants=0;for(boolean b:oct)if(b)octants++;
        double cov=ni/(double)rays;
        int np=180;double[][] op=new double[np][2],nr=new double[np][2];
        for(int k=0;k<np;k++){double t=Math.toRadians(2.0*k);nr[k][0]=Math.cos(t);nr[k][1]=Math.sin(t);op[k][0]=cx+radius*nr[k][0];op[k][1]=cy+radius*nr[k][1];}
        double integ=outlineIntegrity(s,op,nr,rpx,0.035);
        m.fitSupport=cov;m.fitScorePx=1.0-integ;
        if(integ<MIN_INTEGRITY){m.reason=String.format(Locale.US,"hand/occluder crosses outline (clean %.2f)",integ);return null;}
        if(cov<0.60||octants<7){m.reason=String.format(Locale.US,"outline coverage %.2f, %d/8 octants",cov,octants);return null;}
        if(Math.abs(radius-r0)>0.15*r0){m.reason="implausible radius";return null;}
        m.usable=true;m.rotationDeg=0.0;m.radiusErrPx=(radius-r0)*rpx;
        return new double[]{cx,cy};
    }

    /** RANSAC circle (best inlier count, ties broken by residual) + local optimisation:
     *  refit on inliers, re-select inliers, repeat. Mirrors the research implementation. */
    private static double[] ransacCircle(List<double[]> P,double r0,double tol){
        Random rng=new Random(0);int n=P.size();int bestCount=-1;double bestRes=Double.POSITIVE_INFINITY;double[] best=null;
        for(int it=0;it<400;it++){
            int a=rng.nextInt(n),b=rng.nextInt(n),c=rng.nextInt(n);if(a==b||b==c||a==c)continue;
            double[] f=circleLsq(new double[][]{P.get(a),P.get(b),P.get(c)});
            if(f==null||!(f[2]>0.7*r0&&f[2]<1.3*r0))continue;
            int cnt=0;double res=0;
            for(double[] p:P){double e=Math.abs(Math.hypot(p[0]-f[0],p[1]-f[1])-f[2]);if(e<tol){cnt++;res+=e;}}
            if(cnt>bestCount||(cnt==bestCount&&res<bestRes)){bestCount=cnt;bestRes=res;best=f;}
        }
        if(best==null||bestCount<6)return null;
        for(int lo=0;lo<3;lo++){
            List<double[]> in=new ArrayList<>();
            for(double[] p:P)if(Math.abs(Math.hypot(p[0]-best[0],p[1]-best[1])-best[2])<tol)in.add(p);
            if(in.size()<6)return null;
            double[] f=circleLsq(in.toArray(new double[0][]));if(f==null)return null;best=f;
        }
        return best;
    }

    /** Algebraic circle: [2x 2y 1][cx cy c]^T = x^2+y^2. */
    private static double[] circleLsq(double[][] P){
        double[][] A=new double[3][3];double[] b=new double[3];
        for(double[] p:P){double[] r={2*p[0],2*p[1],1};double y=p[0]*p[0]+p[1]*p[1];
            for(int i=0;i<3;i++){b[i]+=r[i]*y;for(int j=0;j<3;j++)A[i][j]+=r[i]*r[j];}}
        double[] x=solve(A,b);if(x==null)return null;
        double rr=x[2]+x[0]*x[0]+x[1]*x[1];if(!(rr>0))return null;
        return new double[]{x[0],x[1],Math.sqrt(rr)};
    }

    // ------------------------------------------------------------------ 6/9 batons
    private static double[] measureBaton(Sampler s,Marker m,double tol,double rpx){
        return measurePolygon(s,m,tol,rpx,batonPolygon(m.hour),0.03);
    }

    /** Free per-side line fits around a master polygon (research measure_polygon): batons and 12 triangle. */
    private static double[] measurePolygon(Sampler s,Marker m,double tol,double rpx,double[][] poly,double depth){
        int k=poly.length;
        double pcx=0,pcy=0;for(double[] p:poly){pcx+=p[0]/k;pcy+=p[1]/k;}
        double[][] a=new double[k][],dm=new double[k][],nm=new double[k][];double[] L=new double[k];
        for(int i=0;i<k;i++){
            double[] p=poly[i],q=poly[(i+1)%k];L[i]=Math.hypot(q[0]-p[0],q[1]-p[1]);
            a[i]=p;dm[i]=new double[]{(q[0]-p[0])/L[i],(q[1]-p[1])/L[i]};
            double[] n={dm[i][1],-dm[i][0]};
            if(n[0]*((p[0]+q[0])/2-pcx)+n[1]*((p[1]+q[1])/2-pcy)<0){n[0]=-n[0];n[1]=-n[1];}
            nm[i]=n;
        }
        double[][] lp=new double[k][],ld=new double[k][];double[] cov=new double[k];
        for(int i=0;i<k;i++){lp[i]=a[i].clone();ld[i]=dm[i].clone();}
        for(double span:new double[]{WIDE,NARROW}){
            double[][] np=new double[k][],nd=new double[k][];
            for(int i=0;i<k;i++){
                double[] n={ld[i][1],-ld[i][0]};if(n[0]*nm[i][0]+n[1]*nm[i][1]<0){n[0]=-n[0];n[1]=-n[1];}
                List<double[]> raw=new ArrayList<>();
                for(int j=0;j<21;j++){
                    double u=0.12+j*(0.76/20.0);
                    double qx=a[i][0]+u*L[i]*dm[i][0],qy=a[i][1]+u*L[i]*dm[i][1];
                    double t=(qx-lp[i][0])*ld[i][0]+(qy-lp[i][1])*ld[i][1];
                    double[] e=edgeOnNormal(s,lp[i][0]+t*ld[i][0],lp[i][1]+t*ld[i][1],n[0],n[1],span);
                    if(e!=null)raw.add(e);
                }
                List<double[]> P=strong(raw);
                double[][] fit=P.size()>=4?ransacLine(P,tol):null;
                if(fit==null||fit[2][0]<5){np[i]=lp[i];nd[i]=ld[i];cov[i]=0.0;continue;}
                double[] d=fit[1];if(d[0]*dm[i][0]+d[1]*dm[i][1]<0){d[0]=-d[0];d[1]=-d[1];}
                np[i]=fit[0];nd[i]=d;cov[i]=fit[2][0]/21.0;
            }
            lp=np;ld=nd;
        }
        double[][] V=new double[k][];
        for(int i=0;i<k;i++){V[i]=intersect(lp[(i+k-1)%k],ld[(i+k-1)%k],lp[i],ld[i]);if(V[i]==null){m.reason="degenerate marker fit";return null;}}
        double[] centre=polygonCentroid(V);
        double[] angErr=new double[k];double maxAng=0,maxOff=0,minCov=1;
        for(int i=0;i<k;i++){
            angErr[i]=wrap90(Math.toDegrees(Math.atan2(ld[i][1],ld[i][0])-Math.atan2(dm[i][1],dm[i][0])));
            double off=(lp[i][0]-a[i][0])*nm[i][0]+(lp[i][1]-a[i][1])*nm[i][1];
            maxAng=Math.max(maxAng,Math.abs(angErr[i]));maxOff=Math.max(maxOff,Math.abs(off));minCov=Math.min(minCov,cov[i]);
        }
        double minInteg=1.0;
        for(int i=0;i<k;i++){
            double[] p=V[i],q=V[(i+1)%k];double dx=q[0]-p[0],dy=q[1]-p[1],len=Math.hypot(dx,dy);
            double[] n={dy/len,-dx/len};
            if(n[0]*((p[0]+q[0])/2-centre[0])+n[1]*((p[1]+q[1])/2-centre[1])<0){n[0]=-n[0];n[1]=-n[1];}
            double[][] seg=new double[30][],nn=new double[30][];
            for(int j=0;j<30;j++){double u=0.15+j*(0.70/29.0);seg[j]=new double[]{p[0]+u*dx,p[1]+u*dy};nn[j]=n;}
            minInteg=Math.min(minInteg,outlineIntegrity(s,seg,nn,rpx,depth));
        }
        m.fitSupport=minCov;m.fitScorePx=1.0-minInteg;
        if(minInteg<MIN_INTEGRITY){m.reason=String.format(Locale.US,"hand/occluder crosses outline (clean %.2f)",minInteg);return null;}
        if(minCov<0.50){m.reason=String.format(Locale.US,"side edge coverage %.2f",minCov);return null;}
        if(maxAng>12||maxOff>0.04){m.reason="implausible side fit";return null;}
        double[] er=radial(m.hour);
        m.usable=true;
        if(k==3){
            // research: apex = vertex nearest the dial centre; centreline = apex -> base midpoint.
            int apex=0;for(int i=1;i<3;i++)if(Math.hypot(V[i][0],V[i][1])<Math.hypot(V[apex][0],V[apex][1]))apex=i;
            double bx=0,by=0;for(int i=0;i<3;i++)if(i!=apex){bx+=V[i][0]/2;by+=V[i][1]/2;}
            m.rotationDeg=wrap90(Math.toDegrees(Math.atan2(by-V[apex][1],bx-V[apex][0])-Math.atan2(er[1],er[0])));
            // side i joins vertex i and i+1; the base side has no apex vertex. Left = smaller master x.
            int base=-1;for(int i=0;i<3;i++)if(apex!=i&&apex!=(i+1)%3)base=i;
            int sa=-1,sb=-1;for(int i=0;i<3;i++)if(i!=base){if(sa<0)sa=i;else sb=i;}
            double xa=poly[sa][0]+poly[(sa+1)%3][0],xb=poly[sb][0]+poly[(sb+1)%3][0];
            int left=xa<xb?sa:sb,right=xa<xb?sb:sa;
            m.leftSideErrDeg=angErr[left];m.rightSideErrDeg=angErr[right];m.baseTiltDeg=angErr[base];
        }else{
            double rot=0;int nl=0;
            for(int i=0;i<k;i++)if(Math.abs(dm[i][0]*er[0]+dm[i][1]*er[1])>0.7){rot+=angErr[i];nl++;}
            m.rotationDeg=nl==0?Double.NaN:rot/nl;
        }
        return centre;
    }

    /** Exhaustive two-point line search (best inlier count, ties broken by residual) + TLS with
     *  local optimisation. Returns {centroid, unit direction, {inlierCount}} or null. Mirrors research. */
    private static double[][] ransacLine(List<double[]> P,double tol){
        int n=P.size();int bestCount=-1;double bestRes=Double.POSITIVE_INFINITY;double[] bp=null,bn=null;
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            double dx=P.get(j)[0]-P.get(i)[0],dy=P.get(j)[1]-P.get(i)[1],L=Math.hypot(dx,dy);if(L<1e-9)continue;
            double nx=-dy/L,ny=dx/L;int c=0;double res=0;
            for(double[] q:P){double e=Math.abs((q[0]-P.get(i)[0])*nx+(q[1]-P.get(i)[1])*ny);if(e<tol){c++;res+=e;}}
            if(c>bestCount||(c==bestCount&&res<bestRes)){bestCount=c;bestRes=res;bp=P.get(i);bn=new double[]{nx,ny};}
        }
        if(bp==null)return null;
        double[] c0=bp,nn=bn;double mx=0,my=0,th=0;int cnt=0;
        for(int lo=0;lo<4;lo++){
            mx=0;my=0;cnt=0;
            for(double[] q:P)if(Math.abs((q[0]-c0[0])*nn[0]+(q[1]-c0[1])*nn[1])<tol){mx+=q[0];my+=q[1];cnt++;}
            if(cnt<2)return null;mx/=cnt;my/=cnt;double sxx=0,sxy=0,syy=0;
            for(double[] q:P)if(Math.abs((q[0]-c0[0])*nn[0]+(q[1]-c0[1])*nn[1])<tol){double dx=q[0]-mx,dy=q[1]-my;sxx+=dx*dx;sxy+=dx*dy;syy+=dy*dy;}
            th=0.5*Math.atan2(2*sxy,sxx-syy);
            c0=new double[]{mx,my};nn=new double[]{-Math.sin(th),Math.cos(th)};
        }
        int fin=0;for(double[] q:P)if(Math.abs((q[0]-mx)*nn[0]+(q[1]-my)*nn[1])<tol)fin++;
        return new double[][]{{mx,my},{Math.cos(th),Math.sin(th)},{fin}};
    }

    private static double[] intersect(double[] p1,double[] d1,double[] p2,double[] d2){
        double det=d1[0]*(-d2[1])-(-d2[0])*d1[1];if(Math.abs(det)<1e-12)return null;
        double rx=p2[0]-p1[0],ry=p2[1]-p1[1];
        double t=(rx*(-d2[1])-(-d2[0])*ry)/det;
        return new double[]{p1[0]+t*d1[0],p1[1]+t*d1[1]};
    }

    private static double[] polygonCentroid(double[][] V){
        double A=0,cx=0,cy=0;int n=V.length;
        for(int i=0;i<n;i++){double[] p=V[i],q=V[(i+1)%n];double cr=p[0]*q[1]-q[0]*p[1];A+=cr;cx+=(p[0]+q[0])*cr;cy+=(p[1]+q[1])*cr;}
        A*=0.5;return new double[]{cx/(6*A),cy/(6*A)};
    }

    // ------------------------------------------------------------------ ring decomposition
    private static Ring fitRing(List<Marker> markers,List<double[]> centres,Sampler s,double rpx){
        Ring ring=new Ring();
        List<Integer> idx=new ArrayList<>();
        for(int i=0;i<markers.size();i++)if(markers.get(i).usable&&centres.get(i)!=null)idx.add(i);
        if(idx.size()<5)return ring;
        int n=idx.size();double[][] A=new double[2*n][4];double[] b=new double[2*n];double[][] P=new double[n][];
        for(int k=0;k<n;k++){
            Marker m=markers.get(idx.get(k));double[] p=masterPoint(m.hour);P[k]=p;
            A[2*k]=new double[]{1,0,p[0],-p[1]};A[2*k+1]=new double[]{0,1,p[1],p[0]};
            b[2*k]=m.canonDx;b[2*k+1]=m.canonDy;
        }
        double[] w=new double[2*n];Arrays.fill(w,1.0);double[] x=null;
        for(int it=0;it<10;it++){
            double[][] N=new double[4][4];double[] r=new double[4];
            for(int i=0;i<2*n;i++)for(int a=0;a<4;a++){r[a]+=A[i][a]*b[i]*w[i];for(int c=0;c<4;c++)N[a][c]+=A[i][a]*A[i][c]*w[i];}
            x=solve(N,r);if(x==null)return ring;
            for(int i=0;i<2*n;i++){
                double res=(b[i]-(A[i][0]*x[0]+A[i][1]*x[1]+A[i][2]*x[2]+A[i][3]*x[3]))*rpx;
                w[i]=Math.abs(res)<=1.0?1.0:1.0/Math.max(Math.abs(res),1e-9);   // Huber, 1 px
            }
        }
        Point t1=s.project(x[0],x[1]),t0=s.project(0,0);
        ring.usable=true;ring.n=n;ring.scalePct=100*x[2];ring.rotationDeg=Math.toDegrees(x[3]);ring.model=x.clone();
        if(t1!=null&&t0!=null){ring.shiftXPx=t1.x-t0.x;ring.shiftYPx=t1.y-t0.y;ring.shiftPx=Math.hypot(ring.shiftXPx,ring.shiftYPx);}
        for(int k=0;k<n;k++)setLocal(s,markers.get(idx.get(k)),ring,rpx);
        return ring;
    }

    /** Local residual = offset minus the ring model evaluated at this marker's master point. */
    private static void setLocal(Sampler s,Marker m,Ring ring,double rpx){
        if(ring==null||!ring.usable||ring.model==null||!Double.isFinite(m.canonDx))return;
        double[] x=ring.model,p=masterPoint(m.hour);
        double mdx=x[0]+x[2]*p[0]-x[3]*p[1],mdy=x[1]+x[2]*p[1]+x[3]*p[0];
        double lx=m.canonDx-mdx,ly=m.canonDy-mdy;
        Point q=s.project(p[0]+lx,p[1]+ly),q0=s.project(p[0],p[1]);
        double[] er=radial(m.hour),et=tangential(m.hour);
        m.localOffsetPx=(q!=null&&q0!=null)?Math.hypot(q.x-q0.x,q.y-q0.y):Math.hypot(lx,ly)*rpx;
        m.localRadialPx=(lx*er[0]+ly*er[1])*rpx;
        m.localTangentialPx=(lx*et[0]+ly*et[1])*rpx;
    }

    // ------------------------------------------------------------------ geometry helpers
    private static boolean isRound(int h){for(int q:ROUND_HOURS)if(q==h)return true;return false;}
    private static double[] radial(int hour){double a=Math.toRadians(hour*30.0);return new double[]{Math.sin(a),-Math.cos(a)};}
    private static double[] tangential(int hour){double a=Math.toRadians(hour*30.0);return new double[]{Math.cos(a),Math.sin(a)};}
    private static double[] masterPoint(int hour){
        if(hour==12)return new double[]{0,-Alpha92GmtMaster.TRI_AREA_CENTROID_R};
        double r=isRound(hour)?Alpha92GmtMaster.ROUND_CENTER_R:Alpha92GmtMaster.BATON_CENTER_R;
        double[] er=radial(hour);return new double[]{er[0]*r,er[1]*r};
    }
    /** Research marker_polygon(12): apex (toward centre), base right, base left. */
    private static double[][] trianglePolygon(){
        return new double[][]{{0,-Alpha92GmtMaster.TRI_APEX_R},{Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R},
                {-Alpha92GmtMaster.TRI_HALF_BASE,-Alpha92GmtMaster.TRI_BASE_R}};
    }
    /** Research marker_polygon ordering: (s,u) = (-1,-1),(1,-1),(1,1),(-1,1) along (radial, tangential). */
    private static double[][] batonPolygon(int hour){
        double[] er=radial(hour),et=tangential(hour);double c=Alpha92GmtMaster.BATON_CENTER_R;
        double rh=Alpha92GmtMaster.BATON_RADIAL_HALF,th=Alpha92GmtMaster.BATON_TANGENTIAL_HALF;
        int[][] su={{-1,-1},{1,-1},{1,1},{-1,1}};double[][] v=new double[4][];
        for(int i=0;i<4;i++)v[i]=new double[]{c*er[0]+su[i][0]*rh*er[0]+su[i][1]*th*et[0],c*er[1]+su[i][0]*rh*er[1]+su[i][1]*th*et[1]};
        return v;
    }
    private static double pxPerR(double[] H){
        if(H==null||H.length<9)return Double.NaN;
        double z0=H[8],z1=H[6]+H[8];if(Math.abs(z0)<1e-12||Math.abs(z1)<1e-12)return Double.NaN;
        double x0=H[2]/z0,y0=H[5]/z0,x1=(H[0]+H[2])/z1,y1=(H[3]+H[5])/z1;
        return Math.hypot(x1-x0,y1-y0);
    }
    private static double wrap90(double d){while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    private static double median(double[] a){
        double[] b=Arrays.stream(a).filter(Double::isFinite).toArray();if(b.length==0)return Double.NaN;Arrays.sort(b);
        return b.length%2==1?b[b.length/2]:0.5*(b[b.length/2-1]+b[b.length/2]);
    }
    private static double[] solve(double[][] A,double[] b){
        int n=b.length;double[][] m=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(A[i],0,m[i],0,n);m[i][n]=b[i];}
        for(int col=0;col<n;col++){
            int piv=col;for(int r=col+1;r<n;r++)if(Math.abs(m[r][col])>Math.abs(m[piv][col]))piv=r;
            if(Math.abs(m[piv][col])<1e-15)return null;double[] tmp=m[col];m[col]=m[piv];m[piv]=tmp;
            double d=m[col][col];for(int c=col;c<=n;c++)m[col][c]/=d;
            for(int r=0;r<n;r++)if(r!=col){double f=m[r][col];if(f!=0)for(int c=col;c<=n;c++)m[r][c]-=f*m[col][c];}
        }
        double[] x=new double[n];for(int i=0;i<n;i++){x[i]=m[i][n];if(!Double.isFinite(x[i]))return null;}
        return x;
    }
}
