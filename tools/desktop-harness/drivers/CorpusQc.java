package com.watchalign.mobile;
import android.graphics.Bitmap;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.regex.*;

/**
 * Research-only corpus export (research/corpus-driven-qc-improvement). Read-only instrumentation:
 * runs exactly the production GMT path used by the regression harness (Load.photo, full-resolution
 * GmtDialCrop, GmtHumanQcAnalyzerV2 on the crop) and writes, per image, one JSON line with every
 * landmark and measurement the analyser already produced, in the ANALYSED image's coordinates
 * (the crop when one was made), plus the mapping back to original pixels.
 *
 * Usage: CorpusQc <list.txt> <out.jsonl>
 */
public class CorpusQc{
 static final Pattern SECT=Suit.SECT,GLOB=Suit.GLOB,ELL=Suit.ELL;
 static final Pattern FRAME=Pattern.compile("track roll ([-+\\d.]+)°, pitch ([-\\d.]+)°, frame score (\\d+(?:\\.\\d+)?)");
 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  List<String> paths=Files.readAllLines(Path.of(a[0]));
  try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[1]),"UTF-8"))){
   for(String p:paths){
    p=p.trim();if(p.isEmpty())continue;
    StringBuilder j=new StringBuilder("{");Suit.kv(j,"path",p);
    try{
     long t0=System.nanoTime();
     Bitmap b=Load.photo(p);
     if(b==null){Suit.kv(j,"error","unreadable");out.println(j.append("}"));out.flush();continue;}
     FullResSource fs=Load.fullSource(p);
     GmtDialCrop.Crop crop=GmtDialCrop.make(b,fs);
     Bitmap src=crop!=null?crop.bitmap:b;
     GmtHumanQcAnalyzerV2.Result h=GmtHumanQcAnalyzerV2.analyse(src,"126710BLNR");
     Suit.num(j,"ms",(System.nanoTime()-t0)/1e6);
     Suit.num(j,"orig_w",fs==null?Double.NaN:fs.width());Suit.num(j,"orig_h",fs==null?Double.NaN:fs.height());
     Suit.num(j,"preview_w",b.getWidth());Suit.num(j,"preview_h",b.getHeight());
     Suit.num(j,"frame_w",src.getWidth());Suit.num(j,"frame_h",src.getHeight());
     Suit.bool(j,"crop_used",crop!=null);
     // original = frame/scale + origin (crop) or frame * orig_w/preview_w (no crop)
     if(crop!=null){Suit.num(j,"to_orig_scale",1.0/crop.scale);Suit.num(j,"to_orig_ox",crop.originX);Suit.num(j,"to_orig_oy",crop.originY);}
     else{double k=fs==null?1:fs.width()/(double)b.getWidth();Suit.num(j,"to_orig_scale",k);Suit.num(j,"to_orig_ox",0);Suit.num(j,"to_orig_oy",0);}
     GmtHumanSummary.Input s=h.summary;var d=h.drawing;
     boolean located=d!=null&&Double.isFinite(d.dialCx);
     Suit.bool(j,"dial_located",located);Suit.bool(j,"no_readable_dial",s!=null&&s.noDial);
     Suit.kv(j,"pose",String.valueOf(h.poseLabel));Suit.kv(j,"rotation_att",String.valueOf(h.rotationAttention));
     Suit.kv(j,"clearance_att",String.valueOf(h.clearanceAttention));Suit.num(j,"track_roll_deg",h.localTrackRollDeg);Suit.bool(j,"local_frame_valid",h.localFrameValid);
     if(located){Suit.num(j,"dial_cx",d.dialCx);Suit.num(j,"dial_cy",d.dialCy);Suit.num(j,"dial_a",d.dialA);Suit.num(j,"dial_b",d.dialB);Suit.num(j,"dial_angle_deg",d.dialAngleDeg);}
     if(s!=null){
      Suit.bool(j,"twelve_valid",s.twelveValid);Suit.bool(j,"stable_frame",s.stableFrame);Suit.num(j,"gap",s.observedGap);Suit.kv(j,"gap_att",String.valueOf(s.gap));
      Suit.num(j,"rot_deg",s.rotationDeg);Suit.num(j,"base_tilt_deg",s.baseTiltDeg);Suit.num(j,"sp59",s.spacing59);Suit.num(j,"sp01",s.spacing01);
      Suit.kv(j,"align_att",String.valueOf(s.alignment));Suit.bool(j,"too_small",s.tooSmall);Suit.bool(j,"hand_at_twelve",s.handAtTwelve);
      Suit.bool(j,"gap_res_limited",s.gapResolutionLimited);Suit.num(j,"tri_px",s.trianglePx);
      Suit.bool(j,"stab_run",s.stabilityRun);Suit.bool(j,"stab_same_edge",s.stabilitySameEdge);Suit.num(j,"stab_gap_spread",s.stabilityGapSpread);Suit.num(j,"stab_rot_spread",s.stabilityRotSpreadDeg);
      Suit.bool(j,"six_valid",s.sixValid);Suit.kv(j,"six_att",String.valueOf(s.sixAttention));Suit.num(j,"six_centring",s.sixCentring);Suit.num(j,"six_rot",s.sixRotationDeg);Suit.num(j,"six_w",s.sixWidthPx);
      Suit.bool(j,"six_stable",s.sixStable);Suit.bool(j,"hand_at_six",s.handAtSix);Suit.kv(j,"six_low",s.sixLowReason==null?"":s.sixLowReason);
      Suit.bool(j,"nine_valid",s.nine.valid);Suit.kv(j,"nine_att",String.valueOf(s.nine.attention));Suit.num(j,"nine_centring",s.nine.centring);Suit.num(j,"nine_rot",s.nine.rotationDeg);
      Suit.num(j,"nine_w",s.nine.widthPx);Suit.bool(j,"nine_stable",s.nine.stable);Suit.kv(j,"nine_low",s.nine.lowReason==null?"":s.nine.lowReason);Suit.kv(j,"layout",String.valueOf(s.layout));
     }
     if(d!=null&&d.twelve!=null){var g=d.twelve;pt(j,"tri_l",g.triLeft);pt(j,"tri_r",g.triRight);pt(j,"tri_tip",g.triTip);pt(j,"tick59",g.tick59);pt(j,"tick60",g.tick60);pt(j,"tick01",g.tick01);}
     if(h.six!=null&&h.six.geometry!=null){var g=h.six.geometry;pt(j,"six_ol",g.outerLeft);pt(j,"six_or",g.outerRight);pt(j,"six_il",g.innerLeft);pt(j,"six_ir",g.innerRight);pt(j,"six_tc",g.tickCentre);}
     if(h.nine!=null&&h.nine.geometry!=null){var g=h.nine.geometry;pt(j,"nine_ol",g.outerLeft);pt(j,"nine_or",g.outerRight);pt(j,"nine_il",g.innerLeft);pt(j,"nine_ir",g.innerRight);pt(j,"nine_tc",g.tickCentre);}
     // Round markers (hour, found, centre, size, offsets), with the master layout position.
     StringBuilder m=new StringBuilder("[");
     for(GmtRoundMarkerAnalyzer.Marker r:h.round){
      if(m.length()>1)m.append(',');
      StringBuilder o=new StringBuilder("{");Suit.num(o,"hour",r.hour);Suit.bool(o,"found",r.found);Suit.bool(o,"stable",r.stable);Suit.bool(o,"hand",r.hand);
      Suit.num(o,"x",r.x);Suit.num(o,"y",r.y);Suit.num(o,"r",r.radiusPx);Suit.num(o,"offset",r.offset);Suit.num(o,"gap",r.gap);Suit.num(o,"inset",r.inset);
      Suit.num(o,"size",r.sizeRatio);Suit.num(o,"ang",r.angleFromExpectedDeg);Suit.num(o,"outer_over_dial_r",r.outerOverDialR);Suit.num(o,"contrast",r.contrast);
      Suit.num(o,"tick_score",r.tickScore);Suit.kv(o,"att",String.valueOf(r.attention));Suit.kv(o,"reason",r.reason);
      double[] ms=GmtRoundMarkerAnalyzer.master(r.hour,Gmt126710BlnrMaster.ROUND_CENTER_R);Suit.num(o,"mx",ms[0]);Suit.num(o,"my",ms[1]);
      m.append(o.append("}"));
     }
     j.append(",\"round\":").append(m.append("]"));
     if(located&&h.round!=null&&!h.round.isEmpty()){
      GmtMarkerPose.Result mp=GmtMarkerPose.estimate(h.round,Math.sqrt(d.dialA*d.dialB));
      Suit.bool(j,"mpose_valid",mp.valid);Suit.num(j,"mpose_tilt",mp.tiltDeg);Suit.num(j,"mpose_tilt_high",mp.tiltHighDeg);
      Suit.num(j,"mpose_resid",mp.residual);Suit.num(j,"mpose_squash_sd",mp.squashSd);Suit.num(j,"mpose_n",mp.markers);Suit.kv(j,"mpose_reason",mp.reason);
     }
     String rep=h.report==null?"":h.report;
     Matcher mm=SECT.matcher(rep);
     if(mm.find()){Suit.num(j,"rh_top",Suit.d(mm.group(2)));Suit.num(j,"rh_top_cov",Suit.d(mm.group(3)));Suit.num(j,"rh_right",Suit.d(mm.group(4)));Suit.num(j,"rh_right_cov",Suit.d(mm.group(5)));
      Suit.num(j,"rh_bottom",Suit.d(mm.group(6)));Suit.num(j,"rh_bottom_cov",Suit.d(mm.group(7)));Suit.num(j,"rh_left",Suit.d(mm.group(8)));Suit.num(j,"rh_left_cov",Suit.d(mm.group(9)));
      Suit.num(j,"rh_v",Suit.d(mm.group(10)));Suit.num(j,"rh_h",Suit.d(mm.group(11)));Suit.num(j,"rh_min_mean",Suit.d(mm.group(12)));Suit.kv(j,"rh_seed",mm.group(1));}
     Matcher g=GLOB.matcher(rep);
     if(g.find()){Suit.num(j,"rg_v",Suit.d(g.group(1)));Suit.num(j,"rg_h",Suit.d(g.group(2)));Suit.num(j,"rg_min_mean",Suit.d(g.group(3)));Suit.num(j,"rg_harm",Suit.d(g.group(4)));Suit.num(j,"rg_cov",Suit.d(g.group(5)));Suit.num(j,"rg_resid",Suit.d(g.group(6)));}
     Matcher e=ELL.matcher(rep);
     if(e.find()){Suit.num(j,"ell_ratio",Suit.d(e.group(1)));Suit.num(j,"ell_tilt",Suit.d(e.group(2)));Suit.num(j,"ell_minor_clock",Suit.d(e.group(3)));}
     Matcher f=FRAME.matcher(rep);
     if(f.find()){Suit.num(j,"frame_roll",Suit.d(f.group(1)));Suit.num(j,"frame_pitch",Suit.d(f.group(2)));Suit.num(j,"frame_score",Suit.d(f.group(3)));}
     Suit.kv(j,"pose_reason",poseReason(rep));
    }catch(Throwable t){Suit.kv(j,"error",t.getClass().getSimpleName()+": "+t.getMessage());}
    if(j.charAt(j.length()-1)==',')j.setLength(j.length()-1);
    out.println(j.append("}"));out.flush();
   }
  }
 }
 static String poseReason(String rep){int i=rep.indexOf("Perspective: ");if(i<0)return "";int e=rep.indexOf('\n',i);return rep.substring(i+13,e<0?rep.length():e);}
 static void pt(StringBuilder j,String k,double[] p){if(p==null)return;Suit.num(j,k+"_x",p[0]);Suit.num(j,k+"_y",p[1]);}
}
