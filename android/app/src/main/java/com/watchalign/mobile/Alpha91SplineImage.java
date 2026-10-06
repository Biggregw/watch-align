package com.watchalign.mobile;

import org.opencv.core.Mat;

/**
 * Cubic B-spline interpolation of an 8-bit grey image over a crop (same interpolant as the
 * research pipeline's scipy map_coordinates(order=3, mode='mirror')). Used by the Alpha91 minute
 * lattice fitter and the Alpha94 marker measurement so Android reproduces the validated research
 * numbers; bilinear sampling of 8-bit data shifts sub-pixel edge positions by ~0.1-0.3 px.
 */
final class Alpha91SplineImage {
    private final float[] c;
    private final int x0,y0,w,h;

    /** Prefilters the crop [x0,x1) x [y0,y1) (clamped to the image). */
    Alpha91SplineImage(Mat gray,int cx0,int cy0,int cx1,int cy1){
        int W=gray.cols(),H=gray.rows();
        x0=Math.max(0,Math.min(W-4,cx0));y0=Math.max(0,Math.min(H-4,cy0));
        int x1=Math.max(x0+4,Math.min(W,cx1)),y1=Math.max(y0+4,Math.min(H,cy1));
        w=x1-x0;h=y1-y0;
        byte[] row=new byte[W];c=new float[w*h];
        for(int y=0;y<h;y++){gray.get(y0+y,0,row);for(int x=0;x<w;x++)c[y*w+x]=row[x0+x]&0xff;}
        double[] line=new double[Math.max(w,h)];
        for(int y=0;y<h;y++){for(int x=0;x<w;x++)line[x]=c[y*w+x];prefilter(line,w);for(int x=0;x<w;x++)c[y*w+x]=(float)line[x];}
        for(int x=0;x<w;x++){for(int y=0;y<h;y++)line[y]=c[y*w+x];prefilter(line,h);for(int y=0;y<h;y++)c[y*w+x]=(float)line[y];}
    }

    /** In-place cubic B-spline prefilter (single pole z=sqrt(3)-2, mirror boundary). */
    private static void prefilter(double[] s,int n){
        final double z=Math.sqrt(3.0)-2.0;
        for(int k=0;k<n;k++)s[k]*=6.0;
        double sum=s[0],zk=z;
        for(int k=1;k<n&&Math.abs(zk)>1e-12;k++){sum+=zk*s[k];zk*=z;}
        s[0]=sum;
        for(int k=1;k<n;k++)s[k]+=z*s[k-1];
        s[n-1]=(z/(z*z-1.0))*(s[n-1]+z*s[n-2]);
        for(int k=n-2;k>=0;k--)s[k]=z*(s[k+1]-s[k]);
    }

    /** Intensity at image pixel coordinates (pixel centres at integers); NaN outside the crop. */
    double at(double px,double py){
        double x=px-x0,y=py-y0;int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        if(ix<1||iy<1||ix+2>=w||iy+2>=h)return Double.NaN;
        double tx=x-ix,ty=y-iy,ux=1-tx,uy=1-ty;
        double tx2=tx*tx,tx3=tx2*tx,ty2=ty*ty,ty3=ty2*ty;
        double wx0=ux*ux*ux/6.0,wx1=(3*tx3-6*tx2+4)/6.0,wx2=(-3*tx3+3*tx2+3*tx+1)/6.0,wx3=tx3/6.0;
        double wy0=uy*uy*uy/6.0,wy1=(3*ty3-6*ty2+4)/6.0,wy2=(-3*ty3+3*ty2+3*ty+1)/6.0,wy3=ty3/6.0;
        int r=(iy-1)*w+ix-1;
        double a0=wx0*c[r]+wx1*c[r+1]+wx2*c[r+2]+wx3*c[r+3];r+=w;
        double a1=wx0*c[r]+wx1*c[r+1]+wx2*c[r+2]+wx3*c[r+3];r+=w;
        double a2=wx0*c[r]+wx1*c[r+1]+wx2*c[r+2]+wx3*c[r+3];r+=w;
        double a3=wx0*c[r]+wx1*c[r+1]+wx2*c[r+2]+wx3*c[r+3];
        return wy0*a0+wy1*a1+wy2*a2+wy3*a3;
    }
}
