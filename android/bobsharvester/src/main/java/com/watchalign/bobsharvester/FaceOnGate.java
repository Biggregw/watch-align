package com.watchalign.bobsharvester;

import java.util.Locale;

/**
 * Model-independent "reasonably face-on dial" check for a product photograph, on a grey-level image (no Android types,
 * so it is unit-tested on the desktop). It only judges photo suitability, never anything about the watch itself.
 *
 * Method: find the most circular strong edge near the image centre (coarse circle search), unwrap the image around
 * that centre into rays x radii, follow the strongest closed edge ring with dynamic programming (smooth from ray to
 * ray, so it follows an ellipse when the camera is tilted instead of jumping between bezel numerals), fit an ellipse to
 * the ring and report its minor/major axis ratio (1 = face-on; cos(tilt) for a tilted camera).
 *
 * v1.3 searched radii only up to 0.43 x the short side and refined each ray over 0.78-1.18 x that radius; on Bob's
 * close-up photos (watch filling the width) that reads bezel numerals and lugs, so 43 of 48 manually reviewed face-on
 * 124060 photos were rejected.
 */
public final class FaceOnGate {
    /** minimum ellipse axis ratio: cos(18 deg) ~ 0.951. Face-on Bob's photos read 0.982-0.995 (48 manually reviewed 124060s); simulated 30-45 deg tilts that lock onto a mixed ring read up to 0.943. */
    public static final double MIN_AXIS=0.95;
    public static final double MIN_SUPPORT=0.70, MAX_CENTRE_OFFSET=0.22, MAX_FIT_RMS=0.035;
    public static final int MIN_SIDE=500, MIN_RADIUS_PX=130, RAYS=180;

    public static final class Result {
        public boolean ok; public String reason="";
        public double radiusPx=Double.NaN, axis=Double.NaN, support=Double.NaN, centreOffset=Double.NaN, fitRms=Double.NaN, sharpness=Double.NaN;
        public String summary(){return String.format(Locale.US,"%s axis=%.3f support=%.2f rms=%.3f R=%.0fpx sharp=%.1f%s",ok?"ACCEPT":"REJECT",axis,support,fitRms,radiusPx,sharpness,reason.isEmpty()?"":" ("+reason+")");}
    }

    private FaceOnGate(){}

    /** @param g grey levels 0-255, row-major w x h, already downscaled (any size; ~850 px long side recommended)
     *  @param scaleToOriginal factor from this image's pixels to the original photo's pixels */
    public static Result assess(int[] g,int w,int h,double scaleToOriginal,int originalW,int originalH){
        Result z=new Result();
        if(Math.min(originalW,originalH)<MIN_SIDE){z.reason="image too small ("+originalW+"x"+originalH+")";return z;}
        int mn=Math.min(w,h);
        // 1. coarse circle: strongest consistent circular edge near the centre (no preference for size)
        int bx=w/2,by=h/2,br=0;double bs=-1;
        int sx=Math.max(4,w/40),sy=Math.max(4,h/40),r0=(int)(mn*.15),r1=(int)(mn*.49),rs=Math.max(3,mn/120);
        for(int cy=(int)(h*.30);cy<=(int)(h*.70);cy+=sy)for(int cx=(int)(w*.30);cx<=(int)(w*.70);cx+=sx)for(int r=r0;r<=r1;r+=rs){
            if(cx-r<3||cy-r<3||cx+r>=w-3||cy+r>=h-3)continue;
            double sum=0;int strong=0;
            for(int k=0;k<48;k++){double a=2*Math.PI*k/48;int d=grad(g,w,h,cx,cy,r,a,3);sum+=Math.min(80,d);if(d>=12)strong++;}
            double sup=strong/48.0,score=(sum/48)*sup;
            if(sup>=.5&&score>bs){bs=score;bx=cx;by=cy;br=r;}
        }
        if(br==0){z.reason="no circular dial/bezel edge found near the centre";return z;}
        // 2. polar unwrap and ring tracking around (bx,by): radii 0.55-1.30 x br within the frame
        int rmin=(int)(br*.55),rmax=(int)Math.min(br*1.30,Math.min(Math.min(bx,by),Math.min(w-1-bx,h-1-by))-3);
        if(rmax-rmin<8){z.reason="dial too close to the image edge";return z;}
        int nr=rmax-rmin+1,lap=RAYS+RAYS/4;
        int[][] gr=new int[RAYS][nr];
        for(int k=0;k<RAYS;k++){double a=2*Math.PI*k/RAYS;for(int j=0;j<nr;j++)gr[k][j]=Math.min(80,grad(g,w,h,bx,by,rmin+j,a,2));}
        int step=Math.max(2,(int)Math.ceil(br*2*Math.PI/RAYS*0.45));   // allows axis ratios down to ~0.6
        double[][] dp=new double[lap][nr];int[][] from=new int[lap][nr];
        for(int j=0;j<nr;j++)dp[0][j]=gr[0][j];
        for(int t=1;t<lap;t++){int k=t%RAYS;for(int j=0;j<nr;j++){double best=-1;int bj=j;for(int d=-step;d<=step;d++){int q=j+d;if(q<0||q>=nr)continue;double v=dp[t-1][q]-Math.abs(d)*1.5;if(v>best){best=v;bj=q;}}dp[t][j]=best+gr[k][j];from[t][j]=bj;}}
        int j=0;for(int q=1;q<nr;q++)if(dp[lap-1][q]>dp[lap-1][j])j=q;
        int[] path=new int[lap];for(int t=lap-1;t>=0;t--){path[t]=j;if(t>0)j=from[t][j];}
        // use the last full revolution (the start-up ray is free to settle)
        double[] xs=new double[RAYS],ys=new double[RAYS];int good=0;double rsum=0;
        for(int t=lap-RAYS;t<lap;t++){int k=t%RAYS;double a=2*Math.PI*k/RAYS,r=rmin+path[t];int i=t-(lap-RAYS);xs[i]=bx+r*Math.cos(a);ys[i]=by+r*Math.sin(a);rsum+=r;if(gr[k][path[t]]>=12)good++;}
        z.support=good/(double)RAYS;
        // 3. ellipse fit (conic through the ring points, centred for conditioning)
        double[] e=fitEllipse(xs,ys);
        if(e==null){z.reason="ring is not an ellipse";return z;}
        double ex=e[0],ey=e[1],ra=e[2],rb=e[3];
        z.axis=Math.min(ra,rb)/Math.max(ra,rb);
        double meanR=Math.sqrt(ra*rb);z.radiusPx=meanR*scaleToOriginal;
        z.centreOffset=Math.hypot(ex-w/2.0,ey-h/2.0)/mn;
        z.fitRms=e[5]/meanR;
        z.sharpness=sharp(g,w,h,(int)Math.round(ex),(int)Math.round(ey),(int)(meanR*.7));
        StringBuilder why=new StringBuilder();
        if(z.support<MIN_SUPPORT)why.append("edge ring too weak/broken (support ").append(f2(z.support)).append("); ");
        if(z.fitRms>MAX_FIT_RMS)why.append("edge ring not elliptical (rms ").append(f3(z.fitRms)).append("); ");
        if(z.axis<MIN_AXIS)why.append("not face-on: dial ellipse ratio ").append(f3(z.axis)).append(" < ").append(f3(MIN_AXIS)).append(" (tilt ~").append(Math.round(Math.toDegrees(Math.acos(Math.min(1,z.axis))))).append(" deg); ");
        if(z.centreOffset>MAX_CENTRE_OFFSET)why.append("dial not near the image centre; ");
        if(z.radiusPx<MIN_RADIUS_PX)why.append("dial too small (").append(Math.round(z.radiusPx)).append(" px); ");
        if(z.sharpness<5)why.append("dial blurred; ");
        z.reason=why.length()==0?"":why.substring(0,why.length()-2);z.ok=why.length()==0;return z;
    }

    static int grad(int[] g,int w,int h,double cx,double cy,double r,double a,int d){
        double c=Math.cos(a),s=Math.sin(a);
        int x1=cl((int)Math.round(cx+(r-d)*c),0,w-1),y1=cl((int)Math.round(cy+(r-d)*s),0,h-1),x2=cl((int)Math.round(cx+(r+d)*c),0,w-1),y2=cl((int)Math.round(cy+(r+d)*s),0,h-1);
        return Math.abs(g[y2*w+x2]-g[y1*w+x1]);
    }

    /** Least-squares conic A x^2 + B xy + C y^2 + D x + E y = 1 (points centred on their mean).
     *  @return {cx, cy, semi-axis a, semi-axis b, angle, rms radial residual} or null if not an ellipse */
    static double[] fitEllipse(double[] xs,double[] ys){
        int n=xs.length;double mx=0,my=0;for(int i=0;i<n;i++){mx+=xs[i];my+=ys[i];}mx/=n;my/=n;
        double[][] M=new double[5][6];
        for(int i=0;i<n;i++){double x=xs[i]-mx,y=ys[i]-my;double[] v={x*x,x*y,y*y,x,y};for(int p=0;p<5;p++){for(int q=0;q<5;q++)M[p][q]+=v[p]*v[q];M[p][5]+=v[p];}}
        double[] s=solve(M);if(s==null)return null;
        double A=s[0],B=s[1],C=s[2],D=s[3],E=s[4];
        double det=4*A*C-B*B;if(det<=0)return null;
        double x0=(B*E-2*C*D)/det,y0=(B*D-2*A*E)/det;
        double F=1+A*x0*x0+B*x0*y0+C*y0*y0;   // value at centre: A x0^2+... -1 = -F  => ellipse (x-x0)' Q (x-x0) = F
        double tr=A+C,dd=Math.sqrt(Math.max(0,(A-C)*(A-C)+B*B)),l1=(tr+dd)/2,l2=(tr-dd)/2;
        if(l1<=0||l2<=0||F<=0)return null;
        double a=Math.sqrt(F/l2),b=Math.sqrt(F/l1),ang=0.5*Math.atan2(B,A-C);
        // radial residual: distance from each point to the ellipse along the ray from its centre
        double ss=0;
        for(int i=0;i<n;i++){double x=xs[i]-mx-x0,y=ys[i]-my-y0,r=Math.hypot(x,y);if(r==0)continue;double ux=x/r,uy=y/r;double q=A*ux*ux+B*ux*uy+C*uy*uy;double re=Math.sqrt(F/q);ss+=(r-re)*(r-re);}
        return new double[]{x0+mx,y0+my,a,b,ang,Math.sqrt(ss/n)};
    }

    static double[] solve(double[][] M){int n=5;for(int c=0;c<n;c++){int p=c;for(int r=c+1;r<n;r++)if(Math.abs(M[r][c])>Math.abs(M[p][c]))p=r;if(Math.abs(M[p][c])<1e-12)return null;double[] t=M[c];M[c]=M[p];M[p]=t;for(int r=0;r<n;r++){if(r==c)continue;double f=M[r][c]/M[c][c];for(int k=c;k<=n;k++)M[r][k]-=f*M[c][k];}}double[] s=new double[n];for(int i=0;i<n;i++)s[i]=M[i][n]/M[i][i];return s;}

    static double sharp(int[] g,int w,int h,int cx,int cy,int r){long sum=0;int n=0,rr=r*r;for(int y=Math.max(2,cy-r);y<Math.min(h-2,cy+r);y+=3){int dy=y-cy;for(int x=Math.max(2,cx-r);x<Math.min(w-2,cx+r);x+=3){int dx=x-cx;if(dx*dx+dy*dy>rr)continue;int i=y*w+x,lap=Math.abs(4*g[i]-g[i-1]-g[i+1]-g[i-w]-g[i+w]);sum+=Math.min(255,lap);n++;}}return n==0?0:sum/(double)n;}
    static int cl(int x,int a,int b){return Math.max(a,Math.min(b,x));}
    static String f2(double x){return String.format(Locale.US,"%.2f",x);}
    static String f3(double x){return String.format(Locale.US,"%.3f",x);}
}
