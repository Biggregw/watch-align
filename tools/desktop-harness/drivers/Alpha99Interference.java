package com.watchalign.mobile;

import android.graphics.Bitmap;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Alpha99 interference check on the desktop: per photo and marker, whether Alpha99MarkerInterference withholds it and
 * why, next to the Alpha94 usable flag. Writes interference.csv and (optional) one strip montage per photo for visual
 * review: grey = sampled strip (outer edge at the top), blue = marker + halo, red = foreign samples, magenta = a
 * structure touching the marker (the withholding evidence).
 *
 * Usage: run.sh Alpha99Interference <manifest.csv> <root> <out_dir> [--no-images]
 */
public class Alpha99Interference {
    public static void main(String[] a) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),true,"UTF-8"));
        nu.pattern.OpenCV.loadLocally();
        boolean images=a.length<4||!a[3].equals("--no-images");
        List<String> lines=Files.readAllLines(Path.of(a[0]));String[] hdr=lines.get(0).split(",",-1);int iPath=-1,iId=-1;
        for(int i=0;i<hdr.length;i++){if(hdr[i].equals("local_path")||hdr[i].equals("path"))iPath=i;if(hdr[i].equals("photo_id"))iId=i;}
        Path out=Path.of(a[2]);Files.createDirectories(out);
        try(PrintWriter csv=new PrintWriter(new FileWriter(out.resolve("interference.csv").toFile()))){
            csv.println("photo_id,dial_radius_px,hour,alpha94_usable,clean,reason,touch_span_R,foreign_frac");
            for(int li=1;li<lines.size();li++){
                String[] f=lines.get(li).split(",",-1);String id=iId>=0?f[iId]:f[iPath];
                Path p=Path.of(a[1]).resolve(f[iPath]);if(!Files.exists(p)){System.out.println("== "+id+": missing");continue;}
                Bitmap b=Alpha96Calib.loadAlpha96(p.toString());
                AutomaticDialOverlay.Result q=b==null?null:AutomaticDialOverlay.build(b,HarnessModel.spec());
                if(q==null||!q.valid||q.homography==null){System.out.println("== "+id+": dial not found");continue;}
                Alpha94MarkerMeasurement.Report r=Alpha94MarkerMeasurement.analyse(b,q.homography,HarnessModel.spec());
                Mat rgba=new Mat(),gray=new Mat();Utils.bitmapToMat(b,rgba);Imgproc.cvtColor(rgba,gray,Imgproc.COLOR_RGBA2GRAY);
                Map<Integer,Alpha99MarkerInterference.Check> m=Alpha99MarkerInterference.analyse(gray,q.homography,HarnessModel.spec());
                StringBuilder s=new StringBuilder("== "+id+String.format(Locale.US," R=%.0f :",r.dialRadiusPx));
                List<Mat> tiles=new ArrayList<>();
                for(Alpha99MarkerInterference.Check c:m.values()){
                    Alpha94MarkerMeasurement.Marker mk=c.hour==12?r.triangle:r.atHour(c.hour);boolean u=mk!=null&&mk.usable;
                    csv.println(String.join(",",id,String.format(Locale.US,"%.1f",r.dialRadiusPx),""+c.hour,""+u,""+c.clean,"\""+c.reason+"\"",
                            String.format(Locale.US,"%.4f",c.touchSpanR),String.format(Locale.US,"%.4f",c.foreignFrac)));
                    s.append(String.format(Locale.US," %d%s%s",c.hour,u?"":"(a94-)",c.clean?"":(c.reason.startsWith("hand")?(c.secondsHand?"[SEC]":String.format(Locale.US,"[HAND %.1fpx]",c.touchGapPx)):"[GLARE]")));
                    if(images&&c.grid!=null)tiles.add(tile(c));
                    if(images&&!c.clean){double ang=Math.toRadians(c.hour*30.0),rc=c.hour==12?0.75:0.78;
                        Mat z=Alpha98Closeups.render(rgba,q.homography,Math.sin(ang)*rc,-Math.cos(ang)*rc,0.22);
                        Imgproc.cvtColor(z,z,Imgproc.COLOR_RGBA2BGR);Imgcodecs.imwrite(out.resolve(id+"_x"+c.hour+".png").toString(),z);}
                }
                System.out.println(s);
                {double rp=Alpha99MarkerInterference.pxPerR(q.homography);
                 double minX=1e9,minY=1e9,maxX=-1e9,maxY=-1e9;for(int kk=0;kk<72;kk++){double t=2*Math.PI*kk/72;double[] H=q.homography;double x=Math.cos(t),y=Math.sin(t),w=H[6]*x+H[7]*y+H[8];double px=(H[0]*x+H[1]*y+H[2])/w,py=(H[3]*x+H[4]*y+H[5])/w;minX=Math.min(minX,px);maxX=Math.max(maxX,px);minY=Math.min(minY,py);maxY=Math.max(maxY,py);}
                 Alpha91SplineImage sp=new Alpha91SplineImage(gray,(int)minX-4,(int)minY-4,(int)maxX+5,(int)maxY+5);Alpha99MarkerInterference.Sampler im=sp::at;
                 List<double[]> ds=Alpha99MarkerInterference.dots(im,q.homography,rp,HarnessModel.spec());StringBuilder z=new StringBuilder(String.format(Locale.US,"   L=%.0f dots:",Alpha99MarkerInterference.lumeContrast(im,q.homography,HarnessModel.spec())));
                 for(int kk=0;kk<Math.min(5,ds.size());kk++)z.append(String.format(Locale.US," %.1f°@%.3f:%.0f",ds.get(kk)[0],ds.get(kk)[1],ds.get(kk)[2]));
                 System.out.println(z);
                 if(images)for(int kk=0;kk<Math.min(3,ds.size());kk++){double ang=Math.toRadians(ds.get(kk)[0]),rr=ds.get(kk)[1];
                    Mat zz=Alpha98Closeups.render(rgba,q.homography,Math.sin(ang)*rr,-Math.cos(ang)*rr,0.12);Imgproc.cvtColor(zz,zz,Imgproc.COLOR_RGBA2BGR);
                    Imgproc.putText(zz,String.format(Locale.US,"%.0f deg s%.0f",ds.get(kk)[0],ds.get(kk)[2]),new Point(5,25),Imgproc.FONT_HERSHEY_SIMPLEX,0.8,new Scalar(0,255,255),2);
                    Imgcodecs.imwrite(out.resolve(id+"_dot"+kk+".png").toString(),zz);}}
                if(images&&!tiles.isEmpty()){
                    int H=0;for(Mat t:tiles)H=Math.max(H,t.rows());
                    List<Mat> padded=new ArrayList<>();
                    for(Mat t:tiles){Mat z=new Mat(H,t.cols()+6,CvType.CV_8UC3,new Scalar(40,40,40));t.copyTo(z.submat(0,t.rows(),0,t.cols()));padded.add(z);}
                    Mat row=new Mat();Core.hconcat(padded,row);
                    Imgcodecs.imwrite(out.resolve(id+"_strips.png").toString(),row);
                }
            }
        }
    }

    private static Mat tile(Alpha99MarkerInterference.Check c){
        int k=3;Mat t=new Mat(c.rows*k+18,Math.max(c.cols*k,60),CvType.CV_8UC3,new Scalar(0,0,0));
        for(int i=0;i<c.rows;i++)for(int j=0;j<c.cols;j++){
            int idx=i*c.cols+j,g=c.grid[idx];int v=Math.max(0,Math.min(255,Math.round(c.values[idx])));
            double[] col;
            switch(g){
                case -1:col=new double[]{30,30,30};break;
                case 3:col=new double[]{Math.min(255,v*0.6+90),v*0.6,v*0.6};break;       // blue tint (BGR)
                case 1:col=new double[]{0,0,255};break;
                case 2:col=new double[]{255,0,255};break;
                case 4:col=new double[]{v*0.8,v*0.8,v};break;
                case 5:col=new double[]{0,140,255};break;
                default:col=new double[]{v,v,v};
            }
            int y=(c.rows-1-i)*k;   // outer edge at the top
            Imgproc.rectangle(t,new Point(j*k,y),new Point(j*k+k-1,y+k-1),new Scalar(col),-1);
        }
        Imgproc.putText(t,c.hour+(c.clean?"":" X"),new Point(2,c.rows*k+14),Imgproc.FONT_HERSHEY_SIMPLEX,0.45,
                c.clean?new Scalar(0,255,0):new Scalar(0,0,255),1);
        return t;
    }
}
