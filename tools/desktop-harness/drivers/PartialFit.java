package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Research (Alpha102): partial-outline round-marker fit. For every round marker the hand / glare check clears, re-fit it
 * with a 90 deg arc (+/- 45 deg) of its outline ignored, in 8 directions, and write how far the centre (local offset
 * components, R units) and the size move compared with the full fit: the error the method itself adds. For every round
 * marker the hand check withholds as HAND, re-fit it with the arc facing the hand ignored (direction from the hand-evidence
 * cells nearest the marker) and write whether it becomes measurable and its offsets.
 * Usage: run.sh PartialFit <manifest.csv> <root> <out_prefix>
 */
public class PartialFit {
    static final double HALF=45.0;

    public static void main(String[] a) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        ModelSpec spec=HarnessModel.spec();
        try(PrintWriter sim=new PrintWriter(new FileWriter(a[2]+"_simulated.csv"));PrintWriter hand=new PrintWriter(new FileWriter(a[2]+"_hand.csv"))){
            sim.println("photo_id,dial_radius_px,hour,exclude_deg,usable,d_local_rad_R,d_local_tan_R,d_size_R");
            hand.println("photo_id,dial_radius_px,hour,gap_px,seconds_line,hand_dir_deg,usable,reason,local_rad_R,local_tan_R,size_R,full_usable");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                Path p=Path.of(a[1]).resolve(f[iPath]);if(!Files.exists(p))continue;
                try{
                    Bitmap b=Alpha96Calib.loadAlpha96(p.toString());
                    AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,spec);
                    if(q==null||!q.valid||q.homography==null)continue;
                    double[] H=q.homography;double R=Alpha99MarkerInterference.pxPerR(H);
                    Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,H,spec);
                    Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                    Map<Integer,Alpha99MarkerInterference.Check> checks=Alpha99MarkerInterference.analyse(gray,H,spec);
                    rgba.release();gray.release();
                    for(ModelSpec.Marker sm:spec.withShape(ModelSpec.Shape.ROUND)){
                        Alpha94MarkerMeasurement.Marker full=r.atHour(sm.hour);Alpha99MarkerInterference.Check c=checks.get(sm.hour);
                        if(c==null)continue;
                        if(c.clean&&full!=null&&full.usable&&Double.isFinite(full.localRadialPx)){
                            for(int k=0;k<8;k++){
                                double dir=k*45.0;
                                Alpha94MarkerMeasurement.Marker pm=Alpha94MarkerMeasurement.remeasureRound(b,H,r,sm,dir,HALF);
                                if(!pm.usable||!Double.isFinite(pm.localRadialPx)){sim.printf(Locale.US,"%s,%.1f,%d,%.0f,false,,,%n",id,R,sm.hour,dir);continue;}
                                sim.printf(Locale.US,"%s,%.1f,%d,%.0f,true,%.6f,%.6f,%.6f%n",id,R,sm.hour,dir,(pm.localRadialPx-full.localRadialPx)/R,
                                        (pm.localTangentialPx-full.localTangentialPx)/R,Double.NaN);
                            }
                        }else if(!c.clean&&Alpha99MarkerInterference.HAND.equals(c.reason)){
                            double dir=Alpha99MarkerInterference.handDirection(c,sm,H);
                            if(!Double.isFinite(dir)){hand.printf(Locale.US,"%s,%.1f,%d,%.2f,%b,,false,no hand cells,,,,%b%n",id,R,sm.hour,c.touchGapPx,c.secondsHand,full!=null&&full.usable);continue;}
                            Alpha94MarkerMeasurement.Marker pm=Alpha94MarkerMeasurement.remeasureRound(b,H,r,sm,dir,HALF);
                            hand.printf(Locale.US,"%s,%.1f,%d,%.2f,%b,%.0f,%b,\"%s\",%s,%s,%s,%b%n",id,R,sm.hour,c.touchGapPx,c.secondsHand,dir,pm.usable,pm.reason==null?"":pm.reason,
                                    pm.usable?String.format(Locale.US,"%.6f",pm.localRadialPx/R):"",pm.usable?String.format(Locale.US,"%.6f",pm.localTangentialPx/R):"",
                                    pm.usable?String.format(Locale.US,"%.6f",pm.radiusErrPx/R):"",full!=null&&full.usable);
                        }
                    }
                }catch(Throwable t){System.err.println(id+" "+t);}
                sim.flush();hand.flush();
                System.err.println(li+"/"+(lines.size()-1)+" "+id);
            }
        }
    }

}
