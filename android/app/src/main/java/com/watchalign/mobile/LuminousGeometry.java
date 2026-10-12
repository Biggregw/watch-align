package com.watchalign.mobile;

import java.util.*;

/** Experimental inner geometry only; no outer reference or QC decisions. */
final class LuminousGeometry {
    static double[][] hull(double[][] points){
        if(points==null||points.length<3)return null;
        List<double[]> p=new ArrayList<>();for(double[] v:points)p.add(v.clone());
        p.sort((a,b)->a[0]==b[0]?Double.compare(a[1],b[1]):Double.compare(a[0],b[0]));
        List<double[]> h=new ArrayList<>();
        for(double[] v:p){while(h.size()>1&&cross(h.get(h.size()-2),h.get(h.size()-1),v)<=0)h.remove(h.size()-1);h.add(v);}
        int lower=h.size();for(int i=p.size()-2;i>=0;i--){double[] v=p.get(i);while(h.size()>lower&&cross(h.get(h.size()-2),h.get(h.size()-1),v)<=0)h.remove(h.size()-1);h.add(v);}
        if(h.size()>1)h.remove(h.size()-1);return h.toArray(new double[0][]);
    }
    static double cross(double[] a,double[] b,double[] c){return (b[0]-a[0])*(c[1]-a[1])-(b[1]-a[1])*(c[0]-a[0]);}
    static double[] centroid(double[][] p){
        double a=0,x=0,y=0;for(int i=0;i<p.length;i++){double[] u=p[i],v=p[(i+1)%p.length];double c=u[0]*v[1]-v[0]*u[1];a+=c;x+=(u[0]+v[0])*c;y+=(u[1]+v[1])*c;}
        return new double[]{x/(3*a),y/(3*a)};
    }
    /** Area moments, rather than a pixel-quantised bounding rectangle. */
    static double angle(double[][] p){
        double a=0,xx=0,yy=0,xy=0;double[] c=centroid(p);
        for(int i=0;i<p.length;i++){double[] u=p[i],v=p[(i+1)%p.length];double z=u[0]*v[1]-v[0]*u[1];a+=z;xx+=(u[0]*u[0]+u[0]*v[0]+v[0]*v[0])*z;yy+=(u[1]*u[1]+u[1]*v[1]+v[1]*v[1])*z;xy+=(2*u[0]*u[1]+u[0]*v[1]+v[0]*u[1]+2*v[0]*v[1])*z;}
        xx=xx/(6*a)-c[0]*c[0];yy=yy/(6*a)-c[1]*c[1];xy=xy/(12*a)-c[0]*c[1];return .5*Math.atan2(2*xy,xx-yy);
    }
    static double angleDifference(double a,double b){double d=Math.toDegrees(a-b);while(d>90)d-=180;while(d<=-90)d+=180;return d;}
    static double[][] local(double[][] p,ModelSpec.Marker m,double[] ring){
        double k=1+ring[2],s=ring[3],den=k*k+s*s,a=Math.toRadians(m.hour*30),co=Math.cos(a),si=Math.sin(a);double[] c=m.masterPoint();double[][] o=new double[p.length][];
        for(int i=0;i<p.length;i++){double x=p[i][0]-ring[0],y=p[i][1]-ring[1];double ux=(k*x+s*y)/den-c[0],uy=(-s*x+k*y)/den-c[1];o[i]=new double[]{co*ux+si*uy,-si*ux+co*uy};}return o;
    }
    static double[][] placed(double[][] p,ModelSpec.Marker m,double[] ring){
        double a=Math.toRadians(m.hour*30),c=Math.cos(a),s=Math.sin(a);double[] mc=m.masterPoint();double[][] o=new double[p.length][];
        for(int i=0;i<p.length;i++)o[i]=new double[]{mc[0]+c*p[i][0]-s*p[i][1],mc[1]+s*p[i][0]+c*p[i][1]};return ExpectedMarkerOutline.moved(o,ring);
    }
    static double[] extent(double[][] p,double angle){double c=Math.cos(angle),s=Math.sin(angle),loX=1e9,hiX=-1e9,loY=1e9,hiY=-1e9;for(double[] v:p){double x=c*v[0]+s*v[1],y=-s*v[0]+c*v[1];loX=Math.min(loX,x);hiX=Math.max(hiX,x);loY=Math.min(loY,y);hiY=Math.max(hiY,y);}return new double[]{hiX-loX,hiY-loY};}
    static double radius(double[][] p,double[] center,double angle){
        double dx=Math.cos(angle),dy=Math.sin(angle),best=Double.POSITIVE_INFINITY;
        for(int i=0;i<p.length;i++){double[] a=p[i],b=p[(i+1)%p.length];double ex=b[0]-a[0],ey=b[1]-a[1],den=dx*ey-dy*ex;if(Math.abs(den)<1e-12)continue;double ax=a[0]-center[0],ay=a[1]-center[1],t=(ax*ey-ay*ex)/den,u=(ax*dy-ay*dx)/den;if(t>=0&&u>=-1e-8&&u<=1+1e-8)best=Math.min(best,t);}return best;
    }
}
