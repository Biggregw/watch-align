package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * RESEARCH ONLY (measurement-architecture review, tools/research/measurement_architecture). Runs the unchanged production
 * measurement chain (Alpha94MarkerMeasurement -> Alpha99MarkerInterference -> AlignmentAnalysis yellow references ->
 * ClearlyOff / MetalFit) on genuine photos and on controlled perturbations of them, and dumps the RAW per-marker
 * quantities so that competing measurement methods can be computed offline from identical measurements:
 *
 *   base               the photo as it is (pose from AutomaticDialOverlay)
 *   mv:H:t|r|rot:AMT   one marker moved tangentially / radially (R) or turned (deg) inside the photo (MetalSynth.move)
 *   ring:t|rot|scale   every applied marker moved together: translation (R), rotation about the dial centre (deg),
 *                      scale about the dial centre (fraction); the printed minute track stays where it was
 *   reg:t|rot|scale    the registration (H) perturbed, the photo unchanged: the physical geometry is identical
 *   persp:x|y          the whole photo warped by a homography (flat perspective, 8 deg); pose re-estimated
 *   res:N              the photo downscaled to an N px dial radius; pose re-estimated
 *
 * One JSON line per (photo, variant). Coordinates are canonical (dial-plane, minute-track registered; R units, x right,
 * y down). Nothing here changes a production class, threshold or reference.
 *
 *   ArchMeasure items.csv out.jsonl [max_photos] [synthetic 0|1|2]
 *
 * Synthetic 2 (genuine calibration, tools/research/genuine_calibration): a grid of known defects on the same photo, only
 * on markers the MarkerGeometry stage finds eligible. Every eligible marker is moved once (tangential or radial,
 * amplitude cycling through CAL_MOVE_R), every baton and triangle is also turned once (CAL_TURN_DEG); two variants move
 * two markers and one moves three (CAL_MULTI_R each, "mm:H:t|r:AMT|H:..."); the whole applied-marker set is moved by
 * CAL_RING_T (two sizes), turned and scaled.
 */
public class ArchMeasure {
    static final double[] CAL_MOVE_R={0.0025,0.005,0.0075,0.010},CAL_TURN_DEG={1.0,2.0,3.0},CAL_RING_T={0.003,0.006};
    static final double CAL_MULTI_R=0.005;
    static final double MOVE_R=0.005,TURN_DEG=1.0,RING_T=0.004,RING_ROT_DEG=0.4,RING_SCALE=0.005,REG_T=0.003,REG_ROT_DEG=0.3,REG_SCALE=0.005,PERSP_DEG=8;

    static ModelSpec model;static ModelReference metal;static AlignmentReference ref;

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        model=HarnessModel.spec();metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        ref=Files.exists(refFile)?AlignmentReference.parse(Files.readString(refFile)):null;
        if(ref!=null){Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));}
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(Path.of(a[0])));
        int max=a.length>2?Integer.parseInt(a[2]):Integer.MAX_VALUE;boolean synth=a.length>3&&"1".equals(a[3]);
        boolean grid=a.length>3&&"2".equals(a[3]);
        int done=0;
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(Path.of(a[1])))){
            for(Map<String,String> r:rows){
                if(done>=max)break;
                String id=r.get("photo_id"),path=r.get("path");
                if(path==null||path.isEmpty()||!Files.exists(Path.of(path)))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(path);if(b==null)continue;
                AutomaticDialOverlay.Result pose;
                try{pose=AutomaticDialOverlay.build(b,model);}catch(Throwable t){pose=null;}
                if(pose==null||!pose.valid||pose.homography==null){w.println("{\"photo_id\":\""+id+"\",\"variant\":\"base\",\"ok\":false}");continue;}
                double[] H=pose.homography;
                Run base=run(b,H);
                w.println(base.json(id,"base",H,true));w.flush();done++;
                System.out.println("ARCH "+id);
                if(grid&&base.co!=null&&base.geo!=null){calibrationGrid(w,id,b,H,base,new Random(id.hashCode()));continue;}
                if(!synth||base.co==null)continue;
                long seed=id.hashCode();Random rng=new Random(seed);
                // single-marker moves (pose unchanged: the minute track is untouched)
                for(ClearlyOff.Marker m:base.co.markers){
                    if(m.state==ClearlyOff.State.CANT_CHECK||m.yellow==null)continue;
                    double sg=rng.nextBoolean()?1:-1;double ah=Math.toRadians(30*(m.hour%12));
                    double[] er={Math.sin(ah),-Math.cos(ah)},et={Math.cos(ah),Math.sin(ah)};
                    boolean tang=rng.nextBoolean();double[] d=tang?et:er;
                    emit(w,id,"mv:"+m.hour+":"+(tang?"t":"r")+":"+f(sg*MOVE_R),b,H,new double[][]{{m.hour,sg*MOVE_R*d[0],sg*MOVE_R*d[1],0}});
                    if(m.shape!=ModelSpec.Shape.ROUND)emit(w,id,"mv:"+m.hour+":rot:"+f(sg*TURN_DEG),b,H,new double[][]{{m.hour,0,0,Math.toRadians(sg*TURN_DEG)}});
                }
                // the whole applied-marker set moved against the printed minute track
                double ang=rng.nextDouble()*2*Math.PI;
                emit(w,id,"ring:t:"+f(RING_T*Math.cos(ang))+":"+f(RING_T*Math.sin(ang)),b,H,ringMove(base,RING_T*Math.cos(ang),RING_T*Math.sin(ang),0,0));
                emit(w,id,"ring:rot:"+f(RING_ROT_DEG),b,H,ringMove(base,0,0,Math.toRadians(RING_ROT_DEG),0));
                emit(w,id,"ring:scale:"+f(RING_SCALE),b,H,ringMove(base,0,0,0,RING_SCALE));
                // registration errors: same photo, perturbed H (canonical q -> physical q + delta etc.)
                double ra=rng.nextDouble()*2*Math.PI;
                regVariant(w,id,"reg:t:"+f(REG_T*Math.cos(ra))+":"+f(REG_T*Math.sin(ra)),b,H,new double[]{1,0,REG_T*Math.cos(ra),0,1,REG_T*Math.sin(ra),0,0,1});
                double c=Math.cos(Math.toRadians(REG_ROT_DEG)),s=Math.sin(Math.toRadians(REG_ROT_DEG));
                regVariant(w,id,"reg:rot:"+f(REG_ROT_DEG),b,H,new double[]{c,-s,0,s,c,0,0,0,1});
                regVariant(w,id,"reg:scale:"+f(REG_SCALE),b,H,new double[]{1+REG_SCALE,0,0,0,1+REG_SCALE,0,0,0,1});
                // flat perspective and resolution: new image, pose re-estimated
                for(String axis:new String[]{"x","y"}){
                    Bitmap p=perspective(b,H,axis);
                    fresh(w,id,"persp:"+axis+":"+f(PERSP_DEG),p);
                }
                for(int target:new int[]{250,200}){
                    if(base.rpx<target*1.15)continue;
                    double k=target/base.rpx;
                    Bitmap p=Bitmap.createScaledBitmap(b,(int)Math.round(b.getWidth()*k),(int)Math.round(b.getHeight()*k),true);
                    fresh(w,id,"res:"+target,p);
                }
            }
        }
    }

    /** synthetic mode 2: known single, multiple and whole-ring defects on the eligible markers of one genuine photo */
    static void calibrationGrid(PrintWriter w,String id,Bitmap b,double[] H,Run base,Random rng){
        List<ClearlyOff.Marker> ok=new ArrayList<>();
        for(ClearlyOff.Marker m:base.co.markers){MarkerGeometry.Marker g=base.geo.at(m.hour);if(g!=null&&g.eligible&&m.yellow!=null)ok.add(m);}
        int k=rng.nextInt(CAL_MOVE_R.length),kt=rng.nextInt(CAL_TURN_DEG.length);
        for(ClearlyOff.Marker m:ok){
            double sg=rng.nextBoolean()?1:-1,amp=CAL_MOVE_R[k++%CAL_MOVE_R.length];boolean tang=rng.nextBoolean();
            double[] d=dir(m.hour,tang);
            emit(w,id,"mv:"+m.hour+":"+(tang?"t":"r")+":"+f(sg*amp),b,H,new double[][]{{m.hour,sg*amp*d[0],sg*amp*d[1],0}});
            if(m.shape!=ModelSpec.Shape.ROUND){double t=(rng.nextBoolean()?1:-1)*CAL_TURN_DEG[kt++%CAL_TURN_DEG.length];
                emit(w,id,"mv:"+m.hour+":rot:"+f(t),b,H,new double[][]{{m.hour,0,0,Math.toRadians(t)}});}
        }
        for(int n:new int[]{2,2,3}){
            if(ok.size()<n+5)continue;
            List<ClearlyOff.Marker> pick=new ArrayList<>(ok);java.util.Collections.shuffle(pick,rng);pick=pick.subList(0,n);
            double[][] mv=new double[n][];StringBuilder v=new StringBuilder("mm:");
            for(int i=0;i<n;i++){ClearlyOff.Marker m=pick.get(i);double sg=rng.nextBoolean()?1:-1;boolean tang=rng.nextBoolean();
                double[] d=dir(m.hour,tang);mv[i]=new double[]{m.hour,sg*CAL_MULTI_R*d[0],sg*CAL_MULTI_R*d[1],0};
                v.append(i>0?"|":"").append(m.hour).append(':').append(tang?"t":"r").append(':').append(f(sg*CAL_MULTI_R));}
            emit(w,id,v.toString(),b,H,mv);
        }
        for(double t:CAL_RING_T){double ang=rng.nextDouble()*2*Math.PI;
            emit(w,id,"ring:t:"+f(t*Math.cos(ang))+":"+f(t*Math.sin(ang)),b,H,ringMove(base,t*Math.cos(ang),t*Math.sin(ang),0,0));}
        emit(w,id,"ring:rot:"+f(RING_ROT_DEG),b,H,ringMove(base,0,0,Math.toRadians(RING_ROT_DEG),0));
        emit(w,id,"ring:scale:"+f(RING_SCALE),b,H,ringMove(base,0,0,0,RING_SCALE));
    }

    /** canonical unit direction at a marker: tangential (clockwise) or radial (outward); y down */
    static double[] dir(int hour,boolean tang){
        double ah=Math.toRadians(30*(hour%12));
        return tang?new double[]{Math.cos(ah),Math.sin(ah)}:new double[]{Math.sin(ah),-Math.cos(ah)};
    }

    // ------------------------------------------------------------------------------------------------ one measurement

    static final class Run {
        double rpx=Double.NaN;Alpha94MarkerMeasurement.Report a94;ClearlyOff.Result co;
        List<double[]> ticks;Map<Integer,double[]> loo=new java.util.HashMap<>();String error;
        /** Phase B: the Java MarkerGeometry stage on the same measurement (parity with methods.py) */
        MarkerGeometry.Result geo;
        String json(String id,String variant,double[] H,boolean withTicks){
            StringBuilder j=new StringBuilder("{");kv(j,"photo_id",id);kv(j,"variant",variant);kv(j,"model",model.id);
            boolean ok=co!=null;j.append("\"ok\":").append(ok).append(',');
            if(error!=null)kv(j,"error",error);
            num(j,"rpx",rpx);arr(j,"H",H);
            if(co!=null){
                j.append("\"lines\":{");boolean first=true;
                for(Map.Entry<Integer,Double> e:co.lines.entrySet()){j.append(first?"":",").append('"').append(e.getKey()).append("\":").append(f(e.getValue()));first=false;}
                j.append("},");num(j,"track_scale",co.trackScale);num(j,"ring_scale",co.ringScale);
            }
            if(withTicks&&ticks!=null){j.append("\"ticks\":[");for(int i=0;i<ticks.size();i++){double[] t=ticks.get(i);j.append(i>0?",":"").append('[').append(f(t[0])).append(',').append(f(t[1])).append(',').append((int)t[2]).append(']');}j.append("],");}
            if(a94!=null&&a94.ring!=null&&a94.ring.usable)arr(j,"a94_ring",a94.ring.model);
            if(a94!=null){j.append("\"a94\":[");boolean first=true;
                for(Alpha94MarkerMeasurement.Marker m:a94.markers){if(!m.usable||!Double.isFinite(m.canonDx))continue;
                    j.append(first?"":",").append('[').append(m.hour).append(',').append(f(m.canonDx)).append(',').append(f(m.canonDy)).append(']');first=false;}
                j.append("],");}
            if(geo!=null&&geo.ring.ok){
                j.append("\"mg_ring\":[").append(f(geo.ring.txPct)).append(',').append(f(geo.ring.tyPct)).append(',')
                 .append(f(geo.ring.scalePct)).append(',').append(f(geo.ring.rotDeg)).append(',').append(f(geo.ring.placeXPct)).append(',')
                 .append(f(geo.ring.placeYPct)).append(',').append(geo.ring.n).append("],");
                j.append("\"mg_ring_hours\":[");for(int i=0;i<geo.ring.hours.length;i++)j.append(i>0?",":"").append(geo.ring.hours[i]);j.append("],");
            }
            if(co!=null){
                j.append("\"markers\":[");boolean first=true;
                for(ClearlyOff.Marker m:co.markers){
                    j.append(first?"":",").append('{');first=false;
                    num(j,"h",m.hour);kv(j,"shape",AlignmentReference.key(m.shape));kv(j,"state",m.state.name());
                    kv(j,"gate",String.join("; ",m.gate));kv(j,"unc",m.uncertain==null?"":m.uncertain);
                    j.append("\"hand\":").append(m.handConfirmed).append(',');
                    j.append("\"rel\":").append(WatchClassification.reliable(m)).append(',');
                    j.append("\"cred\":").append(WatchClassification.credible(m)).append(',');
                    num(j,"cov",m.cover);num(j,"rms",m.fitRmsPx);num(j,"bf",m.badFrac);
                    arr(j,"cO",m.cObs);arr(j,"cG",m.cGen);arr(j,"fix",fixedCentre(m.hour));arr(j,"loo",loo.get(m.hour));
                    num(j,"ft",m.fitTiltDeg);num(j,"side",m.sidePct);num(j,"inout",m.inoutPct);num(j,"tilt",m.tiltDeg);
                    num(j,"pos",m.posPct);num(j,"gap",m.gapPct);
                    j.append("\"top\":").append(m.radialFromTopEdge).append(',');
                    MarkerGeometry.Marker g=geo==null?null:geo.at(m.hour);
                    if(g!=null){j.append("\"mg\":[").append(Double.isFinite(g.tangPct)?f(g.tangPct):"null").append(',')
                        .append(Double.isFinite(g.radPct)?f(g.radPct):"null").append(',').append(Double.isFinite(g.tiltDeg)?f(g.tiltDeg):"null")
                        .append(',').append(g.eligible).append("],");}
                    trim(j);j.append('}');
                }
                j.append("],");
            }
            trim(j);return j.append('}').toString();
        }
    }

    /** the production chain on a given pose (Alpha99Pipeline.run without re-estimating the pose) */
    static Run run(Bitmap b,double[] H){
        Run o=new Run();
        Mat rgba=new Mat(),gray=new Mat();
        try{
            Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            o.a94=Alpha94MarkerMeasurement.analyse(b,H,model);o.rpx=o.a94.dialRadiusPx;
            Mat handGray=gray;
            try{handGray=GuideLines.forHandCheck(rgba,gray,Alpha99MarkerInterference.pxPerR(H));}catch(Throwable t){handGray=gray;}
            Map<Integer,Alpha99MarkerInterference.Check> checks;
            try{checks=Alpha99MarkerInterference.analyse(handGray,H,model);}catch(Throwable t){checks=null;}
            if(handGray!=gray)handGray.release();
            AlignmentAnalysis.Setups su=ref==null?s->null:ref::setup;
            final Mat g=gray;
            AlignmentAnalysis.Output al=AlignmentAnalysis.run((mk,ring)->AlignmentAnalysis.image(g,H,mk,ring),H,o.a94,checks,model,metal,ref,su);
            for(ModelSpec.Marker mk:model.markers)o.loo.put(mk.hour,LuminousAlignment.ringWithout(o.a94,mk.hour));
            o.co=ClearlyOff.analyse(gray,H,o.rpx,Alpha99Pipeline.clearlyOffInputs(al,checks),Alpha99Pipeline.insets(ref),model.minuteTrackInnerR,ClearlyOff.Limits.defaults());
            try{o.geo=MarkerGeometry.fromPipeline(o.co,o.a94,model,metal,H);}catch(Throwable t){o.geo=null;}
            Mat fl=new Mat();gray.convertTo(fl,CvType.CV_32F);
            List<ClearlyOff.Tick> ts=ClearlyOff.tickTrack(new ClearlyOff.Img(fl,H),o.rpx);fl.release();
            o.ticks=new ArrayList<>();for(ClearlyOff.Tick t:ts)o.ticks.add(new double[]{t.a,t.rIn,t.bad.isEmpty()?0:1});
        }catch(Throwable t){o.error=t.getClass().getSimpleName();}
        finally{rgba.release();gray.release();}
        return o;
    }

    /** the fixed genuine reference centre of a marker: its model outline at the reference pose (no ring), vertex mean as
     *  MetalFit's cGen */
    static double[] fixedCentre(int hour){
        ModelSpec.Marker mk=model.atHour(hour);if(mk==null)return null;
        double[][] p=ExpectedMarkerOutline.polygon(mk,metal,null);return p==null?null:ClearlyOff.mean(p);
    }

    // ------------------------------------------------------------------------------------------------ perturbations

    /** each row {hour, tx, ty, theta}: move those markers in the photo (sequentially), re-measure on the same pose */
    static void emit(PrintWriter w,String id,String variant,Bitmap b,double[] H,double[][] moves){
        Bitmap p=moved(b,H,moves);
        Run r=p==null?new Run():run(p,H);if(p==null)r.error="move failed";
        w.println(r.json(id,variant,H,false));w.flush();
    }

    static Bitmap moved(Bitmap b,double[] H,double[][] moves){
        Mat rgba=new Mat();Utils.bitmapToMat(b,rgba);
        List<Mat> ch=new ArrayList<>();org.opencv.core.Core.split(rgba,ch);
        try{
            for(double[] mv:moves){
                ModelSpec.Marker mk=model.atHour((int)mv[0]);if(mk==null)continue;
                double[][] y=ExpectedMarkerOutline.polygon(mk,metal,null);
                for(int i=0;i<3;i++){Mat o=MetalSynth.move(ch.get(i),H,y,mv[1],mv[2],mv[3]);ch.get(i).release();ch.set(i,o);}
            }
            Mat out=new Mat();org.opencv.core.Core.merge(ch,out);
            Bitmap nb=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(out,nb);out.release();
            return nb;
        }catch(Throwable t){return null;}
        finally{rgba.release();for(Mat m:ch)m.release();}
    }

    /** every marker moved by the same rigid / scale change of the whole set about the dial centre */
    static double[][] ringMove(Run base,double tx,double ty,double rot,double scale){
        List<double[]> out=new ArrayList<>();
        for(ModelSpec.Marker mk:model.markers){
            double[] c=fixedCentre(mk.hour);if(c==null)continue;
            double cs=Math.cos(rot),sn=Math.sin(rot);
            double nx=(1+scale)*(cs*c[0]-sn*c[1])+tx,ny=(1+scale)*(sn*c[0]+cs*c[1])+ty;
            out.add(new double[]{mk.hour,nx-c[0],ny-c[1],rot});
        }
        return out.toArray(new double[0][]);
    }

    /** the registration perturbed: H' = H * T (canonical q is physical T q); the photo is unchanged */
    static void regVariant(PrintWriter w,String id,String variant,Bitmap b,double[] H,double[] T){
        double[] Hp=new double[9];
        for(int i=0;i<3;i++)for(int j=0;j<3;j++){double s=0;for(int k=0;k<3;k++)s+=H[3*i+k]*T[3*k+j];Hp[3*i+j]=s;}
        double n=Hp[8];for(int i=0;i<9;i++)Hp[i]/=n;
        Run r=run(b,Hp);w.println(r.json(id,variant,Hp,false));w.flush();
    }

    /** the photo as seen by a camera turned PERSP_DEG about the dial's horizontal (x) or vertical (y) axis (flat dial) */
    static Bitmap perspective(Bitmap b,double[] H,String axis){
        double[] c=project(H,0,0);double f=1.5*Math.max(b.getWidth(),b.getHeight());
        double t=Math.toRadians(PERSP_DEG);double[][] R=axis.equals("x")?new double[][]{{1,0,0},{0,Math.cos(t),-Math.sin(t)},{0,Math.sin(t),Math.cos(t)}}
                :new double[][]{{Math.cos(t),0,Math.sin(t)},{0,1,0},{-Math.sin(t),0,Math.cos(t)}};
        // K R K^-1 with the principal point at the dial centre
        double[][] K={{f,0,c[0]},{0,f,c[1]},{0,0,1}},Ki={{1/f,0,-c[0]/f},{0,1/f,-c[1]/f},{0,0,1}};
        double[][] M=mul(mul(K,R),Ki);
        Mat src=new Mat();Utils.bitmapToMat(b,src);Mat dst=new Mat();Mat Mm=new Mat(3,3,CvType.CV_64F);
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)Mm.put(i,j,M[i][j]);
        Imgproc.warpPerspective(src,dst,Mm,new Size(b.getWidth(),b.getHeight()),Imgproc.INTER_CUBIC,org.opencv.core.Core.BORDER_REPLICATE);
        Bitmap nb=Bitmap.createBitmap(b.getWidth(),b.getHeight(),Bitmap.Config.ARGB_8888);Utils.matToBitmap(dst,nb);
        src.release();dst.release();Mm.release();return nb;
    }

    static void fresh(PrintWriter w,String id,String variant,Bitmap p){
        AutomaticDialOverlay.Result pose;try{pose=AutomaticDialOverlay.build(p,model);}catch(Throwable t){pose=null;}
        if(pose==null||!pose.valid||pose.homography==null){w.println("{\"photo_id\":\""+id+"\",\"variant\":\""+variant+"\",\"ok\":false}");w.flush();return;}
        Run r=run(p,pose.homography);w.println(r.json(id,variant,pose.homography,false));w.flush();
    }

    // ------------------------------------------------------------------------------------------------ helpers

    static double[][] mul(double[][] a,double[][] b){double[][] o=new double[3][3];for(int i=0;i<3;i++)for(int j=0;j<3;j++){double s=0;for(int k=0;k<3;k++)s+=a[i][k]*b[k][j];o[i][j]=s;}return o;}
    static double[] project(double[] H,double x,double y){double w=H[6]*x+H[7]*y+H[8];return new double[]{(H[0]*x+H[1]*y+H[2])/w,(H[3]*x+H[4]*y+H[5])/w};}
    static void trim(StringBuilder j){if(j.charAt(j.length()-1)==',')j.setLength(j.length()-1);}
    static void kv(StringBuilder j,String k,String v){j.append('"').append(k).append("\":\"").append(v.replace("\\","\\\\").replace("\"","\\\"")).append("\",");}
    static void num(StringBuilder j,String k,double v){j.append('"').append(k).append("\":").append(Double.isFinite(v)?f(v):"null").append(',');}
    static void arr(StringBuilder j,String k,double[] v){if(v==null)return;j.append('"').append(k).append("\":[");for(int i=0;i<v.length;i++)j.append(i>0?",":"").append(Double.isFinite(v[i])?f(v[i]):"null");j.append("],");}
    static String f(double v){return String.format(Locale.US,"%.12g",v);}
}
