package com.watchalign.mobile;

import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * RESEARCH (Alpha105g, 9 o'clock reliability). For each photo, through the app's own Alpha99Pipeline: pose, ring fit,
 * every marker's local offset, and for each baton the four fitted sides' offsets from the master outline (+ = outward),
 * the width change (the two long sides together: a real shift moves both sides the same way and keeps the width; an
 * edge picked on the wrong facet moves one side only), the intensity profile across the baton, and the finding. Writes a
 * rectified close-up per baton with the expected (green: master moved by the dial's nominal) and fitted (red) outlines.
 *
 * Usage: run.sh BatonDiag <manifest.csv> <root> <outdir>
 */
public class BatonDiag {
    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        Path out=Path.of(a[2]);Files.createDirectories(out);
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);String id=f[iId];
            Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[iPath]).toString());
            Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,model,ref);
            if(o==null||!o.ok()){System.out.println("== "+id+": no pose");continue;}
            Alpha94MarkerMeasurement.Report r=o.measurement;double R=r.dialRadiusPx;AutomaticDialOverlay.Result q=o.pose;
            System.out.printf(Locale.US,"== %s R=%.1f px  pose: ticks %d sectors %d tickRMS %.2f px branchTurn %d%n",id,R,q.detectedTicks,q.completePairs,q.fitAfter,q.markerBranchTurn);
            if(r.ring!=null)System.out.printf(Locale.US,"   ring: usable %s n %d shift (%.2f, %.2f) px scale %+.3f%% rot %+.3f deg%n",r.ring.usable,r.ring.n,r.ring.shiftXPx,r.ring.shiftYPx,r.ring.scalePct,r.ring.rotationDeg);
            StringBuilder lo=new StringBuilder("   local offsets px (radial+out, tangential+cw):");
            for(Alpha94MarkerMeasurement.Marker m:r.markers)lo.append(String.format(Locale.US," %d:%s",m.hour,m.usable?String.format(Locale.US,"(%+.2f,%+.2f)",m.localRadialPx,m.localTangentialPx):"-"));
            System.out.println(lo);
            Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            Alpha99MarkerInterference.Sampler pix=Alpha99MarkerInterference.sampler(gray,q.homography);final double[] Hh=q.homography;
            Alpha99MarkerInterference.Sampler img=(x,y)->{double w=Hh[6]*x+Hh[7]*y+Hh[8];return pix.at((Hh[0]*x+Hh[1]*y+Hh[2])/w,(Hh[3]*x+Hh[4]*y+Hh[5])/w);};
            Alpha94MarkerMeasurement.Marker tm=r.triangle;
            if(tm!=null&&tm.sideOffR!=null&&tm.sideOffR.length==3)
                System.out.printf(Locale.US,"   triangle 12: usable %s | lateral %+.2f px radial %+.2f px | sides px: right %+.2f base %+.2f left %+.2f | sides-sum %+.2f px%n",
                        tm.usable,tm.localTangentialPx,tm.localRadialPx,tm.sideOffR[0]*R,tm.sideOffR[1]*R,tm.sideOffR[2]*R,(tm.sideOffR[0]+tm.sideOffR[2])*R);
            StringBuilder rr=new StringBuilder("   rounds radius err px / edge coverage:");
            for(Alpha94MarkerMeasurement.Marker m:r.markers)if("round".equals(m.kind))rr.append(String.format(Locale.US," %d:%s",m.hour,m.usable?String.format(Locale.US,"%+.2f/%.2f",m.radiusErrPx,m.fitSupport):("- "+m.reason)));
            System.out.println(rr);
            for(Alpha94MarkerMeasurement.Marker m:r.markers){
                if(!"baton".equals(m.kind))continue;ModelSpec.Marker sp=m.spec;
                Alpha99Findings.Finding fd=null;for(Alpha99Findings.Finding x:o.summary.all)if(x.hour==m.hour&&x.shape==Alpha99Findings.Shape.BATON)fd=x;
                System.out.printf(Locale.US,"   baton %d: usable %s %s | local tang %+.2f px (%.3f%%R) rad %+.2f px | rot %+.2f deg | integ %.2f cov %.2f%n",
                        m.hour,m.usable,m.reason,m.localTangentialPx,100*m.localTangentialPx/R,m.localRadialPx,m.rotationDeg,1-m.fitScorePx,m.fitSupport);
                if(m.sideOffR!=null){
                    // side 0 = long side at -tangential, 1 = outer end, 2 = long side at +tangential (clockwise), 3 = inner end
                    double w=m.sideOffR[0]+m.sideOffR[2],sh=(m.sideOffR[2]-m.sideOffR[0])/2,len=m.sideOffR[1]+m.sideOffR[3];
                    System.out.printf(Locale.US,"      sides px: ccw-long %+.2f  outer-end %+.2f  cw-long %+.2f  inner-end %+.2f | cov %.2f %.2f %.2f %.2f%n",
                            m.sideOffR[0]*R,m.sideOffR[1]*R,m.sideOffR[2]*R,m.sideOffR[3]*R,m.sideCov[0],m.sideCov[1],m.sideCov[2],m.sideCov[3]);
                    System.out.printf(Locale.US,"      width change %+.2f px (%+.3f%%R) | shift from long sides %+.2f px | length change %+.2f px%n",w*R,100*w,sh*R,len*R);
                }
                if(fd!=null)System.out.println("      finding: "+fd.status+" "+(fd.status==Alpha99Findings.Status.NOT_ASSESSED?fd.shortReason:fd.shortLine()));
                profile(img,sp,R);
                crop(img,sp,m,ref,out.resolve(id+"_baton"+m.hour+".png"));
            }
            rgba.release();gray.release();
        }
    }

    /** Grey level across the baton (along the clockwise tangential direction), averaged over the middle of its length. */
    static void profile(Alpha99MarkerInterference.Sampler img,ModelSpec.Marker sp,double R){
        double a=Math.toRadians(sp.hour*30.0);double[] er={Math.sin(a),-Math.cos(a)},et={Math.cos(a),Math.sin(a)};
        StringBuilder s=new StringBuilder("      profile across (px from master centre, cw +):");
        double step=1.0/R;   // one pixel
        int n=(int)Math.ceil(2.0*sp.tangentialHalf*R);
        for(int i=-n;i<=n;i++){double t=i*step,sum=0;int c=0;
            for(int j=-4;j<=4;j++){double u=sp.centreR+j*0.12*sp.radialHalf;double v=img.at(u*er[0]+t*et[0],u*er[1]+t*et[1]);if(Double.isFinite(v)){sum+=v;c++;}}
            s.append(String.format(Locale.US," %d:%.0f",i,c==0?Double.NaN:sum/c));}
        System.out.println(s);
    }

    static void crop(Alpha99MarkerInterference.Sampler img,ModelSpec.Marker sp,Alpha94MarkerMeasurement.Marker m,ModelReference ref,Path file)throws Exception{
        double a=Math.toRadians(sp.hour*30.0);double[] er={Math.sin(a),-Math.cos(a)},et={Math.cos(a),Math.sin(a)};
        double hw=sp.radialHalf*1.5,hh=sp.tangentialHalf*2.6;int W=640,H=(int)Math.round(W*hh/hw);
        BufferedImage bi=new BufferedImage(W,H,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<H;y++)for(int x=0;x<W;x++){
            double u=sp.centreR+(-1+2.0*x/(W-1))*hw*-1,t=(1-2.0*y/(H-1))*hh;   // left = outer end, up = clockwise
            double v=img.at(u*er[0]+t*et[0],u*er[1]+t*et[1]);int g=Double.isFinite(v)?(int)Math.max(0,Math.min(255,Math.round(v))):0;
            bi.setRGB(x,y,(g<<16)|(g<<8)|g);}
        Graphics2D g=bi.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(2f));
        double[][] master=sp.batonPolygon();
        g.setColor(new Color(60,220,90));draw(g,master,sp,er,et,hw,hh,W,H);
        if(m.fitVertices!=null){g.setColor(new Color(255,70,60));draw(g,m.fitVertices,sp,er,et,hw,hh,W,H);}
        g.dispose();javax.imageio.ImageIO.write(bi,"png",file.toFile());
    }
    static void draw(Graphics2D g,double[][] P,ModelSpec.Marker sp,double[] er,double[] et,double hw,double hh,int W,int H){
        int k=P.length;int[] xs=new int[k],ys=new int[k];
        for(int i=0;i<k;i++){double u=P[i][0]*er[0]+P[i][1]*er[1],t=P[i][0]*et[0]+P[i][1]*et[1];
            xs[i]=(int)Math.round((1-(u-sp.centreR)/hw)*(W-1)/2.0);ys[i]=(int)Math.round((1-t/hh)*(H-1)/2.0);}
        g.drawPolygon(xs,ys,k);
    }
}
