package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * REVIEW ONLY (outer-metal inspection). For each photo, through the unchanged Alpha99Pipeline, one sheet with every
 * individual marker in three panels: (1) the raw perspective-corrected crop; (2) the same crop with the detector's
 * scored outline: cyan = the span of each independently fitted side that was actually sampled (12-88% of the master
 * side), red = corners/extensions inferred by intersecting adjacent fitted lines; (3) the app's own close-up
 * (Alpha98Closeups.forFinding with the stored scoring comparison: yellow reference, orange extra, magenta missing).
 * The polygon in (2) is the exact actualPolygon used for the percentage. Images go only to the given folder.
 *
 * Usage: run.sh OuterMetalReview manifest.csv image_root out_dir [photo_id,photo_id,...]
 */
public class OuterMetalReview {
    static final int P=300;

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] cols=lines.get(0).split(",",-1);
        int ip=-1,iid=-1;for(int i=0;i<cols.length;i++){if(cols[i].equals("local_path")||(ip<0&&cols[i].equals("path")))ip=i;if(cols[i].equals("photo_id"))iid=i;}
        Set<String> only=a.length>3?Set.of(a[3].split(",")):null;
        Path out=Path.of(a[2]);Files.createDirectories(out);
        ModelSpec model=HarnessModel.spec();ModelReference ref=HarnessModel.ref();
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);if(f.length<=Math.max(ip,iid))continue;String id=f[iid];
            if(only!=null&&!only.contains(id))continue;
            Path file=Path.of(a[1]).resolve(f[ip]);if(!Files.exists(file))continue;
            Bitmap b=Alpha96Calib.loadAlpha96(file.toString());
            Alpha99Pipeline.Output o=b==null?null:Alpha99Pipeline.run(b,model,ref);
            if(o==null||!o.ok()){System.out.println("SHEET "+id+": no pose");continue;}
            double R=o.measurement.dialRadiusPx;double[] H=o.pose.homography;
            double[] ring=o.measurement.ring!=null&&o.measurement.ring.usable?o.measurement.ring.model:null;
            Mat rgba=new Mat();Utils.bitmapToMat(b,rgba);
            List<BufferedImage> cells=new ArrayList<>();
            for(Alpha99Findings.Finding g:o.summary.all){
                ModelSpec.Marker spec=g.dialWide?null:model.atHour(g.hour);if(spec==null)continue;
                MarkerMismatchResearch.Result q=o.mismatch.get(g.hour);
                Alpha94MarkerMeasurement.Marker m=spec.shape==ModelSpec.Shape.TRIANGLE?o.measurement.triangle:o.measurement.atHour(g.hour);
                Mat raw=Alpha98Closeups.render(rgba,H,g.cx,g.cy,g.half);
                Bitmap rb=Bitmap.createBitmap(Alpha98Closeups.SIZE,Alpha98Closeups.SIZE,Bitmap.Config.ARGB_8888);Utils.matToBitmap(raw,rb);raw.release();
                Bitmap fit=rb.copy(Bitmap.Config.ARGB_8888,true);
                double[] T=MarkerMismatchOverlay.closeupTransform(Alpha98Closeups.SIZE,g.cx,g.cy,g.half);
                double[][] actual=q!=null&&q.score!=null?q.score.actualPolygon:(m!=null&&m.usable?MarkerMismatchResearch.actual(spec,m,R):null);
                drawFit(fit.img,actual,spec,T,m);
                Alpha94MarkerMeasurement.Marker measured=g.shape==Alpha99Findings.Shape.BATON?m:null;
                Bitmap app=Alpha98Closeups.forFinding(rgba,H,g,o.date,ring,model,ref,measured,q);
                String pct=q==null?"no comparison":q.score==null?"area: "+q.reason:String.format(Locale.US,"%s %.2f%% (+%.2f / -%.2f)%s",q.trusted()?"area":"WITHHELD",q.score.mismatchPct,q.score.extraPct,q.score.missingPct,q.trusted()?"":" "+q.reason);
                String cov=m!=null&&m.sideCov!=null?" cov "+fmt(m.sideCov):m!=null&&"round".equals(m.kind)&&m.usable?String.format(Locale.US," edge %.2f",m.fitSupport):"";
                cells.add(cell(rb.img,fit.img,app.img,g.title+" | "+g.status+(m!=null&&!m.usable?" ("+m.reason+")":"")+cov,pct));
                System.out.printf(Locale.US,"MARKER %s %d %s %s | %s%s%n",id,g.hour,spec.shape,g.status,pct,cov);
            }
            rgba.release();
            int cols2=2,rows=(cells.size()+1)/2;BufferedImage sheet=new BufferedImage(cols2*3*P,rows*(P+36),BufferedImage.TYPE_INT_RGB);
            Graphics2D gg=sheet.createGraphics();
            for(int i=0;i<cells.size();i++)gg.drawImage(cells.get(i),(i%2)*3*P,(i/2)*(P+36),null);
            gg.dispose();javax.imageio.ImageIO.write(sheet,"png",out.resolve(id+".png").toFile());
            System.out.println("SHEET "+id+" "+cells.size()+" markers");
        }
    }

    static String fmt(double[] v){StringBuilder s=new StringBuilder();for(double x:v)s.append(String.format(Locale.US,"%s%.2f",s.length()==0?"":"/",x));return s.toString();}

    /** Observed spans (cyan) vs inferred corners/extensions (red) of the exact scored polygon. */
    static void drawFit(BufferedImage img,double[][] V,ModelSpec.Marker spec,double[] T,Alpha94MarkerMeasurement.Marker m){
        if(V==null)return;
        Graphics2D g=img.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.5f));g.setColor(new Color(255,60,60));
        Path2D.Double p=new Path2D.Double();for(int i=0;i<V.length;i++){double[] q=t(T,V[i]);if(i==0)p.moveTo(q[0],q[1]);else p.lineTo(q[0],q[1]);}p.closePath();g.draw(p);
        g.setStroke(new BasicStroke(2.5f));g.setColor(new Color(40,220,255));
        if(spec.shape==ModelSpec.Shape.ROUND){g.draw(p);}
        else{
            // side i of the fitted polygon runs V[i] -> V[i+1]; the detector sampled master side i at u = 0.12..0.88
            double[][] M=spec.shape==ModelSpec.Shape.TRIANGLE?spec.trianglePolygon():spec.batonPolygon();
            int k=V.length;
            for(int i=0;i<k;i++){
                if(m!=null&&m.sideCov!=null&&m.sideCov.length==k&&m.sideCov[i]<=0)continue;
                double[] a0=V[i],a1=V[(i+1)%k],m0=M[i%M.length],m1=M[(i+1)%M.length];
                double[] s0=proj(a0,a1,lerp(m0,m1,.12)),s1=proj(a0,a1,lerp(m0,m1,.88));
                double[] u=t(T,s0),w=t(T,s1);g.draw(new java.awt.geom.Line2D.Double(u[0],u[1],w[0],w[1]));
            }
        }
        g.dispose();
    }
    static double[] lerp(double[] a,double[] b,double u){return new double[]{a[0]+u*(b[0]-a[0]),a[1]+u*(b[1]-a[1])};}
    static double[] proj(double[] a,double[] b,double[] p){double dx=b[0]-a[0],dy=b[1]-a[1],t=((p[0]-a[0])*dx+(p[1]-a[1])*dy)/(dx*dx+dy*dy);return new double[]{a[0]+t*dx,a[1]+t*dy};}
    static double[] t(double[] T,double[] p){return new double[]{T[0]*p[0]+T[1]*p[1]+T[2],T[3]*p[0]+T[4]*p[1]+T[5]};}

    static BufferedImage cell(BufferedImage raw,BufferedImage fit,BufferedImage app,String l1,String l2){
        BufferedImage c=new BufferedImage(3*P,P+36,BufferedImage.TYPE_INT_RGB);Graphics2D g=c.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(raw,0,0,P,P,null);g.drawImage(fit,P,0,P,P,null);g.drawImage(app,2*P,0,P,P,null);
        g.setColor(Color.YELLOW);g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,12));g.drawString(l1,4,P+14);g.drawString(l2,4,P+30);
        g.setColor(Color.DARK_GRAY);g.drawRect(0,0,3*P-1,P+35);g.dispose();return c;
    }
}
