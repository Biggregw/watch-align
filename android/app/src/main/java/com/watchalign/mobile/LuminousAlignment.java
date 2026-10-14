package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Watch Align alignment-only measurement: one common rigid fit of a marker's KNOWN luminous shape (triangle, baton or
 * round) to its luminous boundary, for every supported marker. Port of the robust luminous-edge pilot (14 Oct 2026):
 * from confirmed luminous interior, each ray marches OUTWARD to the FIRST supported lume-to-dark fall (never the
 * outermost bright feature, which can be a lit bevel), and all boundary samples are fitted jointly and robustly
 * (Huber-weighted least squares) by translation and rotation of the fixed shape; a circle by translation only.
 *
 * Reference: the model's master outline, placed by the marker ring fitted on the OTHER markers only (the marker being
 * judged never moves its own reference), inset by the model's genuine-measured luminous inset (outer metal -> lume).
 * The photo is assumed straight-on (owner decision); the existing frozen dial pose H only supplies centre, scale and
 * orientation.
 *
 * Fitted unknowns (canonical dial units, radius 1): radial and tangential shift of the shape's centroid, rotation, and a
 * uniform boundary offset g (photographic edge definition: threshold, lighting, blur). g is a nuisance, never applied to
 * the displayed shape; it is fitted only where the observed sides determine it (a triangle's or baton's centre is then
 * independent of it) and is otherwise fixed (the shape's known size: an INFERRED component, whose uncertainty includes
 * the genuine spread of g). A component the observed edges do not constrain is withheld, never guessed. An estimated g
 * outside the genuine range means the edge found is not the luminous edge (bevel merge, glare): the marker is withheld.
 * No thresholds here decide defects; see AlignmentDecision.
 */
final class LuminousAlignment {
    /** Grey level at image pixel coordinates; NaN outside the image. */
    interface Image{double at(double px,double py);}

    enum Param{RADIAL,TANGENTIAL,ROTATION,GROWTH}

    /** Measurement-quality constants (not defect limits). */
    static final double STEP_PX=0.25;            // ray sampling step, native px
    static final double SPAN_LO=0.15,SPAN_HI=0.85;// middle of each side only: corners and tips are never sampled
    static final double REACH_R=0.035;           // outward search beyond the expected luminous edge (dial radius units)
    static final double MIN_SIDE_COVER=0.40;     // a side is observed when at least this share of its rays found the edge
    static final int MIN_SIDE_SAMPLES=6;
    static final double MIN_CONTRAST=25;         // grey levels between luminous fill and dial
    static final double MAX_DILUTION=5.0;        // geometric dilution of precision: above it a parameter is not determined
    static final double MAX_EDGE_NOISE_PX=0.8;   // robust residual spread above which the edge is too irregular to trust (genuine readings that set the first envelopes had 0.8-1.5 px)
    static final double MIN_ROUND_COVER=0.50;    // share of 32 directions with an edge for a round marker
    static final double FREE_SIZE_ROUND_COVER=0.70;
    static final int MIN_RING_OTHERS=4;          // Alpha94 fits its ring on >= 5 markers including the judged one; here the judged one is left out
    static final double MAX_MERGED_SHARE=0.20;   // more of the outline merged with a lit bevel than this: withheld (genuine outliers had 0.2-0.33)

    static final class Side {
        /** template side endpoints (canonical) and outward unit normal */
        double[] a,b,n;
        int rays,found;
        boolean observed;
        /** observed boundary points (canonical) */
        final List<double[]> points=new ArrayList<>();
        /** mean / max-run residual of this side's samples after the joint fit, px (coherent local anomaly indicator) */
        double meanResidualPx=Double.NaN,anomalyPx=Double.NaN;
        double cover(){return rays==0?0:found/(double)rays;}
    }

    static final class Result {
        final int hour;final ModelSpec.Shape shape;
        boolean assessed;String reason="";
        double rpx=Double.NaN;
        /** reference centroid (canonical), reference outline (luminous template at the reference pose) */
        double[] refCentre;double[][] refPolygon;double refRadius=Double.NaN;
        /** fitted parameters: radial / tangential shift (R), rotation (deg, + clockwise), uniform edge offset g (R) */
        double radialR=Double.NaN,tangentialR=Double.NaN,rotationDeg=Double.NaN,growthR=Double.NaN;
        double seRadialR=Double.NaN,seTangentialR=Double.NaN,seRotationDeg=Double.NaN,seGrowthR=Double.NaN;
        boolean radialOk,tangentialOk,rotationOk,growthFree;
        /** some sides were not observed: the pose uses the known shape (inferred, labelled) */
        boolean inferred;
        double lume=Double.NaN,dial=Double.NaN,threshold=Double.NaN,noisePx=Double.NaN;
        int samples;
        /** share of edge samples set aside as lying well OUTSIDE the fitted shape (a lit bevel merged with the lume) */
        double mergedShare=0;
        /** this marker's g minus the median g of the photo's other markers (NaN when not compared) */
        double photoGrowthDiffR=Double.NaN;
        final List<Side> sides=new ArrayList<>();
        /** round markers: share of 32 directions with an edge and the largest edgeless arc (deg) */
        double roundCover=Double.NaN,roundGapDeg=Double.NaN;
        /** fitted luminous outline at the fitted pose, known size (display only); circle: {cx,cy,r} */
        double[][] fittedPolygon;double[] fittedCircle;
        Result(int hour,ModelSpec.Shape shape){this.hour=hour;this.shape=shape;}
        double[] er(){return radial(hour);} double[] et(){return tangential(hour);}
        /** half the marker's longest extent (R): the lever arm that turns a rotation into a visible displacement */
        double halfLengthR(){
            if(refPolygon==null)return Double.NaN;double m=0;
            for(double[] p:refPolygon)m=Math.max(m,Math.hypot(p[0]-refCentre[0],p[1]-refCentre[1]));return m;
        }
    }

    /** What a marker is fitted against: its outer-metal master moved by the ring of the OTHER markers, inset by the
     *  model's genuine luminous inset, plus the genuine spread of g for inferred components. */
    static final class Setup {
        double insetR;double growthSdR;double growthLimitR=Double.NaN;double growthNominalR=0;
        /** genuine spread of (marker g - the photo's other markers' g); NaN: not checked */
        double photoGrowthLimitR=Double.NaN;
        /** fixed g for a round fitted with known size (median of the photo's other freely sized rounds), else 0 */
        double fixedGrowthR=0;
        /** set aside samples well outside the known-size fit (merged lit bevel); off only while the inset itself is
         *  being measured (calibration pass 1) */
        boolean rejectOutward=true;
        Setup(double insetR,double growthSdR){this.insetR=insetR;this.growthSdR=growthSdR;}
    }

    private LuminousAlignment(){}

    // ------------------------------------------------------------------ reference
    /** Ring similarity (tx, ty, scale-1, rot) from every usable ring marker EXCEPT the given hour (Huber 1 px, as
     *  Alpha94's ring fit). null with fewer than MIN_RING_OTHERS markers. */
    static double[] ringWithout(Alpha94MarkerMeasurement.Report r,int hour){
        if(r==null||!(r.dialRadiusPx>0))return null;
        List<double[]> rows=new ArrayList<>();
        for(Alpha94MarkerMeasurement.Marker m:r.markers){
            if(m.hour==hour||!m.usable||m.spec==null||!Double.isFinite(m.canonDx)||!Double.isFinite(m.canonDy))continue;
            double[] p=m.spec.masterPoint();rows.add(new double[]{p[0],p[1],m.canonDx,m.canonDy});
        }
        return fitRing(rows,r.dialRadiusPx);
    }

    /** rows: {masterX, masterY, dx, dy}. */
    static double[] fitRing(List<double[]> rows,double rpx){
        int n=rows.size();if(n<MIN_RING_OTHERS)return null;
        double[] w=new double[2*n];Arrays.fill(w,1);double[] x=null;
        for(int it=0;it<10;it++){
            double[][] N=new double[4][4];double[] rhs=new double[4];
            for(int k=0;k<n;k++){double[] q=rows.get(k);
                double[][] A={{1,0,q[0],-q[1]},{0,1,q[1],q[0]}};double[] b={q[2],q[3]};
                for(int j=0;j<2;j++)for(int a=0;a<4;a++){rhs[a]+=A[j][a]*b[j]*w[2*k+j];for(int c=0;c<4;c++)N[a][c]+=A[j][a]*A[j][c]*w[2*k+j];}}
            x=solve(N,rhs);if(x==null)return null;
            for(int k=0;k<n;k++){double[] q=rows.get(k);
                double rx=(q[2]-(x[0]+x[2]*q[0]-x[3]*q[1]))*rpx,ry=(q[3]-(x[1]+x[2]*q[1]+x[3]*q[0]))*rpx;
                w[2*k]=Math.abs(rx)<=1?1:1/Math.abs(rx);w[2*k+1]=Math.abs(ry)<=1?1:1/Math.abs(ry);}
        }
        return x;
    }

    static double[] move(double[] p,double[] ring){
        if(ring==null)return p.clone();
        return new double[]{p[0]+ring[0]+ring[2]*p[0]-ring[3]*p[1],p[1]+ring[1]+ring[2]*p[1]+ring[3]*p[0]};
    }

    /** Outer-metal master outline of a polygon marker at the reference pose (canonical). */
    static double[][] masterPolygon(ModelSpec.Marker spec,double[] ring){
        double[][] v=spec.shape==ModelSpec.Shape.TRIANGLE?spec.trianglePolygon():spec.batonPolygon();
        double[][] o=new double[v.length][];for(int i=0;i<v.length;i++)o[i]=move(v[i],ring);return o;
    }

    /** Polygon with every side moved inward by d (d < 0 grows it). */
    static double[][] inset(double[][] v,double d){
        int n=v.length;double[] c=centroid(v);double[][] nrm=new double[n][];double[] off=new double[n];
        for(int i=0;i<n;i++){double[] a=v[i],b=v[(i+1)%n];double[] nn=outward(a,b,c);nrm[i]=nn;off[i]=nn[0]*a[0]+nn[1]*a[1]-d;}
        double[][] o=new double[n][];
        for(int i=0;i<n;i++){int p=(i+n-1)%n;double[] x=intersect(nrm[p],off[p],nrm[i],off[i]);o[i]=x==null?v[i].clone():x;}
        return o;
    }

    // ------------------------------------------------------------------ measurement
    /**
     * Fits one marker. ring: the similarity from the OTHER markers (ringWithout); H: canonical -> image pixels.
     */
    static Result measure(Image img,double[] H,ModelSpec.Marker spec,double[] ring,Setup setup){
        Result r=new Result(spec.hour,spec.shape);
        r.rpx=pxPerR(H);
        if(img==null||H==null||!(r.rpx>20)){r.reason="the dial could not be located";return r;}
        if(ring==null){r.reason="too few other markers to place the genuine reference";return r;}
        double step=STEP_PX/r.rpx;
        boolean round=spec.shape==ModelSpec.Shape.ROUND;
        double inradius;
        if(round){
            r.refCentre=move(spec.masterPoint(),ring);
            r.refRadius=spec.outerR*(1+ring[2])-setup.insetR;
            inradius=r.refRadius;
        }else{
            r.refPolygon=inset(masterPolygon(spec,ring),setup.insetR);
            r.refCentre=centroid(r.refPolygon);
            inradius=2*area(r.refPolygon)/perimeter(r.refPolygon);
        }
        if(!(inradius>0)){r.reason="marker geometry unavailable";return r;}
        // 1. luminous and dial levels: interior of the expected shape, and a band of dial just outside its metal
        double[] lv=round?roundInterior(img,H,r.refCentre,r.refRadius*0.6,r.rpx):polygonInterior(img,H,r.refPolygon,0.6,r.rpx);
        double bevel=Math.max(setup.insetR,0.016);
        double[] dv=round?ring(img,H,r.refCentre,r.refRadius+bevel+0.008,r.refRadius+bevel+0.018,r.rpx)
                :band(img,H,r.refPolygon,bevel+0.008,bevel+0.018,r.rpx);
        if(lv.length<10||dv.length<10){r.reason="the marker is too close to the photo's edge";return r;}
        r.lume=percentile(lv,50);r.dial=percentile(dv,25);
        if(r.lume-r.dial<MIN_CONTRAST){r.reason="its luminous fill is not distinguishable from the dial";return r;}
        r.threshold=r.dial+0.5*(r.lume-r.dial);
        // 2. interior-outward first-fall samples
        List<double[]> rows=new ArrayList<>();   // {n_r, n_t, lever, 1, delta, sideIndex}
        double[] er=radial(spec.hour),et=tangential(spec.hour);
        if(round){
            int N=Math.max(32,Math.min(96,(int)Math.round(2*Math.PI*r.refRadius*r.rpx/2)));
            boolean[] bins=new boolean[32];
            Side s=new Side();r.sides.add(s);
            for(int k=0;k<N;k++){
                double a=2*Math.PI*k/N;double[] n={Math.cos(a),Math.sin(a)};
                double[] q={r.refCentre[0]+r.refRadius*n[0],r.refCentre[1]+r.refRadius*n[1]};
                s.rays++;
                double d=firstFall(img,H,q,n,0.5*r.refRadius,REACH_R,step,r.threshold,r.lume-r.dial,true);
                if(!Double.isFinite(d))continue;
                s.found++;bins[(int)Math.floor(k*32.0/N)%32]=true;
                s.points.add(new double[]{q[0]+d*n[0],q[1]+d*n[1]});
                rows.add(new double[]{n[0]*er[0]+n[1]*er[1],n[0]*et[0]+n[1]*et[1],0,1,d,0});
            }
            int c=0,gap=0,run=0;for(int k=0;k<64;k++){boolean b=bins[k%32];if(k<32&&b)c++;run=b?0:run+1;gap=Math.max(gap,Math.min(run,32));}
            r.roundCover=c/32.0;r.roundGapDeg=gap*360.0/32;
            s.observed=r.roundCover>=MIN_ROUND_COVER;
            if(!s.observed){r.reason=String.format(java.util.Locale.US,"only %.0f%% of its luminous outline is visible",100*r.roundCover);r.samples=rows.size();return r;}
        }else{
            int n=r.refPolygon.length;
            for(int i=0;i<n;i++){
                Side s=new Side();s.a=r.refPolygon[i];s.b=r.refPolygon[(i+1)%n];s.n=outward(s.a,s.b,r.refCentre);r.sides.add(s);
                double len=Math.hypot(s.b[0]-s.a[0],s.b[1]-s.a[1]);
                int K=Math.max(10,Math.min(40,(int)Math.round(len*r.rpx*(SPAN_HI-SPAN_LO)/1.5)));
                List<double[]> mine=new ArrayList<>();
                for(int k=0;k<K;k++){
                    double u=SPAN_LO+(SPAN_HI-SPAN_LO)*(k+0.5)/K;
                    double[] q={s.a[0]+u*(s.b[0]-s.a[0]),s.a[1]+u*(s.b[1]-s.a[1])};
                    double din=innerDistance(r.refPolygon,i,q,s.n);
                    double depth=Math.min(0.5*inradius,0.45*din);
                    s.rays++;
                    if(!(depth>2*step))continue;
                    double d=firstFall(img,H,q,s.n,depth,REACH_R,step,r.threshold,r.lume-r.dial);
                    if(!Double.isFinite(d))continue;
                    s.found++;s.points.add(new double[]{q[0]+d*s.n[0],q[1]+d*s.n[1]});
                    double[] lev={q[0]-r.refCentre[0],q[1]-r.refCentre[1]};
                    mine.add(new double[]{s.n[0]*er[0]+s.n[1]*er[1],s.n[0]*et[0]+s.n[1]*et[1],-s.n[0]*lev[1]+s.n[1]*lev[0],1,d,i});
                }
                s.observed=s.cover()>=MIN_SIDE_COVER&&s.found>=MIN_SIDE_SAMPLES;
                if(s.observed)rows.addAll(mine);
            }
            int obs=0;for(Side s:r.sides)if(s.observed)obs++;
            if(obs<2){r.reason=obs==0?"its luminous edges are not visible":"only one luminous edge is visible";r.samples=rows.size();return r;}
            r.inferred=obs<n;
        }
        r.samples=rows.size();
        // 3. joint robust rigid fit, the first parameter set the observed edges determine
        double halfLen=round?r.refRadius:maxDistance(r.refPolygon,r.refCentre);
        int[][] order=round?new int[][]{{0,1,3},{0,1}}:new int[][]{{0,1,2,3},{0,1,2},{1,2,3},{0,2,3},{1,2},{0,2}};
        if(round&&r.roundCover<FREE_SIZE_ROUND_COVER)order=new int[][]{{0,1}};
        Fit best=null;
        // a lit bevel merged with the lume moves edge samples OUTWARD by up to the bevel width. Against the KNOWN size
        // (a free size would absorb them) such samples lie well outside the fitted shape: they are set aside and the fit
        // repeated, so a partial merge cannot pull the position. Then the final fit on the samples kept.
        // a merged sample sits a whole surround width (about 0.016 R) outside the luminous edge
        double outlierR=Math.max(Alpha99Pipeline.HALF_SURROUND_R,3*0.3/r.rpx);   // the established 0.016 R surround / 2
        int total=rows.size();
        for(int pass=0;setup.rejectOutward&&pass<4;pass++){
            Fit fixed=null;
            for(int[] set:order){if(contains(set,3))continue;Fit f=irls(rows,set,setup.fixedGrowthR,r.rpx,halfLen);if(f!=null&&f.determined){fixed=f;break;}}
            if(fixed==null)break;
            double cut=outlierR;   // physical: half the bevel width (valid edges are gated to <= 1.5 px noise)
            List<double[]> kept=new ArrayList<>();
            for(int k=0;k<rows.size();k++)if(fixed.residualPx[k]/r.rpx<=cut)kept.add(rows.get(k));
            if(kept.size()==rows.size())break;
            rows=kept;
        }
        for(int[] set:order){Fit f=irls(rows,set,setup.fixedGrowthR,r.rpx,halfLen);if(f!=null&&f.determined){best=f;break;}}
        r.mergedShare=1-rows.size()/(double)Math.max(1,total);
        r.samples=rows.size();
        if(r.mergedShare>MAX_MERGED_SHARE){r.reason=String.format(java.util.Locale.US,"a bright bevel merges with its luminous fill on %.0f%% of the outline",100*r.mergedShare);return r;}
        if(best==null){r.reason="the visible luminous edges do not fix its position";return r;}
        r.noisePx=best.sigmaPx;
        if(r.noisePx>MAX_EDGE_NOISE_PX){r.reason=String.format(java.util.Locale.US,"its luminous edge is too irregular (%.1f px)",r.noisePx);return r;}
        r.growthFree=contains(best.set,3);
        r.radialOk=contains(best.set,0);r.tangentialOk=contains(best.set,1);r.rotationOk=contains(best.set,2);
        if(!r.growthFree)r.inferred=true;
        double sdG=r.growthFree?0:setup.growthSdR;
        double[] x=best.x,se=best.se,dg=best.dPerG;
        r.growthR=r.growthFree?x[3]:setup.fixedGrowthR;r.seGrowthR=r.growthFree?se[3]:setup.growthSdR;
        if(r.radialOk){r.radialR=x[0];r.seRadialR=Math.hypot(se[0],dg[0]*sdG);}
        if(r.tangentialOk){r.tangentialR=x[1];r.seTangentialR=Math.hypot(se[1],dg[1]*sdG);}
        if(r.rotationOk){r.rotationDeg=Math.toDegrees(x[2]);r.seRotationDeg=Math.toDegrees(Math.hypot(se[2],dg[2]*sdG));}
        // per-side residual diagnostics (preserved, never used to hide a coherent departure)
        for(int i=0;i<r.sides.size();i++){
            List<Double> res=new ArrayList<>();
            for(int k=0;k<rows.size();k++)if((int)rows.get(k)[5]==i)res.add(best.residualPx[k]);
            if(res.isEmpty())continue;
            double sum=0;for(double v:res)sum+=v;r.sides.get(i).meanResidualPx=sum/res.size();
            int w=Math.max(3,res.size()/4);double worst=0;
            for(int k=0;k+w<=res.size();k++){double m=0;for(int j=k;j<k+w;j++)m+=res.get(j);worst=Math.max(worst,Math.abs(m/w));}
            r.sides.get(i).anomalyPx=worst;
        }
        // 4. a free g outside the genuine range: the edge found is not the luminous edge (bevel merge / glare)
        if(r.growthFree&&Double.isFinite(setup.growthLimitR)&&Math.abs(r.growthR-setup.growthNominalR)>sizeLimit(r,setup.growthLimitR)+2*r.seGrowthR){
            r.reason=String.format(java.util.Locale.US,"its detected luminous edge does not match the marker's known shape (%+.2f%% of the dial radius: bright bevel or glare)",100*(r.growthR-setup.growthNominalR));
            return r;
        }
        // 5. display geometry: the known shape at the fitted pose (no growth, no deformation)
        double tx=(r.radialOk?x[0]:0)*er[0]+(r.tangentialOk?x[1]:0)*et[0],ty=(r.radialOk?x[0]:0)*er[1]+(r.tangentialOk?x[1]:0)*et[1];
        double th=r.rotationOk?x[2]:0;
        if(round)r.fittedCircle=new double[]{r.refCentre[0]+tx,r.refCentre[1]+ty,r.refRadius};
        else{
            r.fittedPolygon=new double[r.refPolygon.length][];
            for(int i=0;i<r.refPolygon.length;i++){double vx=r.refPolygon[i][0]-r.refCentre[0],vy=r.refPolygon[i][1]-r.refCentre[1];
                double c=Math.cos(th),s=Math.sin(th);
                r.fittedPolygon[i]=new double[]{r.refCentre[0]+tx+c*vx-s*vy,r.refCentre[1]+ty+s*vx+c*vy};}
        }
        r.assessed=r.radialOk||r.tangentialOk||r.rotationOk;
        if(!r.assessed)r.reason="the visible luminous edges do not fix its position";
        return r;
    }

    /**
     * The size-consistency limit applied to a fitted marker. A round marker whose outline is seen all round (free-size
     * fit) has a centre that does not depend on its size, so its size only has to reveal what it is checked for: a lit
     * bevel merged with the lume, which grows the radius by about a whole surround width. Genuine lume discs differ by
     * ~1-1.5 px between markers of one sharp photo (genuine GMT, owner's test photo 14 Oct 2026), beyond the genuine
     * photo-level limit, so for these only the established half-surround cut applies. Triangles, batons and partly seen
     * rounds keep the genuine limit (their position depends on the size assumption).
     */
    static double sizeLimit(Result r,double genuineLimit){
        if(r.shape==ModelSpec.Shape.ROUND&&r.growthFree&&Double.isFinite(r.roundCover)&&r.roundCover>=FREE_SIZE_ROUND_COVER)
            return Math.max(genuineLimit,Alpha99Pipeline.HALF_SURROUND_R);
        return genuineLimit;
    }

    /**
     * First supported luminous-to-dark fall along n, starting depth inside the expected edge: distance from q along n
     * (canonical, + outward). The edge is where the profile crosses half-way between the local luminous level and the
     * FIRST outward minimum, provided that minimum is a supported drop (at least 35% of the lume-dial contrast below the
     * lume and followed by a rise or the end of the search). This finds the edge whether the lume is followed by a dark
     * separator and a lit bevel, by a shallow separator, or straight by dark metal/dial; a lit bevel with no separator
     * at all yields its OUTER edge (a whole bevel outward), which the joint fit then sets aside or withholds.
     * NaN when the ray does not start in lume or no supported fall is found.
     */
    static double firstFall(Image img,double[] H,double[] q,double[] n,double depth,double reach,double step,double T,double contrast){
        return firstFall(img,H,q,n,depth,reach,step,T,contrast,false);
    }

    /**
     * @param steepest round markers: the edge is the steepest point of the lume's fall (sub-sample parabola on the
     *                 gradient), which a shallow or wide dark separator does not push outward (the half-way level does:
     *                 about 2 px on genuine round markers whose separator is faint on one side). A separator may then be
     *                 shallower (20% of the contrast instead of 35%), but only when the profile rises again into the lit
     *                 bevel after it (the separator signature); a bevel merged with the lume has no such dip and is still
     *                 found at its outer edge and set aside by the known-size fit.
     */
    static double firstFall(Image img,double[] H,double[] q,double[] n,double depth,double reach,double step,double T,double contrast,boolean steepest){
        int N=(int)Math.floor((depth+reach)/step)+1;if(N<8)return Double.NaN;
        double[] v=new double[N];
        for(int i=0;i<N;i++){double s=-depth+i*step;double[] p=project(H,q[0]+s*n[0],q[1]+s*n[1]);v[i]=p==null?Double.NaN:img.at(p[0],p[1]);
            if(!Double.isFinite(v[i]))return Double.NaN;}
        for(int i=0;i<3;i++)if(v[i]<T)return Double.NaN;            // must start in confirmed luminous fill
        double lume=T+0.5*contrast;                                  // the marker's luminous level (T = dial + contrast/2)
        int i=1;
        while(i<N){
            // the fall begins where the profile leaves the luminous plateau
            while(i<N&&v[i]>=lume-0.25*contrast)i++;
            if(i>=N)return Double.NaN;
            // follow it down to the first minimum (a rise of 10% of the contrast, or the end, stops it)
            int m=i;boolean rose=false;
            for(int j=i;j<N;j++){if(v[j]<v[m])m=j;else if(v[j]>v[m]+0.10*contrast){rose=true;break;}}
            int k0=Math.max(0,i-8);double loc=0;int c=0;for(int k=k0;k<i;k++){loc+=v[k];c++;}loc=c>0?Math.max(loc/c,v[i-1]):v[i-1];
            boolean deep=v[m]<=lume-0.35*contrast;
            boolean separator=steepest&&rose&&v[m]<=loc-0.20*contrast;
            if(!deep&&!separator){i=m+1;continue;}                  // a shallow dip in the lume: keep looking
            if(steepest){
                int lo=Math.max(1,i-8),best=-1;double g=0;
                for(int k=lo;k<=m&&k<N-1;k++){double d=v[k-1]-v[k+1];if(d>g){g=d;best=k;}}
                if(best>0){
                    double gm=best>1?v[best-2]-v[best]:g,gp=best<N-2?v[best]-v[best+2]:g,den=gm-2*g+gp;
                    double off=Math.abs(den)>1e-9?Math.max(-0.5,Math.min(0.5,0.5*(gm-gp)/den)):0;
                    return -depth+(best+off)*step;
                }
            }
            double level=0.5*(loc+v[m]);
            for(int k=m;k>0;k--)if(v[k-1]>=level&&v[k]<level){
                double t=(v[k-1]-level)/Math.max(1e-9,v[k-1]-v[k]);
                return -depth+(k-1+t)*step;
            }
            return Double.NaN;
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ robust joint fit
    static final class Fit{int[] set;double[] x=new double[4],se=new double[4],dPerG=new double[4];double sigmaPx;double[] residualPx;boolean determined;}

    /** rows {c_radial, c_tangential, c_lever, 1, delta, side}; set: indices of the free parameters (0 radial,
     *  1 tangential, 2 rotation, 3 growth); a parameter not in the set is 0, except growth (gFix). */
    static Fit irls(List<double[]> rows,int[] set,double gFix,double rpx,double halfLen){
        int n=rows.size(),p=set.length;if(n<p+3)return null;
        double[] w=new double[n];Arrays.fill(w,1);
        double[] y=new double[n];for(int k=0;k<n;k++)y[k]=rows.get(k)[4]-(contains(set,3)?0:gFix);
        double[] b=null;double[][] inv=null;double sigma=0.3/rpx;double[] res=new double[n];
        for(int it=0;it<20;it++){
            double[][] N=new double[p][p];double[] rhs=new double[p];
            for(int k=0;k<n;k++){double[] r=rows.get(k);for(int a=0;a<p;a++){rhs[a]+=r[set[a]]*y[k]*w[k];for(int c=0;c<p;c++)N[a][c]+=r[set[a]]*r[set[c]]*w[k];}}
            inv=invert(N);if(inv==null)return null;
            b=new double[p];for(int a=0;a<p;a++)for(int c=0;c<p;c++)b[a]+=inv[a][c]*rhs[c];
            for(int k=0;k<n;k++){double f=0;for(int a=0;a<p;a++)f+=rows.get(k)[set[a]]*b[a];res[k]=y[k]-f;}
            double[] abs=new double[n];double med=median(res);for(int k=0;k<n;k++)abs[k]=Math.abs(res[k]-med);
            sigma=Math.max(0.3/rpx,1.4826*median(abs));
            for(int k=0;k<n;k++){double a=Math.abs(res[k]);w[k]=a<=1.5*sigma?1:1.5*sigma/a;}
        }
        Fit f=new Fit();f.set=set;f.sigmaPx=sigma*rpx;f.residualPx=new double[n];for(int k=0;k<n;k++)f.residualPx[k]=res[k]*rpx;
        // dilution: parameter error per unit edge noise, with the converged weights
        double[][] N=new double[p][p];double[] one=new double[p];
        for(int k=0;k<n;k++){double[] r=rows.get(k);for(int a=0;a<p;a++){one[a]+=r[set[a]]*w[k];for(int c=0;c<p;c++)N[a][c]+=r[set[a]]*r[set[c]]*w[k];}}
        double[][] Ni=invert(N);if(Ni==null)return null;
        double wsum=0;for(double v:w)wsum+=v;
        f.determined=true;
        for(int a=0;a<p;a++){
            double dil=Math.sqrt(Math.max(0,Ni[a][a]));      // parameter error per unit edge noise
            double lever=set[a]==2?halfLen:1;                // rotation as the displacement it causes at the marker's end
            // geometric dilution, independent of the sample count: well-spread edges give ~1-3, a nearly
            // unconstrained direction (short arc, nearly parallel sides) gives far more
            if(!Double.isFinite(dil)||dil*lever*Math.sqrt(wsum)>MAX_DILUTION)f.determined=false;
            f.x[set[a]]=b[a];f.se[set[a]]=sigma*dil;
            double d=0;for(int c=0;c<p;c++)d+=Ni[a][c]*one[c];f.dPerG[set[a]]=-d;
        }
        if(!contains(set,3))f.x[3]=gFix;
        return f;
    }

    // ------------------------------------------------------------------ sampling helpers
    static double[] project(double[] H,double x,double y){
        double q=H[6]*x+H[7]*y+H[8];if(!Double.isFinite(q)||Math.abs(q)<1e-12)return null;
        return new double[]{(H[0]*x+H[1]*y+H[2])/q,(H[3]*x+H[4]*y+H[5])/q};
    }
    static double pxPerR(double[] H){
        if(H==null||H.length<9)return Double.NaN;
        double[] a=project(H,0,0),b=project(H,1,0),c=project(H,0,1);if(a==null||b==null||c==null)return Double.NaN;
        return 0.5*(Math.hypot(b[0]-a[0],b[1]-a[1])+Math.hypot(c[0]-a[0],c[1]-a[1]));
    }
    private static double sample(Image img,double[] H,double x,double y){double[] p=project(H,x,y);return p==null?Double.NaN:img.at(p[0],p[1]);}

    private static double[] polygonInterior(Image img,double[] H,double[][] poly,double scale,double rpx){
        double[] c=centroid(poly);double[][] s=new double[poly.length][];
        for(int i=0;i<poly.length;i++)s[i]=new double[]{c[0]+scale*(poly[i][0]-c[0]),c[1]+scale*(poly[i][1]-c[1])};
        double x0=1e9,x1=-1e9,y0=1e9,y1=-1e9;for(double[] p:s){x0=Math.min(x0,p[0]);x1=Math.max(x1,p[0]);y0=Math.min(y0,p[1]);y1=Math.max(y1,p[1]);}
        double st=Math.max(1.0/rpx,(x1-x0)/40);List<Double> v=new ArrayList<>();
        for(double y=y0;y<=y1;y+=st)for(double x=x0;x<=x1;x+=st)if(inside(s,x,y)){double g=sample(img,H,x,y);if(Double.isFinite(g))v.add(g);}
        return toArray(v);
    }
    private static double[] roundInterior(Image img,double[] H,double[] c,double r,double rpx){
        double st=Math.max(1.0/rpx,r/20);List<Double> v=new ArrayList<>();
        for(double y=-r;y<=r;y+=st)for(double x=-r;x<=r;x+=st)if(x*x+y*y<=r*r){double g=sample(img,H,c[0]+x,c[1]+y);if(Double.isFinite(g))v.add(g);}
        return toArray(v);
    }
    private static double[] ring(Image img,double[] H,double[] c,double r0,double r1,double rpx){
        List<Double> v=new ArrayList<>();int N=Math.max(64,(int)(2*Math.PI*r1*rpx/2));
        for(double r=r0;r<=r1+1e-12;r+=Math.max(1.0/rpx,(r1-r0)/4))for(int k=0;k<N;k++){double a=2*Math.PI*k/N;double g=sample(img,H,c[0]+r*Math.cos(a),c[1]+r*Math.sin(a));if(Double.isFinite(g))v.add(g);}
        return toArray(v);
    }
    private static double[] band(Image img,double[] H,double[][] poly,double d0,double d1,double rpx){
        List<Double> v=new ArrayList<>();double[] c=centroid(poly);
        for(int i=0;i<poly.length;i++){double[] a=poly[i],b=poly[(i+1)%poly.length];double[] n=outward(a,b,c);
            double len=Math.hypot(b[0]-a[0],b[1]-a[1]);int K=Math.max(8,(int)(len*rpx/2));
            for(double d=d0;d<=d1+1e-12;d+=Math.max(1.0/rpx,(d1-d0)/4))for(int k=0;k<K;k++){double u=(k+0.5)/K;
                double g=sample(img,H,a[0]+u*(b[0]-a[0])+d*n[0],a[1]+u*(b[1]-a[1])+d*n[1]);if(Double.isFinite(g))v.add(g);}}
        return toArray(v);
    }

    // ------------------------------------------------------------------ geometry helpers
    static double[] radial(int hour){if(hour%12==0)return new double[]{0,-1};double a=Math.toRadians(hour*30.0);return new double[]{Math.sin(a),-Math.cos(a)};}
    static double[] tangential(int hour){if(hour%12==0)return new double[]{1,0};double a=Math.toRadians(hour*30.0);return new double[]{Math.cos(a),Math.sin(a)};}
    static double[] centroid(double[][] v){
        double A=0,cx=0,cy=0;int n=v.length;
        for(int i=0;i<n;i++){double[] p=v[i],q=v[(i+1)%n];double cr=p[0]*q[1]-q[0]*p[1];A+=cr;cx+=(p[0]+q[0])*cr;cy+=(p[1]+q[1])*cr;}
        A*=0.5;if(Math.abs(A)<1e-15){double sx=0,sy=0;for(double[] p:v){sx+=p[0];sy+=p[1];}return new double[]{sx/n,sy/n};}
        return new double[]{cx/(6*A),cy/(6*A)};
    }
    static double area(double[][] v){double A=0;int n=v.length;for(int i=0;i<n;i++){double[] p=v[i],q=v[(i+1)%n];A+=p[0]*q[1]-q[0]*p[1];}return Math.abs(A)/2;}
    static double perimeter(double[][] v){double s=0;int n=v.length;for(int i=0;i<n;i++)s+=Math.hypot(v[(i+1)%n][0]-v[i][0],v[(i+1)%n][1]-v[i][1]);return s;}
    static double maxDistance(double[][] v,double[] c){double m=0;for(double[] p:v)m=Math.max(m,Math.hypot(p[0]-c[0],p[1]-c[1]));return m;}
    static double[] outward(double[] a,double[] b,double[] c){
        double dx=b[0]-a[0],dy=b[1]-a[1],l=Math.hypot(dx,dy);double[] n={dy/l,-dx/l};
        double mx=0.5*(a[0]+b[0])-c[0],my=0.5*(a[1]+b[1])-c[1];if(n[0]*mx+n[1]*my<0){n[0]=-n[0];n[1]=-n[1];}
        return n;
    }
    private static double[] intersect(double[] n1,double o1,double[] n2,double o2){
        double det=n1[0]*n2[1]-n1[1]*n2[0];if(Math.abs(det)<1e-12)return null;
        return new double[]{(o1*n2[1]-n1[1]*o2)/det,(n1[0]*o2-o1*n2[0])/det};
    }
    /** Distance from q (on side i) along -n to the polygon's other sides. */
    static double innerDistance(double[][] poly,int side,double[] q,double[] n){
        double best=Double.POSITIVE_INFINITY;int m=poly.length;
        for(int j=0;j<m;j++){if(j==side)continue;double[] a=poly[j],b=poly[(j+1)%m];
            double ex=b[0]-a[0],ey=b[1]-a[1];double dx=-n[0],dy=-n[1];double det=dx*(-ey)-dy*(-ex);if(Math.abs(det)<1e-15)continue;
            double rx=a[0]-q[0],ry=a[1]-q[1];double t=(rx*(-ey)-ry*(-ex))/det,u=(dx*ry-dy*rx)/det;
            if(t>1e-9&&u>=-1e-9&&u<=1+1e-9)best=Math.min(best,t);}
        return best;
    }
    static boolean inside(double[][] v,double x,double y){
        boolean in=false;int n=v.length;
        for(int i=0,j=n-1;i<n;j=i++)if(((v[i][1]>y)!=(v[j][1]>y))&&(x<(v[j][0]-v[i][0])*(y-v[i][1])/(v[j][1]-v[i][1])+v[i][0]))in=!in;
        return in;
    }
    static boolean contains(int[] a,int v){for(int x:a)if(x==v)return true;return false;}
    static double percentile(double[] a,double p){double[] b=a.clone();Arrays.sort(b);if(b.length==0)return Double.NaN;double i=p/100*(b.length-1);int lo=(int)Math.floor(i),hi=(int)Math.ceil(i);return b[lo]+(b[hi]-b[lo])*(i-lo);}
    static double median(double[] a){return percentile(a,50);}
    private static double[] toArray(List<Double> v){double[] o=new double[v.size()];for(int i=0;i<o.length;i++)o[i]=v.get(i);return o;}
    static double[][] invert(double[][] A){
        int n=A.length;double[][] m=new double[n][2*n];
        for(int i=0;i<n;i++){System.arraycopy(A[i],0,m[i],0,n);m[i][n+i]=1;}
        double scale=0;for(double[] r:A)for(double v:r)scale=Math.max(scale,Math.abs(v));if(!(scale>0))return null;
        for(int c=0;c<n;c++){int piv=c;for(int r=c+1;r<n;r++)if(Math.abs(m[r][c])>Math.abs(m[piv][c]))piv=r;
            if(Math.abs(m[piv][c])<1e-10*scale)return null;double[] t=m[c];m[c]=m[piv];m[piv]=t;
            double d=m[c][c];for(int k=0;k<2*n;k++)m[c][k]/=d;
            for(int r=0;r<n;r++)if(r!=c){double f=m[r][c];if(f!=0)for(int k=0;k<2*n;k++)m[r][k]-=f*m[c][k];}}
        double[][] o=new double[n][n];for(int i=0;i<n;i++)System.arraycopy(m[i],n,o[i],0,n);return o;
    }
    private static double[] solve(double[][] A,double[] b){double[][] i=invert(A);if(i==null)return null;double[] x=new double[b.length];for(int r=0;r<b.length;r++)for(int c=0;c<b.length;c++)x[r]+=i[r][c]*b[c];return x;}
}
