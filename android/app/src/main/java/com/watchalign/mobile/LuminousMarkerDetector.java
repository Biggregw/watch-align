package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

/** Opt-in experimental local, seed-connected inner boundary detection on the frozen pose.
 * No fitted metal boundary, padding, template inset, or reference size enters the segmentation.
 * A closed dark separator, local contrast and three-level boundary stability are required.
 */
final class LuminousMarkerDetector {
    static final class Detection {
        final int hour; double[][] polygon; String reason="not assessed";
        double boundarySpreadPx=Double.NaN,rotationSpreadDeg=Double.NaN,contrast=Double.NaN,edgeCoverage=Double.NaN;
        Detection(int h){hour=h;} boolean usable(){return polygon!=null&&"measured".equals(reason);}
    }
    static Map<Integer,Detection> detect(Bitmap photo,Alpha99Pipeline.Output outer){
        Map<Integer,Detection> results=new LinkedHashMap<>();Mat rgba=new Mat(),gray=new Mat();
        Utils.bitmapToMat(photo,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
        try{for(ModelSpec.Marker m:outer.model.markers){Detection d;
            Alpha99MarkerInterference.Check check=outer.checks==null?null:outer.checks.get(m.hour);
            if(check==null||!check.clean){d=new Detection(m.hour);d.reason="hand, reflection or interference not cleared";}
            else if(outer.measurement.ring==null||!outer.measurement.ring.usable){d=new Detection(m.hour);d.reason="marker-ring placement unavailable";}
            else d=detectOne(gray,outer.pose.homography,m,outer.measurement.ring.model,outer.measurement.dialRadiusPx);
            results.put(m.hour,d);
        }}finally{rgba.release();gray.release();}return results;
    }
    static Detection detectOne(Mat gray,double[] H,ModelSpec.Marker m,double[] ring,double R){
        Detection d=new Detection(m.hour);double[] center=ExpectedMarkerOutline.moved(new double[][]{m.masterPoint()},ring)[0];
        double half=Math.max(m.halfWidth(),(m.outerEdgeR()-m.innerR())*.65)+.025;
        int n=Math.max(24,Math.min(240,(int)Math.ceil(2*half*R)));double step=2*half/n;
        Mat T=new Mat(3,3,CvType.CV_64F),hm=new Mat(3,3,CvType.CV_64F),M=new Mat(),empty=new Mat(),crop=new Mat();
        T.put(0,0,step,0,center[0]-half+.5*step,0,step,center[1]-half+.5*step,0,0,1);hm.put(0,0,H);Core.gemm(hm,T,1,empty,0,M);
        try{
            Imgproc.warpPerspective(gray,crop,M,new Size(n,n),Imgproc.INTER_LINEAR|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0));
            byte[] pixels=new byte[n*n];crop.get(0,0,pixels);int seed=n/2;List<Double> central=new ArrayList<>();
            for(int y=seed-2;y<=seed+2;y++)for(int x=seed-2;x<=seed+2;x++)central.add((double)(pixels[y*n+x]&255));Collections.sort(central);double face=central.get(central.size()/2);
            int[] sorted=new int[pixels.length];for(int i=0;i<sorted.length;i++)sorted[i]=pixels[i]&255;Arrays.sort(sorted);double background=sorted[sorted.length/5];d.contrast=face-background;
            if(d.contrast<35){d.reason="inner material has insufficient local contrast";return d;}
            List<double[][]> candidates=new ArrayList<>();double mid=background+.57*d.contrast;
            for(double fraction:new double[]{-.08,0,.08}){
                double[][] p=component(pixels,n,seed,mid+fraction*d.contrast);
                if(p==null){d.reason="no isolated, closed luminous component at all local contrast levels";return d;}
                candidates.add(p);
            }
            double[][] selected=candidates.get(1);double area=MarkerPolygonMismatch.area(selected),outerArea=MarkerPolygonMismatch.area(m.shape==ModelSpec.Shape.ROUND?MarkerPolygonMismatch.circle(0,0,m.outerR,64):m.shape==ModelSpec.Shape.BATON?m.batonPolygon():m.trianglePolygon());
            double ratio=area*step*step/outerArea;
            if(ratio<.12||ratio>.88){d.reason="component cannot be separated confidently from metal or printing";return d;}
            double[] c=LuminousGeometry.centroid(selected),bounds=LuminousGeometry.extent(selected,0);if(Math.min(bounds[0],bounds[1])*step*R<8){d.reason="inner boundary is too small in the source photograph";return d;}
            double spread=0;int supported=0;
            for(int k=0;k<64;k++){
                double a=k*2*Math.PI/64,r=LuminousGeometry.radius(selected,c,a);
                for(double[][] other:candidates)spread=Math.max(spread,Math.abs(r-LuminousGeometry.radius(other,c,a)));
                // Require the dark transition outside the actual contour, not a bright polished face.
                double inner=sample(pixels,n,c[0]+(r-1)*Math.cos(a),c[1]+(r-1)*Math.sin(a));
                double outer=sample(pixels,n,c[0]+(r+1.5)*Math.cos(a),c[1]+(r+1.5)*Math.sin(a));
                if(inner-outer>=12)supported++;
            }
            d.boundarySpreadPx=spread*step*R;d.edgeCoverage=supported/64.0;
            double minAngle=0,maxAngle=0;for(double[][] p:candidates){double diff=LuminousGeometry.angleDifference(LuminousGeometry.angle(p),LuminousGeometry.angle(selected));minAngle=Math.min(minAngle,diff);maxAngle=Math.max(maxAngle,diff);}d.rotationSpreadDeg=maxAngle-minAngle;
            if(!Double.isFinite(spread)||d.boundarySpreadPx>1.25||d.edgeCoverage<.80){d.reason="inner edge is ambiguous across local contrast levels or has an incomplete dark separator";return d;}
            double[][] canonical=new double[selected.length][];for(int i=0;i<selected.length;i++)canonical[i]=new double[]{center[0]-half+(selected[i][0]+.5)*step,center[1]-half+(selected[i][1]+.5)*step};
            d.polygon=canonical;d.reason="measured";return d;
        }finally{T.release();hm.release();M.release();empty.release();crop.release();}
    }
    /** Seed-connected mask only: nearby print/metal islands never get combined with the luminous face. */
    static double[][] component(byte[] p,int n,int seed,double threshold){
        int start=seed*n+seed;if((p[start]&255)<threshold)return null;
        boolean[] seen=new boolean[p.length];int[] queue=new int[p.length];int head=0,tail=0;queue[tail++]=start;seen[start]=true;List<double[]> edge=new ArrayList<>();
        while(head<tail){int i=queue[head++],x=i%n,y=i/n;if(x==0||y==0||x==n-1||y==n-1)return null;
            for(int j:new int[]{i-1,i+1,i-n,i+n}){if((p[j]&255)<threshold){double f=(threshold-(p[i]&255))/((p[j]&255)-(p[i]&255));edge.add(new double[]{x+f*(j%n-x),y+f*(j/n-y)});continue;}if(!seen[j]){seen[j]=true;queue[tail++]=j;}}
            
        }
        double[][] hull=LuminousGeometry.hull(edge.toArray(new double[0][]));
        // A separate background flood detects even small enclosed holes. Never fill them into an area score.
        boolean[] outside=new boolean[p.length];int[] background=new int[p.length];int bh=0,bt=0;
        background[bt++]=0;outside[0]=true;
        while(bh<bt){int i=background[bh++],x=i%n,y=i/n;
            for(int j:new int[]{x>0?i-1:-1,x<n-1?i+1:-1,y>0?i-n:-1,y<n-1?i+n:-1})
                if(j>=0&&!seen[j]&&!outside[j]){outside[j]=true;background[bt++]=j;}
        }
        for(int i=0;i<p.length;i++)if(!seen[i]&&!outside[i])return null;
        // A convex footprint is supported only when observed contour points lie within local pixel uncertainty.
        // Deep notches/hand cuts are withheld, never silently replaced by a filled convex hull.
        if(hull==null||tail<20||MarkerPolygonMismatch.area(hull)/tail>1.06)return null;
        for(double[] point:edge){double nearest=Double.POSITIVE_INFINITY;
            for(int k=0;k<hull.length;k++){double[] a=hull[k],b=hull[(k+1)%hull.length];double vx=b[0]-a[0],vy=b[1]-a[1];
                double t=Math.max(0,Math.min(1,((point[0]-a[0])*vx+(point[1]-a[1])*vy)/(vx*vx+vy*vy)));
                nearest=Math.min(nearest,Math.hypot(point[0]-a[0]-t*vx,point[1]-a[1]-t*vy));}
            if(nearest>1.25)return null;
        }
        return hull;
    }
    static double sample(byte[] p,int n,double x,double y){int ix=(int)Math.round(x),iy=(int)Math.round(y);return ix<0||iy<0||ix>=n||iy>=n?0:p[iy*n+ix]&255;}
}
