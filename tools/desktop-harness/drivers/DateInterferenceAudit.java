package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** READ-ONLY research diagnostics of 1.6.3 on 10 VERIFIED 126610 genuine watches.
 * The original app's Alpha99Pipeline still performs every measurement; no decision
 * or detector code is changed. This prints competing hand-check evidence and the
 * exact existing fit uncertainty for every flagged marker.
 */
public final class DateInterferenceAudit {
    static final LinkedHashMap<String,Integer> CASES = new LinkedHashMap<>();
    static {
        CASES.put("6f071d93608b9081",12);
        CASES.put("22060f9a8f867d35",12);
        CASES.put("800cd2600f322aa5",12);
        CASES.put("02d7c97e429c6984",12);
        CASES.put("482e344ce9c60e61",12);
        CASES.put("e418538d0e9af0bf",12);
        CASES.put("94ad771e9e2aedde",5);
        CASES.put("2802b4a30cfb277b",9);
        CASES.put("c11761654b519222",12);
        CASES.put("ee07ceedbb6f0bd3",6);
    }
    static String n(double d){return Double.isFinite(d)?String.format(Locale.US,"%.3f",d):"n/a";}
    public static void main(String[] args) throws Exception {
        nu.pattern.OpenCV.loadLocally();
        ModelSpec model=HarnessModel.spec();
        ModelReference genuine=HarnessModel.ref();
        if(!model.id.equals("submariner_126610")) throw new IllegalStateException("Incorrect model");
        Path p=HarnessModel.assetsRoot().toPath().resolve("models/"+model.id+"/alignment/luminous_alignment.json");
        AlignmentReference align=AlignmentReference.parse(Files.readString(p));
        Path mp=p.resolveSibling("metal_alignment.properties");
        if(Files.exists(mp))align.metalExtra=MetalAlignment.parse(Files.readString(mp));

        Path catalogue=Path.of(args[0]),root=Path.of(args[1]);
        List<Map<String,String>> rows=AlignmentCalib.csv(Files.readAllLines(catalogue));
        int done=0;
        for(Map<String,String> row:rows) {
            String id=row.get("photo_id");
            if(!CASES.containsKey(id))continue;
            int hour=CASES.get(id);
            Path imgPath=root.resolve(row.get("local_path"));
            if(!Files.isRegularFile(imgPath)) {
                System.out.println("MISSING|"+id+"|"+row.get("physical_watch_id"));continue;
            }
            Bitmap b=Alpha96Calib.loadAlpha96(imgPath.toString());
            Alpha99Pipeline.Output o=Alpha99Pipeline.run(b,model,genuine,align,null);
            if(o.clearlyOff==null) {System.out.println("NO_ANALYSIS|"+id);continue;}
            ClearlyOff.Marker mark=null;
            for(ClearlyOff.Marker m:o.clearlyOff.markers)if(m.hour==hour){mark=m;break;}
            if(mark==null){System.out.println("MISSING_MARKER|"+id+"|"+hour);continue;}
            Alpha99MarkerInterference.Check check=o.checks==null?null:o.checks.get(hour);
            System.out.println("CASE|"+id+"|"+row.get("physical_watch_id")+"|"+row.get("model")+"|hour="+hour+
                 "|state="+mark.state+"|source="+mark.source+
                 "|side="+n(mark.sidePct)+"|inout="+n(mark.inoutPct)+"|tilt="+n(mark.tiltDeg)+
                 "|gate="+String.join(";",mark.gate).replace("|","/")+"|uncertain="+mark.uncertain.replace("|","/"));
            System.out.println("EDGE|"+id+"|"+hour+"|cover="+n(mark.cover)+"|badFrac="+n(mark.badFrac)+
                "|fitRmsPx="+n(mark.fitRmsPx)+"|sePosPct="+n(mark.sePosPct)+
                "|seRotDeg="+n(mark.seRotDeg)+"|gap="+n(mark.gapPct)+"|fitStatus="+
                (mark.fitStatus==null?"null":java.util.Arrays.toString(countStatus(mark.fitStatus))));
            if(check!=null)System.out.println("ALPHA99|"+id+"|"+hour+"|clean="+check.clean+
                "|reason="+check.reason.replace("|","/")+"|secondsHand="+check.secondsHand+
                "|secondsPsi="+n(check.secondsPsi)+"|touchGapPx="+n(check.touchGapPx)+
                "|foreignFrac="+n(check.foreignFrac)+"|span="+n(check.touchSpanR));
            else System.out.println("ALPHA99|"+id+"|"+hour+"|NULL");
            Mat rgba=new Mat(),gray=new Mat(),f=new Mat();
            Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
            gray.convertTo(f,CvType.CV_32F);
            try{
                ClearlyOff.Img im=new ClearlyOff.Img(f,o.pose.homography);
                List<ClearlyOff.Input> inputs=Alpha99Pipeline.clearlyOffInputs(o.alignment,o.checks);
                ClearlyOff.Ctx ctx=ClearlyOff.context(im,o.measurement.dialRadiusPx,inputs);
                System.out.println("HANDS|"+id+"|"+hour+"|"+ctx.hands.size()+"|"+hands(ctx.hands));
                if(mark.yellow!=null){
                    MetalFit.Outline pts=MetalFit.outline(mark.yellow,o.measurement.dialRadiusPx,mark.shape==ModelSpec.Shape.ROUND);
                    int old=0,second=0;
                    double psi=check==null?Double.NaN:check.secondsPsi;
                    for(double[] pt:pts.pts){
                        if(MetalFit.underHand(pt,ctx.hands))old++;
                        if(Double.isFinite(psi)){
                            double a=Math.toDegrees(Math.atan2(pt[0],-pt[1]));
                            double diff=Math.toRadians(Math.abs(ClearlyOff.pmod(a-psi+180,360)-180));
                            if(ClearlyOff.norm(pt)*Math.sin(diff)<=0.012+1.0/o.measurement.dialRadiusPx+0.006)second++;
                        }
                    }
                    System.out.println("MASK|"+id+"|"+hour+"|outlineSamples="+pts.pts.length+
                       "|maskedByMETAL="+old+"|onAlpha99SecondsLine="+second);
                }
            }finally{rgba.release();gray.release();f.release();}
            try {
                if(o.alignmentCloseups.containsKey(hour)){
                    BufferedImage raw=o.alignmentCloseups.get(hour).img;
                    int w=Math.min(raw.getWidth(),600),h=Math.max(1,(int)Math.round(raw.getHeight()*(w/(double)raw.getWidth())));
                    BufferedImage rgb=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                    Graphics2D g=rgb.createGraphics();g.drawImage(raw,0,0,w,h,null);g.dispose();
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    ImageIO.write(rgb,"jpg",bytes);
                    System.out.println("CROP|"+id+"|"+hour+"|"+Base64.getEncoder().encodeToString(bytes.toByteArray()));
                }
            }catch(Throwable t){System.out.println("CROP_ERROR|"+id+"|"+t);}
            done++;
        }
        System.out.println("AUDIT_DONE|"+done+"|expected="+CASES.size());
        if(done!=CASES.size())throw new IllegalStateException("Incomplete verified audit");
    }
    static int[] countStatus(byte[] values) {
        int[] n=new int[3];for(byte b:values){if(b>=0&&b<n.length)n[b]++;}return n;
    }
    static String hands(List<double[]> h){
        StringBuilder s=new StringBuilder();
        for(double[] x:h)s.append(String.format(Locale.US,"{angle=%.1f,width=%.1f,reach=%.3f}",x[0],x[1],x[2]));
        return s.toString();
    }
}
