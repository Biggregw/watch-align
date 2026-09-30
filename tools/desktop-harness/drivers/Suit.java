package com.watchalign.mobile;
import android.graphics.Bitmap;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.regex.*;

/**
 * Photo suitability for the dataset harvester (tools/dataset_harvester), headless.
 *
 * Usage: Suit <list.txt (one image path per line)> <out.jsonl>
 *
 * Runs the app's own analysis exactly as the regression harness does (Load.photo + Load.human:
 * full-resolution dial crop, GmtHumanQcAnalyzerV2) and reports, per image, only what that analysis
 * already decides or measures: dial found and its ellipse, 12 marker found (or why not), the pose
 * label, the marker-layout tilt (GmtMarkerPose, as the Collect screen's photo check), round markers
 * found, hand/size flags, and the rehaut-sector and dial-ellipse diagnostics the analyser prints in
 * its report. Nothing here changes a QC decision or threshold; it is read-only instrumentation.
 */
public class Suit{
 static final Pattern SECT=Pattern.compile("Local rehaut sectors \\(([^)]*)\\): 12 ([-\\d.]+) px \\(([-\\d.]+) cov\\), 3 ([-\\d.]+) \\(([-\\d.]+)\\), 6 ([-\\d.]+) \\(([-\\d.]+)\\), 9 ([-\\d.]+) \\(([-\\d.]+)\\); V ([-+\\d.]+), H ([-+\\d.]+), min/mean ([-\\d.]+)");
 static final Pattern GLOB=Pattern.compile("Global rehaut diagnostic: V ([-+\\d.]+), H ([-+\\d.]+); minimum/mean ([-\\d.]+); harmonic ([-\\d.]+); coverage ([-\\d.]+); fit residual ([-\\d.]+)");
 static final Pattern ELL=Pattern.compile("Planar cue: ellipse ratio ([-\\d.]+), equivalent tilt ([-\\d.]+)°, minor axis ([-\\d.]+)° clock");

 public static void main(String[] a)throws Exception{
  nu.pattern.OpenCV.loadLocally();
  List<String> paths=Files.readAllLines(Path.of(a[0]));
  try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a[1]),"UTF-8"))){
   for(String p:paths){
    p=p.trim();if(p.isEmpty())continue;
    StringBuilder j=new StringBuilder("{");kv(j,"path",p);
    try{
     Bitmap b=Load.photo(p);
     if(b==null){kv(j,"error","unreadable");out.println(j.append("}"));out.flush();continue;}
     FullResSource fs=Load.fullSource(p);
     num(j,"preview_w",b.getWidth());num(j,"preview_h",b.getHeight());
     num(j,"orig_w",fs==null?Double.NaN:fs.width());num(j,"orig_h",fs==null?Double.NaN:fs.height());
     Load.Human hu=Load.human(b,p);GmtHumanQcAnalyzerV2.Result h=hu.h;
     GmtHumanSummary.Input s=h.summary;
     // located: the analyser fitted a dial; no_readable_dial: its summary judged the dial's landmarks
     // unreadable (the phone's photo check calls both cases "no dial found").
     boolean located=h.drawing!=null&&Double.isFinite(h.drawing.dialCx);
     boolean noDial=s==null||s.noDial||!located;
     bool(j,"dial_found",!noDial);bool(j,"dial_located",located);bool(j,"no_readable_dial",s!=null&&s.noDial);bool(j,"crop_used",hu.crop!=null);
     if(located){
      num(j,"dial_cx",h.drawing.dialCx);num(j,"dial_cy",h.drawing.dialCy);num(j,"dial_a",h.drawing.dialA);num(j,"dial_b",h.drawing.dialB);num(j,"dial_angle_deg",h.drawing.dialAngleDeg);
     }
     boolean tw=!noDial&&h.drawing.twelve!=null;
     bool(j,"twelve_found",tw);
     kv(j,"twelve_not_judged",noDial||h.drawing.notJudged==null?"":h.drawing.notJudged);
     kv(j,"pose",String.valueOf(h.poseLabel));
     if(s!=null){bool(j,"too_small",s.tooSmall);bool(j,"hand_at_twelve",s.handAtTwelve);bool(j,"stable_frame",s.stableFrame);bool(j,"gap_resolution_limited",s.gapResolutionLimited);num(j,"triangle_px",s.trianglePx);}
     int found=0,total=0;for(GmtRoundMarkerAnalyzer.Marker m:h.round){total++;if(m.found)found++;}
     num(j,"round_found",found);num(j,"round_total",total);
     if(located&&h.round!=null&&!h.round.isEmpty()){
      GmtMarkerPose.Result mp=GmtMarkerPose.estimate(h.round,Math.sqrt(h.drawing.dialA*h.drawing.dialB));
      bool(j,"marker_pose_valid",mp.valid);if(mp.valid)num(j,"marker_tilt_deg",mp.tiltDeg);
     }
     String rep=h.report==null?"":h.report;
     Matcher m=SECT.matcher(rep);
     if(m.find()){
      kv(j,"rehaut_seed",m.group(1));
      num(j,"rehaut_top_px",d(m.group(2)));num(j,"rehaut_top_cov",d(m.group(3)));
      num(j,"rehaut_right_px",d(m.group(4)));num(j,"rehaut_right_cov",d(m.group(5)));
      num(j,"rehaut_bottom_px",d(m.group(6)));num(j,"rehaut_bottom_cov",d(m.group(7)));
      num(j,"rehaut_left_px",d(m.group(8)));num(j,"rehaut_left_cov",d(m.group(9)));
      num(j,"rehaut_v_asym",d(m.group(10)));num(j,"rehaut_h_asym",d(m.group(11)));num(j,"rehaut_min_over_mean",d(m.group(12)));
     }
     Matcher g=GLOB.matcher(rep);
     if(g.find()){num(j,"rehaut_global_v",d(g.group(1)));num(j,"rehaut_global_h",d(g.group(2)));num(j,"rehaut_global_harmonic",d(g.group(4)));num(j,"rehaut_global_coverage",d(g.group(5)));num(j,"rehaut_global_residual",d(g.group(6)));}
     Matcher e=ELL.matcher(rep);
     if(e.find()){num(j,"ellipse_ratio",d(e.group(1)));num(j,"ellipse_tilt_deg",d(e.group(2)));num(j,"ellipse_minor_axis_clock_deg",d(e.group(3)));}
    }catch(Throwable t){kv(j,"error",t.getClass().getSimpleName()+": "+t.getMessage());}
    if(j.charAt(j.length()-1)==',')j.setLength(j.length()-1);
    out.println(j.append("}"));out.flush();
   }
  }
 }
 static double d(String s){try{return Double.parseDouble(s);}catch(Exception e){return Double.NaN;}}
 static void kv(StringBuilder j,String k,String v){
  if(j.length()>1&&j.charAt(j.length()-1)!=',')j.append(',');
  j.append('"').append(k).append("\":\"");
  for(char c:(v==null?"":v).toCharArray()){if(c=='"'||c=='\\')j.append('\\').append(c);else if(c<0x20)j.append(' ');else j.append(c);}
  j.append('"');
 }
 static void num(StringBuilder j,String k,double v){
  if(j.length()>1&&j.charAt(j.length()-1)!=',')j.append(',');
  j.append('"').append(k).append("\":").append(Double.isFinite(v)?String.format(Locale.US,"%.6g",v):"null");
 }
 static void bool(StringBuilder j,String k,boolean v){
  if(j.length()>1&&j.charAt(j.length()-1)!=',')j.append(',');
  j.append('"').append(k).append("\":").append(v);
 }
}
