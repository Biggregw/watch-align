package com.watchalign.mobile;
import android.graphics.Bitmap;
import java.nio.file.*;import java.util.*;

/**
 * The app's whole analysis entry point (WatchAlignCoreV13.analyse, as MainActivity calls it) for a
 * model and a list of photos: prints each report plus a hash of the overlay and close-up pixels, so
 * two builds can be compared exactly (e.g. GMT before and after a change).
 *   tools/desktop-harness/run.sh CoreReport <model> <list.txt> > out.txt
 */
public class CoreReport{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String p:Files.readAllLines(Path.of(a[1]))){
   p=p.trim();if(p.isEmpty())continue;
   Bitmap b=Load.photo(p);if(b==null)continue;
   System.out.println("##### "+p);
   WatchAlignCoreV13.AnalysisResult r;
   try{r=WatchAlignCoreV13.analyse(b,Collections.<Bitmap>emptyList(),a[0],Load.fullSource(p));}
   catch(Throwable t){System.out.println("threw "+t.getClass().getSimpleName()+": "+t.getMessage());continue;}
   System.out.println("overlay "+hash(r.perspectiveOverlay)+" closeup "+hash(r.twelveCloseUp)+" twelve "+r.twelveMeasured);
   System.out.println(r.report);
  }
 }
 static String hash(Bitmap b){
  if(b==null)return "none";
  int w=b.getWidth(),h=b.getHeight();int[] px=new int[w*h];b.getPixels(px,0,w,0,0,w,h);
  long x=1125899906842597L;for(int v:px)x=31*x+v;return w+"x"+h+":"+Long.toHexString(x);
 }
}
