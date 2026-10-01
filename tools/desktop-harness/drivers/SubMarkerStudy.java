package com.watchalign.mobile;

import android.graphics.Bitmap;
import android.opengl.*;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Analysis-only harness for the experimental 124060 path. It deliberately does not judge a watch.
 * It runs the existing Sub detector, then applies the already-existing GMT 94%/88% repeatability
 * checks to the shared baton and round-marker analyzers. No production analyzer is changed.
 *
 * Usage: tools/desktop-harness/run.sh SubMarkerStudy <list.txt> <out_dir>
 */
public class SubMarkerStudy {
    private static String q(String s){
        if(s==null)return "";
        return '"'+s.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+'"';
    }
    private static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.6f",v):"";}
    private static String b(boolean v){return v?"true":"false";}
    private static String B(Boolean v){return v==null?"":v.toString();}

    public static void main(String[] a)throws Exception{
        nu.pattern.OpenCV.loadLocally();
        if(a.length<2)throw new IllegalArgumentException("SubMarkerStudy <list.txt> <out_dir>");
        List<String> paths=Files.readAllLines(Path.of(a[0]));
        File out=new File(a[1]);out.mkdirs();
        try(PrintWriter bat=new PrintWriter(new FileWriter(new File(out,"batons.csv")));
            PrintWriter rnd=new PrintWriter(new FileWriter(new File(out,"rounds.csv")));
            PrintWriter pho=new PrintWriter(new FileWriter(new File(out,"photos.csv")))){

            bat.println("path,position,dial_reproducible,twelve_found,status,valid,detector_stable,gap_w,centring_w,rotation_deg,width_px,length_px,tick_score,tick_pitch_deg,ticks_inferred,parallel_deg,stability_run,stability_same_edge,centring_min,centring_max,rotation_min_deg,rotation_max_deg,resample_stable,note");
            rnd.println("path,hour,dial_reproducible,twelve_found,status,found,detector_stable,offset_d,gap_d,inset,size_ratio,diameter_px,tick_score,tick_pitch_deg,ticks_inferred,contrast,angle_from_expected_deg,outer_over_dial_r,stability_run,stability_same_edge,offset_min,offset_max,gap_min,gap_max,inset_min,inset_max,resample_stable,note");
            pho.println("path,orig_w,orig_h,analysis_w,analysis_h,cropped,dial_source,dial_reproducible,twelve_found,batons_found,rounds_found");

            for(String raw:paths){
                String p=raw.trim();if(p.isEmpty())continue;
                Bitmap preview=Load.photo(p);
                if(preview==null)continue;
                FullResSource full=Load.fullSource(p);
                GmtDialCrop.Crop crop=full==null?null:GmtDialCrop.make(preview,full);
                Bitmap target=crop!=null?crop.bitmap:preview;
                Sub124060QcAnalyzer.Result s=Sub124060QcAnalyzer.analyse(target,null);

                int ow=full!=null?full.width():preview.getWidth(), oh=full!=null?full.height():preview.getHeight();
                pho.printf(Locale.US,"%s,%d,%d,%d,%d,%s,%s,%s,%s,%d,%d%n",q(p),ow,oh,target.getWidth(),target.getHeight(),b(crop!=null),s.dialSource,B(s.dialReproducible),b(s.triangle!=null),s.batonsFound(),s.roundsFound());

                Mat src=new Mat();
                try{
                    Utils.bitmapToMat(target,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);

                    for(Sub124060QcAnalyzer.Baton x:s.batons){
                        GmtSixLandmarkAnalyzer.Result r=x.result;
                        if(r!=null&&r.valid&&r.stable&&s.frame!=null)
                            GmtSixLandmarkAnalyzer.measureStability(src,s.frame.cx,s.frame.cy,s.frame.r,r);
                        bat.printf(Locale.US,
                                "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                q(p),x.position.label,B(s.dialReproducible),b(s.triangle!=null),x.status,
                                b(r!=null&&r.valid),b(r!=null&&r.stable),
                                r==null?"":f(r.gap),r==null?"":f(r.centring),r==null?"":f(r.rotationDeg),
                                r==null?"":f(r.widthPx),r==null?"":f(r.lengthPx),r==null?"":f(r.tickScore),
                                r==null?"":f(r.tickPitchDeg),r==null?"":f(r.ticksInferred),r==null?"":f(r.parallelDeg),
                                b(r!=null&&r.stabilityRun),b(r!=null&&r.stabilitySameEdge),
                                r==null?"":f(r.centringMin),r==null?"":f(r.centringMax),r==null?"":f(r.rotMin),r==null?"":f(r.rotMax),
                                b(r!=null&&r.resampleStable()),q(x.note));
                    }

                    List<GmtRoundMarkerAnalyzer.Marker> markers=new ArrayList<>();
                    for(Sub124060QcAnalyzer.Round x:s.rounds)markers.add(x.marker);
                    if(s.frame!=null&&!markers.isEmpty())GmtRoundMarkerAnalyzer.measureStability(src,s.frame,s.tick60,markers);
                    for(Sub124060QcAnalyzer.Round x:s.rounds){
                        GmtRoundMarkerAnalyzer.Marker m=x.marker;
                        rnd.printf(Locale.US,
                                "%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                q(p),m.hour,B(s.dialReproducible),b(s.triangle!=null),x.status,b(m.found),b(m.stable),
                                f(m.offset),f(m.gap),f(m.inset),f(m.sizeRatio),f(m.diameterPx()),f(m.tickScore),f(m.tickPitchDeg),f(m.ticksInferred),
                                f(m.contrast),f(m.angleFromExpectedDeg),f(m.outerOverDialR),b(m.stabilityRun),b(m.stabilitySameEdge),
                                f(m.offMin),f(m.offMax),f(m.gapMin),f(m.gapMax),f(m.insetMin),f(m.insetMax),b(m.resampleStable()),q(x.note));
                    }
                }finally{src.release();}
                bat.flush();rnd.flush();pho.flush();
            }
        }
    }
}
