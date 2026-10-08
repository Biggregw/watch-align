package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Alpha98 preview on the desktop: runs the exact app path (pose -> Alpha94 measurement -> Alpha98DateWindow ->
 * Alpha98Findings -> Alpha98Closeups) and writes, per photo, the results-screen text and the close-up PNGs, plus a CSV
 * of every finding's status (for comparison with the research offline check).
 *
 * Usage: run.sh Alpha98Preview <manifest.csv> <root> <out_dir>
 */
public class Alpha98Preview {
    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        nu.pattern.OpenCV.loadLocally();
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        Path out=Path.of(a[2]);Files.createDirectories(out);
        try(PrintWriter csv=new PrintWriter(new FileWriter(out.resolve("findings.csv").toFile()))){
            csv.println("photo_id,feature,status,text");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                Bitmap b=Alpha96Calib.loadAlpha96(Path.of(a[1]).resolve(f[iPath]).toString());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
                if(q==null||!q.valid||q.homography==null){System.out.println("== "+id+": dial not found");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography,HarnessModel.spec());
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                Alpha98DateWindow.Result d=Alpha98DateWindow.analyse(gray,q.homography,HarnessModel.spec().date);
                Alpha98Findings.Summary s=Alpha98Findings.build(r,d,HarnessModel.spec(),HarnessModel.ref());
                System.out.println("== "+id+"\n"+s.headline());
                int k=0;
                for(Alpha98Findings.Finding x:s.all){
                    csv.println(String.join(",",id,x.key,x.status.name(),"\""+x.text().replace("\"","'")+"\""));
                    if(x.status==Alpha98Findings.Status.OUTSIDE){
                        System.out.println("  [close-up] "+x.text());
                        Bitmap c=Alpha98Closeups.forFinding(rgba,q.homography,x,d,r.ring!=null&&r.ring.usable?r.ring.model:null,HarnessModel.spec(),HarnessModel.ref());
                        Mat cm=new Mat();Utils.bitmapToMat(c,cm);Imgproc.cvtColor(cm,cm,Imgproc.COLOR_RGBA2BGR);
                        Imgcodecs.imwrite(out.resolve(id+"_"+(k++)+"_"+x.key+".png").toString(),cm);
                    }
                }
                for(Alpha98Findings.Finding x:s.notAssessed())System.out.println("  "+x.text());
                StringBuilder w=new StringBuilder("  Within the genuine range: ");for(Alpha98Findings.Finding x:s.within())w.append(x.title).append("; ");
                System.out.println(w);
            }
        }
    }
}
