package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/**
 * Evidence picture for one marker (1.6.0): the whole dial, upright, with the references the clearly-off check measures
 * against, as the owner judges by eye:
 *   blue lines   each hour tick of the printed minute track, through the centre, to the opposite hour tick
 *   blue rings   where the round markers' centres and the batons' outer ends should sit (this dial's own ring)
 *   circle       the marker: red = clearly off, green = no visible issue, grey = can't check
 *   red cross    the measured centre of a marker that is clearly off
 *   short arcs   12 triangle: the minute-track tick ends (blue) and its measured top edge, the gap between them
 * 1.6.3 (experimental, outer-metal fit): above the dial, the marker itself, zoomed and upright: the photo on the left,
 * the same view on the right with
 *   yellow       the genuine outline (where the marker should be)
 *   green        the fitted outer-metal outline where its edge was seen; orange where the edge seen there is
 *                inconsistent (shadowed or reflecting metal); dashed magenta where it is reconstructed (hidden)
 *   cyan / white the angular reference and the fitted direction: batons' long edges against the line from their hour
 *                tick to the opposite one, the triangle's top edge against the line through the inner ends of minute
 *                ticks 59 and 1; round markers: the hour line and the fitted centre
 * DISPLAY ONLY: nothing drawn here feeds the decision.
 */
final class ClearlyOffEvidence {
    static final int SIZE=720;
    static final double HALF=1.12;
    static final Scalar GUIDE=new Scalar(80,200,255,255),RED=new Scalar(255,59,48,255),GREEN=new Scalar(52,199,89,255),
            GREY=new Scalar(160,160,168,255),BLACK=new Scalar(0,0,0,255),AMBER=new Scalar(255,159,10,255),
            YELLOW=new Scalar(255,214,10,255),ORANGE=new Scalar(255,120,0,255),MAGENTA=new Scalar(255,64,255,255),WHITE=new Scalar(255,255,255,255);
    static final int PANEL=360;

    private ClearlyOffEvidence(){}

    static Bitmap render(Mat rgba,double[] H,ModelSpec model,ClearlyOff.Result r,AlignmentDecision.Marker am){
        Mat all=renderMat(rgba,H,model,r,am);
        Bitmap bm=Bitmap.createBitmap(all.cols(),all.rows(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(all,bm);all.release();return bm;
    }

    static Mat renderMat(Mat rgba,double[] H,ModelSpec model,ClearlyOff.Result r,AlignmentDecision.Marker am){
        Mat dial=dialMat(rgba,H,model,r,am);
        ClearlyOff.Marker t=am.track;
        if(t==null||t.yellow==null||t.yellow.length<3)return dial;
        Mat zoom=markerPanels(rgba,H,r,t,colour(am));
        Mat all=new Mat();java.util.List<Mat> parts=new java.util.ArrayList<>();parts.add(zoom);parts.add(dial);
        Core.vconcat(parts,all);zoom.release();dial.release();return all;
    }

    static Scalar colour(AlignmentDecision.Marker am){
        if(am.track!=null&&am.track.state==ClearlyOff.State.POSSIBLE)return AMBER;
        return am.outcome==AlignmentDecision.Outcome.DEFECT?RED:am.outcome==AlignmentDecision.Outcome.NO_DEFECT?GREEN:GREY;
    }

    /** the marker, zoomed and upright: the photo (left) and the evidence on it (right), each PANEL square */
    static Mat markerPanels(Mat rgba,double[] H,ClearlyOff.Result r,ClearlyOff.Marker t,Scalar col){
        double[] c=ClearlyOff.mean(t.yellow);double ext=0;for(double[] p:t.yellow)ext=Math.max(ext,Math.hypot(p[0]-c[0],p[1]-c[1]));
        double half=Math.max(0.05,1.8*ext);
        Mat plain=warp(rgba,H,c[0],c[1],half);Mat ann=plain.clone();
        double k=PANEL/(2*half);int th=2;
        java.util.function.Function<double[],Point> P=q->new Point((q[0]-(c[0]-half))*k-0.5,(q[1]-(c[1]-half))*k-0.5);
        poly(ann,t.yellow,P,YELLOW,1);
        if(t.fitPts!=null&&t.fitStatus!=null){
            double jump=6.0/r.rpx;
            for(int i=0;i+1<t.fitPts.length;i++){
                double[] a=t.fitPts[i],b=t.fitPts[i+1];if(Math.hypot(b[0]-a[0],b[1]-a[1])>jump)continue;
                byte s1=t.fitStatus[i],s2=t.fitStatus[i+1];
                if(s1==MetalFit.HIDDEN||s2==MetalFit.HIDDEN){if((i/6)%2==0)Imgproc.line(ann,P.apply(a),P.apply(b),MAGENTA,th,Imgproc.LINE_AA);}
                else Imgproc.line(ann,P.apply(a),P.apply(b),s1==MetalFit.INCONSISTENT||s2==MetalFit.INCONSISTENT?ORANGE:GREEN,th,Imgproc.LINE_AA);
            }
        }
        double[] co=t.cObs!=null?t.cObs:c;
        if(t.shape==ModelSpec.Shape.BATON&&t.fitPoly!=null){
            Double a1=r.lines.get(t.hour%12),a2=r.lines.get((t.hour+6)%12);
            double[] ax=axis(t.fitPoly);double L=0;for(double[] p:t.fitPoly)L=Math.max(L,Math.abs((p[0]-co[0])*ax[0]+(p[1]-co[1])*ax[1]));
            if(a1!=null&&a2!=null){double[] u=dir(ClearlyOff.cpt(0.93,a1),ClearlyOff.cpt(0.93,a2));
                Imgproc.line(ann,P.apply(new double[]{co[0]-1.5*L*u[0],co[1]-1.5*L*u[1]}),P.apply(new double[]{co[0]+1.5*L*u[0],co[1]+1.5*L*u[1]}),GUIDE,1,Imgproc.LINE_AA);}
            Imgproc.line(ann,P.apply(new double[]{co[0]-L*ax[0],co[1]-L*ax[1]}),P.apply(new double[]{co[0]+L*ax[0],co[1]+L*ax[1]}),WHITE,1,Imgproc.LINE_AA);
        }else if(t.shape==ModelSpec.Shape.TRIANGLE&&t.fitPoly!=null){
            double[][] e=topEdge(t.fitPoly,co);
            if(t.tick59!=null&&t.tick01!=null){double[] u=dir(t.tick59,t.tick01);double w=Math.hypot(e[1][0]-e[0][0],e[1][1]-e[0][1]);
                double[] m=t.tick59,n=t.tick01;
                Imgproc.line(ann,P.apply(new double[]{m[0]-0.3*w*u[0],m[1]-0.3*w*u[1]}),P.apply(new double[]{n[0]+0.3*w*u[0],n[1]+0.3*w*u[1]}),GUIDE,1,Imgproc.LINE_AA);
                Imgproc.circle(ann,P.apply(m),3,GUIDE,-1,Imgproc.LINE_AA);Imgproc.circle(ann,P.apply(n),3,GUIDE,-1,Imgproc.LINE_AA);}
            Imgproc.line(ann,P.apply(e[0]),P.apply(e[1]),WHITE,1,Imgproc.LINE_AA);
        }else{
            Double a=r.lines.get(t.hour%12);double rc=ClearlyOff.norm(c);
            if(a!=null)Imgproc.line(ann,P.apply(ClearlyOff.cpt(rc-1.6*ext,a)),P.apply(ClearlyOff.cpt(rc+1.6*ext,a)),GUIDE,1,Imgproc.LINE_AA);
        }
        Point q=P.apply(co);int u=6;
        Imgproc.line(ann,new Point(q.x-u,q.y),new Point(q.x+u,q.y),WHITE,1,Imgproc.LINE_AA);Imgproc.line(ann,new Point(q.x,q.y-u),new Point(q.x,q.y+u),WHITE,1,Imgproc.LINE_AA);
        label(ann,caption(t),col);
        label(ann,"yellow genuine  green seen  orange uncertain  magenta reconstructed",WHITE,PANEL-8);
        Mat out=new Mat();java.util.List<Mat> two=new java.util.ArrayList<>();two.add(plain);two.add(ann);Core.hconcat(two,out);plain.release();ann.release();
        return out;
    }

    static String caption(ClearlyOff.Marker t){
        String st=t.state==ClearlyOff.State.CLEARLY_OFF?"Clearly off":t.state==ClearlyOff.State.POSSIBLE?"Possible misalignment - inspect":
                t.state==ClearlyOff.State.NO_VISIBLE_ISSUE?"No visible issue":"Can't check";
        StringBuilder b=new StringBuilder(st);
        if(t.shape==ModelSpec.Shape.TRIANGLE){
            if(Double.isFinite(t.tiltDeg))b.append(String.format(java.util.Locale.US," | turn %+.2f/%.1f deg",t.tiltDeg,t.tiltLimitDeg));
            if(Double.isFinite(t.gapPct))b.append(String.format(java.util.Locale.US," | gap %.2f%%",t.gapPct));
        }else{
            if(Double.isFinite(t.posPct))b.append(String.format(java.util.Locale.US," | off %.2f/%.2f%%",t.posPct,t.posLimitPct));
            if(t.shape==ModelSpec.Shape.BATON&&Double.isFinite(t.tiltDeg))b.append(String.format(java.util.Locale.US," | tilt %+.2f/%.2f deg",t.tiltDeg,t.tiltLimitDeg));
        }
        return b.toString();
    }

    static void label(Mat m,String s,Scalar col){label(m,s,col,18);}
    static void label(Mat m,String s,Scalar col,int y){
        double fs=0.42;int[] base=new int[1];
        Size ts=Imgproc.getTextSize(s,Imgproc.FONT_HERSHEY_SIMPLEX,fs,1,base);
        if(ts.width>PANEL-8)fs*= (PANEL-8)/ts.width;
        Imgproc.putText(m,s,new Point(5,y),Imgproc.FONT_HERSHEY_SIMPLEX,fs,BLACK,3,Imgproc.LINE_AA);
        Imgproc.putText(m,s,new Point(5,y),Imgproc.FONT_HERSHEY_SIMPLEX,fs,col,1,Imgproc.LINE_AA);
    }

    static void poly(Mat m,double[][] pts,java.util.function.Function<double[],Point> P,Scalar col,int th){
        for(int i=0;i<pts.length;i++)Imgproc.line(m,P.apply(pts[i]),P.apply(pts[(i+1)%pts.length]),col,th,Imgproc.LINE_AA);
    }
    static double[] dir(double[] a,double[] b){double x=b[0]-a[0],y=b[1]-a[1],n=Math.hypot(x,y);return new double[]{x/n,y/n};}
    /** principal axis of a polygon's vertices */
    static double[] axis(double[][] p){
        double[] c=ClearlyOff.mean(p);double sxx=0,syy=0,sxy=0;
        for(double[] q:p){double x=q[0]-c[0],y=q[1]-c[1];sxx+=x*x;syy+=y*y;sxy+=x*y;}
        double a=0.5*Math.atan2(2*sxy,sxx-syy);return new double[]{Math.cos(a),Math.sin(a)};
    }
    /** the triangle's top edge: its two outermost corners (largest radius, extreme along the tangent) */
    static double[][] topEdge(double[][] poly,double[] c){
        double[] er={c[0]/ClearlyOff.norm(c),c[1]/ClearlyOff.norm(c)},et={-er[1],er[0]};
        double mx=Double.NEGATIVE_INFINITY;for(double[] p:poly)mx=Math.max(mx,p[0]*er[0]+p[1]*er[1]);
        double[] a=null,b=null;double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(double[] p:poly){if(p[0]*er[0]+p[1]*er[1]<mx-0.004)continue;double t=p[0]*et[0]+p[1]*et[1];if(t<lo){lo=t;a=p;}if(t>hi){hi=t;b=p;}}
        return new double[][]{a,b};
    }

    static Mat warp(Mat rgba,double[] H,double cx,double cy,double half){
        double s=2*half/PANEL;
        Mat T=new Mat(3,3,org.opencv.core.CvType.CV_64F);T.put(0,0,s,0,cx-half+0.5*s,0,s,cy-half+0.5*s,0,0,1);
        Mat Hm=new Mat(3,3,org.opencv.core.CvType.CV_64F);Hm.put(0,0,H);
        Mat M=new Mat(),empty=new Mat();Core.gemm(Hm,T,1,empty,0,M);Mat out=new Mat();
        Imgproc.warpPerspective(rgba,out,M,new Size(PANEL,PANEL),Imgproc.INTER_CUBIC|Imgproc.WARP_INVERSE_MAP,Core.BORDER_CONSTANT,new Scalar(0,0,0,255));
        T.release();Hm.release();M.release();empty.release();return out;
    }

    static Mat dialMat(Mat rgba,double[] H,ModelSpec model,ClearlyOff.Result r,AlignmentDecision.Marker am){
        Mat m=Alpha98Closeups.render(rgba,H,0,0,HALF);
        Mat big=new Mat();Imgproc.resize(m,big,new Size(SIZE,SIZE),0,0,Imgproc.INTER_CUBIC);m.release();
        int th=Math.max(1,SIZE/480);
        if(r!=null){
            Mat lay=big.clone();
            for(int h=0;h<6;h++){
                Double a=r.lines.get(h),b=r.lines.get(h+6);if(a==null||b==null)continue;
                Imgproc.line(lay,px(ClearlyOff.cpt(0.93,a)),px(ClearlyOff.cpt(0.93,b)),GUIDE,th,Imgproc.LINE_AA);
            }
            double s=Double.isFinite(r.ringScale)?r.ringScale:1;
            for(double rr:new double[]{r.roundCentreR,r.batonEndR})
                if(Double.isFinite(rr))Imgproc.circle(lay,px(new double[]{0,0}),(int)Math.round(rr*s*SIZE/(2*HALF)),GUIDE,th,Imgproc.LINE_AA);
            Core.addWeighted(lay,0.75,big,0.25,0,big);lay.release();
        }
        ModelSpec.Marker mk=model.atHour(am.hour);
        if(mk!=null){
            double[] c=mk.masterPoint();
            double rad=(mk.shape==ModelSpec.Shape.ROUND?0.12:mk.shape==ModelSpec.Shape.BATON?0.17:0.18)*SIZE/(2*HALF);
            Scalar col=colour(am);
            Imgproc.circle(big,px(c),(int)Math.round(rad),BLACK,4*th+2,Imgproc.LINE_AA);
            Imgproc.circle(big,px(c),(int)Math.round(rad),col,3*th,Imgproc.LINE_AA);
            ClearlyOff.Marker t=am.track;
            if(t!=null&&Double.isFinite(t.gapEdgeR)){
                // the triangle's gap: the minute-track tick ends (blue) and the measured top edge (marker colour)
                double k=SIZE/(2*HALF),a=30*(am.hour%12)-90;
                Imgproc.ellipse(big,px(new double[]{0,0}),new Size(t.gapTrackR*k,t.gapTrackR*k),0,a-9,a+9,GUIDE,2*th,Imgproc.LINE_AA);
                Imgproc.ellipse(big,px(new double[]{0,0}),new Size(t.gapEdgeR*k,t.gapEdgeR*k),0,a-9,a+9,col,2*th,Imgproc.LINE_AA);
            }
            if(t!=null&&(t.state==ClearlyOff.State.CLEARLY_OFF||t.state==ClearlyOff.State.POSSIBLE)&&t.cObs!=null){
                Point p=px(t.cObs);int u=SIZE/90;
                Imgproc.line(big,new Point(p.x-u,p.y),new Point(p.x+u,p.y),col,2*th,Imgproc.LINE_AA);
                Imgproc.line(big,new Point(p.x,p.y-u),new Point(p.x,p.y+u),col,2*th,Imgproc.LINE_AA);
            }
            String lab=String.valueOf(am.hour==0?12:am.hour);
            double[] q=ClearlyOff.cpt(Math.min(1.06,HALF-0.04),Math.toRadians(30*(am.hour%12)));
            double fs=SIZE/600.0;int[] base=new int[1];Size ts=Imgproc.getTextSize(lab,Imgproc.FONT_HERSHEY_SIMPLEX,fs,3*th,base);
            Point at=px(q);at=new Point(at.x-ts.width/2,at.y+ts.height/2);
            Imgproc.putText(big,lab,at,Imgproc.FONT_HERSHEY_SIMPLEX,fs,BLACK,6*th,Imgproc.LINE_AA);
            Imgproc.putText(big,lab,at,Imgproc.FONT_HERSHEY_SIMPLEX,fs,col,3*th,Imgproc.LINE_AA);
        }
        return big;
    }

    /** canonical -> picture px (the picture spans -HALF..HALF in both axes) */
    static Point px(double[] p){double k=SIZE/(2*HALF);return new Point((p[0]+HALF)*k-0.5,(p[1]+HALF)*k-0.5);}
}
