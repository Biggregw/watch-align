package com.watchalign.mobile;

import android.graphics.PointF;

/** Maps photographed dial points back into the canonical unit dial using the four adjusted anchors. */
final class PerspectiveRectifier {
    private PerspectiveRectifier() {}

    static PointF toDial(PerspectiveMasterRenderer.Pose p, PointF imagePoint) {
        if (p == null || imagePoint == null || !p.anchorMode || !p.perspectiveMode) return null;
        double[] q=toDialRaw(p,imagePoint.x,imagePoint.y);
        return q==null?null:new PointF((float)q[0],(float)q[1]);
    }

    /** Pure numeric inverse of the same canonical-to-image homography used by PerspectiveMasterRenderer. */
    static double[] toDialRaw(PerspectiveMasterRenderer.Pose p,double u,double v){
        if(p==null||!p.anchorMode||!p.perspectiveMode)return null;
        double[] h=buildForwardH(p);if(h==null)return null;
        double a=h[0]-u*h[6], b=h[1]-u*h[7], c=u-h[2];
        double d=h[3]-v*h[6], e=h[4]-v*h[7], f=v-h[5];
        double det=a*e-b*d;if(Math.abs(det)<1e-12)return null;
        double x=(c*e-b*f)/det;
        double y=(a*f-c*d)/det;
        return new double[]{x,y};
    }

    /** Same forward four-anchor homography as the renderer, kept local so rectification is independently testable. */
    static double[] buildForwardH(PerspectiveMasterRenderer.Pose p){
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
            for(int r=0;r<8;r++){if(r==col)continue;double factor=a[r][col];for(int j=col;j<9;j++)a[r][j]-=factor*a[col][j];}
        }
        double[] h=new double[8];for(int i=0;i<8;i++)h[i]=a[i][8];return h;
    }

    static double[] projectRaw(PerspectiveMasterRenderer.Pose p,double x,double y){
        double[] h=buildForwardH(p);if(h==null)return null;double den=h[6]*x+h[7]*y+1.0;if(Math.abs(den)<1e-12)return null;
        return new double[]{(h[0]*x+h[1]*y+h[2])/den,(h[3]*x+h[4]*y+h[5])/den};
    }
}
