package com.watchalign.mobile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Alpha91 full-resolution minute-lattice refinement.
 *
 * Port of android/research/alpha91_overlay_registration.py. The candidate's applied
 * hour markers, hands, centre, text, logo and date/cyclops geometry are never pose
 * inputs. The fit uses the exact 6-degree printed minute lattice only.
 *
 * Stage A measures a local bright-stroke centroid around each predicted minute tick.
 * Stage B measures only the tangential centreline and the inner-end edge, avoiding
 * the outer tick portion that can be hidden by the raised rehaut on oblique photos.
 * Both stages use equal angular-sector weighting and a Huber robust 8-parameter
 * homography update.
 */
final class Alpha91MinuteLatticeFitter {
    static final class Result {
        final boolean accepted;
        final Mat homography;
        final int ticksUsed;
        final int sectorsUsed;
        final double tickRmsPx;
        final double tickMedianPx;
        final double coarseToFinalRollDeg;
        final String reason;

        Result(String reason, Mat h) {
            accepted=false; homography=h; ticksUsed=sectorsUsed=0;
            tickRmsPx=tickMedianPx=coarseToFinalRollDeg=Double.NaN;
            this.reason=reason;
        }
        Result(Mat h,int ticks,int sectors,double rms,double median,double roll) {
            this(true,"",h,ticks,sectors,rms,median,roll);
        }
        Result(boolean ok,String reason,Mat h,int ticks,int sectors,double rms,double median,double roll) {
            accepted=ok; homography=h; ticksUsed=ticks; sectorsUsed=sectors;
            tickRmsPx=rms; tickMedianPx=median; coarseToFinalRollDeg=roll; this.reason=reason;
        }
    }

    /** Minute-track geometry of the model (ModelSpec pose block): inner / outer radius and minutes hidden by the date
     *  window or printing, which never pull the fit. */
    private static final class Track {
        final double RIN,ROUT;final int[] EXCLUDED;
        Track(ModelSpec m){RIN=m.minuteTrackInnerR;ROUT=m.minuteTrackOuterR;EXCLUDED=m.excludedMinutes.clone();}
        boolean excluded(int m){for(int q:EXCLUDED)if(q==m)return true;return false;}
    }

    /** Cubic B-spline sampler (research interpolant) over a crop around the coarse dial pose. */
    private static final class SampleImage {
        final Alpha91SplineImage img;
        SampleImage(Mat gray,Mat coarse) {
            if(gray==null||gray.empty()||gray.type()!=CvType.CV_8UC1)throw new IllegalArgumentException("8-bit gray expected");
            double minX=Double.POSITIVE_INFINITY,minY=minX,maxX=Double.NEGATIVE_INFINITY,maxY=maxX;
            for(int k=0;k<72;k++){double t=2*Math.PI*k/72;Point p=project(coarse,1.25*Math.cos(t),1.25*Math.sin(t));
                if(p==null)continue;minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
            if(!Double.isFinite(minX)){minX=0;minY=0;maxX=gray.cols();maxY=gray.rows();}
            img=new Alpha91SplineImage(gray,(int)Math.floor(minX)-8,(int)Math.floor(minY)-8,(int)Math.ceil(maxX)+9,(int)Math.ceil(maxY)+9);
        }
        double at(Mat H,double x,double y) {
            Point p=project(H,x,y);
            return p==null?Double.NaN:img.at(p.x,p.y);
        }
    }

    private static final class Obs {
        final int minute;
        final Point canonical, image;
        final double mass, spread, dr;
        Obs(int m,Point c,Point i,double mass,double spread,double dr){
            minute=m;canonical=c;image=i;this.mass=mass;this.spread=spread;this.dr=dr;
        }
    }

    private Alpha91MinuteLatticeFitter(){}

    static Result fit(Mat gray,Mat coarse,ModelSpec model) {
        Track tr=new Track(model);
        if(gray==null||gray.empty()||coarse==null||coarse.empty())
            return new Result("missing image or coarse pose",coarse==null?null:coarse.clone());
        Mat start=coarse.clone(),h=start.clone();
        try{
            SampleImage smp=new SampleImage(gray,start);
            for(int i=0;i<8;i++){
                List<Obs> q=centroidObservations(smp,h,tr);
                boolean[] ok=gateCentroid(q);
                if(count(ok)<16||sectorCount(q,ok)<8)return new Result("centroid lattice evidence insufficient",h.clone());
                Mat n=refine(h,q,ok,0.3*Math.sqrt(1.0/5.0));
                h.release();h=n;
            }

            List<Obs> last=null;boolean[] lastOk=null;
            for(int i=0;i<6;i++){
                List<Obs> q=innerObservations(smp,h,tr);
                boolean[] ok=gateInner(q);
                if(count(ok)<16||sectorCount(q,ok)<8)return new Result("inner-end lattice evidence insufficient",h.clone());
                Mat n=refine(h,q,ok,0.15);
                h.release();h=n;last=q;lastOk=ok;
            }
            if(last==null||lastOk==null)return new Result("inner-end lattice unavailable",h.clone());

            double[] err=new double[count(lastOk)];int k=0;
            for(int i=0;i<last.size();i++)if(lastOk[i]){
                Obs o=last.get(i);Point p=project(h,o.canonical.x,o.canonical.y);
                err[k++]=p==null?Double.NaN:Math.hypot(p.x-o.image.x,p.y-o.image.y);
            }
            double med=median(err);
            // Confidence-only robust RMS. H is already frozen above. A hand tip can cross one
            // minute stroke and create a several-pixel residual even when the remaining lattice
            // is locked. Set aside only gross isolated outliers (> max(1 px, 6x median)); fail
            // closed if more than 10% would need excluding.
            double grossThreshold=Math.max(1.0,6.0*med);
            int gross=0,finite=0;
            for(double v:err)if(Double.isFinite(v)){finite++;if(v>grossThreshold)gross++;}
            double grossFraction=finite==0?1.0:gross/(double)finite;
            double[] kept=new double[Math.max(0,finite-gross)];int kk=0;
            for(double v:err)if(Double.isFinite(v)&&v<=grossThreshold)kept[kk++]=v;
            double rms=rms(kept);
            int sectors=sectorCount(last,lastOk);
            double roll=wrap180(clock12(h)-clock12(start));
            // Residual gate in dial-radius units so it holds at any photo resolution (the app loads up
            // to 3200 px; tick residuals in pixels grow with image scale). At R=150 px these equal the
            // validated research limits (median 0.30 px, rms 0.60 px). Measured on the genuine/RL
            // fixtures at native and 2.5x scale: correct fits median 0.46-0.96e-3 R, rms 0.59-1.40e-3 R;
            // wrong local fits of the 6-degree lattice median 6.1-7.2e-3 R, rms 12-13e-3 R.
            Point c0=project(h,0,0),c1=project(h,1,0);
            double rpx=(c0==null||c1==null)?Double.NaN:Math.hypot(c1.x-c0.x,c1.y-c0.y);
            boolean medOk=Double.isFinite(med)&&Double.isFinite(rpx)&&med<=0.0020*rpx;
            boolean rmsOk=Double.isFinite(rms)&&Double.isFinite(rpx)&&rms<=0.0040*rpx;
            boolean accepted=sectors>=8&&grossFraction<=0.10&&medOk&&rmsOk&&Math.abs(roll)<2.0;
            if(!accepted)return new Result(false,
                    sectors<8?"too few minute sectors":
                            grossFraction>0.10?"too many contaminated minute ticks":
                            !medOk?"minute-lattice median residual too high (wrong local fit)":
                            !rmsOk?"minute-lattice residual too high":"minute lattice slipped phase",
                    h.clone(),count(lastOk)-gross,sectors,rms,med,roll);
            return new Result(h.clone(),count(lastOk)-gross,sectors,rms,med,roll);
        }catch(Throwable t){
            return new Result("minute-lattice refinement failed: "+t.getClass().getSimpleName(),h.clone());
        }finally{
            h.release();start.release();
        }
    }

    private static List<Obs> centroidObservations(SampleImage smp,Mat H,Track tr){
        final double RIN=tr.RIN,ROUT=tr.ROUT;
        List<Obs> out=new ArrayList<>();
        double rmid=(RIN+ROUT)*0.5;
        for(int m=0;m<60;m++){
            if(tr.excluded(m))continue;
            double a=Math.toRadians(m*6.0),erx=Math.sin(a),ery=-Math.cos(a),etx=Math.cos(a),ety=Math.sin(a);
            int nr=40,nt=51; double[][] I=new double[nr][nt]; double[] abs=new double[nr*nt]; int ai=0;
            double[] row=new double[nt];
            for(int ir=0;ir<nr;ir++){
                double rr=0.918+ir*0.002;
                for(int it=0;it<nt;it++){
                    double tt=Math.toRadians(-2.5+it*0.1)*rmid;
                    I[ir][it]=smp.at(H,rr*erx+tt*etx,rr*ery+tt*ety);
                    row[it]=I[ir][it];
                }
                double bg=medianFinite(row);
                for(int it=0;it<nt;it++)abs[ai++]=Double.isFinite(I[ir][it])?Math.abs(I[ir][it]-bg):Double.NaN;
            }
            double noise=1.4826*median(abs);
            double sw=0,sr=0,st=0,st2=0;
            for(int ir=0;ir<nr;ir++){
                double rr=0.918+ir*0.002;
                for(int z=0;z<nt;z++)row[z]=I[ir][z];
                double bg=medianFinite(row);
                for(int it=0;it<nt;it++){
                    double v=I[ir][it];if(!Double.isFinite(v))continue;
                    double w=Math.max(0,v-bg-2*noise);if(w<=0)continue;
                    double tt=Math.toRadians(-2.5+it*0.1)*rmid;
                    sw+=w;sr+=w*rr;st+=w*tt;st2+=w*tt*tt;
                }
            }
            if(!(sw>0))continue;
            double dr=sr/sw-rmid,dt=st/sw,sp=Math.sqrt(Math.max(0,st2/sw-dt*dt));
            Point canonical=new Point(rmid*erx,rmid*ery);
            Point observedCanonical=new Point(canonical.x+dr*erx+dt*etx,canonical.y+dr*ery+dt*ety);
            Point image=project(H,observedCanonical.x,observedCanonical.y);
            if(image!=null)out.add(new Obs(m,canonical,image,sw,sp,dr));
        }
        return out;
    }

    private static List<Obs> innerObservations(SampleImage smp,Mat H,Track tr){
        final double RIN=tr.RIN;
        List<Obs> out=new ArrayList<>();
        for(int m=0;m<60;m++){
            if(tr.excluded(m))continue;
            double a=Math.toRadians(m*6.0),erx=Math.sin(a),ery=-Math.cos(a),etx=Math.cos(a),ety=Math.sin(a);
            int nr=12,nt=81; double[][] I=new double[nr][nt]; double[] dev=new double[nr*nt]; double[] row=new double[nt];int di=0;
            for(int ir=0;ir<nr;ir++){
                double rr=RIN+0.004+ir*0.002;
                for(int it=0;it<nt;it++){
                    double tt=-0.04+it*0.001;
                    I[ir][it]=smp.at(H,rr*erx+tt*etx,rr*ery+tt*ety);row[it]=I[ir][it];
                }
                double bg=medianFinite(row);
                for(int it=0;it<nt;it++)dev[di++]=Double.isFinite(I[ir][it])?Math.abs(I[ir][it]-bg):Double.NaN;
            }
            double noise=1.4826*median(dev);
            double sw=0,st=0,st2=0;
            for(int ir=0;ir<nr;ir++){
                for(int z=0;z<nt;z++)row[z]=I[ir][z];
                double bg=medianFinite(row);
                for(int it=0;it<nt;it++){
                    double v=I[ir][it];if(!Double.isFinite(v))continue;
                    double w=Math.max(0,v-bg-2*noise);if(w<=0)continue;
                    double tt=-0.04+it*0.001;
                    sw+=w;st+=w*tt;st2+=w*tt*tt;
                }
            }
            if(!(sw>0))continue;
            double dt=st/sw,sp=Math.sqrt(Math.max(0,st2/sw-dt*dt));

            int nrr=91;double[] v=new double[nrr],grad=new double[nrr];
            for(int ir=0;ir<nrr;ir++){
                double rr=RIN-0.025+ir*0.0005,s=0;int n=0;
                for(int it=0;it<5;it++){
                    double tt=dt-0.004+it*0.002;
                    double z=smp.at(H,rr*erx+tt*etx,rr*ery+tt*ety);
                    if(Double.isFinite(z)){s+=z;n++;}
                }
                v[ir]=n==0?Double.NaN:s/n;
            }
            for(int i=1;i<nrr-1;i++)grad[i]=(Double.isFinite(v[i-1])&&Double.isFinite(v[i+1]))?(v[i+1]-v[i-1])*0.5:Double.NaN;
            int best=-1;double bv=-Double.MAX_VALUE;
            for(int i=1;i<nrr-1;i++)if(Double.isFinite(grad[i])&&grad[i]>bv){bv=grad[i];best=i;}
            if(best<=0||best>=nrr-1)continue;
            double den=grad[best-1]-2*grad[best]+grad[best+1];
            double off=Math.abs(den)>1e-9?0.5*(grad[best-1]-grad[best+1])/den:0;
            off=Math.max(-1.0,Math.min(1.0,off));
            double dr=(RIN-0.025+(best+off)*0.0005)-RIN;
            Point canonical=new Point(RIN*erx,RIN*ery);
            Point observedCanonical=new Point(canonical.x+dr*erx+dt*etx,canonical.y+dr*ery+dt*ety);
            Point image=project(H,observedCanonical.x,observedCanonical.y);
            if(image!=null)out.add(new Obs(m,canonical,image,sw/nr,sp,dr));
        }
        return out;
    }

    private static boolean[] gateCentroid(List<Obs> q){
        double[] mass=new double[q.size()],spread=new double[q.size()];
        for(int i=0;i<q.size();i++){mass[i]=q.get(i).mass;spread[i]=q.get(i).spread;}
        double mm=median(mass),ms=median(spread);boolean[] ok=new boolean[q.size()];
        for(int i=0;i<q.size();i++)ok[i]=mass[i]>.4*mm&&mass[i]<2.5*mm&&spread[i]<2*ms;
        return ok;
    }

    private static boolean[] gateInner(List<Obs> q){
        boolean[] ok=gateCentroid(q);
        for(int i=0;i<q.size();i++)ok[i]&=Math.abs(q.get(i).dr)<0.02;
        return ok;
    }

    private static Mat refine(Mat seed,List<Obs> q,boolean[] ok,double fScale){
        double[] p=normalised(seed);double[] sw=sectorWeights(q,ok);
        double best=objective(p,q,ok,sw,fScale);
        for(int iter=0;iter<18;iter++){
            double[][] A=new double[8][8];double[] b=new double[8];
            for(int i=0;i<q.size();i++){
                if(!ok[i]||sw[i]<=0)continue;
                Obs o=q.get(i);double x=o.canonical.x,y=o.canonical.y;
                double w=p[6]*x+p[7]*y+1.0;if(Math.abs(w)<1e-9)continue;
                double px=(p[0]*x+p[1]*y+p[2])/w,py=(p[3]*x+p[4]*y+p[5])/w;
                double[] jx={x/w,y/w,1/w,0,0,0,-px*x/w,-px*y/w};
                double[] jy={0,0,0,x/w,y/w,1/w,-py*x/w,-py*y/w};
                double rx=(px-o.image.x)*sw[i],ry=(py-o.image.y)*sw[i];
                accumulate(A,b,jx,rx,sw[i],huberWeight(rx,fScale));
                accumulate(A,b,jy,ry,sw[i],huberWeight(ry,fScale));
            }
            for(int d=0;d<8;d++)A[d][d]+=1e-7*Math.max(1.0,A[d][d]);
            double[] delta=solve8(A,b);if(delta==null)break;
            double dn=0;for(double z:delta)dn+=z*z;if(Math.sqrt(dn)<1e-9)break;
            boolean improved=false;
            for(double alpha:new double[]{1.0,.5,.25,.125,.0625}){
                double[] cand=p.clone();for(int j=0;j<8;j++)cand[j]-=alpha*delta[j];
                double score=objective(cand,q,ok,sw,fScale);
                if(Double.isFinite(score)&&score<best){p=cand;best=score;improved=true;break;}
            }
            if(!improved)break;
        }
        Mat H=new Mat(3,3,CvType.CV_64F);H.put(0,0,p[0],p[1],p[2],p[3],p[4],p[5],p[6],p[7],1.0);return H;
    }

    private static void accumulate(double[][] A,double[] b,double[] j,double r,double sector,double robust){
        double s=sector*Math.sqrt(Math.max(0,robust));
        for(int a=0;a<8;a++){
            double ja=j[a]*s;b[a]+=ja*(r*Math.sqrt(Math.max(0,robust)));
            for(int c=0;c<8;c++)A[a][c]+=ja*(j[c]*s);
        }
    }

    private static double objective(double[] p,List<Obs> q,boolean[] ok,double[] sw,double f){
        double s=0;
        for(int i=0;i<q.size();i++)if(ok[i]&&sw[i]>0){
            Obs o=q.get(i);Point z=o.canonical;double w=p[6]*z.x+p[7]*z.y+1;if(Math.abs(w)<1e-9)return Double.POSITIVE_INFINITY;
            double x=(p[0]*z.x+p[1]*z.y+p[2])/w,y=(p[3]*z.x+p[4]*z.y+p[5])/w;
            s+=huber((x-o.image.x)*sw[i],f)+huber((y-o.image.y)*sw[i],f);
        }
        return s;
    }
    private static double huber(double r,double f){double a=Math.abs(r);return a<=f?.5*r*r:f*(a-.5*f);}
    private static double huberWeight(double r,double f){double a=Math.abs(r);return a<=f||a<1e-12?1.0:f/a;}

    private static double[] sectorWeights(List<Obs> q,boolean[] ok){
        int[] n=new int[12];for(int i=0;i<q.size();i++)if(ok[i])n[q.get(i).minute/5]++;
        double[] w=new double[q.size()];for(int i=0;i<q.size();i++)if(ok[i]&&n[q.get(i).minute/5]>0)w[i]=Math.sqrt(1.0/n[q.get(i).minute/5]);
        return w;
    }

    private static double[] solve8(double[][] a,double[] b){
        Mat A=new Mat(8,8,CvType.CV_64F),B=new Mat(8,1,CvType.CV_64F),X=new Mat();
        double[] flat=new double[64];int k=0;for(int r=0;r<8;r++)for(int c=0;c<8;c++)flat[k++]=a[r][c];
        A.put(0,0,flat);B.put(0,0,b);
        try{
            if(!Core.solve(A,B,X,Core.DECOMP_SVD)||X.empty())return null;
            double[] x=new double[8];X.get(0,0,x);for(double v:x)if(!Double.isFinite(v))return null;return x;
        }finally{A.release();B.release();X.release();}
    }

    private static double[] normalised(Mat h){
        double[] m=new double[9];h.get(0,0,m);double s=m[8];if(!Double.isFinite(s)||Math.abs(s)<1e-12)s=1;
        return new double[]{m[0]/s,m[1]/s,m[2]/s,m[3]/s,m[4]/s,m[5]/s,m[6]/s,m[7]/s};
    }

    private static int count(boolean[] v){int n=0;for(boolean b:v)if(b)n++;return n;}
    private static int sectorCount(List<Obs> q,boolean[] ok){boolean[] s=new boolean[12];for(int i=0;i<q.size();i++)if(ok[i])s[q.get(i).minute/5]=true;int n=0;for(boolean b:s)if(b)n++;return n;}

    private static double clock12(Mat h){
        Point c=project(h,0,0),p=project(h,0,-1);if(c==null||p==null)return Double.NaN;
        return Math.toDegrees(Math.atan2(p.x-c.x,c.y-p.y));
    }
    static double clock12Deg(Mat h){return clock12(h);}
    private static double wrap180(double d){while(d>180)d-=360;while(d<=-180)d+=360;return d;}

    private static Point project(Mat h,double x,double y){
        if(h==null||h.empty())return null;double[] m=new double[9];h.get(0,0,m);double w=m[6]*x+m[7]*y+m[8];
        if(!Double.isFinite(w)||Math.abs(w)<1e-9)return null;
        double px=(m[0]*x+m[1]*y+m[2])/w,py=(m[3]*x+m[4]*y+m[5])/w;
        return Double.isFinite(px)&&Double.isFinite(py)?new Point(px,py):null;
    }

    private static double medianFinite(double[] a){return median(a);}
    private static double median(double[] a){
        double[] b=Arrays.stream(a).filter(Double::isFinite).toArray();if(b.length==0)return Double.NaN;Arrays.sort(b);
        return b.length%2==1?b[b.length/2]:.5*(b[b.length/2-1]+b[b.length/2]);
    }
    private static double rms(double[] a){double s=0;int n=0;for(double v:a)if(Double.isFinite(v)){s+=v*v;n++;}return n==0?Double.NaN:Math.sqrt(s/n);}
}
