package com.watchalign.mobile;

import org.opencv.core.Mat;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alpha99 per-marker interference check: does a hand (or another long foreign structure) cross or touch this marker?
 *
 * Reads the image on the frozen pose H only; it never changes a measurement. Alpha94's outline-integrity test catches
 * occluders that hide a large share of a marker's outline, but a thin hand (seconds hand, GMT shaft) covers only a few
 * percent of the outline and passes it. Every hand that reaches a marker runs radially from the centre, so it also
 * crosses the plain dial just inside the marker. This check samples a straight strip along the marker's radial axis
 * (the marker plus a margin each side and a corridor towards the centre), takes each row's dial background as the row
 * median, marks samples that differ from it strongly, and looks for a connected, radially elongated structure that
 * reaches the marker itself (within a small halo of its outline). Any such structure withholds the marker: its
 * offset / rotation is never reported as a finding. A strip dominated by foreign samples (glare, reflections) also
 * withholds it. Fail closed: if the strip cannot be sampled, the marker is withheld.
 *
 * Fixed geometry (canonical dial units, R = 1), set from the Alpha92 master before any photo was looked at:
 *   strip half-width = marker half-width + 0.04; corridor 0.12 inward of the marker's inner edge (12: from r 0.50);
 *   outer limit = marker outer edge, at most 0.92 (inside the minute track); marker halo = 0.012 + 2.5 px;
 *   foreign = |I - row median| > max(20 grey levels, 5 x robust noise); a hand = a component reaching the halo,
 *   spanning at least 0.05 R radially, whose closest approach to the outline (followed into the halo against the
 *   halo's own glow profile) is within 0.012 R + 1 px: Alpha94's final edge search reads +/- 0.012 R around the
 *   outline, so anything closer can enter the measured edge, anything further cannot.
 * Thin hands over the marker face (elongated, at most 0.05 R wide, spanning 0.05 R) also withhold it.
 * Seconds hand: on a black dial its dark shaft is visible only where it crosses a marker's bright edge, so its lume
 * dot (a bright disc on a thin shaft, centred r 0.50-0.60, contrast >= 0.45 x the marker lume contrast) fixes the
 * radial line it runs along, and every marker that line passes within 0.012 R + 1 px + 0.006 R (shaft half-width) of
 * is withheld.
 */
final class Alpha99MarkerInterference {
    static final double MARGIN=0.04,CORRIDOR=0.12,OUTER_LIMIT=0.92,HALO=0.012,HALO_PX=2.5;
    static final double MIN_LEVELS=20,NOISE_K=5,MIN_SPAN=0.05,MAX_FOREIGN=0.35,INNER=0.02,MAX_HAND_WIDTH=0.05;
    static final double DATE_X0=0.40,DATE_HALF_Y=0.20,DOT_R0=0.50,DOT_R1=0.60,DOT_IN=0.02,DOT_OUT=0.065,DOT_EDGE=0.05,DOT_ALONG=0.06,DOT_SHAFT=0.012,DOT_MIN_FRAC=0.45,SHAFT_HALF=0.006;
    static final double TOUCH_R=0.012,TOUCH_PX=1.0;
    static final String HAND="hand crosses or touches this marker";
    static final String GLARE="reflection or glare around this marker";

    static final class Check {
        final int hour;
        boolean clean;
        String reason="";
        /** radial span (R) of the longest structure touching the marker; foreign share of the strip. */
        double touchSpanR,acrossSpanR,foreignFrac;
        /** the seconds hand's line (centre -> lume dot) crosses this marker. */
        boolean secondsHand;
        /** closest approach (px) of a structure that withholds the marker to the marker's master outline. */
        double touchGapPx=Double.POSITIVE_INFINITY;
        /** debug grid: rows = r (inner -> outer), cols = t; 0 dial, 1 foreign, 2 hand evidence, 3 marker edge band (not judged), 4 marker face, 5 foreign on the face, -1 off. */
        int rows,cols;int[] grid;float[] values;
        Check(int hour){this.hour=hour;}
    }

    /** Grey-level sampler at image pixel coordinates (Alpha91SplineImage in the app; synthetic images in tests). */
    interface Sampler{double at(double px,double py);}

    private Alpha99MarkerInterference(){}

    /** Checks for 12 and every Alpha94 hour. Never null; a missing image / pose gives every marker not clean. */
    static Map<Integer,Check> analyse(Mat gray,double[] H){
        double rpx=pxPerR(H);
        Sampler img=null;
        try{
            if(gray!=null&&!gray.empty()&&rpx>20){
                double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
                for(int k=0;k<72;k++){double t=2*Math.PI*k/72;double[] p=project(H,Math.cos(t),Math.sin(t));
                    if(p==null)continue;minX=Math.min(minX,p[0]);maxX=Math.max(maxX,p[0]);minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);}
                if(Double.isFinite(minX)){Alpha91SplineImage sp=new Alpha91SplineImage(gray,(int)Math.floor(minX)-4,(int)Math.floor(minY)-4,(int)Math.ceil(maxX)+5,(int)Math.ceil(maxY)+5);img=sp::at;}
            }
        }catch(Throwable t){img=null;}
        return analyse(img,H);
    }

    /** As above on any sampler; a null sampler or unusable pose withholds every marker. */
    static Map<Integer,Check> analyse(Sampler img,double[] H){
        Map<Integer,Check> out=new LinkedHashMap<>();
        int[] hours=new int[Alpha94MarkerMeasurement.HOURS.length+1];hours[0]=12;
        System.arraycopy(Alpha94MarkerMeasurement.HOURS,0,hours,1,Alpha94MarkerMeasurement.HOURS.length);
        double rpx=pxPerR(H);
        if(!(rpx>20))img=null;
        // seconds hand: a dark shaft is invisible on a black dial except where it crosses a marker's bright edge, so its
        // lume dot fixes the line (centre -> dot) it runs along; every marker on that line is withheld
        java.util.List<Double> seconds=new java.util.ArrayList<>();
        if(img!=null){
            try{
                double L=lumeContrast(img,H);
                if(Double.isFinite(L)&&L>0)for(double[] d:dots(img,H,rpx))if(d[2]>=DOT_MIN_FRAC*L)seconds.add(d[0]);
            }catch(Throwable t){img=null;}
        }
        for(int h:hours){
            Check c;
            try{c=img==null?null:check(img,H,h,rpx);}catch(Throwable t){c=null;}
            if(c==null){c=new Check(h);c.clean=false;c.reason="marker surroundings could not be checked";}
            for(double psi:seconds)if(onLine(h,psi,TOUCH_R+TOUCH_PX/rpx+SHAFT_HALF)){c.secondsHand=true;if(c.clean){c.clean=false;c.reason=HAND;}}
            out.put(h,c);
        }
        return out;
    }

    static Check check(Sampler img,double[] H,int hour,double rpx){
        Check c=new Check(hour);
        double a=Math.toRadians(hour*30.0);double[] er={Math.sin(a),-Math.cos(a)},et={Math.cos(a),Math.sin(a)};
        double hw,rin,rout;
        if(hour==12){hw=Alpha92GmtMaster.TRI_HALF_BASE;rin=Alpha92GmtMaster.TRI_APEX_R;rout=Alpha92GmtMaster.TRI_BASE_R;}
        else if(hour==6||hour==9||hour==3){hw=Alpha92GmtMaster.BATON_TANGENTIAL_HALF;rin=Alpha92GmtMaster.BATON_CENTER_R-Alpha92GmtMaster.BATON_RADIAL_HALF;rout=Alpha92GmtMaster.BATON_CENTER_R+Alpha92GmtMaster.BATON_RADIAL_HALF;}
        else{hw=Alpha92GmtMaster.ROUND_OUTER_R;rin=Alpha92GmtMaster.ROUND_CENTER_R-hw;rout=Alpha92GmtMaster.ROUND_CENTER_R+hw;}
        double r0=hour==12?0.50:rin-CORRIDOR,r1=Math.min(rout,OUTER_LIMIT),tw=hw+MARGIN;
        double step=0.75/rpx,halo=HALO+HALO_PX/rpx,inner=INNER+HALO_PX/rpx;
        int rows=(int)Math.ceil((r1-r0)/step)+1,cols=2*(int)Math.ceil(tw/step)+1;
        float[] v=new float[rows*cols],dd=new float[rows*cols];int[] g=new int[rows*cols];
        for(int i=0;i<rows;i++){double r=r0+i*step;
            for(int j=0;j<cols;j++){double t=(j-(cols-1)/2)*step;int k=i*cols+j;
                if(Math.hypot(r,t)>OUTER_LIMIT){g[k]=-1;continue;}
                double d=dist(hour,r,t);dd[k]=(float)d;
                if(d<=halo&&d>-inner)g[k]=3;
                else if(d<=-inner)g[k]=4;
                double[] p=project(H,r*er[0]+t*et[0],r*er[1]+t*et[1]);
                double val=p==null?Double.NaN:img.at(p[0],p[1]);
                if(!Double.isFinite(val))return null;
                v[k]=(float)val;}
        }
        // row background (median) and robust noise
        double[] res=new double[rows*cols];int nres=0;double[] rowMed=new double[rows];
        double[] buf=new double[cols];
        for(int i=0;i<rows;i++){int n=0;for(int j=0;j<cols;j++){int k=i*cols+j;if(g[k]==0)buf[n++]=v[k];}
            rowMed[i]=n>=4?median(Arrays.copyOf(buf,n)):Double.NaN;
            if(Double.isFinite(rowMed[i]))for(int j=0;j<cols;j++){int k=i*cols+j;if(g[k]==0)res[nres++]=Math.abs(v[k]-rowMed[i]);}}
        if(nres<50)return null;
        double sigma=1.4826*median(Arrays.copyOf(res,nres)),thr=Math.max(MIN_LEVELS,NOISE_K*sigma);
        int valid=0,foreign=0;
        for(int i=0;i<rows;i++)for(int j=0;j<cols;j++){int k=i*cols+j;if(g[k]!=0)continue;
            if(!Double.isFinite(rowMed[i])){g[k]=-1;continue;}
            valid++;if(Math.abs(v[k]-rowMed[i])>thr){g[k]=1;foreign++;}}
        c.foreignFrac=valid>0?foreign/(double)valid:1;
        // connected foreign structures (8-neighbour) that touch the marker halo
        boolean[] seen=new boolean[rows*cols];double best=0;
        ArrayDeque<Integer> q=new ArrayDeque<>();java.util.List<Integer> comp=new java.util.ArrayList<>();
        for(int s=0;s<g.length;s++){
            if(g[s]!=1||seen[s])continue;
            comp.clear();q.add(s);seen[s]=true;boolean touch=false;int iMin=Integer.MAX_VALUE,iMax=-1;
            while(!q.isEmpty()){int k=q.poll();comp.add(k);int i=k/cols,j=k%cols;iMin=Math.min(iMin,i);iMax=Math.max(iMax,i);
                for(int di=-1;di<=1;di++)for(int dj=-1;dj<=1;dj++){int ii=i+di,jj=j+dj;if(ii<0||jj<0||ii>=rows||jj>=cols)continue;
                    int kk=ii*cols+jj;if(g[kk]==3)touch=true;if(g[kk]==1&&!seen[kk]){seen[kk]=true;q.add(kk);}}}
            if(!touch)continue;
            double span=(iMax-iMin+1)*step;
            if(span<MIN_SPAN)continue;
            double gap=gapInto(g,v,dd,rows,cols,comp,rpx);
            if(gap<c.touchGapPx)c.touchGapPx=gap;
            if(gap>TOUCH_R*rpx+TOUCH_PX)continue;            // passes clear of the edge the measurement reads
            for(int k:comp)g[k]=2;
            if(span>best)best=span;
        }
        c.touchSpanR=best;
        // a thin structure across the marker's own face (hand over the lume): elongated, narrow, darker or brighter
        double across=interior(g,v,rows,cols,step);
        c.acrossSpanR=across;
        if(best>=MIN_SPAN||across>=MIN_SPAN){c.clean=false;c.reason=HAND;}
        else if(c.foreignFrac>MAX_FOREIGN){c.clean=false;c.reason=GLARE;}
        else c.clean=true;
        c.rows=rows;c.cols=cols;c.grid=g;c.values=v;
        return c;
    }

    /**
     * Follows a withholding structure into the marker's edge band (judged against the band's own glow profile: the
     * median at the same distance from the outline) and returns its closest approach to the outline in px (0 = on it).
     */
    static double gapInto(int[] g,float[] v,float[] dd,int rows,int cols,java.util.List<Integer> comp,double rpx){
        int nb=40;java.util.List<java.util.List<Double>> bins=new java.util.ArrayList<>();for(int b=0;b<nb;b++)bins.add(new java.util.ArrayList<>());
        for(int k=0;k<g.length;k++)if(g[k]==3&&dd[k]>0){int b=(int)(dd[k]*rpx/0.5);if(b<nb)bins.get(b).add((double)v[k]);}
        double[] med=new double[nb],thr=new double[nb];
        for(int b=0;b<nb;b++){java.util.List<Double> l=bins.get(b);if(l.size()<8){med[b]=Double.NaN;continue;}
            double[] x=new double[l.size()];for(int i=0;i<x.length;i++)x[i]=l.get(i);med[b]=median(x.clone());
            double[] dv=new double[x.length];for(int i=0;i<x.length;i++)dv[i]=Math.abs(x[i]-med[b]);thr[b]=Math.max(MIN_LEVELS,NOISE_K*1.4826*median(dv));}
        boolean[] seen=new boolean[g.length];ArrayDeque<Integer> q=new ArrayDeque<>();double gap=Double.POSITIVE_INFINITY;
        for(int k:comp){seen[k]=true;q.add(k);gap=Math.min(gap,Math.max(0,dd[k])*rpx);}
        while(!q.isEmpty()){int k=q.poll();int i=k/cols,j=k%cols;
            for(int di=-1;di<=1;di++)for(int dj=-1;dj<=1;dj++){int ii=i+di,jj=j+dj;if(ii<0||jj<0||ii>=rows||jj>=cols)continue;
                int kk=ii*cols+jj;if(seen[kk]||g[kk]!=3)continue;seen[kk]=true;
                if(dd[kk]<=0){gap=0;continue;}
                int b=(int)(dd[kk]*rpx/0.5);if(b>=nb||!Double.isFinite(med[b]))continue;
                if(Math.abs(v[kk]-med[b])>thr[b]){gap=Math.min(gap,dd[kk]*rpx);q.add(kk);}}}
        return gap;
    }

    private static double interior(int[] g,float[] v,int rows,int cols,double step){
        int n=0;for(int x:g)if(x==4)n++;
        if(n<30)return 0;
        double[] a=new double[n];int m=0;for(int k=0;k<g.length;k++)if(g[k]==4)a[m++]=v[k];
        double med=median(a.clone());double[] dev=new double[n];for(int i=0;i<n;i++)dev[i]=Math.abs(a[i]-med);
        double thr=Math.max(MIN_LEVELS,NOISE_K*1.4826*median(dev));
        for(int k=0;k<g.length;k++)if(g[k]==4&&Math.abs(v[k]-med)>thr)g[k]=5;
        boolean[] seen=new boolean[g.length];ArrayDeque<Integer> q=new ArrayDeque<>();double best=0;
        java.util.List<Integer> comp=new java.util.ArrayList<>();
        for(int s=0;s<g.length;s++){
            if(g[s]!=5||seen[s])continue;
            comp.clear();q.add(s);seen[s]=true;int iMin=Integer.MAX_VALUE,iMax=-1;
            while(!q.isEmpty()){int k=q.poll();comp.add(k);int i=k/cols,j=k%cols;iMin=Math.min(iMin,i);iMax=Math.max(iMax,i);
                for(int di=-1;di<=1;di++)for(int dj=-1;dj<=1;dj++){int ii=i+di,jj=j+dj;if(ii<0||jj<0||ii>=rows||jj>=cols)continue;
                    int kk=ii*cols+jj;if(g[kk]==5&&!seen[kk]){seen[kk]=true;q.add(kk);}}}
            double span=(iMax-iMin+1)*step,width=comp.size()*step/(iMax-iMin+1);
            if(width<=MAX_HAND_WIDTH&&span>best)best=span;
            if(width<=MAX_HAND_WIDTH&&span>=MIN_SPAN)for(int k:comp)g[k]=2;
        }
        return best;
    }

    /**
     * Seconds-hand lume dot candidates: bright filled discs centred between r 0.50 and 0.60 (where the GMT seconds hand
     * carries its dot). Returns {angle deg clockwise from 12, r, score} rows with score = darkest disc sample minus the
     * median of the surrounding ring, in grey levels.
     */
    static java.util.List<double[]> dots(Sampler img,double[] H,double rpx){
        java.util.List<double[]> out=new java.util.ArrayList<>();
        double step=1.0/rpx;
        int na=(int)Math.ceil(2*Math.PI*0.55/step);
        double[] best=new double[na];double[] bestR=new double[na];
        for(int ia=0;ia<na;ia++){double a=2*Math.PI*ia/na;best[ia]=Double.NEGATIVE_INFINITY;
            for(double r=DOT_R0;r<=DOT_R1+1e-9;r+=step){
                double cx=r*Math.sin(a),cy=-r*Math.cos(a);
                if(cx>DATE_X0&&Math.abs(cy)<DATE_HALF_Y)continue;    // date window + frame (fixed master region)
                double mn=Double.POSITIVE_INFINITY;boolean ok=true;
                for(int k=-1;k<8;k++){double x=cx,y=cy;if(k>=0){double b=k*Math.PI/4;x+=DOT_IN*Math.cos(b);y+=DOT_IN*Math.sin(b);}
                    double[] p=project(H,x,y);double v=p==null?Double.NaN:img.at(p[0],p[1]);if(!Double.isFinite(v)){ok=false;break;}mn=Math.min(mn,v);}
                if(!ok)continue;
                double[] ring=new double[16];
                for(int k=0;k<16;k++){double b=k*Math.PI/8;double[] p=project(H,cx+DOT_OUT*Math.cos(b),cy+DOT_OUT*Math.sin(b));ring[k]=p==null?Double.NaN:img.at(p[0],p[1]);if(!Double.isFinite(ring[k])){ok=false;break;}}
                if(!ok)continue;
                double bg=median(ring),sc=mn-bg;
                if(!(sc>best[ia]))continue;
                // a dot on a thin shaft, not a lume bar: beyond the disc along the radius only the thin shaft continues
                // (dark +/- 0.012 either side of it: >= 3 of 4), and across the radius it is dark at 0.05 on both sides
                double ex=Math.sin(a),ey=-Math.cos(a),tx=Math.cos(a),ty=Math.sin(a);int dark=0,across=0;
                for(int e=-1;e<=1;e+=2)for(int f=-1;f<=1;f+=2){
                    if(darkAt(img,H,cx+e*DOT_ALONG*ex+f*DOT_SHAFT*tx,cy+e*DOT_ALONG*ey+f*DOT_SHAFT*ty,bg,sc))dark++;
                }
                for(int f=-1;f<=1;f+=2)if(darkAt(img,H,cx+f*DOT_EDGE*tx,cy+f*DOT_EDGE*ty,bg,sc))across++;
                if(dark<3||across<2)continue;
                best[ia]=sc;bestR[ia]=r;
            }
        }
        for(int ia=0;ia<na;ia++){
            double prev=best[(ia+na-1)%na],next=best[(ia+1)%na];
            if(best[ia]>=prev&&best[ia]>next)out.add(new double[]{360.0*ia/na,bestR[ia],best[ia]});
        }
        out.sort((x,y)->Double.compare(y[2],x[2]));
        return out;
    }

    /** Lume contrast of this photo: median round-marker centre minus median dial between markers (grey levels). */
    static double lumeContrast(Sampler img,double[] H){
        double[] lume=new double[8],dial=new double[24];int i=0;
        for(int h:Alpha94MarkerMeasurement.ROUND_HOURS){double a=Math.toRadians(h*30.0);double[] p=project(H,Alpha92GmtMaster.ROUND_CENTER_R*Math.sin(a),-Alpha92GmtMaster.ROUND_CENTER_R*Math.cos(a));lume[i++]=p==null?Double.NaN:img.at(p[0],p[1]);}
        for(int k=0;k<24;k++){double a=Math.toRadians(k*15.0+7.5);double[] p=project(H,0.66*Math.sin(a),-0.66*Math.cos(a));dial[k]=p==null?Double.NaN:img.at(p[0],p[1]);}
        for(double x:lume)if(!Double.isFinite(x))return Double.NaN;
        for(double x:dial)if(!Double.isFinite(x))return Double.NaN;
        return median(lume)-median(dial);
    }

    /** Does the radial line at psi (deg clockwise from 12) pass within tol of marker hour's master shape? */
    static boolean onLine(int hour,double psi,double tol){
        double d=Math.toRadians(psi-hour*30.0);
        for(double rho=0.45;rho<=0.95;rho+=0.0025)if(dist(hour,rho*Math.cos(d),rho*Math.sin(d))<=tol)return true;
        return false;
    }

    private static boolean darkAt(Sampler img,double[] H,double x,double y,double bg,double sc){
        double[] p=project(H,x,y);double v=p==null?Double.NaN:img.at(p[0],p[1]);return Double.isFinite(v)&&v-bg<0.5*sc;
    }

    /** Signed distance (canonical units, > 0 outside) from (r, t) in the marker's frame to the master marker shape. */
    static double dist(int hour,double r,double t){
        if(hour==12){
            double[][] P={{Alpha92GmtMaster.TRI_APEX_R,0},{Alpha92GmtMaster.TRI_BASE_R,Alpha92GmtMaster.TRI_HALF_BASE},{Alpha92GmtMaster.TRI_BASE_R,-Alpha92GmtMaster.TRI_HALF_BASE}};
            double d=Double.POSITIVE_INFINITY;boolean inside=true;
            for(int i=0;i<3;i++){double[] p=P[i],q=P[(i+1)%3];double ex=q[0]-p[0],ey=q[1]-p[1];
                double u=Math.max(0,Math.min(1,((r-p[0])*ex+(t-p[1])*ey)/(ex*ex+ey*ey)));
                d=Math.min(d,Math.hypot(r-p[0]-u*ex,t-p[1]-u*ey));
                double cr=ex*(t-p[1])-ey*(r-p[0]),cr3=ex*(P[(i+2)%3][1]-p[1])-ey*(P[(i+2)%3][0]-p[0]);
                if(cr*cr3<0)inside=false;}
            return inside?-d:d;
        }
        if(hour==3||hour==6||hour==9){
            double dr=Math.abs(r-Alpha92GmtMaster.BATON_CENTER_R)-Alpha92GmtMaster.BATON_RADIAL_HALF,dt=Math.abs(t)-Alpha92GmtMaster.BATON_TANGENTIAL_HALF;
            return dr>0||dt>0?Math.hypot(Math.max(dr,0),Math.max(dt,0)):Math.max(dr,dt);
        }
        return Math.hypot(r-Alpha92GmtMaster.ROUND_CENTER_R,t)-Alpha92GmtMaster.ROUND_OUTER_R;
    }

    private static double[] project(double[] H,double x,double y){
        double q=H[6]*x+H[7]*y+H[8];if(!Double.isFinite(q)||Math.abs(q)<1e-12)return null;
        return new double[]{(H[0]*x+H[1]*y+H[2])/q,(H[3]*x+H[4]*y+H[5])/q};
    }
    static double pxPerR(double[] H){
        if(H==null||H.length<9)return Double.NaN;
        double z0=H[8],z1=H[6]+H[8];if(Math.abs(z0)<1e-12||Math.abs(z1)<1e-12)return Double.NaN;
        double x0=H[2]/z0,y0=H[5]/z0,x1=(H[0]+H[2])/z1,y1=(H[3]+H[5])/z1;
        return Math.hypot(x1-x0,y1-y0);
    }
    private static double median(double[] a){Arrays.sort(a);int n=a.length;return n==0?Double.NaN:n%2==1?a[n/2]:0.5*(a[n/2-1]+a[n/2]);}
}
