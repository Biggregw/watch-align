package com.watchalign.mobile;

import android.graphics.PointF;

/** Maps photographed dial points back into the canonical unit dial using the four adjusted anchors. */
final class PerspectiveRectifier {
    private PerspectiveRectifier() {}

    static PointF toDial(PerspectiveMasterRenderer.Pose p, PointF imagePoint) {
        if (p == null || imagePoint == null || !p.anchorMode || !p.perspectiveMode) return null;
        double[] h = buildH(p);
        if (h == null) return null;
        double[] inv = invert3x3(new double[]{h[0],h[1],h[2], h[3],h[4],h[5], h[6],h[7],1.0});
        if (inv == null) return null;
        double x=imagePoint.x,y=imagePoint.y;
        double d=inv[6]*x+inv[7]*y+inv[8];
        if (Math.abs(d)<1e-10) return null;
        return new PointF((float)((inv[0]*x+inv[1]*y+inv[2])/d),(float)((inv[3]*x+inv[4]*y+inv[5])/d));
    }

    private static double[] buildH(PerspectiveMasterRenderer.Pose p){
        double[][] src={{0,-1},{1,0},{0,1},{-1,0}};
        double[][] dst={{p.anchor12X,p.anchor12Y},{p.anchor3X,p.anchor3Y},{p.anchor6X,p.anchor6Y},{p.anchor9X,p.anchor9Y}};
        double[][] a=new double[8][9];
        for(int i=0;i<4;i++){
            double x=src[i][0],y=src[i][1],u=dst[i][0],v=dst[i][1];int r=2*i;
            a[r][0]=x;a[r][1]=y;a[r][2]=1;a[r][6]=-u*x;a[r][7]=-u*y;a[r][8]=u;
            a[r+1][3]=x;a[r+1][4]=y;a[r+1][5]=1;a[r+1][6]=-v*x;a[r+1][7]=-v*y;a[r+1][8]=v;
        }
        for(int col=0;col<8;col++){
            int pivot=col;for(int r=col+1;r<8;r++)if(Math.abs(a[r][col])>Math.abs(a[pivot][col]))pivot=r;
            if(Math.abs(a[pivot][col])<1e-10)return null;
            double[] tmp=a[col];a[col]=a[pivot];a[pivot]=tmp;
            double div=a[col][col];for(int j=col;j<9;j++)a[col][j]/=div;
            for(int r=0;r<8;r++){if(r==col)continue;double f=a[r][col];for(int j=col;j<9;j++)a[r][j]-=f*a[col][j];}
        }
        double[] h=new double[8];for(int i=0;i<8;i++)h[i]=a[i][8];return h;
    }

    private static double[] invert3x3(double[] m){
        double a=m[0],b=m[1],c=m[2],d=m[3],e=m[4],f=m[5],g=m[6],h=m[7],i=m[8];
        double A=e*i-f*h,B=-(d*i-f*g),C=d*h-e*g,D=-(b*i-c*h),E=a*i-c*g,F=-(a*h-b*g),G=b*f-c*e,H=-(a*f-c*d),I=a*e-b*d;
        double det=a*A+b*B+c*C;if(Math.abs(det)<1e-12)return null;
        return new double[]{A/det,D/det,G/det,B/det,E/det,H/det,C/det,F/det,I/det};
    }
}
