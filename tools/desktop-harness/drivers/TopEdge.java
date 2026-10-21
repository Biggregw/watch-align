package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EXPERIMENTAL research (read-only; owner, 21 Oct 2026): the 12 triangle's radial position by the existing whole-outline
 * fit (MetalFit; production) and by its measured top edge against the ring of the other markers (TriangleTopEdge),
 * side by side.
 *
 *   TopEdge dump  manifest.csv image_root out.csv [closeup_dir]   one row per photo; annotated close-ups
 *   TopEdge synth manifest.csv image_root out.csv [max_photos]    the triangle moved / turned by known amounts in the
 *                                                                  photo (MetalSynth.move), both methods re-measured
 */
public class TopEdge {
    static final String HEAD="photo_id,trial,amount,rpx,state,old_inout_pct,old_top_pct,old_tilt_deg,old_rms_px,old_bad_frac,old_cover,uncertain,"
            +"new_note,new_radial_pct,new_radial_px,new_angle_deg,new_raw_pct,round_edge_offset_px,tri_edge_offset_px,baton_edge_offset_px,expected_r,measured_r,genuine_r,ring_scale,ring_markers,ring_uncertain,"
            +"profiles,usable,inliers,rms_px,span,second_frac,second_offset_px,p1x,p1y,p2x,p2y,track_r";

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        String mode=a[0];Path manifest=Paths.get(a[1]),root=Paths.get(a[2]),out=Paths.get(a[3]);
        ModelSpec model=HarnessModel.spec();ModelReference metal=HarnessModel.ref();
        Path refFile=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference ref=Files.exists(refFile)?AlignmentReference.parse(Files.readString(refFile)):null;
        if(ref!=null){Path mf=refFile.resolveSibling("metal_alignment.properties");if(Files.exists(mf))ref.metalExtra=MetalAlignment.parse(Files.readString(mf));}
        Path dir=mode.equals("dump")&&a.length>4?Paths.get(a[4]):null;if(dir!=null)Files.createDirectories(dir);
        int max=mode.equals("synth")&&a.length>4?Integer.parseInt(a[4]):Integer.MAX_VALUE,done=0;
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(manifest));
        try(PrintWriter w=new PrintWriter(Files.newBufferedWriter(out))){
            w.println(HEAD);
            for(Map<String,String> r:rows){
                if(done>=max)break;
                String id=r.get("photo_id");Path img=root.resolve(r.get("local_path"));if(!Files.exists(img))continue;
                Bitmap b=Alpha96Calib.loadAlpha96(img.toString());if(b==null)continue;
                Alpha99Pipeline.Output o;
                try{o=Alpha99Pipeline.run(b,model,metal,ref,ref==null?null:ref::setup);}catch(Throwable t){o=null;}
                if(o==null||o.clearlyOff==null||o.alignment==null){System.out.println("TOPEDGE NOPOSE "+id);continue;}
                ClearlyOff.Marker t=o.clearlyOff.at(12);
                if(t==null)continue;
                double rpx=o.clearlyOff.rpx;bias=o.clearlyOff.edgeBiasPx;
                w.println(row(id,"none",0,rpx,t));
                System.out.println("TOPEDGE "+id+" old "+f(t.inoutPct)+" new "+(t.topEdge==null?"error":t.topEdge.ok()?f(t.topEdge.radialPct):t.topEdge.note)
                        +(t.topEdge!=null&&t.topEdge.ambiguous()?" AMBIGUOUS":""));
                if(dir!=null)closeup(b,o.pose.homography,t,rpx,dir.resolve(id+"_12_topedge.png"),id);
                if(mode.equals("synth")){
                    if(t.state==ClearlyOff.State.CANT_CHECK||t.yellow==null)continue;
                    done++;
                    double[] H=o.pose.homography;
                    List<ClearlyOff.Input> in=Alpha99Pipeline.clearlyOffInputs(o.alignment,o.checks);
                    Map<ModelSpec.Shape,Double> insets=Alpha99Pipeline.insets(ref);
                    Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);rgba.release();
                    double a0=0;double[] er={Math.sin(a0),-Math.cos(a0)},et={Math.cos(a0),Math.sin(a0)};
                    Object[][] trials={{"inout",-1.5},{"inout",-1.0},{"inout",-0.5},{"inout",0.5},{"inout",1.0},{"inout",1.5},
                            {"side",-0.8},{"side",0.8},{"turn",-1.5},{"turn",1.5}};
                    for(Object[] tr:trials){
                        String kind=(String)tr[0];double amt=(Double)tr[1];
                        Mat g2=kind.equals("inout")?MetalSynth.move(gray,H,t.yellow,amt/100*er[0],amt/100*er[1],0)
                                :kind.equals("side")?MetalSynth.move(gray,H,t.yellow,amt/100*et[0],amt/100*et[1],0)
                                :MetalSynth.move(gray,H,t.yellow,0,0,Math.toRadians(amt));
                        ClearlyOff.Result res;
                        try{res=ClearlyOff.analyse(g2,H,rpx,in,insets,model.minuteTrackInnerR);}catch(Throwable e){res=null;}
                        g2.release();
                        ClearlyOff.Marker tm=res==null?null:res.at(12);if(res!=null)bias=res.edgeBiasPx;
                        if(tm!=null)w.println(row(id,kind,amt,rpx,tm));
                    }
                    gray.release();
                }
                w.flush();
            }
        }
    }

    /** the existing fit's top edge (its fitted outline's side farthest from the centre): distance from the centre (R) */
    static double fittedTopR(ClearlyOff.Marker t){
        if(t.fitPoly==null)return Double.NaN;double[][] y=t.fitPoly;int n=y.length,top=-1;double best=-1;
        for(int i=0;i<n;i++){double[] a=y[i],b=y[(i+1)%n];double d=ClearlyOff.norm(new double[]{(a[0]+b[0])/2,(a[1]+b[1])/2});if(d>best){best=d;top=i;}}
        double[] A=y[top],B=y[(top+1)%n];double ux=B[0]-A[0],uy=B[1]-A[1],L=Math.hypot(ux,uy);
        return Math.abs(A[0]*uy/L-A[1]*ux/L);
    }

    static Map<ModelSpec.Shape,Double> bias=new java.util.HashMap<>();
    static String row(String id,String trial,double amt,double rpx,ClearlyOff.Marker t){
        TriangleTopEdge.Result e=t.topEdge;
        double oldTop=e==null||!Double.isFinite(e.expectedR)?Double.NaN:100*(fittedTopR(t)-e.expectedR);
        return String.join(",",id,trial,f(amt),f(rpx),t.state.name(),f(t.inoutPct),f(oldTop),f(t.tiltDeg),f(t.fitRmsPx),f(t.badFrac),f(t.cover),t.uncertain.replace(",",";"),
                e==null?"error":e.note.replace(",",";"),e==null?"":f(e.radialPct),e==null?"":f(e.radialPx),e==null?"":f(e.angleDeg),e==null?"":f(e.radialRawPct),
                e==null?"":f(e.roundEdgeOffsetR*rpx),f(bias.getOrDefault(ModelSpec.Shape.TRIANGLE,Double.NaN)),f(bias.getOrDefault(ModelSpec.Shape.BATON,Double.NaN)),
                e==null?"":f(e.expectedR),e==null?"":f(e.measuredR),e==null?"":f(e.genuineR),e==null?"":f(e.scale),e==null?"":String.valueOf(e.ringMarkers),
                e==null?"":(e.ringFromUncertain?"1":""),e==null?"":String.valueOf(e.profiles),e==null?"":String.valueOf(e.usable),e==null?"":String.valueOf(e.inliers),
                e==null?"":f(e.rmsPx),e==null?"":f(e.span),e==null?"":f(e.secondFrac),e==null?"":f(e.secondOffsetPx),
                e==null||e.p1==null?"":f(e.p1[0]),e==null||e.p1==null?"":f(e.p1[1]),e==null||e.p2==null?"":f(e.p2[0]),e==null||e.p2==null?"":f(e.p2[1]),
                e==null?"":f(e.trackR));
    }

    static double[] px(double[] H,double x,double y){double w=H[6]*x+H[7]*y+H[8];return new double[]{(H[0]*x+H[1]*y+H[2])/w,(H[3]*x+H[4]*y+H[5])/w};}

    /** the 12 at high resolution: the photo, the genuine outline as registered (yellow), the reference circle for the top
     *  edge (cyan), the existing fit's outline (orange), the measured top edge (green line) and its edge points (green
     *  inliers, red left out), with the signed difference */
    static void closeup(Bitmap b,double[] H,ClearlyOff.Marker t,double rpx,Path file,String id) throws Exception {
        TriangleTopEdge.Result e=t.topEdge;if(t.yellow==null)return;
        double x0=1e9,y0=1e9,x1=-1e9,y1=-1e9;
        for(double[] q:t.yellow){double[] p=px(H,q[0],q[1]);x0=Math.min(x0,p[0]);x1=Math.max(x1,p[0]);y0=Math.min(y0,p[1]);y1=Math.max(y1,p[1]);}
        double[] trk=px(H,0,-(e!=null&&Double.isFinite(e.trackR)?e.trackR:0.95));y0=Math.min(y0,trk[1]);
        // the top part: from the track down to a third of the triangle's height
        double h=y1-y0;y1=y0+0.45*h;double pad=0.04*rpx;x0-=pad;x1+=pad;y0-=pad;y1+=pad;
        int X0=(int)Math.max(0,x0),Y0=(int)Math.max(0,y0),X1=(int)Math.min(b.getWidth()-1,x1),Y1=(int)Math.min(b.getHeight()-1,y1);
        if(X1<=X0||Y1<=Y0)return;
        double k=Math.max(1,1200.0/(X1-X0));
        int W=(int)((X1-X0)*k),Hh=(int)((Y1-Y0)*k)+120;
        BufferedImage im=new BufferedImage(W,Hh,BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(b.img.getSubimage(X0,Y0,X1-X0,Y1-Y0),0,0,W,(int)((Y1-Y0)*k),null);
        java.util.function.BiFunction<Double,Double,double[]> P=(cx,cy)->{double[] p=px(H,cx,cy);return new double[]{(p[0]-X0)*k,(p[1]-Y0)*k};};
        g.setStroke(new BasicStroke(1.5f));
        poly(g,P,t.yellow,new Color(255,220,0));
        if(t.fitPoly!=null)poly(g,P,t.fitPoly,new Color(255,140,0));
        if(e!=null&&Double.isFinite(e.expectedR)){
            g.setColor(Color.CYAN);double prev[]=null;
            for(double aa=-0.22;aa<=0.22;aa+=0.004){double ang=e.a0+aa;double[] q=P.apply(e.expectedR*Math.sin(ang),-e.expectedR*Math.cos(ang));
                if(prev!=null)g.draw(new Line2D.Double(prev[0],prev[1],q[0],q[1]));prev=q;}
            for(int i=0;i<e.points.size();i++){double[] q=P.apply(e.points.get(i)[0],e.points.get(i)[1]);g.setColor(e.inlier.get(i)?Color.GREEN:Color.RED);
                g.fill(new java.awt.geom.Ellipse2D.Double(q[0]-2.5,q[1]-2.5,5,5));}
            if(e.p1!=null){g.setColor(Color.GREEN);g.setStroke(new BasicStroke(2f));double[] q1=P.apply(e.p1[0],e.p1[1]),q2=P.apply(e.p2[0],e.p2[1]);g.draw(new Line2D.Double(q1[0],q1[1],q2[0],q2[1]));}
        }
        g.setColor(Color.BLACK);g.fillRect(0,(int)((Y1-Y0)*k),W,120);g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,17));
        int ty=(int)((Y1-Y0)*k)+22;
        g.drawString(id+"  yellow genuine (as registered)  orange existing fit  cyan reference circle  green measured top edge (red points left out)",8,ty);
        g.drawString(String.format(Locale.US,"existing (centre of fitted outline): %+.3f %% R   fitted top edge: %+.3f %% R   rms %.2f px  bad %.0f %%  seen %.0f %%",
                t.inoutPct,e==null||!Double.isFinite(e.expectedR)?Double.NaN:100*(fittedTopR(t)-e.expectedR),t.fitRmsPx,100*t.badFrac,100*t.cover),8,ty+24);
        g.drawString("new (measured top edge vs ring): "+(e==null?"error":e.ok()?String.format(Locale.US,"%+.3f %% R (%+.2f px; without the rounds' edge offset %+.3f %% R); edge turned %+.2f deg; %d/%d points, rms %.2f px",
                e.radialPct,e.radialPx,e.radialRawPct,e.angleDeg,e.inliers,e.usable,e.rmsPx):e.note),8,ty+48);
        if(e!=null&&e.ok())g.drawString(String.format(Locale.US,"reference circle %.4f R = %.4f R genuine x %.4f ring scale (%d %s rounds) + their edge offset %.2f px%s",e.expectedR,e.genuineR,e.scale,e.ringMarkers,
                e.ringFromUncertain?"incl. uncertain":"reliable",e.roundEdgeOffsetR*rpx,e.ambiguous()?String.format(Locale.US,"; AMBIGUOUS: second edge %.1f px away (%.0f %%)",e.secondOffsetPx,100*e.secondFrac):""),8,ty+72);
        g.dispose();javax.imageio.ImageIO.write(im,"png",file.toFile());
    }
    static void poly(Graphics2D g,java.util.function.BiFunction<Double,Double,double[]> P,double[][] y,Color c){
        g.setColor(c);for(int i=0;i<y.length;i++){double[] a=P.apply(y[i][0],y[i][1]),b=P.apply(y[(i+1)%y.length][0],y[(i+1)%y.length][1]);g.draw(new Line2D.Double(a[0],a[1],b[0],b[1]));}
    }
    static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.4f",v):"";}
}
