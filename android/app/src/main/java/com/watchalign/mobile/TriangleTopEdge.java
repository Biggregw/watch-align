package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * EXPERIMENTAL (owner, 21 Oct 2026; research only, never used for a verdict): the 12 triangle's radial position from
 * its outer metal TOP edge (the wide edge nearest the minute track), measured directly, against a reference circle
 * established independently from the other markers of the same photo.
 *
 * The existing engine (MetalFit) fits the whole fixed-size outline and reads the radial position from the fitted centre;
 * a reflection, bevel or hand on the two long diagonal sides can move that centre. Here only the straight top edge is
 * used, and its position is the line's perpendicular distance from the dial centre, which a sideways move does not
 * change:
 *
 *   1. Profiles along the hour line's direction, across the genuine top edge's width (corners left out), from inside
 *      the triangle out to just short of this photo's minute-tick inner ends.
 *   2. On each profile the outermost rim-to-dial crossing, with MetalFit's rule: half-way between the dial and the
 *      rim's own level (a shadowed rim is darker than the lume and must not be skipped for the lume boundary inside
 *      it); a profile with no dark dial beyond the crossing, or bright again outside it, is left out.
 *   3. A straight line through the edge points by consensus (RANSAC over every pair, then least squares on the
 *      inliers): a hand or reflection that moves some profiles does not move the line.
 *   4. Measured radius = the line's distance from the dial centre. Reference circle = the genuine top edge's distance
 *      times this photo's ring scale (median measured/genuine centre radius of the round markers, the 12 itself never
 *      included; reliably fitted rounds preferred), moved out by the round markers' own measured edge offset on this
 *      photo (how far the same edge rule finds their outer metal edge outside their genuine outline; MetalFit's
 *      per-shape bias), so the circle stands where the rounds' measured outer edges put the triangle's top edge and the
 *      detector's photo-wide offset cancels. Radial displacement = measured - reference (+ outward). The same without
 *      the rounds' edge offset is kept too (radialRawPct).
 *
 * Nothing here is chosen with the genuine or replica limits: the edge comes from the image and the geometry only.
 * Internal edge-selection parameters (documented, tested in TriangleTopEdgeTest): INLIER_PX, MIN_POINTS, MIN_FRAC,
 * MIN_SPAN, SECOND_FRAC.
 */
final class TriangleTopEdge {
    private TriangleTopEdge(){}

    /** profiles every this many px along the edge; samples every PROFILE_STEP_PX along each profile */
    static final double STEP_PX=1.0,PROFILE_STEP_PX=0.25;
    /** the share of the edge's half width at each end left out (the rounded corners) */
    static final double CORNER_FRAC=0.15;
    /** search from this far inside the expected edge (R) out to this far short of the tick inner ends (R) */
    static final double INSIDE_R=0.035,TRACK_CLEAR_R=0.003;
    static final double MIN_CONTRAST=30,RIM_FRAC=0.25,RIM_PX=1.5;
    /** consensus tolerance (px): an edge point within this of the line is an inlier */
    static final double INLIER_PX=0.5;
    /** the line is measured only with at least MIN_POINTS inliers, MIN_FRAC of the usable profiles, spanning MIN_SPAN of
     *  the edge's searched width */
    static final int MIN_POINTS=12;
    static final double MIN_FRAC=0.5,MIN_SPAN=0.3;
    /** a second consensus line with at least this share of the first's inliers, more than 1 px away: two candidate edges */
    static final double SECOND_FRAC=0.5;

    static final class Result {
        /** empty when measured; otherwise why not (plain words) */
        String note="";
        /** the hour line's angle (rad), the edge's genuine distance from the centre (R), ring scale and its markers */
        double a0=Double.NaN,genuineR=Double.NaN,scale=Double.NaN;int ringMarkers;boolean ringFromUncertain;
        /** expected and measured top-edge distance from the dial centre (R), and the displacement (% R, px; + outward) */
        double expectedR=Double.NaN,measuredR=Double.NaN,radialPct=Double.NaN,radialPx=Double.NaN;
        /** the round markers' measured edge offset used for the reference circle (R) and the displacement without it */
        double roundEdgeOffsetR=Double.NaN,radialRawPct=Double.NaN;
        /** the edge's turn against the line square to the hour line (deg; the sign of the existing triangle rotation) */
        double angleDeg=Double.NaN;
        /** profiles searched, usable (an edge found), inliers; inlier RMS (px); share of the searched width spanned */
        int profiles,usable,inliers;double rmsPx=Double.NaN,span=Double.NaN;
        /** a second, parallel consensus edge: its share of the first's inliers and offset from it (px) */
        double secondFrac=0,secondOffsetPx=Double.NaN;
        /** fitted edge endpoints (canonical, over the inliers' extent), the tick inner-end radius, and every edge point
         *  (canonical) with whether it is an inlier (for the evidence picture) */
        double[] p1,p2;double trackR=Double.NaN;
        final List<double[]> points=new ArrayList<>();final List<Boolean> inlier=new ArrayList<>();
        boolean ok(){return note.isEmpty()&&Double.isFinite(radialPct);}
        boolean ambiguous(){return secondFrac>=SECOND_FRAC;}
        String summary(){
            if(!ok())return "top edge not measured: "+note;
            return String.format(Locale.US,"top edge %.4f R (reference circle %.4f R from %d %s round markers): %+.3f %% R (%+.2f px); edge turned %+.2f deg; %d of %d profiles on one line (RMS %.2f px, %.0f %% of the edge)%s",
                    measuredR,expectedR,ringMarkers,ringFromUncertain?"(incl. uncertain)":"reliable",radialPct,radialPx,angleDeg,inliers,usable,rmsPx,100*span,
                    ambiguous()?String.format(Locale.US,"; AMBIGUOUS: a second edge %.1f px away carries %.0f %% as many points",secondOffsetPx,100*secondFrac):"");
        }
    }

    /**
     * @param img      the photo (canonical sampling)
     * @param yellow   the triangle's genuine outer-metal outline (canonical, as placed by the registration)
     * @param a0       the 12's hour line angle from the minute ticks (rad, clockwise from 12)
     * @param trackR   this photo's minute-tick inner-end radius above the triangle (canonical)
     * @param scale    ring scale from the other round markers (measured / genuine centre radius)
     */
    static Result measure(ClearlyOff.Img img,double[][] yellow,double a0,double trackR,double scale,int ringMarkers,boolean ringFromUncertain,double rpx){
        return measure(img,yellow,a0,trackR,scale,ringMarkers,ringFromUncertain,0,rpx);
    }
    /** @param roundEdgeOffsetR the round markers' measured edge offset on this photo (R, + outward; 0 when unknown) */
    static Result measure(ClearlyOff.Img img,double[][] yellow,double a0,double trackR,double scale,int ringMarkers,boolean ringFromUncertain,double roundEdgeOffsetR,double rpx){
        Result out=new Result();out.a0=a0;out.scale=scale;out.ringMarkers=ringMarkers;out.ringFromUncertain=ringFromUncertain;out.trackR=trackR;
        out.roundEdgeOffsetR=Double.isFinite(roundEdgeOffsetR)?roundEdgeOffsetR:0;
        if(yellow==null||yellow.length<3){out.note="no outline for the triangle";return out;}
        if(!Double.isFinite(a0)){out.note="its hour line could not be placed from the minute track";return out;}
        if(!Double.isFinite(trackR)){out.note="the minute-tick ends above it could not be measured";return out;}
        if(!Double.isFinite(scale)){out.note="too few other round markers to place the reference circle";return out;}
        // the genuine top edge: the side farthest from the centre
        int n=yellow.length,top=-1;double best=-1;
        for(int i=0;i<n;i++){double[] a=yellow[i],b=yellow[(i+1)%n];double d=ClearlyOff.norm(new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2});if(d>best){best=d;top=i;}}
        double[] A=yellow[top],B=yellow[(top+1)%n];
        double[] er={Math.sin(a0),-Math.cos(a0)},et={Math.cos(a0),Math.sin(a0)};
        // genuine edge distance from the centre (perpendicular), its half width along et and centre along et
        double ux=B[0]-A[0],uy=B[1]-A[1],L=Math.hypot(ux,uy);ux/=L;uy/=L;
        out.genuineR=Math.abs(A[0]*uy-A[1]*ux);
        double ta=A[0]*et[0]+A[1]*et[1],tb=B[0]*et[0]+B[1]*et[1],tc=(ta+tb)/2,hw=Math.abs(tb-ta)/2;
        out.expectedR=scale*out.genuineR+out.roundEdgeOffsetR;
        double lo=-INSIDE_R,hi=trackR-TRACK_CLEAR_R-out.expectedR;
        if(!(hi>0.002)){out.note="the minute track leaves no room above the edge";return out;}
        double ds=PROFILE_STEP_PX/rpx;int ns=(int)Math.floor((hi-lo)/ds)+1;
        double[] s=new double[ns];for(int k=0;k<ns;k++)s[k]=lo+k*ds;
        double t0=tc-(1-CORNER_FRAC)*hw,t1=tc+(1-CORNER_FRAC)*hw;int np=Math.max(2,(int)Math.floor((t1-t0)*rpx/STEP_PX)+1);
        double[] X=new double[np*ns],Y=new double[np*ns],tt=new double[np];
        for(int i=0;i<np;i++){double t=t0+(t1-t0)*i/(np-1);tt[i]=t;
            for(int k=0;k<ns;k++){double r=out.expectedR+s[k];X[i*ns+k]=t*et[0]+r*er[0];Y[i*ns+k]=t*et[1]+r*er[1];}}
        double[] v=img.sampleCanon(X,Y);
        out.profiles=np;
        List<double[]> pts=new ArrayList<>();   // {t, offset (R)}
        double[] p=new double[ns];int rimW=(int)Math.round(RIM_PX/PROFILE_STEP_PX);
        for(int i=0;i<np;i++){
            boolean nan=false;for(int k=0;k<ns;k++){p[k]=v[i*ns+k];if(Double.isNaN(p[k]))nan=true;}
            if(nan)continue;
            Double off=edge(p,s,rimW);
            if(off!=null)pts.add(new double[]{tt[i],off});
        }
        out.usable=pts.size();
        if(pts.size()<MIN_POINTS){out.note="too little of its top edge is visible ("+pts.size()+" usable profiles)";return out;}
        double tol=INLIER_PX/rpx;
        double[] line=consensus(pts,tol,null);
        boolean[] in=inliers(pts,line,tol);
        int ni=0;for(boolean b:in)if(b)ni++;
        double tmin=Double.POSITIVE_INFINITY,tmax=Double.NEGATIVE_INFINITY,ss=0;
        for(int i=0;i<pts.size();i++){double[] q=pts.get(i);
            double[] c=new double[]{q[0]*et[0]+(out.expectedR+q[1])*er[0],q[0]*et[1]+(out.expectedR+q[1])*er[1]};
            out.points.add(c);out.inlier.add(in[i]);
            if(in[i]){tmin=Math.min(tmin,q[0]);tmax=Math.max(tmax,q[0]);double e=q[1]-(line[0]+line[1]*q[0]);ss+=e*e;}}
        out.inliers=ni;out.rmsPx=Math.sqrt(ss/Math.max(1,ni-2))*rpx;out.span=(tmax-tmin)/(t1-t0);
        // a second consensus among the points the first line left out
        double[] l2=consensus(pts,tol,in);
        if(l2!=null){boolean[] in2=inliers(pts,l2,tol);int n2=0;for(int i=0;i<in2.length;i++)if(in2[i]&&!in[i])n2++;
            out.secondFrac=(double)n2/Math.max(1,ni);out.secondOffsetPx=(l2[0]+l2[1]*tc-(line[0]+line[1]*tc))*rpx;}
        if(ni<MIN_POINTS||ni<MIN_FRAC*pts.size()){out.note=String.format(Locale.US,"its top edge points do not agree on one line (%d of %d)",ni,pts.size());return out;}
        if(out.span<MIN_SPAN){out.note=String.format(Locale.US,"only %.0f %% of its top edge is visible",100*out.span);return out;}
        // the line o = a + b t in the (et, er) frame: distance from the centre and turn
        double a=line[0],b=line[1];
        out.measuredR=(out.expectedR+a)/Math.sqrt(1+b*b);
        out.angleDeg=-Math.toDegrees(Math.atan(b));
        out.radialPct=100*(out.measuredR-out.expectedR);out.radialRawPct=100*(out.measuredR-(out.expectedR-out.roundEdgeOffsetR));
        out.radialPx=Math.copySign(img.pxBetween(ClearlyOff.cpt(out.expectedR,a0),ClearlyOff.cpt(out.measuredR,a0)),out.measuredR-out.expectedR);
        out.p1=new double[]{tmin*et[0]+(out.expectedR+a+b*tmin)*er[0],tmin*et[1]+(out.expectedR+a+b*tmin)*er[1]};
        out.p2=new double[]{tmax*et[0]+(out.expectedR+a+b*tmax)*er[0],tmax*et[1]+(out.expectedR+a+b*tmax)*er[1]};
        return out;
    }

    /** the outermost rim-to-dial crossing on one profile (offset in R), or null (MetalFit.edges' rule) */
    static Double edge(double[] p,double[] s,int rimW){
        int ns=p.length;
        double top=Double.NEGATIVE_INFINITY;for(double x:p)top=Math.max(top,x);
        double dial=ClearlyOff.percentile(p,10);
        if(top-dial<MIN_CONTRAST)return null;
        int j=ns-1;while(j>=0&&p[j]<dial+RIM_FRAC*(top-dial))j--;
        if(j<0)return null;
        double rim=Double.NEGATIVE_INFINITY;for(int k=Math.max(0,j-rimW);k<=j;k++)rim=Math.max(rim,p[k]);
        double thr=dial+0.5*(rim-dial);
        j=ns-1;while(j>=0&&p[j]<thr)j--;
        if(j<0||j>=ns-2)return null;                    // no dark dial beyond it
        double x=j+(p[j]-thr)/(p[j]-p[j+1]);
        double outside=0;for(int k=j+2;k<ns;k++)outside+=p[k];outside/=(ns-j-2);
        if(outside>thr)return null;                     // bright again outside: not the edge
        return ClearlyOff.interp(x,s);
    }

    /** the line {a, b} (o = a + b t) with the most points within tol, over every pair (skipping points already taken),
     *  refined by least squares on its inliers; null with fewer than two free points */
    static double[] consensus(List<double[]> pts,double tol,boolean[] taken){
        int n=pts.size();int bestN=-1;double[] best=null;
        for(int i=0;i<n;i++){if(taken!=null&&taken[i])continue;
            for(int j=i+1;j<n;j++){if(taken!=null&&taken[j])continue;
                double[] p=pts.get(i),q=pts.get(j);if(q[0]==p[0])continue;
                double b=(q[1]-p[1])/(q[0]-p[0]),a=p[1]-b*p[0];
                int c=0;for(int k=0;k<n;k++){if(taken!=null&&taken[k])continue;double[] r=pts.get(k);if(Math.abs(r[1]-(a+b*r[0]))<=tol)c++;}
                if(c>bestN){bestN=c;best=new double[]{a,b};}
            }}
        if(best==null)return null;
        for(int it=0;it<3;it++){
            double sw=0,st=0,so=0,stt=0,sto=0;
            for(int k=0;k<n;k++){if(taken!=null&&taken[k])continue;double[] r=pts.get(k);
                if(Math.abs(r[1]-(best[0]+best[1]*r[0]))>tol)continue;sw++;st+=r[0];so+=r[1];stt+=r[0]*r[0];sto+=r[0]*r[1];}
            double den=sw*stt-st*st;if(sw<2||Math.abs(den)<1e-18)break;
            double b=(sw*sto-st*so)/den,a=(so-b*st)/sw;best=new double[]{a,b};
        }
        return best;
    }

    static boolean[] inliers(List<double[]> pts,double[] line,double tol){
        boolean[] in=new boolean[pts.size()];
        for(int k=0;k<pts.size();k++){double[] r=pts.get(k);in[k]=Math.abs(r[1]-(line[0]+line[1]*r[0]))<=tol;}
        return in;
    }

    /** this photo's ring scale from the round markers other than the 12 (measured / genuine centre radius), preferring
     *  reliably fitted ones; {scale, count, 1 if uncertain ones were used} */
    static double[] ringScale(List<double[]> reliable,List<double[]> all,int min){
        List<double[]> use=reliable.size()>=min?reliable:all;
        if(use.size()<min)return new double[]{Double.NaN,use.size(),use==all?1:0};
        double[] r=new double[use.size()];for(int i=0;i<r.length;i++)r[i]=use.get(i)[0]/use.get(i)[1];
        Arrays.sort(r);double m=r.length%2==1?r[r.length/2]:0.5*(r[r.length/2-1]+r[r.length/2]);
        return new double[]{m,use.size(),use==all&&use!=reliable?1:0};
    }
}
