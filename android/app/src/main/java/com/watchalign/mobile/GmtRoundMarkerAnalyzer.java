package com.watchalign.mobile;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures the eight round hour markers (1, 2, 4, 5, 7, 8, 10, 11) against their own
 * minute ticks (alpha61).
 *
 * Each marker's outer edge is traced along 72 rays from its centre and fitted with a
 * circle (least squares, outlier rays trimmed). The reference is local, as at 12: the
 * inner ends of the three minute ticks at the marker (e.g. 04/05/06 for the 1). Measured,
 * each as a fraction of the marker's diameter:
 *   offset - sideways, from the midpoint of the ticks one minute either side;
 *            positive = clockwise (towards the later tick)
 *   gap    - outer edge to the line through those two tick ends
 * and its size relative to the other round markers on the same dial.
 *
 * Everything is measured in the original image; nothing is rotated or resampled here.
 */
final class GmtRoundMarkerAnalyzer {
    static final int[] HOURS={1,2,4,5,7,8,10,11};
    /** Rays traced around each marker's edge. */
    static final int RAYS=72;
    /** More than this share of rays off the circle (or missing) means the outline is not clean. */
    static final double MAX_REJECT_FRACTION=0.25;
    /**
     * Quality floors, from the corpus (alpha61): every genuine reading past the offset level had
     * an edge contrast under 70 grey levels, a tick-frame score under 40, an inset outside
     * 0.40-0.75 or centre-tick disagreement over 0.04 (a bracelet, a caseback, a blurred or
     * unlit dial, or a hand over the ticks). Clean genuine readings: contrast 120+, score 57+,
     * inset 0.52-0.67, disagreement under 0.02.
     */
    static final double MIN_EDGE_CONTRAST=80, MIN_TICK_SCORE=45, MIN_INSET=0.40, MAX_INSET=0.75, MAX_TICK_DISAGREEMENT=0.04;
    /** Marker centre must lie within this of where the hour belongs relative to the 12. */
    static final double MAX_ANGLE_FROM_EXPECTED_DEG=8.0;
    /** Fitted surround radius / dial radius below which the lume edge was probably traced instead. */
    static final double MIN_OUTER_R=0.074;
    /** A different edge is assumed when the radius moves more than this across the resize check (px). */
    static final double MAX_RESAMPLE_RADIUS_SHIFT_PX=1.5;
    /** Median round-marker diameter (px) needed before sizes are compared (alpha61). */
    static final double MIN_SIZE_CHECK_PX=36;

    static final class Marker {
        final int hour;
        boolean found;String reason="";
        /** Measured with the outer edge traced cleanly and the dial orientation known. */
        boolean stable;String lowReason="";
        double x=Double.NaN,y=Double.NaN,radiusPx=Double.NaN;
        /** Inner ends of the minute ticks one minute before, at, and one minute after the hour. */
        double[] tickBefore,tickCentre,tickAfter;
        double offset=Double.NaN,offsetFromCentreTick=Double.NaN,gap=Double.NaN,sizeRatio=Double.NaN;
        /** Marker centre to the tick chord, over the chord length (independent of which edge was traced). */
        double inset=Double.NaN,chordPx=Double.NaN;
        double rejectFraction=Double.NaN,contrast=Double.NaN,tickScore=Double.NaN,tickPitchDeg=Double.NaN,ticksInferred=Double.NaN;
        double angleFromExpectedDeg=Double.NaN,outerOverDialR=Double.NaN,seedX=Double.NaN,seedY=Double.NaN;
        /** The seed came from the affine fit to the other markers, so it marks where this hour belongs. */
        boolean seedPlaced;
        /** Expected surround radius at this dial size (px), for drawing a marker that was not found. */
        double expectedRadiusPx=Double.NaN;
        // Resize check: ranges over the original and the 94%/88% re-measurements.
        boolean stabilityRun,stabilitySameEdge;
        double offMin=Double.NaN,offMax=Double.NaN,gapMin=Double.NaN,gapMax=Double.NaN,insetMin=Double.NaN,insetMax=Double.NaN;
        /** Decision, filled in by the QC (GmtHumanQcAnalyzerV2): attention and a short note. */
        GmtHumanQcMath.Attention attention=GmtHumanQcMath.Attention.UNASSESSABLE;String note="";
        boolean hand,tooSmall,unstable,offCentre,sizeOdd;
        /** Hand-check readings: marker-bright share of the ring just outside, coloured (GMT hand) share. */
        double ringBright=Double.NaN,coloured=Double.NaN;
        /** The bright pixels in the ring are one straight band clear of the surround: a hand passing by. */
        boolean handBeside;
        boolean flagged(){return found&&(attention==GmtHumanQcMath.Attention.CHECK||attention==GmtHumanQcMath.Attention.STRONG);}
        boolean clear(){return found&&attention==GmtHumanQcMath.Attention.CLEAR;}
        /**
         * Same rule as the batons: stable when the same edge was found at every scale and the offset
         * either moved by at most about a pixel or stayed below the check level throughout.
         */
        boolean resampleStable(){
            if(!stabilityRun)return true;
            // The offset comes from the circle's centre, so a radius that changes between scales
            // (lume edge on one, surround on another) does not by itself make it unreliable; it
            // only stops the size comparison (ARF Pepsi, the 5: offset +0.016 to +0.017 at every
            // scale, withheld for a 2 px radius change).
            if(!Double.isFinite(offMax))return false;
            return (offMax-offMin)*diameterPx()<=GmtTwelveLandmarkAnalyzer.MAX_RESAMPLE_SHIFT_PX
                    ||Math.max(Math.abs(offMin),Math.abs(offMax))<GmtHumanQcMath.ROUND_OFFSET_CHECK;
        }
        Marker(int h){hour=h;}
        int minute(){return 5*hour;}
        int before(){return minute()-1;}
        int after(){return minute()+1;}
        String tickLabel(int m){return String.format(java.util.Locale.US,"%02d",m);}
        double diameterPx(){return 2*radiusPx;}
        /** Outline as a 16-gon, for the hand check. */
        double[][] polygon(){
            double[][] p=new double[16][];
            for(int i=0;i<16;i++){double t=2*Math.PI*i/16;p[i]=new double[]{x+radiusPx*Math.cos(t),y+radiusPx*Math.sin(t)};}
            return p;
        }
        Marker lowConfidence(String why){if(stable){stable=false;lowReason=why;}return this;}
    }

    private GmtRoundMarkerAnalyzer(){}

    /**
     * The fitted dial outline as an affine image of a circle: image = c + M (cos phi, sin phi) rho,
     * M = rot(angle) diag(a, b). Tilted photos turn the dial into an ellipse, so hour positions
     * are placed around the ellipse (starting from the 12's 60 tick) rather than a circle, and
     * the measurements are made after undoing the squash (rect), so a tilt does not read as an
     * offset. Affine only: perspective beyond that is left to the photo-angle gate.
     */
    static final class DialFrame {
        final double cx,cy,a,b,ct,st,r;
        DialFrame(double cx,double cy,double a,double b,double angleDeg){
            this.cx=cx;this.cy=cy;this.a=a;this.b=b;ct=Math.cos(Math.toRadians(angleDeg));st=Math.sin(Math.toRadians(angleDeg));r=Math.sqrt(a*b);
        }
        static DialFrame circle(double cx,double cy,double r){return new DialFrame(cx,cy,r,r,0);}
        DialFrame scaled(double k){return new DialFrame(cx*k,cy*k,a*k,b*k,Math.toDegrees(Math.atan2(st,ct)));}
        /** Image point at parameter angle phi (radians, clockwise in the image) and radius rho (dial radii). */
        double[] at(double phi,double rho){
            double u=a*Math.cos(phi)*rho,v=b*Math.sin(phi)*rho;
            return new double[]{cx+ct*u-st*v,cy+st*u+ct*v};
        }
        /** Point with the dial's squash undone, in pixels of the mean radius, relative to the centre. */
        double[] rect(double x,double y){
            double dx=x-cx,dy=y-cy,u=ct*dx+st*dy,v=-st*dx+ct*dy;
            return new double[]{u/a*r,v/b*r};
        }
        double phiOf(double x,double y){double[] q=rect(x,y);return Math.atan2(q[1],q[0]);}
    }

    /**
     * @param tick60 inner end of the detected 60 tick, or null when the 12 was not found; then
     *               markers are placed from image-up and measured with low confidence.
     */
    static List<Marker> analyse(Mat bgr,DialFrame dial,double[] tick60){
        List<Marker> out=new ArrayList<>();
        if(bgr==null||bgr.empty()||!(dial.r>20)){for(int h:HOURS){Marker m=new Marker(h);m.reason="invalid dial seed";out.add(m);}return out;}
        Mat gray=new Mat(),enh=new Mat();
        try{
            Imgproc.cvtColor(bgr,gray,Imgproc.COLOR_BGR2GRAY);
            Imgproc.createCLAHE(2.0,new Size(8,8)).apply(gray,enh);
            GmtTwelveLandmarkAnalyzer.Px g=GmtTwelveLandmarkAnalyzer.Px.of(gray),e=GmtTwelveLandmarkAnalyzer.Px.of(enh);
            double[][] seeds=seeds(g,e,dial,tick60);
            for(int i=0;i<HOURS.length;i++)out.add(measure(g,e,dial,tick60,HOURS[i],seeds[i]));
        }finally{gray.release();enh.release();}
        sizeRatios(out);
        return out;
    }

    /** Diameter against the median of the confidently traced markers on the same dial (needs 4). */
    static void sizeRatios(List<Marker> ms){
        List<Double> d=new ArrayList<>();
        for(Marker m:ms)if(m.found&&m.stable)d.add(m.diameterPx());
        if(d.size()<4)return;
        double[] a=new double[d.size()];for(int i=0;i<a.length;i++)a[i]=d.get(i);Arrays.sort(a);
        double med=a.length%2==1?a[a.length/2]:(a[a.length/2-1]+a[a.length/2])/2;
        // Sizes are only compared on markers large enough for the surround (about a fifth of the
        // radius) to be several pixels wide. Below that the fit takes the lume edge on some markers
        // and the surround's outer edge on others, which reads as a 12-18% size difference
        // (rep_cplus_wEYZOyK images 00, 02 and 03 at 25-28 px; rep_vsf_gpZWOfy image_02 at 27 px).
        if(med<MIN_SIZE_CHECK_PX)return;
        for(Marker m:ms)if(m.found)m.sizeRatio=m.diameterPx()/med;
    }

    /** Re-measures each found marker at 94% and 88% and records the ranges. */
    static void measureStability(Mat bgr,DialFrame dial,double[] tick60,List<Marker> ms){
        double[] sc=GmtTwelveLandmarkAnalyzer.STABILITY_SCALES;
        Marker[][] again=new Marker[sc.length][];
        for(int k=0;k<sc.length;k++){
            Mat m=new Mat(),gray=new Mat(),enh=new Mat();
            try{
                Imgproc.resize(bgr,m,new Size(Math.round(bgr.cols()*sc[k]),Math.round(bgr.rows()*sc[k])),0,0,Imgproc.INTER_LINEAR);
                Imgproc.cvtColor(m,gray,Imgproc.COLOR_BGR2GRAY);
                Imgproc.createCLAHE(2.0,new Size(8,8)).apply(gray,enh);
                GmtTwelveLandmarkAnalyzer.Px g=GmtTwelveLandmarkAnalyzer.Px.of(gray),e=GmtTwelveLandmarkAnalyzer.Px.of(enh);
                again[k]=new Marker[ms.size()];
                for(int i=0;i<ms.size();i++){
                    Marker o=ms.get(i);if(!(o.found&&o.stable))continue;
                    // Seeded from the original fit, so the re-measurement cannot pick a different marker.
                    again[k][i]=measure(g,e,dial.scaled(sc[k]),tick60==null?null:new double[]{tick60[0]*sc[k],tick60[1]*sc[k]},o.hour,new double[]{o.seedX*sc[k],o.seedY*sc[k],o.seedPlaced?1:0});
                }
            }finally{m.release();gray.release();enh.release();}
        }
        for(int i=0;i<ms.size();i++){
            Marker o=ms.get(i);if(!(o.found&&o.stable))continue;
            o.stabilityRun=true;o.stabilitySameEdge=true;
            double oMin=o.offset,oMax=o.offset,gMin=o.gap,gMax=o.gap,iMin=o.inset,iMax=o.inset;
            for(int k=0;k<sc.length;k++){
                Marker a=again[k][i];
                if(a==null||!a.found){oMin=oMax=gMin=gMax=iMin=iMax=Double.NaN;o.stabilitySameEdge=false;break;}
                if(Math.abs(a.radiusPx/sc[k]-o.radiusPx)>MAX_RESAMPLE_RADIUS_SHIFT_PX||!a.stable)o.stabilitySameEdge=false;
                oMin=Math.min(oMin,a.offset);oMax=Math.max(oMax,a.offset);gMin=Math.min(gMin,a.gap);gMax=Math.max(gMax,a.gap);
                iMin=Math.min(iMin,a.inset);iMax=Math.max(iMax,a.inset);
            }
            o.offMin=oMin;o.offMax=oMax;o.gapMin=gMin;o.gapMax=gMax;o.insetMin=iMin;o.insetMax=iMax;
        }
    }

    /**
     * Where to look for each marker. First each is searched for widely around its place on the
     * fitted dial ellipse; when at least four are found, an affine map from the master layout
     * (plus the 12's 60 tick) to the markers found is fitted, and every marker is looked for
     * again, closely, where that map puts it. On steep photos the dial-edge ellipse can sit
     * several marker radii off (3KSuGhC image_02); the markers themselves place each other well.
     */
    static double[][] seeds(GmtTwelveLandmarkAnalyzer.Px g,GmtTwelveLandmarkAnalyzer.Px e,DialFrame dial,double[] tick60){
        double phi12=tick60!=null?dial.phiOf(tick60[0],tick60[1]):dial.phiOf(dial.cx,dial.cy-dial.r);
        double[][] ell=new double[HOURS.length][];
        List<double[]> src=new ArrayList<>(),dst=new ArrayList<>();
        for(int i=0;i<HOURS.length;i++){
            ell[i]=dial.at(phi12+Math.toRadians(HOURS[i]*30.0),Gmt126710BlnrMaster.ROUND_CENTER_R);
            Marker w=new Marker(HOURS[i]);
            double[] c=fitCircle(g,ell[i][0],ell[i][1],Gmt126710BlnrMaster.ROUND_OUTER_R*dial.r,w,WIDE_REACH);
            if(c!=null&&w.rejectFraction<=MAX_REJECT_FRACTION){src.add(master(HOURS[i],Gmt126710BlnrMaster.ROUND_CENTER_R));dst.add(new double[]{c[0],c[1]});}
        }
        if(src.size()<4)return ell;
        if(tick60!=null){src.add(master(0,Gmt126710BlnrMaster.MINUTE_TRACK_R-0.010));dst.add(tick60);}
        double[][] A=robustAffine(src,dst,Gmt126710BlnrMaster.ROUND_OUTER_R*dial.r);
        if(A==null)return ell;
        double[][] out=new double[HOURS.length][];
        for(int i=0;i<HOURS.length;i++){double[] q=master(HOURS[i],Gmt126710BlnrMaster.ROUND_CENTER_R);out[i]=new double[]{A[0][0]*q[0]+A[0][1]*q[1]+A[0][2],A[1][0]*q[0]+A[1][1]*q[1]+A[1][2],1};}
        return out;
    }
    static final double WIDE_REACH=2.0;

    /** Master position of hour h at radius rho, 12 up, +x towards 3, +y towards 6. */
    static double[] master(int hour,double rho){double t=Math.toRadians(hour*30.0);return new double[]{Math.sin(t)*rho,-Math.cos(t)*rho};}

    /** Least-squares affine src->dst, dropping the worst point while it is more than tol off (keeps at least 4). */
    static double[][] robustAffine(List<double[]> src,List<double[]> dst,double tol){
        List<double[]> s=new ArrayList<>(src),d=new ArrayList<>(dst);
        while(true){
            double[][] A=affine(s,d);if(A==null)return null;
            int worst=-1;double wr=0;
            for(int i=0;i<s.size();i++){
                double x=A[0][0]*s.get(i)[0]+A[0][1]*s.get(i)[1]+A[0][2],y=A[1][0]*s.get(i)[0]+A[1][1]*s.get(i)[1]+A[1][2];
                double e=Math.hypot(x-d.get(i)[0],y-d.get(i)[1]);if(e>wr){wr=e;worst=i;}
            }
            if(wr<=tol||s.size()<=4)return wr<=2*tol?A:null;
            s.remove(worst);d.remove(worst);
        }
    }
    static double[][] affine(List<double[]> s,List<double[]> d){
        double[][] N=new double[3][3];double[] bx=new double[3],by=new double[3];
        for(int i=0;i<s.size();i++){
            double[] v={s.get(i)[0],s.get(i)[1],1};
            for(int j=0;j<3;j++){for(int k=0;k<3;k++)N[j][k]+=v[j]*v[k];bx[j]+=v[j]*d.get(i)[0];by[j]+=v[j]*d.get(i)[1];}
        }
        double[] x=solve3(N,bx),y=solve3(N,by);if(x==null||y==null)return null;
        return new double[][]{x,y};
    }

    static Marker measure(GmtTwelveLandmarkAnalyzer.Px g,GmtTwelveLandmarkAnalyzer.Px enh,DialFrame dial,double[] tick60,int hour,double[] seed){
        Marker m=new Marker(hour);
        boolean oriented=tick60!=null;
        double cx=dial.cx,cy=dial.cy,r=dial.r;
        double phi=dial.phiOf(seed[0],seed[1]);
        double r0=Gmt126710BlnrMaster.ROUND_OUTER_R*r;
        m.expectedRadiusPx=r0;
        m.seedX=seed[0];m.seedY=seed[1];m.seedPlaced=seed.length>2&&seed[2]>0;
        double[] c=fitCircle(g,seed[0],seed[1],r0,m,1.0);
        if(c==null){m.reason=m.reason.isEmpty()?"marker outline not found":m.reason;return m;}
        m.x=c[0];m.y=c[1];m.radiusPx=c[2];m.outerOverDialR=c[2]/r;
        m.angleFromExpectedDeg=wrap180(Math.toDegrees(dial.phiOf(m.x,m.y)-phi));
        if(oriented&&Math.abs(m.angleFromExpectedDeg)>MAX_ANGLE_FROM_EXPECTED_DEG){
            m.reason=String.format(java.util.Locale.US,"the marker found is %.0f° from where the %d should be",m.angleFromExpectedDeg,hour);
            return m;
        }
        // The marker only chooses which tick is the hour tick (±3.4° search); the ticks supply the frame.
        // Which tick is the hour tick. With the seed placed by the other markers, it is the tick
        // nearest where this marker belongs; otherwise the tick nearest the marker itself, which
        // is only unambiguous for offsets under about 0.23 of its width (half a minute).
        double ang=clockAngle(cx,cy,m.seedPlaced?seed[0]:m.x,m.seedPlaced?seed[1]:m.y);
        double[] ta=GmtTwelveLandmarkAnalyzer.tickAnglesAt(enh,cx,cy,r,ang);
        if(ta==null){m.reason="minute ticks at the marker not found";return m;}
        m.tickPitchDeg=ta[1];m.tickScore=ta[2];
        // The search fixes the ticks' phase, but within ±3.4° either neighbour can win on score
        // (a marker moved 0.3 of its width anticlockwise was measured against the next minute
        // and read +0.19), so take the tick nearest the angle chosen above.
        double hourTick=ta[0]+ta[1]*Math.round(wrap180(ang-ta[0])/ta[1]);
        ta[0]=hourTick;
        // Tick inner ends, never inside the marker: where a marker sits close to the track the
        // walk inward from a tick otherwise runs on along the marker's bright rim.
        // The fit may be on the lume (inside a dark gap and a grey surround), so the side ticks
        // also stay clear of the whole surround: the master surround radius plus 15%. The side
        // ticks sit about 1.6 surround radii from the marker centre, so this never reaches them
        // (ARF Pepsi, the 11: the 56 tick's inner end ran onto the surround and read +0.22).
        double[] avoid={m.x,m.y,m.radiusPx+1.5};
        double[] avoidWide={m.x,m.y,Math.max(m.radiusPx,r0)*1.15+1.5};
        double[] tb=GmtTwelveLandmarkAnalyzer.tickInnerEnd(enh,cx,cy,r,ta[0]-ta[1],avoidWide);
        double[] tc=GmtTwelveLandmarkAnalyzer.tickInnerEnd(enh,cx,cy,r,ta[0],avoid);
        double[] tf=GmtTwelveLandmarkAnalyzer.tickInnerEnd(enh,cx,cy,r,ta[0]+ta[1],avoidWide);
        m.ticksInferred=(tb==null?1:0)+(tc==null?1:0)+(tf==null?1:0);
        if(tb==null||tf==null){m.reason="a minute tick next to the marker could not be located (a hand over it?)";return m;}
        if(tc==null)tc=polar(cx,cy,Math.min(Math.hypot(tb[0]-cx,tb[1]-cy),Math.hypot(tf[0]-cx,tf[1]-cy)),ta[0]);
        m.tickBefore=tb;m.tickCentre=tc;m.tickAfter=tf;
        // Measure with the dial's squash undone.
        double[] B=dial.rect(tb[0],tb[1]),T=dial.rect(tc[0],tc[1]),A=dial.rect(tf[0],tf[1]),C=dial.rect(m.x,m.y);
        double ux=A[0]-B[0],uy=A[1]-B[1],un=Math.hypot(ux,uy);
        if(un<1e-6){m.reason="minute ticks at the marker not resolved";return m;}
        ux/=un;uy/=un;
        double mx=(B[0]+A[0])/2,my=(B[1]+A[1])/2;
        double nx=-uy,ny=ux;if(nx*(0-mx)+ny*(0-my)<0){nx=-nx;ny=-ny;}   // inward, towards the dial centre (origin)
        double d=2*m.radiusPx;
        m.chordPx=un;
        m.offset=((C[0]-mx)*ux+(C[1]-my)*uy)/d;
        m.offsetFromCentreTick=((C[0]-T[0])*ux+(C[1]-T[1])*uy)/d;
        double depth=(C[0]-mx)*nx+(C[1]-my)*ny;
        m.inset=depth/un;
        m.gap=(depth-m.radiusPx)/d;
        m.found=true;m.stable=true;
        if(!oriented)m.lowConfidence("the 12 marker was not found, so the dial orientation is unknown");
        if(m.rejectFraction>MAX_REJECT_FRACTION)m.lowConfidence(String.format(java.util.Locale.US,
                "%.0f%% of the outline is not on a circle (a hand, glare or a damaged edge)",100*m.rejectFraction));
        if(m.outerOverDialR<MIN_OUTER_R)m.lowConfidence("the outer edge of the surround could not be traced (only the lume)");
        if(m.ticksInferred>0)m.lowConfidence("the minute tick at the marker could not be located");
        if(m.contrast<MIN_EDGE_CONTRAST)m.lowConfidence(String.format(java.util.Locale.US,"the marker edge is faint (contrast %.0f grey levels)",m.contrast));
        if(m.tickScore<MIN_TICK_SCORE)m.lowConfidence("the minute ticks beside it are faint or unclear");
        if(m.inset<MIN_INSET||m.inset>MAX_INSET)m.lowConfidence(String.format(java.util.Locale.US,
                "it sits %.2f of a tick spacing in from the track, outside the plausible range; the marker or ticks were probably not found correctly",m.inset));
        if(Math.abs(m.offset-m.offsetFromCentreTick)>MAX_TICK_DISAGREEMENT)m.lowConfidence("the tick at the marker and the ticks either side disagree about where it should sit");
        return m;
    }

    /**
     * Returns {x, y, radius} of the marker's outer edge, or null. Starts from the bright blob
     * nearest the seed, then twice: trace the outermost falling edge on each ray, fit a circle,
     * trim rays off it, refit, and re-centre the rays on the fit.
     */
    static double[] fitCircle(GmtTwelveLandmarkAnalyzer.Px g,double sx,double sy,double r0,Marker m,double reach){
        double[] c0=blobCentre(g,sx,sy,r0,reach);
        if(c0==null){m.reason="no marker-bright area at the expected position";return null;}
        double ox=c0[0],oy=c0[1];
        double[] fit=null;
        final double step=0.25,t0=0.30*r0,t1=1.50*r0;
        final int ns=(int)Math.floor((t1-t0)/step)+1;
        for(int it=0;it<2;it++){
            double[][] prof=new double[RAYS][ns];
            for(int k=0;k<RAYS;k++){
                double a=2*Math.PI*k/RAYS,dx=Math.cos(a),dy=Math.sin(a);
                for(int i=0;i<ns;i++){double t=t0+i*step;prof[k][i]=g.at(ox+dx*t,oy+dy*t);}
            }
            // Dial level: lower quartile just outside the marker (ticks are thin and bright, so few).
            List<Double> dial=new ArrayList<>(),peak=new ArrayList<>();
            int iD0=idx(1.22*r0,t0,step),iD1=idx(1.45*r0,t0,step),iS0=idx(0.80*r0,t0,step),iS1=idx(1.02*r0,t0,step);
            for(int k=0;k<RAYS;k++){
                for(int i=iD0;i<=iD1&&i<ns;i++)if(Double.isFinite(prof[k][i]))dial.add(prof[k][i]);
                double mx=Double.NaN;for(int i=iS0;i<=iS1&&i<ns;i++)if(Double.isFinite(prof[k][i])&&!(prof[k][i]<=mx))mx=prof[k][i];
                if(Double.isFinite(mx))peak.add(mx);
            }
            if(dial.size()<50||peak.size()<RAYS/2){m.reason="marker too close to the photo edge";return null;}
            double D=pct(dial,0.25),S=pct(peak,0.50);
            m.contrast=S-D;
            if(!(S-D>=25)){m.reason=String.format(java.util.Locale.US,"marker edge contrast too low (%.0f grey levels)",S-D);return null;}
            double level=D+0.5*(S-D);
            int iStart=idx(0.50*r0,t0,step),iEnd=Math.min(ns-1,idx(1.25*r0,t0,step)),hold=(int)Math.round(2.0/step);
            // Candidate edges on each ray: every falling crossing at the half level and at a lower
            // level. With light from one side the surround's shadowed half can sit below the half
            // level, so the first crossing there is the lume edge; the lower level finds the
            // surround's outer edge on that side.
            double[] levels={level,D+0.3*(S-D)};
            List<List<Double>> cand=new ArrayList<>();
            double[] px=new double[RAYS],py=new double[RAYS];boolean[] ok=new boolean[RAYS];
            for(int k=0;k<RAYS;k++){
                List<Double> c=new ArrayList<>();
                for(int li=0;li<levels.length;li++){
                    double lv=levels[li];boolean above=false;
                    for(int i=Math.max(1,iStart);i<=iEnd;i++){
                        double v=prof[k][i];if(!Double.isFinite(v))break;
                        if(v>=lv){above=true;continue;}
                        if(!above)continue;
                        boolean stays=true;for(int j=i;j<Math.min(ns,i+hold);j++)if(!(prof[k][j]<lv)){stays=false;break;}
                        if(!stays)continue;
                        double vp=prof[k][i-1],t=t0+(i-1)*step+step*(vp-lv)/Math.max(1e-6,vp-v);
                        c.add(t);above=false;
                        if(li==0&&!ok[k]){double a=2*Math.PI*k/RAYS;px[k]=ox+Math.cos(a)*t;py[k]=oy+Math.sin(a)*t;ok[k]=true;}
                    }
                }
                cand.add(c);
            }
            fit=trimmedFit(px,py,ok,m);
            if(fit==null){if(m.reason.isEmpty())m.reason="marker outline could not be fitted";return null;}
            // Re-pick on each ray the candidate nearest the fitted circle, then fit again.
            for(int k=0;k<RAYS;k++){
                double a=2*Math.PI*k/RAYS,dx=Math.cos(a),dy=Math.sin(a),best=Double.MAX_VALUE;
                for(double t:cand.get(k)){
                    double x=ox+dx*t,y=oy+dy*t,e=Math.abs(Math.hypot(x-fit[0],y-fit[1])-fit[2]);
                    if(e<best){best=e;px[k]=x;py[k]=y;ok[k]=true;}
                }
            }
            fit=trimmedFit(px,py,ok,m);
            if(fit==null){if(m.reason.isEmpty())m.reason="marker outline could not be fitted";return null;}
            ox=fit[0];oy=fit[1];
        }
        if(fit[2]<0.55*r0||fit[2]>1.45*r0){
            m.reason=String.format(java.util.Locale.US,"the shape found is not a round marker of the expected size (radius %.2f of expected)",fit[2]/r0);
            return null;
        }
        if(Math.hypot(fit[0]-sx,fit[1]-sy)>0.6*r0*reach){
            m.reason=String.format(java.util.Locale.US,"the round shape found is %.1f marker radii from the expected position",Math.hypot(fit[0]-sx,fit[1]-sy)/r0);
            return null;
        }
        return fit;
    }

    /**
     * Kasa fit, then repeatedly: drop rays more than 3 robust SDs (at least 1 px) off the
     * circle, measured against the median radius about the current centre, and refit. The median
     * keeps a hand lying along one side (a run of long rays) from dragging the first fit far
     * enough to hide itself.
     */
    static double[] trimmedFit(double[] px,double[] py,boolean[] ok,Marker m){
        int n=px.length;boolean[] use=ok.clone();
        double[] f=kasa(px,py,use);if(f==null)return null;
        f=leastMedianCentre(px,py,ok,f);
        for(int pass=0;pass<4;pass++){
            double[] dd=new double[n];int c=0;
            for(int i=0;i<n;i++)if(ok[i])dd[c++]=Math.hypot(px[i]-f[0],py[i]-f[1]);
            double[] sd=Arrays.copyOf(dd,c);Arrays.sort(sd);
            double med=c>0?sd[c/2]:f[2];
            double[] res=new double[c];for(int i=0;i<c;i++)res[i]=Math.abs(dd[i]-med);Arrays.sort(res);
            double mad=c>0?res[c/2]:0;
            double lim=Math.max(1.0,3.0*1.4826*mad);
            boolean changed=false;
            for(int i=0;i<n;i++){boolean u=ok[i]&&Math.abs(Math.hypot(px[i]-f[0],py[i]-f[1])-med)<=lim;changed|=u!=use[i];use[i]=u;}
            double[] g=kasa(px,py,use);if(g==null)return null;f=g;
            if(!changed&&pass>0)break;
        }
        int kept=0;for(boolean u:use)if(u)kept++;
        if(kept<n/3){m.rejectFraction=1.0-kept/(double)n;return null;}
        // The quality gate counts only gross misses (no edge, or more than 2 px and 8% of the
        // radius off the circle: a hand, glare, a chipped edge). Trimming for the fit is tighter,
        // and on sharp studio photos it trimmed a third of the rays for sub-pixel lighting
        // differences around a clean surround (WOS CPO images, 46 px markers).
        double gross=Math.max(2.0,0.08*f[2]);int bad=0;
        for(int i=0;i<n;i++)if(!ok[i]||Math.abs(Math.hypot(px[i]-f[0],py[i]-f[1])-f[2])>gross)bad++;
        m.rejectFraction=bad/(double)n;
        return f;
    }

    /**
     * Least-median start: the centre within 0.35 radii of the first fit, on a grid of at least half a pixel, where
     * the median spread of ray distances about their median is smallest. Half the rays can be off
     * the circle without moving it.
     */
    static double[] leastMedianCentre(double[] px,double[] py,boolean[] ok,double[] f){
        int n=0;for(boolean b:ok)if(b)n++;
        if(n<6)return f;
        double[] d=new double[n],e=new double[n];
        double best=Double.MAX_VALUE,bx=f[0],by=f[1],br=f[2],span=0.35*f[2];
        double step=Math.max(0.5,f[2]/30.0);   // coarse; the refits that follow are least squares
        for(double dy=-span;dy<=span+1e-9;dy+=step)for(double dx=-span;dx<=span+1e-9;dx+=step){
            double cx=f[0]+dx,cy=f[1]+dy;int k=0;
            for(int i=0;i<px.length;i++)if(ok[i])d[k++]=Math.hypot(px[i]-cx,py[i]-cy);
            double[] s=d.clone();Arrays.sort(s);double med=s[n/2];
            for(int i=0;i<n;i++)e[i]=Math.abs(d[i]-med);Arrays.sort(e);
            double score=e[n/2]+1e-6*Math.hypot(dx,dy);
            if(score<best){best=score;bx=cx;by=cy;br=med;}
        }
        return new double[]{bx,by,br};
    }

    /** Algebraic least-squares circle: x^2+y^2+Dx+Ey+F=0. */
    static double[] kasa(double[] x,double[] y,boolean[] use){
        double sx=0,sy=0,sxx=0,syy=0,sxy=0,sz=0,sxz=0,syz=0;int n=0;
        double mx=0,my=0;for(int i=0;i<x.length;i++)if(use[i]){mx+=x[i];my+=y[i];n++;}
        if(n<6)return null;mx/=n;my/=n;
        for(int i=0;i<x.length;i++)if(use[i]){
            double a=x[i]-mx,b=y[i]-my,z=a*a+b*b;
            sx+=a;sy+=b;sxx+=a*a;syy+=b*b;sxy+=a*b;sz+=z;sxz+=a*z;syz+=b*z;
        }
        // Normal equations for D,E,F (centred coordinates).
        double[][] A={{sxx,sxy,sx},{sxy,syy,sy},{sx,sy,n}};
        double[] B={-sxz,-syz,-sz};
        double[] s=solve3(A,B);if(s==null)return null;
        double ccx=-s[0]/2,ccy=-s[1]/2,rr=ccx*ccx+ccy*ccy-s[2];
        if(!(rr>0))return null;
        return new double[]{ccx+mx,ccy+my,Math.sqrt(rr)};
    }

    private static double[] solve3(double[][] a,double[] b){
        double det=a[0][0]*(a[1][1]*a[2][2]-a[1][2]*a[2][1])-a[0][1]*(a[1][0]*a[2][2]-a[1][2]*a[2][0])+a[0][2]*(a[1][0]*a[2][1]-a[1][1]*a[2][0]);
        if(Math.abs(det)<1e-12)return null;
        double[] x=new double[3];
        for(int c=0;c<3;c++){
            double[][] m=new double[3][3];
            for(int i=0;i<3;i++)for(int j=0;j<3;j++)m[i][j]=j==c?b[i]:a[i][j];
            x[c]=(m[0][0]*(m[1][1]*m[2][2]-m[1][2]*m[2][1])-m[0][1]*(m[1][0]*m[2][2]-m[1][2]*m[2][0])+m[0][2]*(m[1][0]*m[2][1]-m[1][1]*m[2][0]))/det;
        }
        return x;
    }

    /**
     * Centroid of the connected bright area nearest the seed: pixels above an Otsu level
     * within 1.3 marker radii of the seed. Null when there is no plausible marker-sized area.
     */
    static double[] blobCentre(GmtTwelveLandmarkAnalyzer.Px g,double sx,double sy,double r0,double reach){
        double lim=(0.5+0.8*reach)*r0;int x0=(int)Math.floor(sx-lim),x1=(int)Math.ceil(sx+lim),y0=(int)Math.floor(sy-lim),y1=(int)Math.ceil(sy+lim);
        if(x0<1||y0<1||x1>=g.w-1||y1>=g.h-1)return null;
        int w=x1-x0+1,h=y1-y0+1;
        int[] hist=new int[256];double[] v=new double[w*h];boolean[] in=new boolean[w*h];int nIn=0;
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int i=y*w+x;double X=x0+x,Y=y0+y;
            if(Math.hypot(X-sx,Y-sy)>lim)continue;
            v[i]=g.d[(y0+y)*g.w+(x0+x)]&0xff;in[i]=true;hist[(int)v[i]]++;nIn++;
        }
        int t=otsu(hist,nIn);
        boolean[] on=new boolean[w*h];
        int best=-1;double bestD=Double.MAX_VALUE;
        for(int i=0;i<w*h;i++)if(in[i]&&v[i]>t){on[i]=true;double d=Math.hypot(x0+i%w-sx,y0+i/w-sy);if(d<bestD){bestD=d;best=i;}}
        if(best<0||bestD>0.8*r0*reach)return null;
        int[] q=new int[w*h];boolean[] seen=new boolean[w*h];int qh=0,qt=0;q[qt++]=best;seen[best]=true;
        double sumX=0,sumY=0;int n=0;
        while(qh<qt){
            int i=q[qh++];int x=i%w,y=i/w;sumX+=x0+x;sumY+=y0+y;n++;
            int[] nb={i-1,i+1,i-w,i+w};
            for(int j:nb){
                if(j<0||j>=w*h||seen[j]||!on[j])continue;
                if((j==i-1&&x==0)||(j==i+1&&x==w-1))continue;
                seen[j]=true;q[qt++]=j;
            }
        }
        double area=Math.PI*r0*r0;
        if(n<0.25*area||n>2.5*area)return null;
        return new double[]{sumX/n,sumY/n};
    }

    /**
     * Share of the ring from the marker's edge out to 2.2 radii (dial side of the minute track)
     * that is strongly coloured. The dial is black, the markers white and the hour and minute
     * hands steel; the GMT hand's shaft is the only saturated colour there (blue or red). It can
     * cover a marker with its arrowhead while its bright parts barely leave the outline (official
     * render: the arrow over the 5 read as a clear marker).
     */
    static double colouredFraction(Mat bgr,Marker m,double dialCx,double dialCy){
        double R=m.radiusPx,track=Math.hypot(m.tickCentre[0]-dialCx,m.tickCentre[1]-dialCy)-2;
        int x0=(int)Math.max(0,Math.floor(m.x-2.2*R)),x1=(int)Math.min(bgr.cols()-1,Math.ceil(m.x+2.2*R));
        int y0=(int)Math.max(0,Math.floor(m.y-2.2*R)),y1=(int)Math.min(bgr.rows()-1,Math.ceil(m.y+2.2*R));
        if(x1<=x0||y1<=y0)return Double.NaN;
        int w=x1-x0+1,h=y1-y0+1;byte[] px=new byte[w*h*3];
        Mat roi=bgr.submat(y0,y1+1,x0,x1+1).clone();
        try{roi.get(0,0,px);}finally{roi.release();}
        List<Double> chroma=new ArrayList<>();
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            double X=x0+x,Y=y0+y,rr=Math.hypot(X-m.x,Y-m.y);
            if(rr<R+1||rr>2.2*R||Math.hypot(X-dialCx,Y-dialCy)>track)continue;
            int i=3*(y*w+x);int b=px[i]&0xff,g=px[i+1]&0xff,r=px[i+2]&0xff;
            int mx=Math.max(b,Math.max(g,r)),mn=Math.min(b,Math.min(g,r));
            // Only the GMT hands' hues count: red (Pepsi, Coke) or blue (Batman, Batgirl). Green or
            // yellow casts from reflections are common on black dials (e99gXKb image_00).
            boolean red=r==mx&&b<0.75*r&&g<0.75*r, blue=b==mx&&r<0.75*b;
            chroma.add(mx>45&&(red||blue)?(mx-mn)/(double)mx:0.0);
        }
        if(chroma.size()<30)return Double.NaN;
        // Relative to the dial's own tint: a colour cast over the whole photo is not a hand.
        double base=pct(chroma,0.5),lim=Math.max(0.45,base+0.30);
        int n=0;for(double c:chroma)if(c>lim)n++;
        return n/(double)chroma.size();
    }
    static final double MAX_COLOURED_FRACTION = 0.01;

    private static int otsu(int[] hist,int total){
        double sum=0;for(int i=0;i<256;i++)sum+=i*(double)hist[i];
        double sumB=0,wB=0,best=-1;int t=128;
        for(int i=0;i<256;i++){
            wB+=hist[i];if(wB==0)continue;double wF=total-wB;if(wF==0)break;
            sumB+=i*(double)hist[i];double mB=sumB/wB,mF=(sum-sumB)/wF,between=wB*wF*(mB-mF)*(mB-mF);
            if(between>best){best=between;t=i;}
        }
        return t;
    }

    private static int idx(double t,double t0,double step){return (int)Math.round((t-t0)/step);}
    private static double pct(List<Double> l,double q){
        double[] a=new double[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i);Arrays.sort(a);
        return a[(int)Math.round(q*(a.length-1))];
    }
    static double[] polar(double cx,double cy,double radius,double clockDeg){
        double t=Math.toRadians(clockDeg);return new double[]{cx+Math.sin(t)*radius,cy-Math.cos(t)*radius};
    }
    static double clockAngle(double cx,double cy,double x,double y){return Math.toDegrees(Math.atan2(x-cx,cy-y));}
    static double wrap180(double d){while(d>180)d-=360;while(d<=-180)d+=360;return d;}
}
