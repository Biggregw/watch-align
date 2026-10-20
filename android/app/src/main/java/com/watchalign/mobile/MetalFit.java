package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.List;

/**
 * Experimental (1.6.3): a marker's position and rotation from its visible OUTER-METAL edge. Port of
 * tools/research/track/metal.py.
 *
 * The genuine outer-metal outline (the overlay's yellow polygon) is fixed in shape and size: only X/Y (round markers) or
 * X/Y and rotation (batons, triangle) are fitted, never a stretch. Along the outline's normals the outermost rim-to-dial
 * crossing is found, half-way between the dial and the rim's OWN level (a shadowed rim is darker than the lume and must
 * not be skipped for the lume boundary inside it). Points under a hand, without contrast, with no dark dial outside or
 * bright again outside (reflection, a neighbouring hand) are left out, and the rest are fitted robustly (Tukey), so a
 * hand only removes the part of the edge it covers. Each fit is graded:
 *   reliable    the visible edge is smooth, consistent, mostly seen and fixes position (and rotation) tightly;
 *   uncertain   rough, inconsistent (shadowed or reflecting metal), mostly reconstructed or loosely fixed: over a limit
 *               this is "Possible misalignment - inspect", never a silent pass;
 *   indeterminate  too few edge points, or the visible edge cannot fix the position to 1 % R / the rotation to 1 deg:
 *               the only case that is "Can't check".
 */
final class MetalFit {
    private MetalFit(){}

    static final double SPAN=0.02;            // R searched either side of the expected edge
    static final double STEP_PX=0.5,PROFILE_STEP_PX=0.25;
    static final double CORNER_R=0.012;       // outline points this close to a polygon vertex are skipped (rounded corners)
    static final double MIN_CONTRAST=30;
    static final double RIM_FRAC=0.25,RIM_PX=1.5;
    static final int MIN_POINTS=24,ITER=4;
    static final double MAX_SE_POS=0.0025,MAX_SE_ROT=0.35;      // reliable
    static final double CANT_SE_POS=0.01,CANT_SE_ROT=1.0;       // indeterminate beyond these
    static final double EDGE_RMS_OK=0.5,EDGE_BAD_PX=1.0,EDGE_BAD_FRAC=0.15,MIN_COVER=0.5;
    /** Owner, 20 Oct 2026 (genuine Yacht-Master, seconds hand along the 6 baton, 33 % of it seen): when a detected hand
     *  hides at least this share of a marker's outline and less than MIN_COVER of it is seen, the rest would be
     *  reconstructed from the hand's side; the marker is Can't check rather than judged either way. Only when the
     *  independent per-marker hand check (Alpha99MarkerInterference) also finds a hand there: the radial hand finder
     *  alone can follow a short seconds tail out through dial print and glare (owner's Submariner Date, 20 Oct 2026:
     *  a 'hand' to the dial edge at 5 o'clock, where no hand is). */
    static final double HAND_HIDDEN_CANT=0.5;
    static final String HAND_COVERS="a hand covers most of its outline";
    /** hands: the hand's own width (measured near the pivot) carried out to the marker, plus this margin (R) */
    static final double HAND_MARGIN_R=0.012,HAND_WIDTH_AT_R=0.34;
    /** the last HAND_TIP_LEN (R) before a hand's reach is treated as at least HAND_TIP_HALF_R wide (GMT arrow, lume plot) */
    static final double HAND_TIP_LEN=0.12,HAND_TIP_HALF_R=0.045;
    /** Alpha99's interference strip is sampled every 0.75 px; allow that sampling width around its thin shaft. */
    static final double SECONDS_SAMPLE_MARGIN_PX=0.75;
    /** 1.6.6 (owner, 20 Oct 2026; genuine Submariner Date, the seconds hand running beside the 6 baton's right edge while
     *  the detected seconds line sat ~1.5 deg inside it, so its mask missed that edge): a baton whose fit is uncertain
     *  while the independent per-marker check found a hand at it is fitted again WITHOUT the one side nearest the
     *  detected hand. The refit (Fit.alt) is kept only when what remains is clean by the same standards as any reliable
     *  fit. It is used only for the strict genuine standard, with the one-side uncertainty measured on the verified genuine
     *  photos (ALT_U_POS_R, ALT_U_ROT_DEG; tools/desktop-harness/drivers/SideDrop.java); the marker's own reading (replica
     *  QC, close-up) is unchanged. A side counts as beside the hand when the hand's centre line runs along it (within
     *  ALT_HAND_PARALLEL_DEG) and passes within ALT_HAND_NEAR_R of its midpoint, and exactly one side may be. */
    static final double ALT_HAND_NEAR_R=0.02,ALT_HAND_PARALLEL_DEG=15;
    static double ALT_U_POS_R=Double.NaN,ALT_U_ROT_DEG=Double.NaN;
    /** ...and the refit must agree with the independently measured luminous centre within this (R): a misleading edge
     *  evenly along the remaining side would move the refit, not the lume */
    static double ALT_LUME_AGREE_R=Double.NaN;
    /** research only (null in the app): for every reliably fitted baton, each side left out in turn:
     *  {side, side length (R), refit clean 1/0, dx, dy (R), dtilt (deg), x, y} against the full fit */
    static List<double[]> SIDE_DROP_PROBE=null;
    /** edge point status (drawn in the close-up) */
    static final byte SEEN=0,INCONSISTENT=1,HIDDEN=2;
    static final int USED=0,HAND=1,LOW=2,NO_DARK=3,BRIGHT_OUT=4,OUTSIDE=5;

    static final class Outline {double[][] pts,nrm;int[] side;}

    /** points along the closed polygon every STEP_PX with outward normals (polygon corners skipped) */
    static Outline outline(double[][] poly,double rpx,boolean round){
        double[] c=ClearlyOff.mean(poly);double step=STEP_PX/rpx;
        List<double[]> P=new ArrayList<>(),N=new ArrayList<>();List<Integer> S=new ArrayList<>();
        int n=poly.length;
        for(int i=0;i<n;i++){
            double[] a=poly[i],b=poly[(i+1)%n];double L=Math.hypot(b[0]-a[0],b[1]-a[1]);if(L<=0)continue;
            double tx=(b[0]-a[0])/L,ty=(b[1]-a[1])/L;double nx=ty,ny=-tx;
            if(nx*((a[0]+b[0])/2-c[0])+ny*((a[1]+b[1])/2-c[1])<0){nx=-nx;ny=-ny;}
            int k=Math.max(1,(int)(L/step));
            for(int j=0;j<k;j++){double u=(j+0.5)/k*L;
                if(!round&&(u<CORNER_R||L-u<CORNER_R))continue;
                P.add(new double[]{a[0]+tx*u,a[1]+ty*u});N.add(new double[]{nx,ny});S.add(round?0:i);}
        }
        Outline o=new Outline();o.pts=P.toArray(new double[0][]);o.nrm=N.toArray(new double[0][]);
        o.side=new int[S.size()];for(int i=0;i<o.side.length;i++)o.side[i]=S.get(i);return o;
    }

    /** a hand (angle deg, angular width deg near the pivot, reach R) covers canonical point p */
    static boolean underHand(double[] p,List<double[]> hands){
        if(hands==null)return false;
        double r=ClearlyOff.norm(p),ang=Math.toDegrees(Math.atan2(p[0],-p[1]));
        for(double[] h:hands){
            if(r>h[2]+0.02)continue;
            double da=Math.toRadians(Math.abs(ClearlyOff.pmod(ang-h[0]+180,360)-180));
            if(da>Math.PI/2)continue;
            double half=HAND_WIDTH_AT_R*Math.sin(Math.toRadians(h[1]/2));   // the hand's half width (R), not its angle
            if(r>h[2]-HAND_TIP_LEN)half=Math.max(half,HAND_TIP_HALF_R);     // the tip: a GMT arrow or a lume plot is wider
            if(r*Math.sin(da)<=half+HAND_MARGIN_R)return true;
        }
        return false;
    }

    static final class Edges {Outline o;double[] off;int[] why;}

    /** Test the OBSERVED crossing, not the reference point from which its profile was searched.
     * A nearby shaft can lie within SPAN while the marker's own boundary remains clear. A broad mask on the
     * reference would throw away that clean boundary (and can erase the short ends needed to locate a baton).
     * The direction must come from Alpha99's independent seconds-dot/shaft evidence; radial marker edges alone
     * never create a hand. Do not apply the hour/minute tip widening or HAND_MARGIN_R to this thin shaft. */
    static boolean onSecondsHand(double x,double y,double degrees,double rpx){
        if(!Double.isFinite(degrees))return false;
        double a=Math.toRadians(degrees),along=x*Math.sin(a)-y*Math.cos(a),cross=x*Math.cos(a)+y*Math.sin(a);
        return along>0&&Math.abs(cross)<=Alpha99MarkerInterference.SHAFT_HALF+SECONDS_SAMPLE_MARGIN_PX/rpx;
    }

    /** edge offsets (R, + = outward) of the outline along its normals; NaN where left out (why[i] says why) */
    static Edges edges(ClearlyOff.Img img,double[][] poly,double rpx,boolean round,List<double[]> hands){
        return edges(img,poly,rpx,round,hands,Double.NaN);
    }
    static Edges edges(ClearlyOff.Img img,double[][] poly,double rpx,boolean round,List<double[]> hands,double secondsLine){
        return edges(img,poly,rpx,round,hands,secondsLine,-1);
    }
    /** skipSide: a polygon side (index of its first vertex) left out as if under a hand (-1: none) */
    static Edges edges(ClearlyOff.Img img,double[][] poly,double rpx,boolean round,List<double[]> hands,double secondsLine,int skipSide){
        Outline o=outline(poly,rpx,round);int n=o.pts.length;
        double ds=PROFILE_STEP_PX/rpx;int ns=(int)Math.floor(2*SPAN/ds+1e-9)+1;
        double[] s=new double[ns];for(int k=0;k<ns;k++)s[k]=-SPAN+k*ds;
        double[] X=new double[n*ns],Y=new double[n*ns];
        for(int i=0;i<n;i++)for(int k=0;k<ns;k++){X[i*ns+k]=o.pts[i][0]+s[k]*o.nrm[i][0];Y[i*ns+k]=o.pts[i][1]+s[k]*o.nrm[i][1];}
        double[] v=img.sampleCanon(X,Y);
        Edges e=new Edges();e.o=o;e.off=new double[n];e.why=new int[n];
        int nIn=0;while(nIn<ns&&s[nIn]<=0.004)nIn++;
        int rimW=(int)Math.round(RIM_PX/PROFILE_STEP_PX);
        double[] p=new double[ns];
        for(int i=0;i<n;i++){
            e.off[i]=Double.NaN;
            if(underHand(o.pts[i],hands)||(skipSide>=0&&o.side[i]==skipSide)){e.why[i]=HAND;continue;}
            boolean nan=false;for(int k=0;k<ns;k++){p[k]=v[i*ns+k];if(Double.isNaN(p[k]))nan=true;}
            if(nan){e.why[i]=OUTSIDE;continue;}
            double top=Double.NEGATIVE_INFINITY;for(int k=0;k<nIn;k++)top=Math.max(top,p[k]);
            double dial=ClearlyOff.percentile(p,10);
            if(top-dial<MIN_CONTRAST){e.why[i]=LOW;continue;}
            int j=ns-1;while(j>=0&&p[j]<dial+RIM_FRAC*(top-dial))j--;
            double thr=Double.NaN;
            if(j>=0){
                double rim=Double.NEGATIVE_INFINITY;for(int k=Math.max(0,j-rimW);k<=j;k++)rim=Math.max(rim,p[k]);
                thr=dial+0.5*(rim-dial);j=ns-1;while(j>=0&&p[j]<thr)j--;
            }
            if(j<0||j>=ns-2){e.why[i]=NO_DARK;continue;}
            double x=j+(p[j]-thr)/(p[j]-p[j+1]);
            double out=0;for(int k=j+2;k<ns;k++)out+=p[k];out/=(ns-j-2);
            if(out>thr){e.why[i]=BRIGHT_OUT;continue;}
            double off=ClearlyOff.interp(x,s);
            if(onSecondsHand(o.pts[i][0]+off*o.nrm[i][0],o.pts[i][1]+off*o.nrm[i][1],secondsLine,rpx)){
                e.why[i]=HAND;continue;}
            e.off[i]=off;e.why[i]=USED;
        }
        return e;
    }

    static final class Fit {
        double[] cGen,cObs;double tiltDeg=Double.NaN,sePos=Double.NaN,seRot=Double.NaN,cover=Double.NaN,rmsPx=Double.NaN,
                bias=Double.NaN,badFrac=Double.NaN;
        /** share of the outline's sample points hidden by a detected hand (hour, minute or seconds) */
        double handFrac=Double.NaN;
        int n;
        /** non-empty: no meaningful fit (Can't check) */
        String note="";
        /** non-empty: the fit stands but its edge is uncertain (why, plain words) */
        String uncertain="";
        /** the fitted outline's sample points (canonical) and what was seen at each (SEEN / INCONSISTENT / HIDDEN) */
        double[][] pts;byte[] status;
        double[][] poly;
        /** 1.6.6: the clean refit without the side beside a confirmed hand (genuine standard only), and that side */
        Fit alt;int altSide=-1;
        boolean ok(){return note.isEmpty()&&cObs!=null;}
    }

    static double[][] pose(double[][] poly0,double[] c0,double th,double tx,double ty){
        double c=Math.cos(th),s=Math.sin(th);double[][] out=new double[poly0.length][];
        for(int i=0;i<poly0.length;i++){double x=poly0[i][0]-c0[0],y=poly0[i][1]-c0[1];
            out[i]=new double[]{c0[0]+c*x-s*y+tx,c0[1]+s*x+c*y+ty};}
        return out;
    }

    /** one marker: fixed genuine outline, fitted X/Y (+ rotation unless round). bias: this photo's edge offset for the
     *  shape (R, from its fully seen markers), removed so a partly hidden marker is not pulled to its visible side. */
    static Fit fit(ClearlyOff.Img img,double[][] yellow,ModelSpec.Shape shape,double rpx,List<double[]> hands,double bias){
        return fit(img,yellow,shape,rpx,hands,bias,Double.NaN);
    }
    static Fit fit(ClearlyOff.Img img,double[][] yellow,ModelSpec.Shape shape,double rpx,List<double[]> hands,double bias,double secondsLine){
        return fit(img,yellow,shape,rpx,hands,bias,secondsLine,true);
    }
    /** handConfirmed: the independent per-marker check also found a hand at this marker (HAND_COVERS needs it) */
    static Fit fit(ClearlyOff.Img img,double[][] yellow,ModelSpec.Shape shape,double rpx,List<double[]> hands,double bias,double secondsLine,boolean handConfirmed){
        Fit f=fitCore(img,yellow,shape,rpx,hands,bias,secondsLine,handConfirmed,-1);
        if(SIDE_DROP_PROBE!=null&&shape==ModelSpec.Shape.BATON&&f.ok()&&f.uncertain.isEmpty())
            for(int s=0;s<yellow.length;s++){Fit a=fitCore(img,yellow,shape,rpx,hands,bias,secondsLine,handConfirmed,s);
                double[] a0=yellow[s],a1=yellow[(s+1)%yellow.length];
                SIDE_DROP_PROBE.add(new double[]{s,Math.hypot(a1[0]-a0[0],a1[1]-a0[1]),a.ok()&&a.uncertain.isEmpty()?1:0,
                        a.cObs==null?Double.NaN:a.cObs[0]-f.cObs[0],a.cObs==null?Double.NaN:a.cObs[1]-f.cObs[1],a.tiltDeg-f.tiltDeg,f.cObs[0],f.cObs[1]});}
        if(shape==ModelSpec.Shape.BATON&&handConfirmed&&f.ok()&&!f.uncertain.isEmpty()){
            int side=sideBesideHand(f.poly,hands,secondsLine);
            if(side>=0){Fit a=fitCore(img,yellow,shape,rpx,hands,bias,secondsLine,handConfirmed,side);
                if(a.ok()&&a.uncertain.isEmpty()){f.alt=a;f.altSide=side;}}
        }
        return f;
    }

    /** the one polygon side a detected hand's centre line runs along, within ALT_HAND_NEAR_R of its midpoint (hands that
     *  reach it, and the seconds line), or -1 when none or more than one is */
    static int sideBesideHand(double[][] poly,List<double[]> hands,double secondsLine){
        List<Double> lines=new ArrayList<>();
        if(Double.isFinite(secondsLine))lines.add(secondsLine);
        int found=-1,n=poly.length;
        for(int i=0;i<n;i++){
            double[] a=poly[i],b=poly[(i+1)%n];double mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2,r=Math.hypot(mx,my);
            double len=Math.hypot(b[0]-a[0],b[1]-a[1]);if(len<=0)continue;double ux=(b[0]-a[0])/len,uy=(b[1]-a[1])/len;
            List<Double> near=new ArrayList<>(lines);
            if(hands!=null)for(double[] h:hands)if(h[2]>=r-0.02)near.add(h[0]);
            boolean beside=false;
            for(double deg:near){double t=Math.toRadians(deg),dx=Math.sin(t),dy=-Math.cos(t);
                if(mx*dx+my*dy>0&&Math.abs(mx*dy-my*dx)<=ALT_HAND_NEAR_R&&Math.abs(ux*dy-uy*dx)<=Math.sin(Math.toRadians(ALT_HAND_PARALLEL_DEG)))beside=true;}
            if(beside){if(found>=0)return -1;found=i;}
        }
        return found;
    }

    /** the fit itself; skipSide: a polygon side left out (-1: none) */
    static Fit fitCore(ClearlyOff.Img img,double[][] yellow,ModelSpec.Shape shape,double rpx,List<double[]> hands,double bias,double secondsLine,boolean handConfirmed,int skipSide){
        Fit f=new Fit();boolean round=shape==ModelSpec.Shape.ROUND,rotate=!round;int np=rotate?3:2;
        double[] c0=ClearlyOff.mean(yellow);f.cGen=c0;
        double tx=0,ty=0,th=0;
        double[] w=null,res=null,x=null;double[][] J=null;Edges e=null;int[] idx=null;
        for(int it=0;it<ITER;it++){
            double[][] poly=pose(yellow,c0,th,tx,ty);
            e=edges(img,poly,rpx,round,hands,secondsLine,skipSide);
            int m=0;for(double d:e.off)if(!Double.isNaN(d))m++;
            if(m<MIN_POINTS){f.note="too little of its outer edge is visible";f.n=m;f.cover=(double)m/Math.max(1,e.off.length);return f;}
            double[] cc=ClearlyOff.mean(poly);
            J=new double[m][np];double[] r=new double[m];idx=new int[m];int q=0;
            for(int i=0;i<e.off.length;i++){if(Double.isNaN(e.off[i]))continue;
                double[] nr=e.o.nrm[i],pt=e.o.pts[i];J[q][0]=nr[0];J[q][1]=nr[1];
                if(rotate)J[q][2]=nr[0]*-(pt[1]-cc[1])+nr[1]*(pt[0]-cc[0]);
                r[q]=e.off[i]-bias;idx[q]=i;q++;}
            w=new double[m];java.util.Arrays.fill(w,1);
            for(int k=0;k<5;k++){
                x=wls(J,r,w,np);
                res=new double[m];double[] ab=new double[m];
                for(int i=0;i<m;i++){double pr=0;for(int c=0;c<np;c++)pr+=J[i][c]*x[c];res[i]=r[i]-pr;ab[i]=Math.abs(res[i]);}
                double sc=Math.max(1.4826*ClearlyOff.median(ab),0.3/rpx);
                for(int i=0;i<m;i++){double u=res[i]/(4.685*sc);w[i]=Math.abs(u)<1?(1-u*u)*(1-u*u):0;}
            }
            tx+=x[0];ty+=x[1];if(rotate)th+=x[2];
        }
        int m=res.length,keep=0;double sw=0,swr=0,swr2=0;
        for(int i=0;i<m;i++){if(w[i]>0)keep++;sw+=w[i];swr+=w[i]*res[i];swr2+=w[i]*res[i]*res[i];}
        double sig=Math.sqrt(swr2/Math.max(1,keep-np));
        double[][] A=normal(J,w,np);double[][] inv=invert(A,np);
        if(inv==null){f.note="its visible edge does not fix its position";return f;}
        f.sePos=sig*Math.sqrt(Math.max(Math.max(inv[0][0],inv[1][1]),0));
        f.seRot=rotate?Math.toDegrees(sig*Math.sqrt(Math.max(inv[2][2],0))):Double.NaN;
        f.cObs=new double[]{c0[0]+tx,c0[1]+ty};f.tiltDeg=rotate?Math.toDegrees(th):Double.NaN;
        f.n=keep;f.cover=(double)keep/e.off.length;f.rmsPx=sig*rpx;f.bias=swr/Math.max(1e-12,sw)+bias;
        // what was seen of the outline: consistent edge, inconsistent edge (shadowed bevel, reflection) or hidden
        f.poly=pose(yellow,c0,th,tx,ty);f.pts=e.o.pts;f.status=new byte[e.off.length];java.util.Arrays.fill(f.status,HIDDEN);
        int seen=0,bad=0;
        for(int q=0;q<m;q++){int i=idx[q];seen++;
            if(Math.abs(res[q])*rpx>EDGE_BAD_PX||w[q]==0){f.status[i]=INCONSISTENT;bad++;}else f.status[i]=SEEN;}
        f.badFrac=(double)bad/Math.max(1,seen);
        int underHand=0;for(int v:e.why)if(v==HAND)underHand++;
        f.handFrac=(double)underHand/Math.max(1,e.why.length);
        List<String> unc=new ArrayList<>();
        if(f.rmsPx>EDGE_RMS_OK)unc.add(String.format(java.util.Locale.US,"its visible edge is rough (%.1f px)",f.rmsPx));
        if(f.badFrac>EDGE_BAD_FRAC)unc.add(String.format(java.util.Locale.US,"%.0f%% of its visible edge is inconsistent (shadowed or reflecting metal)",100*f.badFrac));
        if(f.cover<MIN_COVER)unc.add(String.format(java.util.Locale.US,"only %.0f%% of its outline is visible, the rest is reconstructed",100*f.cover));
        if(f.sePos>MAX_SE_POS)unc.add("its visible edge only loosely fixes its position");
        if(rotate&&f.seRot>MAX_SE_ROT)unc.add("its visible edge only loosely fixes its rotation");
        f.uncertain=String.join("; ",unc);
        if(handConfirmed&&f.handFrac>=HAND_HIDDEN_CANT&&f.cover<MIN_COVER)f.note=HAND_COVERS;
        else if(f.sePos>CANT_SE_POS)f.note="its visible edge does not fix its position";
        else if(rotate&&f.seRot>CANT_SE_ROT)f.note="its visible edge does not fix its rotation";
        return f;
    }

    /** the edge offsets (R) the last fitAll used, per shape (diagnostics) */
    static final java.util.Map<ModelSpec.Shape,Double> lastBias=new java.util.concurrent.ConcurrentHashMap<>();

    /** every marker of a photo: a first pass for each shape's edge offset (median over fully seen markers), then the fit */
    static Fit[] fitAll(ClearlyOff.Img img,List<ClearlyOff.Input> in,double rpx,List<double[]> hands){
        Fit[] first=new Fit[in.size()];
        for(int i=0;i<in.size();i++){ClearlyOff.Input m=in.get(i);
            first[i]=m.yellow==null||m.yellow.length<3?null:fit(img,m.yellow,m.shape,rpx,hands,0,m.secondsLineDeg,handConfirmed(m));}
        java.util.Map<ModelSpec.Shape,Double> bias=new java.util.HashMap<>();
        for(ModelSpec.Shape s:ModelSpec.Shape.values()){List<Double> b=new ArrayList<>();
            for(int i=0;i<in.size();i++){Fit f=first[i];if(f!=null&&in.get(i).shape==s&&f.ok()&&f.cover>0.9)b.add(f.bias);}
            bias.put(s,b.isEmpty()?0.0:ClearlyOff.median(ClearlyOff.toArray(b)));}
        lastBias.clear();lastBias.putAll(bias);
        if(SIDE_DROP_PROBE!=null)SIDE_DROP_PROBE.clear();   // research: the final pass only
        Fit[] out=new Fit[in.size()];
        for(int i=0;i<in.size();i++){ClearlyOff.Input m=in.get(i);
            out[i]=first[i]==null?null:fit(img,m.yellow,m.shape,rpx,hands,bias.get(m.shape),m.secondsLineDeg,handConfirmed(m));}
        return out;
    }
    static boolean handConfirmed(ClearlyOff.Input m){return Boolean.FALSE.equals(m.handClean);}

    // weighted least squares (normal equations; 2 or 3 unknowns)
    static double[][] normal(double[][] J,double[] w,int np){
        double[][] A=new double[np][np];
        for(int i=0;i<J.length;i++)for(int a=0;a<np;a++)for(int b=0;b<np;b++)A[a][b]+=w[i]*J[i][a]*J[i][b];
        return A;
    }
    static double[] wls(double[][] J,double[] r,double[] w,int np){
        double[][] A=normal(J,w,np);double[] g=new double[np];
        for(int i=0;i<J.length;i++)for(int a=0;a<np;a++)g[a]+=w[i]*J[i][a]*r[i];
        double[][] inv=invert(A,np);double[] x=new double[np];
        if(inv==null)return x;
        for(int a=0;a<np;a++)for(int b=0;b<np;b++)x[a]+=inv[a][b]*g[b];
        return x;
    }
    static double[][] invert(double[][] A,int n){
        double[][] M=new double[n][2*n];
        for(int i=0;i<n;i++){System.arraycopy(A[i],0,M[i],0,n);M[i][n+i]=1;}
        for(int c=0;c<n;c++){
            int p=c;for(int r=c+1;r<n;r++)if(Math.abs(M[r][c])>Math.abs(M[p][c]))p=r;
            if(Math.abs(M[p][c])<1e-300)return null;
            double[] t=M[c];M[c]=M[p];M[p]=t;
            double d=M[c][c];for(int k=0;k<2*n;k++)M[c][k]/=d;
            for(int r=0;r<n;r++)if(r!=c){double fct=M[r][c];if(fct!=0)for(int k=0;k<2*n;k++)M[r][k]-=fct*M[c][k];}
        }
        double[][] out=new double[n][n];for(int i=0;i<n;i++)System.arraycopy(M[i],n,out[i],0,n);
        return out;
    }
}
