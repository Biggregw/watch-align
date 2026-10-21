package com.watchalign.mobile;

import java.util.Locale;

/**
 * 1.6.6 (exit 1 on failure): the one-side refit beside a confirmed hand on synthetic batons (the pictures of
 * secondsBeside, drawn here the same way),
 * with the independent luminous-centre cross-check, which needs OpenCV and so runs here rather than in the unit tests:
 *   honest    the hand beside the right edge, the baton in place or moved 1.2 % R: the refit and the lume agree
 *   trap      plus a misleading reflection evenly along the left edge: the refit moves, the lume does not
 */
public class RefitTraps {
    public static void main(String[] a){
        nu.pattern.OpenCV.loadLocally();
        double[][] gen=rect(0,0.75,0.025,0.08);
        double tol=100*MetalFit.ALT_LUME_AGREE_R;int bad=0;
        for(double dx:new double[]{0,0.006,0.012}){
            for(boolean trap:new boolean[]{false,true}){
                Pic p=secondsBeside(dx,2.5);
                if(trap){double xl=-0.025+dx-2.0/RPX-0.002;
                    p.add(new double[][]{{xl-0.0025,0.66},{xl+0.0025,0.66},{xl+0.0025,0.84},{xl-0.0025,0.84}},200);}
                ClearlyOff.Img img=p.img();
                MetalFit.Fit f=MetalFit.fit(img,gen,ModelSpec.Shape.BATON,RPX,null,0,detected(dx,2.5),true);
                ClearlyOff.Centroid lc=ClearlyOff.centroidFit(img,new ClearlyOff.Input(6,ModelSpec.Shape.BATON,gen,false,"hand",detected(dx,2.5)),0.008,RPX);
                double diff=f.alt==null||!"measured".equals(lc.status)?Double.NaN:100*ClearlyOff.lumeAcross(lc.d[0]-(f.alt.cObs[0]-f.alt.cGen[0]),lc.d[1]-(f.alt.cObs[1]-f.alt.cGen[1]),gen,f.altSide);
                double err=f.alt==null?Double.NaN:100*Math.abs(f.alt.cObs[0]-dx);
                boolean accepted=diff<=tol;
                // honest: accepted and accurate; trap: if the refit is off, the lume must refuse it
                boolean ok=!Double.isFinite(tol)||(trap?(!accepted||err<0.1):(accepted&&err<0.1));   // no tolerance yet: report only
                if(!ok)bad++;
                System.out.printf(Locale.US,"REFITTRAP %s dx %.3f: refit %s err %.3f%% R, lume diff %.3f%% R (tolerance %.3f) -> %s %s%n",trap?"trap  ":"honest",dx,
                        f.alt==null?"none":"side "+f.altSide,err,diff,tol,accepted?"accepted":"refused",ok?"OK":"FAIL");
            }
        }
        System.out.println("REFITTRAPS "+(bad==0?"all as expected":bad+" FAILED"));
        if(bad>0)System.exit(1);
    }

    // the synthetic dial of MetalFitTest (pure Java): dark dial 15, metal rim 180 (0.008 R) around lume 230, 4x4 supersampled
    static final double RPX=400,S=900;
    static final double[] H={RPX,0,S/2, 0,RPX,S/2, 0,0,1};
    static double[][] rect(double cx,double cy,double hw,double hh){return new double[][]{{cx-hw,cy-hh},{cx+hw,cy-hh},{cx+hw,cy+hh},{cx-hw,cy+hh}};}
    static double[][] moved(double[][] p,double tx){double[][] o=new double[p.length][];for(int i=0;i<p.length;i++)o[i]=new double[]{p[i][0]+tx,p[i][1]};return o;}
    static final class Pic {
        final int w=(int)S,h=(int)S;final float[] px=new float[w*h];final java.util.List<double[][]> polys=new java.util.ArrayList<>();final java.util.List<Double> vals=new java.util.ArrayList<>();
        Pic add(double[][] p,double v){polys.add(p);vals.add(v);return this;}
        ClearlyOff.Img img(){
            java.util.Arrays.fill(px,15f);
            double x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;
            for(double[][] p:polys)for(double[] q:p){x0=Math.min(x0,q[0]);y0=Math.min(y0,q[1]);x1=Math.max(x1,q[0]);y1=Math.max(y1,q[1]);}
            int i0=(int)Math.max(0,H[0]*x0+H[2]-4),i1=(int)Math.min(w-1,H[0]*x1+H[2]+4),j0=(int)Math.max(0,H[4]*y0+H[5]-4),j1=(int)Math.min(h-1,H[4]*y1+H[5]+4);
            for(int j=j0;j<=j1;j++)for(int i=i0;i<=i1;i++){double sum=0;
                for(int a=0;a<4;a++)for(int b=0;b<4;b++){double x=((i+(a+0.5)/4-0.5)-H[2])/H[0],y=((j+(b+0.5)/4-0.5)-H[5])/H[4];double v=15;
                    for(int k=0;k<polys.size();k++)if(inside(polys.get(k),x,y))v=vals.get(k);sum+=v;}
                px[j*w+i]=(float)(sum/16);}
            return new ClearlyOff.Img(px,w,h,H);
        }
    }
    static boolean inside(double[][] p,double x,double y){boolean in=false;
        for(int i=0,j=p.length-1;i<p.length;j=i++)if((p[i][1]>y)!=(p[j][1]>y)&&x<(p[j][0]-p[i][0])*(y-p[i][1])/(p[j][1]-p[i][1])+p[i][0])in=!in;return in;}
    static double[][] shrink(double[][] p,double d){double[] c=ClearlyOff.mean(p);double[][] o=new double[p.length][];
        for(int i=0;i<p.length;i++){double dx=p[i][0]-c[0],dy=p[i][1]-c[1],n=Math.hypot(dx,dy);o[i]=new double[]{c[0]+dx*(1-d/n),c[1]+dy*(1-d/n)};}return o;}
    static Pic secondsBeside(double dx,double gapPx){
        double xe=0.025+dx+gapPx/RPX+0.002,ang=Math.atan2(xe,0.83),w=0.0025;double[][] metal=moved(rect(0,0.75,0.025,0.08),dx);
        return new Pic().add(metal,180).add(shrink(metal,0.008),230)
                .add(new double[][]{{-w,0},{w,0},{xe+w,0.83},{xe-w,0.83}},120)
                .add(new double[][]{{xe-w,0.83},{xe+w,0.83},{Math.tan(ang)*0.97+w,0.97},{Math.tan(ang)*0.97-w,0.97}},120);
    }
    static double detected(double dx,double gapPx){return 180-Math.toDegrees(Math.atan2(0.025+dx+gapPx/RPX+0.002,0.83))+1.5;}
}
