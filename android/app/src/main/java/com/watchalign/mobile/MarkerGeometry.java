package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Phase B (owner-approved 22 Oct 2026, behind a flag): the recommended two-level marker measurement of
 * tools/research/measurement_architecture/REPORT.md, ported from methods.py (LB and the ring level) so that the app,
 * genuine reference analysis, archive validation and calibration can read the SAME physical measurement.
 *
 *   marker level  each marker's outer-metal centre (MetalFit) against a robust (Tukey biweight, c = 0.30 % R) similarity
 *                 ring fitted to the OTHER eligible round markers and batons, against the fixed model geometry:
 *                 tangential / radial residual (% R) and rotational residual (deg). Parallax, registration,
 *                 perspective and camera distance are shared by the ring and cancel; one defective marker cannot move
 *                 the ring the others are measured against.
 *   ring level    the similarity of all eligible round markers and batons (production Huber weights): translation
 *                 minus the parallax of raised markers (K_PARALLAX * tan(tilt) towards the far side), rotation and
 *                 scale, so a whole-ring defect is reported, never normalised away.
 *
 * MEASUREMENT ONLY. Nothing here judges a marker: there are no limits, and ClearlyOff still decides every answer. It runs
 * in the app only when ENABLED (system property watchalign.markergeometry=on; off by default), and never changes the
 * pose, the measurements or the results it reads. Parity with methods.py: MarkerGeometryTest
 * (android/app/src/test/resources/marker_geometry/parity.csv).
 */
final class MarkerGeometry {
    private MarkerGeometry(){}

    /** off by default: the app's results are unchanged unless this is switched on explicitly */
    static final boolean ENABLED="on".equals(System.getProperty("watchalign.markergeometry"));
    /** Tukey biweight tuning (R), REPORT.md addendum A.1 */
    static final double BIWEIGHT_C=0.0030;
    /** effective height of the raised markers above the printed minute track (R per unit tan(tilt)); 217 genuine photos,
     *  REPORT.md section 2 (research constant; not a QC limit) */
    static final double K_PARALLAX=0.0228;
    static final int MIN_OTHERS=5,HUBER_ITERS=10,BIWEIGHT_ITERS=20;
    /** archive eligibility (tools/image_archive/eligibility.py): coverage bounds and MetalFit.MIN_COVER */
    static final double COVER_PASS=0.90,COVER_FAIL=0.80,MIN_COVER=0.5;

    static final class In {
        final int hour;final ModelSpec.Shape shape;final double fx,fy,ox,oy,tiltAbsDeg;final boolean eligible;
        /** fixed model centre (fx, fy), measured outer-metal centre (ox, oy), canonical R; tiltAbsDeg NaN for rounds */
        In(int hour,ModelSpec.Shape shape,double fx,double fy,double ox,double oy,double tiltAbsDeg,boolean eligible){
            this.hour=hour;this.shape=shape;this.fx=fx;this.fy=fy;this.ox=ox;this.oy=oy;this.tiltAbsDeg=tiltAbsDeg;this.eligible=eligible;}
        boolean ring(){return eligible&&shape!=ModelSpec.Shape.TRIANGLE;}
    }

    static final class Marker {
        final int hour;final ModelSpec.Shape shape;final boolean eligible;
        /** % R (tangential + clockwise, radial + outward) and deg; NaN when fewer than MIN_OTHERS eligible others */
        double tangPct=Double.NaN,radPct=Double.NaN,tiltDeg=Double.NaN;int others;
        Marker(int hour,ModelSpec.Shape shape,boolean eligible){this.hour=hour;this.shape=shape;this.eligible=eligible;}
        double posPct(){return Math.hypot(tangPct,radPct);}
    }

    static final class Ring {
        boolean ok;int n;
        /** the hours the ring fit used (eligible round markers and batons), ascending */
        int[] hours=new int[0];
        double txPct=Double.NaN,tyPct=Double.NaN,scalePct=Double.NaN,rotDeg=Double.NaN,tiltDeg=Double.NaN,farX,farY;
        /** translation minus the parallax prediction (% R, canonical x right / y down) */
        double placeXPct=Double.NaN,placeYPct=Double.NaN;
        double placePct(){return Math.hypot(placeXPct,placeYPct);}
    }

    static final class Result {
        final List<Marker> markers=new ArrayList<>();final Ring ring=new Ring();
        Marker at(int h){for(Marker m:markers)if(m.hour==h)return m;return null;}
    }

    // ------------------------------------------------------------------------------------------------ pipeline adapter

    /** the stage on a finished production analysis: MetalFit centres, the model geometry and the pose */
    static Result fromPipeline(ClearlyOff.Result co,Alpha94MarkerMeasurement.Report a94,ModelSpec model,ModelReference metal,double[] H){
        List<In> in=new ArrayList<>();
        if(co==null||model==null||H==null)return compute(H,Double.NaN,in,K_PARALLAX);
        for(ClearlyOff.Marker m:co.markers){
            ModelSpec.Marker mk=model.atHour(m.hour);
            if(mk==null||m.cObs==null)continue;
            double[][] poly=ExpectedMarkerOutline.polygon(mk,metal,null);if(poly==null||poly.length<3)continue;
            double[] fix=ClearlyOff.mean(poly);
            double tilt=Double.NaN;
            if(m.shape!=ModelSpec.Shape.ROUND&&Double.isFinite(m.fitTiltDeg)){
                double[] loo=LuminousAlignment.ringWithout(a94,m.hour);
                tilt=m.fitTiltDeg+(loo!=null&&loo.length>3&&Double.isFinite(loo[3])?Math.toDegrees(loo[3]):0.0);
            }
            boolean ok=eligible(WatchClassification.credible(m),WatchClassification.reliable(m),m.cover,m.handConfirmed,m.uncertain);
            in.add(new In(m.hour,m.shape,fix[0],fix[1],m.cObs[0],m.cObs[1],tilt,ok));
        }
        return compute(H,co.rpx,in,K_PARALLAX);
    }

    // ------------------------------------------------------------------------------------------------ eligibility

    static final Pattern HIDDEN=Pattern.compile("of its outline is visible, the rest is reconstructed"),
            ROUGH=Pattern.compile("its visible edge is rough"),INCONSISTENT=Pattern.compile("\\d+% of its visible edge is inconsistent"),
            LOWRES=Pattern.compile("resolution is low");

    /** ELIGIBLE under the archive rules (eligibility.status_from_evidence): credible; no confirmed hand; outline at least
     *  90 % seen; reliable, or its only soft warning is the harmless inconsistent-edge reflection */
    static boolean eligible(boolean credible,boolean reliable,double cover,boolean hand,String soft){
        if(!credible)return false;
        boolean cov=Double.isFinite(cover);
        if(hand||cov&&cover<MIN_COVER||cov&&cover<COVER_FAIL)return false;
        if(!reliable&&!onlyHarmless(soft))return false;
        return cov&&cover>=COVER_PASS;
    }

    /** every clause of the soft warning is the inconsistent-edge class (an empty warning is NOT harmless) */
    static boolean onlyHarmless(String soft){
        if(soft==null)return false;
        boolean any=false;
        for(String c:soft.split(";")){
            String clause=c.trim();if(clause.isEmpty())continue;
            boolean incon=INCONSISTENT.matcher(clause).find();
            boolean other=HIDDEN.matcher(clause).find()||ROUGH.matcher(clause).find()||LOWRES.matcher(clause).find();
            if(!incon||other)return false;
            any=true;
        }
        return any;
    }

    // ------------------------------------------------------------------------------------------------ the measurement

    static Result compute(double[] H,double rpx,List<In> in,double k){
        Result res=new Result();
        List<In> ring=new ArrayList<>();
        for(In m:in)if(m.ring())ring.add(m);
        for(In m:in){
            Marker r=new Marker(m.hour,m.shape,m.eligible);res.markers.add(r);
            List<In> others=new ArrayList<>();for(In g:ring)if(g.hour!=m.hour)others.add(g);
            r.others=others.size();
            if(others.size()<MIN_OTHERS||!(rpx>0))continue;
            double[] sol=biweight(others,rpx,BIWEIGHT_C);if(sol==null)continue;
            double[] e={(m.ox-m.fx)-predX(sol,m.fx,m.fy),(m.oy-m.fy)-predY(sol,m.fx,m.fy)};
            double a=Math.toRadians(30*(m.hour%12));
            r.tangPct=100*(e[0]*Math.cos(a)+e[1]*Math.sin(a));
            r.radPct=100*(e[0]*Math.sin(a)-e[1]*Math.cos(a));
            if(Double.isFinite(m.tiltAbsDeg))r.tiltDeg=m.tiltAbsDeg-Math.toDegrees(sol[3]);
        }
        if(ring.size()>=MIN_OTHERS&&rpx>0){
            double[] sol=huber(ring,rpx);
            if(sol!=null){
                Ring g=res.ring;g.ok=true;g.n=ring.size();
                g.hours=new int[ring.size()];for(int i=0;i<g.hours.length;i++)g.hours[i]=ring.get(i).hour;java.util.Arrays.sort(g.hours);
                g.txPct=100*sol[0];g.tyPct=100*sol[1];g.scalePct=100*sol[2];g.rotDeg=Math.toDegrees(sol[3]);
                double[] p=pose(H);
                g.tiltDeg=Math.toDegrees(p[0]);g.farX=p[1];g.farY=p[2];
                if(Double.isFinite(p[0])&&Double.isFinite(k)){
                    double t=Math.tan(p[0]);g.placeXPct=100*(sol[0]-k*t*p[1]);g.placeYPct=100*(sol[1]-k*t*p[2]);
                }
            }
        }
        return res;
    }

    static double predX(double[] s,double x,double y){return s[0]+s[2]*x-s[3]*y;}
    static double predY(double[] s,double x,double y){return s[1]+s[2]*y+s[3]*x;}

    /** robust similarity (Huber, 1 px) of the rows, as Alpha94MarkerMeasurement.fitRing / methods.similarity */
    static double[] huber(List<In> rows,double rpx){
        int n=rows.size();if(n<3)return null;
        double[] w=new double[2*n];java.util.Arrays.fill(w,1.0);double[] sol=null;
        for(int it=0;it<HUBER_ITERS;it++){
            sol=solve(rows,w);if(sol==null)return null;
            for(int i=0;i<n;i++){In q=rows.get(i);
                double rx=Math.abs((q.ox-q.fx)-predX(sol,q.fx,q.fy))*rpx,ry=Math.abs((q.oy-q.fy)-predY(sol,q.fx,q.fy))*rpx;
                w[2*i]=rx<=1?1:1/Math.max(rx,1e-9);w[2*i+1]=ry<=1?1:1/Math.max(ry,1e-9);}
        }
        return sol;
    }

    /** Tukey biweight on each row's 2-D residual, started from the Huber fit (methods.biweight) */
    static double[] biweight(List<In> rows,double rpx,double c){
        double[] sol=huber(rows,rpx);if(sol==null)return null;
        int n=rows.size();double[] w=new double[2*n];
        for(int it=0;it<BIWEIGHT_ITERS;it++){
            int live=0;
            for(int i=0;i<n;i++){In q=rows.get(i);
                double rx=(q.ox-q.fx)-predX(sol,q.fx,q.fy),ry=(q.oy-q.fy)-predY(sol,q.fx,q.fy);
                double u=Math.hypot(rx,ry)/c,wm=u<1?(1-u*u)*(1-u*u):0.0;if(wm>0)live++;
                w[2*i]=wm;w[2*i+1]=wm;}
            if(live<4)return sol;
            double[] next=solve(rows,w);if(next==null)return sol;
            sol=next;
        }
        return sol;
    }

    /** weighted least squares for (tx, ty, s, phi): rows [1 0 x -y; 0 1 y x] against (dx, dy) */
    static double[] solve(List<In> rows,double[] w){
        double[][] N=new double[4][4];double[] b=new double[4];
        for(int i=0;i<rows.size();i++){In q=rows.get(i);double dx=q.ox-q.fx,dy=q.oy-q.fy;
            double[][] A={{1,0,q.fx,-q.fy},{0,1,q.fy,q.fx}};double[] y={dx,dy};
            for(int j=0;j<2;j++){double wj=w[2*i+j];for(int a=0;a<4;a++){b[a]+=wj*A[j][a]*y[j];for(int cc=0;cc<4;cc++)N[a][cc]+=wj*A[j][a]*A[j][cc];}}}
        return gauss(N,b);
    }

    static double[] gauss(double[][] A,double[] b){
        int n=b.length;double[][] M=new double[n][n+1];
        for(int i=0;i<n;i++){System.arraycopy(A[i],0,M[i],0,n);M[i][n]=b[i];}
        for(int c=0;c<n;c++){
            int p=c;for(int r=c+1;r<n;r++)if(Math.abs(M[r][c])>Math.abs(M[p][c]))p=r;
            if(Math.abs(M[p][c])<1e-14)return null;
            double[] t=M[c];M[c]=M[p];M[p]=t;
            for(int r=0;r<n;r++){if(r==c)continue;double f=M[r][c]/M[c][c];for(int k=c;k<=n;k++)M[r][k]-=f*M[c][k];}
        }
        double[] x=new double[n];for(int i=0;i<n;i++)x[i]=M[i][n]/M[i][i];return x;
    }

    /** {tilt rad, far-side unit x, y} of the dial plane from canonical -> image H (methods.pose) */
    static double[] pose(double[] H){
        if(H==null||H.length<9||!(Math.abs(H[8])>1e-12))return new double[]{Double.NaN,0,0};
        double[] h=new double[9];for(int i=0;i<9;i++)h[i]=H[i]/H[8];
        double x0=h[2],y0=h[5];
        double a=h[0]-x0*h[6],b=h[1]-x0*h[7],c=h[3]-y0*h[6],d=h[4]-y0*h[7];
        // singular values of [[a b][c d]]
        double s1=a*a+b*b+c*c+d*d,det=Math.abs(a*d-b*c);
        double big=Math.sqrt((s1+Math.sqrt(Math.max(0,s1*s1-4*det*det)))/2),small=big>0?det/big:0;
        double t=Math.acos(Math.min(1.0,small/big));
        double px=h[6],py=h[7],nn=Math.hypot(px,py);
        return new double[]{t,nn>0?px/nn:0,nn>0?py/nn:0};
    }

    /** one line for the technical details */
    static String summary(Result r){
        StringBuilder s=new StringBuilder("Marker geometry (research, not judged):");
        if(r.ring.ok)s.append(String.format(Locale.US," ring placement %.2f%% R, rotation %+.2f°, scale %+.2f%% (%d markers)",
                r.ring.placePct(),r.ring.rotDeg,r.ring.scalePct,r.ring.n));
        for(Marker m:r.markers)if(Double.isFinite(m.tangPct))
            s.append(String.format(Locale.US,"\n  %d: %+.2f / %+.2f %% R%s%s",m.hour,m.tangPct,m.radPct,
                    Double.isFinite(m.tiltDeg)?String.format(Locale.US," · %+.2f°",m.tiltDeg):"",m.eligible?"":" (not eligible)"));
        return s.toString();
    }
}
