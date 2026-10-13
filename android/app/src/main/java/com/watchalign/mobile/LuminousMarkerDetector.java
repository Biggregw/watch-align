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
        String method="none",failureCode="UNASSESSED";
        LuminousPartialFit.Fit alignment;
        double[][] circularEvidence,circularAlternatives;
        final List<String> attempts=new ArrayList<>();
        String diagnosticSummary(){Map<String,Integer> counts=new LinkedHashMap<>();for(String attempt:attempts){String code=attempt.split(":",2)[0];counts.put(code,counts.getOrDefault(code,0)+1);}return failureCode+"; attempted stages "+counts;}
        void fail(String code,String message){failureCode=code;reason=message;attempts.add(code+": "+message);}
        double boundarySpreadPx=Double.NaN,rotationSpreadDeg=Double.NaN,contrast=Double.NaN,edgeCoverage=Double.NaN;
        Detection(int h){hour=h;} boolean usable(){return polygon!=null&&"measured".equals(reason);}
    }
    static Map<Integer,Detection> detect(Bitmap photo,Alpha99Pipeline.Output outer){
        Map<Integer,Detection> results=new LinkedHashMap<>();Mat rgba=new Mat(),gray=new Mat();
        Utils.bitmapToMat(photo,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
        try{for(ModelSpec.Marker m:outer.model.markers){Detection d;
            Alpha99MarkerInterference.Check check=outer.checks==null?null:outer.checks.get(m.hour);
            if(check==null||(!check.clean&&!Alpha99MarkerInterference.HAND.equals(check.reason))){d=new Detection(m.hour);d.fail("INTERFERENCE",check==null?"interference evidence unavailable":"existing interference check: "+check.reason);}
            else if(outer.measurement.ring==null||!outer.measurement.ring.usable){d=new Detection(m.hour);d.fail("RING_UNAVAILABLE","marker-ring placement unavailable");}
            else d=detectOne(gray,outer.pose.homography,m,outer.measurement.ring.model,outer.measurement.dialRadiusPx,!check.clean&&Alpha99MarkerInterference.HAND.equals(check.reason));
            if(check!=null&&!check.clean&&Alpha99MarkerInterference.HAND.equals(check.reason))
                excludeHand(d,m,check,outer.pose.homography,outer.measurement.dialRadiusPx);
            results.put(m.hour,d);
        }}finally{rgba.release();gray.release();}return results;
    }
    /** Preserve provisional contour samples until both circular evidence sets have been masked.
     * Unmasked contrary hand observations must not erase a previously supported clean fit. */
    static void excludeHand(Detection d,ModelSpec.Marker m,Alpha99MarkerInterference.Check check,double[] H,double R){
        boolean observed=d.alignment!=null&&d.alignment.observed!=null,circular=m.shape==ModelSpec.Shape.ROUND&&d.circularEvidence!=null;
        if(observed)d.alignment=LuminousPartialFit.fit(withoutHand(d.alignment.observed,m,check,H),m.shape,m.hour,R);
        if(observed||circular){d.polygon=null;d.method="none";d.fail("INTERFERENCE","hand-contaminated samples excluded from partial fit; observed full area withheld");}
        if(circular){
            d.circularEvidence=withoutHand(d.circularEvidence,m,check,H);
            if(d.circularAlternatives!=null)d.circularAlternatives=withoutHand(d.circularAlternatives,m,check,H);
            LuminousCircularBoundary.validate(d,m.hour,R);
        }
    }
    /** Existing hand-grid exclusion, applied separately to each source of observed samples. */
    private static double[][] withoutHand(double[][] points,ModelSpec.Marker m,Alpha99MarkerInterference.Check check,double[] H){
        List<double[]> clean=new ArrayList<>();double a=Math.toRadians(m.hour*30),erx=Math.sin(a),ery=-Math.cos(a),etx=Math.cos(a),ety=Math.sin(a),rpx=Alpha99MarkerInterference.pxPerR(H),step=.75/rpx,r0=m.shape==ModelSpec.Shape.TRIANGLE?m.corridorStartR:m.innerR()-Alpha99MarkerInterference.CORRIDOR;
        if(check.grid!=null)for(double[] p:points){int row=(int)Math.round((p[0]*erx+p[1]*ery-r0)/step),col=(int)Math.round((p[0]*etx+p[1]*ety)/step+(check.cols-1)/2.0);boolean blocked=false;
            for(int dy=-2;dy<=2;dy++)for(int dx=-2;dx<=2;dx++){int y=row+dy,x=col+dx;if(y>=0&&y<check.rows&&x>=0&&x<check.cols){int value=check.grid[y*check.cols+x];if(value==2||value==5)blocked=true;}}
            if(!blocked)clean.add(p);
        }
        return clean.toArray(new double[0][]);
    }
    static Detection detectOne(Mat gray,double[] H,ModelSpec.Marker m,double[] ring,double R){
        return detectOne(gray,H,m,ring,R,false);
    }
    static Detection detectOne(Mat gray,double[] H,ModelSpec.Marker m,double[] ring,double R,boolean deferCircularValidation){
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
            if(d.contrast<35){d.fail("LOW_CONTRAST","inner material has insufficient local contrast");return d;}
            if(m.shape==ModelSpec.Shape.ROUND){
                LuminousCircularBoundary.Evidence evidence=LuminousCircularBoundary.evidence(pixels,n,seed);
                double[][] samples=evidence.primary;d.circularEvidence=new double[samples.length][];
                for(int i=0;i<samples.length;i++)d.circularEvidence[i]=new double[]{center[0]-half+(samples[i][0]+.5)*step,center[1]-half+(samples[i][1]+.5)*step};
                d.circularAlternatives=new double[evidence.alternatives.length][];
                for(int i=0;i<evidence.alternatives.length;i++)d.circularAlternatives[i]=new double[]{center[0]-half+(evidence.alternatives[i][0]+.5)*step,center[1]-half+(evidence.alternatives[i][1]+.5)*step};
            }
            LuminousSeparator.Result fallback=LuminousSeparator.trace(pixels,n,seed);
            if(fallback.visible!=null){double[][] points=new double[fallback.visible.length][];for(int i=0;i<points.length;i++)points[i]=new double[]{center[0]-half+(fallback.visible[i][0]+.5)*step,center[1]-half+(fallback.visible[i][1]+.5)*step};d.alignment=LuminousPartialFit.fit(points,m.shape,m.hour,R);}
            // A polished bevel can bridge the seed island at one cut. Search a bounded local
            // contrast ladder; accept only a complete stable triplet with the same separator checks.
            // Neither a reference score nor expected inner size selects the cut.
            for(double cut:new double[]{.57,.67,.77,.47,.37}){
            List<double[][]> candidates=new ArrayList<>();double[][] observed=null;double mid=background+cut*d.contrast;boolean isolated=true;
            for(double fraction:new double[]{-.08,0,.08}){
                Component result=componentResult(pixels,n,seed,mid+fraction*d.contrast);
                if(result.polygon==null){d.fail(result.code,String.format(Locale.US,"Local cut %.1f: %s",mid+fraction*d.contrast,result.reason));isolated=false;}
                candidates.add(result.polygon);if(fraction==0)observed=result.observed;
            }
            if(!isolated)continue;
            if(accept(d,candidates,m,step,R,center,half,pixels,n,"local_component",observed))return d;
            }
            if(!"NONE".equals(fallback.code)){d.fail(fallback.code,fallback.reason);return d;}
            if(accept(d,fallback.candidates,m,step,R,center,half,pixels,n,"separator",fallback.observed[1]))return d;
            return d;
        }finally{if(m.shape==ModelSpec.Shape.ROUND&&!deferCircularValidation)LuminousCircularBoundary.validate(d,m.hour,R);T.release();hm.release();M.release();empty.release();crop.release();}
    }
    private static boolean accept(Detection d,List<double[][]> candidates,ModelSpec.Marker m,double step,double R,double[] center,double half,byte[] pixels,int n,String method,double[][] observed){
            double[][] selected=candidates.get(1);double area=MarkerPolygonMismatch.area(selected),outerArea=MarkerPolygonMismatch.area(m.shape==ModelSpec.Shape.ROUND?MarkerPolygonMismatch.circle(0,0,m.outerR,64):m.shape==ModelSpec.Shape.BATON?m.batonPolygon():m.trianglePolygon());
            double ratio=area*step*step/outerArea;
            if(ratio<.12||ratio>.88){d.fail("THRESHOLD_LEAKAGE","bright component occupies too much or too little of the marker search region; metal/printing leakage possible");return false;}
            double[] c=LuminousGeometry.centroid(selected),bounds=LuminousGeometry.extent(selected,0);if(Math.min(bounds[0],bounds[1])*step*R<8){d.fail("SOURCE_TOO_SMALL","inner boundary is too small in the source photograph");return false;}
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
            if(!Double.isFinite(spread)||d.boundarySpreadPx>1.25||d.edgeCoverage<.80){d.fail(d.boundarySpreadPx>1.25?"UNSTABLE_CONTOUR":"INCOMPLETE_SEPARATOR",String.format(Locale.US,"contour movement %.2f source px; dark-separator coverage %.0f%%",d.boundarySpreadPx,100*d.edgeCoverage));return false;}
            double[][] canonical=new double[selected.length][];for(int i=0;i<selected.length;i++)canonical[i]=new double[]{center[0]-half+(selected[i][0]+.5)*step,center[1]-half+(selected[i][1]+.5)*step};
            // A footprint accepted by the unchanged full-boundary gates can supply its
            // actual contour points even where polished recovery is not visible. For a
            // withheld footprint, only individually evidenced separator points are used.
            if(observed!=null){double[][] points=new double[observed.length][];
                for(int i=0;i<points.length;i++)points[i]=new double[]{center[0]-half+(observed[i][0]+.5)*step,center[1]-half+(observed[i][1]+.5)*step};
                d.alignment=LuminousPartialFit.fit(points,m.shape,m.hour,R);
            }
            d.polygon=canonical;d.reason="measured";d.failureCode="NONE";d.method=method;return true;
    }
    /** Seed-connected mask only: nearby print/metal islands never get combined with the luminous face. */
    static final class Component {
        final double[][] polygon,observed;final String code,reason;
        Component(double[][] p,String c,String r){this(p,null,c,r);}
        Component(double[][] p,double[][] o,String c,String r){polygon=p;observed=o;code=c;reason=r;}
    }
    static double[][] component(byte[] p,int n,int seed,double threshold){return componentResult(p,n,seed,threshold).polygon;}
    static Component rejected(String code,String reason){return new Component(null,code,reason);}
    static Component componentResult(byte[] p,int n,int seed,double threshold){
        int start=seed*n+seed;if((p[start]&255)<threshold)return rejected("SEED_BELOW_CUT",String.format(Locale.US,"centre seed intensity %d is below cut %.1f",p[start]&255,threshold));
        boolean[] seen=new boolean[p.length];int[] queue=new int[p.length];int head=0,tail=0;queue[tail++]=start;seen[start]=true;List<double[]> edge=new ArrayList<>();
        while(head<tail){int i=queue[head++],x=i%n,y=i/n;if(x==0||y==0||x==n-1||y==n-1)return rejected("CROP_BOUNDARY","seed-connected bright component reaches crop boundary; threshold leakage is possible");
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
        int holes=0;for(int i=0;i<p.length;i++)if(!seen[i]&&!outside[i])holes++;
        if(holes>0)return rejected("ENCLOSED_HOLES","bright component encloses "+holes+" dark pixels; texture, reflections or actual missing material unresolved");
        // A convex footprint is supported only when observed contour points lie within local pixel uncertainty.
        // Deep notches/hand cuts are withheld, never silently replaced by a filled convex hull.
        if(hull==null||tail<20)return rejected("SMALL_COMPONENT","seed component has fewer than 20 pixels or no valid contour");
        if(MarkerPolygonMismatch.area(hull)/tail>1.06)return rejected("CONVEXITY_REJECTION","observed component has unsupported concavities; convex filling would invent area");
        for(double[] point:edge){double nearest=Double.POSITIVE_INFINITY;
            for(int k=0;k<hull.length;k++){double[] a=hull[k],b=hull[(k+1)%hull.length];double vx=b[0]-a[0],vy=b[1]-a[1];
                double t=Math.max(0,Math.min(1,((point[0]-a[0])*vx+(point[1]-a[1])*vy)/(vx*vx+vy*vy)));
                nearest=Math.min(nearest,Math.hypot(point[0]-a[0]-t*vx,point[1]-a[1]-t*vy));}
            if(nearest>1.25)return rejected("CONVEXITY_REJECTION",String.format(Locale.US,"observed contour departs %.2f crop px from convex boundary",nearest));
        }
        return new Component(hull,edge.toArray(new double[0][]),"NONE","measured");
    }
    static double sample(byte[] p,int n,double x,double y){int ix=(int)Math.round(x),iy=(int)Math.round(y);return ix<0||iy<0||ix>=n||iy>=n?0:p[iy*n+ix]&255;}
}
