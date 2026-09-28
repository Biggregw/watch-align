package com.watchalign.mobile;
import android.graphics.Bitmap;
import javax.imageio.ImageIO;import java.io.*;import java.nio.file.*;import java.util.*;
public class Batch{
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  int nsh=a.length>4?Integer.parseInt(a[4]):1,ksh=a.length>5?Integer.parseInt(a[5]):0;
  List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",");
  int iPath=-1,iCls=-1,iWid=-1,iFac=-1,iSplit=-1;
  for(int i=0;i<hdr.length;i++){String h=hdr[i].trim();if(h.equals("local_path")||h.equals("path")||h.equals("image_path"))iPath=i;if(h.equals("class_label"))iCls=i;if(h.equals("physical_watch_id"))iWid=i;if(h.equals("factory"))iFac=i;if(h.equals("split"))iSplit=i;}
  PrintWriter out=new PrintWriter(new FileWriter(a[2]));
  PrintWriter rnd=new PrintWriter(new FileWriter(a[2].replace(".csv","_round.csv")));
  rnd.println("path,class,watch,pose,twelve,hour,found,reason,stable,low,d_px,r_over_dial,offset,off_t,gap,inset,size,rej,contrast,ang,tick_score,pitch,inferred,stab_run,stab_same,off_min,off_max,gap_min,gap_max,ins_min,ins_max,att,note,hand,ring,col,unstable");
  if(ksh==0)out.println("path,class,watch,factory,split,twelve,stable,pose,gap,gap_att,res_limited,tri_px,rot,sp59,sp01,align_att,overlay,too_small,hand,six_valid,six_att,six_centring,six_rot,six_w,six_stable,stab_run,stab_same,stab_dgap,stab_drot,six_low,six_par,six_score,six_pitch,six_inf,nine_valid,nine_att,nine_c,nine_r,nine_w,nine_stable,nine_low,side_label,layout,no_dial");
  Path base=Path.of(a[1]);new File(a[3]).mkdirs();
  for(int li=1;li<lines.size();li++){
   if(li%nsh!=ksh)continue;
   String[] f=lines.get(li).split(",",-1);String rel=f[iPath];File img=base.resolve(rel).toFile();if(!img.exists())continue;
   try{
    Bitmap b=Load.photo(img.getPath());if(b==null)continue;
    Load.Human hu=Load.human(b,img.getPath());GmtHumanQcAnalyzerV2.Result h=hu.h;
    GmtHumanSummary.Input s=h.summary;boolean tw=h.drawing!=null&&h.drawing.twelve!=null;
    double triPx=tw?Math.hypot(h.drawing.twelve.triRight[0]-h.drawing.twelve.triLeft[0],h.drawing.twelve.triRight[1]-h.drawing.twelve.triLeft[1]):Double.NaN;
    String ov="";
    if(tw){Bitmap m=MeasuredOverlayRenderer.render(b,h.drawing);
     var g=h.drawing.twelve;double cx=(g.triLeft[0]+g.triRight[0])/2,cy=(g.triLeft[1]+g.triRight[1])/2+triPx*0.35;int half=(int)Math.round(triPx*1.6);
     java.awt.image.BufferedImage comp=new java.awt.image.BufferedImage(2*half,2*half,java.awt.image.BufferedImage.TYPE_INT_RGB);
     java.awt.Graphics2D g2=comp.createGraphics();g2.drawImage(b.img,-(int)(cx-half),-(int)(cy-half),null);g2.drawImage(m.img,-(int)(cx-half),-(int)(cy-half),null);g2.dispose();
     ov=a[3]+"/"+li+".png";ImageIO.write(comp,"png",new File(ov));}
    String six=Six.fields(s)+String.format(Locale.US,",%s,%s,%.4f,%.2f",s.stabilityRun,s.stabilitySameEdge,s.stabilityGapSpread,s.stabilityRotSpreadDeg)
      +String.format(Locale.US,",%s,%.2f,%.1f,%.2f,%.0f",(s.sixLowReason==null?"":s.sixLowReason).replace(',',';'),h.six==null?Double.NaN:h.six.parallelDeg,h.six==null?Double.NaN:h.six.tickScore,h.six==null?Double.NaN:h.six.tickPitchDeg,h.six==null?Double.NaN:h.six.ticksInferred)
      +String.format(Locale.US,",%s,%s,%.3f,%.2f,%.1f,%s,%s",s.nine.valid,s.nine.attention,s.nine.centring,s.nine.rotationDeg,s.nine.widthPx,s.nine.stable,(s.nine.lowReason==null?"":s.nine.lowReason).replace(',',';'))+","+s.nine.label+","+s.layout+","+s.noDial;
    out.printf(Locale.US,"%s,%s,%s,%s,%s,%s,%s,%s,%.4f,%s,%s,%.1f,%.2f,%.3f,%.3f,%s,%s,%s,%s%s%n",rel,f[iCls],f[iWid],iFac>=0?f[iFac]:"",iSplit>=0?f[iSplit]:"",tw,s.stableFrame,s.pose,s.observedGap,s.gap,s.gapResolutionLimited,triPx,s.rotationDeg,s.spacing59,s.spacing01,s.alignment,ov,s.tooSmall,s.handAtTwelve,six);
    out.flush();
    for(GmtRoundMarkerAnalyzer.Marker m:h.round){
     rnd.printf(Locale.US,"%s,%s,%s,%s,%s,%d,%s,%s,%s,%s,%.1f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.3f,%.0f,%.2f,%.0f,%.2f,%.0f,%s,%s,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%s,%s,%s,%.4f,%.4f,%s%n",rel,f[iCls],f[iWid],s.pose,tw,m.hour,m.found,m.reason.replace(',',';'),m.stable,m.lowReason.replace(',',';'),
      m.diameterPx(),m.outerOverDialR,m.offset,m.offsetFromCentreTick,m.gap,m.inset,m.sizeRatio,m.rejectFraction,m.contrast,m.angleFromExpectedDeg,m.tickScore,m.tickPitchDeg,m.ticksInferred,
      m.stabilityRun,m.stabilitySameEdge,m.offMin,m.offMax,m.gapMin,m.gapMax,m.insetMin,m.insetMax,m.attention,(m.note==null?"":m.note).replace(',',';'),m.hand,m.ringBright,m.coloured,m.unstable);
    }
    rnd.flush();
   }catch(Throwable t){out.printf("%s,%s,%s,,,ERROR %s%n",rel,f[iCls],f[iWid],t.getClass().getSimpleName());out.flush();}
  }
  out.close();rnd.close();
 }}
