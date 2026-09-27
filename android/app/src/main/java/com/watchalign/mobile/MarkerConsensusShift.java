package com.watchalign.mobile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Measures where the round hour markers actually are relative to where the dial-edge
 * pose puts them, and returns the consensus image-space shift.
 *
 * Why this exists: the pose H0 is derived from the dial-edge ellipse, which assumes the
 * dial centre projects to the ellipse centre. That is only true for a distant camera.
 * In a close, tilted phone photo the true centre projects towards the far side of the
 * ellipse by roughly r^2 sin(tilt) / distance (4-5 px on a 200 px dial at ~13 deg),
 * and the raised markers add a little parallax the same way. The outline alone cannot
 * reveal this; the markers, which sit at known radii, can.
 *
 * Safeguards so a real defect is not fitted away:
 *  - translation only (no rotation or scale), from the median of all detected dots;
 *  - a dot whose offset disagrees with the consensus is excluded and stays misaligned;
 *  - at least 5 agreeing dots are required;
 *  - the shift is capped at what perspective can plausibly cause (MAX_SHIFT_R of the
 *    dial radius); a larger consensus offset is reported, not applied.
 *
 * Pure Java, so it can be unit tested without the native OpenCV library.
 */
final class MarkerConsensusShift {
    static final int[] ROUND_HOURS = {1,2,4,5,7,8,10,11};
    static final double MAX_SHIFT_R = 0.035;
    static final int MIN_DOTS = 5;

    static final class Result {
        final boolean applied;
        final double dx, dy;          // image px, add to predicted positions
        final int used, found;
        final double spreadPx;        // RMS disagreement of the used dots
        final String note;
        Result(boolean applied,double dx,double dy,int used,int found,double spread,String note){
            this.applied=applied;this.dx=dx;this.dy=dy;this.used=used;this.found=found;this.spreadPx=spread;this.note=note;
        }
    }

    private MarkerConsensusShift(){}

    /** Projects master coordinates (dial-radius units) through row-major 3x3 H. */
    static double[] project(double[] h,double x,double y){
        double w=h[6]*x+h[7]*y+h[8];
        if(Math.abs(w)<1e-12)w=1e-12;
        return new double[]{(h[0]*x+h[1]*y+h[2])/w,(h[3]*x+h[4]*y+h[5])/w};
    }

    static Result measure(DialEdgeEllipseFit.Intensity img,int w,int h,double[] H,double dialRpx){
        if(img==null||H==null||H.length<9||!(dialRpx>20))return new Result(false,0,0,0,0,Double.NaN,"unavailable");
        List<double[]> off=new ArrayList<>();
        for(int hour:ROUND_HOURS){
            double a=Gmt126710BlnrMaster.angleForHour(hour);
            double[] p=project(H,Gmt126710BlnrMaster.ROUND_CENTER_R*Math.cos(a),Gmt126710BlnrMaster.ROUND_CENTER_R*Math.sin(a));
            double[] c=blobCentroid(img,w,h,p[0],p[1],0.10*dialRpx);
            if(c!=null)c=blobCentroid(img,w,h,c[0],c[1],0.10*dialRpx);   // re-centre once
            if(c!=null)off.add(new double[]{c[0]-p[0],c[1]-p[1]});
        }
        int found=off.size();
        if(found<MIN_DOTS)return new Result(false,0,0,0,found,Double.NaN,"only "+found+" hour markers located");
        double mx=median(off,0),my=median(off,1);
        double tol=Math.max(1.5,0.02*dialRpx);
        double sx=0,sy=0;int n=0;
        for(double[] o:off)if(Math.hypot(o[0]-mx,o[1]-my)<=tol){sx+=o[0];sy+=o[1];n++;}
        if(n<MIN_DOTS)return new Result(false,0,0,n,found,Double.NaN,"hour markers disagree ("+n+"/"+found+" consistent)");
        double dx=sx/n,dy=sy/n,ss=0;
        for(double[] o:off)if(Math.hypot(o[0]-mx,o[1]-my)<=tol)ss+=(o[0]-dx)*(o[0]-dx)+(o[1]-dy)*(o[1]-dy);
        double spread=Math.sqrt(ss/n);
        double mag=Math.hypot(dx,dy);
        if(mag>MAX_SHIFT_R*dialRpx)
            return new Result(false,dx,dy,n,found,spread,String.format(java.util.Locale.US,
                    "markers sit %.1f px (%.1f%% of radius) from the dial-edge pose, more than perspective explains; not applied (possible off-centre dial or strong perspective)",
                    mag,100.0*mag/dialRpx));
        return new Result(true,dx,dy,n,found,spread,"applied");
    }

    /**
     * Centroid of the bright blob inside a disc: pixels above the half level between the
     * disc's dark background (20th percentile) and bright marker (95th percentile).
     */
    static double[] blobCentroid(DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double rad){
        int r=(int)Math.ceil(rad);
        if(cx-r<1||cy-r<1||cx+r>w-2||cy+r>h-2)return null;
        List<double[]> px=new ArrayList<>();
        for(int y=-r;y<=r;y++)for(int x=-r;x<=r;x++){
            if(x*x+y*y>rad*rad)continue;
            px.add(new double[]{cx+x,cy+y,img.at(cx+x,cy+y)});
        }
        double[] v=new double[px.size()];for(int i=0;i<v.length;i++)v[i]=px.get(i)[2];
        double[] s=v.clone();Arrays.sort(s);
        double bg=s[(int)(0.20*(s.length-1))],pk=s[(int)(0.95*(s.length-1))];
        if(pk-bg<40)return null;
        double level=0.5*(bg+pk);
        double wx=0,wy=0,wt=0;int above=0;
        for(double[] q:px)if(q[2]>=level){double wt1=q[2]-level;wx+=q[0]*wt1;wy+=q[1]*wt1;wt+=wt1;above++;}
        double frac=above/(double)px.size();
        if(wt<=0||frac<0.20||frac>0.90)return null;   // not a single marker-sized blob
        return new double[]{wx/wt,wy/wt};
    }


    /** Circle implied by the round markers: centre, dial radius and rotation (deg). */
    static final class Implied {
        final double cx,cy,r,rotDeg,rmsPx;final int used;
        Implied(double cx,double cy,double r,double rot,double rms,int used){this.cx=cx;this.cy=cy;this.r=r;this.rotDeg=rot;this.rmsPx=rms;this.used=used;}
    }

    /**
     * Fits obs_i = C + M v_i with M a rotation-scale (similarity) to the located round
     * markers, where v_i are their master positions. Hands crossing a marker are removed
     * as outliers. Returns null unless at least MIN_DOTS agree.
     */
    static Implied impliedCircle(DialEdgeEllipseFit.Intensity img,int w,int h,double cx,double cy,double r){
        if(img==null||!(r>20))return null;
        List<double[]> rows=new ArrayList<>();   // {vx,vy,ox,oy}
        for(int hour:ROUND_HOURS){
            double a=Gmt126710BlnrMaster.angleForHour(hour);
            double vx=Gmt126710BlnrMaster.ROUND_CENTER_R*Math.cos(a),vy=Gmt126710BlnrMaster.ROUND_CENTER_R*Math.sin(a);
            double[] c=blobCentroid(img,w,h,cx+r*vx,cy+r*vy,0.10*r);
            if(c!=null)c=blobCentroid(img,w,h,c[0],c[1],0.10*r);
            if(c!=null)rows.add(new double[]{vx,vy,c[0],c[1]});
        }
        boolean[] keep=new boolean[rows.size()];Arrays.fill(keep,true);
        double[] sol=null;double rms=Double.NaN;int used=0;
        for(int iter=0;iter<4;iter++){
            sol=solveSimilarity(rows,keep);
            if(sol==null)return null;
            double[] res=new double[rows.size()];
            for(int i=0;i<res.length;i++){double[] q=rows.get(i);
                double px=sol[0]+sol[2]*q[0]-sol[3]*q[1],py=sol[1]+sol[3]*q[0]+sol[2]*q[1];
                res[i]=Math.hypot(q[2]-px,q[3]-py);}
            double lim=Math.max(1.5,0.02*r);
            used=0;double ss=0;boolean changed=false;
            for(int i=0;i<res.length;i++){boolean k=res[i]<=lim;if(k!=keep[i])changed=true;keep[i]=k;if(k){used++;ss+=res[i]*res[i];}}
            if(used<MIN_DOTS)return null;
            rms=Math.sqrt(ss/used);
            if(!changed&&iter>0)break;
        }
        sol=solveSimilarity(rows,keep);
        if(sol==null)return null;
        return new Implied(sol[0],sol[1],Math.hypot(sol[2],sol[3]),Math.toDegrees(Math.atan2(sol[3],sol[2])),rms,used);
    }

    /** Least squares for {cx,cy,p,q} in ox = cx + p vx - q vy, oy = cy + q vx + p vy. */
    private static double[] solveSimilarity(List<double[]> rows,boolean[] keep){
        double[][] m=new double[4][5];int n=0;
        for(int i=0;i<rows.size();i++){if(!keep[i])continue;double[] q=rows.get(i);n++;
            double[][] eq={{1,0,q[0],-q[1],q[2]},{0,1,q[1],q[0],q[3]}};
            for(double[] e:eq)for(int a=0;a<4;a++){for(int b=0;b<4;b++)m[a][b]+=e[a]*e[b];m[a][4]+=e[a]*e[4];}
        }
        if(n<3)return null;
        for(int c=0;c<4;c++){int piv=c;for(int r=c+1;r<4;r++)if(Math.abs(m[r][c])>Math.abs(m[piv][c]))piv=r;
            if(Math.abs(m[piv][c])<1e-12)return null;double[] t=m[c];m[c]=m[piv];m[piv]=t;
            for(int r=0;r<4;r++){if(r==c)continue;double f=m[r][c]/m[c][c];for(int k=c;k<5;k++)m[r][k]-=f*m[c][k];}}
        return new double[]{m[0][4]/m[0][0],m[1][4]/m[1][1],m[2][4]/m[2][2],m[3][4]/m[3][3]};
    }

    private static double median(List<double[]> l,int k){
        double[] a=new double[l.size()];for(int i=0;i<a.length;i++)a[i]=l.get(i)[k];
        Arrays.sort(a);int n=a.length;return n%2==1?a[n/2]:0.5*(a[n/2-1]+a[n/2]);
    }
}
