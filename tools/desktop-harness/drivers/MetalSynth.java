package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Synthetic tests of the clearly-off check (app code) on genuine photos: for every marker the unchanged photo measures,
 *   occlude_dark / occlude_bright  a hand-like bar (0.045 R wide) across it, hiding about a third of its outline
 *   side / inout                   the marker moved 1.5 % R sideways / outwards (its pixels moved inside the photo)
 *   turn                           batons turned 3 deg, the triangle 1.5 deg about their centre
 * and the photo measured again. The bars are not seen by the hand finder (they do not run from the pivot), so they test
 * the edge checks alone. Run with -Dwatchalign.metal=off for the 1.6.2 detector.
 *   MetalSynth manifest.csv image_root out.csv [max_photos]
 */
public class MetalSynth {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        Path manifest=Paths.get(a[0]),root=Paths.get(a[1]),out=Paths.get(a[2]);int max=a.length>3?Integer.parseInt(a[3]):Integer.MAX_VALUE;
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=Files.exists(refFile)?AlignmentReference.parse(Files.readString(refFile)):null;
        if(ref!=null){Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));}
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(manifest));
        int done=0;
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
            w.println("photo_id,model,rpx,hour,shape,trial,amount,base_state,state,base_side,base_inout,base_tilt,side,inout,tilt,cover,uncertain,reason");
            for(Map<String,String> r:rows){
                if(done>=max)break;
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));if(!Files.exists(img))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());if(b==null)continue;
                Alpha99Pipeline.Output o;
                try{o=Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);}catch(Throwable t){o=null;}
                if(o==null||o.clearlyOff==null||o.alignment==null)continue;
                done++;
                double[] H=o.pose.homography;double rpx=o.measurement.dialRadiusPx;
                List<ClearlyOff.Input> in=Alpha99Pipeline.clearlyOffInputs(o.alignment,o.checks);
                Map<ModelSpec.Shape,Double> insets=Alpha99Pipeline.insets(ref);
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);rgba.release();
                ClearlyOff.Result base=o.clearlyOff;
                for(ClearlyOff.Input m:in){
                    ClearlyOff.Marker bm=base.at(m.hour);
                    if(bm==null||bm.state==ClearlyOff.State.CANT_CHECK||m.yellow==null)continue;
                    java.util.Random rng=new java.util.Random((id+"|"+m.hour).hashCode());
                    double ah=Math.toRadians(30*(m.hour%12));double[] er={Math.sin(ah),-Math.cos(ah)},et={Math.cos(ah),Math.sin(ah)};
                    List<Object[]> trials=new ArrayList<>();
                    double ang=rng.nextDouble()*Math.PI;
                    trials.add(new Object[]{"occlude_dark",0.0,occlude(gray,H,m.yellow,ang,false)});
                    trials.add(new Object[]{"occlude_bright",0.0,occlude(gray,H,m.yellow,ang,true)});
                    trials.add(new Object[]{"side",1.5,move(gray,H,m.yellow,0.015*et[0],0.015*et[1],0)});
                    trials.add(new Object[]{"inout",1.5,move(gray,H,m.yellow,0.015*er[0],0.015*er[1],0)});
                    if(m.shape==ModelSpec.Shape.BATON)trials.add(new Object[]{"turn",3.0,move(gray,H,m.yellow,0,0,Math.toRadians(3))});
                    if(m.shape==ModelSpec.Shape.TRIANGLE){double sg=bm.tiltDeg<0?-1:1;trials.add(new Object[]{"turn",1.5*sg,move(gray,H,m.yellow,0,0,Math.toRadians(1.5*sg))});}
                    for(Object[] t:trials){
                        Mat g2=(Mat)t[2];ClearlyOff.Result res;
                        try{res=ClearlyOff.analyse(g2,H,rpx,in,insets,model.minuteTrackInnerR);}catch(Throwable e){res=null;}
                        g2.release();
                        ClearlyOff.Marker tm=res==null?null:res.at(m.hour);
                        w.println(String.join(",",id,model.id,f(rpx),String.valueOf(m.hour),AlignmentReference.key(m.shape),(String)t[0],f((Double)t[1]),
                                bm.state.name(),tm==null?"ERROR":tm.state.name(),f(bm.sidePct),f(bm.inoutPct),f(bm.tiltDeg),
                                tm==null?"":f(tm.sidePct),tm==null?"":f(tm.inoutPct),tm==null?"":f(tm.tiltDeg),tm==null?"":f(tm.cover),
                                tm==null?"":tm.uncertain.replace(",",";"),tm==null?"":tm.reason.replace(",",";")));
                    }
                }
                gray.release();w.flush();System.out.println("SYNTH "+id);
            }
        }
    }

    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.4f",v):"";}

    static Point px(double[] H,double x,double y){double w=H[6]*x+H[7]*y+H[8];return new Point((H[0]*x+H[1]*y+H[2])/w,(H[3]*x+H[4]*y+H[5])/w);}

    /** a hand-like bar, 0.045 R wide, across the marker a third of the way out from its centre (it hides about a third of
     *  the outline), dark or bright like a polished hand; it extends 0.25 R either side, as a hand would */
    static Mat occlude(Mat gray,double[] H,double[][] y,double ang,boolean bright){
        double[] c=ClearlyOff.mean(y);double ux=Math.cos(ang),uy=Math.sin(ang),vx=-uy,vy=ux;
        double ext=0;for(double[] p:y)ext=Math.max(ext,Math.abs((p[0]-c[0])*vx+(p[1]-c[1])*vy));
        double off=0.33*ext;
        Mat o=gray.clone();
        fill(o,H,c,ux,uy,vx,vy,off-0.0225,off+0.0225,bright?220:15);
        return o;
    }
    static void fill(Mat m,double[] H,double[] c,double ux,double uy,double vx,double vy,double a,double b,double val){
        double[][] q={{c[0]+a*vx-0.25*ux,c[1]+a*vy-0.25*uy},{c[0]+a*vx+0.25*ux,c[1]+a*vy+0.25*uy},{c[0]+b*vx+0.25*ux,c[1]+b*vy+0.25*uy},{c[0]+b*vx-0.25*ux,c[1]+b*vy-0.25*uy}};
        Point[] p=new Point[4];for(int i=0;i<4;i++)p[i]=px(H,q[i][0],q[i][1]);
        List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(p));Imgproc.fillPoly(m,l,new Scalar(val));
    }

    /** the marker (its outline grown by 0.02 R) moved by (tx, ty) R and turned by th about its centre, in the photo; the
     *  place it left is filled with the dial around it */
    static Mat move(Mat gray,double[] H,double[][] y,double tx,double ty,double th){
        double[] c=ClearlyOff.mean(y);
        Mat mask=Mat.zeros(gray.size(),CvType.CV_8U),ring=Mat.zeros(gray.size(),CvType.CV_8U);
        poly(mask,H,grow(y,c,0.02),255);poly(ring,H,grow(y,c,0.045),255);poly(ring,H,grow(y,c,0.02),0);
        double bg=Core.mean(gray,ring).val[0];
        double cs=Math.cos(th),sn=Math.sin(th);
        Mat T=new Mat(3,3,CvType.CV_64F);T.put(0,0,cs,-sn,c[0]-cs*c[0]+sn*c[1]+tx, sn,cs,c[1]-sn*c[0]-cs*c[1]+ty, 0,0,1);
        Mat Hm=new Mat(3,3,CvType.CV_64F);Hm.put(0,0,H);Mat Hi=Hm.inv();
        Mat M=new Mat(),M2=new Mat(),e=new Mat();Core.gemm(Hm,T,1,e,0,M2);Core.gemm(M2,Hi,1,e,0,M);
        Mat moved=new Mat(),mmask=new Mat();
        Imgproc.warpPerspective(gray,moved,M,gray.size(),Imgproc.INTER_CUBIC);
        Imgproc.warpPerspective(mask,mmask,M,gray.size(),Imgproc.INTER_NEAREST);
        Mat o=gray.clone();o.setTo(new Scalar(bg),mask);moved.copyTo(o,mmask);
        mask.release();ring.release();T.release();Hm.release();Hi.release();M.release();M2.release();moved.release();mmask.release();
        return o;
    }
    static double[][] grow(double[][] y,double[] c,double d){
        double[][] g=new double[y.length][];
        for(int i=0;i<y.length;i++){double dx=y[i][0]-c[0],dy=y[i][1]-c[1],n=Math.hypot(dx,dy);g[i]=new double[]{c[0]+dx*(1+d/n),c[1]+dy*(1+d/n)};}
        return g;
    }
    static void poly(Mat m,double[] H,double[][] q,double val){
        Point[] p=new Point[q.length];for(int i=0;i<q.length;i++)p[i]=px(H,q[i][0],q[i][1]);
        List<MatOfPoint> l=new ArrayList<>();l.add(new MatOfPoint(p));Imgproc.fillPoly(m,l,new Scalar(val));
    }
}
