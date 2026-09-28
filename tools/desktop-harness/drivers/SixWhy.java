package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;
/** SixWhy <image>...: the 6/9 baton outcome and reason (alpha61). */
public class SixWhy{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  for(String p:a){
   Bitmap b=Load.photo(p);Load.Human hu=Load.human(b,p);var h=hu.h;
   String rep=h.report;
   String six=line(rep,"6 baton: "),nine=line(rep,"9 baton: ");
   System.out.println("== "+p.substring(p.lastIndexOf('/')+1)+"\n   6: "+six+"\n   "+line(rep,"6 geometry")+"\n   "+line(rep,"6 fit detail")+"\n   9: "+nine);
  }
 }
 static String line(String r,String k){int i=r.indexOf(k);if(i<0)return "-";int e=r.indexOf('\n',i);return r.substring(i,e<0?r.length():e);}
}
