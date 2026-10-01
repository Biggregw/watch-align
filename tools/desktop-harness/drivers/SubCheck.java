package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.*;import java.nio.file.*;import java.util.*;

/**
 * Runs the app's experimental Submariner 124060 route (WatchAlignCoreV13 with model "124060") on
 * desktop, exactly as the phone does (preview + full-resolution dial crop).
 *   tools/desktop-harness/run.sh SubCheck <list.txt (one image path per line)> <out_dir>
 * Writes out_dir/subcheck.csv, one overlay PNG and one report TXT per photo.
 *
 * The shared_* columns are diagnostics only: the selected Sub triangle and its already-detected
 * raw 59/60/01 tick ends are measured with TwelveLocalGeometry, after undoing the fitted dial
 * ellipse. They do not feed the app's current Sub values or any verdict.
 */
public class SubCheck{
 static TwelveLocalGeometry.Result shared(Sub124060QcAnalyzer.Result s){
  if(s==null||s.triangle==null||s.frame==null)return new TwelveLocalGeometry.Result("triangle unavailable");
  SubTwelveTriangle.Cand c=s.triangle;
  if(c.tickRaw59==null||c.tickRaw60==null||c.tickRaw01==null)return new TwelveLocalGeometry.Result("raw 59/60/01 unavailable");
  GmtRoundMarkerAnalyzer.DialFrame f=s.frame;
  double[] l=f.rect(c.L[0],c.L[1]),r=f.rect(c.R[0],c.R[1]),t=f.rect(c.T[0],c.T[1]);
  double[] p59=f.rect(c.tickRaw59[0],c.tickRaw59[1]);
  double[] p60=f.rect(c.tickRaw60[0],c.tickRaw60[1]);
  double[] p01=f.rect(c.tickRaw01[0],c.tickRaw01[1]);
  return TwelveLocalGeometry.measure(l,r,t,p59,p60,p01,0,0);
 }
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  List<String> paths=Files.readAllLines(Path.of(a[0]));File out=new File(a[1]);out.mkdirs();
  try(PrintWriter w=new PrintWriter(new FileWriter(new File(out,"subcheck.csv")))){
   w.println("path,orig_w,preview_w,dial_source,dial_reproducible,triangle,tri_x_orig,tri_y_orig,outline,withheld,rotation_deg,gap_r,centring_w,shared_valid,shared_rotation_deg,shared_gap_w,shared_centring_w,shared_base_deg,shared_side_asym,shared_axis_disagree_deg,batons_found,rounds_found,ms");
   int i=0;
   for(String p:paths){
    p=p.trim();if(p.isEmpty())continue;i++;
    long t0=System.currentTimeMillis();
    Bitmap b=Load.photo(p);if(b==null){w.println(p+",,,unreadable");continue;}
    FullResSource full=Load.fullSource(p);
    WatchAlignCoreV13.AnalysisResult r=WatchAlignCoreV13.analyse(b,Collections.<Bitmap>emptyList(),"124060",full);
    long ms=System.currentTimeMillis()-t0;
    Sub124060QcAnalyzer.Result s=r.sub124060;Sub124060Overlay.Drawing d=s.drawing;
    TwelveLocalGeometry.Result q=shared(s);
    double k=full.width()/(double)b.getWidth();
    double tx=Double.NaN,ty=Double.NaN;
    if(d.hasTriangle()){tx=(d.triL[0]+d.triR[0]+d.triT[0])/3*k;ty=(d.triL[1]+d.triR[1]+d.triT[1])/3*k;}
    w.printf(Locale.US,"%s,%d,%d,%s,%s,%s,%.1f,%.1f,%s,%s,%.3f,%.4f,%.4f,%s,%.3f,%.4f,%.4f,%.3f,%.4f,%.3f,%d,%d,%d%n",p,full.width(),b.getWidth(),s.dialSource,s.dialReproducible,s.triangle!=null,tx,ty,
      s.triangle!=null?s.triangle.outline:"",s.twelveWithheld==null?"":s.twelveWithheld.replace(',',';'),s.rotationDeg,s.gapR,s.centringW,q.valid,q.rotationDeg,q.gapOverWidth,q.centringOverWidth,q.baseEdgeDeg,q.sideAsymmetry,q.axisReferenceDisagreementDeg,s.batonsFound(),s.roundsFound(),ms);
    w.flush();
    String stem=String.format("%03d",i);
    if(r.perspectiveOverlay!=null){
     java.awt.image.BufferedImage comp=new java.awt.image.BufferedImage(b.getWidth(),b.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);
     java.awt.Graphics2D g=comp.createGraphics();g.drawImage(b.img,0,0,null);g.drawImage(r.perspectiveOverlay.img,0,0,null);g.dispose();
     ImageIO.write(comp,"jpg",new File(out,stem+".jpg"));
    }
    if(r.twelveCloseUp!=null)ImageIO.write(r.twelveCloseUp.img,"png",new File(out,stem+"_12.png"));
    Files.writeString(new File(out,stem+".txt").toPath(),p+"\n\n"+r.report);
   }
  }
 }
}
