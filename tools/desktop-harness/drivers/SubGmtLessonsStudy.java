package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Development-only diagnostic harness for reusing the mature GMT reliability framework on 124060.
 * No QC verdicts or thresholds are created here and no production analyzer is changed.
 *
 * It records:
 *  - 100/94/88 numeric repeatability for the existing Sub triangle measurements;
 *  - existing GMT resize stability for shared baton and round-marker analyzers;
 *  - Sub radial/chord/base-edge agreement as independent confidence evidence;
 *  - the existing GMT round-marker pose estimator on the Sub marker layout.
 *
 * Usage: tools/desktop-harness/run.sh SubGmtLessonsStudy <list.txt> <out_dir>
 */
public final class SubGmtLessonsStudy {
    private static final double[] SCALES={0.94,0.88};

    private static String q(String s){
        if(s==null)return "";
        return '"'+s.replace("\"","\"\"").replace("\r"," ").replace("\n"," ")+'"';
    }
    private static String f(double v){return Double.isFinite(v)?String.format(Locale.US,"%.8f",v):"";}
    private static String b(boolean v){return v?"true":"false";}
    private static String B(Boolean v){return v==null?"":v.toString();}
    private static double adiff(double a,double c){
        if(!Double.isFinite(a)||!Double.isFinite(c))return Double.NaN;
        return Math.abs(SubTwelveTriangle.wrap90(a-c));
    }

    private static final class TriRepeat {
        final SubTwelveTriangle.Cand original;
        final SubTwelveTriangle.Cand[] scaled=new SubTwelveTriangle.Cand[2];
        final boolean[] same={false,false};
        double gapMin=Double.NaN,gapMax=Double.NaN,rotMin=Double.NaN,rotMax=Double.NaN,cenMin=Double.NaN,cenMax=Double.NaN;
        double gapSpreadR=Double.NaN,gapSpreadPx=Double.NaN,rotSpreadDeg=Double.NaN,rotTipTravelPx=Double.NaN,cenSpreadW=Double.NaN,cenSpreadPx=Double.NaN;
        boolean allSame;
        TriRepeat(SubTwelveTriangle.Cand c){original=c;}
    }

    private static TriRepeat triangleRepeat(Mat src,Sub124060QcAnalyzer.Result s){
        if(s==null||s.triangle==null||s.frame==null)return null;
        SubTwelveTriangle.Cand c=s.triangle;
        TriRepeat o=new TriRepeat(c);
        double gMin=c.gapR,gMax=c.gapR,rMin=c.rotationDeg,rMax=c.rotationDeg,cMin=c.centring,cMax=c.centring;
        boolean all=true;
        for(int i=0;i<SCALES.length;i++){
            double k=SCALES[i];Mat m=new Mat();
            try{
                Imgproc.resize(src,m,new org.opencv.core.Size(Math.round(src.cols()*k),Math.round(src.rows()*k)),0,0,Imgproc.INTER_LINEAR);
                SubTwelveTriangle.Cand x=SubTwelveTriangle.detect(m,s.frame.scaled(k)).best();
                o.scaled[i]=x;
                boolean same=x!=null&&Sub124060QcAnalyzer.sameOutline(c.cx,c.cy,c.widthR,x.cx/k,x.cy/k,x.widthR,s.frame.r);
                o.same[i]=same;all&=same;
                if(same){
                    gMin=Math.min(gMin,x.gapR);gMax=Math.max(gMax,x.gapR);
                    rMin=Math.min(rMin,x.rotationDeg);rMax=Math.max(rMax,x.rotationDeg);
                    cMin=Math.min(cMin,x.centring);cMax=Math.max(cMax,x.centring);
                }
            }finally{m.release();}
        }
        o.allSame=all;
        if(all){
            o.gapMin=gMin;o.gapMax=gMax;o.rotMin=rMin;o.rotMax=rMax;o.cenMin=cMin;o.cenMax=cMax;
            o.gapSpreadR=gMax-gMin;o.gapSpreadPx=o.gapSpreadR*s.frame.r;
            o.rotSpreadDeg=rMax-rMin;
            double heightPx=Math.max(1.0,c.heightR*s.frame.r);
            o.rotTipTravelPx=Math.abs(Math.tan(Math.toRadians(o.rotSpreadDeg))*heightPx);
            o.cenSpreadW=cMax-cMin;
            double widthPx=Math.max(1.0,c.widthR*s.frame.r);
            o.cenSpreadPx=o.cenSpreadW*widthPx;
        }
        return o;
    }

    public static void main(String[] args)throws Exception{
        nu.pattern.OpenCV.loadLocally();
        if(args.length<2)throw new IllegalArgumentException("SubGmtLessonsStudy <list.txt> <out_dir>");
        List<String> paths=Files.readAllLines(Path.of(args[0]));
        File out=new File(args[1]);out.mkdirs();
        try(PrintWriter pho=new PrintWriter(new FileWriter(new File(out,"photos.csv")));
            PrintWriter tri=new PrintWriter(new FileWriter(new File(out,"triangle.csv")));
            PrintWriter bat=new PrintWriter(new FileWriter(new File(out,"batons.csv")));
            PrintWriter rnd=new PrintWriter(new FileWriter(new File(out,"rounds.csv")));
            PrintWriter pos=new PrintWriter(new FileWriter(new File(out,"pose.csv")))){

            pho.println("path,orig_w,orig_h,analysis_w,analysis_h,cropped,dial_source,dial_reproducible,twelve_found,batons_found,rounds_found");
            tri.println("path,dial_reproducible,resize_outline_same_94,resize_outline_same_88,resize_outline_same_all,gap_r,gap_94,gap_88,gap_min,gap_max,gap_spread_r,gap_spread_px,rotation_deg,rotation_94,rotation_88,rotation_min,rotation_max,rotation_spread_deg,rotation_tip_travel_px,centring_w,centring_94,centring_88,centring_min,centring_max,centring_spread_w,centring_spread_px,rotation_chord_deg,base_edge_deg,radial_chord_disagreement_deg,radial_base_disagreement_deg,chord_base_disagreement_deg,width_px,height_px,track_score,track_spread_r,chord_pairs,fit,outline");
            bat.println("path,position,status,valid,detector_stable,stability_run,stability_same_edge,resample_stable,centring_w,centring_min,centring_max,centring_spread_w,rotation_deg,rotation_min,rotation_max,rotation_spread_deg,gap_w,width_px,length_px,tick_score,tick_pitch_deg,ticks_inferred,parallel_deg,note");
            rnd.println("path,hour,status,found,detector_stable,stability_run,stability_same_edge,resample_stable,offset_d,offset_min,offset_max,offset_spread_d,gap_d,gap_min,gap_max,inset,inset_min,inset_max,size_ratio,diameter_px,tick_score,tick_pitch_deg,ticks_inferred,contrast,angle_from_expected_deg,outer_over_dial_r,note");
            pos.println("path,round_found,round_stable,pose_valid,pose_markers,pose_dropped,pose_tilt_deg,pose_tilt_high_deg,pose_residual,pose_squash_sd,pose_near_frontal,pose_reason");

            for(String raw:paths){
                String p=raw.trim();if(p.isEmpty())continue;
                Bitmap preview=Load.photo(p);if(preview==null)continue;
                FullResSource full=Load.fullSource(p);
                GmtDialCrop.Crop crop=full==null?null:GmtDialCrop.make(preview,full);
                Bitmap target=crop!=null?crop.bitmap:preview;
                Sub124060QcAnalyzer.Result s=Sub124060QcAnalyzer.analyse(target,null);
                int ow=full!=null?full.width():preview.getWidth(),oh=full!=null?full.height():preview.getHeight();
                pho.printf(Locale.US,"%s,%d,%d,%d,%d,%s,%s,%s,%s,%d,%d%n",q(p),ow,oh,target.getWidth(),target.getHeight(),b(crop!=null),q(s.dialSource),B(s.dialReproducible),b(s.triangle!=null),s.batonsFound(),s.roundsFound());

                Mat src=new Mat();
                try{
                    Utils.bitmapToMat(target,src);Imgproc.cvtColor(src,src,Imgproc.COLOR_RGBA2BGR);

                    TriRepeat t=triangleRepeat(src,s);
                    if(t!=null){
                        SubTwelveTriangle.Cand c=t.original;
                        SubTwelveTriangle.Cand c94=t.scaled[0],c88=t.scaled[1];
                        tri.printf(Locale.US,
                                "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                q(p),B(s.dialReproducible),b(t.same[0]),b(t.same[1]),b(t.allSame),
                                f(c.gapR),c94==null?"":f(c94.gapR),c88==null?"":f(c88.gapR),f(t.gapMin),f(t.gapMax),f(t.gapSpreadR),f(t.gapSpreadPx),
                                f(c.rotationDeg),c94==null?"":f(c94.rotationDeg),c88==null?"":f(c88.rotationDeg),f(t.rotMin),f(t.rotMax),f(t.rotSpreadDeg),f(t.rotTipTravelPx),
                                f(c.centring),c94==null?"":f(c94.centring),c88==null?"":f(c88.centring),f(t.cenMin),f(t.cenMax),f(t.cenSpreadW),f(t.cenSpreadPx),
                                f(c.rotationChordDeg),f(c.baseEdgeDeg),f(adiff(c.rotationDeg,c.rotationChordDeg)),f(adiff(c.rotationDeg,c.baseEdgeDeg)),f(adiff(c.rotationChordDeg,c.baseEdgeDeg)),
                                f(c.widthR*s.frame.r),f(c.heightR*s.frame.r),f(c.tickScore),f(c.trackSpreadR),Integer.toString(c.chordPairs),q(c.fit),q(c.outline));
                    }

                    for(Sub124060QcAnalyzer.Baton x:s.batons){
                        GmtSixLandmarkAnalyzer.Result r=x.result;
                        if(r!=null&&r.valid&&r.stable&&s.frame!=null)GmtSixLandmarkAnalyzer.measureStability(src,s.frame.cx,s.frame.cy,s.frame.r,r);
                        double cs=(r!=null&&Double.isFinite(r.centringMax))?r.centringMax-r.centringMin:Double.NaN;
                        double rs=(r!=null&&Double.isFinite(r.rotMax))?r.rotMax-r.rotMin:Double.NaN;
                        bat.printf(Locale.US,"%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                q(p),x.position.label,x.status,b(r!=null&&r.valid),b(r!=null&&r.stable),b(r!=null&&r.stabilityRun),b(r!=null&&r.stabilitySameEdge),b(r!=null&&r.resampleStable()),
                                r==null?"":f(r.centring),r==null?"":f(r.centringMin),r==null?"":f(r.centringMax),f(cs),r==null?"":f(r.rotationDeg),r==null?"":f(r.rotMin),r==null?"":f(r.rotMax),f(rs),
                                r==null?"":f(r.gap),r==null?"":f(r.widthPx),r==null?"":f(r.lengthPx),r==null?"":f(r.tickScore),r==null?"":f(r.tickPitchDeg),r==null?"":f(r.ticksInferred),r==null?"":f(r.parallelDeg),q(x.note));
                    }

                    List<GmtRoundMarkerAnalyzer.Marker> markers=new ArrayList<>();
                    for(Sub124060QcAnalyzer.Round x:s.rounds)markers.add(x.marker);
                    GmtMarkerPose.Result mp=s.frame==null?new GmtMarkerPose.Result():GmtMarkerPose.estimate(markers,s.frame.r);
                    int found=0,stable=0;for(GmtRoundMarkerAnalyzer.Marker m:markers){if(m.found)found++;if(m.found&&m.stable)stable++;}
                    pos.printf(Locale.US,"%s,%d,%d,%s,%d,%s,%s,%s,%s,%s,%s,%s%n",q(p),found,stable,b(mp.valid),mp.markers,q(mp.dropped),f(mp.tiltDeg),f(mp.tiltHighDeg),f(mp.residual),f(mp.squashSd),b(mp.nearFrontal()),q(mp.reason));

                    if(s.frame!=null&&!markers.isEmpty())GmtRoundMarkerAnalyzer.measureStability(src,s.frame,s.tick60,markers);
                    for(Sub124060QcAnalyzer.Round x:s.rounds){
                        GmtRoundMarkerAnalyzer.Marker m=x.marker;
                        double os=Double.isFinite(m.offMax)?m.offMax-m.offMin:Double.NaN;
                        rnd.printf(Locale.US,"%s,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                                q(p),m.hour,x.status,b(m.found),b(m.stable),b(m.stabilityRun),b(m.stabilitySameEdge),b(m.resampleStable()),
                                f(m.offset),f(m.offMin),f(m.offMax),f(os),f(m.gap),f(m.gapMin),f(m.gapMax),f(m.inset),f(m.insetMin),f(m.insetMax),f(m.sizeRatio),f(m.diameterPx()),
                                f(m.tickScore),f(m.tickPitchDeg),f(m.ticksInferred),f(m.contrast),f(m.angleFromExpectedDeg),f(m.outerOverDialR),q(x.note));
                    }
                }finally{src.release();}
                pho.flush();tri.flush();bat.flush();rnd.flush();pos.flush();
            }
        }
    }

    private SubGmtLessonsStudy(){}
}
