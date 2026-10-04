package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Research-only direct 12<->6 clearance export.
 *
 * Both clearances are recomputed from the measured geometry after Load.human has mapped
 * full-resolution crop coordinates back to the preview. They are then divided by the SAME
 * fitted-dial radius, rather than by triangle width at 12 and baton width at 6.
 *
 * This makes the two positions directly comparable as image-space radial clearances without
 * changing production QC behaviour.
 *
 * Usage:
 *   tools/desktop-harness/run.sh OppositeGap <resolved_images.csv> <dataset_root>
 */
public final class OppositeGap {
    private OppositeGap() {}

    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        org.opencv.core.Core.setNumThreads(1);
        if (args.length != 2) throw new IllegalArgumentException("OppositeGap <resolved.csv> <dataset_root>");
        Path csv=Path.of(args[0]), root=Path.of(args[1]);
        List<String> lines=Files.readAllLines(csv);
        if(lines.isEmpty()) return;
        String[] hdr=lines.get(0).split(",",-1);
        int iPath=idx(hdr,"local_path"), iClass=idx(hdr,"class_label"), iWatch=idx(hdr,"physical_watch_id"),
                iSplit=idx(hdr,"split"), iSource=idx(hdr,"source_id");
        System.out.println("source_id,split,watch,path,twelve_found,twelve_stable,six_found,six_stable,dial_r_px,gap12_px,gap6_px,gap12_r,gap6_r,raw_diff_r,production_gap12,production_gap6");
        for(int li=1;li<lines.size();li++){
            String[] f=lines.get(li).split(",",-1);
            if(iPath<0||iPath>=f.length) continue;
            if(iClass>=0&&iClass<f.length&&!"gen".equals(f[iClass])) continue;
            String rel=f[iPath];
            File image=root.resolve(rel).toFile();
            if(!image.exists()) continue;
            String source=val(f,iSource), split=val(f,iSplit), watch=val(f,iWatch);
            try{
                Bitmap b=Load.photo(image.getPath());
                if(b==null) continue;
                Load.Human hu=Load.human(b,image.getPath());
                GmtHumanQcAnalyzerV2.Result h=hu.h;
                GmtHumanSummary.Input s=h.summary;
                MeasuredOverlayRenderer.Drawing d=h.drawing;
                double dialR=(d!=null&&Double.isFinite(d.dialA)&&Double.isFinite(d.dialB))?(d.dialA+d.dialB)/2.0:Double.NaN;
                double g12px=d!=null?gap12px(d.twelve):Double.NaN;
                double g6px=d!=null?gap6px(d.six):Double.NaN;
                double g12r=finitePos(dialR)&&Double.isFinite(g12px)?g12px/dialR:Double.NaN;
                double g6r=finitePos(dialR)&&Double.isFinite(g6px)?g6px/dialR:Double.NaN;
                boolean twelveFound=d!=null&&d.twelve!=null&&Double.isFinite(g12r);
                boolean twelveStable=twelveFound&&s!=null&&s.stableFrame;
                boolean sixFound=d!=null&&d.six!=null&&h.six!=null&&h.six.valid&&Double.isFinite(g6r);
                boolean sixStable=sixFound&&h.six.stable&&h.six.resampleStable();
                double prod12=s==null?Double.NaN:s.observedGap;
                double prod6=h.six==null?Double.NaN:h.six.gap;
                System.out.printf(Locale.US,
                        "%s,%s,%s,%s,%s,%s,%s,%s,%.3f,%.4f,%.4f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                        esc(source),esc(split),esc(watch),esc(rel),twelveFound,twelveStable,sixFound,sixStable,
                        dialR,g12px,g6px,g12r,g6r,
                        Double.isFinite(g12r)&&Double.isFinite(g6r)?g12r-g6r:Double.NaN,prod12,prod6);
            }catch(Throwable t){
                System.err.println("OppositeGap failed on "+rel+": "+t.getClass().getSimpleName()+" "+t.getMessage());
            }
        }
    }

    private static int idx(String[] h,String name){for(int i=0;i<h.length;i++)if(name.equals(h[i].trim()))return i;return -1;}
    private static String val(String[] f,int i){return i>=0&&i<f.length?f[i]:"";}
    private static boolean finitePos(double x){return Double.isFinite(x)&&x>1e-9;}
    private static String esc(String s){return s==null?"":s.replace(",",";");}

    private static double gap12px(GmtTwelveLandmarkAnalyzer.Geometry g){
        if(g==null)return Double.NaN;
        double[] mid=mid(g.triLeft,g.triRight);
        return Math.abs(pointLineDistance(mid,g.tick59,g.tick01));
    }

    private static double gap6px(GmtSixLandmarkAnalyzer.Geometry g){
        if(g==null)return Double.NaN;
        double[] mid=mid(g.outerLeft,g.outerRight);
        return Math.abs(pointLineDistance(mid,g.tickAfter,g.tickBefore));
    }

    private static double[] mid(double[] a,double[] b){
        if(a==null||b==null)return new double[]{Double.NaN,Double.NaN};
        return new double[]{(a[0]+b[0])/2.0,(a[1]+b[1])/2.0};
    }

    private static double pointLineDistance(double[] p,double[] a,double[] b){
        if(p==null||a==null||b==null)return Double.NaN;
        double dx=b[0]-a[0],dy=b[1]-a[1],len=Math.hypot(dx,dy);
        if(len<1e-9)return Double.NaN;
        return ((p[0]-a[0])*dy-(p[1]-a[1])*dx)/len;
    }
}
