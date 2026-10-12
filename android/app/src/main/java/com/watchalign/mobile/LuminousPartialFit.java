package com.watchalign.mobile;

import java.util.*;

/** Robust alignment from independently observed inner edges. Ideal intersections/circles supply fitted geometric area only, never observed footprint area. */
final class LuminousPartialFit {
    static final class Line {
        double nx,ny,c,angle,rms,span,angleSpan;double[] a,b;int count;List<double[]> inliers;
    }
    static final class Fit {
        boolean orientation;double angle=Double.NaN,rmsPx=Double.NaN,angleSpanDeg=Double.NaN;
        double[] center,tip;double radius=Double.NaN,width=Double.NaN,height=Double.NaN,symmetry=Double.NaN;
        double[][] ideal,observed;final List<double[][]> segments=new ArrayList<>();
        double centerSpanPx=Double.NaN,radiusSpanPx=Double.NaN;
        double[] crossNormal;double crossC=Double.NaN;
        String reason="insufficient distributed visible edges";
        boolean usable(){return orientation||center!=null||Double.isFinite(crossC);}
    }
    static Fit fit(double[][] points,ModelSpec.Shape shape,int hour,double R){
        Fit out=new Fit();if(points==null||points.length<12||!(R>0))return out;
        double a=Math.toRadians(hour*30),co=Math.cos(a),si=Math.sin(a);List<double[]> local=new ArrayList<>();
        for(double[] p:points)local.add(new double[]{R*(co*p[0]+si*p[1]),R*(-si*p[0]+co*p[1])});
        if(shape==ModelSpec.Shape.ROUND)round(local,out);else if(shape==ModelSpec.Shape.TRIANGLE)triangle(local,out);else baton(local,out);
        out.observed=MarkerPolygonMismatch.copyPolygon(points);
        if(out.center!=null)out.center=global(out.center,co,si,R);if(out.tip!=null)out.tip=global(out.tip,co,si,R);
        if(out.ideal!=null)for(int i=0;i<out.ideal.length;i++)out.ideal[i]=global(out.ideal[i],co,si,R);
        for(double[][] s:out.segments)for(int i=0;i<s.length;i++)s[i]=global(s[i],co,si,R);
        if(out.orientation)out.angle+=a;
        if(out.crossNormal!=null){double nx=out.crossNormal[0],ny=out.crossNormal[1];out.crossNormal=new double[]{co*nx-si*ny,si*nx+co*ny};out.crossC/=R;}
        if(Double.isFinite(out.radius))out.radius/=R;if(Double.isFinite(out.width))out.width/=R;if(Double.isFinite(out.height))out.height/=R;
        return out;
    }
    static double[] global(double[] p,double c,double s,double R){return new double[]{(c*p[0]-s*p[1])/R,(s*p[0]+c*p[1])/R};}
    static Line robustLine(List<double[]> p,double preferred){
        if(p.size()<8)return null;Random random=new Random(107);List<double[]> best=null;double bestScore=0;
        for(int trial=0;trial<600;trial++){
            double[] a=p.get(random.nextInt(p.size())),b=p.get(random.nextInt(p.size()));double dx=b[0]-a[0],dy=b[1]-a[1],length=Math.hypot(dx,dy);if(length<6)continue;
            double angle=Math.atan2(dy,dx);if(Math.abs(LuminousGeometry.angleDifference(angle,Math.toRadians(preferred)))>35)continue;
            double nx=-dy/length,ny=dx/length,c=nx*a[0]+ny*a[1];List<double[]> in=new ArrayList<>();double low=1e9,high=-1e9;
            for(double[] q:p)if(Math.abs(nx*q[0]+ny*q[1]-c)<=.8){in.add(q);double t=dx*q[0]/length+dy*q[1]/length;low=Math.min(low,t);high=Math.max(high,t);}
            boolean[] bins=new boolean[8];if(high>low)for(double[] q:in){double t=dx*q[0]/length+dy*q[1]/length;bins[Math.min(7,(int)(8*(t-low)/(high-low)))]=true;}int occupied=0;for(boolean present:bins)if(present)occupied++;
            double score=in.size()*Math.sqrt(Math.max(0,high-low));if(in.size()>=8&&high-low>=8&&occupied>=6&&score>bestScore){bestScore=score;best=in;}
        }
        if(best==null)return null;Line fit=tls(best);if(fit==null||fit.rms>.8||fit.span<8)return null;
        List<double[]> even=new ArrayList<>(),odd=new ArrayList<>();for(int i=0;i<best.size();i++)(i%2==0?even:odd).add(best.get(i));Line e=tls(even),o=tls(odd);
        fit.angleSpan=e==null||o==null?Double.NaN:Math.abs(LuminousGeometry.angleDifference(e.angle,o.angle));
        if(!Double.isFinite(fit.angleSpan)||fit.angleSpan>2)return null;return fit;
    }
    static Line tls(List<double[]> p){
        if(p.size()<4)return null;double x=0,y=0;for(double[] q:p){x+=q[0];y+=q[1];}x/=p.size();y/=p.size();double xx=0,yy=0,xy=0;
        for(double[] q:p){xx+=(q[0]-x)*(q[0]-x);yy+=(q[1]-y)*(q[1]-y);xy+=(q[0]-x)*(q[1]-y);}double angle=.5*Math.atan2(2*xy,xx-yy),dx=Math.cos(angle),dy=Math.sin(angle);
        Line f=new Line();f.angle=angle;f.nx=-dy;f.ny=dx;f.c=f.nx*x+f.ny*y;f.count=p.size();f.inliers=p;double lo=1e9,hi=-1e9,sum=0;
        for(double[] q:p){double t=dx*(q[0]-x)+dy*(q[1]-y);lo=Math.min(lo,t);hi=Math.max(hi,t);sum+=Math.pow(f.nx*q[0]+f.ny*q[1]-f.c,2);}
        f.rms=Math.sqrt(sum/p.size());f.span=hi-lo;f.a=new double[]{x+lo*dx,y+lo*dy};f.b=new double[]{x+hi*dx,y+hi*dy};return f;
    }
    static List<double[]> remove(List<double[]> p,Line f){List<double[]> out=new ArrayList<>();for(double[] q:p)if(Math.abs(f.nx*q[0]+f.ny*q[1]-f.c)>1.8)out.add(q);return out;}
    static double[] intersection(Line a,Line b){double den=a.nx*b.ny-a.ny*b.nx;if(Math.abs(den)<.15)return null;return new double[]{(a.c*b.ny-a.ny*b.c)/den,(a.nx*b.c-a.c*b.nx)/den};}
    static void add(Fit out,Line f){if(f==null)return;out.segments.add(new double[][]{f.a.clone(),f.b.clone()});out.rmsPx=Double.isNaN(out.rmsPx)?f.rms:Math.max(out.rmsPx,f.rms);out.angleSpanDeg=Double.isNaN(out.angleSpanDeg)?f.angleSpan:Math.max(out.angleSpanDeg,f.angleSpan);}
    static void triangle(List<double[]> p,Fit out){
        Line left=robustLine(p,60),right=robustLine(p,-60),base=robustLine(p,0);add(out,left);add(out,right);add(out,base);
        if(left==null||right==null){out.reason="triangle needs independently supported left and right straight sections";return;}
        double[] tip=intersection(left,right);if(tip==null){out.reason="triangle sides cannot determine a stable intersection";return;}if(Math.min(Math.hypot(tip[0]-left.a[0],tip[1]-left.a[1]),Math.hypot(tip[0]-left.b[0],tip[1]-left.b[1]))<=left.span&&Math.min(Math.hypot(tip[0]-right.a[0],tip[1]-right.a[1]),Math.hypot(tip[0]-right.b[0],tip[1]-right.b[1]))<=right.span)out.tip=tip;
        double lx=Math.abs(Math.cos(left.angle)),ly=Math.abs(Math.sin(left.angle)),rx=-Math.abs(Math.cos(right.angle)),ry=Math.abs(Math.sin(right.angle));out.angle=Math.atan2(ly+ry,lx+rx);out.orientation=true;
        out.reason="side direction supported; base/centre/size withheld unless the base is also visible";
        if(base==null||out.tip==null)return;double[] a=intersection(left,base),b=intersection(right,base);if(a==null||b==null)return;
        // Intersections must be bounded by the observed sections, not distant extrapolation.
        if(Math.hypot(tip[0]-a[0],tip[1]-a[1])>2*left.span||Math.hypot(tip[0]-b[0],tip[1]-b[1])>2*right.span)return;
        out.ideal=new double[][]{tip,a,b};out.center=LuminousGeometry.centroid(out.ideal);double mx=(a[0]+b[0])/2,my=(a[1]+b[1])/2;
        out.angle=Math.atan2(tip[1]-my,tip[0]-mx);out.width=Math.hypot(b[0]-a[0],b[1]-a[1]);out.height=Math.hypot(tip[0]-mx,tip[1]-my);
        out.symmetry=100*((tip[0]-mx)*(b[0]-a[0])+(tip[1]-my)*(b[1]-a[1]))/(out.width*out.width);
        out.reason="three straight sections support idealised triangle direction, centre, dimensions and apex symmetry; tip/corners are idealised intersections, not observed physical boundaries";
    }
    static void baton(List<double[]> p,Fit out){
        Line a=robustLine(p,90),b=a==null?null:robustLine(remove(p,a),90),top=robustLine(p,0),bottom=top==null?null:robustLine(remove(p,top),0);
        add(out,a);add(out,b);add(out,top);add(out,bottom);
        if(a==null){out.reason="no reliable long straight section";return;}
        out.orientation=true;out.angle=a.angle;out.reason="one long edge supports direction; position and dimensions withheld";
        if(b==null)return;double delta=LuminousGeometry.angleDifference(b.angle,a.angle);out.angle=a.angle+Math.toRadians(delta)/2;
        if(Math.abs(delta)>2){out.reason="opposing long-edge directions differ; fixed rectangle width/centre withheld";return;}
        double sign=a.nx*b.nx+a.ny*b.ny>=0?1:-1;out.crossNormal=new double[]{a.nx,a.ny};out.crossC=(a.c+sign*b.c)/2;out.width=Math.abs(a.c-sign*b.c);
        if(out.width<4){out.crossNormal=null;out.crossC=Double.NaN;out.width=Double.NaN;return;}
        out.reason="two long sections support direction, width and transverse centreline; length/along-axis centre withheld";
        if(top==null||bottom==null)return;double[] at=intersection(a,top),bt=intersection(b,top),bb=intersection(b,bottom),ab=intersection(a,bottom);if(at==null||bt==null||bb==null||ab==null)return;
        double height=(Math.hypot(at[0]-ab[0],at[1]-ab[1])+Math.hypot(bt[0]-bb[0],bt[1]-bb[1]))/2;
        if(height<4||height>2*Math.max(a.span,b.span))return;
        out.ideal=new double[][]{at,bt,bb,ab};out.center=LuminousGeometry.centroid(out.ideal);out.height=height;out.reason="four visible straight sections support idealised orientation, centre, width and length; corners are idealised; fitted geometric area is separate from observed footprint area";
    }
    static void round(List<double[]> p,Fit out){
        Random rng=new Random(108);List<double[]> best=null;double[] bestCircle=null;
        for(int trial=0;trial<800;trial++){
            double[] c=circle(p.get(rng.nextInt(p.size())),p.get(rng.nextInt(p.size())),p.get(rng.nextInt(p.size())));if(c==null||c[2]<4)continue;List<double[]> in=new ArrayList<>();for(double[] q:p)if(Math.abs(Math.hypot(q[0]-c[0],q[1]-c[1])-c[2])<=.8)in.add(q);
            if(in.size()>=16&&(best==null||in.size()>best.size())){best=in;bestCircle=c;}
        }
        if(best==null)return;double[] c=leastCircle(best);if(c==null)return;List<Double> angles=new ArrayList<>();Set<Integer> quadrants=new HashSet<>();double sum=0;
        for(double[] q:best){double a=Math.atan2(q[1]-c[1],q[0]-c[0]);if(a<0)a+=2*Math.PI;angles.add(a);quadrants.add((int)(a/(Math.PI/2)));sum+=Math.pow(Math.hypot(q[0]-c[0],q[1]-c[1])-c[2],2);}Collections.sort(angles);double gap=2*Math.PI+angles.get(0)-angles.get(angles.size()-1);for(int i=1;i<angles.size();i++)gap=Math.max(gap,angles.get(i)-angles.get(i-1));
        if(quadrants.size()<3||Math.toDegrees(gap)>150){out.reason="circle arcs insufficiently distributed; centre/radius not identifiable";return;}
        List<double[]> even=new ArrayList<>(),odd=new ArrayList<>();for(int i=0;i<best.size();i++)(i%2==0?even:odd).add(best.get(i));double[] e=leastCircle(even),o=leastCircle(odd);if(e==null||o==null)return;
        out.centerSpanPx=Math.hypot(e[0]-o[0],e[1]-o[1]);out.radiusSpanPx=Math.abs(e[2]-o[2]);out.rmsPx=Math.sqrt(sum/best.size());
        if(out.centerSpanPx>1.25||out.radiusSpanPx>1.25||out.rmsPx>.8){out.reason="circle fit unstable between visible arc subsets";return;}
        out.center=new double[]{c[0],c[1]};out.radius=c[2];out.ideal=MarkerPolygonMismatch.circle(c[0],c[1],c[2],96);out.reason="distributed visible arcs support circle centre/radius; rotation not applicable; fitted circle is not a full physical-footprint area";
    }
    static double[] circle(double[] a,double[] b,double[] c){double den=2*(a[0]*(b[1]-c[1])+b[0]*(c[1]-a[1])+c[0]*(a[1]-b[1]));if(Math.abs(den)<1e-8)return null;double aa=a[0]*a[0]+a[1]*a[1],bb=b[0]*b[0]+b[1]*b[1],cc=c[0]*c[0]+c[1]*c[1];double x=(aa*(b[1]-c[1])+bb*(c[1]-a[1])+cc*(a[1]-b[1]))/den,y=(aa*(c[0]-b[0])+bb*(a[0]-c[0])+cc*(b[0]-a[0]))/den;return new double[]{x,y,Math.hypot(a[0]-x,a[1]-y)};}
    static double[] leastCircle(List<double[]> p){if(p.size()<6)return null;double mx=0,my=0;for(double[] q:p){mx+=q[0];my+=q[1];}mx/=p.size();my/=p.size();double xx=0,xy=0,yy=0,xz=0,yz=0;for(double[] q:p){double x=q[0]-mx,y=q[1]-my,z=x*x+y*y;xx+=x*x;xy+=x*y;yy+=y*y;xz+=x*z;yz+=y*z;}double den=xx*yy-xy*xy;if(Math.abs(den)<1e-8)return null;double x=(xz*yy-yz*xy)/(2*den),y=(yz*xx-xz*xy)/(2*den),radius=0;for(double[] q:p)radius+=Math.hypot(q[0]-mx-x,q[1]-my-y);return new double[]{mx+x,my+y,radius/p.size()};}
}
