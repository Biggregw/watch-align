package com.watchalign.mobile;

import android.graphics.Bitmap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import javax.imageio.ImageIO;

/** Rendering regression without an APK. Checks the app renderer through the desktop Canvas shim,
 * then optionally exports the real pipeline's full-watch scoring overlay and all close-ups.
 * Usage: run.sh MarkerMismatchVisualCheck <outdir> [verified-photo.jpg]
 */
public class MarkerMismatchVisualCheck {
    public static void main(String[] args)throws Exception{
        Path dir=Path.of(args[0]);Files.createDirectories(dir);
        double[][] expected={{-.3,-.2},{.1,-.2},{.1,.2},{-.3,.2}};
        double[][] actual={{0,-.2},{.4,-.2},{.4,.2},{0,.2}};
        MarkerPolygonMismatch.Score score=MarkerPolygonMismatch.compare(expected,actual);
        MarkerMismatchResearch.Result measured=new MarkerMismatchResearch.Result(score,"measured");
        Bitmap crop=MarkerMismatchOverlay.closeup(Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888),0,0,1,measured);
        check(extra(crop.getPixel(200,160)),"extra region must be orange");
        check(missing(crop.getPixel(136,160)),"missing region must be magenta");
        check(crop.getPixel(168,160)==0,"overlap must stay unfilled");
        check(crop.getPixel(270,160)==0,"outside both polygons must stay unfilled");
        double[] H={200,0,150,0,200,150,0,0,1};
        Bitmap overlay=MarkerMismatchOverlay.fullWatch(400,400,H,Collections.singletonMap(9,measured));
        check(yellow(overlay.getPixel(90,150)),"full-watch scoring reference must use expected vertices");
        check(cyan(overlay.getPixel(230,150)),"full-watch actual outline must use detected vertices");
        MarkerMismatchResearch.Result withheld=new MarkerMismatchResearch.Result(score,"partial measurement withheld");
        Bitmap noRegions=MarkerMismatchOverlay.closeup(Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888),0,0,1,withheld);
        check(noRegions.getPixel(200,160)==0&&noRegions.getPixel(136,160)==0,"unreliable regions must stay unfilled");
        Bitmap noActual=MarkerMismatchOverlay.fullWatch(400,400,H,Collections.singletonMap(9,withheld));
        check(yellow(noActual.getPixel(90,150))&&!cyan(noActual.getPixel(230,150)),"withheld full-watch comparison shows reference only");
        // Sampled edge spans (cyan) vs inferred corners (red) on the exact scored polygon.
        MarkerMismatchResearch.Result spans=new MarkerMismatchResearch.Result(score,"measured");
        spans.observedSpans=new double[][][]{{{.04,-.2},{.36,-.2}},{{.4,-.16},{.4,.16}},{{.36,.2},{.04,.2}},{{0,.16},{0,-.16}}};
        Bitmap marked=MarkerMismatchOverlay.closeup(Bitmap.createBitmap(320,320,Bitmap.Config.ARGB_8888),0,0,1,spans);
        check(cyan(marked.getPixel(170,127)),"sampled middle stretch of a fitted side must be cyan");
        check(grey(marked.getPixel(162,127)),"inferred corner stretch must be dashed light grey, not cyan");
        InspectionImageStore.setMarkerOverlays(crop,noActual,overlay);
        check(InspectionImageStore.scoringOverlay==overlay&&InspectionImageStore.bitmap==noActual,"both inspection options must reach the image store");
        InspectionImageStore.setOverlay(crop,noActual,"legacy fixed view");
        check(InspectionImageStore.scoringOverlay==null,"a different overlay must clear stale scoring geometry");
        InspectionImageStore.setMarkerOverlays(crop,noActual,overlay);InspectionImageStore.set(crop,"close-up");
        check(InspectionImageStore.scoringOverlay==null,"a close-up must clear stale scoring geometry");
        InspectionImageStore.setMarkerOverlays(crop,noActual,overlay);InspectionImageStore.clear();
        check(InspectionImageStore.scoringOverlay==null&&InspectionImageStore.bitmap==null,"clearing inspection must clear both options");
        ImageIO.write(crop.img,"png",dir.resolve("synthetic-regions.png").toFile());
        System.out.println("PASS: 14 renderer/store checks (extra/missing, overlap/background, exact reference/actual, withholding, option handoff/clearing)");
        if(args.length<2)return;
        nu.pattern.OpenCV.loadLocally();
        Bitmap photo=Alpha96Calib.loadAlpha96(args[1]);
        Alpha99Pipeline.Output out=Alpha99Pipeline.run(photo,HarnessModel.spec(),HarnessModel.ref());
        check(out.ok(),"verified photo must support the unchanged pose");check(out.scoringOverlay!=null,"scoring overlay must be available");
        ImageIO.write(out.scoringOverlay.img,"png",dir.resolve("scoring-overlay.png").toFile());
        ImageIO.write(out.diagnosticOverlay.img,"png",dir.resolve("fixed-master-overlay.png").toFile());
        java.awt.image.BufferedImage composite=new java.awt.image.BufferedImage(photo.getWidth(),photo.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g=composite.createGraphics();g.drawImage(photo.img,0,0,null);g.drawImage(out.scoringOverlay.img,0,0,null);g.dispose();
        ImageIO.write(composite,"png",dir.resolve("scoring-full-watch.png").toFile());
        for(java.util.Map.Entry<String,Bitmap> entry:out.closeups.entrySet())ImageIO.write(entry.getValue().img,"png",dir.resolve(entry.getKey()+".png").toFile());
        MarkerMismatchResearch.Result nine=out.mismatch.get(9);
        System.out.println("9: "+nine.display());
        Files.writeString(dir.resolve("technical.txt"),out.technical);
    }
    private static void check(boolean valid,String reason){if(!valid)throw new AssertionError(reason);}
    private static boolean extra(int c){return (c>>>24)>0&&(c>>16&255)>200&&(c>>8&255)<180&&(c&255)<100;}
    private static boolean missing(int c){return (c>>>24)>0&&(c>>16&255)>150&&(c>>8&255)<140&&(c&255)>150;}
    private static boolean yellow(int c){return (c>>>24)>0&&(c>>16&255)>200&&(c>>8&255)>160&&(c&255)<80;}
    private static boolean grey(int c){return (c>>>24)>0&&(c>>16&255)>180&&Math.abs((c>>16&255)-(c&255))<12&&Math.abs((c>>8&255)-(c&255))<12;}
    private static boolean cyan(int c){return (c>>>24)>0&&(c>>16&255)<100&&(c>>8&255)>160&&(c&255)>180;}
}
