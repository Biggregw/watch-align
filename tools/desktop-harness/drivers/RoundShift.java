package com.watchalign.mobile;
import android.graphics.Bitmap;import java.util.*;import java.awt.image.BufferedImage;
/** RoundShift <image> <hour> <shift as fraction of diameter, + clockwise> [scale]: moves one marker and re-runs (alpha61 check). */
public class RoundShift{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  Bitmap b=Load.photo(a[0]);int hour=Integer.parseInt(a[1]);double sh=Double.parseDouble(a[2]),sc=a.length>3?Double.parseDouble(a[3]):1.0;
  GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");
  GmtRoundMarkerAnalyzer.Marker m=null;for(var x:h.round)if(x.hour==hour)m=x;
  System.out.printf(Locale.US,"before: %d %s off %+.3f size %.3f d %.1f  C %.1f,%.1f B %.1f,%.1f A %.1f,%.1f%n",hour,m.attention,m.offset,m.sizeRatio,m.diameterPx(),m.x,m.y,m.tickBefore[0],m.tickBefore[1],m.tickAfter[0],m.tickAfter[1]);
  double ux=m.tickAfter[0]-m.tickBefore[0],uy=m.tickAfter[1]-m.tickBefore[1],un=Math.hypot(ux,uy);ux/=un;uy/=un;
  double d=m.diameterPx(),dx=ux*sh*d,dy=uy*sh*d,R=m.radiusPx+2;
  BufferedImage src=b.img,img=new BufferedImage(src.getWidth(),src.getHeight(),BufferedImage.TYPE_INT_ARGB);
  img.getGraphics().drawImage(src,0,0,null);
  // dial colour: median of a ring just outside
  List<Integer> ring=new ArrayList<>();
  for(int t=0;t<360;t+=3){int x=(int)Math.round(m.x+1.5*R*Math.cos(Math.toRadians(t))),y=(int)Math.round(m.y+1.5*R*Math.sin(Math.toRadians(t)));ring.add(src.getRGB(x,y));}
  ring.sort(Comparator.comparingInt(c->((c>>16)&255)+((c>>8)&255)+(c&255)));int dial=ring.get(ring.size()/3);
  for(int y=(int)(m.y-R*sc-Math.abs(dy)-3);y<=m.y+R*sc+Math.abs(dy)+3;y++)for(int x=(int)(m.x-R*sc-Math.abs(dx)-3);x<=m.x+R*sc+Math.abs(dx)+3;x++)
   if(Math.hypot(x-m.x,y-m.y)<=R*Math.max(1,sc)+1)img.setRGB(x,y,dial);
  for(int y=(int)(m.y+dy-R*sc-2);y<=m.y+dy+R*sc+2;y++)for(int x=(int)(m.x+dx-R*sc-2);x<=m.x+dx+R*sc+2;x++){
   double rx=(x-m.x-dx)/sc,ry=(y-m.y-dy)/sc;if(Math.hypot(rx,ry)>R)continue;
   img.setRGB(x,y,src.getRGB((int)Math.round(m.x+rx),(int)Math.round(m.y+ry)));}
  b=new Bitmap(img);
  if(System.getProperty("wa.out")!=null){BufferedImage o=new BufferedImage(img.getWidth(),img.getHeight(),BufferedImage.TYPE_INT_RGB);o.getGraphics().drawImage(img,0,0,null);javax.imageio.ImageIO.write(o,"png",new java.io.File(System.getProperty("wa.out")));}
  GmtHumanQcAnalyzerV2.Result h2=GmtHumanQcAnalyzerV2.analyse(b,"126710BLNR");
  for(var x:h2.round)if(x.hour==hour)System.out.printf(Locale.US,"after:  %d %s off %+.3f size %.3f d %.1f %s  C %.1f,%.1f B %.1f,%.1f A %.1f,%.1f  shift %.1f,%.1f%n",hour,x.attention,x.offset,x.sizeRatio,x.diameterPx(),x.note,x.x,x.y,x.tickBefore[0],x.tickBefore[1],x.tickAfter[0],x.tickAfter[1],dx,dy);
  String rep=h2.summary==null?"":GmtHumanSummary.build(h2.summary);
  for(String l:rep.split("\n"))if(l.startsWith("Round")||l.startsWith("Bottom"))System.out.println(l);
 }}
